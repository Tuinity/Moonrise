package ca.spottedleaf.moonrise.patches.block_counting;

import ca.spottedleaf.moonrise.common.list.ShortList;

public interface BlockCountingChunkSection {

    public boolean moonrise$hasSpecialCollidingBlocks();

    public ShortList moonrise$getTickingBlockList();

    public boolean moonrise$hasFluids();

    public boolean moonrise$hasLavaFluids();

    public boolean moonrise$hasWaterFluids();

}
