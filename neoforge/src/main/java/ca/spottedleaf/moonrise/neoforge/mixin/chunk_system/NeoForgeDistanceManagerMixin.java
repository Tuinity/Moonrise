package ca.spottedleaf.moonrise.neoforge.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemDistanceManager;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(DistanceManager.class)
abstract class NeoForgeDistanceManagerMixin implements ChunkSystemDistanceManager {

	/**
	 * @reason Route to new chunk system
	 * @author Spottedleaf
	 */
	@Overwrite
	public <T> void addRegionTicket(final TicketType<T> type, final ChunkPos pos, final int radius, final T identifier, final boolean forceTicks) {
		this.moonrise$getChunkHolderManager().addTicketAtLevel(type, pos, ChunkLevel.byStatus(FullChunkStatus.FULL) - radius, identifier);
	}

	/**
	 * @reason Route to new chunk system
	 * @author Spottedleaf
	 */
	@Overwrite
	public <T> void removeRegionTicket(final TicketType<T> type, final ChunkPos pos, final int radius, final T identifier, final boolean forceTicks) {
		this.moonrise$getChunkHolderManager().removeTicketAtLevel(type, pos, ChunkLevel.byStatus(FullChunkStatus.FULL) - radius, identifier);
	}
}
