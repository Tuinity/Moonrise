package ca.spottedleaf.moonrise.patches.chunk_tick_iteration;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;

public interface ChunkTickServerLevel {

    public ReferenceList<ServerChunkCache.ChunkAndHolder> moonrise$getPlayerTickingChunks();

    public void moonrise$markChunkForPlayerTicking(final LevelChunk chunk);

    public void moonrise$removeChunkForPlayerTicking(final LevelChunk chunk);

    public void moonrise$addPlayerTickingRequest(final int chunkX, final int chunkZ);

    public void moonrise$removePlayerTickingRequest(final int chunkX, final int chunkZ);

}
