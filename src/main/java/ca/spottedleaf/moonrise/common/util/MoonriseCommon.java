package ca.spottedleaf.moonrise.common.util;

import ca.spottedleaf.concurrentutil.executor.standard.PrioritisedThreadPool;
import ca.spottedleaf.moonrise.common.config.PlaceholderConfig;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MoonriseCommon {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkTaskScheduler.class);

    public static final PrioritisedThreadPool WORKER_POOL;
    public static final int WORKER_THREADS;
    static {
        int defaultWorkerThreads = Runtime.getRuntime().availableProcessors() / 2;
        if (defaultWorkerThreads <= 4) {
            defaultWorkerThreads = defaultWorkerThreads <= 3 ? 1 : 2;
        } else {
            defaultWorkerThreads = defaultWorkerThreads / 2;
        }
        defaultWorkerThreads = Integer.getInteger("Moonrise.WorkerThreadCount", Integer.valueOf(defaultWorkerThreads));

        int workerThreads = PlaceholderConfig.workerThreads;

        if (workerThreads < 0) {
            workerThreads = defaultWorkerThreads;
        } else {
            workerThreads = Math.max(1, workerThreads);
        }

        WORKER_POOL = new PrioritisedThreadPool(
                "Moonrise Chunk System Worker Pool", workerThreads,
                (final Thread thread, final Integer id) -> {
                    thread.setName("Moonrise Chunk System Worker #" + id.intValue());
                    thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                        @Override
                        public void uncaughtException(final Thread thread, final Throwable throwable) {
                            LOGGER.error("Uncaught exception in thread " + thread.getName(), throwable);
                        }
                    });
                }, (long)(20.0e6)); // 20ms
        WORKER_THREADS = workerThreads;
    }

    private MoonriseCommon() {}

}
