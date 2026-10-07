package ca.spottedleaf.moonrise.patches.block_counting;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.Palette;
import java.util.List;

public interface BlockCountingBitStorage {

    public List<BlockCountingEntry> moonrise$countBlocks(final Palette<BlockState> palette);

}
