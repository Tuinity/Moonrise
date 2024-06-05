package ca.spottedleaf.moonrise.patches.chunk_system.util;

import net.minecraft.util.SortedArraySet;

public interface ChunkSystemSortedArraySet<T> {

    public SortedArraySet<T> moonrise$copy();

    public Object[] moonrise$copyBackingArray();

    public T moonrise$replace(final T object);

    public T moonrise$removeAndGet(final T object);

}
