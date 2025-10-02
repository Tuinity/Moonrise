package ca.spottedleaf.moonrise.mixin.tick_loop;

import ca.spottedleaf.moonrise.patches.tick_loop.TickLoopBlockableEventLoop;
import net.minecraft.util.profiling.metrics.ProfilerMeasured;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.util.thread.TaskScheduler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.Queue;
import java.util.concurrent.Executor;

@Mixin(BlockableEventLoop.class)
abstract class BlockableEventLoopMixin<R extends Runnable> implements ProfilerMeasured, TaskScheduler<R>, Executor, TickLoopBlockableEventLoop<R> {

    @Shadow
    @Final
    private Queue<R> pendingRunnables;

    @Shadow
    protected abstract void doRunTask(final R task);

    @Override
    public final void moonrise$executeAllRecentInternalTasks() {
        final int pending = this.pendingRunnables.size();

        // note: due to possible recursive execution, we may execute more tasks than we want to

        for (int i = 0; i < pending; ++i) {
            final R run = this.pendingRunnables.poll();
            if (run == null) {
                // recursion
                break;
            }
            this.doRunTask(run);
        }
    }
}
