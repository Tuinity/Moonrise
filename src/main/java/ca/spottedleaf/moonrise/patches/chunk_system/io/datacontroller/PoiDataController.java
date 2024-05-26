package ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller;

import ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread;
import ca.spottedleaf.moonrise.patches.chunk_system.level.storage.ChunkSystemSectionStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import java.io.IOException;

public final class PoiDataController extends RegionFileIOThread.ChunkDataController {

    private final ServerLevel world;

    public PoiDataController(final ServerLevel world) {
        super(RegionFileIOThread.RegionFileType.POI_DATA);
        this.world = world;
    }

    @Override
    public RegionFileStorage getCache() {
        return ((ChunkSystemSectionStorage)this.world.getPoiManager()).moonrise$getRegionStorage();
    }

    @Override
    public void writeData(final int chunkX, final int chunkZ, final CompoundTag compound) throws IOException {
        ((ChunkSystemSectionStorage)this.world.getPoiManager()).moonrise$write(chunkX, chunkZ, compound);
    }

    @Override
    public CompoundTag readData(final int chunkX, final int chunkZ) throws IOException {
        return ((ChunkSystemSectionStorage)this.world.getPoiManager()).moonrise$read(chunkX, chunkZ);
    }
}
