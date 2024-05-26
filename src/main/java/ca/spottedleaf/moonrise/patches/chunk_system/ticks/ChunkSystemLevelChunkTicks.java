package ca.spottedleaf.moonrise.patches.chunk_system.ticks;

public interface ChunkSystemLevelChunkTicks {

    public boolean moonrise$isDirty(final long tick);

    public void moonrise$clearDirty();

}
