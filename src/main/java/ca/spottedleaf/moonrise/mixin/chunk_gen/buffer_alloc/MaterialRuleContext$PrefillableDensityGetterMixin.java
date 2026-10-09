package ca.spottedleaf.moonrise.mixin.chunk_gen.buffer_alloc;

import ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc.ChunkGenMaterialRuleContext;
import ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc.ThreadLocalFixedDensityBufferCache;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.level.levelgen.material.MaterialRuleContext$PrefillableDensityGetter")
abstract class MaterialRuleContext$PrefillableDensityGetterMixin {

    @Shadow
    @Final
    MaterialRuleContext this$0;

    /**
     * @reason The allocations here are huge (typically 16x16x(1/2 world height)), and avoidable with pooling.
     * @author Spottedleaf
     */
    @Redirect(
        method = "prefillIfNeeded",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/densityfunction/DensityBuffer;createUnpooled(I)Lnet/minecraft/world/level/levelgen/densityfunction/DensityBuffer;"
        )
    )
    private DensityBuffer usePooledAllocator(final int size) {
        final ThreadLocalFixedDensityBufferCache allocator = ((ChunkGenMaterialRuleContext)(Object)this.this$0).moonrise$getDensityBufferAllocator();
        if (allocator == null) {
            return DensityBuffer.createUnpooled(size);
        }
        return allocator.allocate(size);
    }
}
