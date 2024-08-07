package ca.spottedleaf.moonrise.mixin.chunk_tick_iteration;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickDistanceManager;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {

    @Shadow
    @Final
    private ChunkMap.DistanceManager distanceManager;

    @Shadow
    @Final
    public ServerLevel level;

    @Shadow
    protected abstract boolean playerIsCloseEnoughForSpawning(ServerPlayer serverPlayer, ChunkPos chunkPos);

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
     * @reason Use nearby players to avoid iterating over all online players
     * @author Spottedleaf
     */
    @Overwrite
    public boolean anyPlayerCloseEnoughForSpawning(final ChunkPos pos) {
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
        final List<ServerPlayer> ret = new ArrayList<>();

        final ReferenceList<ServerPlayer> players = ((ChunkSystemServerLevel)this.level).moonrise$getNearbyPlayers().getPlayers(
                pos, NearbyPlayers.NearbyMapType.SPAWN_RANGE
        );
        if (players == null) {
            return ret;
        }

        final ServerPlayer[] raw = players.getRawDataUnchecked();
        final int len = players.size();

        Objects.checkFromIndexSize(0, len, raw.length);
        for (int i = 0; i < len; ++i) {
            final ServerPlayer player = raw[i];
            if (this.playerIsCloseEnoughForSpawning(player, pos)) {
                ret.add(player);
            }
        }

        return ret;
    }
}
