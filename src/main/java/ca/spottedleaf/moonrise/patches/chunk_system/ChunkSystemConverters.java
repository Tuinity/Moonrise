package ca.spottedleaf.moonrise.patches.chunk_system;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;

public final class ChunkSystemConverters {

    // See SectionStorage#getVersion
    private static final int DEFAULT_POI_DATA_VERSION = 1945;

    private static final int DEFAULT_ENTITY_CHUNK_DATA_VERSION = -1;

    private static int getCurrentVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }

    private static int getDataVersion(final CompoundTag data, final int dfl) {
        return !data.contains(SharedConstants.DATA_VERSION_TAG, Tag.TAG_ANY_NUMERIC)
            ? dfl : data.getInt(SharedConstants.DATA_VERSION_TAG);
    }

    public static CompoundTag convertPoiCompoundTag(final CompoundTag data, final ServerLevel world) {
        final int dataVersion = getDataVersion(data, DEFAULT_POI_DATA_VERSION);

        return DataFixTypes.POI_CHUNK.update(world.getServer().getFixerUpper(), data, dataVersion, getCurrentVersion());
    }

    public static CompoundTag convertEntityChunkCompoundTag(final CompoundTag data, final ServerLevel world) {
        final int dataVersion = getDataVersion(data, DEFAULT_ENTITY_CHUNK_DATA_VERSION);

        return DataFixTypes.ENTITY_CHUNK.update(world.getServer().getFixerUpper(), data, dataVersion, getCurrentVersion());
    }

    private ChunkSystemConverters() {}
}
