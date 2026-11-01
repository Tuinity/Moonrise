package ca.spottedleaf.moonrise.mixin.snow_generation;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {

    @Unique
    private static final int FEATURES_RADIUS = 1;

    @Inject(
        method = "applyBiomeDecoration",
        at = @At(
            value = "RETURN"
        )
    )
    private void fixSnow(final WorldGenLevel level, final ChunkAccess center,
                         final StructureManager structureManager, final CallbackInfo ci) {
        final ChunkPos centerPos = center.getPos();
        if (SharedConstants.debugVoidTerrain(centerPos)) {
            return;
        }
        if (!PlatformHooks.get().configFixMC119599(level.getLevel())) {
            return;
        }

        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        final int cx = centerPos.x();
        final int cz = centerPos.z();

        // Biome lookups use the same zoom as Vanilla's level.getBiome, which reads noise biomes up to one chunk
        // outside the chunk being processed, so up to 2 chunks from the center. The region only allows status
        // checked access to its direct dependencies (TERRAIN within radius 1), so read the cached chunks directly:
        // FEATURES requires TERRAIN at radius 1, which requires BIOMES at radius 1, so every chunk within
        // radius 2 has completed BIOMES.
        final BiomeManager biomeManager = new BiomeManager((final int quartX, final int quartY, final int quartZ) -> {
            final int chunkX = QuartPos.toSection(quartX);
            final int chunkZ = QuartPos.toSection(quartZ);
            if (Math.max(Math.abs(chunkX - cx), Math.abs(chunkZ - cz)) > (FEATURES_RADIUS + 1)) {
                throw new IllegalStateException("Biome lookup at chunk (" + chunkX + "," + chunkZ + ") is outside radius " + (FEATURES_RADIUS + 1) + " of (" + cx + "," + cz + ")");
            }
            return level.getChunk(chunkX, chunkZ).getNoiseBiome(quartX, quartY, quartZ);
        }, BiomeManager.obfuscateSeed(level.getSeed()));
        for (int dz = -FEATURES_RADIUS; dz <= FEATURES_RADIUS; ++dz) {
            for (int dx = -FEATURES_RADIUS; dx <= FEATURES_RADIUS; ++dx) {
                final int chunkX = dx + cx;
                final int chunkZ = dz + cz;
                final ChunkAccess chunk = level.getChunk(chunkX, chunkZ);

                if (!(chunk instanceof ProtoChunk protoChunk) || (protoChunk instanceof ImposterProtoChunk || protoChunk.isUpgrading())
                    || ((dx != 0 || dz != 0) && protoChunk.getPersistedStatus().isBefore(ChunkStatus.FEATURES))) {
                    continue;
                }

                final int seaLevel = level.getSeaLevel();
                final BlockState defaultSnowBlock = Blocks.SNOW.defaultBlockState();
                for (int lz = 0; lz < 16; ++lz) {
                    for (int lx = 0; lx < 16; ++lx) {
                        final int blockX = (chunkX << 4) | lx;
                        final int blockZ = (chunkZ << 4) | lz;

                        final int heightWithoutLeaves = protoChunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ) + 1;

                        pos.set(blockX, heightWithoutLeaves, blockZ);

                        final Biome biome = biomeManager.getBiome(pos).value();

                        if (biome.getPrecipitationAt(pos, seaLevel) != Biome.Precipitation.SNOW) {
                            continue;
                        }

                        final int heightWithLeaves = protoChunk.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ) + 1;

                        boolean first = true;
                        for (int y = heightWithoutLeaves; y <= heightWithLeaves; ++y) {
                            pos.setY(y);

                            final BlockState current = protoChunk.getBlockState(pos);

                            // note: light is not generated here (TERRAIN), so really it's 0...
                            if (!current.isAir() || !defaultSnowBlock.canSurvive(level, pos)) {
                                continue;
                            }

                            level.setBlock(pos, defaultSnowBlock, Block.UPDATE_CLIENTS);

                            if (first) {
                                first = false;
                                pos.setY(y - 1);
                                final BlockState below = protoChunk.getBlockState(pos);
                                if (below.hasProperty(SnowyBlock.SNOWY)) {
                                    level.setBlock(pos, below.setValue(SnowyBlock.SNOWY, true), Block.UPDATE_CLIENTS);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
