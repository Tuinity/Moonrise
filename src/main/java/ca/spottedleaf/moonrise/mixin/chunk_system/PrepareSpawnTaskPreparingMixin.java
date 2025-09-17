package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.MoonriseChunkLoadCounter;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ChunkLoadCounter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.config.PrepareSpawnTask;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

@Mixin(PrepareSpawnTask.Preparing.class)
abstract class PrepareSpawnTaskPreparingMixin {

    @Shadow
    @Nullable
    private CompletableFuture<?> chunkLoadFuture;

    @Redirect(
        method = "<init>",
        at = @At(
            value = "NEW",
            target = "()Lnet/minecraft/server/level/ChunkLoadCounter;"
        )
    )
    private ChunkLoadCounter replaceChunkLoadCounter() {
        return new MoonriseChunkLoadCounter();
    }

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkLoadCounter;track(Lnet/minecraft/server/level/ServerLevel;Ljava/lang/Runnable;)V"
        )
    )
    private void redirectTrack(
        final ChunkLoadCounter instance,
        final ServerLevel level,
        final Runnable task,
        @Local final ChunkPos chunkPos
    ) {
        final CompletableFuture<?> future = ((MoonriseChunkLoadCounter)instance).trackLoadWithRadius(
            level, chunkPos, 3, ChunkStatus.FULL, Priority.HIGH,
            () -> {
                // make sure chunks are kept loaded for the expire duration afterward
                level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_SPAWN, chunkPos, 3);
            }
        );
        this.chunkLoadFuture = future;
    }
}
