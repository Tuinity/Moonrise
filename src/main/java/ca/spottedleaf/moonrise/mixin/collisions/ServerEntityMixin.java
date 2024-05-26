package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin {

    @Shadow
    @Final
    private Entity entity;

    @Shadow
    private int teleportDelay;

    /**
     * @reason Position errors on hard colliding entities can cause collision issues on boats. To fix this, force any position
     * updates to use teleport which uses full precision.
     * @author Spottedleaf
     */
    @Inject(
            method = "sendChanges",
            at = @At(
                    value = "HEAD"
            )
    )
    private void forceHardCollideTeleport(final CallbackInfo ci) {
        if (((ChunkSystemEntity)this.entity).moonrise$isHardColliding()) {
            this.teleportDelay = 9999;
        }
    }
}
