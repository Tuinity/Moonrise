package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.concurrentutil.map.ConcurrentLong2LongChainedHashTable;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicket;
import ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicketStorage;
import ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicketType;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.saveddata.SavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

@Mixin(TicketStorage.class)
abstract class TicketStorageMixin extends SavedData implements ChunkSystemTicketStorage {

    @Shadow
    @Final
    private Long2ObjectOpenHashMap<List<Ticket>> deactivatedTickets;

    @Shadow
    private Long2ObjectOpenHashMap<List<Ticket>> tickets;

    @Shadow
    private LongSet chunksWithForcedTickets;

    @Unique
    private ChunkMap chunkMap;

    @Override
    public final ChunkMap moonrise$getChunkMap() {
        return this.chunkMap;
    }

    @Override
    public final void moonrise$setChunkMap(final ChunkMap chunkMap) {
        this.chunkMap = chunkMap;
    }

    /**
     * @reason Destroy old chunk system state
     * @author Spottedleaf
     */
    @Inject(
        method = "<init>(Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;)V",
        at = @At(
            value = "RETURN"
        )
    )
    private void destroyFields(Long2ObjectOpenHashMap p_393873_, Long2ObjectOpenHashMap p_394615_, CallbackInfo ci) {
        if (!this.tickets.isEmpty()) {
            throw new IllegalStateException("Expect tickets to be empty here!");
        }
        this.tickets = null;
        this.chunksWithForcedTickets = null;
    }

