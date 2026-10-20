package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.util.ConfigHolder;
import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.patches.chunk_system.io.SyncChunkWrites;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.ServerWatchdog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DedicatedServer.class)
abstract class DedicatedServerMixin {

    /**
     * @reason Make the watchdog a tickthread so that it does not trip any thread checks
     *         when performing an emergency save.
     * @author Spottedleaf
     */
    @Redirect(
        method = "initServer",
        at = @At(
            value = "NEW",
            target = "(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            ordinal = 0
        )
    )
    private Thread redirectServerWatchdogThread(final Runnable task) {
        if (!(task instanceof ServerWatchdog)) {
            throw new IllegalStateException("Wrong injection point!");
        }
        return new TickThread(task, "Server Watchdog"); // name is set later anyways
    }

    /**
     * @reason Replace the vanilla sync-chunk-writes option with our own config option.
     * @author jpenilla
     */
    @Overwrite
    public boolean forceSynchronousWrites() {
        return ConfigHolder.getConfig().chunkSaving.syncChunkWrites();
    }

    /**
     * @reason Log the status of sync chunk writes before loading the worlds.
     * @author jpenilla
     */
    @Inject(
        method = "initServer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/dedicated/DedicatedServer;loadLevel()V"
        )
    )
    private void logSyncChunkWrites(final CallbackInfoReturnable<Boolean> cir) {
        SyncChunkWrites.logStatus(
            ((DedicatedServer)(Object)this).getProperties().syncChunkWrites, "sync-chunk-writes in server.properties"
        );
    }
}
