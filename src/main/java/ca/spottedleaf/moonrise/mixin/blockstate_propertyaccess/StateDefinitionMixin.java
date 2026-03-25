package ca.spottedleaf.moonrise.mixin.blockstate_propertyaccess;

import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccessStateHolder;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StateDefinition.class)
abstract class StateDefinitionMixin {

    /**
     * @reason Init state holder tables
     * @author Spottedleaf
     */
    @Inject(
        at = @At(
            value = "RETURN"
        ),
        method = {
            "createSingletonState",
            "createSinglePropertyStates(Ljava/lang/Object;Lnet/minecraft/world/level/block/state/StateDefinition$Factory;Lnet/minecraft/world/level/block/state/properties/Property;)Lcom/google/common/collect/ImmutableList;",
            "createMultiPropertyStates"
        }
    )
    private static <O, S extends StateHolder<O, S>> void initStateTables(final CallbackInfoReturnable<ImmutableList<S>> cir) {
        final ImmutableList<S> states = cir.getReturnValue();
        if (!states.isEmpty()) {
            ((PropertyAccessStateHolder<O, S>)(StateHolder<O, S>)states.get(0)).moonrise$init(states);
        }
    }
}
