package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemChunkBuffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

@Mixin(RegionFile.ChunkBuffer.class)
public abstract class ChunkBufferMixin extends ByteArrayOutputStream implements ChunkSystemChunkBuffer {

    @Shadow
    @Final
    private ChunkPos pos;

    @Unique
    private boolean writeOnClose = true;

    @Override
    public final boolean moonrise$getWriteOnClose() {
        return this.writeOnClose;
    }

    @Override
    public final void moonrise$setWriteOnClose(final boolean value) {
        this.writeOnClose = value;
    }

    @Override
    public final void moonrise$write(final RegionFile regionFile) throws IOException {
        regionFile.write(this.pos, ByteBuffer.wrap(this.buf, 0, this.count));
    }

    /**
     * @reason Allow delaying write I/O until later
     * @author Spottedleaf
     */
    @Redirect(
            method = "close",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/RegionFile;write(Lnet/minecraft/world/level/ChunkPos;Ljava/nio/ByteBuffer;)V"
            )
    )
    private void redirectClose(final RegionFile instance, final ChunkPos chunkPos, final ByteBuffer byteBuffer) throws IOException {
        if (this.writeOnClose) {
            instance.write(chunkPos, byteBuffer);
        }
    }
}
