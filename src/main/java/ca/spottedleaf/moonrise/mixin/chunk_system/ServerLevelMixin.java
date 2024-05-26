package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread;
import ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.ChunkDataController;
import ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.EntityDataController;
import ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.PoiDataController;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevelReader;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup;
import ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level implements ChunkSystemServerLevel, ChunkSystemLevelReader, WorldGenLevel {

    @Shadow
    private PersistentEntitySectionManager<Entity> entityManager;

    protected ServerLevelMixin(WritableLevelData writableLevelData, ResourceKey<Level> resourceKey, RegistryAccess registryAccess, Holder<DimensionType> holder, Supplier<ProfilerFiller> supplier, boolean bl, boolean bl2, long l, int i) {
        super(writableLevelData, resourceKey, registryAccess, holder, supplier, bl, bl2, l, i);
    }

    @Unique
    private boolean markedClosing;

    @Unique
    private final RegionizedPlayerChunkLoader.ViewDistanceHolder viewDistanceHolder = new RegionizedPlayerChunkLoader.ViewDistanceHolder();

    @Unique
    private final RegionizedPlayerChunkLoader chunkLoader = new RegionizedPlayerChunkLoader((ServerLevel)(Object)this);

    @Unique
    private EntityDataController entityDataController;

    @Unique
    private PoiDataController poiDataController;

    @Unique
    private ChunkDataController chunkDataController;

    @Unique
    private ChunkTaskScheduler chunkTaskScheduler;

    /**
     * @reason Initialise fields / destroy entity manager state
     * @author Spottedleaf
     */
    @Inject(
        method = "<init>",
        at = @At(
            value = "RETURN"
        )
    )
    private void init(MinecraftServer minecraftServer, Executor executor,
                      LevelStorageSource.LevelStorageAccess levelStorageAccess, ServerLevelData serverLevelData,
                      ResourceKey<Level> resourceKey, LevelStem levelStem, ChunkProgressListener chunkProgressListener,
                      boolean bl, long l, List<CustomSpawner> list, boolean bl2, RandomSequences randomSequences,
                      CallbackInfo ci) {
        this.entityManager = null;

        this.entityDataController = new EntityDataController(
                new EntityDataController.EntityRegionFileStorage(
                        new RegionStorageInfo(levelStorageAccess.getLevelId(), resourceKey, "entities"),
                        levelStorageAccess.getDimensionPath(resourceKey).resolve("entities"),
                        minecraftServer.forceSynchronousWrites()
                )
        );
        this.poiDataController = new PoiDataController((ServerLevel)(Object)this);
        this.chunkDataController = new ChunkDataController((ServerLevel)(Object)this);
        this.moonrise$setEntityLookup(new ServerEntityLookup((ServerLevel)(Object)this, ((ServerLevel)(Object)this).new EntityCallbacks()));
        this.chunkTaskScheduler = new ChunkTaskScheduler((ServerLevel)(Object)this, ChunkTaskScheduler.workerThreads);
    }

    @Override
    public final LevelChunk moonrise$getFullChunkIfLoaded(final int chunkX, final int chunkZ) {
        final NewChunkHolder newChunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(CoordinateUtils.getChunkKey(chunkX, chunkZ));
        if (!newChunkHolder.isFullChunkReady()) {
            return null;
        }

        if (newChunkHolder.getCurrentChunk() instanceof LevelChunk levelChunk) {
            return levelChunk;
        }
        // race condition: chunk unloaded, only happens off-main
        return null;
    }

    @Override
    public final ChunkAccess moonrise$getAnyChunkIfLoaded(final int chunkX, final int chunkZ) {
        final NewChunkHolder newChunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(CoordinateUtils.getChunkKey(chunkX, chunkZ));
        if (newChunkHolder == null) {
            return null;
        }
        final NewChunkHolder.ChunkCompletion lastCompletion = newChunkHolder.getLastChunkCompletion();
        return lastCompletion == null ? null : lastCompletion.chunk();
    }

    @Override
    public final ChunkAccess moonrise$getSpecificChunkIfLoaded(final int chunkX, final int chunkZ, final ChunkStatus leastStatus) {
        final NewChunkHolder newChunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (newChunkHolder == null) {
            return null;
        }
        final NewChunkHolder.ChunkCompletion lastCompletion = newChunkHolder.getLastChunkCompletion();
        return lastCompletion == null || !lastCompletion.genStatus().isOrAfter(leastStatus) ? null : lastCompletion.chunk();
    }

    @Override
    public final ChunkAccess moonrise$syncLoadNonFull(final int chunkX, final int chunkZ, final ChunkStatus status) {
        return this.moonrise$getChunkTaskScheduler().syncLoadNonFull(chunkX, chunkZ, status);
    }

    @Override
    public final ChunkTaskScheduler moonrise$getChunkTaskScheduler() {
        return this.chunkTaskScheduler;
    }

    @Override
    public final RegionFileIOThread.ChunkDataController moonrise$getChunkDataController() {
        return this.chunkDataController;
    }

    @Override
    public final RegionFileIOThread.ChunkDataController moonrise$getPoiChunkDataController() {
        return this.poiDataController;
    }

    @Override
    public final RegionFileIOThread.ChunkDataController moonrise$getEntityChunkDataController() {
        return this.entityDataController;
    }

    @Override
    public final int moonrise$getRegionChunkShift() {
        // current default in Folia
        // note that there is no actual regionizing taking place in Moonrise...
        return 2;
    }

    @Override
    public final boolean moonrise$isMarkedClosing() {
        return this.markedClosing;
    }

    @Override
    public final void moonrise$setMarkedClosing(final boolean value) {
        this.markedClosing = value;
    }

    @Override
    public final RegionizedPlayerChunkLoader moonrise$getPlayerChunkLoader() {
        return this.chunkLoader;
    }

    @Override
    public final void moonrise$loadChunksAsync(final BlockPos pos, final int radiusBlocks,
                                               final PrioritisedExecutor.Priority priority,
                                               final Consumer<List<ChunkAccess>> onLoad) {
        this.moonrise$loadChunksAsync(
                (pos.getX() - radiusBlocks) >> 4,
                (pos.getX() + radiusBlocks) >> 4,
                (pos.getZ() - radiusBlocks) >> 4,
                (pos.getZ() + radiusBlocks) >> 4,
                priority, onLoad
        );
    }

    @Override
    public final void moonrise$loadChunksAsync(final BlockPos pos, final int radiusBlocks,
                                               final ChunkStatus chunkStatus, final PrioritisedExecutor.Priority priority,
                                               final Consumer<List<ChunkAccess>> onLoad) {
        this.moonrise$loadChunksAsync(
                (pos.getX() - radiusBlocks) >> 4,
                (pos.getX() + radiusBlocks) >> 4,
                (pos.getZ() - radiusBlocks) >> 4,
                (pos.getZ() + radiusBlocks) >> 4,
                chunkStatus, priority, onLoad
        );
    }

    @Override
    public final void moonrise$loadChunksAsync(final int minChunkX, final int maxChunkX, final int minChunkZ, final int maxChunkZ,
                                               final PrioritisedExecutor.Priority priority,
                                               final Consumer<List<ChunkAccess>> onLoad) {
        this.moonrise$loadChunksAsync(minChunkX, maxChunkX, minChunkZ, maxChunkZ, ChunkStatus.FULL, priority, onLoad);
    }

    @Override
    public final void moonrise$loadChunksAsync(final int minChunkX, final int maxChunkX, final int minChunkZ, final int maxChunkZ,
                                               final ChunkStatus chunkStatus, final PrioritisedExecutor.Priority priority,
                                               final Consumer<List<ChunkAccess>> onLoad) {
        final ChunkTaskScheduler chunkTaskScheduler = this.moonrise$getChunkTaskScheduler();
        final ChunkHolderManager chunkHolderManager = chunkTaskScheduler.chunkHolderManager;

        final int requiredChunks = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        final AtomicInteger loadedChunks = new AtomicInteger();
        final Long holderIdentifier = ChunkTaskScheduler.getNextChunkLoadId();
        final int ticketLevel = ChunkTaskScheduler.getTicketLevel(chunkStatus);

        final List<ChunkAccess> ret = new ArrayList<>(requiredChunks);

        final Consumer<ChunkAccess> consumer = (final ChunkAccess chunk) -> {
            if (chunk != null) {
                synchronized (ret) {
                    ret.add(chunk);
                }
                chunkHolderManager.addTicketAtLevel(ChunkTaskScheduler.CHUNK_LOAD, chunk.getPos(), ticketLevel, holderIdentifier);
            }
            if (loadedChunks.incrementAndGet() == requiredChunks) {
                try {
                    onLoad.accept(java.util.Collections.unmodifiableList(ret));
                } finally {
                    for (int i = 0, len = ret.size(); i < len; ++i) {
                        final ChunkPos chunkPos = ret.get(i).getPos();

                        chunkHolderManager.removeTicketAtLevel(ChunkTaskScheduler.CHUNK_LOAD, chunkPos, ticketLevel, holderIdentifier);
                    }
                }
            }
        };

        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                chunkTaskScheduler.scheduleChunkLoad(cx, cz, chunkStatus, true, priority, consumer);
            }
        }
    }

    @Override
    public final RegionizedPlayerChunkLoader.ViewDistanceHolder moonrise$getViewDistanceHolder() {
        return this.viewDistanceHolder;
    }

    /**
     * @reason Entities are guaranteed to be ticking in the new chunk system
     * @author Spottedleaf
     */
    @Redirect(
            method = "method_31420",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/DistanceManager;inEntityTickingRange(J)Z"
            )
    )
    private boolean shortCircuitTickCheck(final DistanceManager instance, final long chunk) {
        return true;
    }

    /**
     * @reason This logic is handled by the chunk system
     * @author Spottedleaf
     */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;tick()V"
            )
    )
    private void redirectEntityManagerTick(final PersistentEntitySectionManager<Entity> instance) {}

    /**
     * @reason Optimise implementation and route to new chunk system
     * @author Spottedleaf
     */
    @Override
    @Overwrite
    public boolean shouldTickBlocksAt(final long chunkPos) {
        final NewChunkHolder holder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkPos);
        return holder != null && holder.isTickingReady();
    }

    /**
     * @reason saveAll handled by ServerChunkCache#save
     * @author Spottedleaf
     */
    @Redirect(
            method = "save",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;saveAll()V"
            )
    )
    private void redirectSaveAll(final PersistentEntitySectionManager<Entity> instance) {}

    /**
     * @reason autoSave handled by ServerChunkCache#save
     * @author Spottedleaf
     */
    @Redirect(
            method = "save",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;autoSave()V"
            )
    )
    private void redirectAutoSave(final PersistentEntitySectionManager<Entity> instance) {}

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Redirect(
            method = "addPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;addNewEntity(Lnet/minecraft/world/level/entity/EntityAccess;)Z"
            )
    )
    private <T extends EntityAccess> boolean redirectAddPlayerEntity(final PersistentEntitySectionManager<T> instance, final T entity) {
        return this.moonrise$getEntityLookup().addNewEntity((Entity)entity);
    }

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Redirect(
            method = "addEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;addNewEntity(Lnet/minecraft/world/level/entity/EntityAccess;)Z"
            )
    )
    private <T extends EntityAccess> boolean redirectAddEntityEntity(final PersistentEntitySectionManager<T> instance, final T entity) {
        return this.moonrise$getEntityLookup().addNewEntity((Entity)entity);
    }

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Overwrite
    public boolean tryAddFreshEntityWithPassengers(Entity entity) {
        final Stream<UUID> stream = entity.getSelfAndPassengers().map(Entity::getUUID);
        if (stream.anyMatch(this.moonrise$getEntityLookup()::hasEntity)) {
            return false;
        } else {
            this.addFreshEntityWithPassengers(entity);
            return true;
        }
    }

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Redirect(
            method = "saveDebugReport",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;gatherStats()Ljava/lang/String;"
            )
    )
    private String redirectDebugStats(final PersistentEntitySectionManager<Entity> instance) {
        return this.moonrise$getEntityLookup().getDebugInfo();
    }

    /**
     * @reason dumpChunks not implemented
     * @author Spottedleaf
     */
    @Redirect(
            method = "saveDebugReport",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;dumpChunks(Ljava/io/Writer;)V"
            )
    )
    private void redirectChunkMapDebug(final ChunkMap instance, final Writer writer) {}

    /**
     * @reason dumpSections not implemented
     * @author Spottedleaf
     */
    @Redirect(
            method = "saveDebugReport",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;dumpSections(Ljava/io/Writer;)V"
            )
    )
    private void redirectEntityManagerDebug(final PersistentEntitySectionManager<Entity> instance, final Writer writer) {}

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Redirect(
            method = "getWatchdogStats",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;gatherStats()Ljava/lang/String;"
            )
    )
    private String redirectWatchdogStats1(final PersistentEntitySectionManager<Entity> instance) {
        return this.moonrise$getEntityLookup().getDebugInfo();
    }

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Redirect(
            method = "getWatchdogStats",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;getEntityGetter()Lnet/minecraft/world/level/entity/LevelEntityGetter;"
            )
    )
    private LevelEntityGetter<Entity> redirectWatchdogStats2(final PersistentEntitySectionManager<Entity> instance) {
        return this.moonrise$getEntityLookup();
    }

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Redirect(
            method = "getEntities()Lnet/minecraft/world/level/entity/LevelEntityGetter;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;getEntityGetter()Lnet/minecraft/world/level/entity/LevelEntityGetter;"
            )
    )
    private LevelEntityGetter<Entity> redirectGetEntities(final PersistentEntitySectionManager<Entity> instance) {
        return this.moonrise$getEntityLookup();
    }

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Redirect(
            method = "addLegacyChunkEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;addLegacyChunkEntities(Ljava/util/stream/Stream;)V"
            )
    )
    private void redirectLegacyChunkEntities(final PersistentEntitySectionManager<Entity> instance,
                                             final Stream<Entity> stream) {
        this.moonrise$getEntityLookup().addLegacyChunkEntities(stream.toList(), null); // TODO
    }

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Redirect(
            method = "addWorldGenChunkEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;addWorldGenChunkEntities(Ljava/util/stream/Stream;)V"
            )
    )
    private void redirectWorldGenChunkEntities(final PersistentEntitySectionManager<Entity> instance,
                                               final Stream<Entity> stream) {
        this.moonrise$getEntityLookup().addWorldGenChunkEntities(stream.toList(), null); // TODO
    }

    /**
     * @reason Level close now handles this
     * @author Spottedleaf
     */
    @Redirect(
            method = "close",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;close()V"
            )
    )
    private void redirectClose(final PersistentEntitySectionManager<Entity> instance) {}

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Redirect(
            method = "gatherChunkSourceStats",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;gatherStats()Ljava/lang/String;"
            )
    )
    private String redirectGatherChunkSourceStats(final PersistentEntitySectionManager<Entity> instance) {
        return this.moonrise$getEntityLookup().getDebugInfo();
    }

    /**
     * @reason Redirect to chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public boolean areEntitiesLoaded(final long chunkPos) {
        // chunk loading guarantees entity loading
        return this.moonrise$getAnyChunkIfLoaded(CoordinateUtils.getChunkX(chunkPos), CoordinateUtils.getChunkZ(chunkPos)) != null;
    }

    /**
     * @reason Redirect to chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public boolean isPositionTickingWithEntitiesLoaded(final long chunkPos) {
        final NewChunkHolder chunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkPos);
        // isTicking implies the chunk is loaded, and the chunk is loaded now implies the entities are loaded
        return chunkHolder != null && chunkHolder.isTickingReady();
    }

    /**
     * @reason Redirect to chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public boolean isPositionEntityTicking(BlockPos pos) {
        final NewChunkHolder chunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(CoordinateUtils.getChunkKey(pos));
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
    }

    /**
     * @reason Redirect to chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public boolean isNaturalSpawningAllowed(final BlockPos pos) {
        final NewChunkHolder chunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(CoordinateUtils.getChunkKey(pos));
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
    }

    /**
     * @reason Redirect to chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public boolean isNaturalSpawningAllowed(final ChunkPos pos) {
        final NewChunkHolder chunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(CoordinateUtils.getChunkKey(pos));
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
    }

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Redirect(
            method = "method_54438",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;count()I"
            )
    )
    private int redirectCrashCount(final PersistentEntitySectionManager<Entity> instance) {
        return this.moonrise$getEntityLookup().getEntityCount();
    }
}
