package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.collisions.world.CollisionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TransientEntitySectionManager.Callback.class)
public abstract class TransientEntitySectionManagerCallbackMixin<T extends EntityAccess> {

    @Shadow
    @Final
    private T entity;

    @Shadow
    private long currentSectionKey;

    /**
     * @reason Hook into our entity slices
     * @author Spottedleaf
     */
    @Inject(
            method = "onMove",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/EntitySection;remove(Lnet/minecraft/world/level/entity/EntityAccess;)Z"
            )
    )
    private void changeSections(final CallbackInfo ci) {
        final Entity entity = (Entity)this.entity;

        final long currentChunk = this.currentSectionKey;

        ((CollisionLevel)entity.level()).getCollisionLookup().moveEntity(entity, currentChunk);
    }

    /**
     * @reason Hook into our entity slices
     * @author Spottedleaf
     */
    @Inject(
            method = "onRemove",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/EntitySection;remove(Lnet/minecraft/world/level/entity/EntityAccess;)Z"
            )
    )
    private void onRemoved(final CallbackInfo ci) {
        final Entity entity = (Entity)this.entity;

        ((CollisionLevel)entity.level()).getCollisionLookup().removeEntity(entity);
    }
}
