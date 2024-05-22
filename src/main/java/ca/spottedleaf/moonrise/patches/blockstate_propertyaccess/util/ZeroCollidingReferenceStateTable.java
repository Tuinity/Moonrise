package ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.util;

import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccess;
import com.google.common.collect.Table;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ZeroCollidingReferenceStateTable {

    // upper 32 bits: starting index
    // lower 32 bits: bitset for contained ids
    private final long[] this_index_table;
    private final Comparable<?>[] this_table;
    private final StateHolder<?, ?> this_state;

    private long[] index_table;
    private StateHolder<?, ?>[][] value_table;

    private boolean inited;

    public ZeroCollidingReferenceStateTable(final StateHolder<?, ?> state, final Map<Property<?>, Comparable<?>> this_map) {
        this.this_state = state;
        this.this_index_table = this.create_table(this_map.keySet());

        int max_id = -1;
        for (final Property<?> property : this_map.keySet()) {
            final int id = lookup_vindex(property, this.this_index_table);
            if (id > max_id) {
                max_id = id;
            }
        }

        this.this_table = new Comparable[max_id + 1];
        for (final Map.Entry<Property<?>, Comparable<?>> entry : this_map.entrySet()) {
            this.this_table[lookup_vindex(entry.getKey(), this.this_index_table)] = entry.getValue();
        }
    }

    public void loadInTable(final Table<Property<?>, Comparable<?>, StateHolder<?, ?>> table,
                            final Map<Property<?>, Comparable<?>> this_map) {
        if (this.inited) {
            throw new IllegalStateException();
        }
        this.inited = true;
        final Set<Property<?>> combined = new HashSet<>(table.rowKeySet());
        combined.addAll(this_map.keySet());

        this.index_table = this.create_table(combined);

        int max_id = -1;
        for (final Property<?> property : combined) {
            final int id = lookup_vindex(property, this.index_table);
            if (id > max_id) {
                max_id = id;
            }
        }

        this.value_table = new StateHolder[max_id + 1][];

        final Map<Property<?>, Map<Comparable<?>, StateHolder<?, ?>>> map = table.rowMap();
        for (final Property<?> property : map.keySet()) {
            final Map<Comparable<?>, StateHolder<?, ?>> propertyMap = map.get(property);

            final int id = lookup_vindex(property, this.index_table);
            final StateHolder<?, ?>[] states = this.value_table[id] = new StateHolder[property.getPossibleValues().size()];

            for (final Map.Entry<Comparable<?>, StateHolder<?, ?>> entry : propertyMap.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }

                states[((PropertyAccess)property).moonrise$getIdFor(entry.getKey())] = entry.getValue();
            }
        }


        for (final Map.Entry<Property<?>, Comparable<?>> entry : this_map.entrySet()) {
            final Property<?> property = entry.getKey();
            final int index = lookup_vindex(property, this.index_table);

            if (this.value_table[index] == null) {
                this.value_table[index] = new StateHolder[property.getPossibleValues().size()];
            }

            this.value_table[index][((PropertyAccess)property).moonrise$getIdFor(entry.getValue())] = this.this_state;
        }
    }


    protected long[] create_table(final Collection<Property<?>> collection) {
        int max_id = -1;
        for (final Property<?> property : collection) {
            final int id = ((PropertyAccess)property).moonrise$getId();
            if (id > max_id) {
                max_id = id;
            }
        }

        final long[] ret = new long[((max_id + 1) + 31) >>> 5]; // ceil((max_id + 1) / 32)

        for (final Property<?> property : collection) {
            final int id = ((PropertyAccess)property).moonrise$getId();

            ret[id >>> 5] |= (1L << (id & 31));
        }

        int total = 0;
        for (int i = 1, len = ret.length; i < len; ++i) {
            ret[i] |= (long)(total += Long.bitCount(ret[i - 1] & 0xFFFFFFFFL)) << 32;
        }

        return ret;
    }

    public Comparable<?> get(final Property<?> state) {
        final Comparable<?>[] table = this.this_table;
        final int index = lookup_vindex(state, this.this_index_table);

        if (index < 0 || index >= table.length) {
            return null;
        }
        return table[index];
    }

    public StateHolder<?, ?> get(final Property<?> property, final Comparable<?> with) {
        final int index = lookup_vindex(property, this.index_table);
        final StateHolder<?, ?>[][] table = this.value_table;
        if (index < 0 || index >= table.length) {
            return null;
        }

        final StateHolder<?, ?>[] values = table[index];

        final int withId = ((PropertyAccess)property).moonrise$getIdFor(with);
        if (withId < 0 || withId >= values.length) {
            return null;
        }

        return values[withId];
    }

    protected static int lookup_vindex(final Property<?> property, final long[] index_table) {
        final int id = ((PropertyAccess)property).moonrise$getId();
        final long bitset_mask = (1L << (id & 31));
        final long lower_mask = bitset_mask - 1;
        final int index = id >>> 5;
        if (index >= index_table.length) {
            return -1;
        }
        final long index_value = index_table[index];
        final long contains_check = ((index_value & bitset_mask) - 1) >> (Long.SIZE - 1); // -1L if doesn't contain

        // index = total bits set in lower table values (upper 32 bits of index_value) plus total bits set in lower indices below id
        // contains_check is 0 if the bitset had id set, else it's -1: so index is unaffected if contains_check == 0,
        // otherwise it comes out as -1.
        return (int)(((index_value >>> 32) + Long.bitCount(index_value & lower_mask)) | contains_check);
    }
}
