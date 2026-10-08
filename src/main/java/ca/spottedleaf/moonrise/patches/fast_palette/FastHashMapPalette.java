package ca.spottedleaf.moonrise.patches.fast_palette;

import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.world.level.chunk.MissingPaletteEntryException;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PaletteResize;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public final class FastHashMapPalette<T> implements Palette<T> {

    private final T[] valueToIdKeys;
    private final int[] valueToIdValues;
    private final T[] idToValue;
    private final int bits;
    private int size;

    private FastHashMapPalette(final int bits, final List<T> palette) {
        final int mapSize = 1 << (bits + 1);
        this.valueToIdKeys = (T[])new Object[mapSize];
        this.valueToIdValues = new int[mapSize];
        this.idToValue = (T[])new Object[1 << bits];
        this.bits = bits;
        // note: we need to be careful, palette may actually contain duplicates
        for (int i = 0, len = this.size = palette.size(); i < len; ++i) {
            final T value = palette.get(i);

            this.idToValue[i] = value;
            this.insertEntryIfAbsent(value, i);
        }
    }

    private FastHashMapPalette(final FastHashMapPalette<T> other) {
        this.valueToIdKeys = other.valueToIdKeys.clone();
        this.valueToIdValues = other.valueToIdValues.clone();
        this.idToValue = other.idToValue.clone();
        this.bits = other.bits;
        this.size = other.size;
    }

    private void clear() {
        if (this.size != 0) {
            this.size = 0;
            Arrays.fill(this.valueToIdKeys, null);
            Arrays.fill(this.valueToIdValues, 0);
            Arrays.fill(this.idToValue, null);
        }
    }

    // we never replace the value if one is present, since the present
    // value is always < the replacement
    // this allows getEntry(key) to return the same value regardless of whether
    // the linear search is used
    // this does break from behaviour from HashMapPalette (always returns the largest
    // duplicate id), but at the same time it preserves the behaviour for LinearPalette (always
    // returns the smallest duplicate id)
    private void insertEntryIfAbsent(final T key, final int value) {
        final int hash = System.identityHashCode(key);

        final T[] keys = this.valueToIdKeys;
        final int[] values = this.valueToIdValues;

        for (int mask = keys.length - 1, index = hash & mask;; index = (index + 1) & mask) {
            final T entry;
            if ((entry = keys[index]) == null) {
                keys[index] = key;
                values[index] = value;
                return;
            }
            if (entry == key) {
                // value is present
                return;
            }
        }
    }

    private static final int ENTRY_NOT_FOUND = -1;

    private static final int LINEAR_THRESHOLD = 6;

    private int getEntry(final T key) {
        final int size = this.size;
        // note: important that the size check is HERE
        if (size <= LINEAR_THRESHOLD) {
            for (int i = 0; i < size; ++i) {
                if (key == this.idToValue[i]) {
                    return i;
                }
            }
            return ENTRY_NOT_FOUND;
        } else {
            int index = System.identityHashCode(key);

            final T[] keys = this.valueToIdKeys;
            final int[] values = this.valueToIdValues;

            T entry;
            final int mask = keys.length - 1;
            index = index & mask;

            if ((entry = keys[index]) == key) {
                return values[index];
            } else if (entry == null) {
                return ENTRY_NOT_FOUND;
            }

            for (;;) {
                if ((entry = keys[index = (index + 1) & mask]) == key) {
                    return values[index];
                } else if (entry == null) {
                    return ENTRY_NOT_FOUND;
                }
            }
        }
    }

    public static <A> FastHashMapPalette<A> create(final int bits, final List<A> palette) {
        return new FastHashMapPalette<>(bits, palette);
    }

    @Override
    public int idFor(final T value, final PaletteResize<T> onResize) {
        final int ret = this.getEntry(value);
        if (ret != ENTRY_NOT_FOUND) {
            return ret;
        }

        final int nextId = this.size;

        if (nextId < this.idToValue.length) {
            this.idToValue[nextId] = value;
            this.insertEntryIfAbsent(value, nextId);
            ++this.size;
            return nextId;
        }

        return onResize.onResize(this.bits + 1, value);
    }

    @Override
    public boolean maybeHas(final Predicate<T> predicate) {
        final T[] values = this.idToValue;
        for (int i = 0, len = this.size; i < len; ++i) {
            if (predicate.test(values[i])) {
                return true;
            }
        }
        return false;
    }

    public T directValueFor(final int id) {
        return this.idToValue[id];
    }

    @Override
    public T valueFor(final int id) {
        if (id >= 0 && id < this.size) {
            return this.idToValue[id];
        }
        throw new MissingPaletteEntryException(id);
    }

    // note: we need to be very careful here with read and write, as for some reason they differ in behaviour
    //
    //       single value palette does not write the size field
    //       linear and hash palette write the size field


    // note: must be careful, may contain duplicate entries
    private void readLinearOrHash(final FriendlyByteBuf friendlyByteBuf, final IdMap<T> global) {
        this.size = friendlyByteBuf.readVarInt();

        final T[] values = this.idToValue;
        for (int i = 0, len = this.size; i < len; ++i) {
            final T value = global.byIdOrThrow(friendlyByteBuf.readVarInt());

            values[i] = value;
            this.insertEntryIfAbsent(value, i);
        }
    }

    private void writeLinearOrHash(final FriendlyByteBuf friendlyByteBuf, final IdMap<T> global) {
        friendlyByteBuf.writeVarInt(this.size);

        final T[] values = this.idToValue;
        for (int i = 0, len = this.size; i < len; ++i) {
            friendlyByteBuf.writeVarInt(global.getIdOrThrow(values[i]));
        }
    }

    private int writeSizeLinearOrHash(final IdMap<T> global) {
        int ret = VarInt.getByteSize(this.size);

        final T[] values = this.idToValue;
        for (int i = 0, len = this.size; i < len; ++i) {
            ret += VarInt.getByteSize(global.getIdOrThrow(values[i]));
        }

        return ret;
    }

    private void readSingle(final FriendlyByteBuf friendlyByteBuf, final IdMap<T> global) {
        this.size = 1;

        final T value = global.byIdOrThrow(friendlyByteBuf.readVarInt());

        this.idToValue[0] = value;
        this.insertEntryIfAbsent(value, 0);
    }

    private void writeSingle(final FriendlyByteBuf friendlyByteBuf, final IdMap<T> global) {
        friendlyByteBuf.writeVarInt(global.getIdOrThrow(this.idToValue[0]));
    }

    private int writeSizeSingle(final IdMap<T> global) {
        return VarInt.getByteSize(global.getIdOrThrow(this.idToValue[0]));
    }

    @Override
    public void read(final FriendlyByteBuf friendlyByteBuf, final IdMap<T> global) {
        this.clear();
        if (this.bits == 0) {
            this.readSingle(friendlyByteBuf, global);
        } else {
            this.readLinearOrHash(friendlyByteBuf, global);
        }
    }

    @Override
    public void write(final FriendlyByteBuf friendlyByteBuf, final IdMap<T> global) {
        if (this.bits == 0) {
            this.writeSingle(friendlyByteBuf, global);
        } else {
            this.writeLinearOrHash(friendlyByteBuf, global);
        }
    }

    @Override
    public int getSerializedSize(final IdMap<T> global) {
        if (this.bits == 0) {
            return this.writeSizeSingle(global);
        } else {
            return this.writeSizeLinearOrHash(global);
        }
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public FastHashMapPalette<T> copy() {
        return new FastHashMapPalette<>(this);
    }
}
