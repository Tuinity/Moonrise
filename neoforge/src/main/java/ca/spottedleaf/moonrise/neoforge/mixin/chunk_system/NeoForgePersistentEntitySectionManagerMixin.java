package ca.spottedleaf.moonrise.neoforge.mixin.chunk_system;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PersistentEntitySectionManager.class)
abstract class NeoForgePersistentEntitySectionManagerMixin<T extends EntityAccess> {
    @Inject(
        method = "addNewEntityWithoutEvent",
        at = @At("HEAD")
    )
    private void addNewEntityWithoutEvent(T entity, CallbackInfoReturnable<Boolean> cir) {
        throw new UnsupportedOperationException();
    }

    @Inject(
        method = "addEntityWithoutEvent",
        at = @At("HEAD")
    )
    private void addEntityWithoutEvent(T entity, boolean worldGenSpawned, CallbackInfoReturnable<Boolean> cir) {
        throw new UnsupportedOperationException();
    }
}
