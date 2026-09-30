package ca.spottedleaf.moonrise.patches.chunk_gen;

import net.minecraft.world.level.biome.Climate;

public interface ChunkGenRTree<T> {

    public T moonrise$findNearest(final Climate.TargetPoint target);

}
