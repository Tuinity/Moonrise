package ca.spottedleaf.moonrise.mixin.render;

import ca.spottedleaf.concurrentutil.executor.thread.BalancedPrioritisedThreadPool;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import net.minecraft.TracingExecutor;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SectionRenderDispatcher.class)
abstract class SectionRenderDispatcherMixin {

    @Unique
    private static final BalancedPrioritisedThreadPool.OrderedStreamGroup.Queue RENDER_EXECUTOR = MoonriseCommon.CLIENT_GROUP.createExecutor();

    /**
     * @reason Change executor to use our thread pool
     *         Note: even at normal priority, our worker pool will try to share resources equally rather than having it
     *         be a free-for-all
     * @author Spottedleaf
     */
    @Redirect(
            method = "schedule",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/TracingExecutor;execute(Ljava/lang/Runnable;)V"
            )
    )
    private void changeExecutor(final TracingExecutor executor, final Runnable task) {
        RENDER_EXECUTOR.queueTask(TracingExecutorAccessor.moonrise$wrapUnnamed(task), Priority.NORMAL);
    }
}
