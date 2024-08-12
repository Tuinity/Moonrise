package ca.spottedleaf.moonrise.mixin.serverlist;

import net.minecraft.client.multiplayer.resolver.ServerAddressResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.net.InetAddress;
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
                    "*(Lnet/minecraft/client/multiplayer/resolver/ServerAddress;)Ljava/util/Optional;"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/net/InetAddress;getByName(Ljava/lang/String;)Ljava/net/InetAddress;"
            )
    )
    private static InetAddress eliminateRDNS(final String name) throws UnknownHostException {
        final InetAddress ret = InetAddress.getByName(name);

        final byte[] address = ret.getAddress();
        if (address != null) {
            // pass name to prevent rDNS
            return InetAddress.getByAddress(name, address);
        }

        return ret;
    }
}
