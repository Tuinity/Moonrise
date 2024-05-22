package ca.spottedleaf.moonrise.mixin.fluid;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidState.class)
public abstract class FluidStateMixin extends StateHolder<Fluid, FluidState> {

    @Shadow
    public abstract Fluid getType();

    protected FluidStateMixin(Fluid object, Reference2ObjectArrayMap<Property<?>, Comparable<?>> reference2ObjectArrayMap, MapCodec<FluidState> mapCodec) {
        super(object, reference2ObjectArrayMap, mapCodec);
    }

    @Unique
    private int amount;

    @Unique
    private boolean isEmpty;

    @Unique
    private boolean isSource;

    @Unique
    private float ownHeight;

    @Unique
    private boolean isRandomlyTicking;

    @Unique
    private BlockState legacyBlock;

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
        this.isRandomlyTicking = this.getType().isRandomlyTicking();
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

    /**
     * @reason Use cached result, avoiding indirection
     * @author Spottedleaf
     */
    @Overwrite
    public boolean isRandomlyTicking() {
        return this.isRandomlyTicking;
    }
}
