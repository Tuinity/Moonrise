package ca.spottedleaf.moonrise.mixin.random;

import ca.spottedleaf.moonrise.common.util.ThreadUnsafeRandom;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.RandomSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Level.class)
abstract class LevelMixin {

    /**
     * @reason Changes Level#random to use ThreadUnsafeRandom, skipping the thread checks and CAS logic
     * @author Spottedleadf
     */
    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;create()Lnet/minecraft/util/RandomSource;"
        )
    )
    private RandomSource redirectLevelRandom() {
        return new ThreadUnsafeRandom(RandomSupport.generateUniqueSeed());
    }
}
