package ca.spottedleaf.moonrise.mixin.util_thread_counts;

import com.google.common.util.concurrent.MoreExecutors;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(Util.class)
public abstract class UtilMixin {

    @Shadow
    @Final
    static Logger LOGGER;

    @Shadow
    private static int getMaxThreads() {
        return 0;
    }

    @Shadow
    private static void onThreadException(Thread thread, Throwable throwable) {

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
            value = Math.min(8, cpus / 2);
        }

        return Mth.clamp(value, min, max);
    }

    /**
     * @reason Don't over-allocate executor threads, they may choke the rest of the game
     *         Additionally, use a thread pool with a fixed core pool count. This is so that thread-locals are
     *         not lost due to some timeout, as G1GC has some issues cleaning up those.
     * @author Spottedleaf
     */
    @Overwrite
    private static ExecutorService makeExecutor(final String name) {
        final int threads = getThreadCounts(1, getMaxThreads());
        if (threads <= 0) {
            return MoreExecutors.newDirectExecutorService();
        }

        final AtomicInteger workerCount = new AtomicInteger();

        return Executors.newFixedThreadPool(threads, (final Runnable run) -> {
            final Thread ret = new Thread(run);
            ret.setName("Worker-" + name + "-" + workerCount.getAndIncrement());

            ret.setUncaughtExceptionHandler((final Thread thread, final Throwable thr) -> {
                LOGGER.error(thread.getName() + "died", thr);

                onThreadException(thread, thr);
            });

            ret.setDaemon(true); // forkjoin workers are daemon
            ret.setPriority(Thread.NORM_PRIORITY - 1); // de-prioritise over main threads (render/server)

            return ret;
        });
    }
}
