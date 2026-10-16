package ca.spottedleaf.moonrise.mixin.chunk_gen.use_biome_palette;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelChunkSection.class)
abstract class LevelChunkSectionMixin {

    @Shadow
    private PalettedContainerRO<Holder<Biome>> biomes;

    /**
     * @reason Seed the palette with the biome at (0, 0, 0) and fill every other position once, so that
     *         every palette entry is present in the generated section. This also keeps uniform sections
     *         in a single-value palette instead of retaining an unused default biome.
     *         Sampling order must match Vanilla, as the RTree caches the last result (MC-241546).
     * @author jpenilla
     */
    @Overwrite
    public void fillBiomesFromNoise(final BiomeResolver biomeResolver, final Climate.Sampler sampler,
                                   final int quartMinX, final int quartMinY, final int quartMinZ) {
        final Holder<Biome> first = biomeResolver.getNoiseBiome(quartMinX, quartMinY, quartMinZ, sampler);
        final Strategy<Holder<Biome>> strategy = ((PalettedContainer<Holder<Biome>>)this.biomes).strategy;
        final PalettedContainer<Holder<Biome>> newBiomes = new PalettedContainer<>(first, strategy);

        for (int x = 0; x < 4; ++x) {
            for (int y = 0; y < 4; ++y) {
                // Skip (0, 0, 0): the initial value already fills it, so do not sample it again.
                final int startZ = x == 0 && y == 0 ? 1 : 0;
                for (int z = startZ; z < 4; ++z) {
                    // Avoid reading the old value.
                    newBiomes.set(
                        (y << 4) | (z << 2) | x,
                        biomeResolver.getNoiseBiome(quartMinX + x, quartMinY + y, quartMinZ + z, sampler)
                    );
                }
            }
        }

        this.biomes = newBiomes;
    }
}
