package ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.util;

import ca.spottedleaf.concurrentutil.util.IntegerUtil;
import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccess;
import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccessStateHolder;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class ZeroCollidingReferenceStateTable<O, S> {

    private final Int2ObjectOpenHashMap<Indexer> propertyToIndexer;
    private S[] lookup;

    public ZeroCollidingReferenceStateTable(final Collection<Property<?>> properties) {
        this.propertyToIndexer = new Int2ObjectOpenHashMap<>(properties.size());

        final List<Property<?>> sortedProperties = new ArrayList<>(properties);

        // important that each table sees the same property order given the same _set_ of properties,
        // as each table will calculate the index for the block state
        sortedProperties.sort((final Property<?> p1, final Property<?> p2) -> {
            return Integer.compare(
                ((PropertyAccess<?>)p1).moonrise$getId(),
                ((PropertyAccess<?>)p2).moonrise$getId()
            );
        });

        int currentMultiple = 1;
        for (final Property<?> property : sortedProperties) {
            final int totalValues = property.getPossibleValues().size();

            this.propertyToIndexer.put(
                ((PropertyAccess<?>)property).moonrise$getId(),
                new Indexer(
                    totalValues,
                    currentMultiple,
                    IntegerUtil.getUnsignedDivisorMagic((long)currentMultiple, 32),
                    IntegerUtil.getUnsignedDivisorMagic((long)totalValues, 32)
                )
            );

            currentMultiple *= totalValues;
        }
    }

    public <T extends Comparable<T>> boolean hasProperty(final Property<T> property) {
        return this.propertyToIndexer.containsKey(((PropertyAccess<T>)property).moonrise$getId());
    }

    public long getIndex(final StateHolder<O, S> stateHolder) {
        long ret = 0L;

        for (final Map.Entry<Property<?>, Comparable<?>> entry : stateHolder.getValues().entrySet()) {
            final Property<?> property = entry.getKey();
            final Comparable<?> value = entry.getValue();

            final Indexer indexer = this.propertyToIndexer.get(((PropertyAccess<?>)property).moonrise$getId());

            ret += (((PropertyAccess)property).moonrise$getIdFor(value)) * indexer.multiple;
        }

        return ret;
    }

    public boolean isLoaded() {
        return this.lookup != null;
    }

    public void loadInTable(final Map<Map<Property<?>, Comparable<?>>, S> universe) {
        if (this.lookup != null) {
            throw new IllegalStateException();
        }

        this.lookup = (S[])new StateHolder[universe.size()];

        for (final S value : universe.values()) {
            if (value == null) {
                continue;
            }
            this.lookup[(int)((PropertyAccessStateHolder)(StateHolder<O, S>)value).moonrise$getTableIndex()] = value;
        }

        for (final S value : this.lookup) {
            if (value == null) {
                throw new IllegalStateException();
            }
        }
    }

    public <T extends Comparable<T>> T get(final long index, final Property<T> property) {
        final Indexer indexer = this.propertyToIndexer.get(((PropertyAccess<T>)property).moonrise$getId());
        if (indexer == null) {
            return null;
        }

        final long divided = (index * indexer.multipleDivMagic) >>> 32;
        final long modded = (((divided * indexer.modMagic) & 0xFFFFFFFFL) * indexer.totalValues) >>> 32;
        // equiv to: divided = index / multiple
        //           modded = divided % totalValues

        return ((PropertyAccess<T>)property).moonrise$getById((int)modded);
    }

    public <T extends Comparable<T>> S set(final long index, final Property<T> property, final T with) {
        final int newValueId = ((PropertyAccess<T>)property).moonrise$getIdFor(with);
        if (newValueId < 0) {
            return null;
        }

        final Indexer indexer = this.propertyToIndexer.get(((PropertyAccess<T>)property).moonrise$getId());
        if (indexer == null) {
            return null;
        }

        final long divided = (index * indexer.multipleDivMagic) >>> 32;
        final long modded = (((divided * indexer.modMagic) & 0xFFFFFFFFL) * indexer.totalValues) >>> 32;
        // equiv to: divided = index / multiple
        //           modded = divided % totalValues

        // subtract out the old value, add in the new
        final long newIndex = (((long)newValueId - modded) * indexer.multiple) + index;

        return this.lookup[(int)newIndex];
    }

    private static final record Indexer(
        int totalValues, int multiple, long multipleDivMagic, long modMagic
    ) {}
}
