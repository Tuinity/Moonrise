package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.util.MoonriseConstants;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
abstract class OptionsMixin {

    @Shadow
    public boolean syncWrites;

    /**
     * @reason Allow higher view distances
     * @author Spottedleaf
     */
    @ModifyConstant(
            method = "<init>",
            constant = @Constant(
                    intValue = 32, ordinal = 1
            )
    )
    private int replaceViewDistanceConstant(final int constant) {
        return MoonriseConstants.MAX_VIEW_DISTANCE;
    }

    /**
     * @reason Allow higher view distances
     * @author Spottedleaf
     */
    @ModifyConstant(
            method = "<init>",
            constant = @Constant(
                    intValue = 32, ordinal = 2
            )
    )
    private int replaceSimulationDistanceConstant(final int constant) {
        return MoonriseConstants.MAX_VIEW_DISTANCE;
    }

    /**
     * @reason Default sync chunk writes to disabled (vanilla enables them on Windows), as the option is replaced
     *         by our config option. Applied before options.txt is loaded, so an existing value is kept.
     * @author jpenilla
     */
    @Inject(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Options;load()V"
        )
    )
    private void disableSyncWritesByDefault(final CallbackInfo ci) {
        this.syncWrites = false;
    }
}
