package ca.spottedleaf.moonrise.mixin.entity_tracker;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.Set;

@Mixin(ChunkMap.TrackedEntity.class)
abstract class TrackedEntityMixin implements EntityTrackerTrackedEntity {
    @Shadow
    @Final
    private Set<ServerPlayerConnection> seenBy;

    @Shadow
    public abstract void updatePlayer(ServerPlayer serverPlayer);

    @Shadow
    public abstract void removePlayer(ServerPlayer serverPlayer);


    @Unique
    private long lastChunkUpdate = -1L;

    @Unique
    private NearbyPlayers.TrackedChunk lastTrackedChunk;

    @Override
    public final void moonrise$tick(final NearbyPlayers.TrackedChunk chunk) {
        if (chunk == null) {
            this.moonrise$clearPlayers();
            return;
        }

        final ReferenceList<ServerPlayer> players = chunk.getPlayers(NearbyPlayers.NearbyMapType.VIEW_DISTANCE);

        if (players == null) {
            this.moonrise$clearPlayers();
            return;
        }

        final long lastChunkUpdate = this.lastChunkUpdate;
        final long currChunkUpdate = chunk.getUpdateCount();
        final NearbyPlayers.TrackedChunk lastTrackedChunk = this.lastTrackedChunk;
        this.lastChunkUpdate = currChunkUpdate;
        this.lastTrackedChunk = chunk;

        final ServerPlayer[] playersRaw = players.getRawDataUnchecked();

        for (int i = 0, len = players.size(); i < len; ++i) {
            final ServerPlayer player = playersRaw[i];
            this.updatePlayer(player);
        }

        if (lastChunkUpdate != currChunkUpdate || lastTrackedChunk != chunk) {
            // need to purge any players possible not in the chunk list
            for (final ServerPlayerConnection conn : new java.util.ArrayList<>(this.seenBy)) {
                final ServerPlayer player = conn.getPlayer();
                if (!players.contains(player)) {
                    this.removePlayer(player);
                }
            }
        }
    }

    @Override
    public final void moonrise$removeNonTickThreadPlayers() {
        boolean foundToRemove = false;
        for (final ServerPlayerConnection conn : this.seenBy) {
            if (!TickThread.isTickThreadFor(conn.getPlayer())) {
                foundToRemove = true;
                break;
            }
        }

        if (!foundToRemove) {
            return;
        }

        for (final ServerPlayerConnection conn : new java.util.ArrayList<>(this.seenBy)) {
            ServerPlayer player = conn.getPlayer();
            if (!TickThread.isTickThreadFor(player)) {
                this.removePlayer(player);
            }
        }
    }

    @Override
    public final void moonrise$clearPlayers() {
        this.lastChunkUpdate = -1;
        this.lastTrackedChunk = null;
        if (this.seenBy.isEmpty()) {
            return;
        }
        for (final ServerPlayerConnection conn : new java.util.ArrayList<>(this.seenBy)) {
            ServerPlayer player = conn.getPlayer();
            this.removePlayer(player);
        }
    }
}
