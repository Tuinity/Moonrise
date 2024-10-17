package ca.spottedleaf.moonrise.patches.chunk_system.io;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.concurrentutil.completable.CallbackCompletable;
import ca.spottedleaf.concurrentutil.completable.Completable;
import ca.spottedleaf.concurrentutil.executor.Cancellable;
import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.executor.queue.PrioritisedTaskQueue;
import ca.spottedleaf.concurrentutil.function.BiLong1Function;
import ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class MoonriseRegionFileIO {

    private static final int REGION_FILE_SHIFT = 5;
    private static final Logger LOGGER = LoggerFactory.getLogger(MoonriseRegionFileIO.class);

    /**
     * The types of RegionFiles controlled by the I/O thread(s).
     */
    public static enum RegionFileType {
        CHUNK_DATA,
        POI_DATA,
        ENTITY_DATA;
    }

    public static RegionDataController getControllerFor(final ServerLevel world, final RegionFileType type) {
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

    private static final RegionFileType[] CACHED_REGIONFILE_TYPES = RegionFileType.values();

    /**
     * Collects RegionFile data for a certain chunk.
     */
    public static final class RegionFileData {

        private final boolean[] hasResult = new boolean[CACHED_REGIONFILE_TYPES.length];
        private final CompoundTag[] data = new CompoundTag[CACHED_REGIONFILE_TYPES.length];
        private final Throwable[] throwables = new Throwable[CACHED_REGIONFILE_TYPES.length];

        /**
         * Sets the result associated with the specified RegionFile type. Note that
         * results can only be set once per RegionFile type.
         *
         * @param type The RegionFile type.
         * @param data The result to set.
         */
        public void setData(final MoonriseRegionFileIO.RegionFileType type, final CompoundTag data) {
            final int index = type.ordinal();

            if (this.hasResult[index]) {
                throw new IllegalArgumentException("Result already exists for type " + type);
            }
            this.hasResult[index] = true;
            this.data[index] = data;
        }

        /**
         * Sets the result associated with the specified RegionFile type. Note that
         * results can only be set once per RegionFile type.
         *
         * @param type The RegionFile type.
         * @param throwable The result to set.
         */
        public void setThrowable(final MoonriseRegionFileIO.RegionFileType type, final Throwable throwable) {
            final int index = type.ordinal();

            if (this.hasResult[index]) {
                throw new IllegalArgumentException("Result already exists for type " + type);
            }
            this.hasResult[index] = true;
            this.throwables[index] = throwable;
        }

        /**
         * Returns whether there is a result for the specified RegionFile type.
         *
         * @param type Specified RegionFile type.
         *
         * @return Whether a result exists for {@code type}.
         */
        public boolean hasResult(final MoonriseRegionFileIO.RegionFileType type) {
            return this.hasResult[type.ordinal()];
        }

        /**
         * Returns the data result for the RegionFile type.
         *
         * @param type Specified RegionFile type.
         *
         * @throws IllegalArgumentException If the result has not been set for {@code type}.
         * @return The data result for the specified type. If the result is a {@code Throwable},
         * then returns {@code null}.
         */
        public CompoundTag getData(final MoonriseRegionFileIO.RegionFileType type) {
            final int index = type.ordinal();

            if (!this.hasResult[index]) {
                throw new IllegalArgumentException("Result does not exist for type " + type);
            }

            return this.data[index];
        }

        /**
         * Returns the throwable result for the RegionFile type.
         *
         * @param type Specified RegionFile type.
         *
         * @throws IllegalArgumentException If the result has not been set for {@code type}.
         * @return The throwable result for the specified type. If the result is an {@code CompoundTag},
         * then returns {@code null}.
         */
        public Throwable getThrowable(final MoonriseRegionFileIO.RegionFileType type) {
            final int index = type.ordinal();

            if (!this.hasResult[index]) {
                throw new IllegalArgumentException("Result does not exist for type " + type);
            }

            return this.throwables[index];
        }
    }

    public static void flushRegionStorages(final ServerLevel world) throws IOException {
        for (final RegionFileType type : CACHED_REGIONFILE_TYPES) {
            flushRegionStorages(world, type);
        }
    }

    public static void flushRegionStorages(final ServerLevel world, final RegionFileType type) throws IOException {
        getControllerFor(world, type).getCache().flush();
    }

    public static void flush(final MinecraftServer server) {
        for (final ServerLevel world : server.getAllLevels()) {
            flush(world);
        }
    }

    public static void flush(final ServerLevel world) {
        for (final RegionFileType regionFileType : CACHED_REGIONFILE_TYPES) {
            flush(world, regionFileType);
        }
    }

    public static void flush(final ServerLevel world, final RegionFileType type) {
        final RegionDataController taskController = getControllerFor(world, type);

        long failures = 1L; // start at 0.13ms

        while (taskController.hasTasks()) {
            Thread.yield();
            failures = ConcurrentUtil.linearLongBackoff(failures, 125_000L, 5_000_000L); // 125us, 5ms
        }
    }

    public static void partialFlush(final ServerLevel world, final int tasksRemaining) {
        for (long failures = 1L;;) { // start at 0.13ms
            long totalTasks = 0L;
            for (final RegionFileType regionFileType : CACHED_REGIONFILE_TYPES) {
                totalTasks += getControllerFor(world, regionFileType).getTotalWorkingTasks();
            }

            if (totalTasks > (long)tasksRemaining) {
                Thread.yield();
                failures = ConcurrentUtil.linearLongBackoff(failures, 125_000L, 5_000_000L); // 125us, 5ms
            } else {
                return;
            }
        }
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
     * Returns the priority for the specified regionfile type for the specified chunk.
     * @param world Specified world.
     * @param chunkX Specified chunk x.
     * @param chunkZ Specified chunk z.
     * @param type Specified regionfile type.
     * @return The priority for the chunk
     */
    public static Priority getPriority(final ServerLevel world, final int chunkX, final int chunkZ, final RegionFileType type) {
        final RegionDataController taskController = getControllerFor(world, type);
        final ChunkIOTask task = taskController.chunkTasks.get(CoordinateUtils.getChunkKey(chunkX, chunkZ));

        if (task == null) {
            return Priority.COMPLETING;
        }

        return task.getPriority();
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
            MoonriseRegionFileIO.setPriority(world, chunkX, chunkZ, type, priority);
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
        final RegionDataController taskController = getControllerFor(world, type);
        final ChunkIOTask task = taskController.chunkTasks.get(CoordinateUtils.getChunkKey(chunkX, chunkZ));

        if (task != null) {
            task.setPriority(priority);
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
            MoonriseRegionFileIO.raisePriority(world, chunkX, chunkZ, type, priority);
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
        final RegionDataController taskController = getControllerFor(world, type);
        final ChunkIOTask task = taskController.chunkTasks.get(CoordinateUtils.getChunkKey(chunkX, chunkZ));

        if (task != null) {
            task.raisePriority(priority);
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
            MoonriseRegionFileIO.lowerPriority(world, chunkX, chunkZ, type, priority);
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
        final RegionDataController taskController = getControllerFor(world, type);
        final ChunkIOTask task = taskController.chunkTasks.get(CoordinateUtils.getChunkKey(chunkX, chunkZ));

        if (task != null) {
            task.lowerPriority(priority);
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
        MoonriseRegionFileIO.scheduleSave(world, chunkX, chunkZ, data, type, Priority.NORMAL);
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
        scheduleSave(
            world, chunkX, chunkZ,
            (final BiConsumer<CompoundTag, Throwable> consumer) -> {
                consumer.accept(data, null);
            }, null, type, priority
        );
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
     * <li>
     *     The specified write task, if not null, will have its priority controlled by the scheduler.
     * </li>
     *
     * @param world Chunk's world
     * @param chunkX Chunk's x coordinate
     * @param chunkZ Chunk's z coordinate
     * @param completable Chunk's pending data
     * @param writeTask The task responsible for completing the pending chunk data
     * @param type The regionfile type to write to.
     * @param priority The minimum priority to schedule at.
     *
     * @throws IllegalStateException If the file io thread has shutdown.
     */
    public static void scheduleSave(final ServerLevel world, final int chunkX, final int chunkZ, final CallbackCompletable<CompoundTag> completable,
                                    final PrioritisedExecutor.PrioritisedTask writeTask, final RegionFileType type, final Priority priority) {
        scheduleSave(world, chunkX, chunkZ, completable::addWaiter, writeTask, type, priority);
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
     * <li>
     *     The specified write task, if not null, will have its priority controlled by the scheduler.
     * </li>
     *
     * @param world Chunk's world
     * @param chunkX Chunk's x coordinate
     * @param chunkZ Chunk's z coordinate
     * @param completable Chunk's pending data
     * @param writeTask The task responsible for completing the pending chunk data
     * @param type The regionfile type to write to.
     * @param priority The minimum priority to schedule at.
     *
     * @throws IllegalStateException If the file io thread has shutdown.
     */
    public static void scheduleSave(final ServerLevel world, final int chunkX, final int chunkZ, final Completable<CompoundTag> completable,
                                    final PrioritisedExecutor.PrioritisedTask writeTask, final RegionFileType type, final Priority priority) {
        scheduleSave(world, chunkX, chunkZ, completable::whenComplete, writeTask, type, priority);
    }

    private static void scheduleSave(final ServerLevel world, final int chunkX, final int chunkZ, final Consumer<BiConsumer<CompoundTag, Throwable>> scheduler,
                                     final PrioritisedExecutor.PrioritisedTask writeTask, final RegionFileType type, final Priority priority) {
        final RegionDataController taskController = getControllerFor(world, type);

        final boolean[] created = new boolean[1];
        final ChunkIOTask.InProgressWrite write = new ChunkIOTask.InProgressWrite(writeTask);
        final ChunkIOTask task = taskController.chunkTasks.compute(CoordinateUtils.getChunkKey(chunkX, chunkZ),
            (final long keyInMap, final ChunkIOTask taskRunning) -> {
                if (taskRunning == null || taskRunning.failedWrite) {
                    // no task is scheduled or the previous write failed - meaning we need to overwrite it

                    // create task
                    final ChunkIOTask newTask = new ChunkIOTask(
                        world, taskController, chunkX, chunkZ, priority, new ChunkIOTask.InProgressRead()
                    );

                    newTask.pushPendingWrite(write);

                    created[0] = true;

                    return newTask;
                }

                taskRunning.pushPendingWrite(write);

                return taskRunning;
            }
        );

        write.schedule(task, scheduler);

        if (created[0]) {
            taskController.startTask(task);
            task.scheduleWriteCompress();
        } else {
            task.raisePriority(priority);
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
        return MoonriseRegionFileIO.loadAllChunkData(world, chunkX, chunkZ, onComplete, intendingToBlock, Priority.NORMAL);
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
        return MoonriseRegionFileIO.loadChunkData(world, chunkX, chunkZ, onComplete, intendingToBlock, priority, CACHED_REGIONFILE_TYPES);
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
        return MoonriseRegionFileIO.loadChunkData(world, chunkX, chunkZ, onComplete, intendingToBlock, Priority.NORMAL, types);
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
            reads[i] = MoonriseRegionFileIO.loadDataAsync(world, chunkX, chunkZ, type,
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
        return MoonriseRegionFileIO.loadDataAsync(world, chunkX, chunkZ, type, onComplete, intendingToBlock, Priority.NORMAL);
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
        final RegionDataController taskController = getControllerFor(world, type);

        final ImmediateCallbackCompletion callbackInfo = new ImmediateCallbackCompletion();

        final long key = CoordinateUtils.getChunkKey(chunkX, chunkZ);
        final BiLong1Function<ChunkIOTask, ChunkIOTask> compute = (final long keyInMap, final ChunkIOTask running) -> {
            if (running == null) {
                // not scheduled

                // set up task
                final ChunkIOTask newTask = new ChunkIOTask(
                    world, taskController, chunkX, chunkZ, priority, new ChunkIOTask.InProgressRead()
                );
                newTask.inProgressRead.addToAsyncWaiters(onComplete);

                callbackInfo.tasksNeedReadScheduling = true;
                return newTask;
            }

            final ChunkIOTask.InProgressWrite pendingWrite = running.inProgressWrite;

            if (pendingWrite == null) {
                // need to add to waiters here, because the regionfile thread will use compute() to lock and check for cancellations
                if (!running.inProgressRead.addToAsyncWaiters(onComplete)) {
                    callbackInfo.data = running.inProgressRead.value;
                    callbackInfo.throwable = running.inProgressRead.throwable;
                    callbackInfo.completeNow = true;
                }

                callbackInfo.read = running.inProgressRead;

                return running;
            }

            // at this stage we have to use the in progress write's data to avoid an order issue

            if (!pendingWrite.addToAsyncWaiters(onComplete)) {
                // data is ready now
                callbackInfo.data = pendingWrite.value;
                callbackInfo.throwable = pendingWrite.throwable;
                callbackInfo.completeNow = true;
                return running;
            }

            callbackInfo.write = pendingWrite;

            return running;
        };

        final ChunkIOTask ret = taskController.chunkTasks.compute(key, compute);

        // needs to be scheduled
        if (callbackInfo.tasksNeedReadScheduling) {
            taskController.startTask(ret);
            ret.scheduleReadIO();
        } else if (callbackInfo.completeNow) {
            try {
                onComplete.accept(callbackInfo.data == null ? null : callbackInfo.data.copy(), callbackInfo.throwable);
            } catch (final Throwable thr) {
                LOGGER.error("Callback " + ConcurrentUtil.genericToString(onComplete) + " synchronously failed to handle chunk data for task " + ret.toString(), thr);
            }
        } else {
            // we're waiting on a task we didn't schedule, so raise its priority to what we want
            ret.raisePriority(priority);
        }

        return new CancellableRead(onComplete, callbackInfo.read, callbackInfo.write);
    }

    private static final class ImmediateCallbackCompletion {

        private CompoundTag data;
        private Throwable throwable;
        private boolean completeNow;
        private boolean tasksNeedReadScheduling;
        private ChunkIOTask.InProgressRead read;
        private ChunkIOTask.InProgressWrite write;

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

        MoonriseRegionFileIO.loadDataAsync(world, chunkX, chunkZ, type, (final CompoundTag compound, final Throwable thr) -> {
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
    
    private static final class CancellableRead implements Cancellable {

        private BiConsumer<CompoundTag, Throwable> callback;
        private ChunkIOTask.InProgressRead read;
        private ChunkIOTask.InProgressWrite write;

        private CancellableRead(final BiConsumer<CompoundTag, Throwable> callback,
                                final ChunkIOTask.InProgressRead read,
                                final ChunkIOTask.InProgressWrite write) {
            this.callback = callback;
            this.read = read;
            this.write = write;
        }

        @Override
        public boolean cancel() {
            final BiConsumer<CompoundTag, Throwable> callback = this.callback;
            final ChunkIOTask.InProgressRead read = this.read;
            final ChunkIOTask.InProgressWrite write = this.write;

            if (callback == null || (read == null && write == null)) {
                return false;
            }

            this.callback = null;
            this.read = null;
            this.write = null;

            if (read != null) {
                return read.cancel(callback);
            }
            if (write != null) {
                return write.cancel(callback);
            }

            // unreachable
            throw new InternalError();
        }
    }

    private static final class CancellableReads implements Cancellable {

        private Cancellable[] reads;
        private static final VarHandle READS_HANDLE = ConcurrentUtil.getVarHandle(CancellableReads.class, "reads", Cancellable[].class);

        private CancellableReads(final Cancellable[] reads) {
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

    private static final class ChunkIOTask {

        private final ServerLevel world;
        private final RegionDataController regionDataController;
        private final int chunkX;
        private final int chunkZ;
        private Priority priority;
        private PrioritisedExecutor.PrioritisedTask currentTask;

        private final InProgressRead inProgressRead;
        private volatile InProgressWrite inProgressWrite;
        private final ReferenceOpenHashSet<InProgressWrite> allPendingWrites = new ReferenceOpenHashSet<>();

        private RegionDataController.ReadData readData;
        private RegionDataController.WriteData writeData;
        private boolean failedWrite;

        public ChunkIOTask(final ServerLevel world, final RegionDataController regionDataController,
                           final int chunkX, final int chunkZ, final Priority priority, final InProgressRead inProgressRead) {
            this.world = world;
            this.regionDataController = regionDataController;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.priority = priority;
            this.inProgressRead = inProgressRead;
        }

        public Priority getPriority() {
            synchronized (this) {
                return this.priority;
            }
        }

        // must hold lock on this object
        private void updatePriority(final Priority priority) {
            this.priority = priority;
            if (this.currentTask != null) {
                this.currentTask.setPriority(priority);
            }
            for (final InProgressWrite write : this.allPendingWrites) {
                if (write.writeTask != null) {
                    write.writeTask.setPriority(priority);
                }
            }
        }

        public boolean setPriority(final Priority priority) {
            synchronized (this) {
                if (this.priority == priority) {
                    return false;
                }

                this.updatePriority(priority);

                return true;
            }
        }

        public boolean raisePriority(final Priority priority) {
            synchronized (this) {
                if (this.priority.isHigherOrEqualPriority(priority)) {
                    return false;
                }

                this.updatePriority(priority);

                return true;
            }
        }

        public boolean lowerPriority(final Priority priority) {
            synchronized (this) {
                if (this.priority.isLowerOrEqualPriority(priority)) {
                    return false;
                }

                this.updatePriority(priority);

                return true;
            }
        }

        private void pushPendingWrite(final InProgressWrite write) {
            this.inProgressWrite = write;
            synchronized (this) {
                this.allPendingWrites.add(write);
                if (write.writeTask != null) {
                    write.writeTask.setPriority(this.priority);
                }
            }
        }

        private void pendingWriteComplete(final InProgressWrite write) {
            synchronized (this) {
                this.allPendingWrites.remove(write);
            }
        }

        public void scheduleReadIO() {
            final PrioritisedExecutor.PrioritisedTask task;
            synchronized (this) {
                task = this.regionDataController.ioScheduler.createTask(this.chunkX, this.chunkZ, this::performReadIO, this.priority);
                this.currentTask = task;
            }
            task.queue();
        }

        private void performReadIO() {
            final InProgressRead read = this.inProgressRead;
            final long chunkKey = CoordinateUtils.getChunkKey(this.chunkX, this.chunkZ);

            final boolean[] canRead = new boolean[] { true };

            if (read.hasNoWaiters()) {
                // cancelled read? go to task controller to confirm
                final ChunkIOTask inMap = this.regionDataController.chunkTasks.compute(chunkKey, (final long keyInMap, final ChunkIOTask valueInMap) -> {
                    if (valueInMap == null) {
                        throw new IllegalStateException("Write completed concurrently, expected this task: " + ChunkIOTask.this.toString() + ", report this!");
                    }
                    if (valueInMap != ChunkIOTask.this) {
                        throw new IllegalStateException("Chunk task mismatch, expected this task: " + ChunkIOTask.this.toString() + ", got: " + valueInMap.toString() + ", report this!");
                    }

                    if (!read.hasNoWaiters()) {
                        return valueInMap;
                    } else {
                        canRead[0] = false;
                    }

                    if (valueInMap.inProgressWrite != null) {
                        return valueInMap;
                    }

                    return null;
                });

                if (inMap == null) {
                    this.regionDataController.endTask(this);
                    // read is cancelled - and no write pending, so we're done
                    return;
                }
                // if there is a write in progress, we don't actually have to worry about waiters gaining new entries -
                // the readers will just use the in progress write, so the value in canRead is good to use without
                // further synchronisation.
            }

            if (canRead[0]) {
                RegionDataController.ReadData readData = null;
                Throwable throwable = null;

                try {
                    readData = this.regionDataController.readData(this.chunkX, this.chunkZ);
                } catch (final Throwable thr) {
                    throwable = thr;
                    LOGGER.error("Failed to read chunk data for task: " + this.toString(), thr);
                }

                if (throwable != null) {
                    this.finishRead(null, throwable);
                } else {
                    switch (readData.result()) {
                        case NO_DATA:
                        case SYNC_READ: {
                            this.finishRead(readData.syncRead(), null);
                            break;
                        }
                        case HAS_DATA: {
                            this.readData = readData;
                            this.scheduleReadDecompress();
                            // read will handle write scheduling
                            return;
                        }
                        default: {
                            throw new IllegalStateException("Unknown state: " + readData.result());
                        }
                    }
                }
            }

            if (!this.tryAbortWrite()) {
                this.scheduleWriteCompress();
            }
        }

        private void scheduleReadDecompress() {
            final PrioritisedExecutor.PrioritisedTask task;
            synchronized (this) {
                task = this.regionDataController.compressionExecutor.createTask(this::performReadDecompress, this.priority);
                this.currentTask = task;
            }
            task.queue();
        }

        private void performReadDecompress() {
            final RegionDataController.ReadData readData = this.readData;
            this.readData = null;

            CompoundTag compoundTag = null;
            Throwable throwable = null;

            try {
                compoundTag = this.regionDataController.finishRead(this.chunkX, this.chunkZ, readData);
            } catch (final Throwable thr) {
                throwable = thr;
                LOGGER.error("Failed to decompress chunk data for task: " + this.toString(), thr);
            }

            if (compoundTag == null) {
                // need to re-try from the start
                this.scheduleReadIO();
                return;
            }

            this.finishRead(compoundTag, throwable);
            if (!this.tryAbortWrite()) {
                this.scheduleWriteCompress();
            }
        }

        private void finishRead(final CompoundTag compoundTag, final Throwable throwable) {
            this.inProgressRead.complete(this, compoundTag, throwable);
        }

        public void scheduleWriteCompress() {
            final InProgressWrite inProgressWrite = this.inProgressWrite;

            final PrioritisedExecutor.PrioritisedTask task;
            synchronized (this) {
                task = this.regionDataController.compressionExecutor.createTask(() -> {
                    ChunkIOTask.this.performWriteCompress(inProgressWrite);
                }, this.priority);
                this.currentTask = task;
            }

            inProgressWrite.addToWaiters(this, (final CompoundTag data, final Throwable throwable) -> {
                task.queue();
            });
        }

        private boolean tryAbortWrite() {
            final long chunkKey = CoordinateUtils.getChunkKey(this.chunkX, this.chunkZ);
            if (this.inProgressWrite == null) {
                final ChunkIOTask inMap = this.regionDataController.chunkTasks.compute(chunkKey, (final long keyInMap, final ChunkIOTask valueInMap) -> {
                    if (valueInMap == null) {
                        throw new IllegalStateException("Write completed concurrently, expected this task: " + ChunkIOTask.this.toString() + ", report this!");
                    }
                    if (valueInMap != ChunkIOTask.this) {
                        throw new IllegalStateException("Chunk task mismatch, expected this task: " + ChunkIOTask.this.toString() + ", got: " + valueInMap.toString() + ", report this!");
                    }

                    if (valueInMap.inProgressWrite != null) {
                        return valueInMap;
                    }

                    return null;
                });

                if (inMap == null) {
                    this.regionDataController.endTask(this);
                    return true; // set the task value to null, indicating we're done
                } // else: inProgressWrite changed, so now we have something to write
            }

            return false;
        }

        private void performWriteCompress(final InProgressWrite inProgressWrite) {
            final CompoundTag write = inProgressWrite.value;
            if (!inProgressWrite.isComplete()) {
                throw new IllegalStateException("Should be writable");
            }

            RegionDataController.WriteData writeData = null;
            boolean failedWrite = false;

            try {
                writeData = this.regionDataController.startWrite(this.chunkX, this.chunkZ, write);
            } catch (final Throwable thr) {
                // TODO implement this?
                    /*if (thr instanceof RegionFileStorage.RegionFileSizeException) {
                        final int maxSize = RegionFile.MAX_CHUNK_SIZE / (1024 * 1024);
                        LOGGER.error("Chunk at (" + this.chunkX + "," + this.chunkZ + ") in '" + WorldUtil.getWorldName(this.world) + "' exceeds max size of " + maxSize + "MiB, it has been deleted from disk.");
                    } else */
                {
                    failedWrite = thr instanceof IOException;
                    LOGGER.error("Failed to write chunk data for task: " + this.toString(), thr);
                }
            }

            if (writeData == null) {
                // null if a throwable was encountered

                // we cannot continue to the I/O stage here, so try to complete

                if (this.tryCompleteWrite(inProgressWrite, failedWrite)) {
                    return;
                } else {
                    // fetch new data and try again
                    this.scheduleWriteCompress();
                    return;
                }
            } else {
                // writeData != null && !failedWrite
                // we can continue to I/O stage
                this.writeData = writeData;
                this.scheduleWriteIO(inProgressWrite);
                return;
            }
        }

        private void scheduleWriteIO(final InProgressWrite inProgressWrite) {
            final PrioritisedExecutor.PrioritisedTask task;
            synchronized (this) {
                task = this.regionDataController.ioScheduler.createTask(this.chunkX, this.chunkZ, () -> {
                    ChunkIOTask.this.runWriteIO(inProgressWrite);
                }, this.priority);
                this.currentTask = task;
            }
            task.queue();
        }

        private void runWriteIO(final InProgressWrite inProgressWrite) {
            RegionDataController.WriteData writeData = this.writeData;
            this.writeData = null;

            boolean failedWrite = false;

            try {
                this.regionDataController.finishWrite(this.chunkX, this.chunkZ, writeData);
            } catch (final Throwable thr) {
                failedWrite = thr instanceof IOException;
                LOGGER.error("Failed to write chunk data for task: " + this.toString(), thr);
            }

            if (!this.tryCompleteWrite(inProgressWrite, failedWrite)) {
                // fetch new data and try again
                this.scheduleWriteCompress();
            }
            return;
        }

        private boolean tryCompleteWrite(final InProgressWrite written, final boolean failedWrite) {
            final long chunkKey = CoordinateUtils.getChunkKey(this.chunkX, this.chunkZ);

            final boolean[] done = new boolean[] { false };

            this.regionDataController.chunkTasks.compute(chunkKey, (final long keyInMap, final ChunkIOTask valueInMap) -> {
                if (valueInMap == null) {
                    throw new IllegalStateException("Write completed concurrently, expected this task: " + ChunkIOTask.this.toString() + ", report this!");
                }
                if (valueInMap != ChunkIOTask.this) {
                    throw new IllegalStateException("Chunk task mismatch, expected this task: " + ChunkIOTask.this.toString() + ", got: " + valueInMap.toString() + ", report this!");
                }
                if (valueInMap.inProgressWrite == written) {
                    valueInMap.failedWrite = failedWrite;
                    done[0] = true;
                    // keep the data in map if we failed the write so we can try to prevent data loss
                    return failedWrite ? valueInMap : null;
                }
                // different data than expected, means we need to retry write
                return valueInMap;
            });

            if (done[0]) {
                this.regionDataController.endTask(this);
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return "Task for world: '" + WorldUtil.getWorldName(this.world) + "' at (" + this.chunkX + ","
                    + this.chunkZ + ") type: " + this.regionDataController.type.name() + ", hash: " + this.hashCode();
        }

        private static final class InProgressRead {

            private static final Logger LOGGER = LoggerFactory.getLogger(InProgressRead.class);

            private CompoundTag value;
            private Throwable throwable;
            private final MultiThreadedQueue<BiConsumer<CompoundTag, Throwable>> callbacks = new MultiThreadedQueue<>();

            public boolean hasNoWaiters() {
                return this.callbacks.isEmpty();
            }

            public boolean addToAsyncWaiters(final BiConsumer<CompoundTag, Throwable> callback) {
                return this.callbacks.add(callback);
            }

            public boolean cancel(final BiConsumer<CompoundTag, Throwable> callback) {
                return this.callbacks.remove(callback);
            }

            public void complete(final ChunkIOTask task, final CompoundTag value, final Throwable throwable) {
                this.value = value;
                this.throwable = throwable;

                BiConsumer<CompoundTag, Throwable> consumer;
                while ((consumer = this.callbacks.pollOrBlockAdds()) != null) {
                    try {
                        consumer.accept(value == null ? null : value.copy(), throwable);
                    } catch (final Throwable thr) {
                        LOGGER.error("Callback " + ConcurrentUtil.genericToString(consumer) + " failed to handle chunk data (read) for task " + task.toString(), thr);
                    }
                }
            }
        }

        private static final class InProgressWrite {

            private static final Logger LOGGER = LoggerFactory.getLogger(InProgressWrite.class);

            private CompoundTag value;
            private Throwable throwable;
            private volatile boolean complete;
            private final MultiThreadedQueue<BiConsumer<CompoundTag, Throwable>> callbacks = new MultiThreadedQueue<>();

            private final PrioritisedExecutor.PrioritisedTask writeTask;

            public InProgressWrite(final PrioritisedExecutor.PrioritisedTask writeTask) {
                this.writeTask = writeTask;
            }

            public boolean isComplete() {
                return this.complete;
            }

            public void schedule(final ChunkIOTask task, final Consumer<BiConsumer<CompoundTag, Throwable>> scheduler) {
                scheduler.accept((final CompoundTag data, final Throwable throwable) -> {
                    InProgressWrite.this.complete(task, data, throwable);
                });
            }

            public boolean addToAsyncWaiters(final BiConsumer<CompoundTag, Throwable> callback) {
                return this.callbacks.add(callback);
            }

            public void addToWaiters(final ChunkIOTask task, final BiConsumer<CompoundTag, Throwable> consumer) {
                if (!this.callbacks.add(consumer)) {
                    this.syncAccept(task, consumer, this.value, this.throwable);
                }
            }

            private void syncAccept(final ChunkIOTask task, final BiConsumer<CompoundTag, Throwable> consumer, final CompoundTag value, final Throwable throwable) {
                try {
                    consumer.accept(value == null ? null : value.copy(), throwable);
                } catch (final Throwable thr) {
                    LOGGER.error("Callback " + ConcurrentUtil.genericToString(consumer) + " failed to handle chunk data (write) for task " + task.toString(), thr);
                }
            }

            public void complete(final ChunkIOTask task, final CompoundTag value, final Throwable throwable) {
                this.value = value;
                this.throwable = throwable;
                this.complete = true;

                task.pendingWriteComplete(this);

                BiConsumer<CompoundTag, Throwable> consumer;
                while ((consumer = this.callbacks.pollOrBlockAdds()) != null) {
                    this.syncAccept(task, consumer, value, throwable);
                }
            }

            public boolean cancel(final BiConsumer<CompoundTag, Throwable> callback) {
                return this.callbacks.remove(callback);
            }
        }
    }

    public static abstract class RegionDataController {

        public final RegionFileType type;
        private final PrioritisedExecutor compressionExecutor;
        private final IOScheduler ioScheduler;
        private final ConcurrentLong2ReferenceChainedHashTable<ChunkIOTask> chunkTasks = new ConcurrentLong2ReferenceChainedHashTable<>();

        private final AtomicLong inProgressTasks = new AtomicLong();

        public RegionDataController(final RegionFileType type, final PrioritisedExecutor ioExecutor,
                                    final PrioritisedExecutor compressionExecutor) {
            this.type = type;
            this.compressionExecutor = compressionExecutor;
            this.ioScheduler = new IOScheduler(ioExecutor);
        }

        final void startTask(final ChunkIOTask task) {
            this.inProgressTasks.getAndIncrement();
        }

        final void endTask(final ChunkIOTask task) {
            this.inProgressTasks.getAndDecrement();
        }

        public boolean hasTasks() {
            return this.inProgressTasks.get() != 0L;
        }

        public long getTotalWorkingTasks() {
            return this.inProgressTasks.get();
        }

        public abstract RegionFileStorage getCache();

        public static record WriteData(CompoundTag input, WriteResult result, DataOutputStream output, IORunnable write) {
            public static enum WriteResult {
                WRITE,
                DELETE;
            }
        }

        public abstract WriteData startWrite(final int chunkX, final int chunkZ, final CompoundTag compound) throws IOException;

        public abstract void finishWrite(final int chunkX, final int chunkZ, final WriteData writeData) throws IOException;

        public static record ReadData(ReadResult result, DataInputStream input, CompoundTag syncRead) {
            public static enum ReadResult {
                NO_DATA,
                HAS_DATA,
                SYNC_READ;
            }
        }

        public abstract ReadData readData(final int chunkX, final int chunkZ) throws IOException;

        // if the return value is null, then the caller needs to re-try with a new call to readData()
        public abstract CompoundTag finishRead(final int chunkX, final int chunkZ, final ReadData readData) throws IOException;

        public static interface IORunnable {

            public void run(final RegionFile regionFile) throws IOException;

        }
    }

    private static final class IOScheduler {

        private final ConcurrentLong2ReferenceChainedHashTable<RegionIOTasks> regionTasks = new ConcurrentLong2ReferenceChainedHashTable<>();
        private final PrioritisedExecutor executor;

        public IOScheduler(final PrioritisedExecutor executor) {
            this.executor = executor;
        }

        public PrioritisedExecutor.PrioritisedTask createTask(final int chunkX, final int chunkZ,
                                                              final Runnable run, final Priority priority) {
            final PrioritisedExecutor.PrioritisedTask[] ret = new PrioritisedExecutor.PrioritisedTask[1];
            final long subOrder = this.executor.generateNextSubOrder();
            this.regionTasks.compute(CoordinateUtils.getChunkKey(chunkX >> REGION_FILE_SHIFT, chunkZ >> REGION_FILE_SHIFT),
                    (final long regionKey, final RegionIOTasks existing) -> {
                final RegionIOTasks res;
                if (existing != null) {
                    res = existing;
                } else {
                    res = new RegionIOTasks(regionKey, IOScheduler.this);
                }

                ret[0] = res.createTask(run, priority, subOrder);

                return res;
            });

            return ret[0];
        }
    }

    private static final class RegionIOTasks implements Runnable {

        private static final Logger LOGGER = LoggerFactory.getLogger(RegionIOTasks.class);

        private final PrioritisedTaskQueue queue = new PrioritisedTaskQueue();
        private final long regionKey;
        private final IOScheduler ioScheduler;
        private long createdTasks;
        private long executedTasks;

        private PrioritisedExecutor.PrioritisedTask task;

        public RegionIOTasks(final long regionKey, final IOScheduler ioScheduler) {
            this.regionKey = regionKey;
            this.ioScheduler = ioScheduler;
        }

        public PrioritisedExecutor.PrioritisedTask createTask(final Runnable run, final Priority priority,
                                                              final long subOrder) {
            ++this.createdTasks;
            return new WrappedTask(this.queue.createTask(run, priority, subOrder));
        }

        private void adjustTaskPriority() {
            final PrioritisedTaskQueue.PrioritySubOrderPair priority = this.queue.getHighestPrioritySubOrder();
            if (this.task == null) {
                if (priority == null) {
                    return;
                }
                this.task = this.ioScheduler.executor.createTask(this, priority.priority(), priority.subOrder());
                this.task.queue();
            } else {
                if (priority == null) {
                    throw new IllegalStateException();
                } else {
                    this.task.setPriorityAndSubOrder(priority.priority(), priority.subOrder());
                }
            }
        }

        @Override
        public void run() {
            final Runnable run;
            synchronized (this) {
                run = this.queue.pollTask();
            }

            try {
                run.run();
            } finally {
                synchronized (this) {
                    this.task = null;
                    this.adjustTaskPriority();
                }
                this.ioScheduler.regionTasks.compute(this.regionKey, (final long keyInMap, final RegionIOTasks tasks) -> {
                    if (tasks != RegionIOTasks.this) {
                        throw new IllegalStateException("Region task mismatch");
                    }
                    ++tasks.executedTasks;
                    if (tasks.createdTasks != tasks.executedTasks) {
                        return tasks;
                    }

                    if (tasks.task != null) {
                        throw new IllegalStateException("Task may not be null when created==executed");
                    }

                    return null;
                });
            }
        }

        private final class WrappedTask implements PrioritisedExecutor.PrioritisedTask {

            private final PrioritisedExecutor.PrioritisedTask wrapped;

            public WrappedTask(final PrioritisedExecutor.PrioritisedTask wrap) {
                this.wrapped = wrap;
            }

            @Override
            public PrioritisedExecutor getExecutor() {
                return RegionIOTasks.this.ioScheduler.executor;
            }

            @Override
            public boolean queue() {
                synchronized (RegionIOTasks.this) {
                    if (this.wrapped.queue()) {
                        RegionIOTasks.this.adjustTaskPriority();
                        return true;
                    }
                    return false;
                }
            }

            @Override
            public boolean isQueued() {
                return this.wrapped.isQueued();
            }

            @Override
            public boolean cancel() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean execute() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Priority getPriority() {
                return this.wrapped.getPriority();
            }

            @Override
            public boolean setPriority(final Priority priority) {
                synchronized (RegionIOTasks.this) {
                    if (this.wrapped.setPriority(priority) && this.wrapped.isQueued()) {
                        RegionIOTasks.this.adjustTaskPriority();
                        return true;
                    }
                    return false;
                }
            }

            @Override
            public boolean raisePriority(final Priority priority) {
                synchronized (RegionIOTasks.this) {
                    if (this.wrapped.raisePriority(priority) && this.wrapped.isQueued()) {
                        RegionIOTasks.this.adjustTaskPriority();
                        return true;
                    }
                    return false;
                }
            }

            @Override
            public boolean lowerPriority(final Priority priority) {
                synchronized (RegionIOTasks.this) {
                    if (this.wrapped.lowerPriority(priority) && this.wrapped.isQueued()) {
                        RegionIOTasks.this.adjustTaskPriority();
                        return true;
                    }
                    return false;
                }
            }

            @Override
            public long getSubOrder() {
                return this.wrapped.getSubOrder();
            }

            @Override
            public boolean setSubOrder(final long subOrder) {
                synchronized (RegionIOTasks.this) {
                    if (this.wrapped.setSubOrder(subOrder) && this.wrapped.isQueued()) {
                        RegionIOTasks.this.adjustTaskPriority();
                        return true;
                    }
                    return false;
                }
            }

            @Override
            public boolean raiseSubOrder(final long subOrder) {
                synchronized (RegionIOTasks.this) {
                    if (this.wrapped.raiseSubOrder(subOrder) && this.wrapped.isQueued()) {
                        RegionIOTasks.this.adjustTaskPriority();
                        return true;
                    }
                    return false;
                }
            }

            @Override
            public boolean lowerSubOrder(final long subOrder) {
                synchronized (RegionIOTasks.this) {
                    if (this.wrapped.lowerSubOrder(subOrder) && this.wrapped.isQueued()) {
                        RegionIOTasks.this.adjustTaskPriority();
                        return true;
                    }
                    return false;
                }
            }

            @Override
            public boolean setPriorityAndSubOrder(final Priority priority, final long subOrder) {
                synchronized (RegionIOTasks.this) {
                    if (this.wrapped.setPriorityAndSubOrder(priority, subOrder) && this.wrapped.isQueued()) {
                        RegionIOTasks.this.adjustTaskPriority();
                        return true;
                    }
                    return false;
                }
            }
        }
    }
}
