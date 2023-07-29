package ca.spottedleaf.moonrise.mixin.fluid;

import ca.spottedleaf.moonrise.patches.fluids.FluidClassification;
import ca.spottedleaf.moonrise.patches.fluids.FluidFluid;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.material.EmptyFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.LavaFluid;
import net.minecraft.world.level.material.WaterFluid;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Fluid.class)
public abstract class FluidMixin implements FluidFluid {

    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    @Unique
    private FluidClassification classification;

    /**
     * @reason Init caches
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void init(final CallbackInfo ci) {
        if ((Object)this instanceof EmptyFluid) {
            this.classification = FluidClassification.EMPTY;
        } else if ((Object)this instanceof LavaFluid) {
            this.classification = FluidClassification.LAVA;
        } else if ((Object)this instanceof WaterFluid) {
            this.classification = FluidClassification.WATER;
        }

        if (this.classification == null) {
            LOGGER.error("Unknown fluid classification " + this.getClass().getName());
        }
    }

    @Override
    public FluidClassification getClassification() {
        return this.classification;
    }
}
