package ca.spottedleaf.moonrise.patches.chunk_system;

import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import net.minecraft.server.level.ChunkLoadCounter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class MoonriseChunkLoadCounter extends ChunkLoadCounter {

    private int expected;
    private final AtomicInteger loaded = new AtomicInteger(0);

    @Override
    public void track(final ServerLevel level, final Runnable task) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<?> trackLoadWithRadius(
        final ServerLevel level,
        final ChunkPos chunkPos,
        final int chunkRadius,
        final ChunkStatus status,
        final Priority priority,
        final Runnable done
    ) {
        this.expected = (chunkRadius * 2 + 1) * (chunkRadius * 2 + 1);
        final CompletableFuture<?> ret = new CompletableFuture<>();
        ((ChunkSystemServerLevel) level).moonrise$loadChunksAsync(
            chunkPos.x - chunkRadius,
            chunkPos.x + chunkRadius,
            chunkPos.z - chunkRadius,
            chunkPos.z + chunkRadius,
            status,
            priority,
            (chunks) -> {
                if (done != null) {
                    done.run();
                }
                ret.complete(null);
            },
            (chunk) -> this.loaded.incrementAndGet()
        );
        return ret;
    }

    @Override
    public int totalChunks() {
        return this.expected;
    }

    @Override
    public int pendingChunks() {
        return this.expected - this.loaded.get();
    }

    @Override
    public int readyChunks() {
        return this.loaded.get();
    }
}
