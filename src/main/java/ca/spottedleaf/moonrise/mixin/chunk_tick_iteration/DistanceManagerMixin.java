package ca.spottedleaf.moonrise.mixin.chunk_tick_iteration;

import ca.spottedleaf.moonrise.common.misc.PositionCountingAreaMap;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickConstants;
import ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickDistanceManager;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.TickingTracker;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DistanceManager.class)
public abstract class DistanceManagerMixin implements ChunkTickDistanceManager {

    @Shadow
    private DistanceManager.FixedPlayerDistanceChunkTracker naturalSpawnChunkCounter;


    @Unique
    private final PositionCountingAreaMap<ServerPlayer> spawnChunkTracker = new PositionCountingAreaMap<>();

    @Override
    public final void moonrise$addPlayer(final ServerPlayer player, final SectionPos pos) {
        this.spawnChunkTracker.add(player, pos.x(), pos.z(), ChunkTickConstants.PLAYER_SPAWN_TRACK_RANGE);
    }

    @Override
    public final void moonrise$removePlayer(final ServerPlayer player, final SectionPos pos) {
        this.spawnChunkTracker.remove(player);
    }

    @Override
    public final void moonrise$updatePlayer(final ServerPlayer player,
                                            final SectionPos oldPos, final SectionPos newPos,
                                            final boolean oldIgnore, final boolean newIgnore) {
        if (newIgnore) {
            this.spawnChunkTracker.remove(player);
        } else {
            this.spawnChunkTracker.addOrUpdate(player, newPos.x(), newPos.z(), ChunkTickConstants.PLAYER_SPAWN_TRACK_RANGE);
        }
    }

    /**
     * @reason Destroy natural spawning tracker field to prevent it from being used
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void destroyFields(final CallbackInfo ci) {
        this.naturalSpawnChunkCounter = null;
    }

    /**
     * @reason Destroy hook to old spawn tracker
     * @author Spottedleaf
     */
    @Redirect(
            method = "addPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/DistanceManager$FixedPlayerDistanceChunkTracker;update(JIZ)V"
            )
    )
    private void skipSpawnTrackerAdd(final DistanceManager.FixedPlayerDistanceChunkTracker instance,
                                     final long pos, final int i0, final boolean b0) {}

    /**
     * @reason Destroy hook to old spawn tracker
     * @author Spottedleaf
     */
    @Redirect(
            method = "removePlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/DistanceManager$FixedPlayerDistanceChunkTracker;update(JIZ)V"
            )
    )
    private void skipSpawnTrackerRemove(final DistanceManager.FixedPlayerDistanceChunkTracker instance,
                                        final long pos, final int i0, final boolean b0) {}

    /**
     * @reason Use spawnChunkTracker instead
     * @author Spottedleaf
     */
    @Overwrite
    public int getNaturalSpawnChunkCount() {
        return this.spawnChunkTracker.getTotalPositions();
    }

    /**
     * @reason Use spawnChunkTracker instead
     * @author Spottedleaf
     */
    @Overwrite
    public boolean hasPlayersNearby(final long pos) {
        return this.spawnChunkTracker.hasObjectsNear(CoordinateUtils.getChunkX(pos), CoordinateUtils.getChunkZ(pos));
    }
}
