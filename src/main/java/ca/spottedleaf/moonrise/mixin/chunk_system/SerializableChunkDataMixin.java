package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkMapChunkScanAccess;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.ChunkStructureScanner;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

@Mixin(SerializableChunkData.class)
abstract class SerializableChunkDataMixin {

    /**
     * @reason Chunk system handles this during full transition
     * @author Spottedleaf
     * @see ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task.ChunkFullTask
     */
    @Redirect(
        method = "read",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;checkConsistencyWithBlocks(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/LevelChunkSection;)V"
        )
    )
    private void skipConsistencyCheck(final PoiManager instance, final SectionPos sectionPos, final LevelChunkSection levelChunkSection) {}

    /**
     * @reason Process scanned structure data on the chunk load executor at the scanner's priority
     */
    @WrapOperation(
        method = "scanStructures",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private static <T, U> CompletableFuture<U> scanStructuresOnLoadExecutor(final CompletableFuture<T> instance, final Function<? super T, ? extends U> function,
                                                                            final Executor executor, final Operation<CompletableFuture<U>> original,
                                                                            @Local(argsOnly = true) final ChunkStructureScanner scanner) {
        return original.call(
            instance, function,
            scanner.storageAccess() instanceof ChunkMapChunkScanAccess chunkMapScanAccess ? chunkMapScanAccess.loadExecutor() : executor
        );
    }

}
