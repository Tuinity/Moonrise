package ca.spottedleaf.moonrise.mixin.fluid;

import ca.spottedleaf.moonrise.patches.fluids.FluidClassification;
import ca.spottedleaf.moonrise.patches.fluids.FluidFluidState;
import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.EmptyFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import net.minecraft.world.level.material.WaterFluid;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidState.class)
public abstract class FluidStateMixin extends StateHolder<Fluid, FluidState> implements FluidFluidState {

    @Shadow
    public abstract Fluid getType();

    protected FluidStateMixin(Fluid object, ImmutableMap<Property<?>, Comparable<?>> immutableMap, MapCodec<FluidState> mapCodec) {
        super(object, immutableMap, mapCodec);
    }

    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    @Unique
    private int amount;

    @Unique
    private boolean isEmpty;

    @Unique
    private boolean isSource;

    @Unique
    private float ownHeight;

    @Unique
    private BlockState legacyBlock;

    @Unique
    private FluidClassification classification;

    /**
     * @reason Initialise caches
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void init(final CallbackInfo ci) {
        this.amount = this.getType().getAmount((FluidState)(Object)this);
        this.isEmpty = this.getType().isEmpty();
        this.isSource = this.getType().isSource((FluidState)(Object)this);
        this.ownHeight = this.getType().getOwnHeight((FluidState)(Object)this);

        if (this.getType() instanceof EmptyFluid) {
            this.classification = FluidClassification.EMPTY;
        } else if (this.getType() instanceof LavaFluid) {
            this.classification = FluidClassification.LAVA;
        } else if (this.getType() instanceof WaterFluid) {
            this.classification = FluidClassification.WATER;
        }

        if (this.classification == null) {
            LOGGER.error("Unknown fluid classification " + this.getClass().getName());
        }
    }


    /**
     * @reason Use cached result, avoiding indirection
     * @author Spottedleaf
     */
    @Overwrite
    public int getAmount() {
        return this.amount;
    }

    /**
     * @reason Use cached result, avoiding indirection
     * @author Spottedleaf
     */
    @Overwrite
    public boolean isEmpty() {
        return this.isEmpty;
    }

    /**
     * @reason Use cached result, avoiding indirection
     * @author Spottedleaf
     */
    @Overwrite
    public boolean isSource() {
        return this.isSource;
    }

    /**
     * @reason Use cached result, avoiding indirection
     * @author Spottedleaf
     */
    @Overwrite
    public float getOwnHeight() {
        return this.ownHeight;
    }

    /**
     * @reason Use cached result, avoiding indirection
     * @author Spottedleaf
     */
    @Overwrite
    public boolean isSourceOfType(final Fluid other) {
        return this.isSource && this.owner == other;
    }

    /**
     * @reason Use cached result, avoiding indirection
     * @author Spottedleaf
     */
    @Overwrite
    public BlockState createLegacyBlock() {
        if (this.legacyBlock != null) {
            return this.legacyBlock;
        }
        return this.legacyBlock = this.getType().createLegacyBlock((FluidState)(Object)this);
    }

    @Override
    public final FluidClassification getClassification() {
        return this.classification;
    }
}
