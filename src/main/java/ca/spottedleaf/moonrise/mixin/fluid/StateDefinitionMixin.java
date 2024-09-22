package ca.spottedleaf.moonrise.mixin.fluid;

import ca.spottedleaf.moonrise.patches.fluid.FluidFluidState;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StateDefinition.class)
abstract class StateDefinitionMixin<S> {
    @Shadow @Final private ImmutableList<S> states;

    /**
     * @reason Initialise caches
     * @author jpenilla
     */
    @Inject(
        method = "<init>",
        at = @At("RETURN")
    )
    void injectInit(final CallbackInfo ci) {
        for (final S state : this.states) {
            if (!(state instanceof FluidFluidState fluidState)) {
                return;
            }
            fluidState.moonrise$initCaches();
        }
    }
}
