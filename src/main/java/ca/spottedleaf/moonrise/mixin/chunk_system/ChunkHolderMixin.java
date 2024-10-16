package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ChunkHolder.class)
abstract class ChunkHolderMixin extends GenerationChunkHolder implements ChunkSystemChunkHolder {

    @Shadow
    @Final
    private ChunkHolder.PlayerProvider playerProvider;

    @Shadow
    private volatile CompletableFuture<ChunkResult<LevelChunk>> fullChunkFuture;

    @Shadow
    private volatile CompletableFuture<ChunkResult<LevelChunk>> tickingChunkFuture;

    @Shadow
    private volatile CompletableFuture<ChunkResult<LevelChunk>> entityTickingChunkFuture;

    @Shadow
    private CompletableFuture<?> pendingFullStateConfirmation;

    @Shadow
    private CompletableFuture<?> sendSync;

    @Shadow
    private CompletableFuture<?> saveSync;

    public ChunkHolderMixin(ChunkPos chunkPos) {
        super(chunkPos);
    }

    @Unique
    private NewChunkHolder newChunkHolder;

    @Unique
    private final ReferenceList<ServerPlayer> playersSentChunkTo = new ReferenceList<>(EMPTY_PLAYER_ARRAY);

    @Unique
    private boolean isMarkedDirtyForPlayers;

    @Unique
    private ChunkMap getChunkMap() {
        return (ChunkMap)this.playerProvider;
    }

    @Override
    public final NewChunkHolder moonrise$getRealChunkHolder() {
        return this.newChunkHolder;
    }

    @Override
    public final void moonrise$setRealChunkHolder(final NewChunkHolder newChunkHolder) {
        this.newChunkHolder = newChunkHolder;
    }

    @Override
    public final void moonrise$addReceivedChunk(final ServerPlayer player) {
        if (!this.playersSentChunkTo.add(player)) {
            throw new IllegalStateException("Already sent chunk " + this.pos + " in world '" + WorldUtil.getWorldName(this.getChunkMap().level) + "' to player " + player);
        }
    }

    @Override
    public final void moonrise$removeReceivedChunk(final ServerPlayer player) {
        if (!this.playersSentChunkTo.remove(player)) {
            throw new IllegalStateException("Already sent chunk " + this.pos + " in world '" + WorldUtil.getWorldName(this.getChunkMap().level) + "' to player " + player);
        }
    }

    @Override
    public final boolean moonrise$hasChunkBeenSent() {
        return this.playersSentChunkTo.size() != 0;
    }

    @Override
    public final boolean moonrise$hasChunkBeenSent(final ServerPlayer to) {
        return this.playersSentChunkTo.contains(to);
    }

    @Override
    public final List<ServerPlayer> moonrise$getPlayers(final boolean onlyOnWatchDistanceEdge) {
        final List<ServerPlayer> ret = new ArrayList<>();
        final ServerPlayer[] raw = this.playersSentChunkTo.getRawDataUnchecked();
        for (int i = 0, len = this.playersSentChunkTo.size(); i < len; ++i) {
            final ServerPlayer player = raw[i];
            if (onlyOnWatchDistanceEdge && !((ChunkSystemServerLevel)this.getChunkMap().level).moonrise$getPlayerChunkLoader().isChunkSent(player, this.pos.x, this.pos.z, onlyOnWatchDistanceEdge)) {
                continue;
            }
            ret.add(player);
        }

        return ret;
    }

    @Override
    public final boolean moonrise$isMarkedDirtyForPlayers() {
        return this.isMarkedDirtyForPlayers;
    }

    @Override
    public final void moonrise$markDirtyForPlayers(final boolean value) {
        this.isMarkedDirtyForPlayers = value;
    }

    @Unique
    private static final ServerPlayer[] EMPTY_PLAYER_ARRAY = new ServerPlayer[0];

    /**
     * @reason Initialise our fields
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void initFields(final CallbackInfo ci) {
        this.fullChunkFuture = null;
        this.tickingChunkFuture = null;
        this.entityTickingChunkFuture = null;
        this.pendingFullStateConfirmation = null;
        this.sendSync = null;
        this.saveSync = null;
    }

    /**
     * @reason Chunk system is not built on futures anymore, use {@link ChunkTaskScheduler}
     *         schedule methods to await for a chunk load
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<ChunkResult<ChunkAccess>> getTickingChunkFuture() {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore, use {@link ChunkTaskScheduler}
     *         schedule methods to await for a chunk load
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<ChunkResult<ChunkAccess>> getEntityTickingChunkFuture() {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore, use {@link ChunkTaskScheduler}
     *         schedule methods to await for a chunk load
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<ChunkResult<ChunkAccess>> getFullChunkFuture() {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Route to new chunk holder
     * @author Spottedleaf
     */
    @Overwrite
    public LevelChunk getTickingChunk() {
        if (this.newChunkHolder.isTickingReady()) {
            if (this.newChunkHolder.getCurrentChunk() instanceof LevelChunk levelChunk) {
                return levelChunk;
            } // else: race condition: chunk unload
        }
        return null;
    }

