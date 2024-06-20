package ca.spottedleaf.moonrise.mixin.render;

import ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor;
import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(SectionRenderDispatcher.class)
public abstract class SectionRenderDispatcherMixin {

    @Unique
    private static final PrioritisedExecutor RENDER_EXECUTOR = MoonriseCommon.WORKER_POOL.createExecutor(
            "Moonrise Render Executor", 1, MoonriseCommon.WORKER_THREADS
    );

    /**
     * @reason Change executor to use our thread pool
     *         Note: even at normal priority, our worker pool will try to share resources equally rather than having it
     *         be a free-for-all
     * @author Spottedleaf
     */
    @Redirect(
            method = "runTask",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private <U> CompletableFuture<U> changeExecutor(final Supplier<U> supplier, final Executor executor) {
        return CompletableFuture.supplyAsync(
                supplier,
                (final Runnable task) -> {
                    RENDER_EXECUTOR.queueRunnable(task, PrioritisedExecutor.Priority.NORMAL);
                }
        );
    }
}
