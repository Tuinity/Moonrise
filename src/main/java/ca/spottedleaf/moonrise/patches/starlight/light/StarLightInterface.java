package ca.spottedleaf.moonrise.patches.starlight.light;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkStatus;
import ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortCollection;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class StarLightInterface {

    public static final TicketType<Long> CHUNK_WORK_TICKET = TicketType.create("starlight:chunk_work_ticket", Long::compareTo);
    public static final int LIGHT_TICKET_LEVEL = ChunkLevel.byStatus(ChunkStatus.LIGHT);
    // ticket level = ChunkLevel.byStatus(FullChunkStatus.FULL) - input
    public static final int REGION_LIGHT_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.FULL) - LIGHT_TICKET_LEVEL;

    /**
     * Can be {@code null}, indicating the light is all empty.
     */
    public final Level world;
    public final LightChunkGetter lightAccess;

    private final ArrayDeque<SkyStarLightEngine> cachedSkyPropagators;
    private final ArrayDeque<BlockStarLightEngine> cachedBlockPropagators;

    private final LightQueue lightQueue;

    private final LayerLightEventListener skyReader;
    private final LayerLightEventListener blockReader;
    private final boolean isClientSide;

    public final int minSection;
    public final int maxSection;
    public final int minLightSection;
    public final int maxLightSection;

    public final LevelLightEngine lightEngine;

    private final boolean hasBlockLight;
    private final boolean hasSkyLight;

    public StarLightInterface(final LightChunkGetter lightAccess, final boolean hasSkyLight, final boolean hasBlockLight, final LevelLightEngine lightEngine) {
        this.lightAccess = lightAccess;
        this.world = lightAccess == null ? null : (Level)lightAccess.getLevel();
        this.cachedSkyPropagators = hasSkyLight && lightAccess != null ? new ArrayDeque<>() : null;
        this.cachedBlockPropagators = hasBlockLight && lightAccess != null ? new ArrayDeque<>() : null;
        this.isClientSide = !(this.world instanceof ServerLevel);
        if (this.world == null) {
            this.minSection = -4;
            this.maxSection = 19;
            this.minLightSection = -5;
            this.maxLightSection = 20;
        } else {
            this.minSection = WorldUtil.getMinSection(this.world);
            this.maxSection = WorldUtil.getMaxSection(this.world);
            this.minLightSection = WorldUtil.getMinLightSection(this.world);
            this.maxLightSection = WorldUtil.getMaxLightSection(this.world);
        }

        if (this.world instanceof ServerLevel) {
            this.lightQueue = new ServerLightQueue(this);
        } else {
            this.lightQueue = new ClientLightQueue(this);
        }

        this.lightEngine = lightEngine;
        this.hasBlockLight = hasBlockLight;
        this.hasSkyLight = hasSkyLight;
        this.skyReader = !hasSkyLight ? LayerLightEventListener.DummyLightLayerEventListener.INSTANCE : new LayerLightEventListener() {
            @Override
            public void checkBlock(final BlockPos blockPos) {
                StarLightInterface.this.lightEngine.checkBlock(blockPos.immutable());
            }

            @Override
            public void propagateLightSources(final ChunkPos chunkPos) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean hasLightWork() {
                // not really correct...
                return StarLightInterface.this.hasUpdates();
            }

            @Override
            public int runLightUpdates() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setLightEnabled(final ChunkPos chunkPos, final boolean bl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public DataLayer getDataLayerData(final SectionPos pos) {
                final ChunkAccess chunk = StarLightInterface.this.getAnyChunkNow(pos.getX(), pos.getZ());
                if (chunk == null || (!StarLightInterface.this.isClientSide && !chunk.isLightCorrect()) || !chunk.getPersistedStatus().isOrAfter(ChunkStatus.LIGHT)) {
                    return null;
                }

                final int sectionY = pos.getY();

                if (sectionY > StarLightInterface.this.maxLightSection || sectionY < StarLightInterface.this.minLightSection) {
                    return null;
                }

                if (((StarlightChunk)chunk).starlight$getSkyEmptinessMap() == null) {
                    return null;
                }

                return ((StarlightChunk)chunk).starlight$getSkyNibbles()[sectionY - StarLightInterface.this.minLightSection].toVanillaNibble();
            }

            @Override
            public int getLightValue(final BlockPos blockPos) {
                return StarLightInterface.this.getSkyLightValue(blockPos, StarLightInterface.this.getAnyChunkNow(blockPos.getX() >> 4, blockPos.getZ() >> 4));
            }

            @Override
            public void updateSectionStatus(final SectionPos pos, final boolean notReady) {
                StarLightInterface.this.sectionChange(pos, notReady);
            }
        };
        this.blockReader = !hasBlockLight ? LayerLightEventListener.DummyLightLayerEventListener.INSTANCE : new LayerLightEventListener() {
            @Override
            public void checkBlock(final BlockPos blockPos) {
                StarLightInterface.this.lightEngine.checkBlock(blockPos.immutable());
            }

            @Override
            public void propagateLightSources(final ChunkPos chunkPos) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean hasLightWork() {
                // not really correct...
                return StarLightInterface.this.hasUpdates();
            }

            @Override
            public int runLightUpdates() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setLightEnabled(final ChunkPos chunkPos, final boolean bl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public DataLayer getDataLayerData(final SectionPos pos) {
                final ChunkAccess chunk = StarLightInterface.this.getAnyChunkNow(pos.getX(), pos.getZ());

                if (chunk == null || pos.getY() < StarLightInterface.this.minLightSection || pos.getY() > StarLightInterface.this.maxLightSection) {
                    return null;
                }

                return ((StarlightChunk)chunk).starlight$getBlockNibbles()[pos.getY() - StarLightInterface.this.minLightSection].toVanillaNibble();
            }

            @Override
            public int getLightValue(final BlockPos blockPos) {
                return StarLightInterface.this.getBlockLightValue(blockPos, StarLightInterface.this.getAnyChunkNow(blockPos.getX() >> 4, blockPos.getZ() >> 4));
            }

            @Override
            public void updateSectionStatus(final SectionPos pos, final boolean notReady) {
                StarLightInterface.this.sectionChange(pos, notReady);
            }
        };
    }

    public ClientLightQueue getClientLightQueue() {
        if (this.lightQueue instanceof ClientLightQueue clientLightQueue) {
            return clientLightQueue;
        }
        return null;
    }

    public ServerLightQueue getServerLightQueue() {
        if (this.lightQueue instanceof ServerLightQueue serverLightQueue) {
            return serverLightQueue;
        }
        return null;
    }

    public boolean hasSkyLight() {
        return this.hasSkyLight;
    }

    public boolean hasBlockLight() {
        return this.hasBlockLight;
    }

    public int getSkyLightValue(final BlockPos blockPos, final ChunkAccess chunk) {
        if (!this.hasSkyLight) {
            return 0;
        }
        final int x = blockPos.getX();
        int y = blockPos.getY();
        final int z = blockPos.getZ();

        final int minSection = this.minSection;
        final int maxSection = this.maxSection;
        final int minLightSection = this.minLightSection;
        final int maxLightSection = this.maxLightSection;

        if (chunk == null || (!this.isClientSide && !chunk.isLightCorrect()) || !chunk.getPersistedStatus().isOrAfter(ChunkStatus.LIGHT)) {
            return 15;
        }

        int sectionY = y >> 4;

        if (sectionY > maxLightSection) {
            return 15;
        }

        if (sectionY < minLightSection) {
            sectionY = minLightSection;
            y = sectionY << 4;
        }

        final SWMRNibbleArray[] nibbles = ((StarlightChunk)chunk).starlight$getSkyNibbles();
        final SWMRNibbleArray immediate = nibbles[sectionY - minLightSection];

        if (!immediate.isNullNibbleVisible()) {
            return immediate.getVisible(x, y, z);
        }

        final boolean[] emptinessMap = ((StarlightChunk)chunk).starlight$getSkyEmptinessMap();

        if (emptinessMap == null) {
            return 15;
        }

        // are we above this chunk's lowest empty section?
        int lowestY = minLightSection - 1;
        for (int currY = maxSection; currY >= minSection; --currY) {
            if (emptinessMap[currY - minSection]) {
                continue;
            }

            // should always be full lit here
            lowestY = currY;
            break;
        }

        if (sectionY > lowestY) {
            return 15;
        }

        // this nibble is going to depend solely on the skylight data above it
        // find first non-null data above (there does exist one, as we just found it above)
        for (int currY = sectionY + 1; currY <= maxLightSection; ++currY) {
            final SWMRNibbleArray nibble = nibbles[currY - minLightSection];
            if (!nibble.isNullNibbleVisible()) {
                return nibble.getVisible(x, 0, z);
            }
        }

        // should never reach here
        return 15;
    }

    public int getBlockLightValue(final BlockPos blockPos, final ChunkAccess chunk) {
        if (!this.hasBlockLight) {
            return 0;
        }
        final int y = blockPos.getY();
        final int cy = y >> 4;

        final int minLightSection = this.minLightSection;
        final int maxLightSection = this.maxLightSection;

        if (cy < minLightSection || cy > maxLightSection) {
            return 0;
        }

        if (chunk == null) {
            return 0;
        }

        final SWMRNibbleArray nibble = ((StarlightChunk)chunk).starlight$getBlockNibbles()[cy - minLightSection];
        return nibble.getVisible(blockPos.getX(), y, blockPos.getZ());
    }

    public int getRawBrightness(final BlockPos pos, final int ambientDarkness) {
        final ChunkAccess chunk = this.getAnyChunkNow(pos.getX() >> 4, pos.getZ() >> 4);

        final int sky = this.getSkyLightValue(pos, chunk) - ambientDarkness;
        // Don't fetch the block light level if the skylight level is 15, since the value will never be higher.
        if (sky == 15) {
            return 15;
        }
        final int block = this.getBlockLightValue(pos, chunk);
        return Math.max(sky, block);
    }

    public LayerLightEventListener getSkyReader() {
        return this.skyReader;
    }

    public LayerLightEventListener getBlockReader() {
        return this.blockReader;
    }

    public boolean isClientSide() {
        return this.isClientSide;
    }

    public ChunkAccess getAnyChunkNow(final int chunkX, final int chunkZ) {
        if (this.world == null) {
            // empty world
            return null;
        }
        return ((ChunkSystemLevel)this.world).moonrise$getAnyChunkIfLoaded(chunkX, chunkZ);
    }

    public boolean hasUpdates() {
        return !this.lightQueue.isEmpty();
    }

    public Level getWorld() {
        return this.world;
    }

    public LightChunkGetter getLightAccess() {
        return this.lightAccess;
    }

    public SkyStarLightEngine getSkyLightEngine() {
        if (this.cachedSkyPropagators == null) {
            return null;
        }
        final SkyStarLightEngine ret;
        synchronized (this.cachedSkyPropagators) {
            ret = this.cachedSkyPropagators.pollFirst();
        }

        if (ret == null) {
            return new SkyStarLightEngine(this.world);
        }
        return ret;
    }

    public void releaseSkyLightEngine(final SkyStarLightEngine engine) {
        if (this.cachedSkyPropagators == null) {
            return;
        }
        synchronized (this.cachedSkyPropagators) {
            this.cachedSkyPropagators.addFirst(engine);
        }
    }

    public BlockStarLightEngine getBlockLightEngine() {
        if (this.cachedBlockPropagators == null) {
            return null;
        }
        final BlockStarLightEngine ret;
        synchronized (this.cachedBlockPropagators) {
            ret = this.cachedBlockPropagators.pollFirst();
        }

        if (ret == null) {
            return new BlockStarLightEngine(this.world);
        }
        return ret;
    }

    public void releaseBlockLightEngine(final BlockStarLightEngine engine) {
        if (this.cachedBlockPropagators == null) {
            return;
        }
        synchronized (this.cachedBlockPropagators) {
            this.cachedBlockPropagators.addFirst(engine);
        }
    }

    public LightQueue.ChunkTasks blockChange(final BlockPos pos) {
        if (this.world == null || pos.getY() < WorldUtil.getMinBlockY(this.world) || pos.getY() > WorldUtil.getMaxBlockY(this.world)) { // empty world
            return null;
        }

        return this.lightQueue.queueBlockChange(pos);
    }

    public LightQueue.ChunkTasks sectionChange(final SectionPos pos, final boolean newEmptyValue) {
        if (this.world == null) { // empty world
            return null;
        }

        return this.lightQueue.queueSectionChange(pos, newEmptyValue);
    }

    public void forceLoadInChunk(final ChunkAccess chunk, final Boolean[] emptySections) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.forceHandleEmptySectionChanges(this.lightAccess, chunk, emptySections);
            }
            if (blockEngine != null) {
                blockEngine.forceHandleEmptySectionChanges(this.lightAccess, chunk, emptySections);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void loadInChunk(final int chunkX, final int chunkZ, final Boolean[] emptySections) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.handleEmptySectionChanges(this.lightAccess, chunkX, chunkZ, emptySections);
            }
            if (blockEngine != null) {
                blockEngine.handleEmptySectionChanges(this.lightAccess, chunkX, chunkZ, emptySections);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void lightChunk(final ChunkAccess chunk, final Boolean[] emptySections) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.light(this.lightAccess, chunk, emptySections);
            }
            if (blockEngine != null) {
                blockEngine.light(this.lightAccess, chunk, emptySections);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void relightChunks(final Set<ChunkPos> chunks, final Consumer<ChunkPos> chunkLightCallback,
                              final IntConsumer onComplete) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.relightChunks(this.lightAccess, chunks, blockEngine == null ? chunkLightCallback : null,
                        blockEngine == null ? onComplete : null);
            }
            if (blockEngine != null) {
                blockEngine.relightChunks(this.lightAccess, chunks, chunkLightCallback, onComplete);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void checkChunkEdges(final int chunkX, final int chunkZ) {
        this.checkSkyEdges(chunkX, chunkZ);
        this.checkBlockEdges(chunkX, chunkZ);
    }

    public void checkSkyEdges(final int chunkX, final int chunkZ) {
        final SkyStarLightEngine skyEngine = this.getSkyLightEngine();

        try {
            if (skyEngine != null) {
                skyEngine.checkChunkEdges(this.lightAccess, chunkX, chunkZ);
            }
        } finally {
            this.releaseSkyLightEngine(skyEngine);
        }
    }

    public void checkBlockEdges(final int chunkX, final int chunkZ) {
        final BlockStarLightEngine blockEngine = this.getBlockLightEngine();
        try {
            if (blockEngine != null) {
                blockEngine.checkChunkEdges(this.lightAccess, chunkX, chunkZ);
            }
        } finally {
            this.releaseBlockLightEngine(blockEngine);
        }
    }

    public void propagateChanges() {
        final LightQueue lightQueue = this.lightQueue;
        if (lightQueue instanceof ClientLightQueue clientLightQueue) {
            clientLightQueue.drainTasks();
        } // else: invalid usage, although we won't throw because mods...
    }

    public static abstract class LightQueue {

        protected final StarLightInterface lightInterface;

        public LightQueue(final StarLightInterface lightInterface) {
            this.lightInterface = lightInterface;
        }

        public abstract boolean isEmpty();

        public abstract ChunkTasks queueBlockChange(final BlockPos pos);

        public abstract ChunkTasks queueSectionChange(final SectionPos pos, final boolean newEmptyValue);

        public abstract ChunkTasks queueChunkSkylightEdgeCheck(final SectionPos pos, final ShortCollection sections);

        public abstract ChunkTasks queueChunkBlocklightEdgeCheck(final SectionPos pos, final ShortCollection sections);

        public static abstract class ChunkTasks implements Runnable {

            public final long chunkCoordinate;

            protected final StarLightInterface lightEngine;
            protected final LightQueue queue;
            protected final MultiThreadedQueue<Runnable> onComplete = new MultiThreadedQueue<>();
            protected final Set<BlockPos> changedPositions = new HashSet<>();
            protected Boolean[] changedSectionSet;
            protected ShortOpenHashSet queuedEdgeChecksSky;
            protected ShortOpenHashSet queuedEdgeChecksBlock;
            protected List<BooleanSupplier> lightTasks;

            public ChunkTasks(final long chunkCoordinate, final StarLightInterface lightEngine, final LightQueue queue) {
                this.chunkCoordinate = chunkCoordinate;
                this.lightEngine = lightEngine;
                this.queue = queue;
            }

            @Override
            public abstract void run();

            public void queueOrRunTask(final Runnable run) {
                if (!this.onComplete.add(run)) {
                    run.run();
                }
            }

            protected void addChangedPosition(final BlockPos pos) {
                this.changedPositions.add(pos.immutable());
            }

            protected void setChangedSection(final int y, final Boolean newEmptyValue) {
                if (this.changedSectionSet == null) {
                    this.changedSectionSet = new Boolean[this.lightEngine.maxSection - this.lightEngine.minSection + 1];
                }
                this.changedSectionSet[y - this.lightEngine.minSection] = newEmptyValue;
            }

            protected void addLightTask(final BooleanSupplier lightTask) {
                if (this.lightTasks == null) {
                    this.lightTasks = new ArrayList<>();
                }
                this.lightTasks.add(lightTask);
            }

            protected void addEdgeChecksSky(final ShortCollection values) {
                if (this.queuedEdgeChecksSky == null) {
                    this.queuedEdgeChecksSky = new ShortOpenHashSet(Math.max(8, values.size()));
                }
                this.queuedEdgeChecksSky.addAll(values);
            }

            protected void addEdgeChecksBlock(final ShortCollection values) {
                if (this.queuedEdgeChecksBlock == null) {
                    this.queuedEdgeChecksBlock = new ShortOpenHashSet(Math.max(8, values.size()));
                }
                this.queuedEdgeChecksBlock.addAll(values);
            }

            protected final void runTasks() {
                boolean litChunk = false;
                if (this.lightTasks != null) {
                    for (final BooleanSupplier run : this.lightTasks) {
                        if (run.getAsBoolean()) {
                            litChunk = true;
                            break;
                        }
                    }
                }

                if (!litChunk) {
                    final SkyStarLightEngine skyEngine = this.lightEngine.getSkyLightEngine();
                    final BlockStarLightEngine blockEngine = this.lightEngine.getBlockLightEngine();
                    try {
                        final long coordinate = this.chunkCoordinate;
                        final int chunkX = CoordinateUtils.getChunkX(coordinate);
                        final int chunkZ = CoordinateUtils.getChunkZ(coordinate);

                        final Set<BlockPos> positions = this.changedPositions;
                        final Boolean[] sectionChanges = this.changedSectionSet;

                        if (skyEngine != null && (!positions.isEmpty() || sectionChanges != null)) {
                            skyEngine.blocksChangedInChunk(this.lightEngine.getLightAccess(), chunkX, chunkZ, positions, sectionChanges);
                        }
                        if (blockEngine != null && (!positions.isEmpty() || sectionChanges != null)) {
                            blockEngine.blocksChangedInChunk(this.lightEngine.getLightAccess(), chunkX, chunkZ, positions, sectionChanges);
                        }

                        if (skyEngine != null && this.queuedEdgeChecksSky != null) {
                            skyEngine.checkChunkEdges(this.lightEngine.getLightAccess(), chunkX, chunkZ, this.queuedEdgeChecksSky);
                        }
                        if (blockEngine != null && this.queuedEdgeChecksBlock != null) {
                            blockEngine.checkChunkEdges(this.lightEngine.getLightAccess(), chunkX, chunkZ, this.queuedEdgeChecksBlock);
                        }
                    } finally {
                        this.lightEngine.releaseSkyLightEngine(skyEngine);
                        this.lightEngine.releaseBlockLightEngine(blockEngine);
                    }
                }

                Runnable run;
                while ((run = this.onComplete.pollOrBlockAdds()) != null) {
                    run.run();
                }
            }
        }
    }

    public static final class ClientLightQueue extends LightQueue {

        private final Long2ObjectLinkedOpenHashMap<ClientChunkTasks> chunkTasks = new Long2ObjectLinkedOpenHashMap<>();

        public ClientLightQueue(final StarLightInterface lightInterface) {
            super(lightInterface);
        }

        @Override
        public synchronized boolean isEmpty() {
            return this.chunkTasks.isEmpty();
        }

        // must hold synchronized lock on this object
        private ClientChunkTasks getOrCreate(final long key) {
            return this.chunkTasks.computeIfAbsent(key, (final long keyInMap) -> {
                return new ClientChunkTasks(keyInMap, ClientLightQueue.this.lightInterface, ClientLightQueue.this);
            });
        }

        @Override
        public synchronized ClientChunkTasks queueBlockChange(final BlockPos pos) {
            final ClientChunkTasks tasks = this.getOrCreate(CoordinateUtils.getChunkKey(pos));
            tasks.addChangedPosition(pos);
            return tasks;
        }

        @Override
        public synchronized ClientChunkTasks queueSectionChange(final SectionPos pos, final boolean newEmptyValue) {
            final ClientChunkTasks tasks = this.getOrCreate(CoordinateUtils.getChunkKey(pos));

            tasks.setChangedSection(pos.getY(), Boolean.valueOf(newEmptyValue));

            return tasks;
        }

        @Override
        public synchronized ClientChunkTasks queueChunkSkylightEdgeCheck(final SectionPos pos, final ShortCollection sections) {
            final ClientChunkTasks tasks = this.getOrCreate(CoordinateUtils.getChunkKey(pos));

            tasks.addEdgeChecksSky(sections);

            return tasks;
        }

        @Override
        public synchronized ClientChunkTasks queueChunkBlocklightEdgeCheck(final SectionPos pos, final ShortCollection sections) {
            final ClientChunkTasks tasks = this.getOrCreate(CoordinateUtils.getChunkKey(pos));

            tasks.addEdgeChecksBlock(sections);

            return tasks;
        }

        public synchronized ClientChunkTasks removeFirstTask() {
            if (this.chunkTasks.isEmpty()) {
                return null;
            }
            return this.chunkTasks.removeFirst();
        }

        public void drainTasks() {
            ClientChunkTasks task;
            while ((task = this.removeFirstTask()) != null) {
                task.runTasks();
            }
        }

        public static final class ClientChunkTasks extends ChunkTasks {

            public ClientChunkTasks(final long chunkCoordinate, final StarLightInterface lightEngine, final ClientLightQueue queue) {
                super(chunkCoordinate, lightEngine, queue);
            }

            @Override
            public void run() {
                this.runTasks();
            }
        }
    }

    public static final class ServerLightQueue extends LightQueue {

        private final ConcurrentLong2ReferenceChainedHashTable<ServerChunkTasks> chunkTasks = new ConcurrentLong2ReferenceChainedHashTable<>();

        public ServerLightQueue(final StarLightInterface lightInterface) {
            super(lightInterface);
        }

        public void lowerPriority(final int chunkX, final int chunkZ, final PrioritisedExecutor.Priority priority) {
            final ServerChunkTasks task = this.chunkTasks.get(CoordinateUtils.getChunkKey(chunkX, chunkZ));
            if (task != null) {
                task.lowerPriority(priority);
            }
        }

        public void setPriority(final int chunkX, final int chunkZ, final PrioritisedExecutor.Priority priority) {
            final ServerChunkTasks task = this.chunkTasks.get(CoordinateUtils.getChunkKey(chunkX, chunkZ));
            if (task != null) {
                task.setPriority(priority);
            }
        }

        public void raisePriority(final int chunkX, final int chunkZ, final PrioritisedExecutor.Priority priority) {
            final ServerChunkTasks task = this.chunkTasks.get(CoordinateUtils.getChunkKey(chunkX, chunkZ));
            if (task != null) {
                task.raisePriority(priority);
            }
        }

        public PrioritisedExecutor.Priority getPriority(final int chunkX, final int chunkZ) {
            final ServerChunkTasks task = this.chunkTasks.get(CoordinateUtils.getChunkKey(chunkX, chunkZ));
            if (task != null) {
                return task.getPriority();
            }

            return PrioritisedExecutor.Priority.COMPLETING;
        }

        @Override
        public boolean isEmpty() {
            return this.chunkTasks.isEmpty();
        }

        @Override
        public ServerChunkTasks queueBlockChange(final BlockPos pos) {
            final ServerChunkTasks ret = this.chunkTasks.compute(CoordinateUtils.getChunkKey(pos), (final long keyInMap, ServerChunkTasks valueInMap) -> {
                if (valueInMap == null) {
                    valueInMap = new ServerChunkTasks(
                            keyInMap, ServerLightQueue.this.lightInterface, ServerLightQueue.this
                    );
                }
                valueInMap.addChangedPosition(pos);
                return valueInMap;
            });

            ret.schedule();

            return ret;
        }

        @Override
        public ServerChunkTasks queueSectionChange(final SectionPos pos, final boolean newEmptyValue) {
            final ServerChunkTasks ret = this.chunkTasks.compute(CoordinateUtils.getChunkKey(pos), (final long keyInMap, ServerChunkTasks valueInMap) -> {
                if (valueInMap == null) {
                    valueInMap = new ServerChunkTasks(
                            keyInMap, ServerLightQueue.this.lightInterface, ServerLightQueue.this
                    );
                }

                valueInMap.setChangedSection(pos.getY(), Boolean.valueOf(newEmptyValue));

                return valueInMap;
            });

            ret.schedule();

            return ret;
        }

        public ServerChunkTasks queueChunkLightTask(final ChunkPos pos, final BooleanSupplier lightTask, final PrioritisedExecutor.Priority priority) {
            final ServerChunkTasks ret = this.chunkTasks.compute(CoordinateUtils.getChunkKey(pos), (final long keyInMap, ServerChunkTasks valueInMap) -> {
                if (valueInMap == null) {
                    valueInMap = new ServerChunkTasks(
                            keyInMap, ServerLightQueue.this.lightInterface, ServerLightQueue.this, priority
                    );
                }

                valueInMap.addLightTask(lightTask);

                return valueInMap;
            });

            ret.schedule();

            return ret;
        }

        @Override
        public ServerChunkTasks queueChunkSkylightEdgeCheck(final SectionPos pos, final ShortCollection sections) {
            final ServerChunkTasks ret = this.chunkTasks.compute(CoordinateUtils.getChunkKey(pos), (final long keyInMap, ServerChunkTasks valueInMap) -> {
                if (valueInMap == null) {
                    valueInMap = new ServerChunkTasks(
                            keyInMap, ServerLightQueue.this.lightInterface, ServerLightQueue.this
                    );
                }

                valueInMap.addEdgeChecksSky(sections);

                return valueInMap;
            });

            ret.schedule();

            return ret;
        }

        @Override
        public ServerChunkTasks queueChunkBlocklightEdgeCheck(final SectionPos pos, final ShortCollection sections) {
            final ServerChunkTasks ret = this.chunkTasks.compute(CoordinateUtils.getChunkKey(pos), (final long keyInMap, ServerChunkTasks valueInMap) -> {
                if (valueInMap == null) {
                    valueInMap = new ServerChunkTasks(
                            keyInMap, ServerLightQueue.this.lightInterface, ServerLightQueue.this
                    );
                }

                valueInMap.addEdgeChecksBlock(sections);

                return valueInMap;
            });

            ret.schedule();

            return ret;
        }

        public static final class ServerChunkTasks extends ChunkTasks {

            private final AtomicBoolean ticketAdded = new AtomicBoolean();
            private final PrioritisedExecutor.PrioritisedTask task;

            public ServerChunkTasks(final long chunkCoordinate, final StarLightInterface lightEngine,
                                    final ServerLightQueue queue) {
                this(chunkCoordinate, lightEngine, queue, PrioritisedExecutor.Priority.NORMAL);
            }

            public ServerChunkTasks(final long chunkCoordinate, final StarLightInterface lightEngine,
                                    final ServerLightQueue queue, final PrioritisedExecutor.Priority priority) {
                super(chunkCoordinate, lightEngine, queue);
                this.task = ((ChunkSystemServerLevel)(ServerLevel)lightEngine.getWorld()).moonrise$getChunkTaskScheduler().radiusAwareScheduler.createTask(
                        CoordinateUtils.getChunkX(chunkCoordinate), CoordinateUtils.getChunkZ(chunkCoordinate),
                        ((ChunkSystemChunkStatus)ChunkStatus.LIGHT).moonrise$getWriteRadius(), this, priority
                );
            }

            public boolean markTicketAdded() {
                return !this.ticketAdded.get() && !this.ticketAdded.getAndSet(true);
            }

            public void schedule() {
                this.task.queue();
            }

            public boolean cancel() {
                return this.task.cancel();
            }

            public PrioritisedExecutor.Priority getPriority() {
                return this.task.getPriority();
            }

            public void lowerPriority(final PrioritisedExecutor.Priority priority) {
                this.task.lowerPriority(priority);
            }

            public void setPriority(final PrioritisedExecutor.Priority priority) {
                this.task.setPriority(priority);
            }

            public void raisePriority(final PrioritisedExecutor.Priority priority) {
                this.task.raisePriority(priority);
            }

            @Override
            public void run() {
                ((ServerLightQueue)this.queue).chunkTasks.remove(this.chunkCoordinate, this);

                this.runTasks();
            }
        }
    }
}
