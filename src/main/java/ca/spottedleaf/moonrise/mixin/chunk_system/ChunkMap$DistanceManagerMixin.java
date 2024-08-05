package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemDistanceManager;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import java.util.concurrent.Executor;

@Mixin(ChunkMap.DistanceManager.class)
public abstract class ChunkMap$DistanceManagerMixin extends net.minecraft.server.level.DistanceManager implements ChunkSystemDistanceManager {

    @Shadow(aliases = "this$0")
    @Final
    ChunkMap field_17443;

    protected ChunkMap$DistanceManagerMixin(Executor executor, Executor executor2) {
        super(executor, executor2);
    }

    /**
     * @reason Destroy old chunk system hooks
     * @author Spottedleaf
     */
    @Override
    @Overwrite
    public boolean isChunkToRemove(final long pos) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final ChunkMap moonrise$getChunkMap() {
        return this.field_17443;
    }
}
