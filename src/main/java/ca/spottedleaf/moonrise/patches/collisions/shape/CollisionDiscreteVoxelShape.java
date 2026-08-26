package ca.spottedleaf.moonrise.patches.collisions.shape;

public interface CollisionDiscreteVoxelShape {

    public CachedShapeData moonrise$getOrCreateCachedShapeData();

    public CachedShapeData moonrise$getOrCreateCachedShapeData(boolean internRetainedGeometry);

}
