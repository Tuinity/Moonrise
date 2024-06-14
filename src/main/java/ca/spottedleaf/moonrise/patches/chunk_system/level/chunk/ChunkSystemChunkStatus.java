package ca.spottedleaf.moonrise.patches.chunk_system.level.chunk;

import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.concurrent.atomic.AtomicBoolean;

public interface ChunkSystemChunkStatus {

    public boolean moonrise$isParallelCapable();

    public void moonrise$setParallelCapable(final boolean value);

    public int moonrise$getWriteRadius();

    public void moonrise$setWriteRadius(final int value);

    public ChunkStatus moonrise$getNextStatus();

    public boolean moonrise$isEmptyLoadStatus();

    public void moonrise$setEmptyLoadStatus(final boolean value);

    public boolean moonrise$isEmptyGenStatus();

    public AtomicBoolean moonrise$getWarnedAboutNoImmediateComplete();

}
