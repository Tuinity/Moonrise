package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.real_dumb_shit.HolderCompletableFuture;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {

    /**
     * @reason Pass the supplier to the mixin below so that we can change the executor to the parameter provided
     * @author Spottedleaf
     */
    @Redirect(
            method = "createBiomes",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private <U> CompletableFuture<U> passSupplierBiomes(Supplier<U> supplier, Executor executor) {
        return (CompletableFuture<U>)CompletableFuture.completedFuture(supplier);
    }

    /**
     * @reason Retrieve the supplier from the mixin above so that we can change the executor to the parameter provided
     * @author Spottedleaf
     */
    @Inject(
            method = "createBiomes",
            cancellable = true,
            at = @At(
                    value = "RETURN"
            )
    )
    private void unpackSupplierBiomes(Executor executor, RandomState randomState, Blender blender,
                                      StructureManager structureManager, ChunkAccess chunkAccess,
                                      CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        cir.setReturnValue(
                CompletableFuture.supplyAsync(((CompletableFuture<Supplier<ChunkAccess>>)(CompletableFuture)cir.getReturnValue()).join(), executor)
        );
    }


    /**
     * @reason Pass the executor tasks to the mixin below so that we can change the executor to the parameter provided
     * @author Spottedleaf
     */
    @Redirect(
            method = "fillFromNoise",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private <U> CompletableFuture<U> passSupplierNoise(Supplier<U> supplier, Executor executor) {
        final HolderCompletableFuture<U> ret = new HolderCompletableFuture<>();

        ret.toExecute.add(() -> {
            try {
                ret.complete(supplier.get());
            } catch (final Throwable throwable) {
                ret.completeExceptionally(throwable);
            }
        });

        return ret;
    }

    /**
     * @reason Retrieve the executor tasks from the mixin above so that we can change the executor to the parameter provided
     * @author Spottedleaf
     */
    @Redirect(
            method = "fillFromNoise",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;whenCompleteAsync(Ljava/util/function/BiConsumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private <T> CompletableFuture<T> unpackSupplierNoise(final CompletableFuture<T> instance, final BiConsumer<? super T, ? super Throwable> action,
                                                         final Executor executor) {
        final HolderCompletableFuture<T> casted = (HolderCompletableFuture<T>)instance;

        for (final Runnable run : casted.toExecute) {
            executor.execute(run);
        }

        // note: executor is the parameter we want
        return instance.whenCompleteAsync(action, executor);
    }
}
