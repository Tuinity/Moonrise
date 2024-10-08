package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(ChunkStatus.class)
abstract class ChunkStatusMixin implements ChunkSystemChunkStatus {

    @Unique
    private boolean isParallelCapable;

    @Unique
    private boolean emptyLoadTask;

    @Unique
    private int writeRadius = -1;

    @Unique
    private ChunkStatus nextStatus = (ChunkStatus)(Object)this;

    @Unique
    private final AtomicBoolean warnedAboutNoImmediateComplete = new AtomicBoolean();

    @Override
    public final boolean moonrise$isParallelCapable() {
        return this.isParallelCapable;
    }

    @Override
    public final void moonrise$setParallelCapable(final boolean value) {
        this.isParallelCapable = value;
    }

    @Override
    public final int moonrise$getWriteRadius() {
        return this.writeRadius;
    }

    @Override
    public final void moonrise$setWriteRadius(final int value) {
        this.writeRadius = value;
    }

    @Override
    public final ChunkStatus moonrise$getNextStatus() {
        return this.nextStatus;
    }

    @Override
    public final boolean moonrise$isEmptyLoadStatus() {
        return this.emptyLoadTask;
    }

    @Override
    public void moonrise$setEmptyLoadStatus(final boolean value) {
        this.emptyLoadTask = value;
    }

    @Override
    public final boolean moonrise$isEmptyGenStatus() {
        return (Object)this == ChunkStatus.EMPTY;
    }

    @Override
    public final AtomicBoolean moonrise$getWarnedAboutNoImmediateComplete() {
        return this.warnedAboutNoImmediateComplete;
    }

    /**
     * @reason Initialise default values for fields and nextStatus
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void initFields(ChunkStatus prevStatus, EnumSet<Heightmap.Types> enumSet, ChunkType chunkType, CallbackInfo ci) {
        if (prevStatus != null) {
            ((ChunkStatusMixin)(Object)prevStatus).nextStatus = (ChunkStatus)(Object)this;
        }
    }
}
