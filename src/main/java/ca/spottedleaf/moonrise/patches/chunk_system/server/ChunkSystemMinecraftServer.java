package ca.spottedleaf.moonrise.patches.chunk_system.server;

public interface ChunkSystemMinecraftServer {

    public void moonrise$setChunkSystemCrash(final Throwable throwable);

    public void moonrise$executeMidTickTasks();

}
