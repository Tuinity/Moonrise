package ca.spottedleaf.moonrise.patches.collisions.shape;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface CollisionVoxelShape {

    public double moonrise$offsetX();

    public double moonrise$offsetY();

    public double moonrise$offsetZ();

    public double[] moonrise$rootCoordinatesX();

    public double[] moonrise$rootCoordinatesY();

    public double[] moonrise$rootCoordinatesZ();

    public CachedShapeData moonrise$getCachedVoxelData();

    // rets null if not possible to represent this shape as one AABB
    public AABB moonrise$getSingleAABBRepresentation();

    // Only use internally during construction. VoxelShape geometry is immutable afterwards.
    public void moonrise$initCache();

    // Called only when a long-lived owner (currently BlockState caches) retains the shape.
    public void moonrise$promoteRetainedGeometry();

    // this returns empty if not clamped to 1.0 or 0.0 depending on direction
    public VoxelShape moonrise$getFaceShapeClamped(final Direction direction);

    public boolean moonrise$isFullBlock();

    public boolean moonrise$occludesFullBlock();

    public boolean moonrise$occludesFullBlockIfCached();

    // uses a cache internally
    public VoxelShape moonrise$orUnoptimized(final VoxelShape other);
}
