package ca.spottedleaf.moonrise.common.config.moonrise;

import ca.spottedleaf.moonrise.common.config.ui.ClothConfig;
import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.yamlconfig.InitialiseHook;
import ca.spottedleaf.yamlconfig.annotation.Adaptable;
import ca.spottedleaf.yamlconfig.annotation.Serializable;
import ca.spottedleaf.yamlconfig.type.DefaultedValue;
import ca.spottedleaf.yamlconfig.type.Duration;

@Adaptable
public final class MoonriseConfig {

    private static final String BUG_FIX_SECTION = "category.moonrise.bugfixes";
    private static final String CHUNK_SYSTEM_SECTION = "category.moonrise.chunksystem";

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
        public static final class Basic implements InitialiseHook {
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
            @ClothConfig(
                    tooltip = "tooltip.moonrise.loadrate",
                    fieldKeyName = "option.moonrise.loadrate",
                    section = CHUNK_SYSTEM_SECTION
            )
            public double playerMaxLoadRate = -1.0;

            @Serializable(
                    comment = """
                            The maximum rate of chunks to generate for given player, per second. If this value is <= 0,
                            then there is no rate limit.
                            """
            )
            @ClothConfig(
                    tooltip = "tooltip.moonrise.genrate",
                    fieldKeyName = "option.moonrise.genrate",
                    section = CHUNK_SYSTEM_SECTION
            )
            public double playerMaxGenRate = -1.0;

            @Serializable(
                comment = """
                            The delay before chunks are unloaded around players once they leave their view distance.
                            The Vanilla value is 0 ticks. Setting this value higher (i.e 5s) will allow pets to teleport
                            to their owners when they teleport.
                            """
            )
            public Duration playerChunkUnloadDelay = Duration.parse("0t");

            @Override
            public void initialise() {
                RegionizedPlayerChunkLoader.setUnloadDelay(this.playerChunkUnloadDelay.getTimeTicks());
            }
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
                    Configuration options which control the behavior of the common threadpool workers.
                    """
    )
    public WorkerPool workerPool = new WorkerPool();

    @Adaptable
    public static final class WorkerPool implements InitialiseHook {
        @Serializable(
                comment = """
                    Set the number of shared worker threads to be used by chunk rendering,
                    chunk loading, chunk generation. If the value is <= 0, then the number
                    of threads will automatically be determined.
                    """
        )
        @ClothConfig(
                tooltip = "tooltip.moonrise.workerthreads",
                fieldKeyName = "option.moonrise.workerthreads",
                section = CHUNK_SYSTEM_SECTION
        )
        public int workerThreads = -1;

        @Serializable(
            comment = """
                        Set the number of threads dedicated to RegionFile I/O operations.
                        If the value is <= 0, then the number of threads used is 1. Configuring
                        a higher value than 1 is only recommended on SSDs (HDDs scale negatively)
                        and when you have determined that I/O is the bottleneck for chunk loading/saving.
                        """
        )
        @ClothConfig(
            tooltip = "tooltip.moonrise.iothreads",
            fieldKeyName = "option.moonrise.iothreads",
            section = CHUNK_SYSTEM_SECTION
        )
        public int ioThreads = -1;

        @Override
        public void initialise() {
            MoonriseCommon.adjustWorkerThreads(this.workerThreads, this.ioThreads);
        }
    }

    @Serializable
    public ChunkSystem chunkSystem = new ChunkSystem();

    @Adaptable
    public static final class ChunkSystem {
    }

    @Serializable
    public BugFixes bugFixes = new BugFixes();

    @Adaptable
    public static final class BugFixes {

        @Serializable(
                serializedKey = "fix-MC-224294",
                comment = """
                        Fixes https://bugs.mojang.com/browse/MC-224294. By avoiding double ticking lava blocks during
                        chunk random ticking, the cost of world random ticking is significantly reduced.
                        This configuration has two options:
                        true    -> Does not double tick lava. This is different from Vanilla behavior.
                        false   -> Does double tick lava. This is the same behavior as Vanilla.
                        """
        )
        @ClothConfig(
                tooltip = "tooltip.moonrise.fixMC224294",
                fieldKeyName = "option.moonrise.fixMC224294",
                section = BUG_FIX_SECTION
        )
        public boolean fixMC224294 = false;

        @Serializable(
            serializedKey = "fix-MC-159283",
            comment = """
                        Fixes https://bugs.mojang.com/browse/MC-159283. This fixes a bug resulting in the end islands
                        not properly generating at far enough distances in the end. Note that toggling this config option
                        will not affect already generated areas.
                        This configuration has two options:
                        true    -> Fixes the end islands generation. This is different from Vanilla behavior.
                        false   -> Does not fix the end islands generation. This is the same behavior as Vanilla.
                        """
        )
        @ClothConfig(
            tooltip = "tooltip.moonrise.fixMC159283",
            fieldKeyName = "option.moonrise.fixMC159283",
            section = BUG_FIX_SECTION
        )
        public boolean fixMC159283 = false;
    }

    @Serializable
    public Misc misc = new Misc();

    @Adaptable
    public static final class Misc {
    }

    @Serializable
    public TickLoop tickLoop = new TickLoop();

    @Adaptable
    public static final class TickLoop {

        // update comment when changing
        public static final Integer DEFAULT_CATCHUP_TICKS = Integer.valueOf(5);

        @Serializable(
            comment = """
                Configures the maximum number of ticks the server will attempt to catch up on.
                The server will attempt to catch up by "sprinting." This is visually apparent by
                watching mobs move/attack quickly after the server lags.
                
                If the server falls behind by 10 ticks and the configured value is 5, then the server
                will only attempt to catch up by 5 ticks.
                
                Tick catchup exists so that temporary spikes in server lag do not cause the server time
                to fall behind wall time over a long period. However, the speedup caused by the catchup
                process may be disruptive to players.
                
                The default value is set to 5 so that players are not unnecessarily disrupted if the server
                happens to lag for any reason, and so that small lag spikes do not cause de-sync from wall
                time. Note that this value is smaller than Vanilla, which is at least 20 ticks.
                
                To disable tick catchup, set the configured value to 1.
                """
        )
        public DefaultedValue<Integer> catchupTicks = new DefaultedValue<>();
    }
}
