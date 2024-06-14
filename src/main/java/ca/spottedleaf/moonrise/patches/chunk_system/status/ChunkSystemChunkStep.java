package ca.spottedleaf.moonrise.patches.chunk_system.status;

import net.minecraft.world.level.chunk.status.ChunkStatus;

public interface ChunkSystemChunkStep {

    public ChunkStatus moonrise$getRequiredStatusAtRadius(final int radius);

}
