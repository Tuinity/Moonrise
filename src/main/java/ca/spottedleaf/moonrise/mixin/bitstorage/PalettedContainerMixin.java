package ca.spottedleaf.moonrise.mixin.bitstorage;

import ca.spottedleaf.moonrise.patches.bitstorage.PaletteReadoutCache;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.chunk.Configuration;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PaletteResize;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Mixin(PalettedContainer.class)
abstract class PalettedContainerMixin<T> implements PaletteResize<T>, PalettedContainerRO<T> {

    @Shadow
    public abstract void acquire();

    @Shadow
    public abstract void release();

    @Shadow
    public volatile PalettedContainer.Data<T> data;

    @Unique
    private static final ThreadLocal<PaletteReadoutCache> READOUT_CACHE = ThreadLocal.withInitial(() -> {
        return new PaletteReadoutCache();
    });

    @Unique
    private static final boolean DEBUG_PACKING = false;

    @Unique
    private static <T> void validate(List<T> palette, long[] data, int bits,
                                     BitStorage againstStorage, Palette<T> againstPalette) {
        BitStorage storage = data == null ? new ZeroBitStorage(againstStorage.getSize()) :
            new SimpleBitStorage(bits, againstStorage.getSize(), data);

        for (int i = 0; i < storage.getSize(); ++i) {
            T expect = againstPalette.valueFor(againstStorage.get(i));
            T got = palette.get(storage.get(i));

            if (expect != got) {
                throw new IllegalStateException();
            }
        }
    }

    /**
     * @reason Vanilla unpacks the BitStorage to an int array, which causes very large
     *         allocations. This is particularly egregious since the Vanilla code will
     *         unpack to a raw int array _even when the underlying storage is zero_. This
     *         causes significant allocations for chunk saving.
     *         A secondary objective of this overwrite is to optimise the packing logic.
     * @author Spottedleaf
     */
    @Overwrite
    public PalettedContainerRO.PackedData<T> pack(final Strategy<T> strategy) {
        this.acquire();

        try {
            final PalettedContainer.Data<T> data = this.data;

            final BitStorage storage = data.storage();
            final Palette<T> palette = data.palette();

            final PaletteReadoutCache readoutCache = READOUT_CACHE.get();

            final boolean zero = !readoutCache.readout(storage, palette);

            final int newPaletteSize = zero ? 1 : readoutCache.newPaletteCount;
            final List<T> newPalette = new ArrayList<>(newPaletteSize);
            final long[] dataArray;
            final Configuration configuration = strategy.getConfigurationForPaletteSize(newPaletteSize);
            final int bitsToUse = configuration.bitsInStorage();
            if (zero) {
                newPalette.add(palette.valueFor(0));
                if (bitsToUse == 0) {
                    dataArray = null;
                } else {
                    dataArray = new SimpleBitStorage(bitsToUse, storage.getSize()).getRaw();
                }
            } else {
                if (bitsToUse != 0 && newPaletteSize == palette.getSize() && bitsToUse == storage.getBits()) {
                    // palette and bits are identical, so we can just use the old data

                    final List<T> oldPalette = new ArrayList<>(newPaletteSize);
                    for (int i = 0; i < newPaletteSize; ++i) {
                        oldPalette.add(palette.valueFor(i));
                    }

                    // avoid holding references to palette entries
                    Arrays.fill(readoutCache.newPalette, 0, newPaletteSize, null);

                    // we need to use the copy cache here, as some bad mods may write to the container off-thread which
                    // may leave the palette desynced

                    final long[] cpy = Arrays.copyOf(readoutCache.copyCache, storage.getRaw().length);

                    if (DEBUG_PACKING) {
                        validate(oldPalette, cpy, bitsToUse, storage, palette);
                    }

                    return new PalettedContainerRO.PackedData<>(oldPalette, Optional.of(Arrays.stream(cpy)), bitsToUse);
                } else {
                    final Object[] newPaletteRaw = readoutCache.newPalette;
                    Objects.checkFromToIndex(0, readoutCache.newPaletteCount, newPaletteRaw.length);
                    for (int i = 0, len = readoutCache.newPaletteCount; i < len; ++i) {
                        final T entry = (T)newPaletteRaw[i];
                        // avoid holding references to palette entries
                        newPaletteRaw[i] = null;
                        newPalette.add(entry);
                    }

                    if (bitsToUse == 0) {
                        dataArray = null;
                    } else {
                        dataArray = readoutCache.repack(bitsToUse, storage.getSize()).getRaw();
                    }
                }
            }

            if (DEBUG_PACKING) {
                validate(newPalette, dataArray, bitsToUse, storage, palette);
            }

            // why does this trash use streams...?
            return new PalettedContainerRO.PackedData<>(newPalette, dataArray == null ? Optional.empty() : Optional.of(Arrays.stream(dataArray)), bitsToUse);
        } finally {
            this.release();
        }
    }
}
