package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.util.MoonriseConstants;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(Options.class)
abstract class OptionsMixin {

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
}
