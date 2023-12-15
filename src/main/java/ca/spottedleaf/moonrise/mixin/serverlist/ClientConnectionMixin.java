package ca.spottedleaf.moonrise.mixin.serverlist;

import ca.spottedleaf.moonrise.patches.serverlist.ServerListConnection;
import io.netty.channel.ChannelFuture;
import io.netty.channel.SimpleChannelInboundHandler;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.net.InetSocketAddress;

@Mixin(Connection.class)
public abstract class ClientConnectionMixin extends SimpleChannelInboundHandler<Packet<?>> implements ServerListConnection {

    /**
     * @reason Dirty hack to set the timeout before connecting for server ping list
     * @author Spottedleaf
     */
    @Redirect(
            method = "connectToServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/Connection;connect(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;"
            )
    )
    private static ChannelFuture setReadTimeoutHook(final InetSocketAddress address, final boolean epoll,
                                                    final Connection connection) {
        if (StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk((s -> s.filter(f -> f.getDeclaringClass() == ServerStatusPinger.class).findAny())).isPresent()) {
            final int timeout = 5;

            // reduce timeout to 5s so that non-responding servers release the thread allocation fast
            ((ServerListConnection)connection).moonrise$setReadTimeout(timeout);
        }
        return Connection.connect(address, epoll, connection);
    }
}
