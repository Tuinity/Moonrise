package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.ServerWatchdog;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWatchdog.class)
abstract class ServerWatchdogMixin implements Runnable {

    @Shadow
    @Final
    private DedicatedServer server;

    /**
     * @reason Save chunk and player data before exiting.
     * @author Spottedleaf
     */
    @Inject(
        method = "exit",
        at = @At(
            value = "HEAD"
        )
    )
    private void saveBeforeExiting(final CallbackInfo ci) {
        ((ChunkSystemMinecraftServer)this.server).moonrise$issueEmergencySave();
    }
}
