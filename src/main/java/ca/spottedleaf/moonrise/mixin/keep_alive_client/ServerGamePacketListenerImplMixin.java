package ca.spottedleaf.moonrise.mixin.keep_alive_client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerCommonPacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin implements ServerCommonPacketListener {

    @Shadow
    protected abstract boolean isSingleplayerOwner();

    /**
     * @reason Testing the explosion patch resulted in me being kicked for keepalive timeout as netty was unable to
     * push enough packets with intellij debugger being the primary bottleneck. It shouldn't be kicking SP players for this.
     * @author Spottedleaf
     */
    @Redirect(
            method = "keepConnectionAlive",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;disconnect(Lnet/minecraft/network/chat/Component;)V"
            )
    )
    private void refuseSPKick(final ServerCommonPacketListenerImpl instance, final Component component) {
        if (this.isSingleplayerOwner() && Component.translatable("disconnect.timeout").equals(component)) {
            return;
        }

        instance.disconnect(component);
    }
}
