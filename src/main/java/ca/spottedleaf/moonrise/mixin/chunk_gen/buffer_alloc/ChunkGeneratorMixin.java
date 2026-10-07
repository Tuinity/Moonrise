package ca.spottedleaf.moonrise.mixin.chunk_gen.buffer_alloc;

import ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc.ThreadLocalDensityBufferArena;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {

    /**
     * @reason Use thread local allocator
     * @author Spottedleaf
     */
    @Redirect(
        method = "doCreateBiomes",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/RandomState;acquireDensityBufferPool()Lnet/minecraft/world/level/levelgen/densityfunction/DensityBufferPool;"
        )
    )
    private DensityBufferPool allocateDifferentAllocator(final RandomState instance) {
        final ThreadLocalDensityBufferArena allocator = ThreadLocalDensityBufferArena.acquire();
        return allocator == null ? instance.acquireDensityBufferPool() : allocator;
    }

    /**
     * @reason Use thread local allocator
     * @author Spottedleaf
     */
    @Redirect(
        method = "doCreateBiomes",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/RandomState;releaseDensityBufferPool(Lnet/minecraft/world/level/levelgen/densityfunction/DensityBufferPool;)V"
        )
    )
    private void releaseDifferentAllocator(final RandomState instance, final DensityBufferPool pool) {
        if (!(pool instanceof ThreadLocalDensityBufferArena threadLocalDensityBufferArena)) {
            instance.releaseDensityBufferPool(pool);
            return;
        }

        ThreadLocalDensityBufferArena.release(threadLocalDensityBufferArena);
    }
}
