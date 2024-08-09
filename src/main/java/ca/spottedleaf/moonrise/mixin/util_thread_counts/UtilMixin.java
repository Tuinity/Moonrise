package ca.spottedleaf.moonrise.mixin.util_thread_counts;

import net.minecraft.Util;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Mixin(Util.class)
abstract class UtilMixin {

    @Shadow
    private static int getMaxThreads() {
        return 0;
    }

    @Unique
    private static int getThreadCounts(final int min, final int max) {
        final int cpus = Runtime.getRuntime().availableProcessors() / 2;

        final int value;
        if (cpus <= 4) {
            value = cpus <= 2 ? 1 : 2;
        } else if (cpus <= 8) {
            // [5, 8]
            value = cpus <= 6 ? 3 : 4;
        } else {
            value = (cpus - 4) / 2;
        }

        return Mth.clamp(value, min, max);
    }

    /**
     * @reason Don't over-allocate executor threads, they may choke the rest of the game
     *         Additionally, use a thread pool with a fixed core pool count. This is so that thread-locals are
     *         not lost due to some timeout, as G1GC has some issues cleaning up those.
     * @author Spottedleaf
     */
    @Redirect(
        method = "makeExecutor",
        at = @At(
            value = "NEW",
            target = "(ILjava/util/concurrent/ForkJoinPool$ForkJoinWorkerThreadFactory;Ljava/lang/Thread$UncaughtExceptionHandler;Z)Ljava/util/concurrent/ForkJoinPool;"
        )
    )
    private static ForkJoinPool modifyExecutor(final int parallelism, final ForkJoinPool.ForkJoinWorkerThreadFactory factory,
                                               final Thread.UncaughtExceptionHandler handler, final boolean asyncMode) {
        final int threads = getThreadCounts(1, getMaxThreads());

        return new ForkJoinPool(threads, factory, handler, asyncMode, 0, Integer.MAX_VALUE, 1, null, 365, TimeUnit.DAYS);
    }
}
