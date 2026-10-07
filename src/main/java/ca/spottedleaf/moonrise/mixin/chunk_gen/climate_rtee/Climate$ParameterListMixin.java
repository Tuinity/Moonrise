package ca.spottedleaf.moonrise.mixin.chunk_gen.climate_rtee;

import ca.spottedleaf.moonrise.patches.chunk_gen.climate_rtree.ChunkGenRTree;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Climate.ParameterList.class)
abstract class Climate$ParameterListMixin<T> {

    @Shadow
    @Final
    private Climate.RTree<T> index;

    @Shadow
    public abstract T findValueIndex(final Climate.TargetPoint target);

    @Unique
    private static final boolean DEBUG_PARAM_LOOKUP = false;

    /**
     * @reason Route to optimised RTree lookup
     * @author Spottedleaf
     */
    @Overwrite
    public T findValue(final Climate.TargetPoint target) {
        final T moonrise = ((ChunkGenRTree<T>)(Object)this.index).moonrise$findNearest(target);
        if (!DEBUG_PARAM_LOOKUP) {
            return moonrise;
        }
        final T vanilla = this.findValueIndex(target);
        if (vanilla != moonrise) {
            throw new IllegalStateException();
        }
        return vanilla;
    }
}
