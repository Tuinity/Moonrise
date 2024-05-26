package ca.spottedleaf.moonrise.patches.chunk_system.level.poi;

import ca.spottedleaf.moonrise.patches.chunk_system.level.storage.ChunkSystemSectionStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

public interface ChunkSystemPoiManager extends ChunkSystemSectionStorage {

    public ServerLevel moonrise$getWorld();

    public void moonrise$onUnload(final long coordinate);

    public void moonrise$loadInPoiChunk(final PoiChunk poiChunk);

    public void moonrise$checkConsistency(final ChunkAccess chunk);

}
