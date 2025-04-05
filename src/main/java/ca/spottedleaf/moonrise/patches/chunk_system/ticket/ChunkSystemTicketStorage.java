package ca.spottedleaf.moonrise.patches.chunk_system.ticket;

import net.minecraft.server.level.ChunkMap;

public interface ChunkSystemTicketStorage {

    public ChunkMap moonrise$getChunkMap();

    public void moonrise$setChunkMap(final ChunkMap chunkMap);

}
