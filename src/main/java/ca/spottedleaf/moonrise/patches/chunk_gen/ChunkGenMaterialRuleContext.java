package ca.spottedleaf.moonrise.patches.chunk_gen;

public interface ChunkGenMaterialRuleContext {

    public void moonrise$setDensityBufferAllocator(final ThreadLocalDensityBufferCache allocator);

    public ThreadLocalDensityBufferCache moonrise$removeDensityBufferAllocator();
}
