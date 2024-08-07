package ca.spottedleaf.moonrise.neoforge.mixin.chunk_system;

import ca.spottedleaf.concurrentutil.map.ConcurrentLong2ReferenceChainedHashTable;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemDistanceManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(DistanceManager.class)
abstract class NeoForgeDistanceManagerMixin implements ChunkSystemDistanceManager {

    @Unique
    private final ConcurrentLong2ReferenceChainedHashTable<SortedArraySet<Ticket<?>>> mtSafeForcedTickets = new ConcurrentLong2ReferenceChainedHashTable<>();

    /**
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public <T> void addRegionTicket(final TicketType<T> type, final ChunkPos pos, final int radius, final T identifier, final boolean forceTicks) {
        final int level = ChunkLevel.byStatus(FullChunkStatus.FULL) - radius;
        this.moonrise$getChunkHolderManager().addTicketAtLevel(type, pos, level, identifier);
        if (forceTicks) {
            final Ticket<T> forceTicket = new Ticket<>(type, level, identifier, forceTicks);

            this.mtSafeForcedTickets.compute(pos.toLong(), (final long keyInMap, final SortedArraySet<Ticket<?>> valueInMap) -> {
                final SortedArraySet<Ticket<?>> ret;
                if (valueInMap != null) {
                    ret = valueInMap;
                } else {
                    ret = SortedArraySet.create(4);
                }

                ret.add(forceTicket);

                return ret;
            });
        }
    }

    /**
     * @reason Route to new chunk system
     * @author Spottedleaf
     */
    @Overwrite
    public <T> void removeRegionTicket(final TicketType<T> type, final ChunkPos pos, final int radius, final T identifier, final boolean forceTicks) {
        final int level = ChunkLevel.byStatus(FullChunkStatus.FULL) - radius;
        this.moonrise$getChunkHolderManager().removeTicketAtLevel(type, pos, level, identifier);
        if (forceTicks) {
            final Ticket<T> forceTicket = new Ticket<>(type, level, identifier, forceTicks);

            this.mtSafeForcedTickets.computeIfPresent(pos.toLong(), (final long keyInMap, final SortedArraySet<Ticket<?>> valueInMap) -> {
                valueInMap.remove(forceTicket);

                return valueInMap.isEmpty() ? null : valueInMap;
            });
        }
    }

    /**
     * @reason Make this API thread-safe
     * @author Spottedleaf
     */
    @Overwrite
    public boolean shouldForceTicks(final long chunkPos) {
        return this.mtSafeForcedTickets.containsKey(chunkPos);
    }
}
