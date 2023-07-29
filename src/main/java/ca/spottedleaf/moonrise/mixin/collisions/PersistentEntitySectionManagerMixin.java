package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.collisions.world.CollisionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin<T extends EntityAccess> {

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
    private void addEntity(final T entityAccess, final boolean onDisk, final CallbackInfoReturnable<Boolean> cir) {
        final Entity entity = (Entity)entityAccess;

        ((CollisionLevel)entity.level()).getCollisionLookup().addEntity(entity);
    }
}
