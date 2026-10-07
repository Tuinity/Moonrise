package ca.spottedleaf.moonrise.mixin.chunk_gen;

import ca.spottedleaf.moonrise.patches.chunk_gen.ChunkGenMaterialRuleContext;
import ca.spottedleaf.moonrise.patches.chunk_gen.ThreadLocalDensityBufferCache;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MaterialRuleContext.class)
abstract class MaterialRuleContextMixin implements ChunkGenMaterialRuleContext {

    @Unique
    private ThreadLocalDensityBufferCache allocator;

    @Override
    public final void moonrise$setDensityBufferAllocator(final ThreadLocalDensityBufferCache allocator) {
        this.allocator = allocator;
    }

    @Override
    public final ThreadLocalDensityBufferCache moonrise$removeDensityBufferAllocator() {
        final ThreadLocalDensityBufferCache ret = this.allocator;
        this.allocator = null;
        return ret;
    }

    /**
     * @reason The allocations here are huge (typically 16x16x(1/2 world height)), and avoidable with pooling.
     * @author Spottedleaf
     */
    @Redirect(
        method = "getDensitiesInChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/densityfunction/DensityBuffer;createUnpooled(I)Lnet/minecraft/world/level/levelgen/densityfunction/DensityBuffer;"
        )
    )
    private DensityBuffer usePooledAllocator(final int size) {
        if (this.allocator == null) {
            return DensityBuffer.createUnpooled(size);
        }
        return this.allocator.allocate(size);
    }
}
