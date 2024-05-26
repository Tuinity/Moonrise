package ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task;

import ca.spottedleaf.concurrentutil.completable.Completable;
import ca.spottedleaf.concurrentutil.executor.Cancellable;
import ca.spottedleaf.concurrentutil.executor.standard.DelayedPrioritisedTask;
import ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

public abstract class GenericDataLoadTask<OnMain,FinalCompletion> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenericDataLoadTask.class);

    protected static final CompoundTag CANCELLED_DATA = new CompoundTag();

    // reference count is the upper 32 bits
    protected final AtomicLong stageAndReferenceCount = new AtomicLong(STAGE_NOT_STARTED);

    protected static final long STAGE_MASK        = 0xFFFFFFFFL;
    protected static final long STAGE_CANCELLED   = 0xFFFFFFFFL;
    protected static final long STAGE_NOT_STARTED = 0L;
    protected static final long STAGE_LOADING     = 1L;
    protected static final long STAGE_PROCESSING  = 2L;
    protected static final long STAGE_COMPLETED   = 3L;

    // for loading data off disk
    protected final LoadDataFromDiskTask loadDataFromDiskTask;
    // processing off-main
    protected final PrioritisedExecutor.PrioritisedTask processOffMain;
    // processing on-main
    protected final PrioritisedExecutor.PrioritisedTask processOnMain;

    protected final ChunkTaskScheduler scheduler;
    protected final ServerLevel world;
    protected final int chunkX;
    protected final int chunkZ;
    protected final RegionFileIOThread.RegionFileType type;

    public GenericDataLoadTask(final ChunkTaskScheduler scheduler, final ServerLevel world, final int chunkX,
                               final int chunkZ, final RegionFileIOThread.RegionFileType type,
                               final PrioritisedExecutor.Priority priority) {
        this.scheduler = scheduler;
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.type = type;

        final ProcessOnMainTask mainTask;
        if (this.hasOnMain()) {
            mainTask = new ProcessOnMainTask();
            this.processOnMain = this.createOnMain(mainTask, priority);
        } else {
            mainTask = null;
            this.processOnMain = null;
        }

        final ProcessOffMainTask offMainTask;
        if (this.hasOffMain()) {
            offMainTask = new ProcessOffMainTask(mainTask);
            this.processOffMain = this.createOffMain(offMainTask, priority);
        } else {
            offMainTask = null;
            this.processOffMain = null;
        }

        if (this.processOffMain == null && this.processOnMain == null) {
            throw new IllegalStateException("Illegal class implementation: " + this.getClass().getName() + ", should be able to schedule at least one task!");
        }

        this.loadDataFromDiskTask = new LoadDataFromDiskTask(world, chunkX, chunkZ, type, new DataLoadCallback(offMainTask, mainTask), priority);
    }

    public static final record TaskResult<L, R>(L left, R right) {}

    protected abstract boolean hasOffMain();

    protected abstract boolean hasOnMain();

    protected abstract PrioritisedExecutor.PrioritisedTask createOffMain(final Runnable run, final PrioritisedExecutor.Priority priority);

    protected abstract PrioritisedExecutor.PrioritisedTask createOnMain(final Runnable run, final PrioritisedExecutor.Priority priority);

    protected abstract TaskResult<OnMain, Throwable> runOffMain(final CompoundTag data, final Throwable throwable);

    protected abstract TaskResult<FinalCompletion, Throwable> runOnMain(final OnMain data, final Throwable throwable);

    protected abstract void onComplete(final TaskResult<FinalCompletion,Throwable> result);

    protected abstract TaskResult<FinalCompletion, Throwable> completeOnMainOffMain(final OnMain data, final Throwable throwable);

    @Override
    public String toString() {
        return "GenericDataLoadTask{class: " + this.getClass().getName() + ", world: " + WorldUtil.getWorldName(this.world) +
            ", chunk: (" + this.chunkX + "," + this.chunkZ + "), hashcode: " + System.identityHashCode(this) + ", priority: " + this.getPriority() +
            ", type: " + this.type.toString() + "}";
    }

    public PrioritisedExecutor.Priority getPriority() {
        if (this.processOnMain != null) {
            return this.processOnMain.getPriority();
        } else {
            return this.processOffMain.getPriority();
        }
    }

    public void lowerPriority(final PrioritisedExecutor.Priority priority) {
        // can't lower I/O tasks, we don't know what they affect
        if (this.processOffMain != null) {
            this.processOffMain.lowerPriority(priority);
        }
        if (this.processOnMain != null) {
            this.processOnMain.lowerPriority(priority);
        }
    }

    public void setPriority(final PrioritisedExecutor.Priority priority) {
        // can't lower I/O tasks, we don't know what they affect
        this.loadDataFromDiskTask.raisePriority(priority);
        if (this.processOffMain != null) {
            this.processOffMain.setPriority(priority);
        }
        if (this.processOnMain != null) {
            this.processOnMain.setPriority(priority);
        }
    }

    public void raisePriority(final PrioritisedExecutor.Priority priority) {
        // can't lower I/O tasks, we don't know what they affect
        this.loadDataFromDiskTask.raisePriority(priority);
        if (this.processOffMain != null) {
            this.processOffMain.raisePriority(priority);
        }
        if (this.processOnMain != null) {
            this.processOnMain.raisePriority(priority);
        }
    }

    // returns whether scheduleNow() needs to be called
    public boolean schedule(final boolean delay) {
        if (this.stageAndReferenceCount.get() != STAGE_NOT_STARTED ||
            !this.stageAndReferenceCount.compareAndSet(STAGE_NOT_STARTED, (1L << 32) | STAGE_LOADING)) {
            // try and increment reference count
            int failures = 0;
            for (long curr = this.stageAndReferenceCount.get();;) {
                if ((curr & STAGE_MASK) == STAGE_CANCELLED || (curr & STAGE_MASK) == STAGE_COMPLETED) {
                    // cancelled or completed, nothing to do here
                    return false;
                }

                if (curr == (curr = this.stageAndReferenceCount.compareAndExchange(curr, curr + (1L << 32)))) {
                    // successful
                    return false;
                }

                ++failures;
                for (int i = 0; i < failures; ++i) {
                    ConcurrentUtil.backoff();
                }
            }
        }

        if (!delay) {
            this.scheduleNow();
            return false;
        }
        return true;
    }

    public void scheduleNow() {
        this.loadDataFromDiskTask.schedule(); // will schedule the rest
    }

    // assumes the current stage cannot be completed
    // returns false if cancelled, returns true if can proceed
    private boolean advanceStage(final long expect, final long to) {
        int failures = 0;
        for (long curr = this.stageAndReferenceCount.get();;) {
            if ((curr & STAGE_MASK) != expect) {
                // must be cancelled
                return false;
            }

            final long newVal = (curr & ~STAGE_MASK) | to;
            if (curr == (curr = this.stageAndReferenceCount.compareAndExchange(curr, newVal))) {
                return true;
            }

            ++failures;
            for (int i = 0; i < failures; ++i) {
                ConcurrentUtil.backoff();
            }
        }
    }

    public boolean cancel() {
        int failures = 0;
        for (long curr = this.stageAndReferenceCount.get();;) {
            if ((curr & STAGE_MASK) == STAGE_COMPLETED || (curr & STAGE_MASK) == STAGE_CANCELLED) {
                return false;
            }

             if ((curr & STAGE_MASK) == STAGE_NOT_STARTED || (curr & ~STAGE_MASK) == (1L << 32)) {
                // no other references, so we can cancel
                final long newVal = STAGE_CANCELLED;
                if (curr == (curr = this.stageAndReferenceCount.compareAndExchange(curr, newVal))) {
                    this.loadDataFromDiskTask.cancel();
                    if (this.processOffMain != null) {
                        this.processOffMain.cancel();
                    }
                    if (this.processOnMain != null) {
                        this.processOnMain.cancel();
                    }
                    this.onComplete(null);
                    return true;
                }
            } else {
                if ((curr & ~STAGE_MASK) == (0L << 32)) {
                    throw new IllegalStateException("Reference count cannot be zero here");
                }
                // just decrease the reference count
                final long newVal = curr - (1L << 32);
                if (curr == (curr = this.stageAndReferenceCount.compareAndExchange(curr, newVal))) {
                    return false;
                }
            }

            ++failures;
            for (int i = 0; i < failures; ++i) {
                ConcurrentUtil.backoff();
            }
        }
    }

    private final class DataLoadCallback implements BiConsumer<CompoundTag, Throwable> {

        private final ProcessOffMainTask offMainTask;
        private final ProcessOnMainTask onMainTask;

        public DataLoadCallback(final ProcessOffMainTask offMainTask, final ProcessOnMainTask onMainTask) {
            this.offMainTask = offMainTask;
            this.onMainTask = onMainTask;
        }

        @Override
        public void accept(final CompoundTag compoundTag, final Throwable throwable) {
            if (GenericDataLoadTask.this.stageAndReferenceCount.get() == STAGE_CANCELLED) {
                // don't try to schedule further
                return;
            }

            try {
                if (compoundTag == CANCELLED_DATA) {
                    // cancelled, except this isn't possible
                    LOGGER.error("Data callback says cancelled, but stage does not?");
                    return;
                }

                // get off of the regionfile callback ASAP, no clue what locks are held right now...
                if (GenericDataLoadTask.this.processOffMain != null) {
                    this.offMainTask.data = compoundTag;
                    this.offMainTask.throwable = throwable;
                    GenericDataLoadTask.this.processOffMain.queue();
                    return;
                } else {
                    // no off-main task, so go straight to main
                    this.onMainTask.data = (OnMain)compoundTag;
                    this.onMainTask.throwable = throwable;
                    GenericDataLoadTask.this.processOnMain.queue();
                }
            } catch (final Throwable thr2) {
                LOGGER.error("Failed I/O callback for task: " + GenericDataLoadTask.this.toString(), thr2);
                GenericDataLoadTask.this.scheduler.unrecoverableChunkSystemFailure(
                    GenericDataLoadTask.this.chunkX, GenericDataLoadTask.this.chunkZ, Map.of(
                        "Callback throwable", ChunkTaskScheduler.stringIfNull(throwable)
                    ), thr2
                );
            }
        }
    }

    private final class ProcessOffMainTask implements Runnable {

        private CompoundTag data;
        private Throwable throwable;
        private final ProcessOnMainTask schedule;

        public ProcessOffMainTask(final ProcessOnMainTask schedule) {
            this.schedule = schedule;
        }

        @Override
        public void run() {
            if (!GenericDataLoadTask.this.advanceStage(STAGE_LOADING, this.schedule == null ? STAGE_COMPLETED : STAGE_PROCESSING)) {
                // cancelled
                return;
            }
            final TaskResult<OnMain, Throwable> newData = GenericDataLoadTask.this.runOffMain(this.data, this.throwable);

            if (GenericDataLoadTask.this.stageAndReferenceCount.get() == STAGE_CANCELLED) {
                // don't try to schedule further
                return;
            }

            if (this.schedule != null) {
                final TaskResult<FinalCompletion, Throwable> syncComplete = GenericDataLoadTask.this.completeOnMainOffMain(newData.left, newData.right);

                if (syncComplete != null) {
                    if (GenericDataLoadTask.this.advanceStage(STAGE_PROCESSING, STAGE_COMPLETED)) {
                        GenericDataLoadTask.this.onComplete(syncComplete);
                    } // else: cancelled
                    return;
                }

                this.schedule.data = newData.left;
                this.schedule.throwable = newData.right;

                GenericDataLoadTask.this.processOnMain.queue();
            } else {
                GenericDataLoadTask.this.onComplete((TaskResult<FinalCompletion, Throwable>)newData);
            }
        }
    }

    private final class ProcessOnMainTask implements Runnable {

        private OnMain data;
        private Throwable throwable;

        @Override
        public void run() {
            if (!GenericDataLoadTask.this.advanceStage(STAGE_PROCESSING, STAGE_COMPLETED)) {
                // cancelled
                return;
            }
            final TaskResult<FinalCompletion, Throwable> result = GenericDataLoadTask.this.runOnMain(this.data, this.throwable);

            GenericDataLoadTask.this.onComplete(result);
        }
    }

    protected static final class LoadDataFromDiskTask {

        private volatile int priority;
        private static final VarHandle PRIORITY_HANDLE = ConcurrentUtil.getVarHandle(LoadDataFromDiskTask.class, "priority", int.class);

        private static final int PRIORITY_EXECUTED         = Integer.MIN_VALUE >>> 0;
        private static final int PRIORITY_LOAD_SCHEDULED   = Integer.MIN_VALUE >>> 1;
        private static final int PRIORITY_UNLOAD_SCHEDULED = Integer.MIN_VALUE >>> 2;

        private static final int PRIORITY_FLAGS = ~Character.MAX_VALUE;

        private final int getPriorityVolatile() {
            return (int)PRIORITY_HANDLE.getVolatile((LoadDataFromDiskTask)this);
        }

        private final int compareAndExchangePriorityVolatile(final int expect, final int update) {
            return (int)PRIORITY_HANDLE.compareAndExchange((LoadDataFromDiskTask)this, (int)expect, (int)update);
        }

        private final int getAndOrPriorityVolatile(final int val) {
            return (int)PRIORITY_HANDLE.getAndBitwiseOr((LoadDataFromDiskTask)this, (int)val);
        }

        private final void setPriorityPlain(final int val) {
            PRIORITY_HANDLE.set((LoadDataFromDiskTask)this, (int)val);
        }

        private final ServerLevel world;
        private final int chunkX;
        private final int chunkZ;

        private final RegionFileIOThread.RegionFileType type;
        private Cancellable dataLoadTask;
        private Cancellable dataUnloadCancellable;
        private DelayedPrioritisedTask dataUnloadTask;

        private final BiConsumer<CompoundTag, Throwable> onComplete;
        private final AtomicBoolean scheduled = new AtomicBoolean();

        // onComplete should be caller sensitive, it may complete synchronously with schedule() - which does
        // hold a priority lock.
        public LoadDataFromDiskTask(final ServerLevel world, final int chunkX, final int chunkZ,
                                    final RegionFileIOThread.RegionFileType type,
                                    final BiConsumer<CompoundTag, Throwable> onComplete,
                                    final PrioritisedExecutor.Priority priority) {
            if (!PrioritisedExecutor.Priority.isValidPriority(priority)) {
                throw new IllegalArgumentException("Invalid priority " + priority);
            }
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.type = type;
            this.onComplete = onComplete;
            this.setPriorityPlain(priority.priority);
        }

        private void complete(final CompoundTag data, final Throwable throwable) {
            try {
                this.onComplete.accept(data, throwable);
            } catch (final Throwable thr2) {
                ((ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().unrecoverableChunkSystemFailure(this.chunkX, this.chunkZ, Map.of(
                    "Completed throwable", ChunkTaskScheduler.stringIfNull(throwable),
                    "Regionfile type", ChunkTaskScheduler.stringIfNull(this.type)
                ), thr2);
            }
        }

        private boolean markExecuting() {
            return (this.getAndOrPriorityVolatile(PRIORITY_EXECUTED) & PRIORITY_EXECUTED) == 0;
        }

        private boolean isMarkedExecuted() {
            return (this.getPriorityVolatile() & PRIORITY_EXECUTED) != 0;
        }

        public void lowerPriority(final PrioritisedExecutor.Priority priority) {
            if (!PrioritisedExecutor.Priority.isValidPriority(priority)) {
                throw new IllegalArgumentException("Invalid priority " + priority);
            }

            int failures = 0;
            for (int curr = this.getPriorityVolatile();;) {
                if ((curr & PRIORITY_EXECUTED) != 0) {
                    // cancelled or executed
                    return;
                }

                if ((curr & PRIORITY_LOAD_SCHEDULED) != 0) {
                    RegionFileIOThread.lowerPriority(this.world, this.chunkX, this.chunkZ, this.type, priority);
                    return;
                }

                if ((curr & PRIORITY_UNLOAD_SCHEDULED) != 0) {
                    if (this.dataUnloadTask != null) {
                        this.dataUnloadTask.lowerPriority(priority);
                    }
                    // no return - we need to propagate priority
                }

                if (!priority.isHigherPriority(curr & ~PRIORITY_FLAGS)) {
                    return;
                }

                if (curr == (curr = this.compareAndExchangePriorityVolatile(curr, priority.priority | (curr & PRIORITY_FLAGS)))) {
                    return;
                }

                // failed, retry

                ++failures;
                for (int i = 0; i < failures; ++i) {
                    ConcurrentUtil.backoff();
                }
            }
        }

        public void setPriority(final PrioritisedExecutor.Priority priority) {
            if (!PrioritisedExecutor.Priority.isValidPriority(priority)) {
                throw new IllegalArgumentException("Invalid priority " + priority);
            }

            int failures = 0;
            for (int curr = this.getPriorityVolatile();;) {
                if ((curr & PRIORITY_EXECUTED) != 0) {
                    // cancelled or executed
                    return;
                }

                if ((curr & PRIORITY_LOAD_SCHEDULED) != 0) {
                    RegionFileIOThread.setPriority(this.world, this.chunkX, this.chunkZ, this.type, priority);
                    return;
                }

                if ((curr & PRIORITY_UNLOAD_SCHEDULED) != 0) {
                    if (this.dataUnloadTask != null) {
                        this.dataUnloadTask.setPriority(priority);
                    }
                    // no return - we need to propagate priority
                }

                if (curr == (curr = this.compareAndExchangePriorityVolatile(curr, priority.priority | (curr & PRIORITY_FLAGS)))) {
                    return;
                }

                // failed, retry

                ++failures;
                for (int i = 0; i < failures; ++i) {
                    ConcurrentUtil.backoff();
                }
            }
        }

        public void raisePriority(final PrioritisedExecutor.Priority priority) {
            if (!PrioritisedExecutor.Priority.isValidPriority(priority)) {
                throw new IllegalArgumentException("Invalid priority " + priority);
            }

            int failures = 0;
            for (int curr = this.getPriorityVolatile();;) {
                if ((curr & PRIORITY_EXECUTED) != 0) {
                    // cancelled or executed
                    return;
                }

                if ((curr & PRIORITY_LOAD_SCHEDULED) != 0) {
                    RegionFileIOThread.raisePriority(this.world, this.chunkX, this.chunkZ, this.type, priority);
                    return;
                }

                if ((curr & PRIORITY_UNLOAD_SCHEDULED) != 0) {
                    if (this.dataUnloadTask != null) {
                        this.dataUnloadTask.raisePriority(priority);
                    }
                    // no return - we need to propagate priority
                }

                if (!priority.isLowerPriority(curr & ~PRIORITY_FLAGS)) {
                    return;
                }

                if (curr == (curr = this.compareAndExchangePriorityVolatile(curr, priority.priority | (curr & PRIORITY_FLAGS)))) {
                    return;
                }

                // failed, retry

                ++failures;
                for (int i = 0; i < failures; ++i) {
                    ConcurrentUtil.backoff();
                }
            }
        }

        public void cancel() {
            if ((this.getAndOrPriorityVolatile(PRIORITY_EXECUTED) & PRIORITY_EXECUTED) != 0) {
                // cancelled or executed already
                return;
            }

            // OK if we miss the field read, the task cannot complete if the cancelled bit is set and
            // the write to dataLoadTask will check for the cancelled bit
            if (this.dataUnloadCancellable != null) {
                this.dataUnloadCancellable.cancel();
            }

            if (this.dataLoadTask != null) {
                this.dataLoadTask.cancel();
            }

            this.complete(CANCELLED_DATA, null);
        }

        public void schedule() {
            if (this.scheduled.getAndSet(true)) {
                throw new IllegalStateException("schedule() called twice");
            }
            int priority = this.getPriorityVolatile();

            if ((priority & PRIORITY_EXECUTED) != 0) {
                // cancelled
                return;
            }

            final BiConsumer<CompoundTag, Throwable> consumer = (final CompoundTag data, final Throwable thr) -> {
                // because cancelScheduled() cannot actually stop this task from executing in every case, we need
                // to mark complete here to ensure we do not double complete
                if (LoadDataFromDiskTask.this.markExecuting()) {
                    LoadDataFromDiskTask.this.complete(data, thr);
                } // else: cancelled
            };

            final PrioritisedExecutor.Priority initialPriority = PrioritisedExecutor.Priority.getPriority(priority);
            boolean scheduledUnload = false;

            final NewChunkHolder holder = ((ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(this.chunkX, this.chunkZ);
            if (holder != null) {
                final BiConsumer<CompoundTag, Throwable> unloadConsumer = (final CompoundTag data, final Throwable thr) -> {
                    if (data != null) {
                        consumer.accept(data, null);
                    } else {
                        // need to schedule task
                        LoadDataFromDiskTask.this.schedule(false, consumer, PrioritisedExecutor.Priority.getPriority(LoadDataFromDiskTask.this.getPriorityVolatile() & ~PRIORITY_FLAGS));
                    }
                };
                Cancellable unloadCancellable = null;
                CompoundTag syncComplete = null;
                final NewChunkHolder.UnloadTask unloadTask = holder.getUnloadTask(this.type); // can be null if no task exists
                final Completable<CompoundTag> unloadCompletable = unloadTask == null ? null : unloadTask.completable();
                if (unloadCompletable != null) {
                    unloadCancellable = unloadCompletable.addAsynchronousWaiter(unloadConsumer);
                    if (unloadCancellable == null) {
                        syncComplete = unloadCompletable.getResult();
                    }
                }

                if (syncComplete != null) {
                    consumer.accept(syncComplete, null);
                    return;
                }

                if (unloadCancellable != null) {
                    scheduledUnload = true;
                    this.dataUnloadCancellable = unloadCancellable;
                    this.dataUnloadTask = unloadTask.task();
                }
            }

            this.schedule(scheduledUnload, consumer, initialPriority);
        }

        private void schedule(final boolean scheduledUnload, final BiConsumer<CompoundTag, Throwable> consumer, final PrioritisedExecutor.Priority initialPriority) {
            int priority = this.getPriorityVolatile();

            if ((priority & PRIORITY_EXECUTED) != 0) {
                // cancelled
                return;
            }

            if (!scheduledUnload) {
                this.dataLoadTask = RegionFileIOThread.loadDataAsync(
                    this.world, this.chunkX, this.chunkZ, this.type, consumer,
                    initialPriority.isHigherPriority(PrioritisedExecutor.Priority.NORMAL), initialPriority
                );
            }

            int failures = 0;
            for (;;) {
                if (priority == (priority = this.compareAndExchangePriorityVolatile(priority, priority | (scheduledUnload ? PRIORITY_UNLOAD_SCHEDULED : PRIORITY_LOAD_SCHEDULED)))) {
                    return;
                }

                if ((priority & PRIORITY_EXECUTED) != 0) {
                    // cancelled or executed
                    if (this.dataUnloadCancellable != null) {
                        this.dataUnloadCancellable.cancel();
                    }

                    if (this.dataLoadTask != null) {
                        this.dataLoadTask.cancel();
                    }
                    return;
                }

                if (scheduledUnload) {
                    if (this.dataUnloadTask != null) {
                        this.dataUnloadTask.setPriority(PrioritisedExecutor.Priority.getPriority(priority & ~PRIORITY_FLAGS));
                    }
                } else {
                    RegionFileIOThread.setPriority(this.world, this.chunkX, this.chunkZ, this.type, PrioritisedExecutor.Priority.getPriority(priority & ~PRIORITY_FLAGS));
                }

                ++failures;
                for (int i = 0; i < failures; ++i) {
                    ConcurrentUtil.backoff();
                }
            }
        }
    }
}
