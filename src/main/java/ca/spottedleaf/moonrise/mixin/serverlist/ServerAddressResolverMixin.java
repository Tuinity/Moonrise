package ca.spottedleaf.moonrise.mixin.serverlist;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddressResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

@Mixin(ServerAddressResolver.class)
interface ServerAddressResolverMixin {

    /**
     * @reason Avoid rDNS lookups for plain IP addresses
     * @author Spottedleaf
     */
    @Redirect(
        method = {
            "method_36903",
            "lambda$static$0"
        },
        at = @At(
            value = "NEW",
            target = "(Ljava/net/InetAddress;I)Ljava/net/InetSocketAddress;"
        )
    )
    private static InetSocketAddress eliminateRDNS(InetAddress addr, final int port,
                                                   @Local(ordinal = 0, argsOnly = true) final ServerAddress serverAddress) throws UnknownHostException {
        final byte[] address = addr.getAddress();
        if (address != null) {
            // pass name to prevent rDNS
            addr = InetAddress.getByAddress(serverAddress.getHost(), address);
        }

        return new InetSocketAddress(addr, port);
    }
}
