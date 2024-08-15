package ca.spottedleaf.moonrise.mixin.random_ticking;

import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BiomeManager.class)
abstract class BiomeManagerMixin {

    /**
     * @reason Replace floorMod and double division to optimise the function
     * @author Spottedleaf
     */
    @Overwrite
    public static double getFiddle(final long seed) {
        return (double)(((seed >> 24) & (1024 - 1)) - (1024/2)) * (0.9 / 1024.0);
    }
}
