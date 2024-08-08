package ca.spottedleaf.moonrise.mixin.chunk_system;

import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {

    /**
     * @reason Use Runnable:run, as we schedule onto the moonrise common pool
     * @author Spottedleaf
     */
    @Redirect(
            method = "createBiomes",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private <U> CompletableFuture<U> redirectBiomesExecutor(final Supplier<U> supplier, final Executor badExecutor) {
        return CompletableFuture.supplyAsync(supplier, Runnable::run);
    }

    /**
     * @reason Use Runnable:run, as we schedule onto the moonrise common pool
     * @author Spottedleaf
     */
    @Redirect(
            method = "fillFromNoise",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private <U> CompletableFuture<U> redirectNoiseExecutor(final Supplier<U> supplier, final Executor badExecutor) {
        return CompletableFuture.supplyAsync(supplier, Runnable::run);
    }
}
