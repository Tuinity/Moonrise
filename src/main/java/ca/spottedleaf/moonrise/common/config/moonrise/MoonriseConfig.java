package ca.spottedleaf.moonrise.common.config.moonrise;

import ca.spottedleaf.moonrise.common.config.annotation.Adaptable;
import ca.spottedleaf.moonrise.common.config.annotation.Serializable;
import ca.spottedleaf.moonrise.common.config.moonrise.type.DefaultedValue;
import ca.spottedleaf.moonrise.common.config.type.Duration;

@Adaptable
public final class MoonriseConfig {

    @Serializable(
            comment = """
                    Do not change, used internally.
                    """
    )
    public int version = 1;

    @Serializable
    public ChunkLoading chunkLoading = new ChunkLoading();

    @Adaptable
    public static final class ChunkLoading {

        @Serializable(
                comment = """
                        Chunk loading/generation/sending rate targets for the chunk system.  These values are the
                        maximum rates at which the player chunk loader will attempt to load/generate/send chunks to
                        players. Actual resulting rates will depend on hardware.
                        """
        )
        public Basic basic = new Basic();


        @Adaptable
        public static final class Basic {
            @Serializable(
                    comment = """
                            The maximum rate of chunks to send to any given player, per second. If this value is <= 0,
                            then there is no rate limit.
                            """
            )
            public double playerMaxSendRate = -1.0;

            @Serializable(
                    comment = """
                            The maximum rate of chunks to load from disk for any given player, per second. If this value is <= 0,
                            then there is no rate limit.
                            """
            )
            public double playerMaxLoadRate = -1.0;

            @Serializable(
                    comment = """
                            The maximum rate of chunks to generate for given player, per second. If this value is <= 0,
                            then there is no rate limit.
                            """
            )
            public double playerMaxGenRate = -1.0;
        }

        @Serializable(
                comment = """
                        Advanced configuration options for player chunk loading. You shouldn't be touching these
                        unless you have a reason.
                        """
        )
        public Advanced advanced = new Advanced();

        @Adaptable
        public static final class Advanced {

            @Serializable(
                    comment = """
                            Whether to avoid sending chunks to players who have a view distance
                            configured lower than the server's.
                            """
            )
            public boolean autoConfigSendDistance = true;

            @Serializable(
                    comment = """
                            The maximum amount of pending chunk loads per player. If
                            this value is 0, then the player chunk loader will automatically determine a value. If
                            this value is less-than 0, then there is no limit.
                            
                            This value should be used to tune the saturation of the chunk system.
                            """
            )
            public int playerMaxConcurrentChunkLoads = 0;

            @Serializable(
                    comment = """
                            The maximum amount of pending chunk generations per player. If
                            this value is 0, then the player chunk loader will automatically determine a value. If
                            this value is less-than 0, then there is no limit.
                            
                            This value should be used to tune the saturation of the chunk system.
                            """
            )
            public int playerMaxConcurrentChunkGenerates = 0;
        }
    }

    @Serializable
    public ChunkSaving chunkSaving = new ChunkSaving();

    @Adaptable
    public static final class ChunkSaving {

        @Serializable(
                comment = """
                        The interval at which chunks should be incrementally autosaved.
                        """
        )
        public Duration autoSaveInterval = Duration.parse("5m");

        @Serializable(
                comment = """
                        The maximum number of chunks to incrementally autosave each tick. If
                        the value is <= 0, then no chunks will be incrementally saved.
                        """
        )
        public int maxAutoSaveChunksPerTick = 12;
    }

    @Serializable(
            comment = """
                    Set the number of shared worker threads to be used by chunk rendering,
                    chunk loading, chunk generation. If the value is <= 0, then the number
                    of threads will automatically be determined.
                    """
    )
    public int workerThreads = -1;

    @Serializable
    public ChunkSystem chunkSystem = new ChunkSystem();

    @Adaptable
    public static final class ChunkSystem {

        @Serializable(
                comment = """
                        Set the number of threads dedicated to RegionFile I/O operations.
                        If the value is <= 0, then the number of threads used is 1. Configuring
                        a higher value than 1 is only recommended on SSDs (HDDs scale negatively)
                        and when you have determined that I/O is the bottleneck for chunk loading/saving.
                        """
        )
        public int ioThreads = -1;

        @Serializable(
                comment = """
                        Whether to run generation population in parallel. By default this is set to false,
                        as mods affecting world gen are not safe to run in parallel. If you have no mods affecting
                        gen and are saturating the population generation (~10 threads of the worker pool generating
                        chunks), you may set this to true to possibly increase generation speed.
                        """
        )
        public boolean populationGenParallelism = false;
    }

    @Serializable
    public BugFixes bugFixes = new BugFixes();

    @Adaptable
    public static final class BugFixes {

        public static final Boolean FIX_MC224294_DEFAULT = Boolean.TRUE;

        @Serializable(
                serializedKey = "fix-MC-224294",
                comment = """
                        Fixes https://bugs.mojang.com/browse/MC-224294. By avoiding double ticking lava blocks during
                        chunk random ticking, the cost of world random ticking is significantly reduced.
                        This configuration has three options:
                        default -> Current default is "true".
                        true    -> Does not double tick lava. This is different from Vanilla behavior.
                        false   -> Does double tick lava. This is the same behavior as Vanilla.
                        """
        )
        public DefaultedValue<Boolean> fixMC224294 = new DefaultedValue<>();
    }
}
