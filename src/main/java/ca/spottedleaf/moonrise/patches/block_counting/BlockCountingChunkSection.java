package ca.spottedleaf.moonrise.patches.block_counting;

import ca.spottedleaf.moonrise.common.list.IntList;

public interface BlockCountingChunkSection {

    public boolean moonrise$hasSpecialCollidingBlocks();

    public IntList moonrise$getTickingBlockList();

}
