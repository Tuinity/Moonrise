package ca.spottedleaf.moonrise.patches.collisions.shape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface CollisionVoxelShape {

    public double offsetX();

    public double offsetY();

    public double offsetZ();

    public double[] rootCoordinatesX();

    public double[] rootCoordinatesY();

    public double[] rootCoordinatesZ();

    public CachedShapeData getCachedVoxelData();

    // rets null if not possible to represent this shape as one AABB
    public AABB getSingleAABBRepresentation();

    // ONLY USE INTERNALLY, ONLY FOR INITIALISING IN CONSTRUCTOR: VOXELSHAPES ARE STATIC
    public void initCache();

    // this returns empty if not clamped to 1.0 or 0.0 depending on direction
    public VoxelShape getFaceShapeClamped(final Direction direction);

    public boolean isFullBlock();

    public boolean doesClip(final double fromX, final double fromY, final double fromZ,
                            final double directionInvX, final double directionInvY, final double directionInvZ,
                            final double tMax);

    public boolean occludesFullBlock();

    public boolean occludesFullBlockIfCached();

    // uses a cache internally
    public VoxelShape orUnoptimized(final VoxelShape other);
}
