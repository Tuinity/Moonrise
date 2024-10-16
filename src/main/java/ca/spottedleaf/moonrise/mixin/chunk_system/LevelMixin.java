package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable;
import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.dfl.DefaultEntityLookup;
import ca.spottedleaf.moonrise.patches.chunk_system.world.ChunkSystemEntityGetter;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Mixin(Level.class)
abstract class LevelMixin implements ChunkSystemLevel, ChunkSystemEntityGetter, LevelAccessor, AutoCloseable {

    @Shadow
    public abstract ProfilerFiller getProfiler();

    @Shadow
    public abstract LevelChunk getChunk(int i, int j);

    @Shadow
    public abstract int getHeight(Heightmap.Types types, int i, int j);


    @Unique
    private EntityLookup entityLookup;

    @Unique
    private final ConcurrentLong2ReferenceChainedHashTable<ChunkData> chunkData = new ConcurrentLong2ReferenceChainedHashTable<>();

    @Override
    public final EntityLookup moonrise$getEntityLookup() {
        return this.entityLookup;
    }

    @Override
    public void moonrise$setEntityLookup(final EntityLookup entityLookup) {
        if (this.entityLookup != null && !(this.entityLookup instanceof DefaultEntityLookup)) {
            throw new IllegalStateException("Entity lookup already initialised");
        }
        this.entityLookup = entityLookup;
    }

    /**
     * @reason Default initialise entity lookup incase mods extend Level
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void initHook(final CallbackInfo ci, @Local(argsOnly = true) final Holder<DimensionType> dimensionType) {
        this.entityLookup = new DefaultEntityLookup((Level)(Object)this, dimensionType.value());
    }

    /**
     * @reason Route to faster lookup
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public List<Entity> getEntities(final Entity entity, final AABB boundingBox, final Predicate<? super Entity> predicate) {
        this.getProfiler().incrementCounter("getEntities");
        final List<Entity> ret = new ArrayList<>();

        ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(entity, boundingBox, ret, predicate);

        PlatformHooks.get().addToGetEntities((Level)(Object)this, entity, boundingBox, predicate, ret);

        return ret;
    }

    /**
     * @reason Route to faster lookup
     * @author Spottedleaf
     */
    @Overwrite
    public <T extends Entity> void getEntities(final EntityTypeTest<Entity, T> entityTypeTest,
                                               final AABB boundingBox, final Predicate<? super T> predicate,
                                               final List<? super T> into, final int maxCount) {
        this.getProfiler().incrementCounter("getEntities");

        if (entityTypeTest instanceof EntityType<T> byType) {
            if (maxCount != Integer.MAX_VALUE) {
                ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(byType, boundingBox, into, predicate, maxCount);
                PlatformHooks.get().addToGetEntities((Level)(Object)this, entityTypeTest, boundingBox, predicate, into, maxCount);
                return;
            } else {
                ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(byType, boundingBox, into, predicate);
                PlatformHooks.get().addToGetEntities((Level)(Object)this, entityTypeTest, boundingBox, predicate, into, maxCount);
                return;
            }
        }

        if (entityTypeTest == null) {
            if (maxCount != Integer.MAX_VALUE) {
                ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities((Entity)null, boundingBox, (List)into, (Predicate)predicate, maxCount);
                PlatformHooks.get().addToGetEntities((Level)(Object)this, entityTypeTest, boundingBox, predicate, into, maxCount);
                return;
            } else {
                ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities((Entity)null, boundingBox, (List)into, (Predicate)predicate);
                PlatformHooks.get().addToGetEntities((Level)(Object)this, entityTypeTest, boundingBox, predicate, into, maxCount);
                return;
            }
        }

        final Class<? extends Entity> base = entityTypeTest.getBaseClass();

        final Predicate<? super T> modifiedPredicate;
        if (predicate == null) {
            modifiedPredicate = (final T obj) -> {
                return entityTypeTest.tryCast(obj) != null;
            };
        } else {
            modifiedPredicate = (final Entity obj) -> {
                final T casted = entityTypeTest.tryCast(obj);
                if (casted == null) {
                    return false;
                }

                return predicate.test(casted);
            };
        }

        if (base == null || base == Entity.class) {
            if (maxCount != Integer.MAX_VALUE) {
                ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities((Entity)null, boundingBox, (List)into, (Predicate)modifiedPredicate, maxCount);
                PlatformHooks.get().addToGetEntities((Level)(Object)this, entityTypeTest, boundingBox, predicate, into, maxCount);
                return;
            } else {
                ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities((Entity)null, boundingBox, (List)into, (Predicate)modifiedPredicate);
                PlatformHooks.get().addToGetEntities((Level)(Object)this, entityTypeTest, boundingBox, predicate, into, maxCount);
                return;
            }
        } else {
            if (maxCount != Integer.MAX_VALUE) {
                ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(base, null, boundingBox, (List)into, (Predicate)modifiedPredicate, maxCount);
                PlatformHooks.get().addToGetEntities((Level)(Object)this, entityTypeTest, boundingBox, predicate, into, maxCount);
                return;
            } else {
                ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(base, null, boundingBox, (List)into, (Predicate)modifiedPredicate);
                PlatformHooks.get().addToGetEntities((Level)(Object)this, entityTypeTest, boundingBox, predicate, into, maxCount);
                return;
            }
        }
    }

