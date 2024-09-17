package ca.spottedleaf.moonrise.patches.block_counting;

import ca.spottedleaf.moonrise.common.list.ShortList;

public interface BlockCountingChunkSection {

    public boolean moonrise$hasSpecialCollidingBlocks();

    public ShortList moonrise$getTickingBlockList();

}
