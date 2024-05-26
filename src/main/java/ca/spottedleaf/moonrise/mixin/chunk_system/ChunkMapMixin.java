package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.util.MoonriseConstants;
import ca.spottedleaf.moonrise.patches.chunk_system.ChunkSystem;
import ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder;
import ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin extends ChunkStorage implements ChunkHolder.PlayerProvider {

    @Shadow
    @Final
    public ServerLevel level;

    @Shadow
    private Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap;

    @Shadow
    private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;

    @Shadow
    private ChunkTaskPriorityQueueSorter queueSorter;

    @Shadow
    private ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> worldgenMailbox;

    @Shadow
    private ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> mainThreadMailbox;

    @Shadow
    private int serverViewDistance;

    public ChunkMapMixin(RegionStorageInfo regionStorageInfo, Path path, DataFixer dataFixer, boolean bl) {
        super(regionStorageInfo, path, dataFixer, bl);
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void constructor(final CallbackInfo ci) {
        // intentionally destroy old chunk system hooks
        this.updatingChunkMap = null;
        this.visibleChunkMap = null;
        this.queueSorter = null;
        this.worldgenMailbox = null;
        this.mainThreadMailbox = null;
    }

    /**
     * @reason Route to new chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public boolean isChunkTracked(final ServerPlayer player, final int chunkX, final int chunkZ) {
        return ((ChunkSystemServerLevel)this.level).moonrise$getPlayerChunkLoader().isChunkSent(player, chunkX, chunkZ);
    }

    /**
     * @reason Route to new chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public boolean isChunkOnTrackedBorder(final ServerPlayer player, final int chunkX, final int chunkZ) {
        return ((ChunkSystemServerLevel)this.level).moonrise$getPlayerChunkLoader().isChunkSent(player, chunkX, chunkZ, true);
    }

    /**
     * @reason Route to new chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public ChunkHolder getUpdatingChunkIfPresent(final long pos) {
        final NewChunkHolder holder = ((ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(pos);
        return holder == null ? null : holder.vanillaChunkHolder;
    }

    /**
     * @reason Route to new chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public ChunkHolder getVisibleChunkIfPresent(final long pos) {
        final NewChunkHolder holder = ((ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(pos);
        return holder == null ? null : holder.vanillaChunkHolder;
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public IntSupplier getChunkQueueLevel(final long pos) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<ChunkResult<List<ChunkAccess>>> getChunkRangeFuture(final ChunkHolder centerChunk,
                                                                                  final int margin,
                                                                                  final IntFunction<ChunkStatus> distanceToStatus) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<ChunkResult<List<ChunkAccess>>> prepareEntityTickingChunk(final ChunkHolder chunk) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public ChunkHolder updateChunkScheduling(final long pos, final int level, final ChunkHolder holder,
                                             final int newLevel) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Override
    @Overwrite
    public void close() throws IOException {
        throw new UnsupportedOperationException("Use ServerChunkCache#close");
    }

    /**
     * @reason Route to new chunk system and handle close-save operations
     * @author Spottedleaf
     */
    @Overwrite
    public void saveAllChunks(final boolean flush) {
        final boolean shutdown = ((ChunkSystemServerLevel)this.level).moonrise$isMarkedClosing();

        if (!shutdown) {
            ((ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.saveAllChunks(
                    flush, false, false
            );
        } else {
            ((ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.close(
                    true, true
            );
        }
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public boolean hasWork() {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Route to new chunk unloading code
     * @author Spottedleaf
     */
    @Overwrite
    public void processUnloads(final BooleanSupplier shouldKeepTicking) {
        ((ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.processUnloads();
        ((ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.autoSave();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public void scheduleUnload(final long pos, final ChunkHolder holder) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Replaced by concurrent map, removing the need for this logic.
     *         A side-note of this logic is that expensive map copying is no longer performed.
     * @author Spottedleaf
     * @see ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager#chunkHolders
     */
    @Overwrite
    public boolean promoteChunkMap() {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<ChunkResult<ChunkAccess>> schedule(final ChunkHolder holder, final ChunkStatus requiredStatus) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkLoad(final ChunkPos pos) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkGeneration(final ChunkHolder holder,
                                                                                final ChunkStatus requiredStatus) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<ChunkResult<ChunkAccess>> protoChunkToFullChunk(final ChunkHolder chunkHolder,
                                                                              final ChunkAccess chunkAccess) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<ChunkResult<LevelChunk>> prepareTickingChunk(final ChunkHolder holder) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public void onChunkReadyToSend(final LevelChunk chunk) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<ChunkResult<LevelChunk>> prepareAccessibleChunk(final ChunkHolder holder) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     * @see NewChunkHolder#save(boolean)
     */
    @Overwrite
    public boolean saveChunkIfNeeded(final ChunkHolder chunkHolder) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     * @see NewChunkHolder#save(boolean)
     */
    @Overwrite
    public boolean save(final ChunkAccess chunk) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public boolean isExistingChunkFull(final ChunkPos pos) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Route to new player chunk loader
     * @author Spottedleaf
     */
    @Overwrite
    public void setServerViewDistance(final int watchDistance) {
        final int clamped = Mth.clamp(watchDistance, 2, MoonriseConstants.MAX_VIEW_DISTANCE);
        if (clamped == this.serverViewDistance) {
            return;
        }

        this.serverViewDistance = clamped;
        ((ChunkSystemServerLevel)this.level).moonrise$getPlayerChunkLoader().setLoadDistance(this.serverViewDistance + 1);
    }

    /**
     * @reason Route to new player chunk loader
     * @author Spottedleaf
     */
    @Overwrite
    public int getPlayerViewDistance(final ServerPlayer player) {
        return ChunkSystem.getSendViewDistance(player);
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public void markChunkPendingToSend(final ServerPlayer player, final ChunkPos pos) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public static void markChunkPendingToSend(final ServerPlayer player, final LevelChunk chunk) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public static void dropChunk(final ServerPlayer player, final ChunkPos pos) {

    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public void dumpChunks(final Writer writer) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Optional<CompoundTag>> read(final ChunkPos pos) {
        if (!RegionFileIOThread.isRegionFileThread()) {
            try {
                return CompletableFuture.completedFuture(
                        Optional.ofNullable(
                                RegionFileIOThread.loadData(
                                        this.level, pos.x, pos.z, RegionFileIOThread.RegionFileType.CHUNK_DATA,
                                        RegionFileIOThread.getIOBlockingPriorityForCurrentThread()
                                )
                        )
                );
            } catch (final Throwable thr) {
                return CompletableFuture.failedFuture(thr);
            }
        }
        return super.read(pos);
    }

    @Override
    public CompletableFuture<Void> write(final ChunkPos pos, final CompoundTag tag) {
        if (!RegionFileIOThread.isRegionFileThread()) {
            RegionFileIOThread.scheduleSave(
                this.level, pos.x, pos.z, tag,
                RegionFileIOThread.RegionFileType.CHUNK_DATA);
            return null;
        }
        super.write(pos, tag);
        return null;
    }

    @Override
    public void flushWorker() {
        RegionFileIOThread.flush();
    }

    /**
     * @reason New player chunk loader handles this, and redirect to the distance map add
     * @author Spottedleaf
     */
    @Redirect(
            method = "updatePlayerStatus",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;updateChunkTracking(Lnet/minecraft/server/level/ServerPlayer;)V"
            )
    )
    private void avoidUpdateChunkTrackingInUpdate(final ChunkMap instance, final ServerPlayer serverPlayer) {
        ChunkSystem.addPlayerToDistanceMaps(this.level, serverPlayer);
    }

    /**
     * @reason updateChunkTracking is not needed, the player chunk loader has its own tick hook elsewhere
     * @author Spottedleaf
     * @see RegionizedPlayerChunkLoader#tick()
     */
    @Redirect(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;updateChunkTracking(Lnet/minecraft/server/level/ServerPlayer;)V"
            )
    )
    private void skipChunkTrackingInTick(final ChunkMap instance, final ServerPlayer serverPlayer) {}

    /**
     * @reason New player chunk loader handles this, and redirect to the distance map remove
     * @author Spottedleaf
     */
    @Redirect(
            method = "updatePlayerStatus",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;applyChunkTrackingView(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ChunkTrackingView;)V"
            )
    )
    private void avoidApplyChunkTrackingViewInUpdate(final ChunkMap instance, final ServerPlayer serverPlayer,
                                                     final ChunkTrackingView chunkTrackingView) {
        ChunkSystem.removePlayerFromDistanceMaps(this.level, serverPlayer);
    }

    /**
     * Hook into move call so that we can run callbacks on chunk position change
     * @author Spottedleaf
     */
    @Inject(
        method = "move",
        at = @At(
            value = "RETURN"
        )
    )
    private void updateMapsHook(final ServerPlayer player, final CallbackInfo ci) {
        ChunkSystem.updateMaps(this.level, player);
    }

    /**
     * @reason New player chunk loader handles this
     * @author Spottedleaf
     */
    @Redirect(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;updateChunkTracking(Lnet/minecraft/server/level/ServerPlayer;)V"
            )
    )
    private void avoidSetChunkTrackingViewInMove(final ChunkMap instance, final ServerPlayer serverPlayer) {}

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public void updateChunkTracking(final ServerPlayer player) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public void applyChunkTrackingView(final ServerPlayer player, final ChunkTrackingView chunkFilter) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Route to new player chunk loader
     * @author Spottedleaf
     */
    @Override
    @Overwrite
    public List<ServerPlayer> getPlayers(final ChunkPos chunkPos, final boolean onlyOnWatchDistanceEdge) {
        final ChunkHolder holder = this.getVisibleChunkIfPresent(chunkPos.toLong());
        if (holder == null) {
            return new ArrayList<>();
        } else {
            return ((ChunkSystemChunkHolder)holder).moonrise$getPlayers(onlyOnWatchDistanceEdge);
        }
    }

    /**
     * @reason See {@link ChunkHolderMixin#addSendDependency(CompletableFuture)}
     * @author Spottedleaf
     */
    @Overwrite
    public void waitForLightBeforeSending(final ChunkPos centerPos, final int radius) {}

    /**
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public int size() {
        return ((ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.size();
    }

    /**
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public Iterable<ChunkHolder> getChunks() {
        return ((ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager.getOldChunkHoldersIterable();
    }
}
