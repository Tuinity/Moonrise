package ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task;

import ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkStatus;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ChunkUpgradeGenericStatusTask extends ChunkProgressionTask implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkUpgradeGenericStatusTask.class);

    private final ChunkAccess fromChunk;
    private final ChunkStatus fromStatus;
    private final ChunkStatus toStatus;
    private final StaticCache2D<GenerationChunkHolder> neighbours;

    private final PrioritisedExecutor.PrioritisedTask generateTask;

    public ChunkUpgradeGenericStatusTask(final ChunkTaskScheduler scheduler, final ServerLevel world, final int chunkX,
                                         final int chunkZ, final ChunkAccess chunk, final StaticCache2D<GenerationChunkHolder> neighbours,
                                         final ChunkStatus toStatus, final PrioritisedExecutor.Priority priority) {
        super(scheduler, world, chunkX, chunkZ);
        if (!PrioritisedExecutor.Priority.isValidPriority(priority)) {
            throw new IllegalArgumentException("Invalid priority " + priority);
        }
        this.fromChunk = chunk;
        this.fromStatus = chunk.getPersistedStatus();
        this.toStatus = toStatus;
        this.neighbours = neighbours;
        if (((ChunkSystemChunkStatus)this.toStatus).moonrise$isParallelCapable()) {
            this.generateTask = this.scheduler.parallelGenExecutor.createTask(this, priority);
        } else {
            final int writeRadius = ((ChunkSystemChunkStatus)this.toStatus).moonrise$getWriteRadius();
            if (writeRadius < 0) {
                this.generateTask = this.scheduler.radiusAwareScheduler.createInfiniteRadiusTask(this, priority);
            } else {
                this.generateTask = this.scheduler.radiusAwareScheduler.createTask(chunkX, chunkZ, writeRadius, this, priority);
            }
        }
    }

    @Override
    public ChunkStatus getTargetStatus() {
        return this.toStatus;
    }

    private boolean isEmptyTask() {
        // must use fromStatus here to avoid any race condition with run() overwriting the status
        final boolean generation = !this.fromStatus.isOrAfter(this.toStatus);
        return (generation && ((ChunkSystemChunkStatus)this.toStatus).moonrise$isEmptyGenStatus()) || (!generation && ((ChunkSystemChunkStatus)this.toStatus).moonrise$isEmptyLoadStatus());
    }

    @Override
    public void run() {
        final ChunkAccess chunk = this.fromChunk;

        final ServerChunkCache serverChunkCache = this.world.getChunkSource();
        final ChunkMap chunkMap = serverChunkCache.chunkMap;

        final CompletableFuture<ChunkAccess> completeFuture;

        final boolean generation;
        boolean completing = false;

        // note: should optimise the case where the chunk does not need to execute the status, because
        // schedule() calls this synchronously if it will run through that path

        final WorldGenContext ctx = chunkMap.worldGenContext;
        try {
            generation = !chunk.getPersistedStatus().isOrAfter(this.toStatus);
            if (generation) {
                if (((ChunkSystemChunkStatus)this.toStatus).moonrise$isEmptyGenStatus()) {
                    if (chunk instanceof ProtoChunk) {
                        ((ProtoChunk)chunk).setPersistedStatus(this.toStatus);
                    }
                    completing = true;
                    this.complete(chunk, null);
                    return;
                }
                completeFuture = ChunkPyramid.GENERATION_PYRAMID.getStepTo(this.toStatus).apply(ctx, this.neighbours, this.fromChunk)
                        .whenComplete((final ChunkAccess either, final Throwable throwable) -> {
                                    if (either instanceof ProtoChunk proto) {
                                        proto.setPersistedStatus(ChunkUpgradeGenericStatusTask.this.toStatus);
                                    }
                                }
                        );
            } else {
                if (((ChunkSystemChunkStatus)this.toStatus).moonrise$isEmptyLoadStatus()) {
                    completing = true;
                    this.complete(chunk, null);
                    return;
                }
                completeFuture = ChunkPyramid.LOADING_PYRAMID.getStepTo(this.toStatus).apply(ctx, this.neighbours, this.fromChunk);
            }
        } catch (final Throwable throwable) {
            if (!completing) {
                this.complete(null, throwable);
                return;
            }

            this.scheduler.unrecoverableChunkSystemFailure(this.chunkX, this.chunkZ, Map.of(
                "Target status", ChunkTaskScheduler.stringIfNull(this.toStatus),
                "From status", ChunkTaskScheduler.stringIfNull(this.fromStatus),
                "Generation task", this
            ), throwable);

            LOGGER.error(
                    "Failed to complete status for chunk: status:" + this.toStatus + ", chunk: (" + this.chunkX +
                            "," + this.chunkZ + "), world: " + WorldUtil.getWorldName(this.world),
                    throwable
            );

            return;
        }

        if (!completeFuture.isDone() && !((ChunkSystemChunkStatus)this.toStatus).moonrise$getWarnedAboutNoImmediateComplete().getAndSet(true)) {
            LOGGER.warn("Future status not complete after scheduling: " + this.toStatus.toString() + ", generate: " + generation);
        }

        final ChunkAccess newChunk;

        try {
            newChunk = completeFuture.join();
        } catch (final Throwable throwable) {
            this.complete(null, throwable);
            return;
        }

        if (newChunk == null) {
            this.complete(null,
                    new IllegalStateException(
                            "Chunk for status: " + ChunkUpgradeGenericStatusTask.this.toStatus.toString()
                                    + ", generation: " + generation + " should not be null! Future: " + completeFuture
                    ).fillInStackTrace()
            );
            return;
        }

        this.complete(newChunk, null);
    }

    private volatile boolean scheduled;
    private static final VarHandle SCHEDULED_HANDLE = ConcurrentUtil.getVarHandle(ChunkUpgradeGenericStatusTask.class, "scheduled", boolean.class);

    @Override
    public boolean isScheduled() {
        return this.scheduled;
    }

    @Override
    public void schedule() {
        if ((boolean)SCHEDULED_HANDLE.getAndSet((ChunkUpgradeGenericStatusTask)this, true)) {
            throw new IllegalStateException("Cannot double call schedule()");
        }
        if (this.isEmptyTask()) {
            if (this.generateTask.cancel()) {
                this.run();
            }
        } else {
            this.generateTask.queue();
        }
    }

    @Override
    public void cancel() {
        if (this.generateTask.cancel()) {
            this.complete(null, null);
        }
    }

    @Override
    public PrioritisedExecutor.Priority getPriority() {
        return this.generateTask.getPriority();
    }

    @Override
    public void lowerPriority(final PrioritisedExecutor.Priority priority) {
        if (!PrioritisedExecutor.Priority.isValidPriority(priority)) {
            throw new IllegalArgumentException("Invalid priority " + priority);
        }
        this.generateTask.lowerPriority(priority);
    }

    @Override
    public void setPriority(final PrioritisedExecutor.Priority priority) {
        if (!PrioritisedExecutor.Priority.isValidPriority(priority)) {
            throw new IllegalArgumentException("Invalid priority " + priority);
        }
        this.generateTask.setPriority(priority);
    }

    @Override
    public void raisePriority(final PrioritisedExecutor.Priority priority) {
        if (!PrioritisedExecutor.Priority.isValidPriority(priority)) {
            throw new IllegalArgumentException("Invalid priority " + priority);
        }
        this.generateTask.raisePriority(priority);
    }
}
