package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.GeneratingChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

@Mixin(GenerationChunkHolder.class)
public abstract class GenerationChunkHolderMixin {

    @Shadow
    public abstract int getTicketLevel();

    @Shadow
    private AtomicReference<ChunkStatus> startedWork;

    @Shadow
    private AtomicReferenceArray<CompletableFuture<ChunkResult<ChunkAccess>>> futures;

    @Shadow
    private AtomicReference<ChunkGenerationTask> task;

    @Shadow
    private AtomicInteger generationRefCount;

    /**
     * @reason Destroy old chunk system fields
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void init(final CallbackInfo ci) {
        this.startedWork = null;
        this.futures = null;
        this.task = null;
        this.generationRefCount = null;
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkGenerationTask(final ChunkStatus chunkStatus,
                                                                                   final ChunkMap chunkMap) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<ChunkResult<ChunkAccess>> applyStep(final ChunkStep chunkStep, final GeneratingChunkMap generatingChunkMap,
                                                                 final StaticCache2D<GenerationChunkHolder> staticCache2D) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public void updateHighestAllowedStatus(final ChunkMap chunkMap) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public void replaceProtoChunk(final ImposterProtoChunk imposterProtoChunk) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public void removeTask(final ChunkGenerationTask chunkGenerationTask) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public void rescheduleChunkTask(final ChunkMap chunkMap, final ChunkStatus chunkStatus) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<ChunkResult<ChunkAccess>> getOrCreateFuture(final ChunkStatus chunkStatus) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public void failAndClearPendingFuturesBetween(final ChunkStatus from, final ChunkStatus to) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public void failAndClearPendingFuture(final int idx, final CompletableFuture<ChunkResult<ChunkAccess>> expect) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public void completeFuture(final ChunkStatus status, final ChunkAccess chunk) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public ChunkStatus findHighestStatusWithPendingFuture(final ChunkStatus from) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public boolean acquireStatusBump(final ChunkStatus chunkStatus) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public boolean isStatusDisallowed(final ChunkStatus chunkStatus) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public void increaseGenerationRefCount() {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public void decreaseGenerationRefCount() {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public int getGenerationRefCount() {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Route to new chunk holder
     * @author Spottedleaf
     */
    @Overwrite
    public ChunkAccess getChunkIfPresentUnchecked(final ChunkStatus chunkStatus) {
        return ((ChunkSystemChunkHolder)(Object)this).moonrise$getRealChunkHolder().getChunkIfPresentUnchecked(chunkStatus);
    }

    /**
     * @reason Route to new chunk holder
     * @author Spottedleaf
     */
    @Overwrite
    public ChunkAccess getChunkIfPresent(final ChunkStatus chunkStatus) {
        return ((ChunkSystemChunkHolder)(Object)this).moonrise$getRealChunkHolder().getChunkIfPresent(chunkStatus);
    }

    /**
     * @reason Route to new chunk holder
     * @author Spottedleaf
     */
    @Overwrite
    public ChunkAccess getLatestChunk() {
        final NewChunkHolder.ChunkCompletion lastCompletion = ((ChunkSystemChunkHolder)(Object)this).moonrise$getRealChunkHolder().getLastChunkCompletion();
        return lastCompletion == null ? null : lastCompletion.chunk();
    }

    /**
     * @reason Route to new chunk holder
     * @author Spottedleaf
     */
    @Overwrite
    public ChunkStatus getPersistedStatus() {
        final ChunkAccess chunk = this.getLatestChunk();
        return chunk == null ? null : chunk.getPersistedStatus();
    }

    /**
     * @reason Route to new chunk holder
     * @author Spottedleaf
     */
    @Overwrite
    public FullChunkStatus getFullStatus() {
        return ((ChunkSystemChunkHolder)(Object)this).moonrise$getRealChunkHolder().getChunkStatus();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public List<Pair<ChunkStatus, CompletableFuture<ChunkResult<ChunkAccess>>>> getAllFutures() {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Route to new chunk holder
     * @author Spottedleaf
     */
    @Overwrite
    public ChunkStatus getLatestStatus() {
        final NewChunkHolder.ChunkCompletion lastCompletion = ((ChunkSystemChunkHolder)(Object)this).moonrise$getRealChunkHolder().getLastChunkCompletion();
        return lastCompletion == null ? null : lastCompletion.genStatus();
    }
}
