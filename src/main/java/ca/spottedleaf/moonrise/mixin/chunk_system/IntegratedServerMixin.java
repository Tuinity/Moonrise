package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.common.util.ConfigHolder;
import ca.spottedleaf.moonrise.patches.chunk_system.io.SyncChunkWrites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IntegratedServer.class)
abstract class IntegratedServerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

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
            target = "Lnet/minecraft/client/server/IntegratedServer;loadLevel()V"
        )
    )
    private void logSyncChunkWrites(final CallbackInfoReturnable<Boolean> cir) {
        SyncChunkWrites.logStatus(this.minecraft.options.syncWrites, "syncChunkWrites in options.txt");
    }
}
