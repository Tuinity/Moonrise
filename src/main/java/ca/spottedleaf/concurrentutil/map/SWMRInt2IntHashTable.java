package ca.spottedleaf.concurrentutil.map;

import ca.spottedleaf.concurrentutil.util.ArrayUtil;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.IntegerUtil;
import ca.spottedleaf.concurrentutil.util.Validate;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class SWMRInt2IntHashTable {

    protected int size;

    protected TableEntry[] table;

    protected final float loadFactor;

    protected static final VarHandle SIZE_HANDLE = ConcurrentUtil.getVarHandle(SWMRInt2IntHashTable.class, "size", int.class);
    protected static final VarHandle TABLE_HANDLE = ConcurrentUtil.getVarHandle(SWMRInt2IntHashTable.class, "table", TableEntry[].class);

    /* size */

    protected final int getSizePlain() {
        return (int)SIZE_HANDLE.get(this);
    }

    protected final int getSizeOpaque() {
        return (int)SIZE_HANDLE.getOpaque(this);
    }

    protected final int getSizeAcquire() {
        return (int)SIZE_HANDLE.getAcquire(this);
    }

    protected final void setSizePlain(final int value) {
        SIZE_HANDLE.set(this, value);
    }

    protected final void setSizeOpaque(final int value) {
        SIZE_HANDLE.setOpaque(this, value);
    }

    protected final void setSizeRelease(final int value) {
        SIZE_HANDLE.setRelease(this, value);
    }

    /* table */

    protected final TableEntry[] getTablePlain() {
        //noinspection unchecked
        return (TableEntry[])TABLE_HANDLE.get(this);
    }

    protected final TableEntry[] getTableAcquire() {
        //noinspection unchecked
        return (TableEntry[])TABLE_HANDLE.getAcquire(this);
    }

    protected final void setTablePlain(final TableEntry[] table) {
        TABLE_HANDLE.set(this, table);
    }

    protected final void setTableRelease(final TableEntry[] table) {
        TABLE_HANDLE.setRelease(this, table);
    }

    protected static final int DEFAULT_CAPACITY = 16;
    protected static final float DEFAULT_LOAD_FACTOR = 0.75f;
    protected static final int MAXIMUM_CAPACITY = Integer.MIN_VALUE >>> 1;

    /**
     * Constructs this map with a capacity of {@code 16} and load factor of {@code 0.75f}.
     */
    public SWMRInt2IntHashTable() {
        this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs this map with the specified capacity and load factor of {@code 0.75f}.
     * @param capacity specified initial capacity, > 0
     */
    public SWMRInt2IntHashTable(final int capacity) {
        this(capacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs this map with the specified capacity and load factor.
     * @param capacity specified capacity, > 0
     * @param loadFactor specified load factor, > 0 && finite
     */
    public SWMRInt2IntHashTable(final int capacity, final float loadFactor) {
        final int tableSize = getCapacityFor(capacity);

        if (loadFactor <= 0.0 || !Float.isFinite(loadFactor)) {
            throw new IllegalArgumentException("Invalid load factor: " + loadFactor);
        }

        //noinspection unchecked
        final TableEntry[] table = new TableEntry[tableSize];
        this.setTablePlain(table);

        if (tableSize == MAXIMUM_CAPACITY) {
            this.threshold = -1;
        } else {
            this.threshold = getTargetCapacity(tableSize, loadFactor);
        }

        this.loadFactor = loadFactor;
    }

    /**
     * Constructs this map with a capacity of {@code 16} or the specified map's size, whichever is larger, and
     * with a load factor of {@code 0.75f}.
     * All of the specified map's entries are copied into this map.
     * @param other The specified map.
     */
    public SWMRInt2IntHashTable(final SWMRInt2IntHashTable other) {
        this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR, other);
    }

    /**
     * Constructs this map with a minimum capacity of the specified capacity or the specified map's size, whichever is larger, and
     * with a load factor of {@code 0.75f}.
     * All of the specified map's entries are copied into this map.
     * @param capacity specified capacity, > 0
     * @param other The specified map.
     */
    public SWMRInt2IntHashTable(final int capacity, final SWMRInt2IntHashTable other) {
        this(capacity, DEFAULT_LOAD_FACTOR, other);
    }

    /**
     * Constructs this map with a min capacity of the specified capacity or the specified map's size, whichever is larger, and
     * with the specified load factor.
     * All of the specified map's entries are copied into this map.
     * @param capacity specified capacity, > 0
     * @param loadFactor specified load factor, > 0 && finite
     * @param other The specified map.
     */
    public SWMRInt2IntHashTable(final int capacity, final float loadFactor, final SWMRInt2IntHashTable other) {
        this(Math.max(Validate.notNull(other, "Null map").size(), capacity), loadFactor);
        this.putAll(other);
    }

    public final float getLoadFactor() {
        return this.loadFactor;
    }

    protected static int getCapacityFor(final int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Invalid capacity: " + capacity);
        }
        if (capacity >= MAXIMUM_CAPACITY) {
            return MAXIMUM_CAPACITY;
        }
        return IntegerUtil.roundCeilLog2(capacity);
    }

    /** Callers must still use acquire when reading the value of the entry. */
    protected final TableEntry getEntryForOpaque(final int key) {
        final int hash = SWMRInt2IntHashTable.getHash(key);
        final TableEntry[] table = this.getTableAcquire();

        for (TableEntry curr = ArrayUtil.getOpaque(table, hash & (table.length - 1)); curr != null; curr = curr.getNextOpaque()) {
            if (key == curr.key) {
                return curr;
            }
        }

        return null;
    }

    protected final TableEntry getEntryForPlain(final int key) {
        final int hash = SWMRInt2IntHashTable.getHash(key);
        final TableEntry[] table = this.getTablePlain();

        for (TableEntry curr = table[hash & (table.length - 1)]; curr != null; curr = curr.getNextPlain()) {
            if (key == curr.key) {
                return curr;
            }
        }

        return null;
    }

    /* MT-Safe */

    /** must be deterministic given a key */
    protected static int getHash(final int key) {
        return it.unimi.dsi.fastutil.HashCommon.mix(key);
    }

    // rets -1 if capacity*loadFactor is too large
    protected static int getTargetCapacity(final int capacity, final float loadFactor) {
        final double ret = (double)capacity * (double)loadFactor;
        if (Double.isInfinite(ret) || ret >= ((double)Integer.MAX_VALUE)) {
            return -1;
        }

        return (int)ret;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        /* Make no attempt to deal with concurrent modifications */
        if (!(obj instanceof SWMRInt2IntHashTable)) {
            return false;
        }
        final SWMRInt2IntHashTable other = (SWMRInt2IntHashTable)obj;

        if (this.size() != other.size()) {
            return false;
        }

        final TableEntry[] table = this.getTableAcquire();

        for (int i = 0, len = table.length; i < len; ++i) {
            for (TableEntry curr = ArrayUtil.getOpaque(table, i); curr != null; curr = curr.getNextOpaque()) {
                final int value = curr.getValueAcquire();

                final int otherValue = other.get(curr.key);
                if (value != otherValue) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        /* Make no attempt to deal with concurrent modifications */
        int hash = 0;
        final TableEntry[] table = this.getTableAcquire();

        for (int i = 0, len = table.length; i < len; ++i) {
            for (TableEntry curr = ArrayUtil.getOpaque(table, i); curr != null; curr = curr.getNextOpaque()) {
                hash += curr.hashCode();
            }
        }

        return hash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder(64);
        builder.append("SingleWriterMultiReaderHashMap:{");

        this.forEach((final int key, final int value) -> {
            builder.append("{key: \"").append(key).append("\", value: \"").append(value).append("\"}");
        });

        return builder.append('}').toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SWMRInt2IntHashTable clone() {
        return new SWMRInt2IntHashTable(this.getTableAcquire().length, this.loadFactor, this);
    }

    /**
     * {@inheritDoc}
     */
    public void forEach(final Consumer<? super SWMRInt2IntHashTable.TableEntry> action) {
        Validate.notNull(action, "Null action");

        final TableEntry[] table = this.getTableAcquire();
        for (int i = 0, len = table.length; i < len; ++i) {
            for (TableEntry curr = ArrayUtil.getOpaque(table, i); curr != null; curr = curr.getNextOpaque()) {
                action.accept(curr);
            }
        }
    }

    @FunctionalInterface
    public static interface BiIntIntConsumer {
        public void accept(final int key, final int value);
    }

    /**
     * {@inheritDoc}
     */
    public void forEach(final BiIntIntConsumer action) {
        Validate.notNull(action, "Null action");

        final TableEntry[] table = this.getTableAcquire();
        for (int i = 0, len = table.length; i < len; ++i) {
            for (TableEntry curr = ArrayUtil.getOpaque(table, i); curr != null; curr = curr.getNextOpaque()) {
                final int value = curr.getValueAcquire();

                action.accept(curr.key, value);
            }
        }
    }

    /**
     * Provides the specified consumer with all keys contained within this map.
     * @param action The specified consumer.
     */
    public void forEachKey(final IntConsumer action) {
        Validate.notNull(action, "Null action");

        final TableEntry[] table = this.getTableAcquire();
        for (int i = 0, len = table.length; i < len; ++i) {
            for (TableEntry curr = ArrayUtil.getOpaque(table, i); curr != null; curr = curr.getNextOpaque()) {
                action.accept(curr.key);
            }
        }
    }

    /**
     * Provides the specified consumer with all values contained within this map. Equivalent to {@code map.values().forEach(Consumer)}.
     * @param action The specified consumer.
     */
    public void forEachValue(final IntConsumer action) {
        Validate.notNull(action, "Null action");

        final TableEntry[] table = this.getTableAcquire();
        for (int i = 0, len = table.length; i < len; ++i) {
            for (TableEntry curr = ArrayUtil.getOpaque(table, i); curr != null; curr = curr.getNextOpaque()) {
                final int value = curr.getValueAcquire();

                action.accept(value);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public int get(final int key) {
        final TableEntry entry = this.getEntryForOpaque(key);
        return entry == null ? 0 : entry.getValueAcquire();
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsKey(final int key) {
        final TableEntry entry = this.getEntryForOpaque(key);
        return entry != null;
    }

    /**
     * {@inheritDoc}
     */
    public int getOrDefault(final int key, final int defaultValue) {
        final TableEntry entry = this.getEntryForOpaque(key);

        return entry == null ? defaultValue : entry.getValueAcquire();
    }

    /**
     * {@inheritDoc}
     */
    public int size() {
        return this.getSizeAcquire();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
        return this.getSizeAcquire() == 0;
    }

    /* Non-MT-Safe */

    protected int threshold;

    protected final void checkResize(final int minCapacity) {
        if (minCapacity <= this.threshold || this.threshold < 0) {
            return;
        }

        final TableEntry[] table = this.getTablePlain();
        int newCapacity = minCapacity >= MAXIMUM_CAPACITY ? MAXIMUM_CAPACITY : IntegerUtil.roundCeilLog2(minCapacity);
        if (newCapacity < 0) {
            newCapacity = MAXIMUM_CAPACITY;
        }
        if (newCapacity <= table.length) {
            if (newCapacity == MAXIMUM_CAPACITY) {
                return;
            }
            newCapacity = table.length << 1;
        }

        //noinspection unchecked
        final TableEntry[] newTable = new TableEntry[newCapacity];
        final int indexMask = newCapacity - 1;

        for (int i = 0, len = table.length; i < len; ++i) {
            for (TableEntry entry = table[i]; entry != null; entry = entry.getNextPlain()) {
                final int key = entry.key;
                final int hash = SWMRInt2IntHashTable.getHash(key);
                final int index = hash & indexMask;

                /* we need to create a new entry since there could be reading threads */
                final TableEntry insert = new TableEntry(key, entry.getValuePlain());

                final TableEntry prev = newTable[index];

                newTable[index] = insert;
                insert.setNextPlain(prev);
            }
        }

        if (newCapacity == MAXIMUM_CAPACITY) {
            this.threshold = -1; /* No more resizing */
        } else {
            this.threshold = getTargetCapacity(newCapacity, this.loadFactor);
        }
        this.setTableRelease(newTable); /* use release to publish entries in table */
    }

    protected final int addToSize(final int num) {
        final int newSize = this.getSizePlain() + num;

        this.setSizeOpaque(newSize);
        this.checkResize(newSize);

        return newSize;
    }

    protected final int removeFromSize(final int num) {
        final int newSize = this.getSizePlain() - num;

        this.setSizeOpaque(newSize);

        return newSize;
    }

    protected final int put(final int key, final int value, final boolean onlyIfAbsent) {
        final TableEntry[] table = this.getTablePlain();
        final int hash = SWMRInt2IntHashTable.getHash(key);
        final int index = hash & (table.length - 1);

        final TableEntry head = table[index];
        if (head == null) {
            final TableEntry insert = new TableEntry(key, value);
            ArrayUtil.setRelease(table, index, insert);
            this.addToSize(1);
            return 0;
        }

        for (TableEntry curr = head;;) {
            if (key == curr.key) {
                if (onlyIfAbsent) {
                    return curr.getValuePlain();
                }

                final int currVal = curr.getValuePlain();
                curr.setValueRelease(value);
                return currVal;
            }

            final TableEntry next = curr.getNextPlain();
            if (next != null) {
                curr = next;
                continue;
            }

            final TableEntry insert = new TableEntry(key, value);

            curr.setNextRelease(insert);
            this.addToSize(1);
            return 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    public int put(final int key, final int value) {
        return this.put(key, value, false);
    }

    /**
     * {@inheritDoc}
     */
    public int putIfAbsent(final int key, final int value) {
        return this.put(key, value, true);
    }

    protected final int remove(final int key, final int hash) {
        final TableEntry[] table = this.getTablePlain();
        final int index = (table.length - 1) & hash;

        final TableEntry head = table[index];
        if (head == null) {
            return 0;
        }

        if (head.key == key) {
            ArrayUtil.setRelease(table, index, head.getNextPlain());
            this.removeFromSize(1);

            return head.getValuePlain();
        }

        for (TableEntry curr = head.getNextPlain(), prev = head; curr != null; prev = curr, curr = curr.getNextPlain()) {
            if (key == curr.key) {
                prev.setNextRelease(curr.getNextPlain());
                this.removeFromSize(1);

                return curr.getValuePlain();
            }
        }

        return 0;
    }

    /**
     * {@inheritDoc}
     */
    public int remove(final int key) {
        return this.remove(key, SWMRInt2IntHashTable.getHash(key));
    }

    /**
     * {@inheritDoc}
     */
    public void putAll(final SWMRInt2IntHashTable map) {
        Validate.notNull(map, "Null map");

        final int size = map.size();
        this.checkResize(Math.max(this.getSizePlain() + size/2, size)); /* preemptively resize */
        map.forEach(this::put);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This call is non-atomic and the order that which entries are removed is undefined. The clear operation itself
     * is release ordered, that is, after the clear operation is performed a release fence is performed.
     * </p>
     */
    public void clear() {
        Arrays.fill(this.getTablePlain(), null);
        this.setSizeRelease(0);
    }

    public static final class TableEntry {

        protected final int key;
        protected int value;

        protected TableEntry next;

        protected static final VarHandle VALUE_HANDLE = ConcurrentUtil.getVarHandle(TableEntry.class, "value", Object.class);
        protected static final VarHandle NEXT_HANDLE = ConcurrentUtil.getVarHandle(TableEntry.class, "next", TableEntry.class);

        /* value */

        protected final int getValuePlain() {
            //noinspection unchecked
            return (int)VALUE_HANDLE.get(this);
        }

        protected final int getValueAcquire() {
            //noinspection unchecked
            return (int)VALUE_HANDLE.getAcquire(this);
        }

        protected final void setValueRelease(final int to) {
            VALUE_HANDLE.setRelease(this, to);
        }

        /* next */

        protected final TableEntry getNextPlain() {
            //noinspection unchecked
            return (TableEntry)NEXT_HANDLE.get(this);
        }

        protected final TableEntry getNextOpaque() {
            //noinspection unchecked
            return (TableEntry)NEXT_HANDLE.getOpaque(this);
        }

        protected final void setNextPlain(final TableEntry next) {
            NEXT_HANDLE.set(this, next);
        }

        protected final void setNextRelease(final TableEntry next) {
            NEXT_HANDLE.setRelease(this, next);
        }

        protected TableEntry(final int key, final int value) {
            this.key = key;
            this.value = value;
        }

        public int getKey() {
            return this.key;
        }

        public int getValue() {
            return this.getValueAcquire();
        }

        /**
         * {@inheritDoc}
         */
        public int setValue(final int value) {
            final int curr = this.getValuePlain();

            this.setValueRelease(value);
            return curr;
        }

        protected static int hash(final int key, final int value) {
            return SWMRInt2IntHashTable.getHash(key) ^ SWMRInt2IntHashTable.getHash(value);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return hash(this.key, this.getValueAcquire());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof TableEntry)) {
                return false;
            }
            final TableEntry other = (TableEntry)obj;
            final int otherKey = other.getKey();
            final int thisKey = this.getKey();
            final int otherValue = other.getValueAcquire();
            final int thisVal = this.getValueAcquire();
            return (thisKey == otherKey) && (thisVal == otherValue);
        }
    }

}
