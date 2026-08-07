package ca.spottedleaf.moonrise.patches.chunk_system.scheduling;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import it.unimi.dsi.fastutil.longs.AbstractLongSortedSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import it.unimi.dsi.fastutil.objects.AbstractObjectCollection;
import it.unimi.dsi.fastutil.objects.AbstractObjectSortedSet;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.LongFunction;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * A delegating Long2ObjectLinkedOpenHashMap that exposes loaded chunk holders
 * from Moonrise's ChunkHolderManager to third-party mods which may be
 * expecting updatingChunkMap/visibleChunkMap to be non-null, matching vanilla.
 */
public final class MoonriseChunkHolderMap extends Long2ObjectLinkedOpenHashMap<ChunkHolder> {

    private final ServerLevel level;

    public MoonriseChunkHolderMap(final ServerLevel level) {
        this.level = level;
    }

    private ChunkHolderManager getHolderManager() {
        if (this.level == null) {
            return null;
        }
        return ((ChunkSystemServerLevel) this.level).moonrise$getChunkTaskScheduler().chunkHolderManager;
    }

    @Override
    public int size() {
        final ChunkHolderManager manager = this.getHolderManager();
        return manager == null ? 0 : manager.size();
    }

    @Override
    public boolean isEmpty() {
        return this.size() == 0;
    }

    @Override
    public ChunkHolder get(final long key) {
        final ChunkHolderManager manager = this.getHolderManager();
        if (manager == null) {
            return null;
        }
        final NewChunkHolder holder = manager.getChunkHolder(key);
        return holder == null ? null : holder.vanillaChunkHolder;
    }

    @Override
    public boolean containsKey(final long key) {
        return this.get(key) != null;
    }

