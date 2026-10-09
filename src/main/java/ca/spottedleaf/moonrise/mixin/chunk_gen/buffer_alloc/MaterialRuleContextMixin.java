package ca.spottedleaf.moonrise.mixin.chunk_gen.buffer_alloc;

import ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc.ChunkGenMaterialRuleContext;
import ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc.ThreadLocalFixedDensityBufferCache;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MaterialRuleContext.class)
abstract class MaterialRuleContextMixin implements ChunkGenMaterialRuleContext {

    @Unique
    private ThreadLocalFixedDensityBufferCache allocator;

    @Override
    public final void moonrise$setDensityBufferAllocator(final ThreadLocalFixedDensityBufferCache allocator) {
        this.allocator = allocator;
    }

    @Override
    public final ThreadLocalFixedDensityBufferCache moonrise$getDensityBufferAllocator() {
        return this.allocator;
    }

    @Override
    public final ThreadLocalFixedDensityBufferCache moonrise$removeDensityBufferAllocator() {
        final ThreadLocalFixedDensityBufferCache ret = this.allocator;
        this.allocator = null;
        return ret;
    }
}
