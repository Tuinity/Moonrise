package ca.spottedleaf.moonrise.mixin.fluid;

import ca.spottedleaf.moonrise.patches.fluid.FluidFluidState;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MappedRegistry.class)
abstract class MappedRegistryMixin<T> {
    /**
     * @reason Initialise caches
     * @author jpenilla
     */
    @Inject(
        method = "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;",
        at = @At("RETURN")
    )
    private void injectFluidRegister(
        final ResourceKey<?> resourceKey,
        final T object,
        final RegistrationInfo registrationInfo,
        final CallbackInfoReturnable<Holder.Reference<T>> cir
    ) {
        if (resourceKey.registryKey() == (Object) Registries.FLUID) {
            for (final FluidState possibleState : ((Fluid) object).getStateDefinition().getPossibleStates()) {
                ((FluidFluidState) (Object) possibleState).moonrise$initCaches();
            }
        }
    }
}
