package ca.spottedleaf.moonrise.mixin.block_counting;

import ca.spottedleaf.moonrise.patches.block_counting.BlockCountingBitStorage;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.util.BitStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BitStorage.class)
interface BitStorageMixin extends BlockCountingBitStorage {

    @Shadow
    int getSize();

    @Shadow
    int get(int i);

    // provide default impl in case mods implement this...
    @Override
    public default Int2ObjectOpenHashMap<ShortArrayList> moonrise$countEntries() {
        final Int2ObjectOpenHashMap<ShortArrayList> ret = new Int2ObjectOpenHashMap<>();

        final int size = this.getSize();
        for (int index = 0; index < size; ++index) {
            final int paletteIdx = this.get(index);
            ret.computeIfAbsent(paletteIdx, (final int key) -> {
                return new ShortArrayList(64);
            }).add((short)index);
        }

        return ret;
    }
}
