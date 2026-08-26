package ca.spottedleaf.moonrise.patches.collisions.shape;

public interface CollisionArrayVoxelShape {

    /**
     * Canonicalise coordinate lists only when this shape is known to be retained.
     */
    public void moonrise$internRetainedCoordinates();
}
