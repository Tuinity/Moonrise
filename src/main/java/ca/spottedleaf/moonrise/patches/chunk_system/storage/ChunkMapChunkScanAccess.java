package ca.spottedleaf.moonrise.patches.chunk_system.storage;

import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import org.slf4j.Logger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

public final class ChunkMapChunkScanAccess implements ChunkScanAccess {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerLevel world;
    private final Priority priority;
    private final Executor loadExecutor;

    public ChunkMapChunkScanAccess(final ServerLevel world, final Priority priority) {
        this.world = world;
        this.priority = priority;
        this.loadExecutor = (final Runnable run) -> {
            ((ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().loadExecutor.createTask(run, this.priority).queue();
        };
    }

    public ChunkMapChunkScanAccess withPriority(final Priority priority) {
        return priority == this.priority ? this : new ChunkMapChunkScanAccess(this.world, priority);
    }

    /**
     * Returns an executor which schedules onto the chunk load executor at this scanner's priority,
     * for processing scanned data (such as datafixing) off of the region file IO threads.
     */
    public Executor loadExecutor() {
        return this.loadExecutor;
    }

    @Override
    public CompletableFuture<Void> scanChunk(final ChunkPos chunkPos, final StreamTagVisitor streamTagVisitor) {
        final CompletableFuture<Void> ret = new CompletableFuture<>();

        MoonriseRegionFileIO.loadDataAsync(
            this.world, chunkPos.x(), chunkPos.z(), MoonriseRegionFileIO.RegionFileType.CHUNK_DATA,
            (BiConsumer<CompoundTag, Throwable> & MoonriseRegionFileIO.NoCopyNBTData) (final CompoundTag data, final Throwable thr) -> {
                if (thr != null) {
                    ret.completeExceptionally(thr);
                    return;
                }

                if (data == null) {
                    ret.complete(null);
                    return;
                }

                try {
                    data.acceptAsRoot(streamTagVisitor);

                    ret.complete(null);
                } catch (final Throwable thr2) {
                    ret.completeExceptionally(thr2);
                    LOGGER.error("Error while scanning chunk " + chunkPos + " in world '" + WorldUtil.getWorldName(this.world) + "':", thr2);
                }
            }, this.priority.isHigherPriority(Priority.NORMAL), this.priority
        );

        return ret;
    }
}
