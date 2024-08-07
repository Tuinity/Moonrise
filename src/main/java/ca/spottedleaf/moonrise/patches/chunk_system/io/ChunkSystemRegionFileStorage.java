package ca.spottedleaf.moonrise.patches.chunk_system.io;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.storage.RegionFile;
import java.io.IOException;

public interface ChunkSystemRegionFileStorage {

    public boolean moonrise$doesRegionFileNotExistNoIO(final int chunkX, final int chunkZ);

    public RegionFile moonrise$getRegionFileIfLoaded(final int chunkX, final int chunkZ);

    public RegionFile moonrise$getRegionFileIfExists(final int chunkX, final int chunkZ) throws IOException;

    public MoonriseRegionFileIO.RegionDataController.WriteData moonrise$startWrite(
            final int chunkX, final int chunkZ, final CompoundTag compound
    ) throws IOException;

    public void moonrise$finishWrite(
                    final int chunkX, final int chunkZ, final MoonriseRegionFileIO.RegionDataController.WriteData writeData
    ) throws IOException;

    public MoonriseRegionFileIO.RegionDataController.ReadData moonrise$readData(
            final int chunkX, final int chunkZ
    ) throws IOException;

    // if the return value is null, then the caller needs to re-try with a new call to readData()
    public CompoundTag moonrise$finishRead(
            final int chunkX, final int chunkZ, final MoonriseRegionFileIO.RegionDataController.ReadData readData
    ) throws IOException;
}
