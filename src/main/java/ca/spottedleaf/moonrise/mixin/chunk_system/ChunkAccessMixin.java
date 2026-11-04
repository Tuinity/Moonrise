package ca.spottedleaf.moonrise.mixin.chunk_system;

import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkAccess.class)
abstract class ChunkAccessMixin {

    @Shadow
    private volatile boolean unsaved;

    @Shadow
    public abstract void markUnsaved();

    @Inject(
        method = "incrementInhabitedTime",
        at = @At("TAIL")
    )
    private void markUnsavedOnInhabitedTime(final CallbackInfo ci) {
        if (!this.unsaved) {
            this.markUnsaved();
        }
    }
}
