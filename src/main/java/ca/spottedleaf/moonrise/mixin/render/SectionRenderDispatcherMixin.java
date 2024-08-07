package ca.spottedleaf.moonrise.mixin.render;

import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.executor.thread.PrioritisedThreadPool;
import ca.spottedleaf.concurrentutil.util.Priority;
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
abstract class SectionRenderDispatcherMixin {

    @Unique
    private static final PrioritisedThreadPool.ExecutorGroup.ThreadPoolExecutor RENDER_EXECUTOR = MoonriseCommon.RENDER_EXECUTOR_GROUP.createExecutor(
            -1, MoonriseCommon.WORKER_QUEUE_HOLD_TIME, 0
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
                    RENDER_EXECUTOR.queueTask(task, Priority.NORMAL);
                }
        );
    }
}
