package ca.spottedleaf.moonrise.patches.chunk_system.level.chunk;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;

public interface ChunkSystemLevelChunk {

    public boolean moonrise$isPostProcessingDone();

    public NewChunkHolder moonrise$getChunkHolder();

    public void moonrise$setChunkHolder(final NewChunkHolder holder);

}
