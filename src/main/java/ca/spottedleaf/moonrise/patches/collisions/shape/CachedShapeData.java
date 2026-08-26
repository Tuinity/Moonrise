package ca.spottedleaf.moonrise.patches.collisions.shape;

/**
 * Cached collision data. The voxelSet array must not be modified after construction because instances may be shared.
 */
public record CachedShapeData(
        int sizeX, int sizeY, int sizeZ,
        long[] voxelSet,
        int minFullX, int minFullY, int minFullZ,
        int maxFullX, int maxFullY, int maxFullZ,
        boolean isEmpty, boolean hasSingleAABB
) {
}
