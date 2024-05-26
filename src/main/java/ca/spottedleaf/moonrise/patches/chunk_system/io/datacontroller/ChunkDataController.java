package ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller;

import ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread;
import ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemChunkStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class ChunkDataController extends RegionFileIOThread.ChunkDataController {

    private final ServerLevel world;

    public ChunkDataController(final ServerLevel world) {
        super(RegionFileIOThread.RegionFileType.CHUNK_DATA);
        this.world = world;
    }

    @Override
    public RegionFileStorage getCache() {
        return ((ChunkSystemChunkStorage)this.world.getChunkSource().chunkMap).moonrise$getRegionStorage();
    }

    @Override
    public void writeData(final int chunkX, final int chunkZ, final CompoundTag compound) throws IOException {
        final CompletableFuture<Void> future = this.world.getChunkSource().chunkMap.write(new ChunkPos(chunkX, chunkZ), compound);

        try {
            if (future != null) {
                // rets non-null when sync writing (i.e. future should be completed here)
                future.join();
            }
        } catch (final CompletionException ex) {
            if (ex.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw ex;
        }
    }

    @Override
    public CompoundTag readData(final int chunkX, final int chunkZ) throws IOException {
        try {
            return this.world.getChunkSource().chunkMap.read(new ChunkPos(chunkX, chunkZ)).join().orElse(null);
        } catch (final CompletionException ex) {
            if (ex.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw ex;
        }
    }
}
