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

    private static final File CONFIG_FILE = new File(System.getProperty("Moonrise.ConfigFile", "moonrise.yml"));
    private static final YamlConfig<MoonriseConfig> CONFIG;
    static {
        try {
            CONFIG = new YamlConfig<>(MoonriseConfig.class, new MoonriseConfig());
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    private static final String CONFIG_HEADER = """
            This is the configuration file for Moonrise.
            
            Each configuration option is prefixed with a comment to explain what it does. Additional changes to this file
            other than modifying the options, such as adding comments, will be overwritten when Moonrise loads the config.
            
            Below are the Moonrise startup flags. Note that startup flags must be placed in the JVM arguments, not
            program arguments.
            -DMoonrise.ConfigFile=<file> - Override the config file location. Might be useful for multiple game versions.
            -DMoonrise.WorkerThreadCount=<number> - Override the auto configured worker thread counts (worker-threads).
            """;

    static {
        reloadConfig();
    }

    public static YamlConfig<MoonriseConfig> getConfigRaw() {
        return CONFIG;
    }

    public static MoonriseConfig getConfig() {
        return CONFIG.config;
    }

    public static boolean reloadConfig() {
        synchronized (CONFIG) {
            if (CONFIG_FILE.exists()) {
                try {
                    CONFIG.load(CONFIG_FILE);
                } catch (final Exception ex) {
                    LOGGER.error("Failed to load configuration, using defaults", ex);
                    return false;
                }
            }

            // write back any changes, or create if needed
            return saveConfig();
        }
    }

    public static boolean saveConfig() {
        synchronized (CONFIG) {
            try {
                CONFIG.save(CONFIG_FILE, CONFIG_HEADER);
                return true;
            } catch (final Exception ex) {
                LOGGER.error("Failed to save configuration", ex);
                return false;
            }
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

        if (workerThreads <= 0) {
            workerThreads = defaultWorkerThreads;
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
