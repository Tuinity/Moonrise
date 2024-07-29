package ca.spottedleaf.moonrise.mixin.loading_screen;

import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReceivingLevelScreen.class)
public abstract class ReceivingLevelScreenMixin extends Screen {

    @Shadow
    public abstract void onClose();

    protected ReceivingLevelScreenMixin(Component component) {
        super(component);
    }

    /**
     * @reason Close the loading screen immediately
     * @author Spottedleaf
     */
    @Inject(
            method = "tick",
            cancellable = true,
            at = @At(
                    value = "HEAD"
            )
    )
    private void immediatelyClose(final CallbackInfo ci) {
        this.onClose();
        ci.cancel();
    }

}
