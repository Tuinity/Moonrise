package ca.spottedleaf.moonrise.common.util;

import ca.spottedleaf.concurrentutil.executor.standard.PrioritisedThreadPool;
import ca.spottedleaf.moonrise.common.config.MoonriseConfig;
import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapterRegistry;
import ca.spottedleaf.moonrise.common.config.config.YamlConfig;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;

public final class MoonriseCommon {

    private static final Logger LOGGER = LoggerFactory.getLogger(MoonriseCommon.class);

    private static final File CONFIG_FILE = new File("moonrise.yaml");
    private static final YamlConfig<MoonriseConfig> CONFIG;
    static {
        try {
            CONFIG = new YamlConfig<>(MoonriseConfig.class, new MoonriseConfig());
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    static {
        reloadConfig();
    }

    public static MoonriseConfig getConfig() {
        return CONFIG.config;
    }

    public static void reloadConfig() {
        if (CONFIG_FILE.exists()) {
            try {
                CONFIG.load(CONFIG_FILE);
            } catch (final Exception ex) {
                LOGGER.error("Failed to load configuration, using defaults", ex);
                return;
            }
        }

        // write back any changes, or create if needed
        saveConfig();
    }

    public static void saveConfig() {
        try {
            CONFIG.save(CONFIG_FILE);
        } catch (final Exception ex) {
            LOGGER.error("Failed to save configuration", ex);
        }
    }

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

        int workerThreads = MoonriseCommon.getConfig().workerThreads;

        if (workerThreads < 0) {
            workerThreads = defaultWorkerThreads;
        } else {
            workerThreads = Math.max(1, workerThreads);
        }

        WORKER_POOL = new PrioritisedThreadPool(
                "Moonrise Worker Pool", workerThreads,
                (final Thread thread, final Integer id) -> {
                    thread.setName("Moonrise Common Worker #" + id.intValue());
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
