package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.level.storage.ChunkSystemSectionStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
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

@Mixin(SectionStorage.class)
abstract class SectionStorageMixin<R, P> implements ChunkSystemSectionStorage, AutoCloseable {

    @Shadow
    private SimpleRegionStorage simpleRegionStorage;

    @Shadow
    @Final
    static Logger LOGGER;


    @Unique
    private RegionFileStorage storage;

    @Override
    public final RegionFileStorage moonrise$getRegionStorage() {
        return this.storage;
    }

    /**
     * @reason Retrieve storage from IOWorker, and then nuke it
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void initHook(final CallbackInfo ci) {
        this.storage = this.simpleRegionStorage.worker.storage;
        this.simpleRegionStorage = null;
    }

    /**
     * @reason Route to new chunk system hook
     * @author Spottedleaf
     */
    @Overwrite
    public final CompletableFuture<Optional<CompoundTag>> tryRead(final ChunkPos pos) {
        try {
            return CompletableFuture.completedFuture(Optional.ofNullable(this.moonrise$read(pos.x, pos.z)));
        } catch (final Throwable thr) {
            return CompletableFuture.failedFuture(thr);
        }
    }

    /**
     * @reason Destroy old chunk system hook
     * @author Spottedleaf
     */
    @Overwrite
    public void unpackChunk(final ChunkPos chunkPos, final SectionStorage.PackedChunk<P> packedChunk) {
        throw new IllegalStateException("Only chunk system can load in state, offending class:" + this.getClass().getName());
    }

    /**
     * @reason Route to new chunk system hook
     * @author Spottedleaf
     */
    @Redirect(
            method = "writeChunk(Lnet/minecraft/world/level/ChunkPos;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/SimpleRegionStorage;write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Void> redirectWrite(final SimpleRegionStorage instance, final ChunkPos pos,
                                                  final CompoundTag tag) {
        try {
            this.moonrise$write(pos.x, pos.z, tag);
        } catch (final IOException ex) {
            LOGGER.error("Error writing poi chunk data to disk for chunk " + pos, ex);
        }
        return null;
    }

    /**
     * @reason Route to new chunk system hook
     * @author Spottedleaf
     */
    @Redirect(
            method = "close",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/SimpleRegionStorage;close()V"
            )
    )
    private void redirectClose(final SimpleRegionStorage instance) throws IOException {
        this.moonrise$close();
    }
}
