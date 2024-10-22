package ca.spottedleaf.moonrise.mixin.random_ticking;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Biome.class)
abstract class BiomeMixin {

    @Shadow
    protected abstract float getHeightAdjustedTemperature(BlockPos blockPos, int seaLevel);

    /**
     * @reason Cache appears ineffective
     * @author Spottedleaf
     */
    @Overwrite
    public float getTemperature(final BlockPos pos, final int seaLevel) {
        return this.getHeightAdjustedTemperature(pos, seaLevel);
    }
}
