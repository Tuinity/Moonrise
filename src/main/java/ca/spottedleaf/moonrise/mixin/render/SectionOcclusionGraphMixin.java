package ca.spottedleaf.moonrise.mixin.render;

import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.executor.thread.PrioritisedThreadPool;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SectionOcclusionGraph.class)
abstract class SectionOcclusionGraphMixin {

    @Unique
    private static final PrioritisedThreadPool.ExecutorGroup.ThreadPoolExecutor SECTION_OCCLUSION_EXECUTOR = MoonriseCommon.SECTION_OCCLUSION_EXECUTOR_GROUP.createExecutor(
        -1, MoonriseCommon.WORKER_QUEUE_HOLD_TIME, 0
    );

    /**
     * @reason Change executor to use our thread pool
     *         Note: even at normal priority, our worker pool will try to share resources equally rather than having it
     *         be a free-for-all
     * @author jpenilla
     */
    @Redirect(
        method = "scheduleFullUpdate",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;runAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private CompletableFuture<Void> changeExecutor(final Runnable runnable, final Executor executor) {
        final PrioritisedExecutor.PrioritisedTask[] prioritisedTask = new PrioritisedExecutor.PrioritisedTask[1];
        final CompletableFuture<Void> future = new CompletableFuture<>() {
            @Override
            public Void get() throws InterruptedException, ExecutionException {
                prioritisedTask[0].execute();
                return super.get();
            }
        };
        prioritisedTask[0] = SECTION_OCCLUSION_EXECUTOR.queueTask(() -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (final Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        }, Priority.HIGH); // Higher than SectionRenderDispatcherMixin#changeExecutor
        return future;
    }
}
