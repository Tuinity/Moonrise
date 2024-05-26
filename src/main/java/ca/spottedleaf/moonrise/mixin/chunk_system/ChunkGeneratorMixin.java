package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevelReader;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    /**
     * @reason Pass the supplier to the mixin below so that we can change the executor to the parameter provided
     * @author Spottedleaf
     */
    @Redirect(
            method = "createBiomes",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private <U> CompletableFuture<U> passSupplier(Supplier<U> supplier, Executor executor) {
        return (CompletableFuture<U>)CompletableFuture.completedFuture(supplier);
    }

    /**
     * @reason Retrieve the supplier from the mixin above so that we can change the executor to the parameter provided
     * @author Spottedleaf
     */
    @Inject(
            method = "createBiomes",
            cancellable = true,
            at = @At(
                    value = "RETURN"
            )
    )
    private void unpackSupplier(Executor executor, RandomState randomState, Blender blender,
                                StructureManager structureManager, ChunkAccess chunkAccess,
                                CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        cir.setReturnValue(
                CompletableFuture.supplyAsync(((CompletableFuture<Supplier<ChunkAccess>>)(CompletableFuture)cir.getReturnValue()).join(), executor)
        );
    }

    /**
     * @reason Bypass thread checks on sync load by using syncLoadNonFull
     * @author Spottedleaf
     */
    @Redirect(
            method = "getStructureGeneratingAt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelReader;getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;)Lnet/minecraft/world/level/chunk/ChunkAccess;"
            )
    )
    private static ChunkAccess redirectToNonSyncLoad(final LevelReader instance, final int x, final int z,
                                                     final ChunkStatus toStatus) {
        return ((ChunkSystemLevelReader)instance).moonrise$syncLoadNonFull(x, z, toStatus);
    }
}
