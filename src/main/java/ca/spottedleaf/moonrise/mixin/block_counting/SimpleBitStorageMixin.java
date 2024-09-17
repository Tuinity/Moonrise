package ca.spottedleaf.moonrise.mixin.block_counting;

import ca.spottedleaf.moonrise.patches.block_counting.BlockCountingBitStorage;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

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
    public final Int2ObjectOpenHashMap<ShortArrayList> moonrise$countEntries() {
        final int valuesPerLong = this.valuesPerLong;
        final int bits = this.bits;
        final long mask = (1L << bits) - 1L;
        final int size = this.size;

        if (bits <= 6) {
            final ShortArrayList[] byId = new ShortArrayList[1 << bits];
            final Int2ObjectOpenHashMap<ShortArrayList> ret = new Int2ObjectOpenHashMap<>(1 << bits);

            int index = 0;

            for (long value : this.data) {
                int li = 0;
                do {
                    final int paletteIdx = (int)(value & mask);
                    value >>= bits;
                    ++li;

                    final ShortArrayList coords = byId[paletteIdx];
                    if (coords != null) {
                        coords.add((short)index++);
                        continue;
                    } else {
                        final ShortArrayList newCoords = new ShortArrayList(64);
                        byId[paletteIdx] = newCoords;
                        newCoords.add((short)index++);
                        ret.put(paletteIdx, newCoords);
                        continue;
                    }
                } while (li < valuesPerLong && index < size);
            }

            return ret;
        } else {
            final Int2ObjectOpenHashMap<ShortArrayList> ret = new Int2ObjectOpenHashMap<>(
                1 << 6
            );

            int index = 0;

            for (long value : this.data) {
                int li = 0;
                do {
                    final int paletteIdx = (int)(value & mask);
                    value >>= bits;
                    ++li;

                    ret.computeIfAbsent(paletteIdx, (final int key) -> {
                        return new ShortArrayList(64);
                    }).add((short)index++);
                } while (li < valuesPerLong && index < size);
            }

            return ret;
        }
    }
}
