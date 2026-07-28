package ca.spottedleaf.moonrise.patches.chunk_system.scheduling;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;

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
    public ChunkHolder get(final Object key) {
        if (key instanceof Long longKey) {
            return this.get(longKey.longValue());
        }
        return null;
    }

    @Override
    public boolean containsKey(final long key) {
        return this.get(key) != null;
    }

    @Override
    public boolean containsKey(final Object key) {
        if (key instanceof Long longKey) {
            return this.containsKey(longKey.longValue());
        }
        return false;
    }

    @Override
    public ObjectCollection<ChunkHolder> values() {
        return new AbstractObjectCollection<ChunkHolder>() {
            @Override
            public ObjectIterator<ChunkHolder> iterator() {
                final ChunkHolderManager manager = MoonriseChunkHolderMap.this.getHolderManager();
                final List<ChunkHolder> holders = manager == null ? List.of() : manager.getOldChunkHolders();
                final Iterator<ChunkHolder> iterator = holders.iterator();
                return new ObjectIterator<ChunkHolder>() {
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
