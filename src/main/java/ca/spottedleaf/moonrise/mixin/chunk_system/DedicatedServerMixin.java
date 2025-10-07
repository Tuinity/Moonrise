package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.ServerWatchdog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
}
