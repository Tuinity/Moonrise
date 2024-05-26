package ca.spottedleaf.moonrise.patches.chunk_system.io;

import net.minecraft.world.level.chunk.storage.RegionFile;
import java.io.IOException;

public interface ChunkSystemRegionFileStorage {

    public boolean moonrise$doesRegionFileNotExistNoIO(final int chunkX, final int chunkZ);

    public RegionFile moonrise$getRegionFileIfLoaded(final int chunkX, final int chunkZ);

    public RegionFile moonrise$getRegionFileIfExists(final int chunkX, final int chunkZ) throws IOException;

}
