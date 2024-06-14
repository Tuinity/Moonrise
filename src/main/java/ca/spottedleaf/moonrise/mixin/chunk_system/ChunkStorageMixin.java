package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemChunkStorage;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.levelgen.structure.LegacyStructureDataHandler;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(ChunkStorage.class)
public abstract class ChunkStorageMixin implements ChunkSystemChunkStorage, AutoCloseable {

    @Shadow
    private IOWorker worker;

    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    @Unique
    private RegionFileStorage storage;

    /**
     * @reason Destroy old IO worker field after retrieving region storage from it
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void initHook(final CallbackInfo ci) {
        this.storage = this.worker.storage;
        this.worker = null;
    }

    @Override
    public final RegionFileStorage moonrise$getRegionStorage() {
        return this.storage;
    }

    /**
     * @reason The code using this method only uses it to avoid retrieving possibly empty biome blending data. There is
     *         no actual cost to retrieve this data, but there is an obvious significant penalty to loading the NBT data
     *         from disk directly to check (even _if_ cached).
     * @author Spottedleaf
     */
    @Overwrite
    public boolean isOldChunkAround(final ChunkPos pos, final int radius) {
        return true;
    }


    /**
     * @reason Legacy data is accessed by multiple threads, and so it should be synchronised correctly.
     *         The initialisation code is oddly initialised correctly, but not the actual accesses after.
     * @author Spottedleaf
     */
    @Redirect(
            method = "upgradeChunkTag",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/structure/LegacyStructureDataHandler;updateFromLegacy(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;"
            )
    )
    private CompoundTag synchroniseLegacyDataUpgrade(final LegacyStructureDataHandler instance, final CompoundTag compoundTag) {
        synchronized (instance) {
            return instance.updateFromLegacy(compoundTag);
        }
    }

    /**
     * @reason Redirect to use the raw storage. It is expected that {@link net.minecraft.server.level.ChunkMap}
     *         overrides to route to the RegionFile IO thread, as ChunkStorage may be initialised directly when
     *         forceUpgrading. The IO threads are not capable of servicing requests during forceUpgrading, as a
     *         world is not initialised.
     * @author Spottedleaf
     */
    @Redirect(
            method = "read",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/IOWorker;loadAsync(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Optional<CompoundTag>> redirectLoad(final IOWorker instance, final ChunkPos chunkPos) {
        try {
            return CompletableFuture.completedFuture(Optional.ofNullable(this.storage.read(chunkPos)));
        } catch (final Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    /**
     * @reason Redirect to use the raw storage. It is expected that {@link net.minecraft.server.level.ChunkMap}
     *         overrides to route to the RegionFile IO thread, as ChunkStorage may be initialised directly when
     *         forceUpgrading. The IO threads are not capable of servicing requests during forceUpgrading, as a
     *         world is not initialised.
     * @author Spottedleaf
     */
    @Redirect(
            method = "write",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/IOWorker;store(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Void> redirectWrite(final IOWorker instance, final ChunkPos chunkPos,
                                                  final CompoundTag compoundTag) {
        try {
            this.storage.write(chunkPos, compoundTag);
            return CompletableFuture.completedFuture(null);
        } catch (final Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    /**
     * @reason Legacy data is accessed by multiple threads, and so it should be synchronised correctly.
     *         The initialisation code is oddly initialised correctly, but not the actual accesses after.
     * @author Spottedleaf
     */
    @Redirect(
            method = "handleLegacyStructureIndex",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/structure/LegacyStructureDataHandler;removeIndex(J)V"
            )
    )
    private void synchroniseLegacyDataWrite(final LegacyStructureDataHandler instance,
                                            final long pos) {
        synchronized (instance) {
            instance.removeIndex(pos);
        }
    }

    /**
     * @reason Redirect to flush the storage directly
     * @author Spottedleaf
     */
    @Overwrite
    public void flushWorker() {
        try {
            this.storage.flush();
        } catch (final IOException ex) {
            LOGGER.error("Failed to flush chunk storage", ex);
        }
    }

    /**
     * @reason Redirect to close the storage directly
     * @author Spottedleaf
     */
    @Override
    @Overwrite
    public void close() throws Exception {
        this.storage.close();
    }

    /**
     * @reason Redirect to access the storage directly
     * @author Spottedleaf
     */
    @Overwrite
    public ChunkScanAccess chunkScanner() {
        // TODO ChunkMap implementation?
        return (chunkPos, streamTagVisitor) -> {
            try {
                this.storage.scanChunk(chunkPos, streamTagVisitor);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    /**
     * @reason Redirect to access the storage directly
     * @author Spottedleaf
     */
    @Overwrite
    public RegionStorageInfo storageInfo() {
        return this.storage.info();
    }
}
