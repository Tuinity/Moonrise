package ca.spottedleaf.moonrise.mixin.typed_instance;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.TypedInstance;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.stream.Stream;

@Mixin(FluidState.class)
abstract class FluidStateMixin extends StateHolder<Fluid, FluidState> implements TypedInstance<Fluid> {

    protected FluidStateMixin(final Fluid owner, final Property<?>[] propertyKeys, final Comparable<?>[] propertyValues) {
        super(owner, propertyKeys, propertyValues);
    }


    @Unique
    private Fluid fluidOwner;

    @Unique
    private Holder.Reference<Fluid> reference;

    /**
     * @reason Directly cache Block and Holder.Reference fields
     * @author Spottedleaf
     */
    @Inject(
        method = "<init>",
        at = @At(
            value = "CTOR_HEAD",
            unsafe = true
        )
    )
    private void initTypeRef(final Fluid owner, final Property<?>[] propertyKeys, final Comparable<?>[] propertyValues,
                             final CallbackInfo ci) {
        this.fluidOwner = owner;
        this.reference = owner.builtInRegistryHolder();
    }

    /**
     * @reason Avoid casting
     * @author Spottedleaf
     */
    @Overwrite
    public Fluid getType() {
        return this.fluidOwner;
    }

    @Override
    public final Stream<TagKey<Fluid>> tags() {
        return this.reference.tags();
    }

    @Override
    public final boolean is(final TagKey<Fluid> tag) {
        return this.reference.is(tag);
    }

    @Override
    public final boolean is(final HolderSet<Fluid> set) {
        return set.contains(this.reference);
    }

    @Override
    public final boolean is(final Fluid rawType) {
        return this.fluidOwner == rawType;
    }

    @Override
    public final boolean is(final Holder<Fluid> type) {
        return this.reference == type;
    }

    @Override
    public final boolean is(final ResourceKey<Fluid> type) {
        return this.reference.is(type);
    }
}
