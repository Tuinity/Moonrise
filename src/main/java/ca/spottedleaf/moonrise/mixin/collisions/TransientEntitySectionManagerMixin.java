package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.collisions.world.CollisionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TransientEntitySectionManager.class)
public abstract class TransientEntitySectionManagerMixin<T extends EntityAccess> {

    /**
     * @reason Hook into our entity slices
     * @author Spottedleaf
     */
    @Inject(
            method = "addEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/EntitySection;add(Lnet/minecraft/world/level/entity/EntityAccess;)V"
            )
    )
    private void addEntity(final T entityAccess, final CallbackInfo ci) {
        final Entity entity = (Entity)entityAccess;

        ((CollisionLevel)entity.level()).moonrise$getCollisionLookup().addEntity(entity);
    }
}
