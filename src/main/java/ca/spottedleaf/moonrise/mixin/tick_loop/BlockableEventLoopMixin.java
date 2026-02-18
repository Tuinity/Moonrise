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
import java.util.function.Predicate;

@Mixin(BlockableEventLoop.class)
abstract class BlockableEventLoopMixin<R extends Runnable> implements ProfilerMeasured, TaskScheduler<R>, Executor, TickLoopBlockableEventLoop<R> {

    @Shadow
    @Final
    private Queue<R> pendingRunnables;

    @Shadow
    protected abstract void doRunTask(final R task);

    @Shadow
    public abstract boolean isSameThread();

    @Override
    public final boolean moonrise$executeAllRecentInternalTasks() {
        final int pending = this.pendingRunnables.size();

        // note: due to possible recursive execution, we may execute more tasks than we want to

        int ran = 0;

        for (int i = 0; i < pending; ++i) {
            final R run = this.pendingRunnables.poll();
            if (run == null) {
                // recursion
                break;
            }
            this.doRunTask(run);
            ++ran;
        }

        return ran > 0;
    }

    @Override
    public boolean moonrise$runTaskIf(final Predicate<? super R> predicate) {
        if (!this.isSameThread()) {
            throw new IllegalStateException();
        }

        final R run = this.pendingRunnables.peek();
        if (run == null || (predicate != null && !predicate.test(run))) {
            return false;
        }

        this.pendingRunnables.remove();

        this.doRunTask(run);

        return true;
    }
}
