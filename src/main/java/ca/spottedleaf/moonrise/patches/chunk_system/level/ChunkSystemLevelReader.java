package ca.spottedleaf.moonrise.patches.chunk_system.level;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public interface ChunkSystemLevelReader {

    public ChunkAccess moonrise$syncLoadNonFull(final int chunkX, final int chunkZ, final ChunkStatus status);

}
