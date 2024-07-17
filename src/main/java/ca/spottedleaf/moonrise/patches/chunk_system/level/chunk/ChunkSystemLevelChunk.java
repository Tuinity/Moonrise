package ca.spottedleaf.moonrise.patches.chunk_system.level.chunk;

import net.minecraft.server.level.ServerChunkCache;

public interface ChunkSystemLevelChunk {

    public boolean moonrise$isPostProcessingDone();

    public ServerChunkCache.ChunkAndHolder moonrise$getChunkAndHolder();

    public void moonrise$setChunkAndHolder(final ServerChunkCache.ChunkAndHolder holder);

}
