package ca.spottedleaf.moonrise.patches.chunk_system.io;

import ca.spottedleaf.moonrise.common.util.ConfigHolder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class SyncChunkWrites {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Logs whether sync chunk writes are enabled through our config, or whether the ignored vanilla option is enabled.
     * @param vanillaEnabled Whether the vanilla option is enabled
     * @param vanillaOption Description of the vanilla option, such as {@code sync-chunk-writes in server.properties}
     */
    public static void logStatus(final boolean vanillaEnabled, final String vanillaOption) {
        if (ConfigHolder.getConfig().chunkSaving.syncChunkWrites()) {
            LOGGER.warn(
                "Synchronous chunk writes are enabled (chunk-saving.sync-chunk-writes in the Moonrise config). "
                    + "Moonrise's region file handling makes them unnecessary, and they significantly reduce chunk saving performance."
            );
        } else if (vanillaEnabled) {
            LOGGER.info(
                vanillaOption + " is ignored, Moonrise uses chunk-saving.sync-chunk-writes in its config instead (currently disabled)."
            );
        }
    }

    private SyncChunkWrites() {}
}