    @Override
    public ChunkHolder put(final long k, final ChunkHolder chunkHolder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChunkHolder remove(final long k) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChunkHolder removeFirst() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChunkHolder removeLast() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChunkHolder getAndMoveToFirst(final long k) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChunkHolder getAndMoveToLast(final long k) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChunkHolder putAndMoveToFirst(final long k, final ChunkHolder chunkHolder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChunkHolder putAndMoveToLast(final long k, final ChunkHolder chunkHolder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsValue(final Object v) {
        return this.values().contains(v);
    }

    @Override
    public ChunkHolder getOrDefault(final long k, final ChunkHolder defaultValue) {
        final ChunkHolder ret = this.get(k);
        return ret == null ? defaultValue : ret;
    }

    @Override
    public @Nullable ChunkHolder putIfAbsent(final long key, final ChunkHolder value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(final long k, final Object v) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean replace(final long key, final ChunkHolder oldValue, final ChunkHolder newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChunkHolder replace(final long k, final ChunkHolder chunkHolder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChunkHolder computeIfAbsent(final long k, final LongFunction<? extends ChunkHolder> mappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChunkHolder computeIfAbsent(final long key, final Long2ObjectFunction<? extends ChunkHolder> mappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChunkHolder computeIfPresent(final long k, final BiFunction<? super Long, ? super ChunkHolder, ? extends ChunkHolder> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChunkHolder compute(final long k, final BiFunction<? super Long, ? super ChunkHolder, ? extends ChunkHolder> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChunkHolder merge(final long k, final ChunkHolder chunkHolder, final BiFunction<? super ChunkHolder, ? super ChunkHolder, ? extends ChunkHolder> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long firstLongKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long lastLongKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean trim(final int n) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Long2ObjectLinkedOpenHashMap<ChunkHolder> clone() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
        return this.long2ObjectEntrySet().hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        return this == o;
    }

    @Override
    public FastSortedEntrySet<ChunkHolder> long2ObjectEntrySet() {
        class Set extends AbstractObjectSortedSet<Long2ObjectMap.Entry<ChunkHolder>> implements FastSortedEntrySet<ChunkHolder> {

            @Override
            public ObjectBidirectionalIterator<Entry<ChunkHolder>> fastIterator() {
                return this.iterator();
            }

            @Override
            public ObjectBidirectionalIterator<Entry<ChunkHolder>> fastIterator(final Entry<ChunkHolder> from) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ObjectBidirectionalIterator<Entry<ChunkHolder>> iterator(final Entry<ChunkHolder> fromElement) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ObjectBidirectionalIterator<Entry<ChunkHolder>> iterator() {
                final ChunkHolderManager manager = MoonriseChunkHolderMap.this.getHolderManager();
                final List<NewChunkHolder> holders = manager == null ? List.of() : manager.getChunkHolders();
                final Iterator<NewChunkHolder> iterator = holders.iterator();
                return new ObjectBidirectionalIterator<>() {
                    @Override
                    public Entry<ChunkHolder> previous() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean hasPrevious() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public Entry<ChunkHolder> next() {
                        final NewChunkHolder holder = iterator.next();
                        return new Entry<>() {
                            @Override
                            public long getLongKey() {
                                return CoordinateUtils.getChunkKey(holder.chunkX, holder.chunkZ);
                            }

                            @Override
                            public ChunkHolder getValue() {
                                return holder.vanillaChunkHolder;
                            }

                            @Override
                            public ChunkHolder setValue(final ChunkHolder value) {
                                throw new UnsupportedOperationException();
                            }
                        };
                    }
                };
            }

            @Override
            public int size() {
                return MoonriseChunkHolderMap.this.size();
            }

            @Override
            public @Nullable Comparator<? super Entry<ChunkHolder>> comparator() {
                return null;
            }

            @Override
            public ObjectSortedSet<Entry<ChunkHolder>> subSet(final Entry<ChunkHolder> fromElement, final Entry<ChunkHolder> toElement) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ObjectSortedSet<Entry<ChunkHolder>> headSet(final Entry<ChunkHolder> toElement) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ObjectSortedSet<Entry<ChunkHolder>> tailSet(final Entry<ChunkHolder> fromElement) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Entry<ChunkHolder> first() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Entry<ChunkHolder> last() {
                throw new UnsupportedOperationException();
            }
        }
        return new Set();
    }

    @Override
    public LongSortedSet keySet() {
        return new AbstractLongSortedSet() {
            @Override
            public LongBidirectionalIterator iterator() {
                final ChunkHolderManager manager = MoonriseChunkHolderMap.this.getHolderManager();
                final List<NewChunkHolder> holders = manager == null ? List.of() : manager.getChunkHolders();
                final Iterator<NewChunkHolder> iterator = holders.iterator();
                return new LongBidirectionalIterator() {
                    @Override
                    public long previousLong() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean hasPrevious() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public long nextLong() {
                        final NewChunkHolder holder = iterator.next();
                        return CoordinateUtils.getChunkKey(holder.chunkX, holder.chunkZ);
                    }

                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }
                };
            }

            @Override
            public LongBidirectionalIterator iterator(final long fromElement) {
                throw new UnsupportedOperationException();
            }

            @Override
            public LongSortedSet subSet(final long fromElement, final long toElement) {
                throw new UnsupportedOperationException();
            }

            @Override
            public LongSortedSet headSet(final long toElement) {
                throw new UnsupportedOperationException();
            }

            @Override
            public LongSortedSet tailSet(final long fromElement) {
                throw new UnsupportedOperationException();
            }

            @Override
            public LongComparator comparator() {
                throw new UnsupportedOperationException();
            }

            @Override
            public long firstLong() {
                throw new UnsupportedOperationException();
            }

            @Override
            public long lastLong() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int size() {
                return MoonriseChunkHolderMap.this.size();
            }
        };
    }

    @Override
    public ObjectCollection<ChunkHolder> values() {
        return new AbstractObjectCollection<>() {
            @Override
            public ObjectIterator<ChunkHolder> iterator() {
                final ChunkHolderManager manager = MoonriseChunkHolderMap.this.getHolderManager();
                final List<ChunkHolder> holders = manager == null ? List.of() : manager.getOldChunkHolders();
                final Iterator<ChunkHolder> iterator = holders.iterator();
                return new ObjectIterator<>() {
                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public ChunkHolder next() {
                        return iterator.next();
                    }
                };
            }

            @Override
            public int size() {
                return MoonriseChunkHolderMap.this.size();
            }
        };
    }
}
