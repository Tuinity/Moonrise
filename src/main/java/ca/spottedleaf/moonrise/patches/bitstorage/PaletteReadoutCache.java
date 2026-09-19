package ca.spottedleaf.moonrise.patches.bitstorage;

import ca.spottedleaf.common.util.IntegerUtil;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.chunk.Palette;
import java.util.Arrays;
import java.util.Objects;

public final class PaletteReadoutCache {

    public static final int MAX_BIT_STORAGE_SIZE = 4096;
    public static final int MAX_BIT_STORAGE_BITS = 12;
    static {
        if ((1 << MAX_BIT_STORAGE_BITS) != MAX_BIT_STORAGE_SIZE) {
            throw new IllegalStateException();
        }
    }

    public final Object[] newPalette = new Object[MAX_BIT_STORAGE_SIZE];
    public int newPaletteCount;
    public final long[] copyCache = new long[MAX_BIT_STORAGE_SIZE];
    public final int[] reEncoded = new int[MAX_BIT_STORAGE_SIZE];
    private final int[] newPaletteMapping = new int[MAX_BIT_STORAGE_SIZE];
    private final Int2IntOpenHashMap newPaletteMappingLarge = new Int2IntOpenHashMap(MAX_BIT_STORAGE_SIZE, 0.5f);

    public SimpleBitStorage repack(final int bits, final int size) {
        // note: reEcoded len does not need to be size exactly; it must be > size
        return new SimpleBitStorage(bits, size, this.reEncoded);
    }

    public boolean readout(final BitStorage src, final Palette<?> srcPalette) {
        if (src instanceof ZeroBitStorage || srcPalette.getSize() == 1) {
            return false;
        }

        final int srcSize = src.getSize();

        if (srcSize > MAX_BIT_STORAGE_SIZE) {
            throw new UnsupportedOperationException();
        }
        final long[] srcRaw = src.getRaw();
        final int srcCount = src.getSize();
        final int srcBits = src.getBits();
        final int srcValuesPerElement = (Long.SIZE / srcBits);
        final long srcMask = IntegerUtil.getLongMask(srcBits);

        final long[] cpy = this.copyCache;
        Objects.checkFromToIndex(0, srcRaw.length, cpy.length);

        final int[] newPaletteMapping;
        final Int2IntOpenHashMap newPaletteMappingLarge;
        if ((srcMask + 1) > this.newPaletteMapping.length) {
            newPaletteMapping = null;
            newPaletteMappingLarge = this.newPaletteMappingLarge;

            newPaletteMappingLarge.clear();
        } else {
            newPaletteMapping = this.newPaletteMapping;
            newPaletteMappingLarge = null;

            Arrays.fill(newPaletteMapping, 0, (int)(srcMask + 1), -1);
        }

        final int[] reEncoded = this.reEncoded;
        Objects.checkFromToIndex(0, srcSize, reEncoded.length);

        final Object[] newPalette = this.newPalette;
        Objects.checkFromToIndex(0, srcSize, newPalette.length);

        int rem = srcCount;
        int reEncodeIndex = 0;
        int nextPaletteId = 0;
        for (int i = 0, len = srcRaw.length; i < len; ++i) {
            long value = srcRaw[i];
            cpy[i] = value;

            for (int k = 0, len2 = Math.min(rem, srcValuesPerElement); k < len2; ++k, --rem) {
                final int decoded = (int)(value & srcMask);
                value >>>= srcBits;

                final int mapped = newPaletteMapping != null ? newPaletteMapping[decoded] : newPaletteMappingLarge.getOrDefault(decoded, -1);
                if (mapped != -1) {
                    reEncoded[reEncodeIndex++] = mapped;
                    continue;
                }


                final int computedMapping = nextPaletteId++;
                newPalette[computedMapping] = srcPalette.valueFor(decoded);
                reEncoded[reEncodeIndex++] = computedMapping;

                if (newPaletteMapping != null) {
                    newPaletteMapping[decoded] = computedMapping;
                } else {
                    newPaletteMappingLarge.put(decoded, computedMapping);
                }
            }
        }

        if (rem != 0) {
            throw new IllegalStateException();
        }

        this.newPaletteCount = nextPaletteId;

        return true;
    }
}
