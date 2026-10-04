package ca.spottedleaf.moonrise.mixin.heightmap;

import ca.spottedleaf.moonrise.patches.heightmap.HeightmapWorkaroundMixinFieldInit;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Heightmap.class)
abstract class HeightmapMixin {

    @Shadow
    @Final
    private BitStorage data;

    @Shadow
    @Final
    private ChunkAccess chunk;


    // note: as it stands, max block height is currently 4096
    //       this will fit inside char
    //       additionally, for normal worlds (height = 384 or 256) the number of
    //       bytes used in the storage is 296 compared to this (512)
    @Unique
    private final char[] heightmapRaw = new char[16*16];

    /**
     * @reason Init raw heightmap
     * @author Spottedleaf
     */
    @Inject(
        method = "setRawData",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/System;arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V",
            shift = At.Shift.AFTER
        )
    )
    private void initRawHeightmap(final ChunkAccess chunk, final Heightmap.Types type, final long[] data, final CallbackInfo ci) {
        final int[] tmp = HeightmapWorkaroundMixinFieldInit.TEMP_ARRAY.get();
        this.data.unpack(tmp);
        for (int i = 0, len = tmp.length; i < len; ++i) {
            this.heightmapRaw[i] = (char)tmp[i];
        }
    }

    /**
     * @reason Update raw heightmap
     * @author Spottedleaf
     */
    @Redirect(
        method = "setHeight",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/BitStorage;set(II)V"
        )
    )
    private void updateRawHeightmap(final BitStorage instance, final int index, final int value) {
        instance.set(index, value);
        this.heightmapRaw[index] = (char)value;
    }

    /**
     * @reason Use raw heightmap
     * @author Spottedleaf
     */
    @Overwrite
    private int getFirstAvailable(final int index) {
        return (int)this.heightmapRaw[index] + this.chunk.getMinY();
    }

    /**
     * @reason Avoid use of multiply
     * @author Spottedleaf
     */
    @Overwrite
    private static int getIndex(final int x, final int z) {
        return x | (z << 4);
    }
}
