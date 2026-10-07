package ca.spottedleaf.moonrise.mixin.block_counting;

import ca.spottedleaf.moonrise.patches.block_counting.BlockCountingBitStorage;
import ca.spottedleaf.moonrise.patches.block_counting.BlockCountingEntry;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.util.BitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.Palette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.ArrayList;
import java.util.List;

@Mixin(ZeroBitStorage.class)
abstract class ZeroBitStorageMixin implements BitStorage, BlockCountingBitStorage {

    @Shadow
    @Final
    private int size;

    @Override
    public final List<BlockCountingEntry> moonrise$countBlocks(final Palette<BlockState> palette) {
        final int size = this.size;

        final BlockState state = palette.valueFor(0);

        final ShortArrayList coordinates;
        if (state.isRandomlyTicking()) {
            final short[] raw = new short[size];
            for (int i = 0; i < size; ++i) {
                raw[i] = (short)i;
            }

            coordinates = ShortArrayList.wrap(raw, size);
        } else {
            coordinates = null;
        }

        final List<BlockCountingEntry> ret = new ArrayList<>(1);
        ret.add(new BlockCountingEntry(size, state, coordinates));
        return ret;
    }
}
