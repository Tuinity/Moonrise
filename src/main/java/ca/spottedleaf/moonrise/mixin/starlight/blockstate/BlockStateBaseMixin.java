package ca.spottedleaf.moonrise.mixin.starlight.blockstate;

import ca.spottedleaf.moonrise.patches.starlight.blockstate.StarlightAbstractBlockState;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseMixin extends StateHolder<Block, BlockState> implements StarlightAbstractBlockState {

    protected BlockStateBaseMixin(Block object, Reference2ObjectArrayMap<Property<?>, Comparable<?>> reference2ObjectArrayMap, MapCodec<BlockState> mapCodec) {
        super(object, reference2ObjectArrayMap, mapCodec);
    }

    @Shadow
    @Final
    private boolean useShapeForLightOcclusion;

    @Shadow
    @Final
    private boolean canOcclude;


    @Unique
    private boolean isConditionallyFullOpaque;

    @Override
    public final boolean starlight$isConditionallyFullOpaque() {
        return this.isConditionallyFullOpaque;
    }

    /**
     * @reason Initialises our light state for this block.
     * @author Spottedleaf
     */
    @Inject(
            method = "initCache",
            at = @At(
                value = "RETURN"
            )
    )
    public void initLightAccessState(final CallbackInfo ci) {
        this.isConditionallyFullOpaque = this.canOcclude & this.useShapeForLightOcclusion;
    }
}
