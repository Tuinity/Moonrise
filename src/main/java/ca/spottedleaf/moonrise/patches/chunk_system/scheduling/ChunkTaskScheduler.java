package ca.spottedleaf.moonrise.patches.chunk_system.scheduling;

import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.executor.queue.PrioritisedTaskQueue;
import ca.spottedleaf.concurrentutil.executor.thread.PrioritisedThreadPool;
import ca.spottedleaf.concurrentutil.lock.ReentrantAreaLock;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.config.moonrise.MoonriseConfig;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.JsonUtil;
import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkStatus;
import ca.spottedleaf.moonrise.patches.chunk_system.player.ChunkSystemServerPlayer;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.executor.RadiusAwarePrioritisedExecutor;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task.ChunkFullTask;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task.ChunkLightTask;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task.ChunkLoadTask;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task.ChunkProgressionTask;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task.ChunkUpgradeGenericStatusTask;
import ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer;
import ca.spottedleaf.moonrise.patches.chunk_system.status.ChunkSystemChunkStep;
import ca.spottedleaf.moonrise.patches.chunk_system.util.ParallelSearchRadiusIteration;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class ChunkTaskScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkTaskScheduler.class);

    public static void init(final boolean useParallelGen) {
        for (final PrioritisedThreadPool.ExecutorGroup.ThreadPoolExecutor executor : MoonriseCommon.RADIUS_AWARE_GROUP.getAllExecutors()) {
            executor.setMaxParallelism(useParallelGen ? -1 : 1);
        }

        LOGGER.info("Chunk system is using population gen parallelism: " + useParallelGen);
    }

    public static final TicketType<Long> CHUNK_LOAD = TicketType.create("chunk_system:chunk_load", Long::compareTo);
    private static final AtomicLong CHUNK_LOAD_IDS = new AtomicLong();

    public static Long getNextChunkLoadId() {
        return Long.valueOf(CHUNK_LOAD_IDS.getAndIncrement());
    }

    public static final TicketType<Long> NON_FULL_CHUNK_LOAD = TicketType.create("chunk_system:non_full_load", Long::compareTo);
    private static final AtomicLong NON_FULL_CHUNK_LOAD_IDS = new AtomicLong();

    public static Long getNextNonFullLoadId() {
        return Long.valueOf(NON_FULL_CHUNK_LOAD_IDS.getAndIncrement());
    }

    public static final TicketType<Long> ENTITY_LOAD = TicketType.create("chunk_system:entity_load", Long::compareTo);
    private static final AtomicLong ENTITY_LOAD_IDS = new AtomicLong();

    public static Long getNextEntityLoadId() {
        return Long.valueOf(ENTITY_LOAD_IDS.getAndIncrement());
    }

    public static final TicketType<Long> POI_LOAD = TicketType.create("chunk_system:poi_load", Long::compareTo);
    private static final AtomicLong POI_LOAD_IDS = new AtomicLong();

    public static Long getNextPoiLoadId() {
        return Long.valueOf(POI_LOAD_IDS.getAndIncrement());
    }

    public static final TicketType<Long> CHUNK_RELIGHT = TicketType.create("starlight:chunk_relight", Long::compareTo);
    private static final AtomicLong CHUNK_RELIGHT_IDS = new AtomicLong();

    public static Long getNextChunkRelightId() {
        return Long.valueOf(CHUNK_RELIGHT_IDS.getAndIncrement());
    }


    public static int getTicketLevel(final ChunkStatus status) {
        return ChunkLevel.byStatus(status);
    }

    public final ServerLevel world;
    public final RadiusAwarePrioritisedExecutor radiusAwareScheduler;
    public final PrioritisedThreadPool.ExecutorGroup.ThreadPoolExecutor parallelGenExecutor;
    private final PrioritisedThreadPool.ExecutorGroup.ThreadPoolExecutor radiusAwareGenExecutor;
    public final PrioritisedThreadPool.ExecutorGroup.ThreadPoolExecutor loadExecutor;
    public final PrioritisedThreadPool.ExecutorGroup.ThreadPoolExecutor ioExecutor;
    public final PrioritisedThreadPool.ExecutorGroup.ThreadPoolExecutor compressionExecutor;
    public final PrioritisedThreadPool.ExecutorGroup.ThreadPoolExecutor saveExecutor;

    private final PrioritisedTaskQueue mainThreadExecutor = new PrioritisedTaskQueue();

    public final ChunkHolderManager chunkHolderManager;

    static {
        ((ChunkSystemChunkStatus)ChunkStatus.EMPTY).moonrise$setWriteRadius(0);
        ((ChunkSystemChunkStatus)ChunkStatus.STRUCTURE_STARTS).moonrise$setWriteRadius(0);
        ((ChunkSystemChunkStatus)ChunkStatus.STRUCTURE_REFERENCES).moonrise$setWriteRadius(0);
        ((ChunkSystemChunkStatus)ChunkStatus.BIOMES).moonrise$setWriteRadius(0);
        ((ChunkSystemChunkStatus)ChunkStatus.NOISE).moonrise$setWriteRadius(0);
        ((ChunkSystemChunkStatus)ChunkStatus.SURFACE).moonrise$setWriteRadius(0);
        ((ChunkSystemChunkStatus)ChunkStatus.CARVERS).moonrise$setWriteRadius(0);
        ((ChunkSystemChunkStatus)ChunkStatus.FEATURES).moonrise$setWriteRadius(1);
        ((ChunkSystemChunkStatus)ChunkStatus.INITIALIZE_LIGHT).moonrise$setWriteRadius(0);
        ((ChunkSystemChunkStatus)ChunkStatus.LIGHT).moonrise$setWriteRadius(2);
        ((ChunkSystemChunkStatus)ChunkStatus.SPAWN).moonrise$setWriteRadius(0);
        ((ChunkSystemChunkStatus)ChunkStatus.FULL).moonrise$setWriteRadius(0);

        ((ChunkSystemChunkStatus)ChunkStatus.EMPTY).moonrise$setEmptyLoadStatus(true);
        ((ChunkSystemChunkStatus)ChunkStatus.STRUCTURE_REFERENCES).moonrise$setEmptyLoadStatus(true);
        ((ChunkSystemChunkStatus)ChunkStatus.BIOMES).moonrise$setEmptyLoadStatus(true);
        ((ChunkSystemChunkStatus)ChunkStatus.NOISE).moonrise$setEmptyLoadStatus(true);
        ((ChunkSystemChunkStatus)ChunkStatus.SURFACE).moonrise$setEmptyLoadStatus(true);
        ((ChunkSystemChunkStatus)ChunkStatus.CARVERS).moonrise$setEmptyLoadStatus(true);
        ((ChunkSystemChunkStatus)ChunkStatus.FEATURES).moonrise$setEmptyLoadStatus(true);
        ((ChunkSystemChunkStatus)ChunkStatus.SPAWN).moonrise$setEmptyLoadStatus(true);

        /*
          It's important that the neighbour read radius is taken into account. If _any_ later status is using some chunk as
          a neighbour, it must be also safe if that neighbour is being generated. i.e for any status later than FEATURES,
          for a status to be parallel safe it must not read the block data from its neighbours.
         */
        final List<ChunkStatus> parallelCapableStatus = Arrays.asList(
                // No-op executor.
                ChunkStatus.EMPTY,

                // This is parallel capable, as CB has fixed the concurrency issue with stronghold generations.
                // Does not touch neighbour chunks.
                ChunkStatus.STRUCTURE_STARTS,

                // Surprisingly this is parallel capable. It is simply reading the already-created structure starts
                // into the structure references for the chunk. So while it reads from it neighbours, its neighbours
                // will not change, even if executed in parallel.
                ChunkStatus.STRUCTURE_REFERENCES,

                // Safe. Mojang runs it in parallel as well.
                ChunkStatus.BIOMES,

                // Safe. Mojang runs it in parallel as well.
                ChunkStatus.NOISE,

                // Parallel safe. Only touches the target chunk. Biome retrieval is now noise based, which is
                // completely thread-safe.
                ChunkStatus.SURFACE,

                // No global state is modified in the carvers. It only touches the specified chunk. So it is parallel safe.
                ChunkStatus.CARVERS,

                // FEATURES is not parallel safe. It writes to neighbours.

                // no-op executor
                ChunkStatus.INITIALIZE_LIGHT

                // LIGHT is not parallel safe. It also doesn't run on the generation executor, so no point.

                // Only writes to the specified chunk. State is not read by later statuses. Parallel safe.
                // Note: it may look unsafe because it writes to a worldgenregion, but the region size is always 0 -
                // see the task margin.
                // However, if the neighbouring FEATURES chunk is unloaded, but then fails to load in again (for whatever
                // reason), then it would write to this chunk - and since this status reads blocks from itself, it's not
                // safe to execute this in parallel.
                // SPAWN

                // FULL is executed on main.
        );

        for (final ChunkStatus status : parallelCapableStatus) {
            ((ChunkSystemChunkStatus)status).moonrise$setParallelCapable(true);
        }
    }

    private static final int[] ACCESS_RADIUS_TABLE_LOAD = new int[ChunkStatus.getStatusList().size()];
    private static final int[] ACCESS_RADIUS_TABLE_GEN = new int[ChunkStatus.getStatusList().size()];
    private static final int[] ACCESS_RADIUS_TABLE = new int[ChunkStatus.getStatusList().size()];
    static {
        Arrays.fill(ACCESS_RADIUS_TABLE_LOAD, -1);
        Arrays.fill(ACCESS_RADIUS_TABLE_GEN, -1);
        Arrays.fill(ACCESS_RADIUS_TABLE, -1);
    }

    private static int getAccessRadius0(final ChunkStatus toStatus, final ChunkPyramid pyramid) {
        if (toStatus == ChunkStatus.EMPTY) {
            return 0;
        }

        final ChunkStep chunkStep = pyramid.getStepTo(toStatus);

        final int radius = chunkStep.getAccumulatedRadiusOf(ChunkStatus.EMPTY);
        int maxRange = radius;

        for (int dist = 0; dist <= radius; ++dist) {
            final ChunkStatus requiredNeighbourStatus = ((ChunkSystemChunkStep)(Object)chunkStep).moonrise$getRequiredStatusAtRadius(dist);
            final int rad = ACCESS_RADIUS_TABLE[requiredNeighbourStatus.getIndex()];
            if (rad == -1) {
                throw new IllegalStateException();
            }

            maxRange = Math.max(maxRange, dist + rad);
        }

        return maxRange;
    }

    private static final int MAX_ACCESS_RADIUS;

    static {
        final List<ChunkStatus> statuses = ChunkStatus.getStatusList();
        for (int i = 0, len = statuses.size(); i < len; ++i) {
            final ChunkStatus status = statuses.get(i);
            ACCESS_RADIUS_TABLE_LOAD[i] = getAccessRadius0(status, ChunkPyramid.LOADING_PYRAMID);
            ACCESS_RADIUS_TABLE_GEN[i] = getAccessRadius0(status, ChunkPyramid.GENERATION_PYRAMID);
            ACCESS_RADIUS_TABLE[i] = Math.max(
                    ACCESS_RADIUS_TABLE_LOAD[i],
                    ACCESS_RADIUS_TABLE_GEN[i]
            );
        }
        MAX_ACCESS_RADIUS = ACCESS_RADIUS_TABLE[ACCESS_RADIUS_TABLE.length - 1];
    }

    public static int getMaxAccessRadius() {
        return MAX_ACCESS_RADIUS;
    }

    public static int getAccessRadius(final ChunkStatus genStatus) {
        return ACCESS_RADIUS_TABLE[genStatus.getIndex()];
    }

    public static int getAccessRadius(final FullChunkStatus status) {
        return (status.ordinal() - 1) + getAccessRadius(ChunkStatus.FULL);
    }


    public final ReentrantAreaLock schedulingLockArea;
    private final int lockShift;

    public final int getChunkSystemLockShift() {
        return this.lockShift;
    }

    public ChunkTaskScheduler(final ServerLevel world) {
        this.world = world;
        // must be >= region shift (in paper, doesn't exist) and must be >= ticket propagator section shift
        // it must be >= region shift since the regioniser assumes ticket updates do not occur in parallel for the region sections
        // it must be >= ticket propagator section shift so that the ticket propagator can assume that owning a position implies owning
        // the entire section
        // we just take the max, as we want the smallest shift that satisfies these properties
        this.lockShift = Math.max(((ChunkSystemServerLevel)world).moonrise$getRegionChunkShift(), ThreadedTicketLevelPropagator.SECTION_SHIFT);
        this.schedulingLockArea = new ReentrantAreaLock(this.getChunkSystemLockShift());

        this.parallelGenExecutor = MoonriseCommon.PARALLEL_GEN_GROUP.createExecutor(-1, MoonriseCommon.WORKER_QUEUE_HOLD_TIME, 0);
        this.radiusAwareGenExecutor = MoonriseCommon.RADIUS_AWARE_GROUP.createExecutor(1, MoonriseCommon.WORKER_QUEUE_HOLD_TIME, 0);
        this.loadExecutor = MoonriseCommon.LOAD_GROUP.createExecutor(-1, MoonriseCommon.WORKER_QUEUE_HOLD_TIME, 0);
        this.radiusAwareScheduler = new RadiusAwarePrioritisedExecutor(this.radiusAwareGenExecutor, 16);
        this.ioExecutor = MoonriseCommon.SERVER_REGION_IO_GROUP.createExecutor(-1, MoonriseCommon.IO_QUEUE_HOLD_TIME, 0);
        // we need a separate executor here so that on shutdown we can continue to process I/O tasks
        this.compressionExecutor = MoonriseCommon.LOAD_GROUP.createExecutor(-1, MoonriseCommon.WORKER_QUEUE_HOLD_TIME, 0);
        this.saveExecutor = MoonriseCommon.LOAD_GROUP.createExecutor(-1, MoonriseCommon.WORKER_QUEUE_HOLD_TIME, 0);
        this.chunkHolderManager = new ChunkHolderManager(world, this);
    }

    private final AtomicBoolean failedChunkSystem = new AtomicBoolean();

    public static Object stringIfNull(final Object obj) {
        return obj == null ? "null" : obj;
    }

    public void unrecoverableChunkSystemFailure(final int chunkX, final int chunkZ, final Map<String, Object> objectsOfInterest, final Throwable thr) {
        final NewChunkHolder holder = this.chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        LOGGER.error("Chunk system error at chunk (" + chunkX + "," + chunkZ + "), holder: " + holder + ", exception:", new Throwable(thr));

        if (this.failedChunkSystem.getAndSet(true)) {
            return;
        }

        final ReportedException reportedException = thr instanceof ReportedException ? (ReportedException)thr : new ReportedException(new CrashReport("Chunk system error", thr));

        CrashReportCategory crashReportCategory = reportedException.getReport().addCategory("Chunk system details");
        crashReportCategory.setDetail("Chunk coordinate", new ChunkPos(chunkX, chunkZ).toString());
        crashReportCategory.setDetail("ChunkHolder", Objects.toString(holder));
        crashReportCategory.setDetail("unrecoverableChunkSystemFailure caller thread", Thread.currentThread().getName());

        crashReportCategory = reportedException.getReport().addCategory("Chunk System Objects of Interest");
        for (final Map.Entry<String, Object> entry : objectsOfInterest.entrySet()) {
            if (entry.getValue() instanceof Throwable thrObject) {
                crashReportCategory.setDetailError(Objects.toString(entry.getKey()), thrObject);
            } else {
                crashReportCategory.setDetail(Objects.toString(entry.getKey()), Objects.toString(entry.getValue()));
            }
        }

        final Runnable crash = () -> {
            throw new RuntimeException("Chunk system crash propagated from unrecoverableChunkSystemFailure", reportedException);
        };

        // this may not be good enough, specifically thanks to stupid ass plugins swallowing exceptions
        this.scheduleChunkTask(chunkX, chunkZ, crash, Priority.BLOCKING);
        // so, make the main thread pick it up
        ((ChunkSystemMinecraftServer)this.world.getServer()).moonrise$setChunkSystemCrash(new RuntimeException("Chunk system crash propagated from unrecoverableChunkSystemFailure", reportedException));
    }

    public boolean executeMainThreadTask() {
        TickThread.ensureTickThread("Cannot execute main thread task off-main");
        return this.mainThreadExecutor.executeTask();
    }

    public void raisePriority(final int x, final int z, final Priority priority) {
        this.chunkHolderManager.raisePriority(x, z, priority);
    }

    public void setPriority(final int x, final int z, final Priority priority) {
        this.chunkHolderManager.setPriority(x, z, priority);
    }

    public void lowerPriority(final int x, final int z, final Priority priority) {
        this.chunkHolderManager.lowerPriority(x, z, priority);
    }

    public void scheduleTickingState(final int chunkX, final int chunkZ, final FullChunkStatus toStatus,
                                     final boolean addTicket, final Priority priority,
                                     final Consumer<LevelChunk> onComplete) {
        final int radius = toStatus.ordinal() - 1; // 0 -> BORDER, 1 -> TICKING, 2 -> ENTITY_TICKING

        if (!TickThread.isTickThreadFor(this.world, chunkX, chunkZ, Math.max(0, radius))) {
            this.scheduleChunkTask(chunkX, chunkZ, () -> {
                ChunkTaskScheduler.this.scheduleTickingState(chunkX, chunkZ, toStatus, addTicket, priority, onComplete);
            }, priority);
            return;
        }
        final int accessRadius = getAccessRadius(toStatus);
        if (this.chunkHolderManager.ticketLockArea.isHeldByCurrentThread(chunkX, chunkZ, accessRadius)) {
            throw new IllegalStateException("Cannot schedule chunk load during ticket level update");
        }
        if (this.schedulingLockArea.isHeldByCurrentThread(chunkX, chunkZ, accessRadius)) {
            throw new IllegalStateException("Cannot schedule chunk loading recursively");
        }

        if (toStatus == FullChunkStatus.INACCESSIBLE) {
            throw new IllegalArgumentException("Cannot wait for INACCESSIBLE status");
        }

        final int minLevel = 33 - (toStatus.ordinal() - 1);
        final Long chunkReference = addTicket ? getNextChunkLoadId() : null;
        final long chunkKey = CoordinateUtils.getChunkKey(chunkX, chunkZ);

        if (addTicket) {
            this.chunkHolderManager.addTicketAtLevel(CHUNK_LOAD, chunkKey, minLevel, chunkReference);
            this.chunkHolderManager.processTicketUpdates();
        }

        final Consumer<LevelChunk> loadCallback = onComplete == null && !addTicket ? null : (final LevelChunk chunk) -> {
            try {
                if (onComplete != null) {
                    onComplete.accept(chunk);
                }
            } finally {
                if (addTicket) {
                    ChunkTaskScheduler.this.chunkHolderManager.removeTicketAtLevel(CHUNK_LOAD, chunkKey, minLevel, chunkReference);
                }
            }
        };

        final boolean scheduled;
        final LevelChunk chunk;
        final ReentrantAreaLock.Node ticketLock = this.chunkHolderManager.ticketLockArea.lock(chunkX, chunkZ, accessRadius);
        try {
            final ReentrantAreaLock.Node schedulingLock = this.schedulingLockArea.lock(chunkX, chunkZ, accessRadius);
            try {
                final NewChunkHolder chunkHolder = this.chunkHolderManager.getChunkHolder(chunkKey);
                if (chunkHolder == null || chunkHolder.getTicketLevel() > minLevel) {
                    scheduled = false;
                    chunk = null;
                } else {
                    final FullChunkStatus currStatus = chunkHolder.getChunkStatus();
                    if (currStatus.isOrAfter(toStatus)) {
                        scheduled = false;
                        chunk = (LevelChunk)chunkHolder.getCurrentChunk();
                    } else {
                        scheduled = true;
                        chunk = null;

                        for (int dz = -radius; dz <= radius; ++dz) {
                            for (int dx = -radius; dx <= radius; ++dx) {
                                final NewChunkHolder neighbour =
                                    (dx | dz) == 0 ? chunkHolder : this.chunkHolderManager.getChunkHolder(dx + chunkX, dz + chunkZ);
                                if (neighbour != null) {
                                    neighbour.raisePriority(priority);
                                }
                            }
                        }

                        // ticket level should schedule for us
                        if (loadCallback != null) {
                            chunkHolder.addFullStatusConsumer(toStatus, loadCallback);
                        }
                    }
                }
            } finally {
                this.schedulingLockArea.unlock(schedulingLock);
            }
        } finally {
            this.chunkHolderManager.ticketLockArea.unlock(ticketLock);
        }

        if (loadCallback != null && !scheduled) {
            // couldn't schedule
            try {
                loadCallback.accept(chunk);
            } catch (final Throwable thr) {
                LOGGER.error("Failed to process chunk full status callback", thr);
            }
        }
    }

    public void scheduleChunkLoad(final int chunkX, final int chunkZ, final boolean gen, final ChunkStatus toStatus, final boolean addTicket,
                                  final Priority priority, final Consumer<ChunkAccess> onComplete) {
        if (gen) {
            this.scheduleChunkLoad(chunkX, chunkZ, toStatus, addTicket, priority, onComplete);
            return;
        }
        this.scheduleChunkLoad(chunkX, chunkZ, ChunkStatus.EMPTY, addTicket, priority, (final ChunkAccess chunk) -> {
            if (chunk == null) {
                if (onComplete != null) {
                    onComplete.accept(null);
                }
            } else {
                if (chunk.getPersistedStatus().isOrAfter(toStatus)) {
                    this.scheduleChunkLoad(chunkX, chunkZ, toStatus, addTicket, priority, onComplete);
                } else {
                    if (onComplete != null) {
                        onComplete.accept(null);
                    }
                }
            }
        });
    }

    // only appropriate to use with syncLoadNonFull
    public boolean beginChunkLoadForNonFullSync(final int chunkX, final int chunkZ, final ChunkStatus toStatus,
                                                final Priority priority) {
        final int accessRadius = getAccessRadius(toStatus);
        final long chunkKey = CoordinateUtils.getChunkKey(chunkX, chunkZ);
        final int minLevel = ChunkTaskScheduler.getTicketLevel(toStatus);
        final List<ChunkProgressionTask> tasks = new ArrayList<>();
        final ReentrantAreaLock.Node ticketLock = this.chunkHolderManager.ticketLockArea.lock(chunkX, chunkZ, accessRadius); // Folia - use area based lock to reduce contention
        try {
            final ReentrantAreaLock.Node schedulingLock = this.schedulingLockArea.lock(chunkX, chunkZ, accessRadius); // Folia - use area based lock to reduce contention
            try {
                final NewChunkHolder chunkHolder = this.chunkHolderManager.getChunkHolder(chunkKey);
                if (chunkHolder == null || chunkHolder.getTicketLevel() > minLevel) {
                    return false;
                } else {
                    final ChunkStatus genStatus = chunkHolder.getCurrentGenStatus();
                    if (genStatus != null && genStatus.isOrAfter(toStatus)) {
                        return true;
                    } else {
                        chunkHolder.raisePriority(priority);

                        if (!chunkHolder.upgradeGenTarget(toStatus)) {
                            this.schedule(chunkX, chunkZ, toStatus, chunkHolder, tasks);
                        }
                    }
                }
            } finally {
                this.schedulingLockArea.unlock(schedulingLock);
            }
        } finally {
            this.chunkHolderManager.ticketLockArea.unlock(ticketLock);
        }

        for (int i = 0, len = tasks.size(); i < len; ++i) {
            tasks.get(i).schedule();
        }

        return true;
    }

    // Note: on Moonrise the non-full sync load requires blocking on managedBlock, but this is fine since there is only
    // one main thread. On Folia, it is required that the non-full load can occur completely asynchronously to avoid deadlock
    // between regions
    public ChunkAccess syncLoadNonFull(final int chunkX, final int chunkZ, final ChunkStatus status) {
        if (status == null || status.isOrAfter(ChunkStatus.FULL)) {
            throw new IllegalArgumentException("Status: " + status);
        }

        if (!TickThread.isTickThread()) {
            return this.world.getChunkSource().getChunk(chunkX, chunkZ, status, true);
        }

        ChunkAccess loaded = ((ChunkSystemServerLevel)this.world).moonrise$getSpecificChunkIfLoaded(chunkX, chunkZ, status);
        if (loaded != null) {
            return loaded;
        }

        final Long ticketId = getNextNonFullLoadId();
        final int ticketLevel = getTicketLevel(status);
        this.chunkHolderManager.addTicketAtLevel(NON_FULL_CHUNK_LOAD, chunkX, chunkZ, ticketLevel, ticketId);
        this.chunkHolderManager.processTicketUpdates();

        this.beginChunkLoadForNonFullSync(chunkX, chunkZ, status, Priority.BLOCKING);

        // we could do a simple spinwait here, since we do not need to process tasks while performing this load
        // but we process tasks only because it's a better use of the time spent
        this.world.getChunkSource().mainThreadProcessor.managedBlock(() -> {
            return ((ChunkSystemServerLevel)this.world).moonrise$getSpecificChunkIfLoaded(chunkX, chunkZ, status) != null;
        });

        loaded = ((ChunkSystemServerLevel)this.world).moonrise$getSpecificChunkIfLoaded(chunkX, chunkZ, status);

        this.chunkHolderManager.removeTicketAtLevel(NON_FULL_CHUNK_LOAD, chunkX, chunkZ, ticketLevel, ticketId);

        if (loaded == null) {
            throw new IllegalStateException("Expected chunk to be loaded for status " + status);
        }

        return loaded;
    }

    public void scheduleChunkLoad(final int chunkX, final int chunkZ, final ChunkStatus toStatus, final boolean addTicket,
                                  final Priority priority, final Consumer<ChunkAccess> onComplete) {
        if (!TickThread.isTickThreadFor(this.world, chunkX, chunkZ)) {
            this.scheduleChunkTask(chunkX, chunkZ, () -> {
                ChunkTaskScheduler.this.scheduleChunkLoad(chunkX, chunkZ, toStatus, addTicket, priority, onComplete);
            }, priority);
            return;
        }
        final int accessRadius = getAccessRadius(toStatus);
        if (this.chunkHolderManager.ticketLockArea.isHeldByCurrentThread(chunkX, chunkZ, accessRadius)) {
            throw new IllegalStateException("Cannot schedule chunk load during ticket level update");
        }
        if (this.schedulingLockArea.isHeldByCurrentThread(chunkX, chunkZ, accessRadius)) {
            throw new IllegalStateException("Cannot schedule chunk loading recursively");
        }

        if (toStatus == ChunkStatus.FULL) {
            this.scheduleTickingState(chunkX, chunkZ, FullChunkStatus.FULL, addTicket, priority, (Consumer)onComplete);
            return;
        }

        final int minLevel = ChunkTaskScheduler.getTicketLevel(toStatus);
        final Long chunkReference = addTicket ? getNextChunkLoadId() : null;
        final long chunkKey = CoordinateUtils.getChunkKey(chunkX, chunkZ);

        if (addTicket) {
            this.chunkHolderManager.addTicketAtLevel(CHUNK_LOAD, chunkKey, minLevel, chunkReference);
            this.chunkHolderManager.processTicketUpdates();
        }

        final Consumer<ChunkAccess> loadCallback = (final ChunkAccess chunk) -> {
            try {
                if (onComplete != null) {
                    onComplete.accept(chunk);
                }
            } finally {
                if (addTicket) {
                    ChunkTaskScheduler.this.chunkHolderManager.removeTicketAtLevel(CHUNK_LOAD, chunkKey, minLevel, chunkReference);
                }
            }
        };

        final List<ChunkProgressionTask> tasks = new ArrayList<>();

        final boolean scheduled;
        final ChunkAccess chunk;
        final ReentrantAreaLock.Node ticketLock = this.chunkHolderManager.ticketLockArea.lock(chunkX, chunkZ, accessRadius);
        try {
            final ReentrantAreaLock.Node schedulingLock = this.schedulingLockArea.lock(chunkX, chunkZ, accessRadius);
            try {
                final NewChunkHolder chunkHolder = this.chunkHolderManager.getChunkHolder(chunkKey);
                if (chunkHolder == null || chunkHolder.getTicketLevel() > minLevel) {
                    scheduled = false;
                    chunk = null;
                } else {
                    final ChunkStatus genStatus = chunkHolder.getCurrentGenStatus();
                    if (genStatus != null && genStatus.isOrAfter(toStatus)) {
                        scheduled = false;
                        chunk = chunkHolder.getCurrentChunk();
                    } else {
                        scheduled = true;
                        chunk = null;
                        chunkHolder.raisePriority(priority);

                        if (!chunkHolder.upgradeGenTarget(toStatus)) {
                            this.schedule(chunkX, chunkZ, toStatus, chunkHolder, tasks);
                        }
                        chunkHolder.addStatusConsumer(toStatus, loadCallback);
                    }
                }
            } finally {
                this.schedulingLockArea.unlock(schedulingLock);
            }
        } finally {
            this.chunkHolderManager.ticketLockArea.unlock(ticketLock);
        }

        for (int i = 0, len = tasks.size(); i < len; ++i) {
            tasks.get(i).schedule();
        }

        if (!scheduled) {
            // couldn't schedule
            try {
                loadCallback.accept(chunk);
            } catch (final Throwable thr) {
                LOGGER.error("Failed to process chunk status callback", thr);
            }
        }
    }

    private ChunkProgressionTask createTask(final int chunkX, final int chunkZ, final ChunkAccess chunk,
                                            final NewChunkHolder chunkHolder, final StaticCache2D<GenerationChunkHolder> neighbours,
                                            final ChunkStatus toStatus, final Priority initialPriority) {
        if (toStatus == ChunkStatus.EMPTY) {
            return new ChunkLoadTask(this, this.world, chunkX, chunkZ, chunkHolder, initialPriority);
        }
        if (toStatus == ChunkStatus.LIGHT) {
            return new ChunkLightTask(this, this.world, chunkX, chunkZ, chunk, initialPriority);
        }
        if (toStatus == ChunkStatus.FULL) {
            return new ChunkFullTask(this, this.world, chunkX, chunkZ, chunkHolder, chunk, initialPriority);
        }

        return new ChunkUpgradeGenericStatusTask(this, this.world, chunkX, chunkZ, chunk, neighbours, toStatus, initialPriority);
    }

    ChunkProgressionTask schedule(final int chunkX, final int chunkZ, final ChunkStatus targetStatus, final NewChunkHolder chunkHolder,
                                  final List<ChunkProgressionTask> allTasks) {
        return this.schedule(chunkX, chunkZ, targetStatus, chunkHolder, allTasks, chunkHolder.getEffectivePriority(Priority.NORMAL));
    }

    // rets new task scheduled for the _specified_ chunk
    // note: this must hold the scheduling lock
    // minPriority is only used to pass the priority through to neighbours, as priority calculation has not yet been done
    // schedule will ignore the generation target, so it should be checked by the caller to ensure the target is not regressed!
    private ChunkProgressionTask schedule(final int chunkX, final int chunkZ, final ChunkStatus targetStatus,
                                          final NewChunkHolder chunkHolder, final List<ChunkProgressionTask> allTasks,
                                          final Priority minPriority) {
        if (!this.schedulingLockArea.isHeldByCurrentThread(chunkX, chunkZ, getAccessRadius(targetStatus))) {
            throw new IllegalStateException("Not holding scheduling lock");
        }

        if (chunkHolder.hasGenerationTask()) {
            chunkHolder.upgradeGenTarget(targetStatus);
            return null;
        }

        final Priority requestedPriority = Priority.max(
                minPriority, chunkHolder.getEffectivePriority(Priority.NORMAL)
        );
        final ChunkStatus currentGenStatus = chunkHolder.getCurrentGenStatus();
        final ChunkAccess chunk = chunkHolder.getCurrentChunk();

        if (currentGenStatus == null) {
            // not yet loaded
            final ChunkProgressionTask task = this.createTask(
                chunkX, chunkZ, chunk, chunkHolder, null, ChunkStatus.EMPTY, requestedPriority
            );

            allTasks.add(task);

            final List<NewChunkHolder> chunkHolderNeighbours = new ArrayList<>(1);
            chunkHolderNeighbours.add(chunkHolder);

            chunkHolder.setGenerationTarget(targetStatus);
            chunkHolder.setGenerationTask(task, ChunkStatus.EMPTY, chunkHolderNeighbours);

            return task;
        }

        if (currentGenStatus.isOrAfter(targetStatus)) {
            // nothing to do
            return null;
        }

        // we know for sure now that we want to schedule _something_, so set the target
        chunkHolder.setGenerationTarget(targetStatus);

        final ChunkStatus chunkRealStatus = chunk.getPersistedStatus();
        final ChunkStatus toStatus = ((ChunkSystemChunkStatus)currentGenStatus).moonrise$getNextStatus();
        final ChunkPyramid chunkPyramid = chunkRealStatus.isOrAfter(toStatus) ? ChunkPyramid.LOADING_PYRAMID : ChunkPyramid.GENERATION_PYRAMID;
        final ChunkStep chunkStep = chunkPyramid.getStepTo(toStatus);

        final int neighbourReadRadius = Math.max(
                0,
                chunkStep.getAccumulatedRadiusOf(ChunkStatus.EMPTY)
        );

        boolean unGeneratedNeighbours = false;

        if (neighbourReadRadius > 0) {
            final ChunkMap chunkMap = this.world.getChunkSource().chunkMap;
            for (final long pos : ParallelSearchRadiusIteration.getSearchIteration(neighbourReadRadius)) {
                final int x = CoordinateUtils.getChunkX(pos);
                final int z = CoordinateUtils.getChunkZ(pos);
                final int radius = Math.max(Math.abs(x), Math.abs(z));
                final ChunkStatus requiredNeighbourStatus = ((ChunkSystemChunkStep)(Object)chunkStep).moonrise$getRequiredStatusAtRadius(radius);

                unGeneratedNeighbours |= this.checkNeighbour(
                        chunkX + x, chunkZ + z, requiredNeighbourStatus, chunkHolder, allTasks, requestedPriority
                );
            }
        }

        if (unGeneratedNeighbours) {
            // can't schedule, but neighbour completion will schedule for us when they're ALL done

            // propagate our priority to neighbours
            chunkHolder.recalculateNeighbourPriorities();
            return null;
        }

        // need to gather neighbours

        final List<NewChunkHolder> chunkHolderNeighbours = new ArrayList<>((2 * neighbourReadRadius + 1) * (2 * neighbourReadRadius + 1));
        final StaticCache2D<GenerationChunkHolder> neighbours = StaticCache2D
                .create(chunkX, chunkZ, neighbourReadRadius, (final int nx, final int nz) -> {
                    final NewChunkHolder holder = nx == chunkX && nz == chunkZ ? chunkHolder : this.chunkHolderManager.getChunkHolder(nx, nz);
                    chunkHolderNeighbours.add(holder);

                    return holder.vanillaChunkHolder;
                });

        final ChunkProgressionTask task = this.createTask(
                chunkX, chunkZ, chunk, chunkHolder, neighbours, toStatus,
                chunkHolder.getEffectivePriority(Priority.NORMAL)
        );
        allTasks.add(task);

        chunkHolder.setGenerationTask(task, toStatus, chunkHolderNeighbours);

        return task;
    }

    // rets true if the neighbour is not at the required status, false otherwise
    private boolean checkNeighbour(final int chunkX, final int chunkZ, final ChunkStatus requiredStatus, final NewChunkHolder center,
                                   final List<ChunkProgressionTask> tasks, final Priority minPriority) {
        final NewChunkHolder chunkHolder = this.chunkHolderManager.getChunkHolder(chunkX, chunkZ);

        if (chunkHolder == null) {
            throw new IllegalStateException("Missing chunkholder when required");
        }

        final ChunkStatus holderStatus = chunkHolder.getCurrentGenStatus();
        if (holderStatus != null && holderStatus.isOrAfter(requiredStatus)) {
            return false;
        }

        if (chunkHolder.hasFailedGeneration()) {
            return true;
        }

        center.addGenerationBlockingNeighbour(chunkHolder);
        chunkHolder.addWaitingNeighbour(center, requiredStatus);

        if (chunkHolder.upgradeGenTarget(requiredStatus)) {
            return true;
        }

        // not at status required, so we need to schedule its generation
        this.schedule(
            chunkX, chunkZ, requiredStatus, chunkHolder, tasks, minPriority
        );

        return true;
    }

    /**
     * @deprecated Chunk tasks must be tied to coordinates in the future
     */
    @Deprecated
    public PrioritisedExecutor.PrioritisedTask scheduleChunkTask(final Runnable run) {
        return this.scheduleChunkTask(run, Priority.NORMAL);
    }

    /**
     * @deprecated Chunk tasks must be tied to coordinates in the future
     */
    @Deprecated
    public PrioritisedExecutor.PrioritisedTask scheduleChunkTask(final Runnable run, final Priority priority) {
        return this.mainThreadExecutor.queueTask(run, priority);
    }

    public PrioritisedExecutor.PrioritisedTask createChunkTask(final int chunkX, final int chunkZ, final Runnable run) {
        return this.createChunkTask(chunkX, chunkZ, run, Priority.NORMAL);
    }

    public PrioritisedExecutor.PrioritisedTask createChunkTask(final int chunkX, final int chunkZ, final Runnable run,
                                                               final Priority priority) {
        return this.mainThreadExecutor.createTask(run, priority);
    }

    public PrioritisedExecutor.PrioritisedTask scheduleChunkTask(final int chunkX, final int chunkZ, final Runnable run) {
        return this.scheduleChunkTask(chunkX, chunkZ, run, Priority.NORMAL);
    }

    public PrioritisedExecutor.PrioritisedTask scheduleChunkTask(final int chunkX, final int chunkZ, final Runnable run,
                                                                 final Priority priority) {
        return this.mainThreadExecutor.queueTask(run, priority);
    }

    public boolean halt(final boolean sync, final long maxWaitNS) {
        this.radiusAwareGenExecutor.halt();
        this.parallelGenExecutor.halt();
        this.loadExecutor.halt();
        if (sync) {
            final long time = System.nanoTime();
            for (long failures = 9L;; failures = ConcurrentUtil.linearLongBackoff(failures, 500_000L, 50_000_000L)) {
                if (
                        !this.radiusAwareGenExecutor.isActive() &&
                        !this.parallelGenExecutor.isActive() &&
                        !this.loadExecutor.isActive()
                ) {
                    return true;
                }
                if ((System.nanoTime() - time) >= maxWaitNS) {
                    return false;
                }
            }
        }

        return true;
    }

    public boolean haltIO(final boolean sync, final long maxWaitNS) {
        this.ioExecutor.halt();
        this.saveExecutor.halt();
        this.compressionExecutor.halt();
        if (sync) {
            final long time = System.nanoTime();
            for (long failures = 9L;; failures = ConcurrentUtil.linearLongBackoff(failures, 500_000L, 50_000_000L)) {
                if (
                        !this.ioExecutor.isActive() &&
                        !this.saveExecutor.isActive() &&
                        !this.compressionExecutor.isActive()
                ) {
                    return true;
                }
                if ((System.nanoTime() - time) >= maxWaitNS) {
                    return false;
                }
            }
        }

        return true;
    }

    public static final ArrayDeque<ChunkInfo> WAITING_CHUNKS = new ArrayDeque<>(); // stack

    public static final class ChunkInfo {

        public final int chunkX;
        public final int chunkZ;
        public final ServerLevel world;

        public ChunkInfo(final int chunkX, final int chunkZ, final ServerLevel world) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.world = world;
        }

        public JsonObject toJson() {
            final JsonObject ret = new JsonObject();

            ret.addProperty("chunk-x", Integer.valueOf(this.chunkX));
            ret.addProperty("chunk-z", Integer.valueOf(this.chunkZ));
            ret.addProperty("world-name", WorldUtil.getWorldName(this.world));

            return ret;
        }

        @Override
        public String toString() {
            return "[( " + this.chunkX + "," + this.chunkZ + ") in '" + WorldUtil.getWorldName(this.world) + "']";
        }
    }

    public static void pushChunkWait(final ServerLevel world, final int chunkX, final int chunkZ) {
        synchronized (WAITING_CHUNKS) {
            WAITING_CHUNKS.push(new ChunkInfo(chunkX, chunkZ, world));
        }
    }

    public static void popChunkWait() {
        synchronized (WAITING_CHUNKS) {
            WAITING_CHUNKS.pop();
        }
    }

    public static ChunkInfo[] getChunkInfos() {
        synchronized (WAITING_CHUNKS) {
            return WAITING_CHUNKS.toArray(new ChunkInfo[0]);
        }
    }

    private static JsonObject debugPlayer(final ServerPlayer player) {
        final Level world = player.level();

        final JsonObject ret = new JsonObject();

        ret.addProperty("name", player.getScoreboardName());
        ret.addProperty("uuid", player.getUUID().toString());
        ret.addProperty("real", ((ChunkSystemServerPlayer)player).moonrise$isRealPlayer());

        ret.addProperty("world-name", WorldUtil.getWorldName(world));

        final Vec3 pos = player.position();

        ret.addProperty("x", pos.x);
        ret.addProperty("y", pos.y);
        ret.addProperty("z", pos.z);

        final Entity.RemovalReason removalReason = player.getRemovalReason();

        ret.addProperty("removal-reason", removalReason == null ? "null" : removalReason.name());

        ret.add("view-distances", ((ChunkSystemServerPlayer)player).moonrise$getViewDistanceHolder().toJson());

        return ret;
    }

    public JsonObject getDebugJson() {
        final JsonObject ret = new JsonObject();

        ret.addProperty("lock_shift", Integer.valueOf(this.getChunkSystemLockShift()));
        ret.addProperty("ticket_shift", Integer.valueOf(ThreadedTicketLevelPropagator.SECTION_SHIFT));
        ret.addProperty("region_shift", Integer.valueOf(((ChunkSystemServerLevel)this.world).moonrise$getRegionChunkShift()));

        ret.addProperty("name", WorldUtil.getWorldName(this.world));
        ret.addProperty("view-distance", ((ChunkSystemServerLevel)this.world).moonrise$getPlayerChunkLoader().getAPIViewDistance());
        ret.addProperty("tick-distance", ((ChunkSystemServerLevel)this.world).moonrise$getPlayerChunkLoader().getAPITickDistance());
        ret.addProperty("send-distance", ((ChunkSystemServerLevel)this.world).moonrise$getPlayerChunkLoader().getAPISendViewDistance());

        final JsonArray players = new JsonArray();
        ret.add("players", players);

        for (final ServerPlayer player : this.world.players()) {
            players.add(debugPlayer(player));
        }

        ret.add("chunk-holder-manager", this.chunkHolderManager.getDebugJson());

        return ret;
    }

    public static JsonObject debugAllWorlds(final MinecraftServer server) {
        final JsonObject ret = new JsonObject();

        ret.addProperty("data-version", 2);

        final JsonArray allPlayers = new JsonArray();
        ret.add("all-players", allPlayers);

        for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
            allPlayers.add(debugPlayer(player));
        }

        final JsonArray chunkWaitInfos = new JsonArray();
        ret.add("chunk-wait-infos", chunkWaitInfos);

        for (final ChunkTaskScheduler.ChunkInfo info : getChunkInfos()) {
            chunkWaitInfos.add(info.toJson());
        }

        final JsonArray worlds = new JsonArray();
        ret.add("worlds", worlds);

        for (final ServerLevel world : server.getAllLevels()) {
            worlds.add(((ChunkSystemServerLevel)world).moonrise$getChunkTaskScheduler().getDebugJson());
        }

        return ret;
    }

    public static File getChunkDebugFile() {
        return new File(
                new File(new File("."), "debug"),
                "chunks-" + DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss").format(LocalDateTime.now()) + ".txt"
        );
    }

    public static void dumpAllChunkLoadInfo(final MinecraftServer server, final boolean writeDebugInfo) {
        final ChunkInfo[] chunkInfos = getChunkInfos();
        if (chunkInfos.length > 0) {
            LOGGER.error("Chunk wait task info below: ");
            for (final ChunkInfo chunkInfo : chunkInfos) {
                final NewChunkHolder holder = ((ChunkSystemServerLevel)chunkInfo.world).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkInfo.chunkX, chunkInfo.chunkZ);
                LOGGER.error("Chunk wait: " + chunkInfo);
                LOGGER.error("Chunk holder: " + holder);
            }

            if (writeDebugInfo) {
                final File file = getChunkDebugFile();
                LOGGER.error("Writing chunk information dump to " + file);
                try {
                    JsonUtil.writeJson(ChunkTaskScheduler.debugAllWorlds(server), file);
                    LOGGER.error("Successfully written chunk information!");
                } catch (final Throwable thr) {
                    LOGGER.error("Failed to dump chunk information to file " + file.toString(), thr);
                }
            }
        }
    }
}
