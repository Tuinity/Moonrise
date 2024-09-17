package ca.spottedleaf.moonrise.patches.block_counting;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;

public interface BlockCountingBitStorage {

    public Int2ObjectOpenHashMap<ShortArrayList> moonrise$countEntries();

}