    /**
     * @reason The forced chunks would be empty, as tickets should always be empty.
     *         We need to do this to avoid throwing immediately.
     * @author Spottedleaf
     */
    @Redirect(
        method = "<init>(Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/TicketStorage;updateForcedChunks()V"
        )
    )
    private void avoidUpdatingForcedChunks(final TicketStorage instance) {}


    /**
     * @reason Redirect regular ticket retrieval to new chunk system
     * @author Spottedleaf
     */
    @Redirect(
        method = "forEachTicket(Ljava/util/function/BiConsumer;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/TicketStorage;forEachTicket(Ljava/util/function/BiConsumer;Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;)V",
            ordinal = 0
        )
    )
    private void redirectRegularTickets(final BiConsumer<ChunkPos, Ticket> consumer, final Long2ObjectOpenHashMap<List<Ticket>> ticketsParam) {
        if (ticketsParam != null) {
            throw new IllegalStateException("Bad injection point");
        }

        final Long2ObjectOpenHashMap<Collection<Ticket>> tickets = ((ChunkSystemServerLevel)this.chunkMap.level)
            .moonrise$getChunkTaskScheduler().chunkHolderManager.getTicketsCopy();

        for (final Iterator<Long2ObjectMap.Entry<Collection<Ticket>>> iterator = tickets.long2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
            final Long2ObjectMap.Entry<Collection<Ticket>> entry = iterator.next();

            final long pos = entry.getLongKey();
            final Collection<Ticket> chunkTickets = entry.getValue();

            final ChunkPos chunkPos = new ChunkPos(pos);

            for (final Ticket ticket : chunkTickets) {
                consumer.accept(chunkPos, ticket);
            }
        }
    }

    /**
     * @reason Support new chunk system
     * @author jpenilla
     */
    @Overwrite
    public boolean shouldKeepDimensionActive() {
        final ConcurrentLong2LongChainedHashTable ticketCounters = ((ChunkSystemServerLevel)this.chunkMap.level).moonrise$getChunkTaskScheduler().chunkHolderManager
            .getTicketCounters(ChunkSystemTicketType.COUNTER_TYPE_KEEP_DIMENSION_ACTIVE);
        return ticketCounters != null && !ticketCounters.isEmpty();
    }

    /**
     * @reason Avoid setting old chunk system state
     * @author Spottedleaf
     */
    @Overwrite
    public void setLoadingChunkUpdatedListener(final TicketStorage.ChunkUpdated callback) {}

    /**
     * @reason Avoid setting old chunk system state
     * @author Spottedleaf
     */
    @Overwrite
    public void setSimulationChunkUpdatedListener(final TicketStorage.ChunkUpdated callback) {}

    /**
     * @reason Redirect to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public boolean hasTickets() {
        return ((ChunkSystemServerLevel)this.chunkMap.level).moonrise$getChunkTaskScheduler().chunkHolderManager.hasTickets();
    }

    /**
     * @reason Redirect to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public List<Ticket> getTickets(final long pos) {
        return ((ChunkSystemServerLevel)this.chunkMap.level).moonrise$getChunkTaskScheduler().chunkHolderManager
            .getTicketsAt(CoordinateUtils.getChunkX(pos), CoordinateUtils.getChunkZ(pos));
    }

    /**
     * @reason Redirect to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public boolean addTicket(final long pos, final Ticket ticket) {
        final boolean ret = ((ChunkSystemServerLevel)this.chunkMap.level).moonrise$getChunkTaskScheduler().chunkHolderManager
            .addTicketAtLevel(ticket.getType(), pos, ticket.getTicketLevel(), ((ChunkSystemTicket<?>)ticket).moonrise$getIdentifier());

        this.setDirty();

        return ret;
    }

    /**
     * @reason Redirect to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public boolean removeTicket(final long pos, final Ticket ticket) {
        final boolean ret = ((ChunkSystemServerLevel)this.chunkMap.level).moonrise$getChunkTaskScheduler().chunkHolderManager
            .removeTicketAtLevel(ticket.getType(), pos, ticket.getTicketLevel(), ((ChunkSystemTicket<?>)ticket).moonrise$getIdentifier());

        if (ret) {
            this.setDirty();
        }

        return ret;
    }

    /**
     * @reason Redirect to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public void purgeStaleTickets(final ChunkMap chunkMap) {
        ((ChunkSystemServerLevel)chunkMap.level).moonrise$getChunkTaskScheduler().chunkHolderManager.tick();
        this.setDirty();
    }

    /**
     * @reason All tickets (inactive or not) are packed and saved, so there's no real reason we need to remove them.
     *         Vanilla removes them as it requires every chunk to go through the unload logic; however we already manually
     *         do this on shutdown.
     * @author Spottedleaf
     */
    @Redirect(
        method = "deactivateTicketsOnClosing",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/TicketStorage;removeTicketIf(Lnet/minecraft/world/level/TicketStorage$TicketPredicate;Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;)V"
        )
    )
    private void avoidRemovingTicketsOnShutdown(final TicketStorage instance,
                                                final TicketStorage.TicketPredicate predicate,
                                                final Long2ObjectOpenHashMap<List<Ticket>> tickets) {}

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public void removeTicketIf(final TicketStorage.TicketPredicate predicate, final Long2ObjectOpenHashMap<List<Ticket>> into) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public void replaceTicketLevelOfType(final int newLevel, final TicketType forType) {
        throw new UnsupportedOperationException();
    }

    /**
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public LongSet getForceLoadedChunks() {
        final ConcurrentLong2LongChainedHashTable forced = ((ChunkSystemServerLevel)this.chunkMap.level).moonrise$getChunkTaskScheduler()
            .chunkHolderManager.getTicketCounters(ChunkSystemTicketType.COUNTER_TYPE_FORCED);

        if (forced == null) {
            return new LongLinkedOpenHashSet();
        }

        // note: important to presize correctly using size/loadfactor to avoid awful write performance
        //       think: iteration over our map has the same hash strategy, and if ret is not sized
        //       correctly then every (ret.table.length) may collide. During resize, open hashed tables
        //       (like LongLinkedOpenHashSet) must reinsert - leading to O(n^2) to copy IF we do not initially
        //       size correctly
        final LongLinkedOpenHashSet ret = new LongLinkedOpenHashSet(forced.size(), forced.getLoadFactor());

        for (final PrimitiveIterator.OfLong iterator = forced.keyIterator(); iterator.hasNext();) {
            ret.add(iterator.nextLong());
        }

        return ret;
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Overwrite
    public LongSet getAllChunksWithTicketThat(final Predicate<Ticket> predicate) {
        throw new UnsupportedOperationException();
    }
}
