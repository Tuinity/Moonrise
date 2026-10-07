package ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc;

public interface ChunkGenMaterialRuleContext {

    public void moonrise$setDensityBufferAllocator(final ThreadLocalFixedDensityBufferCache allocator);

    public ThreadLocalFixedDensityBufferCache moonrise$removeDensityBufferAllocator();
}
