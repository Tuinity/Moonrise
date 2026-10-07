package ca.spottedleaf.moonrise.mixin.chunk_gen.buffer_alloc;

import ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc.ChunkGenMaterialRuleContext;
import ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc.ThreadLocalFixedDensityBufferCache;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Mixin(MaterialSystem.class)
abstract class MaterialSystemMixin {

    @Unique
    private static final ThreadLocal<ThreadLocalFixedDensityBufferCache> BUFFERS_FOR_BUILD_SURFACE = ThreadLocal.withInitial(() -> {
        return new ThreadLocalFixedDensityBufferCache();
    });

    @Unique
    private static final ThreadLocal<ThreadLocalFixedDensityBufferCache> BUFFERS_FOR_TOP_MATERIAL = ThreadLocal.withInitial(() -> {
        return new ThreadLocalFixedDensityBufferCache();
    });

    /**
     * @reason Provide a buffer allocator to the returned context to significantly reduce memory allocations
     * @author Spottedleaf
     */
    @Redirect(
        method = "buildSurface",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/world/level/levelgen/material/MaterialSystem;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/densityfunction/DensityVolume;Lnet/minecraft/world/level/levelgen/densityfunction/DensitySamplerSet;Ljava/util/function/Function;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;Ljava/util/Set;)Lnet/minecraft/world/level/levelgen/material/MaterialRuleContext;"
        )
    )
    private MaterialRuleContext setAllocatorForSurface(final MaterialSystem system, final RandomState randomState,
                                                       final DensityVolume expectedVolume, final DensitySamplerSet densitySamplers,
                                                       final Function<BlockPos, Holder<Biome>> biomeGetter, final WorldGenerationContext context,
                                                       final Set<Holder<Biome>> possibleBiomes) {
        final MaterialRuleContext ret = new MaterialRuleContext(system, randomState, expectedVolume, densitySamplers, biomeGetter, context, possibleBiomes);

        ((ChunkGenMaterialRuleContext)(Object)ret).moonrise$setDensityBufferAllocator(BUFFERS_FOR_BUILD_SURFACE.get().acquire());

        return ret;
    }

    /**
     * @reason Provide a buffer allocator to the returned context to significantly reduce memory allocations
     * @author Spottedleaf
     */
    @Inject(
        method = "buildSurface",
        at = @At(
            value = "RETURN"
        )
    )
    private void restoreAllocatorForSurface(final CallbackInfo ci) {
        // note: unable to get the MaterialRuleContext as a local variable, so we really just have to hope that no mod uses it out of scope...
        BUFFERS_FOR_BUILD_SURFACE.get().release();
    }


    /**
     * @reason Provide a buffer allocator to the returned context to significantly reduce memory allocations
     * @author Spottedleaf
     */
    @Redirect(
        method = "topMaterial",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/world/level/levelgen/material/MaterialSystem;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/densityfunction/DensityVolume;Lnet/minecraft/world/level/levelgen/densityfunction/DensitySamplerSet;Ljava/util/function/Function;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;Ljava/util/Set;)Lnet/minecraft/world/level/levelgen/material/MaterialRuleContext;"
        )
    )
    private MaterialRuleContext setAllocatorForTop(final MaterialSystem system, final RandomState randomState,
                                                   final DensityVolume expectedVolume, final DensitySamplerSet densitySamplers,
                                                   final Function<BlockPos, Holder<Biome>> biomeGetter, final WorldGenerationContext context,
                                                   final Set<Holder<Biome>> possibleBiomes) {
        final MaterialRuleContext ret = new MaterialRuleContext(system, randomState, expectedVolume, densitySamplers, biomeGetter, context, possibleBiomes);

        ((ChunkGenMaterialRuleContext)(Object)ret).moonrise$setDensityBufferAllocator(BUFFERS_FOR_TOP_MATERIAL.get().acquire());
        BUFFERS_FOR_TOP_MATERIAL.set(null);

        return ret;
    }

    /**
     * @reason Provide a buffer allocator to the returned context to significantly reduce memory allocations
     * @author Spottedleaf
     */
    @Inject(
        method = "topMaterial",
        at = @At(
            value = "RETURN"
        )
    )
    private void restoreAllocatorForTop(final CallbackInfoReturnable<Optional<BlockState>> cir,
                                        final @Local(argsOnly = false, ordinal = 0) MaterialRuleContext context) {
        final ThreadLocalFixedDensityBufferCache allocator = ((ChunkGenMaterialRuleContext)(Object)context).moonrise$removeDensityBufferAllocator();
        if (allocator != null) {
            allocator.release();
            BUFFERS_FOR_TOP_MATERIAL.set(allocator);
        }
    }
}
