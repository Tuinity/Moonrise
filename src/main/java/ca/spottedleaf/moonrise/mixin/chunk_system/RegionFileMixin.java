package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemChunkBuffer;
import ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemRegionFile;
import ca.spottedleaf.moonrise.patches.chunk_system.util.stream.ExternalChunkStreamMarker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Mixin(RegionFile.class)
public abstract class RegionFileMixin implements ChunkSystemRegionFile {

    @Shadow
    @Final
    RegionFileVersion version;


    // TODO can't really add synchronized to methods, can we?


    @Override
    public final MoonriseRegionFileIO.RegionDataController.WriteData moonrise$startWrite(final CompoundTag data, final ChunkPos pos) throws IOException {
        final RegionFile.ChunkBuffer buffer = ((RegionFile)(Object)this).new ChunkBuffer(pos);
        ((ChunkSystemChunkBuffer)buffer).moonrise$setWriteOnClose(false);

        final DataOutputStream out = new DataOutputStream(this.version.wrap(buffer));

        return new MoonriseRegionFileIO.RegionDataController.WriteData(
                data, MoonriseRegionFileIO.RegionDataController.WriteData.WriteResult.WRITE,
                out, ((ChunkSystemChunkBuffer)buffer)::moonrise$write
        );
    }

    /**
     * @reason Wrap external streams so that callers on read methods can determine whether the data is stored externally or not
     * @author Spottedleaf
     */
    @Inject(
            method = "createExternalChunkInputStream",
            cancellable = true,
            at = @At(
                    value = "RETURN"
            )
    )
    private void markExternalChunkStreams(final CallbackInfoReturnable<DataInputStream> cir) {
        final DataInputStream is = cir.getReturnValue();
        if (is == null) {
            return;
        }
        cir.setReturnValue(new ExternalChunkStreamMarker(is));
    }
}
