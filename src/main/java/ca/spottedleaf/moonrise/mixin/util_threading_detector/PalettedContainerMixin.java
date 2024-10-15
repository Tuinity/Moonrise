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
        // cannot use `<init>*` due to https://github.com/FabricMC/tiny-remapper/issues/137
        method = {
            "<init>(Lnet/minecraft/core/IdMap;Lnet/minecraft/world/level/chunk/PalettedContainer$Strategy;Lnet/minecraft/world/level/chunk/PalettedContainer$Configuration;Lnet/minecraft/util/BitStorage;Ljava/util/List;)V",
            "<init>(Lnet/minecraft/core/IdMap;Lnet/minecraft/world/level/chunk/PalettedContainer$Strategy;Lnet/minecraft/world/level/chunk/PalettedContainer$Data;)V",
            "<init>(Lnet/minecraft/core/IdMap;Ljava/lang/Object;Lnet/minecraft/world/level/chunk/PalettedContainer$Strategy;)V"
        },
        at = @At(
            value = "NEW",
            target = "Lnet/minecraft/util/ThreadingDetector;"
        ),
        require = 3 // Require matching all 3 constructors
    )
    private static ThreadingDetector threadingDetector(final String name) {
        return THREADING_DETECTOR;
    }
}
