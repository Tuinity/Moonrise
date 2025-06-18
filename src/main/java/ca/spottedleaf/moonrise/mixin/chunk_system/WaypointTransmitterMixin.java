package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.chunk_system.player.ChunkSystemServerPlayer;
import ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(WaypointTransmitter.class)
interface WaypointTransmitterMixin extends Waypoint {

    /**
     * @reason Redirect to the new player chunk loader, as the Vanilla chunk tracker is set to empty on Moonrise
     * @author Spottedleaf
     */
    @Overwrite
    public static boolean isChunkVisible(final ChunkPos pos, final ServerPlayer player) {
        final RegionizedPlayerChunkLoader.PlayerChunkLoaderData playerChunkLoader = ((ChunkSystemServerPlayer)player).moonrise$getChunkLoader();
        return playerChunkLoader != null && playerChunkLoader.getSentChunksRaw().contains(CoordinateUtils.getChunkKey(pos));
    }
}
