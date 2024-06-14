package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.status.ChunkSystemChunkStep;
import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStatusTask;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkStep.class)
public abstract class ChunkStepMixin implements ChunkSystemChunkStep {

    @Shadow
    public abstract int getAccumulatedRadiusOf(ChunkStatus chunkStatus);

    @Unique
    private ChunkStatus[] byRadius;

    /**
     * @reason Moonrise schedules chunks by requesting neighbours be brought up to a status, rather than scheduling
     *         neighbours incrementally. As a result, we need a mapping of neighbour radius -> max chunk status, which
     *         we build here.
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void init(ChunkStatus chunkStatus, ChunkDependencies chunkDependencies, ChunkDependencies chunkDependencies2,
                      int i, ChunkStatusTask chunkStatusTask, CallbackInfo ci) {
        this.byRadius = new ChunkStatus[this.getAccumulatedRadiusOf(ChunkStatus.EMPTY) + 1];
        this.byRadius[0] = chunkStatus.getParent();

        for (ChunkStatus status = chunkStatus.getParent(); status != ChunkStatus.EMPTY; status = status.getParent()) {
            final int radius = this.getAccumulatedRadiusOf(status);

            for (int j = 0; j <= radius; ++j) {
                if (this.byRadius[j] == null) {
                    this.byRadius[j] = status;
                }
            }
        }
    }

    @Override
    public final ChunkStatus moonrise$getRequiredStatusAtRadius(final int radius) {
        return this.byRadius[radius];
    }
}
