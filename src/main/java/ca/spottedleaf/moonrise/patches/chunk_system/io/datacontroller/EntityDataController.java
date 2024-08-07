package ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller;

import ca.spottedleaf.moonrise.patches.chunk_system.io.ChunkSystemRegionFileStorage;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import java.io.IOException;
import java.nio.file.Path;

public final class EntityDataController extends MoonriseRegionFileIO.RegionDataController {

    private final EntityRegionFileStorage storage;

    public EntityDataController(final EntityRegionFileStorage storage, final ChunkTaskScheduler taskScheduler) {
        super(MoonriseRegionFileIO.RegionFileType.ENTITY_DATA, taskScheduler.ioExecutor, taskScheduler.compressionExecutor);
        this.storage = storage;
    }

    @Override
    public RegionFileStorage getCache() {
        return this.storage;
    }

    @Override
    public WriteData startWrite(final int chunkX, final int chunkZ, final CompoundTag compound) throws IOException {
        checkPosition(new ChunkPos(chunkX, chunkZ), compound);

        return ((ChunkSystemRegionFileStorage)this.getCache()).moonrise$startWrite(chunkX, chunkZ, compound);
    }

    @Override
    public void finishWrite(final int chunkX, final int chunkZ, final WriteData writeData) throws IOException {
        ((ChunkSystemRegionFileStorage)this.getCache()).moonrise$finishWrite(chunkX, chunkZ, writeData);
    }

    @Override
    public ReadData readData(final int chunkX, final int chunkZ) throws IOException {
        return ((ChunkSystemRegionFileStorage)this.getCache()).moonrise$readData(chunkX, chunkZ);
    }

    @Override
    public CompoundTag finishRead(final int chunkX, final int chunkZ, final ReadData readData) throws IOException {
        return ((ChunkSystemRegionFileStorage)this.getCache()).moonrise$finishRead(chunkX, chunkZ, readData);
    }

    private static void checkPosition(final ChunkPos pos, final CompoundTag nbt) {
        final ChunkPos nbtPos = nbt == null ? null : EntityStorage.readChunkPos(nbt);
        if (nbtPos != null && !pos.equals(nbtPos)) {
            throw new IllegalArgumentException(
                    "Entity chunk coordinate and serialized data do not have matching coordinates, trying to serialize coordinate " + pos.toString()
                            + " but compound says coordinate is " + nbtPos
            );
        }
    }

    public static final class EntityRegionFileStorage extends RegionFileStorage {

        public EntityRegionFileStorage(final RegionStorageInfo regionStorageInfo, final Path directory,
                                       final boolean dsync) {
            super(regionStorageInfo, directory, dsync);
        }

        @Override
        public void write(final ChunkPos pos, final CompoundTag nbt) throws IOException {
            checkPosition(pos, nbt);
            super.write(pos, nbt);
        }
    }
}
