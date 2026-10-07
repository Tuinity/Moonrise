package ca.spottedleaf.moonrise.patches.chunk_gen.buffer_alloc;

public interface ChunkGenMaterialRuleContext {

    public void moonrise$setDensityBufferAllocator(final ThreadLocalDensityBufferCache allocator);

    public ThreadLocalDensityBufferCache moonrise$removeDensityBufferAllocator();
}
