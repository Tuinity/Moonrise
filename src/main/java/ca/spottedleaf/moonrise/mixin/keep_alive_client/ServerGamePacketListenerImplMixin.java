package ca.spottedleaf.moonrise.mixin.keep_alive_client;

import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin extends net.minecraft.server.network.ServerCommonPacketListenerImpl implements ServerGamePacketListener, ServerPlayerConnection, TickablePacketListener {

    public ServerGamePacketListenerImplMixin(net.minecraft.server.MinecraftServer minecraftServer, net.minecraft.network.Connection connection, net.minecraft.server.network.CommonListenerCookie commonListenerCookie) {
        super(minecraftServer, connection, commonListenerCookie);
    }

    /**
     * @reason Testing the explosion patch resulted in me being kicked for keepalive timeout as netty was unable to
     * push enough packets with intellij debugger being the primary bottleneck. It shouldn't be kicking SP players for this.
     * @author Spottedleaf
     */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;disconnect(Lnet/minecraft/network/chat/Component;)V"
            )
    )
    private void refuseSPKick(final ServerGamePacketListenerImpl instance, final Component component) {
        if (Component.translatable("disconnect.timeout").equals(component) &&
                ((ServerGamePacketListenerImplMixin)(Object)instance).isSingleplayerOwner()) {
            return;
        }

        instance.disconnect(component);
    }
}
