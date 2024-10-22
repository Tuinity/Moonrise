package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemDistanceManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ThrottlingChunkTaskDispatcher;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.TickingTracker;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Set;
import java.util.concurrent.Executor;

@Mixin(DistanceManager.class)
abstract class DistanceManagerMixin implements ChunkSystemDistanceManager {

    @Shadow
    Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets;

    @Shadow
    private DistanceManager.ChunkTicketTracker ticketTracker;

    @Shadow
    private TickingTracker tickingTicketsTracker;

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
     * @reason Destroy old chunk system state to prevent it from being used
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void destroyFields(final CallbackInfo ci) {
        this.tickets = null;
        this.ticketTracker = null;
        this.tickingTicketsTracker = null;
        this.playerTicketManager = null;
        this.chunksToUpdateFutures = null;
        this.ticketDispatcher = null;
        this.ticketsToRelease = null;
        this.mainThreadExecutor = null;
        this.simulationDistance = -1;
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
    public void purgeStaleTickets() {
        this.moonrise$getChunkHolderManager().tick();
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
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public void addTicket(final long pos, final Ticket<?> ticket) {
        this.moonrise$getChunkHolderManager().addTicketAtLevel((TicketType)ticket.getType(), pos, ticket.getTicketLevel(), ticket.key);
    }

    /**
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public void removeTicket(final long pos, final Ticket<?> ticket) {
        this.moonrise$getChunkHolderManager().removeTicketAtLevel((TicketType)ticket.getType(), pos, ticket.getTicketLevel(), ticket.key);
    }

    /**
     * @reason Remove old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public SortedArraySet<Ticket<?>> getTickets(final long pos) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public void updateChunkForced(final ChunkPos pos, final boolean forced) {
        if (forced) {
            this.moonrise$getChunkHolderManager().addTicketAtLevel(TicketType.FORCED, pos, ChunkMap.FORCED_TICKET_LEVEL, pos);
        } else {
            this.moonrise$getChunkHolderManager().removeTicketAtLevel(TicketType.FORCED, pos, ChunkMap.FORCED_TICKET_LEVEL, pos);
        }
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
                    target = "Lnet/minecraft/server/level/TickingTracker;addTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V"
            )
    )
    private <T> void skipTickingTicketTrackerAdd(final TickingTracker instance, final TicketType<T> ticketType,
                                                 final ChunkPos chunkPos, final int i, final T object) {}

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
                    target = "Lnet/minecraft/server/level/TickingTracker;removeTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V"
            )
    )
    private <T> void skipTickingTicketTrackerRemove(final TickingTracker instance, final TicketType<T> ticketType,
                                                    final ChunkPos chunkPos, final int i, final T object) {}

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
    public String getTicketDebugString(final long pos) {
        return this.moonrise$getChunkHolderManager().getTicketDebugString(pos);
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
        ((ChunkSystemServerLevel)this.moonrise$getChunkMap().level).moonrise$getPlayerChunkLoader().setTickDistance(simulationDistance);
    }

    /**
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public String getDebugStatus() {
        return "No DistanceManager stats available";
    }

    /**
     * @reason Remove old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public void dumpTickets(final String file) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Remove old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public TickingTracker tickingTracker() {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason This hack is not required anymore, see {@link MinecraftServerMixin}
     * @author Spottedleaf
     */
    @Overwrite
    public void removeTicketsOnClosing() {}

    /**
     * @reason This hack is not required anymore, see {@link MinecraftServerMixin}
     * @author Spottedleaf
     */
    @Overwrite
    public boolean hasTickets() {
        throw new UnsupportedOperationException();
    }
}
