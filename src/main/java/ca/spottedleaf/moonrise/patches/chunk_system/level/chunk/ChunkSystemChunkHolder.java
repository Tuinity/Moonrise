package ca.spottedleaf.moonrise.patches.chunk_system.level.chunk;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.List;

public interface ChunkSystemChunkHolder {

    public NewChunkHolder moonrise$getRealChunkHolder();

    public void moonrise$setRealChunkHolder(final NewChunkHolder newChunkHolder);

    public void moonrise$addReceivedChunk(final ServerPlayer player);

    public void moonrise$removeReceivedChunk(final ServerPlayer player);

    public boolean moonrise$hasChunkBeenSent();

    public boolean moonrise$hasChunkBeenSent(final ServerPlayer to);

    public List<ServerPlayer> moonrise$getPlayers(final boolean onlyOnWatchDistanceEdge);

    public LevelChunk moonrise$getFullChunk();

}
