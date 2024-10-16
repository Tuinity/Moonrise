package ca.spottedleaf.moonrise.mixin.chunk_tick_iteration;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import ca.spottedleaf.moonrise.common.util.SimpleRandom;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkHolder;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemLevelChunk;
import ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickServerLevel;
import net.minecraft.Util;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;
import java.util.Objects;

@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin extends ChunkSource {

    @Shadow
    @Final
    public ServerLevel level;

    @Shadow
    @Final
    public ChunkMap chunkMap;


    @Unique
    private final SimpleRandom shuffleRandom = new SimpleRandom(0L);

    @Unique
    private boolean isChunkNearPlayer(final ChunkMap chunkMap, final ChunkPos chunkPos, final LevelChunk levelChunk) {
        final ChunkData chunkData = ((ChunkSystemChunkHolder)((ChunkSystemLevelChunk)levelChunk).moonrise$getChunkAndHolder().holder())
            .moonrise$getRealChunkHolder().holderData;
        final NearbyPlayers.TrackedChunk nearbyPlayers = chunkData.nearbyPlayers;
        if (nearbyPlayers == null) {
            return false;
        }

        final ReferenceList<ServerPlayer> players = nearbyPlayers.getPlayers(NearbyPlayers.NearbyMapType.SPAWN_RANGE);

        if (players == null) {
            return false;
        }

        final ServerPlayer[] raw = players.getRawDataUnchecked();
        final int len = players.size();

        Objects.checkFromIndexSize(0, len, raw.length);
        for (int i = 0; i < len; ++i) {
            if (chunkMap.playerIsCloseEnoughForSpawning(raw[i], chunkPos)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @reason Use the player ticking chunks list, which already contains chunks that are:
     *         1. block ticking
     *         2. within spawn range (8 chunks on any axis)
     * @author Spottedleaf
     */
    @Overwrite
    private void collectTickingChunks(final List<LevelChunk> list) {
        final ReferenceList<ServerChunkCache.ChunkAndHolder> tickingChunks =
            ((ChunkTickServerLevel)this.level).moonrise$getPlayerTickingChunks();

        final ServerChunkCache.ChunkAndHolder[] raw = tickingChunks.getRawDataUnchecked();
        final int size = tickingChunks.size();

        final ChunkMap chunkMap = this.chunkMap;

        for (int i = 0; i < size; ++i) {
            final ServerChunkCache.ChunkAndHolder chunkAndHolder = raw[i];
            final LevelChunk levelChunk = chunkAndHolder.chunk();

            if (!this.isChunkNearPlayer(chunkMap, levelChunk.getPos(), levelChunk)) {
                continue;
            }

            list.add(levelChunk);
        }
    }

    /**
     * @reason Use random implementation which does not use CAS and has a faster nextInt(int)
     *         function
     * @author Spottedleaf
     */
    @Redirect(
        method = "tickChunks()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/Util;shuffle(Ljava/util/List;Lnet/minecraft/util/RandomSource;)V"
        )
    )
    private <T> void useBetterRandom(final List<T> list, final RandomSource randomSource) {
        this.shuffleRandom.setSeed(randomSource.nextLong());
        Util.shuffle(list, this.shuffleRandom);
    }
}
