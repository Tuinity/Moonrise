package ca.spottedleaf.moonrise.patches.block_counting;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;

public interface BlockCountingBitStorage {

    public Int2ObjectOpenHashMap<IntArrayList> moonrise$countEntries();

}
