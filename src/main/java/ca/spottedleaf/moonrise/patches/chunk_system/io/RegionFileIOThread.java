package ca.spottedleaf.moonrise.patches.chunk_system.io;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.concurrentutil.executor.Cancellable;
import ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.executor.standard.PrioritisedQueueExecutorThread;
import ca.spottedleaf.concurrentutil.executor.standard.PrioritisedThreadedTaskQueue;
import ca.spottedleaf.concurrentutil.function.BiLong1Function;
import ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Prioritised RegionFile I/O executor, responsible for all RegionFile access.
 * <p>
 *     All functions provided are MT-Safe, however certain ordering constraints are recommended:
 *     <li>
 *         Chunk saves may not occur for unloaded chunks.
 *     </li>
 *     <li>
 *         Tasks must be scheduled on the chunk scheduler thread.
 *     </li>
 *     By following these constraints, no chunk data loss should occur with the exception of underlying I/O problems.
 * </p>
 */
public final class RegionFileIOThread extends PrioritisedQueueExecutorThread {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegionFileIOThread.class);

    /**
     * The kinds of region files controlled by the region file thread. Add more when needed, and ensure
     * getControllerFor is updated.
     */
    public static enum RegionFileType {
        CHUNK_DATA,
        POI_DATA,
        ENTITY_DATA;
    }

    private static final RegionFileType[] CACHED_REGIONFILE_TYPES = RegionFileType.values();

    public static ChunkDataController getControllerFor(final ServerLevel world, final RegionFileType type) {
        switch (type) {
            case CHUNK_DATA:
                return ((ChunkSystemServerLevel)world).moonrise$getChunkDataController();
            case POI_DATA:
                return ((ChunkSystemServerLevel)world).moonrise$getPoiChunkDataController();
            case ENTITY_DATA:
                return ((ChunkSystemServerLevel)world).moonrise$getEntityChunkDataController();
            default:
                throw new IllegalStateException("Unknown controller type " + type);
        }
    }

    /**
     * Collects regionfile data for a certain chunk.
     */
    public static final class RegionFileData {

        private final boolean[] hasResult = new boolean[CACHED_REGIONFILE_TYPES.length];
        private final CompoundTag[] data = new CompoundTag[CACHED_REGIONFILE_TYPES.length];
        private final Throwable[] throwables = new Throwable[CACHED_REGIONFILE_TYPES.length];

        /**
         * Sets the result associated with the specified regionfile type. Note that
         * results can only be set once per regionfile type.
         *
         * @param type The regionfile type.
         * @param data The result to set.
         */
        public void setData(final RegionFileType type, final CompoundTag data) {
            final int index = type.ordinal();

            if (this.hasResult[index]) {
                throw new IllegalArgumentException("Result already exists for type " + type);
            }
            this.hasResult[index] = true;
            this.data[index] = data;
        }

        /**
         * Sets the result associated with the specified regionfile type. Note that
         * results can only be set once per regionfile type.
         *
         * @param type The regionfile type.
         * @param throwable The result to set.
         */
        public void setThrowable(final RegionFileType type, final Throwable throwable) {
            final int index = type.ordinal();

            if (this.hasResult[index]) {
                throw new IllegalArgumentException("Result already exists for type " + type);
            }
            this.hasResult[index] = true;
            this.throwables[index] = throwable;
        }

        /**
         * Returns whether there is a result for the specified regionfile type.
         *
         * @param type Specified regionfile type.
         *
         * @return Whether a result exists for {@code type}.
         */
        public boolean hasResult(final RegionFileType type) {
            return this.hasResult[type.ordinal()];
        }

        /**
         * Returns the data result for the regionfile type.
         *
         * @param type Specified regionfile type.
         *
         * @throws IllegalArgumentException If the result has not been set for {@code type}.
         * @return The data result for the specified type. If the result is a {@code Throwable},
         * then returns {@code null}.
         */
        public CompoundTag getData(final RegionFileType type) {
            final int index = type.ordinal();

            if (!this.hasResult[index]) {
                throw new IllegalArgumentException("Result does not exist for type " + type);
            }

            return this.data[index];
        }

        /**
         * Returns the throwable result for the regionfile type.
         *
         * @param type Specified regionfile type.
         *
         * @throws IllegalArgumentException If the result has not been set for {@code type}.
         * @return The throwable result for the specified type. If the result is an {@code CompoundTag},
         * then returns {@code null}.
         */
        public Throwable getThrowable(final RegionFileType type) {
            final int index = type.ordinal();

            if (!this.hasResult[index]) {
                throw new IllegalArgumentException("Result does not exist for type " + type);
            }

            return this.throwables[index];
        }
    }

    private static final Object INIT_LOCK = new Object();

    static RegionFileIOThread[] threads;

    /* needs to be consistent given a set of parameters */
    static RegionFileIOThread selectThread(final ServerLevel world, final int chunkX, final int chunkZ, final RegionFileType type) {
        if (threads == null) {
            throw new IllegalStateException("Threads not initialised");
        }

        final int regionX = chunkX >> 5;
        final int regionZ = chunkZ >> 5;
        final int typeOffset = type.ordinal();

        return threads[(System.identityHashCode(world) + regionX + regionZ + typeOffset) % threads.length];
    }

    /**
     * Shuts down the I/O executor(s). Watis for all tasks to complete if specified.
     * Tasks queued during this call might not be accepted, and tasks queued after will not be accepted.
     *
     * @param wait Whether to wait until all tasks have completed.
     */
    public static void close(final boolean wait) {
        for (int i = 0, len = threads.length; i < len; ++i) {
            threads[i].close(false, true);
        }
        if (wait) {
            RegionFileIOThread.flush();
        }
    }

    public static long[] getExecutedTasks() {
        final long[] ret = new long[threads.length];
        for (int i = 0, len = threads.length; i < len; ++i) {
            ret[i] = threads[i].getTotalTasksExecuted();
        }

        return ret;
    }

    public static long[] getTasksScheduled() {
        final long[] ret = new long[threads.length];
        for (int i = 0, len = threads.length; i < len; ++i) {
            ret[i] = threads[i].getTotalTasksScheduled();
        }
        return ret;
    }

    public static void flush() {
        for (int i = 0, len = threads.length; i < len; ++i) {
            threads[i].waitUntilAllExecuted();
        }
    }

    public static void flushRegionStorages(final ServerLevel world) throws IOException {
        for (final RegionFileType type : CACHED_REGIONFILE_TYPES) {
            getControllerFor(world, type).getCache().flush();
        }
    }

    public static void partialFlush(final int totalTasksRemaining) {
        long failures = 1L; // start out at 0.25ms

        for (;;) {
            final long[] executed = getExecutedTasks();
            final long[] scheduled = getTasksScheduled();

            long sum = 0;
            for (int i = 0; i < executed.length; ++i) {
                sum += scheduled[i] - executed[i];
            }

            if (sum <= totalTasksRemaining) {
                break;
            }

            failures = ConcurrentUtil.linearLongBackoff(failures, 250_000L, 5_000_000L); // 500us, 5ms
        }
    }

    /**
     * Inits the executor with the specified number of threads.
     *
     * @param threads Specified number of threads.
     */
    public static void init(final int threads) {
        synchronized (INIT_LOCK) {
            if (RegionFileIOThread.threads != null) {
                throw new IllegalStateException("Already initialised threads");
            }

            RegionFileIOThread.threads = new RegionFileIOThread[threads];

            for (int i = 0; i < threads; ++i) {
                RegionFileIOThread.threads[i] = new RegionFileIOThread(i);
                RegionFileIOThread.threads[i].start();
            }
        }
    }

    public static void deinit() {
        if (false) {
            // TODO does this cause issues with mods? how to implement
            close(true);
            synchronized (INIT_LOCK) {
                RegionFileIOThread.threads = null;
            }
        } else { RegionFileIOThread.flush(); }
    }

    private RegionFileIOThread(final int threadNumber) {
        super(new PrioritisedThreadedTaskQueue(), (int)(1.0e6)); // 1.0ms spinwait time
        this.setName("RegionFile I/O Thread #" + threadNumber);
        this.setPriority(Thread.NORM_PRIORITY - 2); // we keep priority close to normal because threads can wait on us
        this.setUncaughtExceptionHandler((final Thread thread, final Throwable thr) -> {
            LOGGER.error("Uncaught exception thrown from I/O thread, report this! Thread: " + thread.getName(), thr);
        });
    }

    /**
     * Returns whether the current thread is a regionfile I/O executor.
     * @return Whether the current thread is a regionfile I/O executor.
     */
    public static boolean isRegionFileThread() {
        return Thread.currentThread() instanceof RegionFileIOThread;
    }

    /**
     * Returns the priority associated with blocking I/O based on the current thread. The goal is to avoid
     * dumb plugins from taking away priority from threads we consider crucial.
     * @return The priroity to use with blocking I/O on the current thread.
     */
    public static Priority getIOBlockingPriorityForCurrentThread() {
        if (TickThread.isTickThread()) {
            return Priority.BLOCKING;
        }
        return Priority.HIGHEST;
    }

    /**
     * Returns the current {@code CompoundTag} pending for write for the specified chunk & regionfile type.
     * Note that this does not copy the result, so do not modify the result returned.
     *
     * @param world Specified world.
     * @param chunkX Specified chunk x.
     * @param chunkZ Specified chunk z.
     * @param type Specified regionfile type.
     *
     * @return The compound tag associated for the specified chunk. {@code null} if no write was pending, or if {@code null} is the write pending.
     */
    public static CompoundTag getPendingWrite(final ServerLevel world, final int chunkX, final int chunkZ, final RegionFileType type) {
        final RegionFileIOThread thread = RegionFileIOThread.selectThread(world, chunkX, chunkZ, type);
        return thread.getPendingWriteInternal(world, chunkX, chunkZ, type);
    }

    CompoundTag getPendingWriteInternal(final ServerLevel world, final int chunkX, final int chunkZ, final RegionFileType type) {
        final ChunkDataController taskController = getControllerFor(world, type);
        final ChunkDataTask task = taskController.tasks.get(CoordinateUtils.getChunkKey(chunkX, chunkZ));

        if (task == null) {
            return null;
        }

        final CompoundTag ret = task.inProgressWrite;

        return ret == ChunkDataTask.NOTHING_TO_WRITE ? null : ret;
    }

    /**
     * Returns the priority for the specified regionfile type for the specified chunk.
     * @param world Specified world.
     * @param chunkX Specified chunk x.
     * @param chunkZ Specified chunk z.
     * @param type Specified regionfile type.
     * @return The priority for the chunk
     */
    public static Priority getPriority(final ServerLevel world, final int chunkX, final int chunkZ, final RegionFileType type) {
        final RegionFileIOThread thread = RegionFileIOThread.selectThread(world, chunkX, chunkZ, type);
        return thread.getPriorityInternal(world, chunkX, chunkZ, type);
    }

    Priority getPriorityInternal(final ServerLevel world, final int chunkX, final int chunkZ, final RegionFileType type) {
        final ChunkDataController taskController = getControllerFor(world, type);
        final ChunkDataTask task = taskController.tasks.get(CoordinateUtils.getChunkKey(chunkX, chunkZ));

        if (task == null) {
            return Priority.COMPLETING;
        }

        return task.prioritisedTask.getPriority();
    }

    /**
     * Sets the priority for all regionfile types for the specified chunk. Note that great care should
     * be taken using this method, as there can be multiple tasks tied to the same chunk that want different
     * priorities.
     *
     * @param world Specified world.
     * @param chunkX Specified chunk x.
     * @param chunkZ Specified chunk z.
     * @param priority New priority.
     *
     * @see #raisePriority(ServerLevel, int, int, Priority)
     * @see #raisePriority(ServerLevel, int, int, RegionFileType, Priority)
     * @see #lowerPriority(ServerLevel, int, int, Priority)
     * @see #lowerPriority(ServerLevel, int, int, RegionFileType, Priority)
     */
    public static void setPriority(final ServerLevel world, final int chunkX, final int chunkZ,
                                   final Priority priority) {
        for (final RegionFileType type : CACHED_REGIONFILE_TYPES) {
            RegionFileIOThread.setPriority(world, chunkX, chunkZ, type, priority);
        }
    }

    /**
     * Sets the priority for the specified regionfile type for the specified chunk. Note that great care should
     * be taken using this method, as there can be multiple tasks tied to the same chunk that want different
     * priorities.
     *
     * @param world Specified world.
     * @param chunkX Specified chunk x.
     * @param chunkZ Specified chunk z.
     * @param type Specified regionfile type.
     * @param priority New priority.
     *
     * @see #raisePriority(ServerLevel, int, int, Priority)
     * @see #raisePriority(ServerLevel, int, int, RegionFileType, Priority)
     * @see #lowerPriority(ServerLevel, int, int, Priority)
     * @see #lowerPriority(ServerLevel, int, int, RegionFileType, Priority)
     */
    public static void setPriority(final ServerLevel world, final int chunkX, final int chunkZ, final RegionFileType type,
                                   final Priority priority) {
        final RegionFileIOThread thread = RegionFileIOThread.selectThread(world, chunkX, chunkZ, type);
        thread.setPriorityInternal(world, chunkX, chunkZ, type, priority);
    }

    void setPriorityInternal(final ServerLevel world, final int chunkX, final int chunkZ, final RegionFileType type,
                             final Priority priority) {
        final ChunkDataController taskController = getControllerFor(world, type);
        final ChunkDataTask task = taskController.tasks.get(CoordinateUtils.getChunkKey(chunkX, chunkZ));

        if (task != null) {
            task.prioritisedTask.setPriority(priority);
        }
    }

    /**
     * Raises the priority for all regionfile types for the specified chunk.
     *
     * @param world Specified world.
     * @param chunkX Specified chunk x.
     * @param chunkZ Specified chunk z.
     * @param priority New priority.
     *
     * @see #setPriority(ServerLevel, int, int, Priority)
     * @see #setPriority(ServerLevel, int, int, RegionFileType, Priority)
     * @see #lowerPriority(ServerLevel, int, int, Priority)
     * @see #lowerPriority(ServerLevel, int, int, RegionFileType, Priority)
     */
    public static void raisePriority(final ServerLevel world, final int chunkX, final int chunkZ,
                                     final Priority priority) {
        for (final RegionFileType type : CACHED_REGIONFILE_TYPES) {
            RegionFileIOThread.raisePriority(world, chunkX, chunkZ, type, priority);
        }
    }

    /**
     * Raises the priority for the specified regionfile type for the specified chunk.
     *
     * @param world Specified world.
     * @param chunkX Specified chunk x.
     * @param chunkZ Specified chunk z.
     * @param type Specified regionfile type.
     * @param priority New priority.
     *
     * @see #setPriority(ServerLevel, int, int, Priority)
     * @see #setPriority(ServerLevel, int, int, RegionFileType, Priority)
     * @see #lowerPriority(ServerLevel, int, int, Priority)
     * @see #lowerPriority(ServerLevel, int, int, RegionFileType, Priority)
     */
    public static void raisePriority(final ServerLevel world, final int chunkX, final int chunkZ, final RegionFileType type,
                                     final Priority priority) {
        final RegionFileIOThread thread = RegionFileIOThread.selectThread(world, chunkX, chunkZ, type);
        thread.raisePriorityInternal(world, chunkX, chunkZ, type, priority);
    }

    void raisePriorityInternal(final ServerLevel world, final int chunkX, final int chunkZ, final RegionFileType type,
                               final Priority priority) {
        final ChunkDataController taskController = getControllerFor(world, type);
        final ChunkDataTask task = taskController.tasks.get(CoordinateUtils.getChunkKey(chunkX, chunkZ));

        if (task != null) {
            task.prioritisedTask.raisePriority(priority);
        }
    }

    /**
     * Lowers the priority for all regionfile types for the specified chunk.
     *
     * @param world Specified world.
     * @param chunkX Specified chunk x.
     * @param chunkZ Specified chunk z.
     * @param priority New priority.
     *
     * @see #raisePriority(ServerLevel, int, int, Priority)
     * @see #raisePriority(ServerLevel, int, int, RegionFileType, Priority)
     * @see #setPriority(ServerLevel, int, int, Priority)
     * @see #setPriority(ServerLevel, int, int, RegionFileType, Priority)
     */
    public static void lowerPriority(final ServerLevel world, final int chunkX, final int chunkZ,
                                     final Priority priority) {
        for (final RegionFileType type : CACHED_REGIONFILE_TYPES) {
            RegionFileIOThread.lowerPriority(world, chunkX, chunkZ, type, priority);
        }
    }

    /**
     * Lowers the priority for the specified regionfile type for the specified chunk.
     *
     * @param world Specified world.
     * @param chunkX Specified chunk x.
     * @param chunkZ Specified chunk z.
     * @param type Specified regionfile type.
     * @param priority New priority.
     *
     * @see #raisePriority(ServerLevel, int, int, Priority)
     * @see #raisePriority(ServerLevel, int, int, RegionFileType, Priority)
     * @see #setPriority(ServerLevel, int, int, Priority)
     * @see #setPriority(ServerLevel, int, int, RegionFileType, Priority)
     */
    public static void lowerPriority(final ServerLevel world, final int chunkX, final int chunkZ, final RegionFileType type,
                                     final Priority priority) {
        final RegionFileIOThread thread = RegionFileIOThread.selectThread(world, chunkX, chunkZ, type);
        thread.lowerPriorityInternal(world, chunkX, chunkZ, type, priority);
    }

    void lowerPriorityInternal(final ServerLevel world, final int chunkX, final int chunkZ, final RegionFileType type,
                               final Priority priority) {
        final ChunkDataController taskController = getControllerFor(world, type);
        final ChunkDataTask task = taskController.tasks.get(CoordinateUtils.getChunkKey(chunkX, chunkZ));

        if (task != null) {
            task.prioritisedTask.lowerPriority(priority);
        }
    }

    /**
     * Schedules the chunk data to be written asynchronously.
     * <p>
     *     Impl notes:
     * </p>
     * <li>
     *     This function presumes a chunk load for the coordinates is not called during this function (anytime after is OK). This means
     *     saves must be scheduled before a chunk is unloaded.
     * </li>
     * <li>
     *     Writes may be called concurrently, although only the "later" write will go through.
     * </li>
     *
     * @param world Chunk's world
     * @param chunkX Chunk's x coordinate
     * @param chunkZ Chunk's z coordinate
     * @param data Chunk's data
     * @param type The regionfile type to write to.
     *
     * @throws IllegalStateException If the file io thread has shutdown.
     */
    public static void scheduleSave(final ServerLevel world, final int chunkX, final int chunkZ, final CompoundTag data,
                                    final RegionFileType type) {
        RegionFileIOThread.scheduleSave(world, chunkX, chunkZ, data, type, Priority.NORMAL);
    }

    /**
     * Schedules the chunk data to be written asynchronously.
     * <p>
     *     Impl notes:
     * </p>
     * <li>
     *     This function presumes a chunk load for the coordinates is not called during this function (anytime after is OK). This means
     *     saves must be scheduled before a chunk is unloaded.
     * </li>
     * <li>
     *     Writes may be called concurrently, although only the "later" write will go through.
     * </li>
     *
     * @param world Chunk's world
     * @param chunkX Chunk's x coordinate
     * @param chunkZ Chunk's z coordinate
     * @param data Chunk's data
     * @param type The regionfile type to write to.
     * @param priority The minimum priority to schedule at.
     *
     * @throws IllegalStateException If the file io thread has shutdown.
     */
    public static void scheduleSave(final ServerLevel world, final int chunkX, final int chunkZ, final CompoundTag data,
                                    final RegionFileType type, final Priority priority) {
        final RegionFileIOThread thread = RegionFileIOThread.selectThread(world, chunkX, chunkZ, type);
        thread.scheduleSaveInternal(world, chunkX, chunkZ, data, type, priority);
    }

    void scheduleSaveInternal(final ServerLevel world, final int chunkX, final int chunkZ, final CompoundTag data,
                              final RegionFileType type, final Priority priority) {
        final ChunkDataController taskController = getControllerFor(world, type);

        final boolean[] created = new boolean[1];
        final long key = CoordinateUtils.getChunkKey(chunkX, chunkZ);
        final ChunkDataTask task = taskController.tasks.compute(key, (final long keyInMap, final ChunkDataTask taskRunning) -> {
            if (taskRunning == null || taskRunning.failedWrite) {
                // no task is scheduled or the previous write failed - meaning we need to overwrite it

                // create task
                final ChunkDataTask newTask = new ChunkDataTask(world, chunkX, chunkZ, taskController, RegionFileIOThread.this, priority);
                newTask.inProgressWrite = data;
                created[0] = true;

                return newTask;
            }

            taskRunning.inProgressWrite = data;

            return taskRunning;
        });

        if (created[0]) {
            task.prioritisedTask.queue();
        } else {
            task.prioritisedTask.raisePriority(priority);
        }
    }

    /**
     * Schedules a load to be executed asynchronously. This task will load all regionfile types, and then call
     * {@code onComplete}. This is a bulk load operation, see {@link #loadDataAsync(ServerLevel, int, int, RegionFileType, BiConsumer, boolean)}
     * for single load.
     * <p>
     *     Impl notes:
     * </p>
     * <li>
     *     The {@code onComplete} parameter may be completed during the execution of this function synchronously or it may
     *     be completed asynchronously on this file io thread. Interacting with the file IO thread in the completion of
     *     data is undefined behaviour, and can cause deadlock.
     * </li>
     *
     * @param world Chunk's world
     * @param chunkX Chunk's x coordinate
     * @param chunkZ Chunk's z coordinate
     * @param onComplete Consumer to execute once this task has completed
     * @param intendingToBlock Whether the caller is intending to block on completion. This only affects the cost
     *                         of this call.
     *
     * @return The {@link Cancellable} for this chunk load. Cancelling it will not affect other loads for the same chunk data.
     *
     * @see #loadDataAsync(ServerLevel, int, int, RegionFileType, BiConsumer, boolean)
     * @see #loadDataAsync(ServerLevel, int, int, RegionFileType, BiConsumer, boolean, Priority)
     * @see #loadChunkData(ServerLevel, int, int, Consumer, boolean, RegionFileType...)
     * @see #loadChunkData(ServerLevel, int, int, Consumer, boolean, Priority, RegionFileType...)
     */
    public static Cancellable loadAllChunkData(final ServerLevel world, final int chunkX, final int chunkZ,
                                               final Consumer<RegionFileData> onComplete, final boolean intendingToBlock) {
        return RegionFileIOThread.loadAllChunkData(world, chunkX, chunkZ, onComplete, intendingToBlock, Priority.NORMAL);
    }

    /**
     * Schedules a load to be executed asynchronously. This task will load all regionfile types, and then call
     * {@code onComplete}. This is a bulk load operation, see {@link #loadDataAsync(ServerLevel, int, int, RegionFileType, BiConsumer, boolean, Priority)}
     * for single load.
     * <p>
     *     Impl notes:
     * </p>
     * <li>
     *     The {@code onComplete} parameter may be completed during the execution of this function synchronously or it may
     *     be completed asynchronously on this file io thread. Interacting with the file IO thread in the completion of
     *     data is undefined behaviour, and can cause deadlock.
     * </li>
     *
     * @param world Chunk's world
     * @param chunkX Chunk's x coordinate
     * @param chunkZ Chunk's z coordinate
     * @param onComplete Consumer to execute once this task has completed
     * @param intendingToBlock Whether the caller is intending to block on completion. This only affects the cost
     *                         of this call.
     * @param priority The minimum priority to load the data at.
     *
     * @return The {@link Cancellable} for this chunk load. Cancelling it will not affect other loads for the same chunk data.
     *
     * @see #loadDataAsync(ServerLevel, int, int, RegionFileType, BiConsumer, boolean)
     * @see #loadDataAsync(ServerLevel, int, int, RegionFileType, BiConsumer, boolean, Priority)
     * @see #loadChunkData(ServerLevel, int, int, Consumer, boolean, RegionFileType...)
     * @see #loadChunkData(ServerLevel, int, int, Consumer, boolean, Priority, RegionFileType...)
     */
    public static Cancellable loadAllChunkData(final ServerLevel world, final int chunkX, final int chunkZ,
                                               final Consumer<RegionFileData> onComplete, final boolean intendingToBlock,
                                               final Priority priority) {
        return RegionFileIOThread.loadChunkData(world, chunkX, chunkZ, onComplete, intendingToBlock, priority, CACHED_REGIONFILE_TYPES);
    }

    /**
     * Schedules a load to be executed asynchronously. This task will load data for the specified regionfile type(s), and
     * then call {@code onComplete}. This is a bulk load operation, see {@link #loadDataAsync(ServerLevel, int, int, RegionFileType, BiConsumer, boolean)}
     * for single load.
     * <p>
     *     Impl notes:
     * </p>
     * <li>
     *     The {@code onComplete} parameter may be completed during the execution of this function synchronously or it may
     *     be completed asynchronously on this file io thread. Interacting with the file IO thread in the completion of
     *     data is undefined behaviour, and can cause deadlock.
     * </li>
     *
     * @param world Chunk's world
     * @param chunkX Chunk's x coordinate
     * @param chunkZ Chunk's z coordinate
     * @param onComplete Consumer to execute once this task has completed
     * @param intendingToBlock Whether the caller is intending to block on completion. This only affects the cost
     *                         of this call.
     * @param types The regionfile type(s) to load.
     *
     * @return The {@link Cancellable} for this chunk load. Cancelling it will not affect other loads for the same chunk data.
     *
     * @see #loadDataAsync(ServerLevel, int, int, RegionFileType, BiConsumer, boolean)
     * @see #loadDataAsync(ServerLevel, int, int, RegionFileType, BiConsumer, boolean, Priority)
     * @see #loadAllChunkData(ServerLevel, int, int, Consumer, boolean)
     * @see #loadAllChunkData(ServerLevel, int, int, Consumer, boolean, Priority)
     */
    public static Cancellable loadChunkData(final ServerLevel world, final int chunkX, final int chunkZ,
                                            final Consumer<RegionFileData> onComplete, final boolean intendingToBlock,
                                            final RegionFileType... types) {
        return RegionFileIOThread.loadChunkData(world, chunkX, chunkZ, onComplete, intendingToBlock, Priority.NORMAL, types);
    }

    /**
     * Schedules a load to be executed asynchronously. This task will load data for the specified regionfile type(s), and
     * then call {@code onComplete}. This is a bulk load operation, see {@link #loadDataAsync(ServerLevel, int, int, RegionFileType, BiConsumer, boolean, Priority)}
     * for single load.
     * <p>
     *     Impl notes:
     * </p>
     * <li>
     *     The {@code onComplete} parameter may be completed during the execution of this function synchronously or it may
     *     be completed asynchronously on this file io thread. Interacting with the file IO thread in the completion of
     *     data is undefined behaviour, and can cause deadlock.
     * </li>
     *
     * @param world Chunk's world
     * @param chunkX Chunk's x coordinate
     * @param chunkZ Chunk's z coordinate
     * @param onComplete Consumer to execute once this task has completed
     * @param intendingToBlock Whether the caller is intending to block on completion. This only affects the cost
     *                         of this call.
     * @param types The regionfile type(s) to load.
     * @param priority The minimum priority to load the data at.
     *
     * @return The {@link Cancellable} for this chunk load. Cancelling it will not affect other loads for the same chunk data.
     *
     * @see #loadDataAsync(ServerLevel, int, int, RegionFileType, BiConsumer, boolean)
     * @see #loadDataAsync(ServerLevel, int, int, RegionFileType, BiConsumer, boolean, Priority)
     * @see #loadAllChunkData(ServerLevel, int, int, Consumer, boolean)
     * @see #loadAllChunkData(ServerLevel, int, int, Consumer, boolean, Priority)
     */
    public static Cancellable loadChunkData(final ServerLevel world, final int chunkX, final int chunkZ,
                                            final Consumer<RegionFileData> onComplete, final boolean intendingToBlock,
                                            final Priority priority, final RegionFileType... types) {
        if (types == null) {
            throw new NullPointerException("Types cannot be null");
        }
        if (types.length == 0) {
            throw new IllegalArgumentException("Types cannot be empty");
        }

        final RegionFileData ret = new RegionFileData();

        final Cancellable[] reads = new CancellableRead[types.length];
        final AtomicInteger completions = new AtomicInteger();
        final int expectedCompletions = types.length;

        for (int i = 0; i < expectedCompletions; ++i) {
            final RegionFileType type = types[i];
            reads[i] = RegionFileIOThread.loadDataAsync(world, chunkX, chunkZ, type,
                (final CompoundTag data, final Throwable throwable) -> {
                    if (throwable != null) {
                        ret.setThrowable(type, throwable);
                    } else {
                        ret.setData(type, data);
                    }

                    if (completions.incrementAndGet() == expectedCompletions) {
                        onComplete.accept(ret);
                    }
                }, intendingToBlock, priority);
        }

        return new CancellableReads(reads);
    }

    /**
     * Schedules a load to be executed asynchronously. This task will load the specified regionfile type, and then call
     * {@code onComplete}.
     * <p>
     *     Impl notes:
     * </p>
     * <li>
     *     The {@code onComplete} parameter may be completed during the execution of this function synchronously or it may
     *     be completed asynchronously on this file io thread. Interacting with the file IO thread in the completion of
     *     data is undefined behaviour, and can cause deadlock.
     * </li>
     *
     * @param world Chunk's world
     * @param chunkX Chunk's x coordinate
     * @param chunkZ Chunk's z coordinate
     * @param onComplete Consumer to execute once this task has completed
     * @param intendingToBlock Whether the caller is intending to block on completion. This only affects the cost
     *                         of this call.
     *
     * @return The {@link Cancellable} for this chunk load. Cancelling it will not affect other loads for the same chunk data.
     *
     * @see #loadChunkData(ServerLevel, int, int, Consumer, boolean, RegionFileType...)
     * @see #loadChunkData(ServerLevel, int, int, Consumer, boolean, Priority, RegionFileType...)
     * @see #loadAllChunkData(ServerLevel, int, int, Consumer, boolean)
     * @see #loadAllChunkData(ServerLevel, int, int, Consumer, boolean, Priority)
     */
    public static Cancellable loadDataAsync(final ServerLevel world, final int chunkX, final int chunkZ,
                                            final RegionFileType type, final BiConsumer<CompoundTag, Throwable> onComplete,
                                            final boolean intendingToBlock) {
        return RegionFileIOThread.loadDataAsync(world, chunkX, chunkZ, type, onComplete, intendingToBlock, Priority.NORMAL);
    }

    /**
     * Schedules a load to be executed asynchronously. This task will load the specified regionfile type, and then call
     * {@code onComplete}.
     * <p>
     *     Impl notes:
     * </p>
     * <li>
     *     The {@code onComplete} parameter may be completed during the execution of this function synchronously or it may
     *     be completed asynchronously on this file io thread. Interacting with the file IO thread in the completion of
     *     data is undefined behaviour, and can cause deadlock.
     * </li>
     *
     * @param world Chunk's world
     * @param chunkX Chunk's x coordinate
     * @param chunkZ Chunk's z coordinate
     * @param onComplete Consumer to execute once this task has completed
     * @param intendingToBlock Whether the caller is intending to block on completion. This only affects the cost
     *                         of this call.
     * @param priority Minimum priority to load the data at.
     *
     * @return The {@link Cancellable} for this chunk load. Cancelling it will not affect other loads for the same chunk data.
     *
     * @see #loadChunkData(ServerLevel, int, int, Consumer, boolean, RegionFileType...)
     * @see #loadChunkData(ServerLevel, int, int, Consumer, boolean, Priority, RegionFileType...)
     * @see #loadAllChunkData(ServerLevel, int, int, Consumer, boolean)
     * @see #loadAllChunkData(ServerLevel, int, int, Consumer, boolean, Priority)
     */
    public static Cancellable loadDataAsync(final ServerLevel world, final int chunkX, final int chunkZ,
                                            final RegionFileType type, final BiConsumer<CompoundTag, Throwable> onComplete,
                                            final boolean intendingToBlock, final Priority priority) {
        final RegionFileIOThread thread = RegionFileIOThread.selectThread(world, chunkX, chunkZ, type);
        return thread.loadDataAsyncInternal(world, chunkX, chunkZ, type, onComplete, intendingToBlock, priority);
    }

    private static Boolean doesRegionFileExist(final int chunkX, final int chunkZ, final boolean intendingToBlock,
                                               final ChunkDataController taskController) {
        final ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        if (intendingToBlock) {
            return taskController.computeForRegionFile(chunkX, chunkZ, true, (final RegionFile file) -> {
                if (file == null) { // null if no regionfile exists
                    return Boolean.FALSE;
                }

                return file.hasChunk(chunkPos) ? Boolean.TRUE : Boolean.FALSE;
            });
        } else {
            // first check if the region file for sure does not exist
            if (taskController.doesRegionFileNotExist(chunkX, chunkZ)) {
                return Boolean.FALSE;
            } // else: it either exists or is not known, fall back to checking the loaded region file

            return taskController.computeForRegionFileIfLoaded(chunkX, chunkZ, (final RegionFile file) -> {
                if (file == null) { // null if not loaded
                    // not sure at this point, let the I/O thread figure it out
                    return Boolean.TRUE;
                }

                return file.hasChunk(chunkPos) ? Boolean.TRUE : Boolean.FALSE;
            });
        }
    }

    Cancellable loadDataAsyncInternal(final ServerLevel world, final int chunkX, final int chunkZ,
                                      final RegionFileType type, final BiConsumer<CompoundTag, Throwable> onComplete,
                                      final boolean intendingToBlock, final Priority priority) {
        final ChunkDataController taskController = getControllerFor(world, type);

        final ImmediateCallbackCompletion callbackInfo = new ImmediateCallbackCompletion();

        final long key = CoordinateUtils.getChunkKey(chunkX, chunkZ);
        final BiLong1Function<ChunkDataTask, ChunkDataTask> compute = (final long keyInMap, final ChunkDataTask running) -> {
            if (running == null) {
                // not scheduled

                if (callbackInfo.regionFileCalculation == null) {
                    // caller will compute this outside of compute(), to avoid holding the bin lock
                    callbackInfo.needsRegionFileTest = true;
                    return null;
                }

                if (callbackInfo.regionFileCalculation == Boolean.FALSE) {
                    // not on disk
                    callbackInfo.data = null;
                    callbackInfo.throwable = null;
                    callbackInfo.completeNow = true;
                    return null;
                }

                // set up task
                final ChunkDataTask newTask = new ChunkDataTask(
                    world, chunkX, chunkZ, taskController, RegionFileIOThread.this, priority
                );
                newTask.inProgressRead = new InProgressRead();
                newTask.inProgressRead.addToAsyncWaiters(onComplete);

                callbackInfo.tasksNeedsScheduling = true;
                return newTask;
            }

            final CompoundTag pendingWrite = running.inProgressWrite;

            if (pendingWrite == ChunkDataTask.NOTHING_TO_WRITE) {
                // need to add to waiters here, because the regionfile thread will use compute() to lock and check for cancellations
                if (!running.inProgressRead.addToAsyncWaiters(onComplete)) {
                    callbackInfo.data = running.inProgressRead.value;
                    callbackInfo.throwable = running.inProgressRead.throwable;
                    callbackInfo.completeNow = true;
                }
                return running;
            }

            // at this stage we have to use the in progress write's data to avoid an order issue
            callbackInfo.data = pendingWrite;
            callbackInfo.throwable = null;
            callbackInfo.completeNow = true;
            return running;
        };

        ChunkDataTask curr = taskController.tasks.get(key);
        if (curr == null) {
            callbackInfo.regionFileCalculation = doesRegionFileExist(chunkX, chunkZ, intendingToBlock, taskController);
        }
        ChunkDataTask ret = taskController.tasks.compute(key, compute);
        if (callbackInfo.needsRegionFileTest) {
            // curr isn't null but when we went into compute() it was
            callbackInfo.regionFileCalculation = doesRegionFileExist(chunkX, chunkZ, intendingToBlock, taskController);
            // now it should be fine
            ret = taskController.tasks.compute(key, compute);
        }

        // needs to be scheduled
        if (callbackInfo.tasksNeedsScheduling) {
            ret.prioritisedTask.queue();
        } else if (callbackInfo.completeNow) {
            try {
                onComplete.accept(callbackInfo.data == null ? null : callbackInfo.data.copy(), callbackInfo.throwable);
            } catch (final Throwable thr) {
                LOGGER.error("Callback " + ConcurrentUtil.genericToString(onComplete) + " synchronously failed to handle chunk data for task " + ret.toString(), thr);
            }
        } else {
            // we're waiting on a task we didn't schedule, so raise its priority to what we want
            ret.prioritisedTask.raisePriority(priority);
        }

        return new CancellableRead(onComplete, ret);
    }

    /**
     * Schedules a load task to be executed asynchronously, and blocks on that task.
     *
     * @param world Chunk's world
     * @param chunkX Chunk's x coordinate
     * @param chunkZ Chunk's z coordinate
     * @param type Regionfile type
     * @param priority Minimum priority to load the data at.
     *
     * @return The chunk data for the chunk. Note that a {@code null} result means the chunk or regionfile does not exist on disk.
     *
     * @throws IOException If the load fails for any reason
     */
    public static CompoundTag loadData(final ServerLevel world, final int chunkX, final int chunkZ, final RegionFileType type,
                                       final Priority priority) throws IOException {
        final CompletableFuture<CompoundTag> ret = new CompletableFuture<>();

        RegionFileIOThread.loadDataAsync(world, chunkX, chunkZ, type, (final CompoundTag compound, final Throwable thr) -> {
            if (thr != null) {
                ret.completeExceptionally(thr);
            } else {
                ret.complete(compound);
            }
        }, true, priority);

        try {
            return ret.join();
        } catch (final CompletionException ex) {
            throw new IOException(ex);
        }
    }

    private static final class ImmediateCallbackCompletion {

        public CompoundTag data;
        public Throwable throwable;
        public boolean completeNow;
        public boolean tasksNeedsScheduling;
        public boolean needsRegionFileTest;
        public Boolean regionFileCalculation;

    }

    private static final class CancellableRead implements Cancellable {

        private BiConsumer<CompoundTag, Throwable> callback;
        private ChunkDataTask task;

        CancellableRead(final BiConsumer<CompoundTag, Throwable> callback, final ChunkDataTask task) {
            this.callback = callback;
            this.task = task;
        }

        @Override
        public boolean cancel() {
            final BiConsumer<CompoundTag, Throwable> callback = this.callback;
            final ChunkDataTask task = this.task;

            if (callback == null || task == null) {
                return false;
            }

            this.callback = null;
            this.task = null;

            final InProgressRead read = task.inProgressRead;

            // read can be null if no read was scheduled (i.e no regionfile existed or chunk in regionfile didn't)
            return read != null && read.cancel(callback);
        }
    }

    private static final class CancellableReads implements Cancellable {

        private Cancellable[] reads;

        private static final VarHandle READS_HANDLE = ConcurrentUtil.getVarHandle(CancellableReads.class, "reads", Cancellable[].class);

        CancellableReads(final Cancellable[] reads) {
            this.reads = reads;
        }

        @Override
        public boolean cancel() {
            final Cancellable[] reads = (Cancellable[])READS_HANDLE.getAndSet((CancellableReads)this, (Cancellable[])null);

            if (reads == null) {
                return false;
            }

            boolean ret = false;

            for (final Cancellable read : reads) {
                ret |= read.cancel();
            }

            return ret;
        }
    }

    private static final class InProgressRead {

        private static final Logger LOGGER = LoggerFactory.getLogger(InProgressRead.class);

        private CompoundTag value;
        private Throwable throwable;
        private MultiThreadedQueue<BiConsumer<CompoundTag, Throwable>> callbacks = new MultiThreadedQueue<>();

        public boolean hasNoWaiters() {
            return this.callbacks.isEmpty();
        }

        public boolean addToAsyncWaiters(final BiConsumer<CompoundTag, Throwable> callback) {
            return this.callbacks.add(callback);
        }

        public boolean cancel(final BiConsumer<CompoundTag, Throwable> callback) {
            return this.callbacks.remove(callback);
        }

        public void complete(final ChunkDataTask task, final CompoundTag value, final Throwable throwable) {
            this.value = value;
            this.throwable = throwable;

            BiConsumer<CompoundTag, Throwable> consumer;
            while ((consumer = this.callbacks.pollOrBlockAdds()) != null) {
                try {
                    consumer.accept(value == null ? null : value.copy(), throwable);
                } catch (final Throwable thr) {
                    LOGGER.error("Callback " + ConcurrentUtil.genericToString(consumer) + " failed to handle chunk data for task " + task.toString(), thr);
                }
            }
        }
    }

    public static abstract class ChunkDataController {

        // ConcurrentHashMap synchronizes per chain, so reduce the chance of task's hashes colliding.
        private final ConcurrentLong2ReferenceChainedHashTable<ChunkDataTask> tasks = ConcurrentLong2ReferenceChainedHashTable.createWithCapacity(8192, 0.5f);

        public final RegionFileType type;

        public ChunkDataController(final RegionFileType type) {
            this.type = type;
        }

        public abstract RegionFileStorage getCache();

        public abstract void writeData(final int chunkX, final int chunkZ, final CompoundTag compound) throws IOException;

        public abstract CompoundTag readData(final int chunkX, final int chunkZ) throws IOException;

        public boolean hasTasks() {
            return !this.tasks.isEmpty();
        }

        public boolean doesRegionFileNotExist(final int chunkX, final int chunkZ) {
            return ((ChunkSystemRegionFileStorage)(Object)this.getCache()).moonrise$doesRegionFileNotExistNoIO(chunkX, chunkZ);
        }

        public <T> T computeForRegionFile(final int chunkX, final int chunkZ, final boolean existingOnly, final Function<RegionFile, T> function) {
            final RegionFileStorage cache = this.getCache();
            final RegionFile regionFile;
            synchronized (cache) {
                try {
                    if (existingOnly) {
                        regionFile = ((ChunkSystemRegionFileStorage)(Object)cache).moonrise$getRegionFileIfExists(chunkX, chunkZ);
                    } else {
                        regionFile = cache.getRegionFile(new ChunkPos(chunkX, chunkZ));
                    }
                } catch (final IOException ex) {
                    throw new RuntimeException(ex);
                }

                return function.apply(regionFile);
            }
        }

        public <T> T computeForRegionFileIfLoaded(final int chunkX, final int chunkZ, final Function<RegionFile, T> function) {
            final RegionFileStorage cache = this.getCache();
            final RegionFile regionFile;

            synchronized (cache) {
                regionFile = ((ChunkSystemRegionFileStorage)(Object)cache).moonrise$getRegionFileIfLoaded(chunkX, chunkZ);

                return function.apply(regionFile);
            }
        }
    }

    private static final class ChunkDataTask implements Runnable {

        private static final CompoundTag NOTHING_TO_WRITE = new CompoundTag();

        private static final Logger LOGGER = LoggerFactory.getLogger(ChunkDataTask.class);

        private InProgressRead inProgressRead;
        private volatile CompoundTag inProgressWrite = NOTHING_TO_WRITE; // only needs to be acquire/release

        private boolean failedWrite;

        private final ServerLevel world;
        private final int chunkX;
        private final int chunkZ;
        private final ChunkDataController taskController;

        private final PrioritisedTask prioritisedTask;

        /*
         * IO thread will perform reads before writes for a given chunk x and z
         *
         * How reads/writes are scheduled:
         *
         * If read is scheduled while scheduling write, take no special action and just schedule write
         * If read is scheduled while scheduling read and no write is scheduled, chain the read task
         *
         *
         * If write is scheduled while scheduling read, use the pending write data and ret immediately (so no read is scheduled)
         * If write is scheduled while scheduling write (ignore read in progress), overwrite the write in progress data
         *
         * This allows the reads and writes to act as if they occur synchronously to the thread scheduling them, however
         * it fails to properly propagate write failures thanks to writes overwriting each other
         */

        public ChunkDataTask(final ServerLevel world, final int chunkX, final int chunkZ, final ChunkDataController taskController,
                             final PrioritisedExecutor executor, final Priority priority) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.taskController = taskController;
            this.prioritisedTask = executor.createTask(this, priority);
        }

        @Override
        public String toString() {
            return "Task for world: '" + WorldUtil.getWorldName(this.world) + "' at (" + this.chunkX + "," + this.chunkZ +
                    ") type: " + this.taskController.type.name() + ", hash: " + this.hashCode();
        }

        @Override
        public void run() {
            final InProgressRead read = this.inProgressRead;
            final long chunkKey = CoordinateUtils.getChunkKey(this.chunkX, this.chunkZ);

            if (read != null) {
                final boolean[] canRead = new boolean[] { true };

                if (read.hasNoWaiters()) {
                    // cancelled read? go to task controller to confirm
                    final ChunkDataTask inMap = this.taskController.tasks.compute(chunkKey, (final long keyInMap, final ChunkDataTask valueInMap) -> {
                        if (valueInMap == null) {
                            throw new IllegalStateException("Write completed concurrently, expected this task: " + ChunkDataTask.this.toString() + ", report this!");
                        }
                        if (valueInMap != ChunkDataTask.this) {
                            throw new IllegalStateException("Chunk task mismatch, expected this task: " + ChunkDataTask.this.toString() + ", got: " + valueInMap.toString() + ", report this!");
                        }

                        if (!read.hasNoWaiters()) {
                            return valueInMap;
                        } else {
                            canRead[0] = false;
                        }

                        return valueInMap.inProgressWrite == NOTHING_TO_WRITE ? null : valueInMap;
                    });

                    if (inMap == null) {
                        // read is cancelled - and no write pending, so we're done
                        return;
                    }
                    // if there is a write in progress, we don't actually have to worry about waiters gaining new entries -
                    // the readers will just use the in progress write, so the value in canRead is good to use without
                    // further synchronisation.
                }

                if (canRead[0]) {
                    CompoundTag compound = null;
                    Throwable throwable = null;

                    try {
                        compound = this.taskController.readData(this.chunkX, this.chunkZ);
                    } catch (final Throwable thr) {
                        throwable = thr;
                        LOGGER.error("Failed to read chunk data for task: " + this.toString(), thr);
                    }
                    read.complete(this, compound, throwable);
                }
            }

            CompoundTag write = this.inProgressWrite;

            if (write == NOTHING_TO_WRITE) {
                final ChunkDataTask inMap = this.taskController.tasks.compute(chunkKey, (final long keyInMap, final ChunkDataTask valueInMap) -> {
                    if (valueInMap == null) {
                        throw new IllegalStateException("Write completed concurrently, expected this task: " + ChunkDataTask.this.toString() + ", report this!");
                    }
                    if (valueInMap != ChunkDataTask.this) {
                        throw new IllegalStateException("Chunk task mismatch, expected this task: " + ChunkDataTask.this.toString() + ", got: " + valueInMap.toString() + ", report this!");
                    }
                    return valueInMap.inProgressWrite == NOTHING_TO_WRITE ? null : valueInMap;
                });

                if (inMap == null) {
                    return; // set the task value to null, indicating we're done
                } // else: inProgressWrite changed, so now we have something to write
            }

            for (;;) {
                write = this.inProgressWrite;
                final CompoundTag dataWritten = write;

                boolean failedWrite = false;

                try {
                    this.taskController.writeData(this.chunkX, this.chunkZ, write);
                } catch (final Throwable thr) {
                    // TODO implement this?
                    /*if (thr instanceof RegionFileStorage.RegionFileSizeException) {
                        final int maxSize = RegionFile.MAX_CHUNK_SIZE / (1024 * 1024);
                        LOGGER.error("Chunk at (" + this.chunkX + "," + this.chunkZ + ") in '" + WorldUtil.getWorldName(this.world) + "' exceeds max size of " + maxSize + "MiB, it has been deleted from disk.");
                    } else */{
                        failedWrite = thr instanceof IOException;
                        LOGGER.error("Failed to write chunk data for task: " + this.toString(), thr);
                    }
                }

                final boolean finalFailWrite = failedWrite;
                final boolean[] done = new boolean[] { false };

                this.taskController.tasks.compute(chunkKey, (final long keyInMap, final ChunkDataTask valueInMap) -> {
                    if (valueInMap == null) {
                        throw new IllegalStateException("Write completed concurrently, expected this task: " + ChunkDataTask.this.toString() + ", report this!");
                    }
                    if (valueInMap != ChunkDataTask.this) {
                        throw new IllegalStateException("Chunk task mismatch, expected this task: " + ChunkDataTask.this.toString() + ", got: " + valueInMap.toString() + ", report this!");
                    }
                    if (valueInMap.inProgressWrite == dataWritten) {
                        valueInMap.failedWrite = finalFailWrite;
                        done[0] = true;
                        // keep the data in map if we failed the write so we can try to prevent data loss
                        return finalFailWrite ? valueInMap : null;
                    }
                    // different data than expected, means we need to retry write
                    return valueInMap;
                });

                if (done[0]) {
                    return;
                }

                // fetch & write new data
                continue;
            }
        }
    }
}
