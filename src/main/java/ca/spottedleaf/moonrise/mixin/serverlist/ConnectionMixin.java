package ca.spottedleaf.moonrise.mixin.serverlist;

import ca.spottedleaf.moonrise.patches.serverlist.ServerListConnection;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
abstract class ConnectionMixin extends SimpleChannelInboundHandler<Packet<?>> implements ServerListConnection {

    @Shadow
    private Channel channel;

    @Unique
    private static final String TIMEOUT_PIPELINE_NAME = "timeout";

    @Unique
    private static final int DEFAULT_TIMEOUT = 30;

    @Unique
    private volatile int timeout = DEFAULT_TIMEOUT;

    @Override
    public final int moonrise$getReadTimeout() {
        return this.timeout;
    }

    @Override
    public final void moonrise$setReadTimeout(final int seconds) {
        if (this.channel != null) {
            this.channel.eventLoop().execute(() -> {
                this.timeout = seconds;
                if (this.channel != null) {
                    this.channel.pipeline().replace(TIMEOUT_PIPELINE_NAME, TIMEOUT_PIPELINE_NAME, new ReadTimeoutHandler(seconds));
                    this.channel.pipeline().channel().config().setOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, seconds * 1000);
                }
            });
        } else {
            this.timeout = seconds;
        }
    }

    /**
     * @reason Used to set the timeout handler for connectToServer
     * @author Spottedleaf
     */
    @Inject(
            method = "configurePacketHandler",
            at = @At(
                    value = "RETURN"
            )
    )
    private void delayedTimeout(final ChannelPipeline pipeline, final CallbackInfo ci) {
        final int timeout = this.timeout;
        if (timeout != DEFAULT_TIMEOUT) {
            pipeline.replace(TIMEOUT_PIPELINE_NAME, TIMEOUT_PIPELINE_NAME, new ReadTimeoutHandler(timeout));

            pipeline.channel().config().setOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout * 1000);
        }
    }
}
