package ca.spottedleaf.moonrise.mixin.chunk_gen.buffer_alloc;

import ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc.ChunkGenMaterialRuleContext;
import ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc.ThreadLocalFixedDensityBufferCache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.ChunkTerrainBuilder;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.Set;

@Mixin(ChunkTerrainBuilder.class)
abstract class ChunkTerrainBuilderMixin {

    @Unique
    private static final ThreadLocal<ThreadLocalFixedDensityBufferCache> BUFFERS = ThreadLocal.withInitial(() -> {
        return new ThreadLocalFixedDensityBufferCache();
    });

    @Shadow
    @Final
    private MaterialRuleContext ruleContext;

    /**
     * @reason Provide a buffer allocator to the context to significantly reduce memory allocations.
     *         The rule is compiled in the constructor, which is where densities are prefilled.
     * @author Spottedleaf
     */
    @Redirect(
        method = "<init>",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/world/level/levelgen/material/MaterialSystem;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/densityfunction/DensityVolume;Lnet/minecraft/world/level/levelgen/densityfunction/DensitySamplerSet;Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/levelgen/VerticalAnchor$Context;Ljava/util/Set;)Lnet/minecraft/world/level/levelgen/material/MaterialRuleContext;"
        )
    )
    private MaterialRuleContext setAllocator(final MaterialSystem system, final RandomState randomState,
                                             final DensityVolume expectedVolume, final DensitySamplerSet densitySamplers,
                                             final BiomeResolver biomeGetter, final VerticalAnchor.Context verticalAnchorContext,
                                             final Set<Holder<Biome>> possibleBiomes) {
        final MaterialRuleContext ret = new MaterialRuleContext(system, randomState, expectedVolume, densitySamplers, biomeGetter, verticalAnchorContext, possibleBiomes);
        final ThreadLocalFixedDensityBufferCache allocator = BUFFERS.get();

        // note: a builder created while another is in use on this thread falls back to unpooled buffers
        ((ChunkGenMaterialRuleContext)(Object)ret).moonrise$setDensityBufferAllocator(allocator == null ? null : allocator.acquire());
        BUFFERS.set(null);

        return ret;
    }

    /**
     * @reason Release the buffer allocator once the chunk is filled
     * @author Spottedleaf
     */
    @WrapMethod(
        method = "fillChunk"
    )
    private void releaseAllocator(final ChunkAccess chunk, final NoiseChunk noiseChunk, final CarvingMask carvingMask,
                                  final Operation<Void> original) {
        try {
            original.call(chunk, noiseChunk, carvingMask);
        } finally {
            // note: the builder is discarded after fillChunk, so we really just have to hope that no mod uses it out of scope...
            final ThreadLocalFixedDensityBufferCache allocator = ((ChunkGenMaterialRuleContext)(Object)this.ruleContext).moonrise$removeDensityBufferAllocator();
            if (allocator != null) {
                allocator.release();
                BUFFERS.set(allocator);
            }
        }
    }
}
