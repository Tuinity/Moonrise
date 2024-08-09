package ca.spottedleaf.moonrise.common.util;

import ca.spottedleaf.concurrentutil.executor.thread.PrioritisedThreadPool;
import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapterRegistry;
import ca.spottedleaf.moonrise.common.config.moonrise.MoonriseConfig;
import ca.spottedleaf.moonrise.common.config.config.YamlConfig;
import ca.spottedleaf.moonrise.common.config.moonrise.adapter.DefaultedTypeAdapter;
import ca.spottedleaf.moonrise.common.config.moonrise.type.DefaultedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class MoonriseCommon {

    private static final Logger LOGGER = LoggerFactory.getLogger(MoonriseCommon.class);

    public static final PrioritisedThreadPool WORKER_POOL = new PrioritisedThreadPool(
            new Consumer<>() {
                private final AtomicInteger idGenerator = new AtomicInteger();

                @Override
                public void accept(Thread thread) {
                    thread.setDaemon(true);
                    thread.setName("Moonrise Common Worker #" + this.idGenerator.getAndIncrement());
                    thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                        @Override
                        public void uncaughtException(final Thread thread, final Throwable throwable) {
                            LOGGER.error("Uncaught exception in thread " + thread.getName(), throwable);
                        }
                    });
                }
            }
    );
    public static final long WORKER_QUEUE_HOLD_TIME = (long)(20.0e6); // 20ms
    public static final int CLIENT_DIVISION = 0;
    public static final PrioritisedThreadPool.ExecutorGroup RENDER_EXECUTOR_GROUP = MoonriseCommon.WORKER_POOL.createExecutorGroup(CLIENT_DIVISION, 0);
    public static final int SERVER_DIVISION = 1;
    public static final PrioritisedThreadPool.ExecutorGroup PARALLEL_GEN_GROUP = MoonriseCommon.WORKER_POOL.createExecutorGroup(SERVER_DIVISION, 0);
    public static final PrioritisedThreadPool.ExecutorGroup RADIUS_AWARE_GROUP = MoonriseCommon.WORKER_POOL.createExecutorGroup(SERVER_DIVISION, 0);
    public static final PrioritisedThreadPool.ExecutorGroup LOAD_GROUP         = MoonriseCommon.WORKER_POOL.createExecutorGroup(SERVER_DIVISION, 0);

    public static void adjustWorkerThreads(final MoonriseConfig.WorkerPool config) {
        int defaultWorkerThreads = Runtime.getRuntime().availableProcessors() / 2;
        if (defaultWorkerThreads <= 4) {
            defaultWorkerThreads = defaultWorkerThreads <= 3 ? 1 : 2;
        } else {
            defaultWorkerThreads = defaultWorkerThreads / 2;
        }
        defaultWorkerThreads = Integer.getInteger("Moonrise.WorkerThreadCount", Integer.valueOf(defaultWorkerThreads));

        int workerThreads = config.workerThreads;

        if (workerThreads <= 0) {
            workerThreads = defaultWorkerThreads;
        }

        final int ioThreads = Math.max(1, config.ioThreads);

        WORKER_POOL.adjustThreadCount(workerThreads);
        IO_POOL.adjustThreadCount(ioThreads);

        LOGGER.info("Moonrise is using " + workerThreads + " worker threads, " + ioThreads + " I/O threads");
    }

    public static final PrioritisedThreadPool IO_POOL = new PrioritisedThreadPool(
            new Consumer<>() {
                private final AtomicInteger idGenerator = new AtomicInteger();

                @Override
                public void accept(Thread thread) {
                    thread.setDaemon(true);
                    thread.setName("Moonrise I/O Worker #" + this.idGenerator.getAndIncrement());
                    thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                        @Override
                        public void uncaughtException(final Thread thread, final Throwable throwable) {
                            LOGGER.error("Uncaught exception in thread " + thread.getName(), throwable);
                        }
                    });
                }
            }
    );
    public static final long IO_QUEUE_HOLD_TIME = (long)(100.0e6); // 100ms
    public static final PrioritisedThreadPool.ExecutorGroup SERVER_REGION_IO_GROUP = IO_POOL.createExecutorGroup(SERVER_DIVISION, 0);
    public static final PrioritisedThreadPool.ExecutorGroup PROFILER_DUMP_IO_GROUP = IO_POOL.createExecutorGroup(CLIENT_DIVISION, 0);

    private static final File CONFIG_FILE = new File(System.getProperty("Moonrise.ConfigFile", "config/moonrise.yml"));
    private static final TypeAdapterRegistry CONFIG_ADAPTERS = new TypeAdapterRegistry();
    private static final YamlConfig<MoonriseConfig> CONFIG;
    static {
        CONFIG_ADAPTERS.putAdapter(DefaultedValue.class, new DefaultedTypeAdapter());

        try {
            CONFIG = new YamlConfig<>(MoonriseConfig.class, new MoonriseConfig(), CONFIG_ADAPTERS);
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
        final boolean ret = reloadConfig0();

        CONFIG.callInitialisers();

        return ret;
    }

    private static boolean reloadConfig0() {
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

    public static void haltExecutors() {
        MoonriseCommon.WORKER_POOL.shutdown(false);
        LOGGER.info("Awaiting termination of worker pool for up to 60s...");
        if (!MoonriseCommon.WORKER_POOL.join(TimeUnit.SECONDS.toMillis(60L))) {
            LOGGER.error("Worker pool did not shut down in time!");
            MoonriseCommon.WORKER_POOL.halt(false);
        }

        MoonriseCommon.IO_POOL.shutdown(false);
        LOGGER.info("Awaiting termination of I/O pool for up to 60s...");
        if (!MoonriseCommon.IO_POOL.join(TimeUnit.SECONDS.toMillis(60L))) {
            LOGGER.error("I/O pool did not shut down in time!");
            MoonriseCommon.IO_POOL.halt(false);
        }
    }

    private MoonriseCommon() {}
}
