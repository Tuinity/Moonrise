package ca.spottedleaf.moonrise.mixin.explosions;

import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Level.class)
public abstract class ExplosionProfileMixin implements LevelAccessor, AutoCloseable {

    @Unique
    private long time;
    @Unique
    private int count;

    @Redirect(
            method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;ZLnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/sounds/SoundEvent;)Lnet/minecraft/world/level/Explosion;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Explosion;explode()V"
            )
    )
    private void aa(Explosion instance) {
        long start = System.nanoTime();
        instance.explode();
        long end = System.nanoTime();
        ++this.count;
        this.time += (end - start);
    }

    /**
     * @author Spottedleaf
     * @reason print profile info
     */
    @Overwrite
    @Override
    public void close() throws Exception {
        this.getChunkSource().close();
        System.out.println("expl: count: " + this.count + ", time: " + this.time + ", ms per explode: " + (double)this.time / (double)this.count * 1.0E-6);
    }
}
