package ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task;

import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemLevelChunk;
import ca.spottedleaf.moonrise.patches.chunk_system.level.poi.ChunkSystemPoiManager;
import ca.spottedleaf.moonrise.patches.chunk_system.level.poi.PoiChunk;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.VarHandle;

public final class ChunkFullTask extends ChunkProgressionTask implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkFullTask.class);

    private final NewChunkHolder chunkHolder;
    private final ChunkAccess fromChunk;
    private final PrioritisedExecutor.PrioritisedTask convertToFullTask;

    public ChunkFullTask(final ChunkTaskScheduler scheduler, final ServerLevel world, final int chunkX, final int chunkZ,
                         final NewChunkHolder chunkHolder, final ChunkAccess fromChunk, final Priority priority) {
        super(scheduler, world, chunkX, chunkZ);
        this.chunkHolder = chunkHolder;
        this.fromChunk = fromChunk;
        this.convertToFullTask = scheduler.createChunkTask(chunkX, chunkZ, this, priority);
    }

    @Override
    public ChunkStatus getTargetStatus() {
        return ChunkStatus.FULL;
    }

    @Override
    public void run() {
        final PlatformHooks platformHooks = PlatformHooks.get();

        // See Vanilla ChunkPyramid#LOADING_PYRAMID.FULL for what this function should be doing
        final LevelChunk chunk;
        try {
            // moved from the load from nbt stage into here
            final PoiChunk poiChunk = this.chunkHolder.getPoiChunk();
            if (poiChunk == null) {
                LOGGER.error("Expected poi chunk to be loaded with chunk for task " + this.toString());
            } else {
                poiChunk.load();
                ((ChunkSystemPoiManager)this.world.getPoiManager()).moonrise$checkConsistency(this.fromChunk);
            }

            if (this.fromChunk instanceof ImposterProtoChunk wrappedFull) {
                chunk = wrappedFull.getWrapped();
            } else {
                final ServerLevel world = this.world;
                final ProtoChunk protoChunk = (ProtoChunk)this.fromChunk;
                chunk = new LevelChunk(this.world, protoChunk, (final LevelChunk unused) -> {
                    ChunkStatusTasks.postLoadProtoChunk(world, protoChunk.getEntities());
                });
                this.chunkHolder.replaceProtoChunk(new ImposterProtoChunk(chunk, false));
            }

            ((ChunkSystemLevelChunk)chunk).moonrise$setChunkAndHolder(new ServerChunkCache.ChunkAndHolder(chunk, this.chunkHolder.vanillaChunkHolder));

            final NewChunkHolder chunkHolder = this.chunkHolder;

            chunk.setFullStatus(chunkHolder::getChunkStatus);
            try {
                platformHooks.setCurrentlyLoading(this.chunkHolder.vanillaChunkHolder, chunk);
                chunk.runPostLoad();
                // Unlike Vanilla, we load the entity chunk here, as we load the NBT in empty status (unlike Vanilla)
                // This brings entity addition back in line with older versions of the game
                // Since we load the NBT in the empty status, this will never block for I/O
                ((ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager.getOrCreateEntityChunk(this.chunkX, this.chunkZ, false);
                chunk.setLoaded(true);
                chunk.registerAllBlockEntitiesAfterLevelLoad();
                chunk.registerTickContainerInLevel(this.world);
                chunk.setUnsavedListener(this.world.getChunkSource().chunkMap.worldGenContext.unsavedListener());
                platformHooks.chunkFullStatusComplete(chunk, (ProtoChunk)this.fromChunk);
            } finally {
                platformHooks.setCurrentlyLoading(this.chunkHolder.vanillaChunkHolder, null);
            }
        } catch (final Throwable throwable) {
            this.complete(null, throwable);
            return;
        }
        this.complete(chunk, null);
    }

    protected volatile boolean scheduled;
    protected static final VarHandle SCHEDULED_HANDLE = ConcurrentUtil.getVarHandle(ChunkFullTask.class, "scheduled", boolean.class);

    @Override
    public boolean isScheduled() {
        return this.scheduled;
    }

    @Override
    public void schedule() {
        if ((boolean)SCHEDULED_HANDLE.getAndSet((ChunkFullTask)this, true)) {
            throw new IllegalStateException("Cannot double call schedule()");
        }
        this.convertToFullTask.queue();
    }

    @Override
    public void cancel() {
        if (this.convertToFullTask.cancel()) {
            this.complete(null, null);
        }
    }

    @Override
    public Priority getPriority() {
        return this.convertToFullTask.getPriority();
    }

    @Override
    public void lowerPriority(final Priority priority) {
        if (!Priority.isValidPriority(priority)) {
            throw new IllegalArgumentException("Invalid priority " + priority);
        }
        this.convertToFullTask.lowerPriority(priority);
    }

    @Override
    public void setPriority(final Priority priority) {
        if (!Priority.isValidPriority(priority)) {
            throw new IllegalArgumentException("Invalid priority " + priority);
        }
        this.convertToFullTask.setPriority(priority);
    }

    @Override
    public void raisePriority(final Priority priority) {
        if (!Priority.isValidPriority(priority)) {
            throw new IllegalArgumentException("Invalid priority " + priority);
        }
        this.convertToFullTask.raisePriority(priority);
    }
}
