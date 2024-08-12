package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.misc.Delayed26WayDistancePropagator3D;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.poi.ChunkSystemPoiManager;
import ca.spottedleaf.moonrise.patches.chunk_system.level.poi.ChunkSystemPoiSection;
import ca.spottedleaf.moonrise.patches.chunk_system.level.poi.PoiChunk;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Codec;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(PoiManager.class)
// Declare the generic type as Object so that our Overrides match the method signature of the superclass
// Specifically, getOrCreate must return Object so that existing invokes do not route to the superclass
public abstract class PoiManagerMixin extends SectionStorage<Object> implements ChunkSystemPoiManager {

    @Shadow
    abstract boolean isVillageCenter(long l);

    @Shadow
    public abstract void checkConsistencyWithBlocks(SectionPos sectionPos, LevelChunkSection levelChunkSection);

    public PoiManagerMixin(SimpleRegionStorage simpleRegionStorage, Function<Runnable, Codec<Object>> function, Function<Runnable, Object> function2, RegistryAccess registryAccess, ChunkIOErrorReporter chunkIOErrorReporter, LevelHeightAccessor levelHeightAccessor) {
        super(simpleRegionStorage, function, function2, registryAccess, chunkIOErrorReporter, levelHeightAccessor);
    }

    @Unique
    private ServerLevel world;

    // the vanilla tracker needs to be replaced because it does not support level removes, and we need level removes
    // to support poi unloading
    @Unique
    private final Delayed26WayDistancePropagator3D villageDistanceTracker = new Delayed26WayDistancePropagator3D();

    @Unique
    private static final int POI_DATA_SOURCE = 7;

    @Unique
    private static int convertBetweenLevels(final int level) {
        return POI_DATA_SOURCE - level;
    }

    @Unique
    private void updateDistanceTracking(long section) {
        if (this.isVillageCenter(section)) {
            this.villageDistanceTracker.setSource(section, POI_DATA_SOURCE);
        } else {
            this.villageDistanceTracker.removeSource(section);
        }
    }

    /**
     * @reason Initialise fields
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void initHook(RegionStorageInfo regionStorageInfo, Path path, DataFixer dataFixer, boolean bl,
                          RegistryAccess registryAccess, ChunkIOErrorReporter chunkIOErrorReporter,
                          LevelHeightAccessor levelHeightAccessor, CallbackInfo ci) {
        this.world = (ServerLevel)levelHeightAccessor;
    }

    /**
     * @reason Replace vanilla tracker
     * @author Spottedleaf
     */
    @Overwrite
    public int sectionsToVillage(final SectionPos pos) {
        this.villageDistanceTracker.propagateUpdates();
        return convertBetweenLevels(this.villageDistanceTracker.getLevel(CoordinateUtils.getChunkSectionKey(pos)));
    }

    /**
     * @reason Replace vanilla tracker and avoid superclass poi data writing (which is now handled by chunk autosave)
     * @author Spottedleaf
     */
    @Overwrite
    public void tick(final BooleanSupplier shouldKeepTicking) {
        this.villageDistanceTracker.propagateUpdates();
    }

