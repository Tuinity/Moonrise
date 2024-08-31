package ca.spottedleaf.moonrise.mixin.end_island;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DensityFunctions.EndIslandDensityFunction.class)
abstract class DensityFunctions$EndIslandDensityFunctionMixin {

    /**
     * @reason Fix <a href="https://bugs.mojang.com/browse/MC-159283">MC-159283</a> by avoiding overflow in the distance calculation.
     *         See the bug report for the issue description.
     * @author Spottedleaf
     */
    @Redirect(
        method = "getHeightValue",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/Mth;sqrt(F)F",
            ordinal = 0
        )
    )
    private static float fixMC159283(final float input,
                                     @Local(ordinal = 0, argsOnly = true) final int x,
                                     @Local(ordinal = 1, argsOnly = true) final int z) {
        if (PlatformHooks.get().configFixMC159283()) {
            return (float)Math.sqrt((double)((long)x * (long)x + (long)z * (long)z));
        } else {
            return (float)Math.sqrt((double)(float)(x * x + z * z));
        }
    }
}
