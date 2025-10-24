package ca.spottedleaf.moonrise.fabric.mixin.chunk_system;

import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Level.class)
abstract class FabricLevelMixin {
    /**
     * @reason Allow block updates in non-ticking chunks, as new chunk system sends non-ticking chunks to clients
     * @author Spottedleaf
     */
    @Redirect(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/FullChunkStatus;isOrAfter(Lnet/minecraft/server/level/FullChunkStatus;)Z"
        )
    )
    private boolean sendUpdatesForFullChunks(final FullChunkStatus instance,
        final FullChunkStatus fullChunkStatus) {

        return instance.isOrAfter(FullChunkStatus.FULL);
    }
}
