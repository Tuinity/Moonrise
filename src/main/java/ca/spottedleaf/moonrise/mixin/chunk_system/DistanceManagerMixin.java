package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.MoonriseConstants;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemDistanceManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicketStorage;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.LoadingChunkTracker;
import net.minecraft.server.level.SimulationChunkTracker;
import net.minecraft.server.level.ThrottlingChunkTaskDispatcher;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

@Mixin(DistanceManager.class)
abstract class DistanceManagerMixin implements ChunkSystemDistanceManager {

    @Shadow
    public LoadingChunkTracker loadingChunkTracker;

    @Shadow
    public SimulationChunkTracker simulationChunkTracker;

    @Shadow
    @Final
    private TicketStorage ticketStorage;

    @Shadow
    private DistanceManager.PlayerTicketTracker playerTicketManager;

    @Shadow
    Set<ChunkHolder> chunksToUpdateFutures;

    @Shadow
    ThrottlingChunkTaskDispatcher ticketDispatcher;

    @Shadow
    LongSet ticketsToRelease;

    @Shadow
    Executor mainThreadExecutor;

    @Shadow
    private int simulationDistance;


    @Override
    public ChunkMap moonrise$getChunkMap() {
        throw new AbstractMethodError();
    }

    /**
     * @reason Destroy old chunk system state to prevent it from being used, and set the chunk map
     *         for the ticket storage
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void destroyFields(final CallbackInfo ci) {
        this.loadingChunkTracker = null;
        this.simulationChunkTracker = null;
        this.playerTicketManager = null;
        this.chunksToUpdateFutures = null;
        this.ticketDispatcher = null;
        this.ticketsToRelease = null;
        this.mainThreadExecutor = null;
        this.simulationDistance = -1;

        ((ChunkSystemTicketStorage)this.ticketStorage).moonrise$setChunkMap(this.moonrise$getChunkMap());
    }

    @Override
    public final ChunkHolderManager moonrise$getChunkHolderManager() {
        return ((ChunkSystemServerLevel)this.moonrise$getChunkMap().level).moonrise$getChunkTaskScheduler().chunkHolderManager;
    }

    /**
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public boolean runAllUpdates(final ChunkMap chunkStorage) {
        return this.moonrise$getChunkHolderManager().processTicketUpdates();
    }

    /**
     * @reason Remove old chunk system hooks
     * @author Spottedleaf
     */
    @Redirect(
            method = "addPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/DistanceManager$PlayerTicketTracker;update(JIZ)V"
            )
    )
    private void skipTickingTicketTrackerAdd(final DistanceManager.PlayerTicketTracker instance, final long l,
                                             final int i, final boolean b) {}

    /**
     * @reason Remove old chunk system hooks
     * @author Spottedleaf
     */
    @Redirect(
            method = "addPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/TicketStorage;addTicket(Lnet/minecraft/server/level/Ticket;Lnet/minecraft/world/level/ChunkPos;)V"
            )
    )
    private void skipTickingTicketTrackerAdd(final TicketStorage instance, final Ticket ticket, final ChunkPos pos) {}

    /**
     * @reason Remove old chunk system hooks
     * @author Spottedleaf
     */
    @Redirect(
            method = "addPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/DistanceManager;getPlayerTicketLevel()I"
            )
    )
    private int skipTicketLevelAdd(final DistanceManager instance) {
        return 0;
    }

    /**
     * @reason Remove old chunk system hooks
     * @author Spottedleaf
     */
    @Redirect(
            method = "removePlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/DistanceManager$PlayerTicketTracker;update(JIZ)V"
            )
    )
    private void skipTickingTicketTrackerRemove(final DistanceManager.PlayerTicketTracker instance, final long l,
                                                final int i, final boolean b) {}

    /**
     * @reason Remove old chunk system hooks
     * @author Spottedleaf
     */
    @Redirect(
            method = "removePlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/TicketStorage;removeTicket(Lnet/minecraft/server/level/Ticket;Lnet/minecraft/world/level/ChunkPos;)V"
            )
    )
    private void skipTickingTicketTrackerRemove(final TicketStorage instance, final Ticket ticket, final ChunkPos pos) {}

    /**
     * @reason Remove old chunk system hooks
     * @author Spottedleaf
     */
    @Redirect(
            method = "removePlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/DistanceManager;getPlayerTicketLevel()I"
            )
    )
    private int skipTicketLevelRemove(final DistanceManager instance) {
        return 0;
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public int getPlayerTicketLevel() {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public boolean inEntityTickingRange(final long pos) {
        final NewChunkHolder chunkHolder = this.moonrise$getChunkHolderManager().getChunkHolder(pos);
        return chunkHolder != null && chunkHolder.isEntityTickingReady();
    }

    /**
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public boolean inBlockTickingRange(final long pos) {
        final NewChunkHolder chunkHolder = this.moonrise$getChunkHolderManager().getChunkHolder(pos);
        return chunkHolder != null && chunkHolder.isTickingReady();
    }

    /**
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public int getChunkLevel(final long pos, final boolean simulation) {
        final NewChunkHolder chunkHolder = this.moonrise$getChunkHolderManager().getChunkHolder(pos);
        return chunkHolder == null ? ChunkHolderManager.MAX_TICKET_LEVEL + 1 : chunkHolder.getTicketLevel();
    }

    /**
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public void updatePlayerTickets(final int viewDistance) {
        this.moonrise$getChunkMap().setServerViewDistance(viewDistance);
    }

    /**
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public void updateSimulationDistance(final int simulationDistance) {
        // note: vanilla does not clamp to 0, but we do simply because we need a min of 0
        final int clamped = Mth.clamp(simulationDistance, 0, MoonriseConstants.MAX_VIEW_DISTANCE);

        ((ChunkSystemServerLevel)this.moonrise$getChunkMap().level).moonrise$getPlayerChunkLoader().setTickDistance(clamped);
    }

    /**
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public void forEachEntityTickingChunk(final LongConsumer consumer) {
        final ReferenceList<LevelChunk> chunks = ((ChunkSystemServerLevel)this.moonrise$getChunkMap().level).moonrise$getEntityTickingChunks();
        final LevelChunk[] raw = chunks.getRawDataUnchecked();
        final int size = chunks.size();

        Objects.checkFromToIndex(0, size, raw.length);
        for (int i = 0; i < size; ++i) {
            final LevelChunk chunk = raw[i];

            consumer.accept(CoordinateUtils.getChunkKey(chunk.getPos()));
        }
    }

    /**
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public String getDebugStatus() {
        return "N/A";
    }
}
