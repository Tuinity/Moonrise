package ca.spottedleaf.moonrise.mixin.chunk_tick_iteration;

import ca.spottedleaf.concurrentutil.map.ConcurrentLong2LongChainedHashTable;
import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemLevelChunk;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import ca.spottedleaf.moonrise.patches.chunk_system.ticket.ChunkSystemTicketType;
import ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickDistanceManager;
import ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickServerLevel;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.PrimitiveIterator;

@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {

    @Shadow
    @Final
    private ChunkMap.DistanceManager distanceManager;

    @Shadow
    @Final
    public ServerLevel level;

    @Shadow
    public abstract boolean playerIsCloseEnoughForSpawning(ServerPlayer serverPlayer, ChunkPos chunkPos);

    /**
     * @reason Hook for updating the spawn tracker in distance manager. We add our own hook instead of using the
     *         addPlayer/removePlayer calls as it is more efficient to update the spawn tracker than to add and remove,
     *         as the update method will only update chunks that are different.
     * @author Spottedleaf
     */
    @Inject(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;updatePlayerPos(Lnet/minecraft/server/level/ServerPlayer;)V"
            )
    )
    private void updateSpawnTracker(final ServerPlayer player, final CallbackInfo ci,
                                    @Local(ordinal = 0) final SectionPos oldPos, @Local(ordinal = 1) final SectionPos newPos,
                                    @Local(ordinal = 0) final boolean oldIgnore, @Local(ordinal = 1) final boolean newIgnore) {
        ((ChunkTickDistanceManager)this.distanceManager).moonrise$updatePlayer(player, oldPos, newPos, oldIgnore, newIgnore);
    }

    /**
     * @reason Add hook for spawn tracker
     * @author Spottedleaf
     */
    @Inject(
            method = "updatePlayerStatus",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap$DistanceManager;addPlayer(Lnet/minecraft/core/SectionPos;Lnet/minecraft/server/level/ServerPlayer;)V"
            )
    )
    private void addPlayerToSpawnTracker(final ServerPlayer player, final boolean add, final CallbackInfo ci) {
        ((ChunkTickDistanceManager)this.distanceManager).moonrise$addPlayer(player, SectionPos.of(player));
    }

    /**
     * @reason Remove hook for spawn tracker
     * @author Spottedleaf
     */
    @Inject(
            method = "updatePlayerStatus",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap$DistanceManager;removePlayer(Lnet/minecraft/core/SectionPos;Lnet/minecraft/server/level/ServerPlayer;)V"
            )
    )
    private void removePlayerFromSpawnTracker(final ServerPlayer player, final boolean add, final CallbackInfo ci) {
        ((ChunkTickDistanceManager)this.distanceManager).moonrise$removePlayer(player, SectionPos.of(player));
    }

    /**
     * @reason Avoid checking for DEFAULT state, as we make internal perform this implicitly.
     * @author Spottedleaf
     */
    @Overwrite
    public boolean anyPlayerCloseEnoughForSpawning(final ChunkPos pos) {
        if (((ChunkTickDistanceManager)this.distanceManager).moonrise$hasAnyNearbyNarrow(pos.x, pos.z)) {
            return true;
        }

        return this.anyPlayerCloseEnoughForSpawningInternal(pos);
    }

    /**
     * @reason Use nearby players to avoid iterating over all online players
     * @author Spottedleaf
     */
    @Overwrite
    public boolean anyPlayerCloseEnoughForSpawningInternal(final ChunkPos pos) {
        final ReferenceList<ServerPlayer> players = ((ChunkSystemServerLevel)this.level).moonrise$getNearbyPlayers().getPlayers(
                pos, NearbyPlayers.NearbyMapType.SPAWN_RANGE
        );
        if (players == null) {
            return false;
        }

        final ServerPlayer[] raw = players.getRawDataUnchecked();
        final int len = players.size();

        Objects.checkFromIndexSize(0, len, raw.length);
        for (int i = 0; i < len; ++i) {
            if (this.playerIsCloseEnoughForSpawning(raw[i], pos)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @reason Use nearby players to avoid iterating over all online players
     * @author Spottedleaf
     */
    @Overwrite
    public List<ServerPlayer> getPlayersCloseForSpawning(final ChunkPos pos) {
        final ReferenceList<ServerPlayer> players = ((ChunkSystemServerLevel)this.level).moonrise$getNearbyPlayers().getPlayers(
                pos, NearbyPlayers.NearbyMapType.SPAWN_RANGE
        );
        if (players == null) {
            return new ArrayList<>();
        }

        List<ServerPlayer> ret = null;

        final ServerPlayer[] raw = players.getRawDataUnchecked();
        final int len = players.size();

        Objects.checkFromIndexSize(0, len, raw.length);
        for (int i = 0; i < len; ++i) {
            final ServerPlayer player = raw[i];
            if (this.playerIsCloseEnoughForSpawning(player, pos)) {
                if (ret == null) {
                    ret = new ArrayList<>(len - i);
                    ret.add(player);
                } else {
                    ret.add(player);
                }
            }
        }

        return ret == null ? new ArrayList<>() : ret;
    }

    @Unique
    private boolean isChunkNearPlayer(final ChunkMap chunkMap, final ChunkPos chunkPos, final LevelChunk levelChunk) {
        final ChunkData chunkData = ((ChunkSystemLevelChunk)levelChunk).moonrise$getChunkHolder().holderData;
        final NearbyPlayers.TrackedChunk nearbyPlayers = chunkData.nearbyPlayers;
        if (nearbyPlayers == null) {
            return false;
        }

        if (((ChunkTickDistanceManager)this.distanceManager).moonrise$hasAnyNearbyNarrow(chunkPos.x, chunkPos.z)) {
            return true;
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
     *         1. entity ticking
     *         2. within spawn range (8 chunks on any axis)
     * @author Spottedleaf
     */
    @Inject(
        method = "collectSpawningChunks",
        // use cancellable inject to be compatible with the chunk system's hook here
        cancellable = true,
        at = @At(
            value = "HEAD"
        )
    )
    public void collectSpawningChunks(final List<LevelChunk> list, final CallbackInfo ci) {
        final ReferenceList<LevelChunk> tickingChunks = ((ChunkTickServerLevel)this.level).moonrise$getPlayerTickingChunks();

        final ConcurrentLong2LongChainedHashTable forceSpawningChunks = ((ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler()
            .chunkHolderManager.getTicketCounters(ChunkSystemTicketType.COUNTER_TYPER_NATURAL_SPAWNING_FORCED);

        final LevelChunk[] raw = tickingChunks.getRawDataUnchecked();
        final int size = tickingChunks.size();

        Objects.checkFromToIndex(0, size, raw.length);

        if (forceSpawningChunks != null && !forceSpawningChunks.isEmpty()) {
            // note: expect forceSpawningChunks.size <<< tickingChunks.size
            final LongOpenHashSet seen = new LongOpenHashSet(forceSpawningChunks.size());

            final ChunkHolderManager chunkHolderManager = ((ChunkSystemServerLevel)this.level).moonrise$getChunkTaskScheduler().chunkHolderManager;

            // note: this fixes a bug in neoforge where these chunks don't tick away from a player...
            // note: this is NOT the only problem with their implementation, either...
            for (final PrimitiveIterator.OfLong iterator = forceSpawningChunks.keyIterator(); iterator.hasNext();) {
                final long pos = iterator.nextLong();

                final NewChunkHolder holder = chunkHolderManager.getChunkHolder(pos);

                if (holder == null || !holder.isEntityTickingReady()) {
                    continue;
                }

                seen.add(pos);

                list.add((LevelChunk)holder.getCurrentChunk());
            }

            for (int i = 0; i < size; ++i) {
                final LevelChunk levelChunk = raw[i];

                if (seen.contains(CoordinateUtils.getChunkKey(levelChunk.getPos()))) {
                    // do not add duplicate chunks
                    continue;
                }

                if (!this.isChunkNearPlayer((ChunkMap)(Object)this, levelChunk.getPos(), levelChunk)) {
                    continue;
                }

                list.add(levelChunk);
            }
        } else {
            for (int i = 0; i < size; ++i) {
                final LevelChunk levelChunk = raw[i];

                if (!this.isChunkNearPlayer((ChunkMap)(Object)this, levelChunk.getPos(), levelChunk)) {
                    continue;
                }

                list.add(levelChunk);
            }
        }

        ci.cancel();
    }
}
