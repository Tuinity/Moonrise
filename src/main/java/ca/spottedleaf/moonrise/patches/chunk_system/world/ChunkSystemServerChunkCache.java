package ca.spottedleaf.moonrise.patches.chunk_system.world;

import net.minecraft.world.level.chunk.LevelChunk;

public interface ChunkSystemServerChunkCache {

    public void moonrise$setFullChunk(final int chunkX, final int chunkZ, final LevelChunk chunk);

    public LevelChunk moonrise$getFullChunkIfLoaded(final int chunkX, final int chunkZ);

}
