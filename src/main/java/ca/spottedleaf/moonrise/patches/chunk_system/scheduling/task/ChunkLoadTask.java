package ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.lock.ReentrantAreaLock;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.ChunkSystemConverters;
import ca.spottedleaf.moonrise.patches.chunk_system.ChunkSystemFeatures;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.level.poi.PoiChunk;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class ChunkLoadTask extends ChunkProgressionTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkLoadTask.class);

    private final NewChunkHolder chunkHolder;
    private final ChunkDataLoadTask loadTask;

    private volatile boolean cancelled;
    private NewChunkHolder.GenericDataLoadTaskCallback entityLoadTask;
    private NewChunkHolder.GenericDataLoadTaskCallback poiLoadTask;
    private GenericDataLoadTask.TaskResult<ChunkAccess, Throwable> loadResult;
    private final AtomicInteger taskCountToComplete = new AtomicInteger(3); // one for poi, one for entity, and one for chunk data

    public ChunkLoadTask(final ChunkTaskScheduler scheduler, final ServerLevel world, final int chunkX, final int chunkZ,
                         final NewChunkHolder chunkHolder, final Priority priority) {
        super(scheduler, world, chunkX, chunkZ);
        this.chunkHolder = chunkHolder;
        this.loadTask = new ChunkDataLoadTask(scheduler, world, chunkX, chunkZ, priority);
        this.loadTask.addCallback((final GenericDataLoadTask.TaskResult<ChunkAccess, Throwable> result) -> {
            ChunkLoadTask.this.loadResult = result; // must be before getAndDecrement
            ChunkLoadTask.this.tryCompleteLoad();
        });
    }

    private void tryCompleteLoad() {
        final int count = this.taskCountToComplete.decrementAndGet();
        if (count == 0) {
            final GenericDataLoadTask.TaskResult<ChunkAccess, Throwable> result = this.cancelled ? null : this.loadResult; // only after the getAndDecrement
            ChunkLoadTask.this.complete(result == null ? null : result.left(), result == null ? null : result.right());
        } else if (count < 0) {
            throw new IllegalStateException("Called tryCompleteLoad() too many times");
        }
    }

    @Override
    public ChunkStatus getTargetStatus() {
        return ChunkStatus.EMPTY;
    }

    private boolean scheduled;

    @Override
    public boolean isScheduled() {
        return this.scheduled;
    }

    @Override
    public void schedule() {
        final NewChunkHolder.GenericDataLoadTaskCallback entityLoadTask;
        final NewChunkHolder.GenericDataLoadTaskCallback poiLoadTask;

        final Consumer<GenericDataLoadTask.TaskResult<?, ?>> scheduleLoadTask = (final GenericDataLoadTask.TaskResult<?, ?> result) -> {
            ChunkLoadTask.this.tryCompleteLoad();
        };

        // NOTE: it is IMPOSSIBLE for getOrLoadEntityData/getOrLoadPoiData to complete synchronously, because
        // they must schedule a task to off main or to on main to complete
        final ReentrantAreaLock.Node schedulingLock = this.scheduler.schedulingLockArea.lock(this.chunkX, this.chunkZ);
        try {
            if (this.scheduled) {
                throw new IllegalStateException("schedule() called twice");
            }
            this.scheduled = true;
            if (this.cancelled) {
                return;
            }
            if (!this.chunkHolder.isEntityChunkNBTLoaded()) {
                entityLoadTask = this.chunkHolder.getOrLoadEntityData((Consumer)scheduleLoadTask);
            } else {
                entityLoadTask = null;
                this.tryCompleteLoad();
            }

            if (!this.chunkHolder.isPoiChunkLoaded()) {
                poiLoadTask = this.chunkHolder.getOrLoadPoiData((Consumer)scheduleLoadTask);
            } else {
                poiLoadTask = null;
                this.tryCompleteLoad();
            }

            this.entityLoadTask = entityLoadTask;
            this.poiLoadTask = poiLoadTask;
        } finally {
            this.scheduler.schedulingLockArea.unlock(schedulingLock);
        }

        if (entityLoadTask != null) {
            entityLoadTask.schedule();
        }

        if (poiLoadTask != null) {
            poiLoadTask.schedule();
        }

        this.loadTask.schedule(false);
    }

    @Override
    public void cancel() {
        // must be before load task access, so we can synchronise with the writes to the fields
        final boolean scheduled;
        final ReentrantAreaLock.Node schedulingLock = this.scheduler.schedulingLockArea.lock(this.chunkX, this.chunkZ);
        try {
            // must read field here, as it may be written later conucrrently -
            // we need to know if we scheduled _before_ cancellation
            scheduled = this.scheduled;
            this.cancelled = true;
        } finally {
            this.scheduler.schedulingLockArea.unlock(schedulingLock);
        }

        /*
        Note: The entityLoadTask/poiLoadTask do not complete when cancelled,
        so we need to manually try to complete in those cases
        It is also important to note that we set the cancelled field first, just in case
        the chunk load task attempts to complete with a non-null value
        */

        if (scheduled) {
            // since we scheduled, we need to cancel the tasks
            if (this.entityLoadTask != null) {
                if (this.entityLoadTask.cancel()) {
                    this.tryCompleteLoad();
                }
            }
            if (this.poiLoadTask != null) {
                if (this.poiLoadTask.cancel()) {
                    this.tryCompleteLoad();
                }
            }
        } else {
            // since nothing was scheduled, we need to decrement the task count here ourselves

            // for entity load task
            this.tryCompleteLoad();

            // for poi load task
            this.tryCompleteLoad();
        }
        this.loadTask.cancel();
    }

    @Override
    public Priority getPriority() {
        return this.loadTask.getPriority();
    }

    @Override
    public void lowerPriority(final Priority priority) {
        final EntityDataLoadTask entityLoad = this.chunkHolder.getEntityDataLoadTask();
        if (entityLoad != null) {
            entityLoad.lowerPriority(priority);
        }

        final PoiDataLoadTask poiLoad = this.chunkHolder.getPoiDataLoadTask();

        if (poiLoad != null) {
            poiLoad.lowerPriority(priority);
        }

        this.loadTask.lowerPriority(priority);
    }

    @Override
    public void setPriority(final Priority priority) {
        final EntityDataLoadTask entityLoad = this.chunkHolder.getEntityDataLoadTask();
        if (entityLoad != null) {
            entityLoad.setPriority(priority);
        }

        final PoiDataLoadTask poiLoad = this.chunkHolder.getPoiDataLoadTask();

        if (poiLoad != null) {
            poiLoad.setPriority(priority);
        }

        this.loadTask.setPriority(priority);
    }

    @Override
    public void raisePriority(final Priority priority) {
        final EntityDataLoadTask entityLoad = this.chunkHolder.getEntityDataLoadTask();
        if (entityLoad != null) {
            entityLoad.raisePriority(priority);
        }

        final PoiDataLoadTask poiLoad = this.chunkHolder.getPoiDataLoadTask();

        if (poiLoad != null) {
            poiLoad.raisePriority(priority);
        }

        this.loadTask.raisePriority(priority);
    }

    protected static abstract class CallbackDataLoadTask<OnMain,FinalCompletion> extends GenericDataLoadTask<OnMain,FinalCompletion> {

        private TaskResult<FinalCompletion, Throwable> result;
        private final MultiThreadedQueue<Consumer<TaskResult<FinalCompletion, Throwable>>> waiters = new MultiThreadedQueue<>();

        protected volatile boolean completed;
        protected static final VarHandle COMPLETED_HANDLE = ConcurrentUtil.getVarHandle(CallbackDataLoadTask.class, "completed", boolean.class);

        protected CallbackDataLoadTask(final ChunkTaskScheduler scheduler, final ServerLevel world, final int chunkX,
                                       final int chunkZ, final MoonriseRegionFileIO.RegionFileType type,
                                       final Priority priority) {
            super(scheduler, world, chunkX, chunkZ, type, priority);
        }

        public void addCallback(final Consumer<TaskResult<FinalCompletion, Throwable>> consumer) {
            if (!this.waiters.add(consumer)) {
                try {
                    consumer.accept(this.result);
                } catch (final Throwable throwable) {
                    this.scheduler.unrecoverableChunkSystemFailure(this.chunkX, this.chunkZ, Map.of(
                        "Consumer", ChunkTaskScheduler.stringIfNull(consumer),
                        "Completed throwable", ChunkTaskScheduler.stringIfNull(this.result.right()),
                        "CallbackDataLoadTask impl", this.getClass().getName()
                    ), throwable);
                }
            }
        }

        @Override
        protected void onComplete(final TaskResult<FinalCompletion, Throwable> result) {
            if ((boolean)COMPLETED_HANDLE.getAndSet((CallbackDataLoadTask)this, (boolean)true)) {
                throw new IllegalStateException("Already completed");
            }
            this.result = result;
            Consumer<TaskResult<FinalCompletion, Throwable>> consumer;
            while ((consumer = this.waiters.pollOrBlockAdds()) != null) {
                try {
                    consumer.accept(result);
                } catch (final Throwable throwable) {
                    this.scheduler.unrecoverableChunkSystemFailure(this.chunkX, this.chunkZ, Map.of(
                        "Consumer", ChunkTaskScheduler.stringIfNull(consumer),
                        "Completed throwable", ChunkTaskScheduler.stringIfNull(result.right()),
                        "CallbackDataLoadTask impl", this.getClass().getName()
                    ), throwable);
                    return;
                }
            }
        }
    }

    private static final class ChunkDataLoadTask extends CallbackDataLoadTask<CompoundTag, ChunkAccess> {
        private ChunkDataLoadTask(final ChunkTaskScheduler scheduler, final ServerLevel world, final int chunkX,
                                    final int chunkZ, final Priority priority) {
            super(scheduler, world, chunkX, chunkZ, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA, priority);
        }

        @Override
        protected boolean hasOffMain() {
            return true;
        }

        @Override
        protected boolean hasOnMain() {
            return true;
        }

        @Override
        protected PrioritisedExecutor.PrioritisedTask createOffMain(final Runnable run, final Priority priority) {
            return this.scheduler.loadExecutor.createTask(run, priority);
        }

        @Override
        protected PrioritisedExecutor.PrioritisedTask createOnMain(final Runnable run, final Priority priority) {
            return this.scheduler.createChunkTask(this.chunkX, this.chunkZ, run, priority);
        }

        @Override
        protected TaskResult<ChunkAccess, Throwable> completeOnMainOffMain(final CompoundTag data, final Throwable throwable) {
            if (throwable != null) {
                return new TaskResult<>(null, throwable);
            }
            if (data == null) {
                return new TaskResult<>(this.getEmptyChunk(), null);
            }

            if (ChunkSystemFeatures.supportsAsyncChunkDeserialization()) {
                return this.deserialize(data);
            }
            // need to deserialize on main thread
            return null;
        }

        private ProtoChunk getEmptyChunk() {
            return new ProtoChunk(
                new ChunkPos(this.chunkX, this.chunkZ), UpgradeData.EMPTY, this.world,
                this.world.registryAccess().registryOrThrow(Registries.BIOME), (BlendingData)null
            );
        }

        @Override
        protected TaskResult<CompoundTag, Throwable> runOffMain(final CompoundTag data, final Throwable throwable) {
            if (throwable != null) {
                LOGGER.error("Failed to load chunk data for task: " + this.toString() + ", chunk data will be lost", throwable);
                return new TaskResult<>(null, null);
            }

            if (data == null) {
                return new TaskResult<>(null, null);
            }

            try {
                // run converters
                final CompoundTag converted = this.world.getChunkSource().chunkMap.upgradeChunkTag(data);

                return new TaskResult<>(converted, null);
            } catch (final Throwable thr2) {
                LOGGER.error("Failed to parse chunk data for task: " + this.toString() + ", chunk data will be lost", thr2);
                return new TaskResult<>(null, null);
            }
        }

        private TaskResult<ChunkAccess, Throwable> deserialize(final CompoundTag data) {
            try {
                final ChunkAccess deserialized = ChunkSerializer.read(
                        this.world, this.world.getPoiManager(), this.world.getChunkSource().chunkMap.storageInfo(), new ChunkPos(this.chunkX, this.chunkZ), data
                );
                return new TaskResult<>(deserialized, null);
            } catch (final Throwable thr2) {
                LOGGER.error("Failed to parse chunk data for task: " + this.toString() + ", chunk data will be lost", thr2);
                return new TaskResult<>(this.getEmptyChunk(), null);
            }
        }

        @Override
        protected TaskResult<ChunkAccess, Throwable> runOnMain(final CompoundTag data, final Throwable throwable) {
            // data != null && throwable == null
            if (ChunkSystemFeatures.supportsAsyncChunkDeserialization()) {
                throw new UnsupportedOperationException();
            }
            return this.deserialize(data);
        }
    }

    public static final class PoiDataLoadTask extends CallbackDataLoadTask<PoiChunk, PoiChunk> {

        public PoiDataLoadTask(final ChunkTaskScheduler scheduler, final ServerLevel world, final int chunkX,
                               final int chunkZ, final Priority priority) {
            super(scheduler, world, chunkX, chunkZ, MoonriseRegionFileIO.RegionFileType.POI_DATA, priority);
        }

        @Override
        protected boolean hasOffMain() {
            return true;
        }

        @Override
        protected boolean hasOnMain() {
            return false;
        }

        @Override
        protected PrioritisedExecutor.PrioritisedTask createOffMain(final Runnable run, final Priority priority) {
            return this.scheduler.loadExecutor.createTask(run, priority);
        }

        @Override
        protected PrioritisedExecutor.PrioritisedTask createOnMain(final Runnable run, final Priority priority) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected TaskResult<PoiChunk, Throwable> completeOnMainOffMain(final PoiChunk data, final Throwable throwable) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected TaskResult<PoiChunk, Throwable> runOffMain(final CompoundTag data, final Throwable throwable) {
            if (throwable != null) {
                LOGGER.error("Failed to load poi data for task: " + this.toString() + ", poi data will be lost", throwable);
                return new TaskResult<>(PoiChunk.empty(this.world, this.chunkX, this.chunkZ), null);
            }

            if (data == null || data.isEmpty()) {
                // nothing to do
                return new TaskResult<>(PoiChunk.empty(this.world, this.chunkX, this.chunkZ), null);
            }

            try {
                // run converters
                final CompoundTag converted = ChunkSystemConverters.convertPoiCompoundTag(data, this.world);

                // now we need to parse it
                return new TaskResult<>(PoiChunk.parse(this.world, this.chunkX, this.chunkZ, converted), null);
            } catch (final Throwable thr2) {
                LOGGER.error("Failed to run parse poi data for task: " + this.toString() + ", poi data will be lost", thr2);
                return new TaskResult<>(PoiChunk.empty(this.world, this.chunkX, this.chunkZ), null);
            }
        }

        @Override
        protected TaskResult<PoiChunk, Throwable> runOnMain(final PoiChunk data, final Throwable throwable) {
            throw new UnsupportedOperationException();
        }
    }

    public static final class EntityDataLoadTask extends CallbackDataLoadTask<CompoundTag, CompoundTag> {

        public EntityDataLoadTask(final ChunkTaskScheduler scheduler, final ServerLevel world, final int chunkX,
                                  final int chunkZ, final Priority priority) {
            super(scheduler, world, chunkX, chunkZ, MoonriseRegionFileIO.RegionFileType.ENTITY_DATA, priority);
        }

        @Override
        protected boolean hasOffMain() {
            return true;
        }

        @Override
        protected boolean hasOnMain() {
            return false;
        }

        @Override
        protected PrioritisedExecutor.PrioritisedTask createOffMain(final Runnable run, final Priority priority) {
            return this.scheduler.loadExecutor.createTask(run, priority);
        }

        @Override
        protected PrioritisedExecutor.PrioritisedTask createOnMain(final Runnable run, final Priority priority) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected TaskResult<CompoundTag, Throwable> completeOnMainOffMain(final CompoundTag data, final Throwable throwable) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected TaskResult<CompoundTag, Throwable> runOffMain(final CompoundTag data, final Throwable throwable) {
            if (throwable != null) {
                LOGGER.error("Failed to load entity data for task: " + this.toString() + ", entity data will be lost", throwable);
                return new TaskResult<>(null, null);
            }

            if (data == null || data.isEmpty()) {
                // nothing to do
                return new TaskResult<>(null, null);
            }

            try {
                return new TaskResult<>(ChunkSystemConverters.convertEntityChunkCompoundTag(data, this.world), null);
            } catch (final Throwable thr2) {
                LOGGER.error("Failed to run converters for entity data for task: " + this.toString() + ", entity data will be lost", thr2);
                return new TaskResult<>(null, thr2);
            }
        }

        @Override
        protected TaskResult<CompoundTag, Throwable> runOnMain(final CompoundTag data, final Throwable throwable) {
            throw new UnsupportedOperationException();
        }
    }
}
