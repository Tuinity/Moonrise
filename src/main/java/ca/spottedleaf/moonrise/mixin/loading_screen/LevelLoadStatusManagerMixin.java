package ca.spottedleaf.moonrise.mixin.loading_screen;

import net.minecraft.client.multiplayer.LevelLoadStatusManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadStatusManager.class)
public abstract class LevelLoadStatusManagerMixin {

    @Shadow
    private LevelLoadStatusManager.Status status;

    /**
     * @reason Close the loading screen immediately
     * @author Spottedleaf
     */
    @Inject(
            method = "loadingPacketsReceived",
            cancellable = true,
            at = @At(
                    value = "HEAD"
            )
    )
    private void immediatelyClose(final CallbackInfo ci) {
        if (this.status == LevelLoadStatusManager.Status.WAITING_FOR_SERVER) {
            this.status = LevelLoadStatusManager.Status.LEVEL_READY;
            ci.cancel();
        }
    }
}
