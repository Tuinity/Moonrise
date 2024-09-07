package ca.spottedleaf.moonrise.neoforge.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemDistanceManager;
import ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickServerLevel;
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

@Mixin(DistanceManager.class)
abstract class NeoForgeDistanceManagerMixin implements ChunkSystemDistanceManager {

    @Shadow
    @Final
    private Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> forcedTickets;

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

            this.forcedTickets.compute(pos.toLong(), (final Long keyInMap, final SortedArraySet<Ticket<?>> valueInMap) -> {
                final SortedArraySet<Ticket<?>> ret;
                if (valueInMap != null) {
                    ret = valueInMap;
                } else {
                    ret = SortedArraySet.create(4);
                }

                if (ret.add(forceTicket)) {
                    ((ChunkTickServerLevel)NeoForgeDistanceManagerMixin.this.moonrise$getChunkMap().level).moonrise$addPlayerTickingRequest(
                        CoordinateUtils.getChunkX(keyInMap.longValue()), CoordinateUtils.getChunkZ(keyInMap.longValue())
                    );
                }

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

            this.forcedTickets.computeIfPresent(pos.toLong(), (final Long keyInMap, final SortedArraySet<Ticket<?>> valueInMap) -> {
                if (valueInMap.remove(forceTicket)) {
                    ((ChunkTickServerLevel)NeoForgeDistanceManagerMixin.this.moonrise$getChunkMap().level).moonrise$removePlayerTickingRequest(
                        CoordinateUtils.getChunkX(keyInMap.longValue()), CoordinateUtils.getChunkZ(keyInMap.longValue())
                    );
                }

                return valueInMap.isEmpty() ? null : valueInMap;
            });
        }
    }

    /**
     * @reason Only use containsKey, as we fix the leak with this impl
     * @author Spottedleaf
     */
    @Overwrite
    public boolean shouldForceTicks(final long chunkPos) {
        return this.forcedTickets.containsKey(chunkPos);
    }
}
