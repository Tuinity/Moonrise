package ca.spottedleaf.moonrise.patches.block_counting;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockCountingEntry {

    public int count;
    public final BlockState state;
    public final ShortArrayList coords;

    public BlockCountingEntry(final int count, final BlockState state, final ShortArrayList coords) {
        this.count = count;
        this.state = state;
        this.coords = coords;
    }
}
