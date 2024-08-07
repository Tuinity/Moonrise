package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.player.ChunkSystemServerPlayer;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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

}
