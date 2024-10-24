package ca.spottedleaf.moonrise.patches.chunk_system.level.storage;

import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import java.io.IOException;

public interface ChunkSystemSectionStorage {

    public RegionFileStorage moonrise$getRegionStorage();

    public void moonrise$close() throws IOException;

}
