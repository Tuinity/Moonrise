package ca.spottedleaf.moonrise.mixin.block_counting;

import ca.spottedleaf.moonrise.patches.block_counting.BlockCountingBitStorage;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.util.BitStorage;
import net.minecraft.util.ZeroBitStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ZeroBitStorage.class)
abstract class ZeroBitStorageMixin implements BitStorage, BlockCountingBitStorage {

    @Shadow
    @Final
    private int size;

    @Override
    public final Int2ObjectOpenHashMap<ShortArrayList> moonrise$countEntries() {
        final int size = this.size;

        final short[] raw = new short[size];
        for (int i = 0; i < size; ++i) {
            raw[i] = (short)i;
        }

        final ShortArrayList coordinates = ShortArrayList.wrap(raw, size);

        final Int2ObjectOpenHashMap<ShortArrayList> ret = new Int2ObjectOpenHashMap<>(1);
        ret.put(0, coordinates);
        return ret;
    }
}
