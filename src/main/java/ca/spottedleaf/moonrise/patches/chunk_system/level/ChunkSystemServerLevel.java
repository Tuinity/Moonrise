package ca.spottedleaf.moonrise.patches.chunk_system.level;

import ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread;
import ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.List;
import java.util.function.Consumer;

public interface ChunkSystemServerLevel extends ChunkSystemLevel {

    public ChunkTaskScheduler moonrise$getChunkTaskScheduler();

    public RegionFileIOThread.ChunkDataController moonrise$getChunkDataController();

    public RegionFileIOThread.ChunkDataController moonrise$getPoiChunkDataController();

    public RegionFileIOThread.ChunkDataController moonrise$getEntityChunkDataController();

    public int moonrise$getRegionChunkShift();

    public boolean moonrise$isMarkedClosing();

    public void moonrise$setMarkedClosing(final boolean value);

    public RegionizedPlayerChunkLoader moonrise$getPlayerChunkLoader();

    public void moonrise$loadChunksAsync(final BlockPos pos, final int radiusBlocks,
                                         final PrioritisedExecutor.Priority priority,
                                         final Consumer<List<ChunkAccess>> onLoad);

    public void moonrise$loadChunksAsync(final BlockPos pos, final int radiusBlocks,
                                         final ChunkStatus chunkStatus, final PrioritisedExecutor.Priority priority,
                                         final Consumer<List<ChunkAccess>> onLoad);

    public void moonrise$loadChunksAsync(final int minChunkX, final int maxChunkX, final int minChunkZ, final int maxChunkZ,
                                         final PrioritisedExecutor.Priority priority,
                                         final Consumer<List<ChunkAccess>> onLoad);

    public void moonrise$loadChunksAsync(final int minChunkX, final int maxChunkX, final int minChunkZ, final int maxChunkZ,
                                         final ChunkStatus chunkStatus, final PrioritisedExecutor.Priority priority,
                                         final Consumer<List<ChunkAccess>> onLoad);

    public RegionizedPlayerChunkLoader.ViewDistanceHolder moonrise$getViewDistanceHolder();

    public long moonrise$getLastMidTickFailure();

    public void moonrise$setLastMidTickFailure(final long time);

    public NearbyPlayers moonrise$getNearbyPlayers();
}
