package ca.spottedleaf.moonrise.patches.chunk_system.level.chunk;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import net.minecraft.server.level.ChunkMap;

public interface ChunkSystemDistanceManager {

    public ChunkMap moonrise$getChunkMap();

    public ChunkHolderManager moonrise$getChunkHolderManager();

}