    /**
     * @reason Replace vanilla tracker, mark poi chunk as dirty
     * @author Spottedleaf
     */
    @Override
    @Overwrite
    public void setDirty(final long pos) {
        final int chunkX = CoordinateUtils.getChunkSectionX(pos);
        final int chunkZ = CoordinateUtils.getChunkSectionZ(pos);
        final ChunkHolderManager manager = ((ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager;
        final PoiChunk chunk = manager.getPoiChunkIfLoaded(chunkX, chunkZ, false);
        if (chunk != null) {
            chunk.setDirty(true);
        }
        this.updateDistanceTracking(pos);
    }

    /**
     * @reason Replace vanilla tracker
     * @author Spottedleaf
     */
    @Override
    @Overwrite
    public void onSectionLoad(final long pos) {
        this.updateDistanceTracking(pos);
    }

    @Override
    public Optional<Object> get(final long pos) {
        final int chunkX = CoordinateUtils.getChunkSectionX(pos);
        final int chunkY = CoordinateUtils.getChunkSectionY(pos);
        final int chunkZ = CoordinateUtils.getChunkSectionZ(pos);

        TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Accessing poi chunk off-main");

        final ChunkHolderManager manager = ((ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager;
        final PoiChunk ret = manager.getPoiChunkIfLoaded(chunkX, chunkZ, true);

        return ret == null ? Optional.empty() : (Optional)ret.getSectionForVanilla(chunkY);
    }

    @Override
    public Optional<Object> getOrLoad(final long pos) {
        final int chunkX = CoordinateUtils.getChunkSectionX(pos);
        final int chunkY = CoordinateUtils.getChunkSectionY(pos);
        final int chunkZ = CoordinateUtils.getChunkSectionZ(pos);

        TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Accessing poi chunk off-main");

        final ChunkHolderManager manager = ((ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager;

        if (chunkY >= WorldUtil.getMinSection(this.world) && chunkY <= WorldUtil.getMaxSection(this.world)) {
            final PoiChunk ret = manager.getPoiChunkIfLoaded(chunkX, chunkZ, true);
            if (ret != null) {
                return (Optional)ret.getSectionForVanilla(chunkY);
            } else {
                return (Optional)manager.loadPoiChunk(chunkX, chunkZ).getSectionForVanilla(chunkY);
            }
        }
        // retain vanilla behavior: do not load section if out of bounds!
        return Optional.empty();
    }

    @Override
    protected Object getOrCreate(final long pos) {
        final int chunkX = CoordinateUtils.getChunkSectionX(pos);
        final int chunkY = CoordinateUtils.getChunkSectionY(pos);
        final int chunkZ = CoordinateUtils.getChunkSectionZ(pos);

        TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Accessing poi chunk off-main");

        final ChunkHolderManager manager = ((ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager;

        final PoiChunk ret = manager.getPoiChunkIfLoaded(chunkX, chunkZ, true);
        if (ret != null) {
            return ret.getOrCreateSection(chunkY);
        } else {
            return manager.loadPoiChunk(chunkX, chunkZ).getOrCreateSection(chunkY);
        }
    }

    @Override
    public final ServerLevel moonrise$getWorld() {
        return this.world;
    }

    @Override
    public final void moonrise$onUnload(final long coordinate) { // Paper - rewrite chunk system
        final int chunkX = CoordinateUtils.getChunkX(coordinate);
        final int chunkZ = CoordinateUtils.getChunkZ(coordinate);
        TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Unloading poi chunk off-main");
        for (int section = this.levelHeightAccessor.getMinSection(); section < this.levelHeightAccessor.getMaxSection(); ++section) {
            final long sectionPos = SectionPos.asLong(chunkX, section, chunkZ);
            this.updateDistanceTracking(sectionPos);
        }
    }

    @Override
    public final void moonrise$loadInPoiChunk(final PoiChunk poiChunk) {
        final int chunkX = poiChunk.chunkX;
        final int chunkZ = poiChunk.chunkZ;
        TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Loading poi chunk off-main");
        for (int sectionY = this.levelHeightAccessor.getMinSection(); sectionY < this.levelHeightAccessor.getMaxSection(); ++sectionY) {
            final PoiSection section = poiChunk.getSection(sectionY);
            if (section != null && !((ChunkSystemPoiSection)section).moonrise$isEmpty()) {
                this.onSectionLoad(SectionPos.asLong(chunkX, sectionY, chunkZ));
            }
        }
    }

    @Override
    public final void moonrise$checkConsistency(final ChunkAccess chunk) {
        final int chunkX = chunk.getPos().x;
        final int chunkZ = chunk.getPos().z;

        final int minY = WorldUtil.getMinSection(chunk);
        final int maxY = WorldUtil.getMaxSection(chunk);
        final LevelChunkSection[] sections = chunk.getSections();
        for (int section = minY; section <= maxY; ++section) {
            this.checkConsistencyWithBlocks(SectionPos.of(chunkX, section, chunkZ), sections[section - minY]);
        }
    }

    /**
     * @reason The loaded field is unused, so adding entries needlessly consumes memory.
     * @author Spottedleaf
     */
    @Redirect(
            method = "ensureLoadedAndValid",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;",
                    ordinal = 1
            )
    )
    private <T> Stream<T> skipLoadedSet(final Stream<T> instance, final Predicate<? super T> predicate) {
        return instance;
    }

    @Override
    public final void moonrise$close() throws IOException {}

    @Override
    public final CompoundTag moonrise$read(final int chunkX, final int chunkZ) throws IOException {
        return MoonriseRegionFileIO.loadData(
                this.world, chunkX, chunkZ, MoonriseRegionFileIO.RegionFileType.POI_DATA,
                MoonriseRegionFileIO.getIOBlockingPriorityForCurrentThread()
        );
    }

    @Override
    public final void moonrise$write(final int chunkX, final int chunkZ, final CompoundTag data) throws IOException {
        MoonriseRegionFileIO.scheduleSave(this.world, chunkX, chunkZ, data, MoonriseRegionFileIO.RegionFileType.POI_DATA);
    }
}
