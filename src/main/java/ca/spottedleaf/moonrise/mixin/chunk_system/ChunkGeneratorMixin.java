package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevelReader;
import com.llamalad7.mixinextras.sugar.Local;
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
     * @reason Use Runnable:run, as we schedule onto the moonrise common pool
     * @author Spottedleaf
     */
    @Redirect(
            method = "createBiomes",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private <U> CompletableFuture<U> redirectBiomesExecutor(final Supplier<U> supplier, final Executor badExecutor) {
        return CompletableFuture.supplyAsync(supplier, Runnable::run);
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
