package ca.spottedleaf.moonrise.mixin.chunk_system;

import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.function.UnaryOperator;

@Mixin(ChunkPyramid.class)
public abstract class ChunkPyramidMixin {

    /**
     * @reason Starlight does not require loading neighbours for light data, as Starlight performs chunk edge checks on
     *         both light loading and light generation. As a result, we can skip loading the 8 neighbours for a basic
     *         chunk load - bringing the total access radius for a pure chunk load to 0.
     * @author Spottedleaf
     */
    @Redirect(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/status/ChunkPyramid$Builder;step(Lnet/minecraft/world/level/chunk/status/ChunkStatus;Ljava/util/function/UnaryOperator;)Lnet/minecraft/world/level/chunk/status/ChunkPyramid$Builder;",
                    ordinal = 21
            )
    )
    private static ChunkPyramid.Builder removeLoadLightDependency(final ChunkPyramid.Builder instance,
                                                                  final ChunkStatus chunkStatus, final UnaryOperator<ChunkStep.Builder> unaryOperator) {
        if (chunkStatus != ChunkStatus.LIGHT) {
            throw new RuntimeException("Redirected wrong target");
        }
        if (ChunkPyramid.GENERATION_PYRAMID == null || ChunkPyramid.LOADING_PYRAMID != null) {
            throw new RuntimeException("Redirected wrong target");
        }
        return instance.step(
                chunkStatus,
                (builder -> {
                    return builder.setTask(ChunkStatusTasks::light);
                })
        );
    }

}
