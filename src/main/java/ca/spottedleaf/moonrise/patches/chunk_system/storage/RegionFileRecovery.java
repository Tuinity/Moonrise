package ca.spottedleaf.moonrise.patches.chunk_system.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import java.nio.file.Path;
import java.util.BitSet;

public final class RegionFileRecovery {

    // the Level tag was removed in this data version
    private static final int LEVEL_TAG_REMOVAL_VERSION = 2842;

    // types used by vanilla for chunk data, including the force upgrade storage types
    public static boolean isChunkStorage(final RegionStorageInfo info) {
        if (info == null) {
            return false;
        }

        final String type = info.type();
        return "chunk".equals(type) || "chunksource".equals(type) || "chunktarget".equals(type);
    }

    // TODO check on update
    private static CompoundTag getChunkRoot(final CompoundTag chunkData) {
        if (NbtUtils.getDataVersion(chunkData) < LEVEL_TAG_REMOVAL_VERSION) {
            return chunkData.getCompoundOrEmpty("Level");
        }
        return chunkData;
    }

    public static ChunkPos getChunkCoordinate(final CompoundTag chunkData) {
        final CompoundTag root = getChunkRoot(chunkData);
        return new ChunkPos(root.getIntOr("xPos", 0), root.getIntOr("zPos", 0));
    }

    public static long getLastWorldSaveTime(final CompoundTag chunkData) {
        return getChunkRoot(chunkData).getLongOr("LastUpdate", 0L);
    }

    // returns the lowest chunk coordinate contained in the regionfile
    public static ChunkPos getRegionFileCoordinates(final Path file) {
        final String fileName = file.getFileName().toString();
        if (!fileName.startsWith("r.") || !fileName.endsWith(".mca")) {
            return null;
        }

        final String[] split = fileName.split("\\.");
        if (split.length != 4) {
            return null;
        }

        try {
            return new ChunkPos(Integer.parseInt(split[1]) << 5, Integer.parseInt(split[2]) << 5);
        } catch (final NumberFormatException ex) {
            return null;
        }
    }

    public static boolean inSameRegionFile(final ChunkPos first, final ChunkPos second) {
        return (first.x() >> 5) == (second.x() >> 5) && (first.z() >> 5) == (second.z() >> 5);
    }

    public static boolean tryAllocate(final BitSet allocated, final int from, final int length) {
        final int firstSet = allocated.nextSetBit(from);
        if (firstSet >= 0 && firstSet < (from + length)) {
            return false;
        }
        allocated.set(from, from + length);
        return true;
    }

    public static int allocate(final BitSet allocated, final int length) {
        int current = 0;
        for (;;) {
            final int freeStart = allocated.nextClearBit(current);
            final int freeEnd = allocated.nextSetBit(freeStart);
            if (freeEnd == -1 || freeEnd - freeStart >= length) {
                allocated.set(freeStart, freeStart + length);
                return freeStart;
            }
            current = freeEnd;
        }
    }

    private RegionFileRecovery() {}
}
