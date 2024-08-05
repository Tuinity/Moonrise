package ca.spottedleaf.moonrise.mixin.farm_block;

import ca.spottedleaf.moonrise.patches.chunk_getblock.GetBlockChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(FarmBlock.class)
public abstract class FarmBlockMixin {

	// TODO: NeoForge - APIs this method calls require a BlockPos, so is there much advantage to not using betweenClosed anymore?
    /**
     * @reason Avoid usage of betweenClosed, this can become very hot when
     *         there are significant numbers of farm blocks in the world
     * @author Spottedleaf
     */
    @Overwrite
    public static boolean isNearWater(final LevelReader world, final BlockPos pos) {
        final ChunkSource chunkCache = ((Level)world).getChunkSource();
        final int xOff = pos.getX();
        final int yOff = pos.getY();
        final int zOff = pos.getZ();

        for (int dz = -4; dz <= 4; ++dz) {
            final int z = dz + zOff;
            for (int dx = -4; dx <= 4; ++dx) {
                final int x = xOff + dx;
                final LevelChunk chunk = (LevelChunk)chunkCache.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
                for (int dy = 0; dy <= 1; ++dy) {
                    final int y = dy + yOff;
                    final FluidState fluid = ((GetBlockChunk)chunk).moonrise$getBlock(x, y, z).getFluidState();
                    if (!fluid.isEmpty() && fluid.is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
