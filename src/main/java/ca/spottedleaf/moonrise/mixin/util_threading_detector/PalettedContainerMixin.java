package ca.spottedleaf.moonrise.mixin.util_threading_detector;

import net.minecraft.util.ThreadingDetector;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PalettedContainer.class)
abstract class PalettedContainerMixin {
    @Unique
    private static final ThreadingDetector THREADING_DETECTOR = new ThreadingDetector("PalettedContainer");

    /**
     * @reason our ThreadingDetectorMixin makes all instance methods no-op, no use in having multiple instances
     * @author jpenilla
     */
    @Redirect(
        method = "<init>*",
        at = @At(
            value = "NEW",
            target = "Lnet/minecraft/util/ThreadingDetector;"
        )
    )
    private static ThreadingDetector threadingDetector(final String name) {
        return THREADING_DETECTOR;
    }
}
