package ca.spottedleaf.moonrise.mixin.chunk_gen;

import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Climate.RTree.Node.class)
abstract class Climate$RTree$NodeMixin {

    @Shadow
    @Final
    protected Climate.Parameter[] parameterSpace;

    /**
     * @reason Optimise
     * @author Spottedleaf
     */
    @Overwrite
    public long distance(final long[] target) {
        final Climate.Parameter[] params = this.parameterSpace;

        if (params.length < 7 || target.length < 7) {
            throw new IndexOutOfBoundsException();
        }

        long distance = 0L;

        for (int i = 0; i < 7; ++i) {
            distance += Mth.square(params[i].distance(target[i]));
        }

        return distance;
    }
}
