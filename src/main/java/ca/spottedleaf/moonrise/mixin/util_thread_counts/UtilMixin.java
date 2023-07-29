package ca.spottedleaf.moonrise.mixin.util_thread_counts;

import net.minecraft.Util;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Util.class)
public abstract class UtilMixin {

    /**
     * @reason Don't over-allocate executor threads, they may choke the rest of the game
     * @author Spottedleaf
     */
    @Redirect(
            method = "makeExecutor",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;clamp(III)I"
            )
    )
    private static int correctThreadCounts(int value, final int min, final int max) {
        final int cpus = Runtime.getRuntime().availableProcessors() / 2;
        if (cpus <= 4) {
            value = cpus <= 2 ? 1 : 2;
        } else if (cpus <= 8) {
            // [5, 8]
            value = cpus <= 6 ? 3 : 4;
        } else {
            value = Math.min(8, cpus / 2);
        }

        return Mth.clamp(value, min, max);
    }
}
