package ca.spottedleaf.moonrise.common.util;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.common.config.moonrise.MoonriseConfig;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import ca.spottedleaf.yamlconfig.config.YamlConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;

public final class ConfigHolder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigHolder.class);

    private static final File CONFIG_FILE = new File(System.getProperty(PlatformHooks.get().getBrand() + ".ConfigFile", "config/moonrise.yml"));
    private static final TypeAdapterRegistry CONFIG_ADAPTERS = new TypeAdapterRegistry();
    private static final YamlConfig<MoonriseConfig> CONFIG;
    static {
        try {
            CONFIG = new YamlConfig<>(MoonriseConfig.class, new MoonriseConfig(), CONFIG_ADAPTERS);
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    private static final String CONFIG_HEADER = String.format("""
            This is the configuration file for Moonrise.
            
            Each configuration option is prefixed with a comment to explain what it does. Additional changes to this file
            other than modifying the options, such as adding comments, will be overwritten when Moonrise loads the config.
            
            Below are the Moonrise startup flags. Note that startup flags must be placed in the JVM arguments, not
            program arguments.
            -D%1$s.ConfigFile=<file> - Override the config file location. Might be useful for multiple game versions.
            -D%1$s.WorkerThreadCount=<number> - Override the auto configured worker thread counts (worker-threads).
            -D%1$s.MaxViewDistance=<number> - Overrides the maximum view distance, should only use for debugging purposes.
            """, PlatformHooks.get().getBrand());

    static {
        reloadConfig(true);
    }

    public static YamlConfig<MoonriseConfig> getConfigRaw() {
        return CONFIG;
    }

    public static MoonriseConfig getConfig() {
        return CONFIG.config;
    }

    public static boolean reloadConfig() {
        return reloadConfig(false);
    }

    public static boolean reloadConfig(final boolean startup) {
        synchronized (CONFIG) {
            if (CONFIG_FILE.exists()) {
                try {
                    CONFIG.load(CONFIG_FILE);
                } catch (final Exception ex) {
                    if (startup) {
                        LOGGER.error("Failed to load configuration, using defaults", ex);
                        CONFIG.callInitialisers();
                    } else {
                        LOGGER.error("Failed to reload configuration", ex);
                    }
                    return false;
                }
            }

            CONFIG.callInitialisers();

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

    private ConfigHolder() {}
}
