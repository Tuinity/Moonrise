package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.player.ChunkSystemServerPlayer;
import ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
abstract class PlayerListMixin {

    /**
     * @reason Mark the player as "real", which enables chunk loading
     * @author Spottedleaf
     */
    @Inject(
            method = "placeNewPlayer",
            at = @At(
                    value = "HEAD"
            )
    )
    private void initRealPlayer(final Connection connection, final ServerPlayer serverPlayer,
                                final CommonListenerCookie commonListenerCookie, final CallbackInfo ci) {
        ((ChunkSystemServerPlayer)serverPlayer).moonrise$setRealPlayer(true);
    }

    /**
     * @reason prevent chunk load cycling on join
     * @author jpenilla
     */
    @Inject(
        method = "placeNewPlayer",
        at = @At(
            target = "Lnet/minecraft/server/level/ServerLevel;addNewPlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
            value = "INVOKE"
        )
    )
    private void addJoinTicket(final Connection connection, final ServerPlayer serverPlayer,
                                final CommonListenerCookie commonListenerCookie, final CallbackInfo ci) {
        ((ChunkSystemServerLevel) serverPlayer.serverLevel()).moonrise$getChunkTaskScheduler().chunkHolderManager.addTicketAtLevel(
            RegionizedPlayerChunkLoader.PLAYER_JOIN,
            serverPlayer.chunkPosition(),
            RegionizedPlayerChunkLoader.TICK_TICKET_LEVEL,
            Unit.INSTANCE);
    }

    /**
     * @reason The RegionizedPlayerChunkLoader will handle the VD packet
     * @author Spottedleaf
     */
    @Redirect(
        method = "setViewDistance",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"
        )
    )
    private void doNotAdjustVD(final PlayerList instance, final Packet<?> packet) {}

}
