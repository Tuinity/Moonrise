package ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.util;

import ca.spottedleaf.concurrentutil.util.IntegerUtil;
import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccess;
import ca.spottedleaf.moonrise.patches.blockstate_propertyaccess.PropertyAccessStateHolder;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.AbstractReference2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;

public final class ZeroCollidingReferenceStateTable<O, S> {

    private final Int2ObjectOpenHashMap<Indexer> propertyToIndexer;
    private S[] lookup;
    private final Collection<Property<?>> properties;

    public ZeroCollidingReferenceStateTable(final Collection<Property<?>> properties) {
        this.propertyToIndexer = new Int2ObjectOpenHashMap<>(properties.size());
        this.properties = new ReferenceOpenHashSet<>(properties);

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

        for (final Map.Entry<Map<Property<?>, Comparable<?>>, S> entry : universe.entrySet()) {
            final S value = entry.getValue();
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

    public <T extends Comparable<T>> S trySet(final long index, final Property<T> property, final T with, final S dfl) {
        final Indexer indexer = this.propertyToIndexer.get(((PropertyAccess<T>)property).moonrise$getId());
        if (indexer == null) {
            return dfl;
        }

        final int newValueId = ((PropertyAccess<T>)property).moonrise$getIdFor(with);
        if (newValueId < 0) {
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

    public Collection<Property<?>> getProperties() {
        return Collections.unmodifiableCollection(this.properties);
    }

    public Map<Property<?>, Comparable<?>> getMapView(final long stateIndex) {
        return new MapView(stateIndex);
    }

    private static final record Indexer(
        int totalValues, int multiple, long multipleDivMagic, long modMagic
    ) {}

    private class MapView extends AbstractReference2ObjectMap<Property<?>, Comparable<?>> {
        private final long stateIndex;
        private EntrySet entrySet;

        MapView(final long stateIndex) {
            this.stateIndex = stateIndex;
        }

        @Override
        public boolean containsKey(final Object key) {
            return key instanceof Property<?> prop && ZeroCollidingReferenceStateTable.this.hasProperty(prop);
        }

        @Override
        public int size() {
            return ZeroCollidingReferenceStateTable.this.properties.size();
        }

        @Override
        public ObjectSet<Entry<Property<?>, Comparable<?>>> reference2ObjectEntrySet() {
            if (this.entrySet == null)
                this.entrySet = new EntrySet();
            return this.entrySet;
        }

        @Override
        public Comparable<?> get(final Object key) {
            return key instanceof Property<?> prop ? ZeroCollidingReferenceStateTable.this.get(this.stateIndex, prop) : null;
        }

        class EntrySet extends AbstractObjectSet<Entry<Property<?>, Comparable<?>>> {
            @Override
            public ObjectIterator<Reference2ObjectMap.Entry<Property<?>, Comparable<?>>> iterator() {
                Iterator<Property<?>> propIterator = properties.iterator();
                return new ObjectIterator<>() {
                    @Override
                    public boolean hasNext() {
                        return propIterator.hasNext();
                    }

                    @Override
                    public Entry<Property<?>, Comparable<?>> next() {
                        Property<?> prop = propIterator.next();
                        return new AbstractReference2ObjectMap.BasicEntry<>(prop, ZeroCollidingReferenceStateTable.this.get(MapView.this.stateIndex, prop));
                    }
                };
            }

            @Override
            public int size() {
                return ZeroCollidingReferenceStateTable.this.properties.size();
            }
        }
    }
}
