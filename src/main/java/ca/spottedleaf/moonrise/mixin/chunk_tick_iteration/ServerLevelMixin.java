package ca.spottedleaf.moonrise.mixin.chunk_tick_iteration;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemLevelChunk;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickServerLevel;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerLevel.class)
abstract class ServerLevelMixin implements ChunkTickServerLevel {

    @Unique
    private static final ServerChunkCache.ChunkAndHolder[] EMPTY_PLAYER_CHUNK_HOLDERS = new ServerChunkCache.ChunkAndHolder[0];

    @Unique
    private final ReferenceList<ServerChunkCache.ChunkAndHolder> playerTickingChunks = new ReferenceList<>(EMPTY_PLAYER_CHUNK_HOLDERS);

    @Unique
    private final Long2IntOpenHashMap playerTickingRequests = new Long2IntOpenHashMap();

    @Override
    public final ReferenceList<ServerChunkCache.ChunkAndHolder> moonrise$getPlayerTickingChunks() {
        return this.playerTickingChunks;
    }

    @Override
    public final void moonrise$markChunkForPlayerTicking(final LevelChunk chunk) {
        final ChunkPos pos = chunk.getPos();
        if (!this.playerTickingRequests.containsKey(CoordinateUtils.getChunkKey(pos))) {
            return;
        }

        this.playerTickingChunks.add(((ChunkSystemLevelChunk)chunk).moonrise$getChunkAndHolder());
    }

    @Override
    public final void moonrise$removeChunkForPlayerTicking(final LevelChunk chunk) {
        this.playerTickingChunks.remove(((ChunkSystemLevelChunk)chunk).moonrise$getChunkAndHolder());
    }

    @Override
    public final void moonrise$addPlayerTickingRequest(final int chunkX, final int chunkZ) {
        TickThread.ensureTickThread((ServerLevel)(Object)this, chunkX, chunkZ, "Cannot add ticking request async");

        final long chunkKey = CoordinateUtils.getChunkKey(chunkX, chunkZ);

        if (this.playerTickingRequests.addTo(chunkKey, 1) != 0) {
            // already added
            return;
        }

        final NewChunkHolder chunkHolder = ((ChunkSystemServerLevel)(ServerLevel)(Object)this).moonrise$getChunkTaskScheduler()
            .chunkHolderManager.getChunkHolder(chunkKey);

        if (chunkHolder == null || !chunkHolder.isTickingReady()) {
            return;
        }

        this.playerTickingChunks.add(
            ((ChunkSystemLevelChunk)(LevelChunk)chunkHolder.getCurrentChunk()).moonrise$getChunkAndHolder()
        );
    }

    @Override
    public final void moonrise$removePlayerTickingRequest(final int chunkX, final int chunkZ) {
        TickThread.ensureTickThread((ServerLevel)(Object)this, chunkX, chunkZ, "Cannot remove ticking request async");

        final long chunkKey = CoordinateUtils.getChunkKey(chunkX, chunkZ);
        final int val = this.playerTickingRequests.addTo(chunkKey, -1);

        if (val <= 0) {
            throw new IllegalStateException("Negative counter");
        }

        if (val != 1) {
            // still has at least one request
            return;
        }

        final NewChunkHolder chunkHolder = ((ChunkSystemServerLevel)(ServerLevel)(Object)this).moonrise$getChunkTaskScheduler()
            .chunkHolderManager.getChunkHolder(chunkKey);

        if (chunkHolder == null || !chunkHolder.isTickingReady()) {
            return;
        }

        this.playerTickingChunks.remove(
            ((ChunkSystemLevelChunk)(LevelChunk)chunkHolder.getCurrentChunk()).moonrise$getChunkAndHolder()
        );
    }
}
