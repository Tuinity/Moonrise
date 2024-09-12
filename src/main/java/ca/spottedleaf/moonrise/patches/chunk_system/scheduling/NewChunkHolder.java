package ca.spottedleaf.moonrise.patches.chunk_system.scheduling;

import ca.spottedleaf.concurrentutil.completable.CallbackCompletable;
import ca.spottedleaf.concurrentutil.executor.Cancellable;
import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.lock.ReentrantAreaLock;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.common.misc.LazyRunnable;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.common.util.ChunkSystem;
import ca.spottedleaf.moonrise.patches.chunk_system.ChunkSystemFeatures;
import ca.spottedleaf.moonrise.patches.chunk_system.async_save.AsyncChunkSaveData;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkStatus;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.level.poi.ChunkSystemPoiManager;
import ca.spottedleaf.moonrise.patches.chunk_system.level.poi.PoiChunk;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task.ChunkLoadTask;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task.ChunkProgressionTask;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task.GenericDataLoadTask;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class NewChunkHolder {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewChunkHolder.class);

    public final ChunkData holderData;

    public final ServerLevel world;
    public final int chunkX;
    public final int chunkZ;

    public final ChunkTaskScheduler scheduler;

    // load/unload state

    // chunk data state

    private ChunkEntitySlices entityChunk;
    // entity chunk that is loaded, but not yet deserialized
    private CompoundTag pendingEntityChunk;

    ChunkEntitySlices loadInEntityChunk(final boolean transientChunk) {
        TickThread.ensureTickThread(this.world, this.chunkX, this.chunkZ, "Cannot sync load entity data off-main");
        final CompoundTag entityChunk;
        final ChunkEntitySlices ret;
        final ReentrantAreaLock.Node schedulingLock = this.scheduler.schedulingLockArea.lock(this.chunkX, this.chunkZ);
        try {
            if (this.entityChunk != null && (transientChunk || !this.entityChunk.isTransient())) {
                return this.entityChunk;
            }
            final CompoundTag pendingEntityChunk = this.pendingEntityChunk;
            if (!transientChunk && pendingEntityChunk == null) {
                throw new IllegalStateException("Must load entity data from disk before loading in the entity chunk!");
            }

            if (this.entityChunk == null) {
                ret = this.entityChunk = new ChunkEntitySlices(
                    this.world, this.chunkX, this.chunkZ, this.getChunkStatus(),
                    this.holderData, WorldUtil.getMinSection(this.world), WorldUtil.getMaxSection(this.world)
                );

                ret.setTransient(transientChunk);

                ((ChunkSystemServerLevel)this.world).moonrise$getEntityLookup().entitySectionLoad(this.chunkX, this.chunkZ, ret);
            } else {
                // transientChunk = false here
                ret = this.entityChunk;
                this.entityChunk.setTransient(false);
            }

            if (!transientChunk) {
                this.pendingEntityChunk = null;
                entityChunk = pendingEntityChunk == EMPTY_ENTITY_CHUNK ? null : pendingEntityChunk;
            } else {
                entityChunk = null;
            }
        } finally {
            this.scheduler.schedulingLockArea.unlock(schedulingLock);
        }

        if (!transientChunk) {
            if (entityChunk != null) {
                final List<Entity> entities = ChunkEntitySlices.readEntities(this.world, entityChunk);

                ((ChunkSystemServerLevel)this.world).moonrise$getEntityLookup().addEntityChunkEntities(entities, new ChunkPos(this.chunkX, this.chunkZ));
            }
        }

        return ret;
    }

    // needed to distinguish whether the entity chunk has been read from disk but is empty or whether it has _not_
    // been read from disk
    private static final CompoundTag EMPTY_ENTITY_CHUNK = new CompoundTag();

    private ChunkLoadTask.EntityDataLoadTask entityDataLoadTask;
    // note: if entityDataLoadTask is cancelled, but on its completion entityDataLoadTaskWaiters.size() != 0,
    // then the task is rescheduled
    private List<GenericDataLoadTaskCallback> entityDataLoadTaskWaiters;

    public ChunkLoadTask.EntityDataLoadTask getEntityDataLoadTask() {
        return this.entityDataLoadTask;
    }

    // must hold schedule lock for the two below functions

    // returns only if the data has been loaded from disk, DOES NOT relate to whether it has been deserialized
    // or added into the world (or even into entityChunk)
    public boolean isEntityChunkNBTLoaded() {
        return (this.entityChunk != null && !this.entityChunk.isTransient()) || this.pendingEntityChunk != null;
    }

    private void completeEntityLoad(final GenericDataLoadTask.TaskResult<CompoundTag, Throwable> result) {
        final List<GenericDataLoadTaskCallback> completeWaiters;
        ChunkLoadTask.EntityDataLoadTask entityDataLoadTask = null;
        boolean scheduleEntityTask = false;
        ReentrantAreaLock.Node schedulingLock = this.scheduler.schedulingLockArea.lock(this.chunkX, this.chunkZ);
        try {
            final List<GenericDataLoadTaskCallback> waiters = this.entityDataLoadTaskWaiters;
            this.entityDataLoadTask = null;
            if (result != null) {
                this.entityDataLoadTaskWaiters = null;
                this.pendingEntityChunk = result.left() == null ? EMPTY_ENTITY_CHUNK : result.left();
                if (result.right() != null) {
                    LOGGER.error("Unhandled entity data load exception, data data will be lost: ", result.right());
                }

                for (final GenericDataLoadTaskCallback callback : waiters) {
                    callback.markCompleted();
                }

                completeWaiters = waiters;
            } else {
                // cancelled
                completeWaiters = null;

                // need to re-schedule?
                if (waiters.isEmpty()) {
                    this.entityDataLoadTaskWaiters = null;
                    // no tasks to schedule _for_
                } else {
                    entityDataLoadTask = this.entityDataLoadTask = new ChunkLoadTask.EntityDataLoadTask(
                        this.scheduler, this.world, this.chunkX, this.chunkZ, this.getEffectivePriority(Priority.NORMAL)
                    );
                    entityDataLoadTask.addCallback(this::completeEntityLoad);
                    // need one schedule() per waiter
                    for (final GenericDataLoadTaskCallback callback : waiters) {
                        scheduleEntityTask |= entityDataLoadTask.schedule(true);
                    }
                }
            }
        } finally {
            this.scheduler.schedulingLockArea.unlock(schedulingLock);
        }

        if (scheduleEntityTask) {
            entityDataLoadTask.scheduleNow();
        }

        // avoid holding the scheduling lock while completing
        if (completeWaiters != null) {
            for (final GenericDataLoadTaskCallback callback : completeWaiters) {
                callback.acceptCompleted(result);
            }
        }

        schedulingLock = this.scheduler.schedulingLockArea.lock(this.chunkX, this.chunkZ);
        try {
            this.checkUnload();
        } finally {
            this.scheduler.schedulingLockArea.unlock(schedulingLock);
        }
    }

    // note: it is guaranteed that the consumer cannot be called for the entirety that the schedule lock is held
    // however, when the consumer is invoked, it will hold the schedule lock
    public GenericDataLoadTaskCallback getOrLoadEntityData(final Consumer<GenericDataLoadTask.TaskResult<CompoundTag, Throwable>> consumer) {
        if (this.isEntityChunkNBTLoaded()) {
            throw new IllegalStateException("Cannot load entity data, it is already loaded");
        }
        // why not just acquire the lock? because the caller NEEDS to call isEntityChunkNBTLoaded before this!
        if (!this.scheduler.schedulingLockArea.isHeldByCurrentThread(this.chunkX, this.chunkZ)) {
            throw new IllegalStateException("Must hold scheduling lock");
        }

        final GenericDataLoadTaskCallback ret = new EntityDataLoadTaskCallback((Consumer)consumer, this);

        if (this.entityDataLoadTask == null) {
            this.entityDataLoadTask = new ChunkLoadTask.EntityDataLoadTask(
                this.scheduler, this.world, this.chunkX, this.chunkZ, this.getEffectivePriority(Priority.NORMAL)
            );
            this.entityDataLoadTask.addCallback(this::completeEntityLoad);
            this.entityDataLoadTaskWaiters = new ArrayList<>();
        }
        this.entityDataLoadTaskWaiters.add(ret);
        if (this.entityDataLoadTask.schedule(true)) {
            ret.schedule = this.entityDataLoadTask;
        }
        this.checkUnload();

        return ret;
    }

    private static final class EntityDataLoadTaskCallback extends GenericDataLoadTaskCallback {

        public EntityDataLoadTaskCallback(final Consumer<GenericDataLoadTask.TaskResult<?, Throwable>> consumer, final NewChunkHolder chunkHolder) {
            super(consumer, chunkHolder);
        }

        @Override
        void internalCancel() {
            this.chunkHolder.entityDataLoadTaskWaiters.remove(this);
            this.chunkHolder.entityDataLoadTask.cancel();
        }
    }

    private PoiChunk poiChunk;

    private ChunkLoadTask.PoiDataLoadTask poiDataLoadTask;
    // note: if entityDataLoadTask is cancelled, but on its completion entityDataLoadTaskWaiters.size() != 0,
    // then the task is rescheduled
    private List<GenericDataLoadTaskCallback> poiDataLoadTaskWaiters;

    public ChunkLoadTask.PoiDataLoadTask getPoiDataLoadTask() {
        return this.poiDataLoadTask;
    }

    // must hold schedule lock for the two below functions

    public boolean isPoiChunkLoaded() {
        return this.poiChunk != null;
    }

    private void completePoiLoad(final GenericDataLoadTask.TaskResult<PoiChunk, Throwable> result) {
        final List<GenericDataLoadTaskCallback> completeWaiters;
        ChunkLoadTask.PoiDataLoadTask poiDataLoadTask = null;
        boolean schedulePoiTask = false;
        ReentrantAreaLock.Node schedulingLock = this.scheduler.schedulingLockArea.lock(this.chunkX, this.chunkZ);
        try {
            final List<GenericDataLoadTaskCallback> waiters = this.poiDataLoadTaskWaiters;
            this.poiDataLoadTask = null;
            if (result != null) {
                this.poiDataLoadTaskWaiters = null;
                this.poiChunk = result.left();
                if (result.right() != null) {
                    LOGGER.error("Unhandled poi load exception, poi data will be lost: ", result.right());
                }

                for (final GenericDataLoadTaskCallback callback : waiters) {
                    callback.markCompleted();
                }

                completeWaiters = waiters;
            } else {
                // cancelled
                completeWaiters = null;

                // need to re-schedule?
                if (waiters.isEmpty()) {
                    this.poiDataLoadTaskWaiters = null;
                    // no tasks to schedule _for_
                } else {
                    poiDataLoadTask = this.poiDataLoadTask = new ChunkLoadTask.PoiDataLoadTask(
                        this.scheduler, this.world, this.chunkX, this.chunkZ, this.getEffectivePriority(Priority.NORMAL)
                    );
                    poiDataLoadTask.addCallback(this::completePoiLoad);
                    // need one schedule() per waiter
                    for (final GenericDataLoadTaskCallback callback : waiters) {
                        schedulePoiTask |= poiDataLoadTask.schedule(true);
                    }
                }
            }
        } finally {
            this.scheduler.schedulingLockArea.unlock(schedulingLock);
        }

        if (schedulePoiTask) {
            poiDataLoadTask.scheduleNow();
        }

        // avoid holding the scheduling lock while completing
        if (completeWaiters != null) {
            for (final GenericDataLoadTaskCallback callback : completeWaiters) {
                callback.acceptCompleted(result);
            }
        }
        schedulingLock = this.scheduler.schedulingLockArea.lock(this.chunkX, this.chunkZ);
        try {
            this.checkUnload();
        } finally {
            this.scheduler.schedulingLockArea.unlock(schedulingLock);
        }
    }

    // note: it is guaranteed that the consumer cannot be called for the entirety that the schedule lock is held
    // however, when the consumer is invoked, it will hold the schedule lock
    public GenericDataLoadTaskCallback getOrLoadPoiData(final Consumer<GenericDataLoadTask.TaskResult<PoiChunk, Throwable>> consumer) {
        if (this.isPoiChunkLoaded()) {
            throw new IllegalStateException("Cannot load poi data, it is already loaded");
        }
        // why not just acquire the lock? because the caller NEEDS to call isPoiChunkLoaded before this!
        if (!this.scheduler.schedulingLockArea.isHeldByCurrentThread(this.chunkX, this.chunkZ)) {
            throw new IllegalStateException("Must hold scheduling lock");
        }

        final GenericDataLoadTaskCallback ret = new PoiDataLoadTaskCallback((Consumer)consumer, this);

        if (this.poiDataLoadTask == null) {
            this.poiDataLoadTask = new ChunkLoadTask.PoiDataLoadTask(
                this.scheduler, this.world, this.chunkX, this.chunkZ, this.getEffectivePriority(Priority.NORMAL)
            );
            this.poiDataLoadTask.addCallback(this::completePoiLoad);
            this.poiDataLoadTaskWaiters = new ArrayList<>();
        }
        this.poiDataLoadTaskWaiters.add(ret);
        if (this.poiDataLoadTask.schedule(true)) {
            ret.schedule = this.poiDataLoadTask;
        }
        this.checkUnload();

        return ret;
    }

    private static final class PoiDataLoadTaskCallback extends GenericDataLoadTaskCallback {

        public PoiDataLoadTaskCallback(final Consumer<GenericDataLoadTask.TaskResult<?, Throwable>> consumer, final NewChunkHolder chunkHolder) {
            super(consumer, chunkHolder);
        }

        @Override
        void internalCancel() {
            this.chunkHolder.poiDataLoadTaskWaiters.remove(this);
            this.chunkHolder.poiDataLoadTask.cancel();
        }
    }

    public static abstract class GenericDataLoadTaskCallback implements Cancellable {

        protected final Consumer<GenericDataLoadTask.TaskResult<?, Throwable>> consumer;
        protected final NewChunkHolder chunkHolder;
        protected boolean completed;
        protected GenericDataLoadTask<?, ?> schedule;
        protected final AtomicBoolean scheduled = new AtomicBoolean();

        public GenericDataLoadTaskCallback(final Consumer<GenericDataLoadTask.TaskResult<?, Throwable>> consumer,
                                           final NewChunkHolder chunkHolder) {
            this.consumer = consumer;
            this.chunkHolder = chunkHolder;
        }

        public void schedule() {
            if (this.scheduled.getAndSet(true)) {
                throw new IllegalStateException("Double calling schedule()");
            }
            if (this.schedule != null) {
                this.schedule.scheduleNow();
                this.schedule = null;
            }
        }

        boolean isCompleted() {
            return this.completed;
        }

        // must hold scheduling lock
        private boolean setCompleted() {
            if (this.completed) {
                return false;
            }
            return this.completed = true;
        }

        // must hold scheduling lock
        void markCompleted() {
            if (this.completed) {
                throw new IllegalStateException("May not be completed here");
            }
            this.completed = true;
        }

        void acceptCompleted(final GenericDataLoadTask.TaskResult<?, Throwable> result) {
            if (result != null) {
                if (this.completed) {
                    this.consumer.accept(result);
                } else {
                    throw new IllegalStateException("Cannot be uncompleted at this point");
                }
            } else {
                throw new NullPointerException("Result cannot be null (cancelled)");
            }
        }

        // holds scheduling lock
        abstract void internalCancel();

        @Override
        public boolean cancel() {
            final NewChunkHolder holder = this.chunkHolder;
            final ReentrantAreaLock.Node schedulingLock = holder.scheduler.schedulingLockArea.lock(holder.chunkX, holder.chunkZ);
            try {
                if (!this.completed) {
                    this.completed = true;
                    this.internalCancel();
                    return true;
                }
                return false;
            } finally {
                holder.scheduler.schedulingLockArea.unlock(schedulingLock);
            }
        }
    }

    private ChunkAccess currentChunk;

    // generation status state

    /**
     * Current status the chunk has been brought up to by the chunk system. null indicates no work at all
     */
    private ChunkStatus currentGenStatus;

    // This allows lockless access to the chunk and last gen status
    private static final ChunkStatus[] ALL_STATUSES = ChunkStatus.getStatusList().toArray(new ChunkStatus[0]);

    public static final record ChunkCompletion(ChunkAccess chunk, ChunkStatus genStatus) {};
    private static final VarHandle CHUNK_COMPLETION_ARRAY_HANDLE = ConcurrentUtil.getArrayHandle(ChunkCompletion[].class);
    private final ChunkCompletion[] chunkCompletions = new ChunkCompletion[ALL_STATUSES.length];

    private volatile ChunkCompletion lastChunkCompletion;

    public ChunkCompletion getLastChunkCompletion() {
        return this.lastChunkCompletion;
    }

    public ChunkAccess getChunkIfPresentUnchecked(final ChunkStatus status) {
        final ChunkCompletion completion = (ChunkCompletion)CHUNK_COMPLETION_ARRAY_HANDLE.getVolatile(this.chunkCompletions, status.getIndex());
        return completion == null ? null : completion.chunk;
    }

    public ChunkAccess getChunkIfPresent(final ChunkStatus status) {
        final ChunkStatus maxStatus = ChunkLevel.generationStatus(this.getTicketLevel());

        if (maxStatus == null || status.isAfter(maxStatus)) {
            return null;
        }

        return this.getChunkIfPresentUnchecked(status);
    }

    public void replaceProtoChunk(final ImposterProtoChunk imposterProtoChunk) {
        for (int i = 0, max = ChunkStatus.FULL.getIndex(); i < max; ++i) {
            CHUNK_COMPLETION_ARRAY_HANDLE.setVolatile(this.chunkCompletions, i, new ChunkCompletion(imposterProtoChunk, ALL_STATUSES[i]));
        }
    }

    /**
     * The target final chunk status the chunk system will bring the chunk to.
     */
    private ChunkStatus requestedGenStatus;

    private ChunkProgressionTask generationTask;
    private ChunkStatus generationTaskStatus;

    /**
     * contains the neighbours that this chunk generation is blocking on
     */
    private final ReferenceLinkedOpenHashSet<NewChunkHolder> neighboursBlockingGenTask = new ReferenceLinkedOpenHashSet<>(4);

    /**
     * map of ChunkHolder -> Required Status for this chunk
     */
    private final Reference2ObjectLinkedOpenHashMap<NewChunkHolder, ChunkStatus> neighboursWaitingForUs = new Reference2ObjectLinkedOpenHashMap<>();

    public void addGenerationBlockingNeighbour(final NewChunkHolder neighbour) {
        this.neighboursBlockingGenTask.add(neighbour);
    }

    public void addWaitingNeighbour(final NewChunkHolder neighbour, final ChunkStatus requiredStatus) {
        final boolean wasEmpty = this.neighboursWaitingForUs.isEmpty();
        this.neighboursWaitingForUs.put(neighbour, requiredStatus);
        if (wasEmpty) {
            this.checkUnload();
        }
    }

    // priority state

    // the target priority for this chunk to generate at
    private Priority priority = null;
    private boolean priorityLocked;

    // the priority neighbouring chunks have requested this chunk generate at
    private Priority neighbourRequestedPriority = null;

    public Priority getEffectivePriority(final Priority dfl) {
        final Priority neighbour = this.neighbourRequestedPriority;
        final Priority us = this.priority;

        if (neighbour == null) {
            return us == null ? dfl : us;
        }
        if (us == null) {
            return neighbour;
        }

        return Priority.max(us, neighbour);
    }

    private void recalculateNeighbourRequestedPriority() {
        if (this.neighboursWaitingForUs.isEmpty()) {
            this.neighbourRequestedPriority = null;
            return;
        }

        Priority max = null;

        for (final NewChunkHolder holder : this.neighboursWaitingForUs.keySet()) {
            final Priority neighbourPriority = holder.getEffectivePriority(null);
            if (neighbourPriority != null && (max == null || neighbourPriority.isHigherPriority(max))) {
                max = neighbourPriority;
            }
        }

        final Priority current = this.getEffectivePriority(Priority.NORMAL);
        this.neighbourRequestedPriority = max;
        final Priority next = this.getEffectivePriority(Priority.NORMAL);

        if (current == next) {
            return;
        }

        // our effective priority has changed, so change our task
        if (this.generationTask != null) {
            this.generationTask.setPriority(next);
        }

        // now propagate this to our neighbours
        this.recalculateNeighbourPriorities();
    }

    public void recalculateNeighbourPriorities() {
        for (final NewChunkHolder holder : this.neighboursBlockingGenTask) {
            holder.recalculateNeighbourRequestedPriority();
        }
    }

    // must hold scheduling lock
    public void raisePriority(final Priority priority) {
        if (this.priority != null && this.priority.isHigherOrEqualPriority(priority)) {
            return;
        }
        this.setPriority(priority);
    }

    private void lockPriority() {
        this.priority = null;
        this.priorityLocked = true;
    }

    // must hold scheduling lock
    public void setPriority(final Priority priority) {
        if (this.priorityLocked) {
            return;
        }
        final Priority old = this.getEffectivePriority(null);
        this.priority = priority;
        final Priority newPriority = this.getEffectivePriority(Priority.NORMAL);

        if (old != newPriority) {
            if (this.generationTask != null) {
                this.generationTask.setPriority(newPriority);
            }
        }

        this.recalculateNeighbourPriorities();
    }

    // must hold scheduling lock
    public void lowerPriority(final Priority priority) {
        if (this.priority != null && this.priority.isLowerOrEqualPriority(priority)) {
            return;
        }
        this.setPriority(priority);
    }

    // error handling state
    private ChunkStatus failedGenStatus;
    private Throwable genTaskException;
    private Thread genTaskFailedThread;

    private boolean failedLightUpdate;

    public void failedLightUpdate() {
        this.failedLightUpdate = true;
    }

    public boolean hasFailedGeneration() {
        return this.genTaskException != null;
    }

    // ticket level state
    private int oldTicketLevel = ChunkHolderManager.MAX_TICKET_LEVEL + 1;
    private int currentTicketLevel = ChunkHolderManager.MAX_TICKET_LEVEL + 1;

    public int getTicketLevel() {
        return this.currentTicketLevel;
    }

    public final ChunkHolder vanillaChunkHolder;

    public NewChunkHolder(final ServerLevel world, final int chunkX, final int chunkZ, final ChunkTaskScheduler scheduler) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.scheduler = scheduler;
        this.vanillaChunkHolder = new ChunkHolder(
                new ChunkPos(chunkX, chunkZ), ChunkHolderManager.MAX_TICKET_LEVEL, world,
                world.getLightEngine(), null, world.getChunkSource().chunkMap
        );
        ((ChunkSystemChunkHolder)this.vanillaChunkHolder).moonrise$setRealChunkHolder(this);
        this.holderData = ((ChunkSystemLevel)this.world).moonrise$requestChunkData(CoordinateUtils.getChunkKey(chunkX, chunkZ));
    }

    public ChunkAccess getCurrentChunk() {
        return this.currentChunk;
    }

    int getCurrentTicketLevel() {
        return this.currentTicketLevel;
    }

    void updateTicketLevel(final int toLevel) {
        this.currentTicketLevel = toLevel;
    }

    private int totalNeighboursUsingThisChunk = 0;

    // holds schedule lock
    public void addNeighbourUsingChunk() {
        final int now = ++this.totalNeighboursUsingThisChunk;

        if (now == 1) {
            this.checkUnload();
        }
    }

    // holds schedule lock
    public void removeNeighbourUsingChunk() {
        final int now = --this.totalNeighboursUsingThisChunk;

        if (now == 0) {
            this.checkUnload();
        }

        if (now < 0) {
            throw new IllegalStateException("Neighbours using this chunk cannot be negative");
        }
    }

    // must hold scheduling lock
    // returns string reason for why chunk should remain loaded, null otherwise
    public final String isSafeToUnload() {
        // is ticket level below threshold?
        if (this.oldTicketLevel <= ChunkHolderManager.MAX_TICKET_LEVEL) {
            return "ticket_level";
        }

        // are we being used by another chunk for generation?
        if (this.totalNeighboursUsingThisChunk != 0) {
            return "neighbours_generating";
        }

        // are we going to be used by another chunk for generation?
        if (!this.neighboursWaitingForUs.isEmpty()) {
            return "neighbours_waiting";
        }

        // chunk must be marked inaccessible (i.e. unloaded to plugins)
        if (this.getChunkStatus() != FullChunkStatus.INACCESSIBLE) {
            return "fullchunkstatus";
        }

        // are we currently generating anything, or have requested generation?
        if (this.generationTask != null) {
            return "generating";
        }
        if (this.requestedGenStatus != null) {
            return "requested_generation";
        }

        // entity data requested?
        if (this.entityDataLoadTask != null) {
            return "entity_data_requested";
        }

        // poi data requested?
        if (this.poiDataLoadTask != null) {
            return "poi_data_requested";
        }

        // are we pending serialization?
        if (this.entityDataUnload != null) {
            return "entity_serialization";
        }
        if (this.poiDataUnload != null) {
            return "poi_serialization";
        }
        if (this.chunkDataUnload != null) {
            return "chunk_serialization";
        }

        // Note: light tasks do not need a check, as they add a ticket.

        // nothing is using this chunk, so it should be unloaded
        return null;
    }

    /** Unloaded from chunk map */
    private boolean unloaded;

    void onUnload() {
        this.unloaded = true;
        ((ChunkSystemServerLevel)this.world).moonrise$removeUnsyncedChunk(this.vanillaChunkHolder);
        ((ChunkSystemLevel)this.world).moonrise$releaseChunkData(CoordinateUtils.getChunkKey(this.chunkX, this.chunkZ));
    }

    private boolean inUnloadQueue = false;

    void removeFromUnloadQueue() {
        this.inUnloadQueue = false;
    }

    // must hold scheduling lock
    private void checkUnload() {
        if (this.unloaded) {
            return;
        }
        if (this.isSafeToUnload() == null) {
            // ensure in unload queue
            if (!this.inUnloadQueue) {
                this.inUnloadQueue = true;
                this.scheduler.chunkHolderManager.unloadQueue.addChunk(this.chunkX, this.chunkZ);
            }
        } else {
            // ensure not in unload queue
            if (this.inUnloadQueue) {
                this.inUnloadQueue = false;
                this.scheduler.chunkHolderManager.unloadQueue.removeChunk(this.chunkX, this.chunkZ);
            }
        }
    }

    static final record UnloadState(NewChunkHolder holder, ChunkAccess chunk, ChunkEntitySlices entityChunk, PoiChunk poiChunk) {};

    // note: these are completed with null to indicate that no write occurred
    // they are also completed with null to indicate a null write occurred
    private UnloadTask chunkDataUnload;
    private UnloadTask entityDataUnload;
    private UnloadTask poiDataUnload;

    public static final record UnloadTask(CallbackCompletable<CompoundTag> completable, PrioritisedExecutor.PrioritisedTask task,
                                          LazyRunnable toRun) {}

    public UnloadTask getUnloadTask(final MoonriseRegionFileIO.RegionFileType type) {
        switch (type) {
            case CHUNK_DATA:
                return this.chunkDataUnload;
            case ENTITY_DATA:
                return this.entityDataUnload;
            case POI_DATA:
                return this.poiDataUnload;
            default:
                throw new IllegalStateException("Unknown regionfile type " + type);
        }
    }

    private void removeUnloadTask(final MoonriseRegionFileIO.RegionFileType type) {
        switch (type) {
            case CHUNK_DATA: {
                this.chunkDataUnload = null;
                return;
            }
            case ENTITY_DATA: {
                this.entityDataUnload = null;
                return;
            }
            case POI_DATA: {
                this.poiDataUnload = null;
                return;
            }
            default:
                throw new IllegalStateException("Unknown regionfile type " + type);
        }
    }

    private UnloadState unloadState;

    // holds schedule lock
    UnloadState unloadStage1() {
        // because we hold the scheduling lock, we cannot actually unload anything
        // so, what we do here instead is to null this chunk's state and setup the unload tasks
        // the unload tasks will ensure that any loads that take place after stage1 (i.e during stage2, in which
        // we do not hold the lock) c
        final ChunkAccess chunk = this.currentChunk;
        final ChunkEntitySlices entityChunk = this.entityChunk;
        final PoiChunk poiChunk = this.poiChunk;
        // chunk state
        this.currentChunk = null;
        this.currentGenStatus = null;
        for (int i = 0; i < this.chunkCompletions.length; ++i) {
            CHUNK_COMPLETION_ARRAY_HANDLE.setRelease(this.chunkCompletions, i, (ChunkCompletion)null);
        }
        this.lastChunkCompletion = null;
        // entity chunk state
        this.entityChunk = null;
        this.pendingEntityChunk = null;

        // poi chunk state
        this.poiChunk = null;

        // priority state
        this.priorityLocked = false;

        if (chunk != null) {
            final LazyRunnable toRun = new LazyRunnable();
            this.chunkDataUnload = new UnloadTask(new CallbackCompletable<>(), this.scheduler.loadExecutor.createTask(toRun), toRun);
        }
        if (poiChunk != null) {
            this.poiDataUnload = new UnloadTask(new CallbackCompletable<>(), null, null);
        }
        if (entityChunk != null) {
            this.entityDataUnload = new UnloadTask(new CallbackCompletable<>(), null, null);
        }

        return this.unloadState = (chunk != null || entityChunk != null || poiChunk != null) ? new UnloadState(this, chunk, entityChunk, poiChunk) : null;
    }

    // data is null if failed or does not need to be saved
    void completeAsyncUnloadDataSave(final MoonriseRegionFileIO.RegionFileType type, final CompoundTag data) {
        if (data != null) {
            MoonriseRegionFileIO.scheduleSave(this.world, this.chunkX, this.chunkZ, data, type);
        }

        this.getUnloadTask(type).completable().complete(data);
        final ReentrantAreaLock.Node schedulingLock = this.scheduler.schedulingLockArea.lock(this.chunkX, this.chunkZ);
        try {
            // can only write to these fields while holding the schedule lock
            this.removeUnloadTask(type);
            this.checkUnload();
        } finally {
            this.scheduler.schedulingLockArea.unlock(schedulingLock);
        }
    }

    void unloadStage2(final UnloadState state) {
        this.unloadState = null;
        final ChunkAccess chunk = state.chunk();
        final ChunkEntitySlices entityChunk = state.entityChunk();
        final PoiChunk poiChunk = state.poiChunk();

        final boolean shouldLevelChunkNotSave = ChunkSystemFeatures.forceNoSave(chunk);

        // unload chunk data
        if (chunk != null) {
            if (chunk instanceof LevelChunk levelChunk) {
                levelChunk.setLoaded(false);
                PlatformHooks.get().chunkUnloadFromWorld(levelChunk);
            }

            if (!shouldLevelChunkNotSave) {
                this.saveChunk(chunk, true);
            } else {
                this.completeAsyncUnloadDataSave(MoonriseRegionFileIO.RegionFileType.CHUNK_DATA, null);
            }

            if (chunk instanceof LevelChunk levelChunk) {
                this.world.unload(levelChunk);
            }
        }

        // unload entity data
        if (entityChunk != null) {
            this.saveEntities(entityChunk, true);
            // yes this is a hack to pass the compound tag through...
            final CompoundTag lastEntityUnload = this.lastEntityUnload;
            this.lastEntityUnload = null;

            if (entityChunk.unload()) {
                final ReentrantAreaLock.Node schedulingLock = this.scheduler.schedulingLockArea.lock(this.chunkX, this.chunkZ);
                try {
                    entityChunk.setTransient(true);
                    this.entityChunk = entityChunk;
                } finally {
                    this.scheduler.schedulingLockArea.unlock(schedulingLock);
                }
            } else {
                ((ChunkSystemServerLevel)this.world).moonrise$getEntityLookup().entitySectionUnload(this.chunkX, this.chunkZ);
            }
            // we need to delay the callback until after determining transience, otherwise a potential loader could
            // set entityChunk before we do
            this.entityDataUnload.completable().complete(lastEntityUnload);
        }

        // unload poi data
        if (poiChunk != null) {
            if (poiChunk.isDirty() && !shouldLevelChunkNotSave) {
                this.savePOI(poiChunk, true);
            } else {
                this.poiDataUnload.completable().complete(null);
            }

            if (poiChunk.isLoaded()) {
                ((ChunkSystemPoiManager)this.world.getPoiManager()).moonrise$onUnload(CoordinateUtils.getChunkKey(this.chunkX, this.chunkZ));
            }
        }
    }

    boolean unloadStage3() {
        // can only write to these while holding the schedule lock, and we instantly complete them in stage2
        this.poiDataUnload = null;
        this.entityDataUnload = null;

        // we need to check if anything has been loaded in the meantime (or if we have transient entities)
        if (this.entityChunk != null || this.poiChunk != null || this.currentChunk != null) {
            return false;
        }

        return this.isSafeToUnload() == null;
    }

    private void cancelGenTask() {
        if (this.generationTask != null) {
            this.generationTask.cancel();
        } else {
            // otherwise, we are blocking on neighbours, so remove them
            if (!this.neighboursBlockingGenTask.isEmpty()) {
                for (final NewChunkHolder neighbour : this.neighboursBlockingGenTask) {
                    if (neighbour.neighboursWaitingForUs.remove(this) == null) {
                        throw new IllegalStateException("Corrupt state");
                    }
                    if (neighbour.neighboursWaitingForUs.isEmpty()) {
                        neighbour.checkUnload();
                    }
                }
                this.neighboursBlockingGenTask.clear();
                this.checkUnload();
            }
        }
    }

    // holds: ticket level update lock
    // holds: schedule lock
    public void processTicketLevelUpdate(final List<ChunkProgressionTask> scheduledTasks, final List<NewChunkHolder> changedLoadStatus) {
        final int oldLevel = this.oldTicketLevel;
        final int newLevel = this.currentTicketLevel;

        if (oldLevel == newLevel) {
            return;
        }

        this.oldTicketLevel = newLevel;

        final FullChunkStatus oldState = ChunkLevel.fullStatus(oldLevel);
        final FullChunkStatus newState = ChunkLevel.fullStatus(newLevel);
        final boolean oldUnloaded = oldLevel > ChunkHolderManager.MAX_TICKET_LEVEL;
        final boolean newUnloaded = newLevel > ChunkHolderManager.MAX_TICKET_LEVEL;

        final ChunkStatus maxGenerationStatusOld = ChunkLevel.generationStatus(oldLevel);
        final ChunkStatus maxGenerationStatusNew = ChunkLevel.generationStatus(newLevel);

        // check for cancellations from downgrading ticket level
        if (this.requestedGenStatus != null && !newState.isOrAfter(FullChunkStatus.FULL) && newLevel > oldLevel) {
            // note: cancel() may invoke onChunkGenComplete synchronously here
            if (newUnloaded) {
                // need to cancel all tasks
                // note: requested status must be set to null here before cancellation, to indicate to the
                // completion logic that we do not want rescheduling to occur
                this.requestedGenStatus = null;
                this.cancelGenTask();
            } else {
                final ChunkStatus toCancel = ((ChunkSystemChunkStatus)maxGenerationStatusNew).moonrise$getNextStatus();
                final ChunkStatus currentRequestedStatus = this.requestedGenStatus;

                if (currentRequestedStatus.isOrAfter(toCancel)) {
                    // we do have to cancel something here
                    // clamp requested status to the maximum
                    if (this.currentGenStatus != null && this.currentGenStatus.isOrAfter(maxGenerationStatusNew)) {
                        // already generated to status, so we must cancel
                        this.requestedGenStatus = null;
                        this.cancelGenTask();
                    } else {
                        // not generated to status, so we may have to cancel
                        // note: gen task is always 1 status above current gen status if not null
                        this.requestedGenStatus = maxGenerationStatusNew;
                        if (this.generationTaskStatus != null && this.generationTaskStatus.isOrAfter(toCancel)) {
                            // TOOD is this even possible? i don't think so
                            throw new IllegalStateException("?????");
                        }
                    }
                }
            }
        }

        if (oldState != newState) {
            if (newState.isOrAfter(oldState)) {
                // status upgrade
                if (!oldState.isOrAfter(FullChunkStatus.FULL) && newState.isOrAfter(FullChunkStatus.FULL)) {
                    // may need to schedule full load
                    if (this.currentGenStatus != ChunkStatus.FULL) {
                        if (this.requestedGenStatus != null) {
                            this.requestedGenStatus = ChunkStatus.FULL;
                        } else {
                            this.scheduler.schedule(
                                this.chunkX, this.chunkZ, ChunkStatus.FULL, this, scheduledTasks
                            );
                        }
                    }
                }
            } else {
                // status downgrade
                if (!newState.isOrAfter(FullChunkStatus.ENTITY_TICKING) && oldState.isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                    this.completeFullStatusConsumers(FullChunkStatus.ENTITY_TICKING, null);
                }

                if (!newState.isOrAfter(FullChunkStatus.BLOCK_TICKING) && oldState.isOrAfter(FullChunkStatus.BLOCK_TICKING)) {
                    this.completeFullStatusConsumers(FullChunkStatus.BLOCK_TICKING, null);
                }

                if (!newState.isOrAfter(FullChunkStatus.FULL) && oldState.isOrAfter(FullChunkStatus.FULL)) {
                    this.completeFullStatusConsumers(FullChunkStatus.FULL, null);
                }
            }

            if (this.updatePendingStatus()) {
                changedLoadStatus.add(this);
            }
        }

        if (oldUnloaded != newUnloaded) {
            this.checkUnload();
        }

        // Don't really have a choice but to place this hook here
        PlatformHooks.get().onChunkHolderTicketChange(this.world, this, oldLevel, newLevel);
    }

    static final int NEIGHBOUR_RADIUS = 2;
    private long fullNeighbourChunksLoadedBitset;

    private static int getFullNeighbourIndex(final int relativeX, final int relativeZ) {
        // index = (relativeX + NEIGHBOUR_CACHE_RADIUS) + (relativeZ + NEIGHBOUR_CACHE_RADIUS) * (NEIGHBOUR_CACHE_RADIUS * 2 + 1)
        // optimised variant of the above by moving some of the ops to compile time
        return relativeX + (relativeZ * (NEIGHBOUR_RADIUS * 2 + 1)) + (NEIGHBOUR_RADIUS + NEIGHBOUR_RADIUS * ((NEIGHBOUR_RADIUS * 2 + 1)));
    }
    public final boolean isNeighbourFullLoaded(final int relativeX, final int relativeZ) {
        return (this.fullNeighbourChunksLoadedBitset & (1L << getFullNeighbourIndex(relativeX, relativeZ))) != 0;
    }

    // returns true if this chunk changed pending full status
    // must hold scheduling lock
    public final boolean setNeighbourFullLoaded(final int relativeX, final int relativeZ) {
        final int index = getFullNeighbourIndex(relativeX, relativeZ);
        this.fullNeighbourChunksLoadedBitset |= (1L << index);
        return this.updatePendingStatus();
    }

    // returns true if this chunk changed pending full status
    // must hold scheduling lock
    public final boolean setNeighbourFullUnloaded(final int relativeX, final int relativeZ) {
        final int index = getFullNeighbourIndex(relativeX, relativeZ);
        this.fullNeighbourChunksLoadedBitset &= ~(1L << index);
        return this.updatePendingStatus();
    }

    private static long getLoadedMask(final int radius) {
        long mask = 0L;
        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                mask |= (1L << getFullNeighbourIndex(dx, dz));
            }
        }

        return mask;
    }

    private static final long CHUNK_LOADED_MASK_RAD0 = getLoadedMask(0);
    private static final long CHUNK_LOADED_MASK_RAD1 = getLoadedMask(1);
    private static final long CHUNK_LOADED_MASK_RAD2 = getLoadedMask(2);

    // only updated while holding scheduling lock
    private FullChunkStatus pendingFullChunkStatus = FullChunkStatus.INACCESSIBLE;
    // updated while holding no locks, but adds a ticket before to prevent pending status from dropping
    // so, current will never update to a value higher than pending
    private FullChunkStatus currentFullChunkStatus = FullChunkStatus.INACCESSIBLE;

    public FullChunkStatus getChunkStatus() {
        // no volatile access, access off-main is considered racey anyways
        return this.currentFullChunkStatus;
    }

    public boolean isEntityTickingReady() {
        return this.getChunkStatus().isOrAfter(FullChunkStatus.ENTITY_TICKING);
    }

    public boolean isTickingReady() {
        return this.getChunkStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING);
    }

    public boolean isFullChunkReady() {
        return this.getChunkStatus().isOrAfter(FullChunkStatus.FULL);
    }

    private static FullChunkStatus getStatusForBitset(final long bitset) {
        if ((bitset & CHUNK_LOADED_MASK_RAD2) == CHUNK_LOADED_MASK_RAD2) {
            return FullChunkStatus.ENTITY_TICKING;
        } else if ((bitset & CHUNK_LOADED_MASK_RAD1) == CHUNK_LOADED_MASK_RAD1) {
            return FullChunkStatus.BLOCK_TICKING;
        } else if ((bitset & CHUNK_LOADED_MASK_RAD0) == CHUNK_LOADED_MASK_RAD0) {
            return FullChunkStatus.FULL;
        } else {
            return FullChunkStatus.INACCESSIBLE;
        }
    }

    // must hold scheduling lock
    // returns whether the pending status was changed
    private boolean updatePendingStatus() {
        final FullChunkStatus byTicketLevel = ChunkLevel.fullStatus(this.oldTicketLevel); // oldTicketLevel is controlled by scheduling lock

        FullChunkStatus pending = getStatusForBitset(this.fullNeighbourChunksLoadedBitset);
        if (pending == FullChunkStatus.INACCESSIBLE && byTicketLevel.isOrAfter(FullChunkStatus.FULL) && this.currentGenStatus == ChunkStatus.FULL) {
            // the bitset is only for chunks that have gone through the status updater
            // but here we are ready to go to FULL
            pending = FullChunkStatus.FULL;
        }

        if (pending.isOrAfter(byTicketLevel)) { // pending >= byTicketLevel
            // cannot set above ticket level
            pending = byTicketLevel;
        }

        if (this.pendingFullChunkStatus == pending) {
            return false;
        }

        this.pendingFullChunkStatus = pending;

        return true;
    }

    private void onFullChunkLoadChange(final boolean loaded, final List<NewChunkHolder> changedFullStatus) {
        final ReentrantAreaLock.Node schedulingLock = this.scheduler.schedulingLockArea.lock(this.chunkX, this.chunkZ, NEIGHBOUR_RADIUS);
        try {
            for (int dz = -NEIGHBOUR_RADIUS; dz <= NEIGHBOUR_RADIUS; ++dz) {
                for (int dx = -NEIGHBOUR_RADIUS; dx <= NEIGHBOUR_RADIUS; ++dx) {
                    final NewChunkHolder holder = (dx | dz) == 0 ? this : this.scheduler.chunkHolderManager.getChunkHolder(dx + this.chunkX, dz + this.chunkZ);
                    if (loaded) {
                        if (holder.setNeighbourFullLoaded(-dx, -dz)) {
                            changedFullStatus.add(holder);
                        }
                    } else {
                        if (holder != null && holder.setNeighbourFullUnloaded(-dx, -dz)) {
                            changedFullStatus.add(holder);
                        }
                    }
                }
            }
        } finally {
            this.scheduler.schedulingLockArea.unlock(schedulingLock);
        }
    }

    private void changeEntityChunkStatus(final FullChunkStatus toStatus) {
        ((ChunkSystemServerLevel)this.world).moonrise$getEntityLookup().chunkStatusChange(this.chunkX, this.chunkZ, toStatus);
    }

    private boolean processingFullStatus = false;

    private void updateCurrentState(final FullChunkStatus to) {
        this.currentFullChunkStatus = to;
    }

    // only to be called on the main thread, no locks need to be held
    public boolean handleFullStatusChange(final List<NewChunkHolder> changedFullStatus) {
        TickThread.ensureTickThread(this.world, this.chunkX, this.chunkZ, "Cannot update full status thread off-main");

        boolean ret = false;

        if (this.processingFullStatus) {
            // we cannot process updates recursively, as we may be in the middle of logic to upgrade/downgrade status
            return ret;
        }

        this.processingFullStatus = true;
        try {
            for (;;) {
                // check if we have any remaining work to do

                // we do not need to hold the scheduling lock to read pending, as changes to pending
                // will queue a status update

                final FullChunkStatus pending = this.pendingFullChunkStatus;
                FullChunkStatus current = this.currentFullChunkStatus;

                if (pending == current) {
                    if (pending == FullChunkStatus.INACCESSIBLE) {
                        final ReentrantAreaLock.Node schedulingLock = this.scheduler.schedulingLockArea.lock(this.chunkX, this.chunkZ);
                        try {
                            this.checkUnload();
                        } finally {
                            this.scheduler.schedulingLockArea.unlock(schedulingLock);
                        }
                    }
                    return ret;
                }

                ret = true;

                // note: because the chunk system delays any ticket downgrade to the chunk holder manager tick, we
                //       do not need to consider cases where the ticket level may decrease during this call by asynchronous
                //       ticket changes

                // chunks cannot downgrade state while status is pending a change
                // note: currentChunk must be LevelChunk, as current != pending which means that at least one is not ACCESSIBLE
                final LevelChunk chunk = (LevelChunk)this.currentChunk;

                // Note: we assume that only load/unload contain plugin logic
                // plugin logic is anything stupid enough to possibly change the chunk status while it is already
                // being changed (i.e during load it is possible it will try to set to full ticking)
                // in order to allow this change, we also need this plugin logic to be contained strictly after all
                // of the chunk system load callbacks are invoked
                if (pending.isOrAfter(current)) {
                    // state upgrade
                    if (!current.isOrAfter(FullChunkStatus.FULL) && pending.isOrAfter(FullChunkStatus.FULL)) {
                        this.updateCurrentState(FullChunkStatus.FULL);
                        ChunkSystem.onChunkPreBorder(chunk, this.vanillaChunkHolder);
                        this.scheduler.chunkHolderManager.ensureInAutosave(this);
                        this.changeEntityChunkStatus(FullChunkStatus.FULL);
                        ChunkSystem.onChunkBorder(chunk, this.vanillaChunkHolder);
                        this.onFullChunkLoadChange(true, changedFullStatus);
                        this.completeFullStatusConsumers(FullChunkStatus.FULL, chunk);
                    }

                    if (!current.isOrAfter(FullChunkStatus.BLOCK_TICKING) && pending.isOrAfter(FullChunkStatus.BLOCK_TICKING)) {
                        this.updateCurrentState(FullChunkStatus.BLOCK_TICKING);
                        this.changeEntityChunkStatus(FullChunkStatus.BLOCK_TICKING);
                        ChunkSystem.onChunkTicking(chunk, this.vanillaChunkHolder);
                        this.completeFullStatusConsumers(FullChunkStatus.BLOCK_TICKING, chunk);
                    }

                    if (!current.isOrAfter(FullChunkStatus.ENTITY_TICKING) && pending.isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                        this.updateCurrentState(FullChunkStatus.ENTITY_TICKING);
                        this.changeEntityChunkStatus(FullChunkStatus.ENTITY_TICKING);
                        ChunkSystem.onChunkEntityTicking(chunk, this.vanillaChunkHolder);
                        this.completeFullStatusConsumers(FullChunkStatus.ENTITY_TICKING, chunk);
                    }
                } else {
                    if (current.isOrAfter(FullChunkStatus.ENTITY_TICKING) && !pending.isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                        this.changeEntityChunkStatus(FullChunkStatus.BLOCK_TICKING);
                        ChunkSystem.onChunkNotEntityTicking(chunk, this.vanillaChunkHolder);
                        this.updateCurrentState(FullChunkStatus.BLOCK_TICKING);
                    }

                    if (current.isOrAfter(FullChunkStatus.BLOCK_TICKING) && !pending.isOrAfter(FullChunkStatus.BLOCK_TICKING)) {
                        this.changeEntityChunkStatus(FullChunkStatus.FULL);
                        ChunkSystem.onChunkNotTicking(chunk, this.vanillaChunkHolder);
                        this.updateCurrentState(FullChunkStatus.FULL);
                    }

                    if (current.isOrAfter(FullChunkStatus.FULL) && !pending.isOrAfter(FullChunkStatus.FULL)) {
                        this.onFullChunkLoadChange(false, changedFullStatus);
                        this.changeEntityChunkStatus(FullChunkStatus.INACCESSIBLE);
                        ChunkSystem.onChunkNotBorder(chunk, this.vanillaChunkHolder);
                        ChunkSystem.onChunkPostNotBorder(chunk, this.vanillaChunkHolder);
                        this.updateCurrentState(FullChunkStatus.INACCESSIBLE);
                    }
                }
            }
        } finally {
            this.processingFullStatus = false;
        }
    }

    // note: must hold scheduling lock
    // rets true if the current requested gen status is not null (effectively, whether further scheduling is not needed)
    boolean upgradeGenTarget(final ChunkStatus toStatus) {
        if (toStatus == null) {
            throw new NullPointerException("toStatus cannot be null");
        }
        if (this.requestedGenStatus == null && this.generationTask == null) {
            return false;
        }
        if (this.requestedGenStatus == null || !this.requestedGenStatus.isOrAfter(toStatus)) {
            this.requestedGenStatus = toStatus;
        }
        return true;
    }

    public void setGenerationTarget(final ChunkStatus toStatus) {
        this.requestedGenStatus = toStatus;
    }

    public boolean hasGenerationTask() {
        return this.generationTask != null;
    }

    public ChunkStatus getCurrentGenStatus() {
        return this.currentGenStatus;
    }

    public ChunkStatus getRequestedGenStatus() {
        return this.requestedGenStatus;
    }

    private final Reference2ObjectOpenHashMap<ChunkStatus, List<Consumer<ChunkAccess>>> statusWaiters = new Reference2ObjectOpenHashMap<>();

    void addStatusConsumer(final ChunkStatus status, final Consumer<ChunkAccess> consumer) {
        this.statusWaiters.computeIfAbsent(status, (final ChunkStatus keyInMap) -> {
            return new ArrayList<>(4);
        }).add(consumer);
    }

    private void completeStatusConsumers(ChunkStatus status, final ChunkAccess chunk) {
        // Update progress listener for LevelLoadingScreen
        if (chunk != null) {
            final ChunkProgressListener progressListener = this.world.getChunkSource().chunkMap.progressListener;
            if (progressListener != null) {
                final ChunkStatus finalStatus = status;
                this.scheduler.scheduleChunkTask(this.chunkX, this.chunkZ, () -> {
                    progressListener.onStatusChange(this.vanillaChunkHolder.getPos(), finalStatus);
                });
            }
        }

        // need to tell future statuses to complete if cancelled
        do {
            this.completeStatusConsumers0(status, chunk);
        } while (chunk == null && status != (status = ((ChunkSystemChunkStatus)status).moonrise$getNextStatus()));
    }

    private void completeStatusConsumers0(final ChunkStatus status, final ChunkAccess chunk) {
        final List<Consumer<ChunkAccess>> consumers;
        consumers = this.statusWaiters.remove(status);

        if (consumers == null) {
            return;
        }

        // must be scheduled to main, we do not trust the callback to not do anything stupid
        this.scheduler.scheduleChunkTask(this.chunkX, this.chunkZ, () -> {
            for (final Consumer<ChunkAccess> consumer : consumers) {
                try {
                    consumer.accept(chunk);
                } catch (final Throwable thr) {
                    LOGGER.error("Failed to process chunk status callback", thr);
                }
            }
        }, Priority.HIGHEST);
    }

    private final Reference2ObjectOpenHashMap<FullChunkStatus, List<Consumer<LevelChunk>>> fullStatusWaiters = new Reference2ObjectOpenHashMap<>();

    void addFullStatusConsumer(final FullChunkStatus status, final Consumer<LevelChunk> consumer) {
        this.fullStatusWaiters.computeIfAbsent(status, (final FullChunkStatus keyInMap) -> {
            return new ArrayList<>(4);
        }).add(consumer);
    }

    private void completeFullStatusConsumers(FullChunkStatus status, final LevelChunk chunk) {
        final List<Consumer<LevelChunk>> consumers;
        consumers = this.fullStatusWaiters.remove(status);

        if (consumers == null) {
            return;
        }

        // must be scheduled to main, we do not trust the callback to not do anything stupid
        this.scheduler.scheduleChunkTask(this.chunkX, this.chunkZ, () -> {
            for (final Consumer<LevelChunk> consumer : consumers) {
                try {
                    consumer.accept(chunk);
                } catch (final Throwable thr) {
                    LOGGER.error("Failed to process chunk status callback", thr);
                }
            }
        }, Priority.HIGHEST);
    }

    // note: must hold scheduling lock
    private void onChunkGenComplete(final ChunkAccess newChunk, final ChunkStatus newStatus,
                                    final List<ChunkProgressionTask> scheduleList, final List<NewChunkHolder> changedLoadStatus) {
        if (!this.neighboursBlockingGenTask.isEmpty()) {
            throw new IllegalStateException("Cannot have neighbours blocking this gen task");
        }
        if (newChunk != null || (this.requestedGenStatus == null || !this.requestedGenStatus.isOrAfter(newStatus))) {
            this.completeStatusConsumers(newStatus, newChunk);
        }
        // done now, clear state (must be done before scheduling new tasks)
        this.generationTask = null;
        this.generationTaskStatus = null;
        if (newChunk == null) {
            // task was cancelled
            // should be careful as this could be called while holding the schedule lock and/or inside the
            // ticket level update
            // while a task may be cancelled, it is possible for it to be later re-scheduled
            // however, because generationTask is only set to null on _completion_, the scheduler leaves
            // the rescheduling logic to us here
            final ChunkStatus requestedGenStatus = this.requestedGenStatus;
            this.requestedGenStatus = null;
            if (requestedGenStatus != null) {
                // it looks like it has been requested, so we must reschedule
                if (!this.neighboursWaitingForUs.isEmpty()) {
                    for (final Iterator<Reference2ObjectMap.Entry<NewChunkHolder, ChunkStatus>> iterator = this.neighboursWaitingForUs.reference2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
                        final Reference2ObjectMap.Entry<NewChunkHolder, ChunkStatus> entry = iterator.next();

                        final NewChunkHolder chunkHolder = entry.getKey();
                        final ChunkStatus toStatus = entry.getValue();

                        if (!requestedGenStatus.isOrAfter(toStatus)) {
                            // if we were cancelled, we are responsible for removing the waiter
                            if (!chunkHolder.neighboursBlockingGenTask.remove(this)) {
                                throw new IllegalStateException("Corrupt state");
                            }
                            if (chunkHolder.neighboursBlockingGenTask.isEmpty()) {
                                chunkHolder.checkUnload();
                            }
                            iterator.remove();
                            continue;
                        }
                    }
                }

                // note: only after generationTask -> null, generationTaskStatus -> null, and requestedGenStatus -> null
                this.scheduler.schedule(
                    this.chunkX, this.chunkZ, requestedGenStatus, this, scheduleList
                );

                // return, can't do anything further
                return;
            }

            if (!this.neighboursWaitingForUs.isEmpty()) {
                for (final NewChunkHolder chunkHolder : this.neighboursWaitingForUs.keySet()) {
                    if (!chunkHolder.neighboursBlockingGenTask.remove(this)) {
                        throw new IllegalStateException("Corrupt state");
                    }
                    if (chunkHolder.neighboursBlockingGenTask.isEmpty()) {
                        chunkHolder.checkUnload();
                    }
                }
                this.neighboursWaitingForUs.clear();
            }
            // reset priority, we have nothing left to generate to
            this.setPriority(null);
            this.checkUnload();
            return;
        }

        this.currentChunk = newChunk;
        this.currentGenStatus = newStatus;
        final ChunkCompletion completion = new ChunkCompletion(newChunk, newStatus);
        CHUNK_COMPLETION_ARRAY_HANDLE.setVolatile(this.chunkCompletions, newStatus.getIndex(), completion);
        this.lastChunkCompletion = completion;

        final ChunkStatus requestedGenStatus = this.requestedGenStatus;

        List<NewChunkHolder> needsScheduling = null;
        boolean recalculatePriority = false;
        for (final Iterator<Reference2ObjectMap.Entry<NewChunkHolder, ChunkStatus>> iterator
             = this.neighboursWaitingForUs.reference2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
            final Reference2ObjectMap.Entry<NewChunkHolder, ChunkStatus> entry = iterator.next();
            final NewChunkHolder neighbour = entry.getKey();
            final ChunkStatus requiredStatus = entry.getValue();

            if (!newStatus.isOrAfter(requiredStatus)) {
                if (requestedGenStatus == null || !requestedGenStatus.isOrAfter(requiredStatus)) {
                    // if we're cancelled, still need to clear this map
                    if (!neighbour.neighboursBlockingGenTask.remove(this)) {
                        throw new IllegalStateException("Neighbour is not waiting for us?");
                    }
                    if (neighbour.neighboursBlockingGenTask.isEmpty()) {
                        neighbour.checkUnload();
                    }

                    iterator.remove();
                }
                continue;
            }

            // doesn't matter what isCancelled is here, we need to schedule if we can

            recalculatePriority = true;
            if (!neighbour.neighboursBlockingGenTask.remove(this)) {
                throw new IllegalStateException("Neighbour is not waiting for us?");
            }

            if (neighbour.neighboursBlockingGenTask.isEmpty()) {
                if (neighbour.requestedGenStatus != null) {
                    if (needsScheduling == null) {
                        needsScheduling = new ArrayList<>();
                    }
                    needsScheduling.add(neighbour);
                } else {
                    neighbour.checkUnload();
                }
            }

            // remove last; access to entry will throw if removed
            iterator.remove();
        }

        if (newStatus == ChunkStatus.FULL) {
            this.lockPriority();
            // try to push pending to FULL
            if (this.updatePendingStatus()) {
                changedLoadStatus.add(this);
            }
        }

        if (recalculatePriority) {
            this.recalculateNeighbourRequestedPriority();
        }

        if (requestedGenStatus != null && !newStatus.isOrAfter(requestedGenStatus)) {
            this.scheduleNeighbours(needsScheduling, scheduleList);

            // we need to schedule more tasks now
            this.scheduler.schedule(
                this.chunkX, this.chunkZ, requestedGenStatus, this, scheduleList
            );
        } else {
            // we're done now
            if (requestedGenStatus != null) {
                this.requestedGenStatus = null;
            }
            // reached final stage, so stop scheduling now
            this.setPriority(null);
            this.checkUnload();

            this.scheduleNeighbours(needsScheduling, scheduleList);
        }
    }

    private void scheduleNeighbours(final List<NewChunkHolder> needsScheduling, final List<ChunkProgressionTask> scheduleList) {
        if (needsScheduling != null) {
            for (int i = 0, len = needsScheduling.size(); i < len; ++i) {
                final NewChunkHolder neighbour = needsScheduling.get(i);

                this.scheduler.schedule(
                    neighbour.chunkX, neighbour.chunkZ, neighbour.requestedGenStatus, neighbour, scheduleList
                );
            }
        }
    }

    public void setGenerationTask(final ChunkProgressionTask generationTask, final ChunkStatus taskStatus,
                                  final List<NewChunkHolder> neighbours) {
        if (this.generationTask != null || (this.currentGenStatus != null && this.currentGenStatus.isOrAfter(taskStatus))) {
            throw new IllegalStateException("Currently generating or provided task is trying to generate to a level we are already at!");
        }
        if (this.requestedGenStatus == null || !this.requestedGenStatus.isOrAfter(taskStatus)) {
            throw new IllegalStateException("Cannot schedule generation task when not requested");
        }
        this.generationTask = generationTask;
        this.generationTaskStatus = taskStatus;

        for (int i = 0, len = neighbours.size(); i < len; ++i) {
            neighbours.get(i).addNeighbourUsingChunk();
        }

        this.checkUnload();

        generationTask.onComplete((final ChunkAccess access, final Throwable thr) -> {
            if (generationTask != this.generationTask) {
                throw new IllegalStateException(
                    "Cannot complete generation task '" + generationTask + "' because we are waiting on '" + this.generationTask + "' instead!"
                );
            }
            if (thr != null) {
                if (this.genTaskException != null) {
                    LOGGER.warn("Ignoring exception for " + this.toString(), thr);
                    return;
                }
                // don't set generation task to null, so that scheduling will not attempt to create another task and it
                // will automatically block any further scheduling usage of this chunk as it will wait forever for a failed
                // task to complete
                this.genTaskException = thr;
                this.failedGenStatus = taskStatus;
                this.genTaskFailedThread = Thread.currentThread();

                this.scheduler.unrecoverableChunkSystemFailure(this.chunkX, this.chunkZ, Map.of(
                    "Generation task", ChunkTaskScheduler.stringIfNull(generationTask),
                    "Task to status", ChunkTaskScheduler.stringIfNull(taskStatus)
                ), thr);
                return;
            }

            final boolean scheduleTasks;
            List<ChunkProgressionTask> tasks = ChunkHolderManager.getCurrentTicketUpdateScheduling();
            if (tasks == null) {
                scheduleTasks = true;
                tasks = new ArrayList<>();
            } else {
                scheduleTasks = false;
                // we are currently updating ticket levels, so we already hold the schedule lock
                // this means we have to leave the ticket level update to handle the scheduling
            }
            final List<NewChunkHolder> changedLoadStatus = new ArrayList<>();
            // theoretically, we could schedule a chunk at the max radius which performs another max radius access. So we need to double the radius.
            final ReentrantAreaLock.Node schedulingLock = this.scheduler.schedulingLockArea.lock(this.chunkX, this.chunkZ, 2 * ChunkTaskScheduler.getMaxAccessRadius());
            try {
                for (int i = 0, len = neighbours.size(); i < len; ++i) {
                    neighbours.get(i).removeNeighbourUsingChunk();
                }
                this.onChunkGenComplete(access, taskStatus, tasks, changedLoadStatus);
            } finally {
                this.scheduler.schedulingLockArea.unlock(schedulingLock);
            }
            this.scheduler.chunkHolderManager.addChangedStatuses(changedLoadStatus);

            if (scheduleTasks) {
                // can't hold the lock while scheduling, so we have to build the tasks and then schedule after
                for (int i = 0, len = tasks.size(); i < len; ++i) {
                    tasks.get(i).schedule();
                }
            }
        });
    }

    public PoiChunk getPoiChunk() {
        return this.poiChunk;
    }

    public ChunkEntitySlices getEntityChunk() {
        return this.entityChunk;
    }

    public long lastAutoSave;

    public static final record SaveStat(boolean savedChunk, boolean savedEntityChunk, boolean savedPoiChunk) {}

    private static final MoonriseRegionFileIO.RegionFileType[] REGION_FILE_TYPES = MoonriseRegionFileIO.RegionFileType.values();

    public SaveStat save(final boolean shutdown) {
        TickThread.ensureTickThread(this.world, this.chunkX, this.chunkZ, "Cannot save data off-main");

        ChunkAccess chunk = this.getCurrentChunk();
        PoiChunk poi = this.getPoiChunk();
        ChunkEntitySlices entities = this.getEntityChunk();
        boolean executedUnloadTask = false;
        final boolean[] executedUnloadTasks = new boolean[REGION_FILE_TYPES.length];

        if (shutdown) {
            // make sure that the async unloads complete
            if (this.unloadState != null) {
                // must have errored during unload
                chunk = this.unloadState.chunk();
                poi = this.unloadState.poiChunk();
                entities = this.unloadState.entityChunk();
            }
            for (final MoonriseRegionFileIO.RegionFileType regionFileType : REGION_FILE_TYPES) {
                final UnloadTask unloadTask = this.getUnloadTask(regionFileType);
                if (unloadTask == null) {
                    continue;
                }

                final PrioritisedExecutor.PrioritisedTask task = unloadTask.task();
                if (task != null && task.isQueued()) {
                    final boolean executed = task.execute();
                    executedUnloadTask |= executed;
                    executedUnloadTasks[regionFileType.ordinal()] = executed;
                }
            }
        }

        final boolean forceNoSaveChunk = ChunkSystemFeatures.forceNoSave(chunk);

        // can only synchronously save worldgen chunks during shutdown
        boolean canSaveChunk = !forceNoSaveChunk && (chunk != null && ((shutdown || chunk instanceof LevelChunk) && chunk.isUnsaved()));
        boolean canSavePOI = !forceNoSaveChunk && (poi != null && poi.isDirty());
        boolean canSaveEntities = entities != null;

        if (canSaveChunk) {
            canSaveChunk = this.saveChunk(chunk, false);
        }
        if (canSavePOI) {
            canSavePOI = this.savePOI(poi, false);
        }
        if (canSaveEntities) {
            // on shutdown, we need to force transient entity chunks to save
            canSaveEntities = this.saveEntities(entities, shutdown);
            if (shutdown) {
                this.lastEntityUnload = null;
            }
        }

        return executedUnloadTask | canSaveChunk | canSaveEntities | canSavePOI ?
                new SaveStat(
                        canSaveChunk | executedUnloadTasks[MoonriseRegionFileIO.RegionFileType.CHUNK_DATA.ordinal()],
                        canSaveEntities | executedUnloadTasks[MoonriseRegionFileIO.RegionFileType.ENTITY_DATA.ordinal()],
                        canSavePOI | executedUnloadTasks[MoonriseRegionFileIO.RegionFileType.POI_DATA.ordinal()]
                )
                : null;
    }

    static final class AsyncChunkSerializeTask implements Runnable {

        private final ServerLevel world;
        private final ChunkAccess chunk;
        private final AsyncChunkSaveData asyncSaveData;
        private final NewChunkHolder toComplete;

        public AsyncChunkSerializeTask(final ServerLevel world, final ChunkAccess chunk, final AsyncChunkSaveData asyncSaveData,
                                       final NewChunkHolder toComplete) {
            this.world = world;
            this.chunk = chunk;
            this.asyncSaveData = asyncSaveData;
            this.toComplete = toComplete;
        }

        @Override
        public void run() {
            final CompoundTag toSerialize;
            try {
                toSerialize = ChunkSystemFeatures.saveChunkAsync(this.world, this.chunk, this.asyncSaveData);
            } catch (final Throwable throwable) {
                LOGGER.error("Failed to asynchronously save chunk " + this.chunk.getPos() + " for world '" + WorldUtil.getWorldName(this.world) + "', falling back to synchronous save", throwable);
                final ChunkPos pos = this.chunk.getPos();
                ((ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().scheduleChunkTask(pos.x, pos.z, () -> {
                    final CompoundTag synchronousSave;
                    try {
                        synchronousSave = ChunkSystemFeatures.saveChunkAsync(AsyncChunkSerializeTask.this.world, AsyncChunkSerializeTask.this.chunk, AsyncChunkSerializeTask.this.asyncSaveData);
                    } catch (final Throwable throwable2) {
                        LOGGER.error("Failed to synchronously save chunk " + AsyncChunkSerializeTask.this.chunk.getPos() + " for world '" + WorldUtil.getWorldName(AsyncChunkSerializeTask.this.world) + "', chunk data will be lost", throwable2);
                        AsyncChunkSerializeTask.this.toComplete.completeAsyncUnloadDataSave(MoonriseRegionFileIO.RegionFileType.CHUNK_DATA, null);
                        return;
                    }

                    AsyncChunkSerializeTask.this.toComplete.completeAsyncUnloadDataSave(MoonriseRegionFileIO.RegionFileType.CHUNK_DATA, synchronousSave);
                    LOGGER.info("Successfully serialized chunk " + AsyncChunkSerializeTask.this.chunk.getPos() + " for world '" + WorldUtil.getWorldName(AsyncChunkSerializeTask.this.world) + "' synchronously");

                }, Priority.HIGHEST);
                return;
            }
            this.toComplete.completeAsyncUnloadDataSave(MoonriseRegionFileIO.RegionFileType.CHUNK_DATA, toSerialize);
        }

        @Override
        public String toString() {
            return "AsyncChunkSerializeTask{" +
                "chunk={pos=" + this.chunk.getPos() + ",world=\"" + WorldUtil.getWorldName(this.world) + "\"}" +
                "}";
        }
    }

    private boolean saveChunk(final ChunkAccess chunk, final boolean unloading) {
        if (!chunk.isUnsaved()) {
            if (unloading) {
                this.completeAsyncUnloadDataSave(MoonriseRegionFileIO.RegionFileType.CHUNK_DATA, null);
            }
            return false;
        }
        boolean completing = false;
        boolean failedAsyncPrepare = false;
        try {
            if (unloading && ChunkSystemFeatures.supportsAsyncChunkSave()) {
                try {
                    final AsyncChunkSaveData asyncSaveData = ChunkSystemFeatures.getAsyncSaveData(this.world, chunk);

                    this.chunkDataUnload.toRun().setRunnable(new AsyncChunkSerializeTask(this.world, chunk, asyncSaveData, this));

                    chunk.setUnsaved(false);

                    this.chunkDataUnload.task().queue();

                    return true;
                } catch (final Throwable thr) {
                    LOGGER.error("Failed to prepare async chunk data (" + this.chunkX + "," + this.chunkZ + ") in world '" + WorldUtil.getWorldName(this.world) + "', falling back to synchronous save", thr);
                    failedAsyncPrepare = true;
                    // fall through to synchronous save
                }
            }

            final CompoundTag save = ChunkSerializer.write(this.world, chunk);
            PlatformHooks.get().chunkSyncSave(this.world, chunk, save);

            if (unloading) {
                completing = true;
                this.completeAsyncUnloadDataSave(MoonriseRegionFileIO.RegionFileType.CHUNK_DATA, save);
                if (failedAsyncPrepare) {
                    LOGGER.info("Successfully serialized chunk data (" + this.chunkX + "," + this.chunkZ + ") in world '" + WorldUtil.getWorldName(this.world) + "' synchronously");
                }
            } else {
                MoonriseRegionFileIO.scheduleSave(this.world, this.chunkX, this.chunkZ, save, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA);
            }
            chunk.setUnsaved(false);
        } catch (final Throwable thr) {
            LOGGER.error("Failed to save chunk data (" + this.chunkX + "," + this.chunkZ + ") in world '" + WorldUtil.getWorldName(this.world) + "'", thr);
            if (unloading && !completing) {
                this.completeAsyncUnloadDataSave(MoonriseRegionFileIO.RegionFileType.CHUNK_DATA, null);
            }
        }

        return true;
    }

    private boolean lastEntitySaveNull;
    private CompoundTag lastEntityUnload;
    private boolean saveEntities(final ChunkEntitySlices entities, final boolean unloading) {
        try {
            CompoundTag mergeFrom = null;
            if (entities.isTransient()) {
                if (!unloading) {
                    // if we're a transient chunk, we cannot save until unloading because otherwise a double save will
                    // result in double adding the entities
                    return false;
                }
                try {
                    mergeFrom = MoonriseRegionFileIO.loadData(this.world, this.chunkX, this.chunkZ, MoonriseRegionFileIO.RegionFileType.ENTITY_DATA, Priority.BLOCKING);
                } catch (final Exception ex) {
                    LOGGER.error("Cannot merge transient entities for chunk (" + this.chunkX + "," + this.chunkZ + ") in world '" + WorldUtil.getWorldName(this.world) + "', data on disk will be replaced", ex);
                }
            }

            final CompoundTag save = entities.save();
            if (mergeFrom != null) {
                if (save == null) {
                    // don't override the data on disk with nothing
                    return false;
                } else {
                    ChunkEntitySlices.copyEntities(mergeFrom, save);
                }
            }
            if (save == null && this.lastEntitySaveNull) {
                return false;
            }

            MoonriseRegionFileIO.scheduleSave(this.world, this.chunkX, this.chunkZ, save, MoonriseRegionFileIO.RegionFileType.ENTITY_DATA);
            this.lastEntitySaveNull = save == null;
            if (unloading) {
                this.lastEntityUnload = save;
            }
        } catch (final Throwable thr) {
            LOGGER.error("Failed to save entity data (" + this.chunkX + "," + this.chunkZ + ") in world '" + WorldUtil.getWorldName(this.world) + "'", thr);
        }

        return true;
    }

    private boolean lastPoiSaveNull;
    private boolean savePOI(final PoiChunk poi, final boolean unloading) {
        try {
            final CompoundTag save = poi.save();
            poi.setDirty(false);
            if (save == null && this.lastPoiSaveNull) {
                if (unloading) {
                    this.poiDataUnload.completable().complete(null);
                }
                return false;
            }

            MoonriseRegionFileIO.scheduleSave(this.world, this.chunkX, this.chunkZ, save, MoonriseRegionFileIO.RegionFileType.POI_DATA);
            this.lastPoiSaveNull = save == null;
            if (unloading) {
                this.poiDataUnload.completable().complete(save);
            }
        } catch (final Throwable thr) {
            LOGGER.error("Failed to save poi data (" + this.chunkX + "," + this.chunkZ + ") in world '" + WorldUtil.getWorldName(this.world) + "'", thr);
        }

        return true;
    }

    @Override
    public String toString() {
        final ChunkCompletion lastCompletion = this.lastChunkCompletion;
        final ChunkEntitySlices entityChunk = this.entityChunk;
        final FullChunkStatus pendingFullStatus = this.pendingFullChunkStatus;
        final FullChunkStatus currentFullStatus = this.currentFullChunkStatus;
        return "NewChunkHolder{" +
            "world=" + WorldUtil.getWorldName(this.world) +
            ", chunkX=" + this.chunkX +
            ", chunkZ=" + this.chunkZ +
            ", entityChunkFromDisk=" + (entityChunk != null && !entityChunk.isTransient()) +
            ", lastChunkCompletion={chunk_class=" + (lastCompletion == null || lastCompletion.chunk() == null ? "null" : lastCompletion.chunk().getClass().getName()) + ",status=" + (lastCompletion == null ? "null" : lastCompletion.genStatus()) + "}" +
            ", currentGenStatus=" + this.currentGenStatus +
            ", requestedGenStatus=" + this.requestedGenStatus +
            ", generationTask=" + this.generationTask +
            ", generationTaskStatus=" + this.generationTaskStatus +
            ", priority=" + this.priority +
            ", priorityLocked=" + this.priorityLocked +
            ", neighbourRequestedPriority=" + this.neighbourRequestedPriority +
            ", effective_priority=" + this.getEffectivePriority(null) +
            ", oldTicketLevel=" + this.oldTicketLevel +
            ", currentTicketLevel=" + this.currentTicketLevel +
            ", totalNeighboursUsingThisChunk=" + this.totalNeighboursUsingThisChunk +
            ", fullNeighbourChunksLoadedBitset=" + this.fullNeighbourChunksLoadedBitset +
            ", currentChunkStatus=" + currentFullStatus +
            ", pendingChunkStatus=" + pendingFullStatus +
            ", is_unload_safe=" + this.isSafeToUnload() +
            ", killed=" + this.unloaded +
            '}';
    }

    private static JsonElement serializeStacktraceElement(final StackTraceElement element) {
        return element == null ? JsonNull.INSTANCE : new JsonPrimitive(element.toString());
    }

    private static JsonObject serializeCompletable(final CallbackCompletable<?> completable) {
        final JsonObject ret = new JsonObject();

        if (completable == null) {
            return ret;
        }

        ret.addProperty("valid", Boolean.TRUE);

        final boolean isCompleted = completable.isCompleted();
        ret.addProperty("completed", Boolean.valueOf(isCompleted));

        if (isCompleted) {
            final Throwable throwable = completable.getThrowable();
            if (throwable != null) {
                final JsonArray throwableJson = new JsonArray();
                ret.add("throwable", throwableJson);

                for (final StackTraceElement element : throwable.getStackTrace()) {
                    throwableJson.add(serializeStacktraceElement(element));
                }
            } else {
                final Object result = completable.getResult();
                ret.add("result_class", result == null ? JsonNull.INSTANCE : new JsonPrimitive(result.getClass().getName()));
            }
        }

        return ret;
    }

    // (probably) holds ticket and scheduling lock
    public JsonObject getDebugJson() {
        final JsonObject ret = new JsonObject();

        final ChunkCompletion lastCompletion = this.lastChunkCompletion;
        final ChunkEntitySlices slices = this.entityChunk;
        final PoiChunk poiChunk = this.poiChunk;

        ret.addProperty("chunkX", Integer.valueOf(this.chunkX));
        ret.addProperty("chunkZ", Integer.valueOf(this.chunkZ));
        ret.addProperty("entity_chunk", slices == null ? "null" : "transient=" + slices.isTransient());
        ret.addProperty("poi_chunk", "null=" + (poiChunk == null));
        ret.addProperty("completed_chunk_class", lastCompletion == null ? "null" : lastCompletion.chunk().getClass().getName());
        ret.addProperty("completed_gen_status", lastCompletion == null ? "null" : lastCompletion.genStatus().toString());
        ret.addProperty("priority", Objects.toString(this.priority));
        ret.addProperty("neighbour_requested_priority", Objects.toString(this.neighbourRequestedPriority));
        ret.addProperty("generation_task", Objects.toString(this.generationTask));
        ret.addProperty("is_safe_unload", Objects.toString(this.isSafeToUnload()));
        ret.addProperty("old_ticket_level", Integer.valueOf(this.oldTicketLevel));
        ret.addProperty("current_ticket_level", Integer.valueOf(this.currentTicketLevel));
        ret.addProperty("neighbours_using_chunk", Integer.valueOf(this.totalNeighboursUsingThisChunk));

        final JsonObject neighbourWaitState = new JsonObject();
        ret.add("neighbour_state", neighbourWaitState);

        final JsonArray blockingGenNeighbours = new JsonArray();
        neighbourWaitState.add("blocking_gen_task", blockingGenNeighbours);
        for (final NewChunkHolder blockingGenNeighbour : this.neighboursBlockingGenTask) {
            final JsonObject neighbour = new JsonObject();
            blockingGenNeighbours.add(neighbour);

            neighbour.addProperty("chunkX", Integer.valueOf(blockingGenNeighbour.chunkX));
            neighbour.addProperty("chunkZ", Integer.valueOf(blockingGenNeighbour.chunkZ));
        }

        final JsonArray neighboursWaitingForUs = new JsonArray();
        neighbourWaitState.add("neighbours_waiting_on_us", neighboursWaitingForUs);
        for (final Reference2ObjectMap.Entry<NewChunkHolder, ChunkStatus> entry : this.neighboursWaitingForUs.reference2ObjectEntrySet()) {
            final NewChunkHolder holder = entry.getKey();
            final ChunkStatus status = entry.getValue();

            final JsonObject neighbour = new JsonObject();
            neighboursWaitingForUs.add(neighbour);


            neighbour.addProperty("chunkX", Integer.valueOf(holder.chunkX));
            neighbour.addProperty("chunkZ", Integer.valueOf(holder.chunkZ));
            neighbour.addProperty("waiting_for", Objects.toString(status));
        }

        ret.addProperty("pending_chunk_full_status", Objects.toString(this.pendingFullChunkStatus));
        ret.addProperty("current_chunk_full_status", Objects.toString(this.currentFullChunkStatus));
        ret.addProperty("generation_task", Objects.toString(this.generationTask));
        ret.addProperty("requested_generation", Objects.toString(this.requestedGenStatus));
        ret.addProperty("has_entity_load_task", Boolean.valueOf(this.entityDataLoadTask != null));
        ret.addProperty("has_poi_load_task", Boolean.valueOf(this.poiDataLoadTask != null));

        final UnloadTask entityDataUnload = this.entityDataUnload;
        final UnloadTask poiDataUnload = this.poiDataUnload;
        final UnloadTask chunkDataUnload = this.chunkDataUnload;

        ret.add("entity_unload_completable", serializeCompletable(entityDataUnload == null ? null : entityDataUnload.completable()));
        ret.add("poi_unload_completable", serializeCompletable(poiDataUnload == null ? null : poiDataUnload.completable()));
        ret.add("chunk_unload_completable", serializeCompletable(chunkDataUnload == null ? null : chunkDataUnload.completable()));

        final PrioritisedExecutor.PrioritisedTask unloadTask = chunkDataUnload == null ? null : chunkDataUnload.task();
        if (unloadTask == null) {
            ret.addProperty("unload_task_priority", "null");
            ret.addProperty("unload_task_suborder", Long.valueOf(0L));
        } else {
            ret.addProperty("unload_task_priority", Objects.toString(unloadTask.getPriority()));
            ret.addProperty("unload_task_suborder", Long.valueOf(unloadTask.getSubOrder()));
        }

        ret.addProperty("killed", Boolean.valueOf(this.unloaded));

        return ret;
    }
}
