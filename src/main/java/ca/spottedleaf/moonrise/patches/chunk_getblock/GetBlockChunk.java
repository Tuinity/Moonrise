package ca.spottedleaf.moonrise.patches.chunk_getblock;

import net.minecraft.world.level.block.state.BlockState;

public interface GetBlockChunk {

    public BlockState moonrise$getBlock(final int x, final int y, final int z);

}
