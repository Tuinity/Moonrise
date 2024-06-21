package ca.spottedleaf.moonrise.patches.chunk_system.queue;

import ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class ChunkUnloadQueue {

    public final int coordinateShift;
    private final AtomicLong orderGenerator = new AtomicLong();
    private final ConcurrentLong2ReferenceChainedHashTable<UnloadSection> unloadSections = new ConcurrentLong2ReferenceChainedHashTable<>();

    /*
     * Note: write operations do not occur in parallel for any given section.
     * Note: coordinateShift <= region shift in order for retrieveForCurrentRegion() to function correctly
     */

    public ChunkUnloadQueue(final int coordinateShift) {
        this.coordinateShift = coordinateShift;
    }

    public static record SectionToUnload(int sectionX, int sectionZ, long order, int count) {}

    public List<SectionToUnload> retrieveForAllRegions() {
        final List<SectionToUnload> ret = new ArrayList<>();

        for (final Iterator<ConcurrentLong2ReferenceChainedHashTable.TableEntry<UnloadSection>> iterator = this.unloadSections.entryIterator(); iterator.hasNext();) {
            final ConcurrentLong2ReferenceChainedHashTable.TableEntry<UnloadSection> entry = iterator.next();
            final long key = entry.getKey();
            final UnloadSection section = entry.getValue();
            final int sectionX = CoordinateUtils.getChunkX(key);
            final int sectionZ = CoordinateUtils.getChunkZ(key);

            ret.add(new SectionToUnload(sectionX, sectionZ, section.order, section.chunks.size()));
        }

        ret.sort((final SectionToUnload s1, final SectionToUnload s2) -> {
            return Long.compare(s1.order, s2.order);
        });

        return ret;
    }

    public UnloadSection getSectionUnsynchronized(final int sectionX, final int sectionZ) {
        return this.unloadSections.get(CoordinateUtils.getChunkKey(sectionX, sectionZ));
    }

    public UnloadSection removeSection(final int sectionX, final int sectionZ) {
        return this.unloadSections.remove(CoordinateUtils.getChunkKey(sectionX, sectionZ));
    }

    // write operation
    public boolean addChunk(final int chunkX, final int chunkZ) {
        // write operations do not occur in parallel for a given section
        final int shift = this.coordinateShift;
        final int sectionX = chunkX >> shift;
        final int sectionZ = chunkZ >> shift;
        final long sectionKey = CoordinateUtils.getChunkKey(sectionX, sectionZ);
        final long chunkKey = CoordinateUtils.getChunkKey(chunkX, chunkZ);

        UnloadSection section = this.unloadSections.get(sectionKey);
        if (section == null) {
            section = new UnloadSection(this.orderGenerator.getAndIncrement());
            this.unloadSections.put(sectionKey, section);
        }

        return section.chunks.add(chunkKey);
    }

    // write operation
    public boolean removeChunk(final int chunkX, final int chunkZ) {
        // write operations do not occur in parallel for a given section
        final int shift = this.coordinateShift;
        final int sectionX = chunkX >> shift;
        final int sectionZ = chunkZ >> shift;
        final long sectionKey = CoordinateUtils.getChunkKey(sectionX, sectionZ);
        final long chunkKey = CoordinateUtils.getChunkKey(chunkX, chunkZ);

        final UnloadSection section = this.unloadSections.get(sectionKey);

        if (section == null) {
            return false;
        }

        if (!section.chunks.remove(chunkKey)) {
            return false;
        }

        if (section.chunks.isEmpty()) {
            this.unloadSections.remove(sectionKey);
        }

        return true;
    }

    public JsonElement toDebugJson() {
        final JsonArray ret = new JsonArray();

        for (final SectionToUnload section : this.retrieveForAllRegions()) {
            final JsonObject sectionJson = new JsonObject();
            ret.add(sectionJson);

            sectionJson.addProperty("sectionX", section.sectionX());
            sectionJson.addProperty("sectionZ", section.sectionX());
            sectionJson.addProperty("order", section.order());

            final JsonArray coordinates = new JsonArray();
            sectionJson.add("coordinates", coordinates);

            final UnloadSection actualSection = this.getSectionUnsynchronized(section.sectionX(), section.sectionZ());
            if (actualSection != null) {
                for (final LongIterator iterator = actualSection.chunks.clone().iterator(); iterator.hasNext(); ) {
                    final long coordinate = iterator.nextLong();

                    final JsonObject coordinateJson = new JsonObject();
                    coordinates.add(coordinateJson);

                    coordinateJson.addProperty("chunkX", Integer.valueOf(CoordinateUtils.getChunkX(coordinate)));
                    coordinateJson.addProperty("chunkZ", Integer.valueOf(CoordinateUtils.getChunkZ(coordinate)));
                }
            }
        }

        return ret;
    }

    public static final class UnloadSection {

        public final long order;
        public final LongLinkedOpenHashSet chunks = new LongLinkedOpenHashSet();

        public UnloadSection(final long order) {
            this.order = order;
        }
    }
}