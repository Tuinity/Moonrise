package ca.spottedleaf.moonrise.fabric.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MinecraftServer.class)
abstract class FabricMinecraftServerMixin {

	/**
	 * @reason Make server thread an instance of TickThread for thread checks
	 * @author Spottedleaf
	 */
    @Redirect(
            method = "spin",
            at = @At(
                    value = "NEW",
                    target = "(Ljava/lang/Runnable;Ljava/lang/String;)Ljava/lang/Thread;"
            )
    )
    private static Thread createTickThread(final Runnable target, final String name) {
        return new TickThread(target, name);
    }

}
