package ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller;

import ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import java.io.IOException;
import java.nio.file.Path;

public final class EntityDataController extends RegionFileIOThread.ChunkDataController {

    private final EntityRegionFileStorage storage;

    public EntityDataController(final EntityRegionFileStorage storage) {
        super(RegionFileIOThread.RegionFileType.ENTITY_DATA);
        this.storage = storage;
    }

    @Override
    public RegionFileStorage getCache() {
        return this.storage;
    }

    @Override
    public void writeData(final int chunkX, final int chunkZ, final CompoundTag compound) throws IOException {
        this.storage.write(new ChunkPos(chunkX, chunkZ), compound);
    }

    @Override
    public CompoundTag readData(final int chunkX, final int chunkZ) throws IOException {
        return this.storage.read(new ChunkPos(chunkX, chunkZ));
    }

    public static final class EntityRegionFileStorage extends RegionFileStorage {

        public EntityRegionFileStorage(final RegionStorageInfo regionStorageInfo, final Path directory,
                                       final boolean dsync) {
            super(regionStorageInfo, directory, dsync);
        }

        @Override
        public void write(final ChunkPos pos, final CompoundTag nbt) throws IOException {
            final ChunkPos nbtPos = nbt == null ? null : EntityStorage.readChunkPos(nbt);
            if (nbtPos != null && !pos.equals(nbtPos)) {
                throw new IllegalArgumentException(
                        "Entity chunk coordinate and serialized data do not have matching coordinates, trying to serialize coordinate " + pos.toString()
                                + " but compound says coordinate is " + nbtPos + " for world: " + this
                );
            }
            super.write(pos, nbt);
        }
    }
}
