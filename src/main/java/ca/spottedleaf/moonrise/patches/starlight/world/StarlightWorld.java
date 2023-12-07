package ca.spottedleaf.moonrise.patches.starlight.world;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

public interface StarlightWorld {

    // rets full chunk without blocking
    public LevelChunk starlight$getChunkAtImmediately(final int chunkX, final int chunkZ);

    // rets chunk at any stage, if it exists, immediately
    public ChunkAccess starlight$getAnyChunkImmediately(final int chunkX, final int chunkZ);

}
