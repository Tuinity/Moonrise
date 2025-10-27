package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.concurrent.CompletableFuture;

@Mixin(PlayerSpawnFinder.class)
abstract class PlayerSpawnFinderMixin {

    /**
     * @reason Mark chunks as high priority
     * @author Spottedleaf
     */
    @Redirect(
        method = "scheduleCandidate",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerChunkCache;addTicketAndLoadWithRadius(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;I)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private CompletableFuture<?> markSpawnLoadingAsHighPriority(
        final ServerChunkCache instance, final TicketType ticketType,
        final ChunkPos chunkPos, final int radius) {

        return ((ChunkSystemServerLevel)instance.level).moonrise$getChunkTaskScheduler().chunkHolderManager.addTicketAndLoadWithRadius(
            ticketType, chunkPos, 0, ChunkStatus.FULL, Priority.HIGH
        );
    }
}