    /**
     * Route to faster lookup
     * @author Spottedleaf
     */
    @Override
    public final <T extends Entity> List<T> getEntitiesOfClass(final Class<T> entityClass, final AABB boundingBox, final Predicate<? super T> predicate) {
        this.getProfiler().incrementCounter("getEntities");
        final List<T> ret = new ArrayList<>();

        ((ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(entityClass, null, boundingBox, ret, predicate);
        // note: superclass would have entered the above method, so we do need this hook here
        PlatformHooks.get().addToGetEntities(
            (Level)(Object)this, EntityTypeTest.forClass(entityClass), boundingBox, predicate, ret, Integer.MAX_VALUE
        );

        return ret;
    }

    /**
     * Route to faster lookup
     * @author Spottedleaf
     */
    @Override
    public final List<Entity> moonrise$getHardCollidingEntities(final Entity entity, final AABB box, final Predicate<? super Entity> predicate) {
        this.getProfiler().incrementCounter("getEntities");
        final List<Entity> ret = new ArrayList<>();

        ((ChunkSystemLevel)this).moonrise$getEntityLookup().getHardCollidingEntities(entity, box, ret, predicate);

        return ret;
    }

    @Override
    public LevelChunk moonrise$getFullChunkIfLoaded(final int chunkX, final int chunkZ) {
        return (LevelChunk)this.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    @Override
    public ChunkAccess moonrise$getAnyChunkIfLoaded(final int chunkX, final int chunkZ) {
        return this.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.EMPTY, false);
    }

    @Override
    public ChunkAccess moonrise$getSpecificChunkIfLoaded(final int chunkX, final int chunkZ, final ChunkStatus leastStatus) {
        return this.getChunkSource().getChunk(chunkX, chunkZ, leastStatus, false);
    }

    @Override
    public void moonrise$midTickTasks() {
        // no-op on ClientLevel
    }

    @Override
    public final ChunkData moonrise$getChunkData(final long chunkKey) {
        return this.chunkData.get(chunkKey);
    }

    @Override
    public final ChunkData moonrise$getChunkData(final int chunkX, final int chunkZ) {
        return this.chunkData.get(CoordinateUtils.getChunkKey(chunkX, chunkZ));
    }

    @Override
    public final ChunkData moonrise$requestChunkData(final long chunkKey) {
        return this.chunkData.compute(chunkKey, (final long keyInMap, final ChunkData valueInMap) -> {
            if (valueInMap == null) {
                final ChunkData ret = new ChunkData();
                ret.increaseRef();
                return ret;
            }

            valueInMap.increaseRef();
            return valueInMap;
        });
    }

    @Override
    public final ChunkData moonrise$releaseChunkData(final long chunkKey) {
        return this.chunkData.compute(chunkKey, (final long keyInMap, final ChunkData chunkData) -> {
            return chunkData.decreaseRef() == 0 ? null : chunkData;
        });
    }

    @Override
    public boolean moonrise$areChunksLoaded(final int fromX, final int fromZ, final int toX, final int toZ) {
        final ChunkSource chunkSource = this.getChunkSource();

        for (int currZ = fromZ; currZ <= toZ; ++currZ) {
            for (int currX = fromX; currX <= toX; ++currX) {
                if (!chunkSource.hasChunk(currX, currZ)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * @reason Declare method in this class so that any invocations are virtual, and not interface.
     * @author Spottedleaf
     */
    @Override
    public boolean hasChunk(final int x, final int z) {
        return this.getChunkSource().hasChunk(x, z);
    }

    @Override
    public boolean hasChunksAt(final int minBlockX, final int minBlockZ, final int maxBlockX, final int maxBlockZ) {
        return this.moonrise$areChunksLoaded(
            minBlockX >> 4, minBlockZ >> 4, maxBlockX >> 4, maxBlockZ >> 4
        );
    }

    /**
     * @reason Turn all getChunk(x, z, status) calls into virtual invokes, instead of interface invokes:
     *         1. The interface invoke is expensive
     *         2. The method makes other interface invokes (again, expensive)
     *         Instead, we just directly call getChunk(x, z, status, true) which avoids the interface invokes entirely.
     * @author Spottedleaf
     */
    @Override
    public ChunkAccess getChunk(final int x, final int z, final ChunkStatus status) {
        return ((Level)(Object)this).getChunk(x, z, status, true);
    }

    @Override
    public BlockPos getHeightmapPos(Heightmap.Types types, BlockPos blockPos) {
        return new BlockPos(blockPos.getX(), this.getHeight(types, blockPos.getX(), blockPos.getZ()), blockPos.getZ());
    }

    /**
     * @reason Allow block updates in non-ticking chunks, as new chunk system sends non-ticking chunks to clients
     * @author Spottedleaf
     */
    @Redirect(
            method = {
                    "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
                    // NeoForge splits logic from the original method into this one
                    "markAndNotifyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;II)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/FullChunkStatus;isOrAfter(Lnet/minecraft/server/level/FullChunkStatus;)Z"
            )
    )
    private boolean sendUpdatesForFullChunks(final FullChunkStatus instance,
                                             final FullChunkStatus fullChunkStatus) {

        return instance.isOrAfter(FullChunkStatus.FULL);
    }

    // TODO: Thread.currentThread() != this.thread to TickThread?


    /**
     * @reason Execute mid-tick chunk tasks during entity ticking
     * @author Spottedleaf
     */
    @Inject(
            method = "guardEntityTick",
            at = @At(
                    value = "INVOKE",
                    shift = At.Shift.AFTER,
                    target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"
            )
    )
    private void midTickEntity(final CallbackInfo ci) {
        this.moonrise$midTickTasks();
    }
}
