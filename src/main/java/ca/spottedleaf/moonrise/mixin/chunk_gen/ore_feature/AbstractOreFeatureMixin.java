package ca.spottedleaf.moonrise.mixin.chunk_gen.ore_feature;

import net.minecraft.world.level.levelgen.feature.AbstractOreFeature;
import net.minecraft.world.level.levelgen.feature.BlockReplacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Arrays;
import java.util.List;

@Mixin(AbstractOreFeature.class)
abstract class AbstractOreFeatureMixin {

    @Shadow
    @Final
    @Mutable
    protected List<BlockReplacement> targetStates;

    /**
     * @reason Avoid type indirection for iteration on targetStates
     * @author Spottedleaf
     */
    @Inject(
        method = "<init>",
        at = @At(
            value = "RETURN"
        )
    )
    private void useSingleListImpl(final List<BlockReplacement> targetStates, final int size,
                                   final float discardChanceOnAirExposure,
                                   final CallbackInfo ci) {
        final BlockReplacement[] array = targetStates.toArray(new BlockReplacement[0]);
        this.targetStates = Arrays.asList(array);
    }
}
