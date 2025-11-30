package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

@Mixin(PlayerSpawnFinder.class)
abstract class PlayerSpawnFinderMixin {

    @Shadow @Final
    private ServerLevel level;

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
            ticketType, chunkPos, radius, ChunkStatus.FULL, Priority.HIGH
        );
    }

    @WrapOperation(
        method = "scheduleCandidate",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;whenCompleteAsync(Ljava/util/function/BiConsumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private <T> CompletableFuture<T> replaceCallbackExecutor(
        final CompletableFuture<T> instance,
        final BiConsumer<? super T, ? super Throwable> action,
        final Executor $, // replaced
        final Operation<CompletableFuture<T>> original
    ) {
        return original.call(instance, action, this.level.getChunkSource().mainThreadProcessor);
    }

}
