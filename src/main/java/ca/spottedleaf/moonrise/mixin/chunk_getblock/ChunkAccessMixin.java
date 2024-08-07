package ca.spottedleaf.moonrise.mixin.chunk_getblock;

import ca.spottedleaf.moonrise.common.util.WorldUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.StructureAccess;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkAccess.class)
abstract class ChunkAccessMixin implements BlockGetter, BiomeManager.NoiseBiomeSource, LightChunk, StructureAccess {

    @Shadow
    @Final
    protected LevelChunkSection[] sections;


    @Unique
    private int minSection;

    @Unique
    private int maxSection;

    /**
     * Initialises the min/max section
     */
    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    public void onConstruct(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor, Registry registry, long l, LevelChunkSection[] levelChunkSections, BlendingData blendingData, CallbackInfo ci) {
        this.minSection = WorldUtil.getMinSection(levelHeightAccessor);
        this.maxSection = WorldUtil.getMaxSection(levelHeightAccessor);
    }

    /**
     * @reason Optimise implementation
     * @author Spottedleaf
     */
    @Override
    @Overwrite
    public Holder<Biome> getNoiseBiome(final int biomeX, final int biomeY, final int biomeZ) {
        int sectionY = (biomeY >> 2) - this.minSection;
        int rel = biomeY & 3;

        final LevelChunkSection[] sections = this.sections;

        if (sectionY < 0) {
            sectionY = 0;
            rel = 0;
        } else if (sectionY >= sections.length) {
            sectionY = sections.length - 1;
            rel = 3;
        }

        return sections[sectionY].getNoiseBiome(biomeX & 3, rel, biomeZ & 3);
    }
}
