package ca.spottedleaf.moonrise.mixin.block_counting;

import ca.spottedleaf.moonrise.patches.block_counting.BlockCountingBitStorage;
import ca.spottedleaf.moonrise.patches.block_counting.BlockCountingEntry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.Palette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.ArrayList;
import java.util.List;

@Mixin(BitStorage.class)
interface BitStorageMixin extends BlockCountingBitStorage {

    @Shadow
    int getSize();

    @Shadow
    int get(int i);

    // provide default impl in case mods implement this...
    @Override
    public default List<BlockCountingEntry> moonrise$countBlocks(final Palette<BlockState> palette) {
        final Int2ObjectOpenHashMap<BlockCountingEntry> byPaletteId = new Int2ObjectOpenHashMap<>();
        final List<BlockCountingEntry> ret = new ArrayList<>();

        final int size = this.getSize();
        for (int index = 0; index < size; ++index) {
            final int paletteIdx = this.get(index);

            final BlockCountingEntry ifPresent = byPaletteId.get(paletteIdx);
            if (ifPresent != null) {
                ++ifPresent.count;
                if (ifPresent.coords != null) {
                    ifPresent.coords.add((short)index);
                }
            } else {
                final BlockState state = palette.valueFor(paletteIdx);
                final ShortArrayList coords;
                if (state.isRandomlyTicking()) {
                    coords = new ShortArrayList(64);
                    coords.add((short)index);
                } else {
                    coords = null;
                }

                final BlockCountingEntry entry = new BlockCountingEntry(1, state, coords);
                byPaletteId.put(paletteIdx, entry);
                ret.add(entry);
            }
        }

        return ret;
    }
}
