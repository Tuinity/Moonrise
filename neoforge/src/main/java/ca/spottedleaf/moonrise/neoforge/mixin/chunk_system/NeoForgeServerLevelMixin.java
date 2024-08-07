package ca.spottedleaf.moonrise.neoforge.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
abstract class NeoForgeServerLevelMixin implements ChunkSystemLevel {

    /**
     * @reason Redirect to new entity manager
     * @author Spottedleaf
     */
    @Redirect(
        method = "addPlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;addNewEntityWithoutEvent(Lnet/minecraft/world/level/entity/EntityAccess;)Z"
        )
    )
    private <T extends EntityAccess> boolean redirectAddPlayerEntity(final PersistentEntitySectionManager<T> instance, final T entity) {
        return this.moonrise$getEntityLookup().addNewEntity((Entity)entity, false);
    }
}