    /**
     * @reason Chunk system is not built on futures anymore, and I am pretty sure this is a disgusting hack for a problem
     *         that doesn't even exist.
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<?> getSendSyncFuture() {
        throw new UnsupportedOperationException();
    }

    @Unique
    private boolean isRadiusLoaded(final int radius) {
        final ChunkHolderManager manager = ((ChunkSystemServerLevel)this.getChunkMap().level).moonrise$getChunkTaskScheduler()
                .chunkHolderManager;
        final ChunkPos pos = this.pos;
        final int chunkX = pos.x;
        final int chunkZ = pos.z;
        for (int dz = -radius; dz <= radius; ++dz) {
            for (int dx = -radius; dx <= radius; ++dx) {
                if ((dx | dz) == 0) {
                    continue;
                }

                final NewChunkHolder holder = manager.getChunkHolder(dx + chunkX, dz + chunkZ);

                if (holder == null || !holder.isFullChunkReady()) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * @reason Chunk sending may now occur for non-ticking chunks, provided that both the 1 radius neighbours are FULL
     *         and post-processing is ran.
     * @author Spottedleaf
     */
    @Overwrite
    public LevelChunk getChunkToSend() {
        final LevelChunk ret = this.moonrise$getFullChunk();
        if (ret != null && this.isRadiusLoaded(1)) {
            return ret;
        }
        return null;
    }

    @Override
    public final LevelChunk moonrise$getFullChunk() {
        if (this.newChunkHolder.isFullChunkReady()) {
            if (this.newChunkHolder.getCurrentChunk() instanceof LevelChunk levelChunk) {
                return levelChunk;
            } // else: race condition: chunk unload
        }
        return null;
    }

    /**
     * @reason Chunk system is not built on futures anymore, unloading is now checked via {@link NewChunkHolder#isSafeToUnload()}
     *         while holding chunk system locks.
     * @author Spottedleaf
     */
    @Overwrite
    public CompletableFuture<ChunkAccess> getSaveSyncFuture() {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public boolean isReadyForSaving() {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public void addSaveDependency(final CompletableFuture<?> completableFuture) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason need to reroute getTickingChunk to getChunkToSend, as we do not bring all sent chunks to ticking
     * @author Spottedleaf
     */
    @Redirect(
            method = "blockChanged",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkHolder;getTickingChunk()Lnet/minecraft/world/level/chunk/LevelChunk;")
    )
    private LevelChunk redirectBlockUpdate(final ChunkHolder instance) {
        if (this.playersSentChunkTo.size() == 0) {
            // no players to sent to, so don't need to update anything
            return null;
        }
        return this.getChunkToSend();
    }

    /**
     * @reason need to reroute getTickingChunk to getChunkToSend, as we do not bring all sent chunks to ticking
     * @author Spottedleaf
     */
    @Redirect(
            method = "sectionLightChanged",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkHolder;getTickingChunk()Lnet/minecraft/world/level/chunk/LevelChunk;"
            )
    )
    private LevelChunk redirectLightUpdate(final ChunkHolder instance) {
        if (this.playersSentChunkTo.size() == 0) {
            // no players to sent to, so don't need to update anything
            return null;
        }
        return this.getChunkToSend();
    }

    /**
     * @reason Redirect player retrieval to the sent player list, as we do not maintain the Vanilla hook
     * @author Spottedleaf
     */
    @Redirect(
            method = "broadcastChanges",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkHolder$PlayerProvider;getPlayers(Lnet/minecraft/world/level/ChunkPos;Z)Ljava/util/List;")
    )
    private List<ServerPlayer> redirectPlayerRetrieval(final ChunkHolder.PlayerProvider instance, final ChunkPos chunkPos,
                                                       final boolean onlyOnWatchDistanceEdge) {
        return this.moonrise$getPlayers(onlyOnWatchDistanceEdge);
    }

    /**
     * @reason Chunk system is not built on futures anymore, and I am pretty sure this is a disgusting hack for a problem
     *         that doesn't even exist.
     * @author Spottedleaf
     */
    @Overwrite
    public void addSendDependency(final CompletableFuture<?> completableFuture) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Route to new chunk holder
     * @author Spottedleaf
     */
    @Overwrite
    public int getTicketLevel() {
        return this.newChunkHolder.getTicketLevel();
    }

    /**
     * @reason Set chunk priority instead in the new chunk system
     * @author Spottedleaf
     * @see ChunkTaskScheduler
     */
    @Overwrite
    public int getQueueLevel() {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Set chunk priority instead in the new chunk system
     * @author Spottedleaf
     * @see ChunkTaskScheduler
     */
    @Overwrite
    public void setQueueLevel(int i) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Use ticket system to control ticket levels
     * @author Spottedleaf
     * @see net.minecraft.server.level.ServerChunkCache#addRegionTicket(TicketType, ChunkPos, int, Object)
     */
    @Overwrite
    public void setTicketLevel(int i) {
        // don't throw, this is called during construction of ChunkHolder
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public void scheduleFullChunkPromotion(final ChunkMap chunkMap,
                                            final CompletableFuture<ChunkResult<LevelChunk>> completableFuture,
                                            final Executor executor, final FullChunkStatus fullChunkStatus) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system is not built on futures anymore
     * @author Spottedleaf
     */
    @Overwrite
    public void demoteFullChunk(final ChunkMap chunkMap, final FullChunkStatus fullChunkStatus) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Chunk system hooks for ticket level updating now in {@link NewChunkHolder#processTicketLevelUpdate(List, List)}
     * @author Spottedleaf
     */
    @Overwrite
    public void updateFutures(final ChunkMap chunkMap, final Executor executor) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason New chunk system has no equivalent, as chunks should be saved according to their dirty flag to ensure
     *         that all unsaved data is not lost.
     * @author Spottedleaf
     */
    @Overwrite
    public boolean wasAccessibleSinceLastSave() {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason New chunk system has no equivalent, as chunks should be saved according to their dirty flag to ensure
     *         that all unsaved data is not lost.
     * @author Spottedleaf
     */
    @Overwrite
    public void refreshAccessibility() {
        throw new UnsupportedOperationException();
    }
}
