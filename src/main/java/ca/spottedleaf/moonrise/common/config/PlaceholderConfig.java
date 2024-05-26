package ca.spottedleaf.moonrise.common.config;

public final class PlaceholderConfig {

    public static double chunkLoadingBasic$playerMaxChunkSendRate = -1.0;
    public static double chunkLoadingBasic$playerMaxChunkLoadRate = -1.0;
    public static double chunkLoadingBasic$playerMaxChunkGenerateRate = -1.0;

    public static boolean chunkLoadingAdvanced$autoConfigSendDistance = true;
    public static int chunkLoadingAdvanced$playerMaxConcurrentChunkLoads = 0;
    public static int chunkLoadingAdvanced$playerMaxConcurrentChunkGenerates = 0;

    public static int autoSaveInterval = 60 * 5 * 20; // 5 mins
    public static int maxAutoSaveChunksPerTick = 12;

    public static int chunkSystemIOThreads = -1;
    public static int chunkSystemThreads = -1;
    public static String chunkSystemGenParallelism = "default";

}
