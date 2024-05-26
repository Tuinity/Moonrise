package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerChunkCache.MainThreadExecutor.class)
public abstract class ServerChunkCache$MainThreadExecutorMixin extends BlockableEventLoop<Runnable> {

    @Shadow
    @Final
    ServerChunkCache field_18810;

    protected ServerChunkCache$MainThreadExecutorMixin(String string) {
        super(string);
    }

    /**
     * @reason Support new chunk system
     * @author Spottedleaf
     */
    @Override
    @Overwrite
    public boolean pollTask() {
        final ServerChunkCache serverChunkCache = this.field_18810;
        if (serverChunkCache.runDistanceManagerUpdates()) {
            return true;
        } else {
            return super.pollTask() | ((ChunkSystemServerLevel)serverChunkCache.level).moonrise$getChunkTaskScheduler().executeMainThreadTask();
        }
    }
}
