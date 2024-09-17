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
    private long mask;

    @Shadow
    @Final
    private int size;

    @Override
    public final Int2ObjectOpenHashMap<ShortArrayList> moonrise$countEntries() {
        final int valuesPerLong = this.valuesPerLong;
        final int bits = this.bits;
        final long mask = this.mask;
        final int size = this.size;

        // we may be backed by global palette, so limit bits for init capacity
        final Int2ObjectOpenHashMap<ShortArrayList> ret = new Int2ObjectOpenHashMap<>(
                1 << Math.min(6, bits)
        );

        int index = 0;

        for (long value : this.data) {
            int li = 0;
            do {
                final int paletteIdx = (int)(value & mask);
                value >>= bits;

                ret.computeIfAbsent(paletteIdx, (final int key) -> {
                    return new ShortArrayList(64);
                }).add((short)index);

                ++li;
                ++index;
            } while (li < valuesPerLong && index < size);
        }

        return ret;
    }
}
