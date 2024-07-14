package ca.spottedleaf.moonrise.patches.block_counting;

import ca.spottedleaf.moonrise.common.list.IBlockDataList;

public interface BlockCountingChunkSection {

    public int moonrise$getSpecialCollidingBlocks();

    public IBlockDataList moonrise$getTickingBlockList();

}
