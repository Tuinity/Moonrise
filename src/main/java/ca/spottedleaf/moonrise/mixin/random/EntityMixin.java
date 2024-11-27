package ca.spottedleaf.moonrise.mixin.random;

import ca.spottedleaf.moonrise.common.util.ThreadUnsafeRandom;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.RandomSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
abstract class EntityMixin {

    /**
     * @reason Changes Entity#random to use ThreadUnsafeRandom, skipping the thread checks and CAS logic
     * @author Spottedleadf
     */
    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;create()Lnet/minecraft/util/RandomSource;"
        )
    )
    private RandomSource redirectEntityRandom() {
        return new ThreadUnsafeRandom(RandomSupport.generateUniqueSeed());
    }
}
