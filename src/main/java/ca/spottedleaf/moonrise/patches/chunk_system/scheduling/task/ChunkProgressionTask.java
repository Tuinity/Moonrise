package ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.function.BiConsumer;

public abstract class ChunkProgressionTask {

    private final MultiThreadedQueue<BiConsumer<ChunkAccess, Throwable>> waiters = new MultiThreadedQueue<>();
    private ChunkAccess completedChunk;
    private Throwable completedThrowable;

    protected final ChunkTaskScheduler scheduler;
    protected final ServerLevel world;
    protected final int chunkX;
    protected final int chunkZ;

    protected volatile boolean completed;
    protected static final VarHandle COMPLETED_HANDLE = ConcurrentUtil.getVarHandle(ChunkProgressionTask.class, "completed", boolean.class);

    protected ChunkProgressionTask(final ChunkTaskScheduler scheduler, final ServerLevel world, final int chunkX, final int chunkZ) {
        this.scheduler = scheduler;
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    // Used only for debug json
    public abstract boolean isScheduled();

    // Note: It is the responsibility of the task to set the chunk's status once it has completed
    public abstract ChunkStatus getTargetStatus();

    /* Only executed once */
    /* Implementations must be prepared to handle cases where cancel() is called before schedule() */
    public abstract void schedule();

    /* May be called multiple times */
    public abstract void cancel();

    public abstract PrioritisedExecutor.Priority getPriority();

    /* Schedule lock is always held for the priority update calls */

    public abstract void lowerPriority(final PrioritisedExecutor.Priority priority);

    public abstract void setPriority(final PrioritisedExecutor.Priority priority);

    public abstract void raisePriority(final PrioritisedExecutor.Priority priority);

    public final void onComplete(final BiConsumer<ChunkAccess, Throwable> onComplete) {
        if (!this.waiters.add(onComplete)) {
            try {
                onComplete.accept(this.completedChunk, this.completedThrowable);
            } catch (final Throwable throwable) {
                this.scheduler.unrecoverableChunkSystemFailure(this.chunkX, this.chunkZ, Map.of(
                    "Consumer", ChunkTaskScheduler.stringIfNull(onComplete),
                    "Completed throwable", ChunkTaskScheduler.stringIfNull(this.completedThrowable)
                ), throwable);
            }
        }
    }

    protected final void complete(final ChunkAccess chunk, final Throwable throwable) {
        try {
            this.complete0(chunk, throwable);
        } catch (final Throwable thr2) {
            this.scheduler.unrecoverableChunkSystemFailure(this.chunkX, this.chunkZ, Map.of(
                "Completed throwable", ChunkTaskScheduler.stringIfNull(throwable)
            ), thr2);
        }
    }

    private void complete0(final ChunkAccess chunk, final Throwable throwable) {
        if ((boolean)COMPLETED_HANDLE.getAndSet((ChunkProgressionTask)this, (boolean)true)) {
            throw new IllegalStateException("Already completed");
        }
        this.completedChunk = chunk;
        this.completedThrowable = throwable;

        BiConsumer<ChunkAccess, Throwable> consumer;
        while ((consumer = this.waiters.pollOrBlockAdds()) != null) {
            consumer.accept(chunk, throwable);
        }
    }

    @Override
    public String toString() {
        return "ChunkProgressionTask{class: " + this.getClass().getName() + ", for world: " + WorldUtil.getWorldName(this.world) +
            ", chunk: (" + this.chunkX + "," + this.chunkZ + "), hashcode: " + System.identityHashCode(this) + ", priority: " + this.getPriority() +
            ", status: " + this.getTargetStatus().toString() + ", scheduled: " + this.isScheduled() + "}";
    }
}