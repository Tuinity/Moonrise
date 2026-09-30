package ca.spottedleaf.moonrise.mixin.typed_instance;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.TypedInstance;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.stream.Stream;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin extends StateHolder<Block, BlockState> implements TypedInstance<Block> {

    protected BlockStateBaseMixin(final Block owner, final Property<?>[] propertyKeys, final Comparable<?>[] propertyValues) {
        super(owner, propertyKeys, propertyValues);
    }

    @Unique
    private Block blockOwner;

    @Unique
    private Holder.Reference<Block> reference;

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
    private void initTypeRef(final Block owner, final Property<?>[] propertyKeys, final Comparable<?>[] propertyValues,
                             final CallbackInfo ci) {
        this.blockOwner = owner;
        this.reference = owner.builtInRegistryHolder();
    }

    /**
     * @reason Avoid cast
     * @author Spottedleaf
     */
    @Overwrite
    public Block getBlock() {
        return this.blockOwner;
    }

    @Override
    public final Stream<TagKey<Block>> tags() {
        return this.reference.tags();
    }

    @Override
    public final boolean is(final TagKey<Block> tag) {
        return this.reference.is(tag);
    }

    @Override
    public final boolean is(final HolderSet<Block> set) {
        return set.contains(this.reference);
    }

    @Override
    public final boolean is(final Block rawType) {
        return this.blockOwner == rawType;
    }

    @Override
    public final boolean is(final Holder<Block> type) {
        return this.reference == type;
    }

    @Override
    public final boolean is(final ResourceKey<Block> type) {
        return this.reference.is(type);
    }
}
