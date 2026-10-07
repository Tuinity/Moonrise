package ca.spottedleaf.moonrise.patches.chunk_gen.climate_rtree;

import net.minecraft.world.level.biome.Climate;

public final class RTreeLeafDistance<T> {

    public Climate.RTree.Leaf<T> leaf;
    public long distance;
    public final long[] parameterArray = new long[7];

    public RTreeLeafDistance(final Climate.RTree.Leaf<T> leaf, final long distance) {
        this.leaf = leaf;
        this.distance = distance;
    }
}
