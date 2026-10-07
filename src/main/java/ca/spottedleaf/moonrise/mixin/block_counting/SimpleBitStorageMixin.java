package ca.spottedleaf.moonrise.mixin.block_counting;

import ca.spottedleaf.moonrise.patches.block_counting.BlockCountingBitStorage;
import ca.spottedleaf.moonrise.patches.block_counting.BlockCountingEntry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.Palette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.ArrayList;
import java.util.List;

@Mixin(SimpleBitStorage.class)
abstract class SimpleBitStorageMixin implements BitStorage, BlockCountingBitStorage {

    @Shadow
    @Final
    private long[] data;

    @Shadow
    @Final
    private int valuesPerLong;

    @Shadow
    @Final
    private int bits;

    @Shadow
    @Final
    private int size;

    @Override
    public final List<BlockCountingEntry> moonrise$countBlocks(final Palette<BlockState> palette) {
        final int valuesPerLong = this.valuesPerLong;
        final int bits = this.bits;
        final long mask = (1L << bits) - 1L;
        final int size = this.size;

        if (bits <= 6) {
            final BlockCountingEntry[] byPaletteId = new BlockCountingEntry[1 << bits];
            final List<BlockCountingEntry> ret = new ArrayList<>(1 << bits);

            int index = 0;

            for (long value : this.data) {
                int li = 0;
                do {
                    final int paletteIdx = (int)(value & mask);
                    value >>= bits;
                    ++li;

                    final BlockCountingEntry ifPresent = byPaletteId[paletteIdx];
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
                        byPaletteId[paletteIdx] = entry;
                        ret.add(entry);
                    }
                    ++index;
                } while (li < valuesPerLong && index < size);
            }

            return ret;
        } else {
            final List<BlockCountingEntry> ret = new ArrayList<>(1 << bits);
            final Int2ObjectOpenHashMap<BlockCountingEntry> byPaletteId = new Int2ObjectOpenHashMap<>(
                1 << 6
            );

            int index = 0;

            for (long value : this.data) {
                int li = 0;
                do {
                    final int paletteIdx = (int)(value & mask);
                    value >>= bits;
                    ++li;

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
                    ++index;
                } while (li < valuesPerLong && index < size);
            }

            return ret;
        }
    }
}
