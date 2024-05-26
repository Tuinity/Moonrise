package ca.spottedleaf.moonrise.patches.chunk_system.player;

public interface ChunkSystemServerPlayer {

    public boolean moonrise$isRealPlayer();

    public void moonrise$setRealPlayer(final boolean real);

    public RegionizedPlayerChunkLoader.PlayerChunkLoaderData moonrise$getChunkLoader();

    public void moonrise$setChunkLoader(final RegionizedPlayerChunkLoader.PlayerChunkLoaderData loader);

    public RegionizedPlayerChunkLoader.ViewDistanceHolder moonrise$getViewDistanceHolder();

}
