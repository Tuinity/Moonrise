package ca.spottedleaf.moonrise.patches.collisions.block;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface CollisionBlockState {

    // note: this does not consider canOcclude, it is only based on the cached collision shape (i.e hasCache())
    // and whether Shapes.faceShapeOccludes(EMPTY, cached shape) is true
    public boolean moonrise$occludesFullBlock();

    // whether the cached collision shape exists and is empty
    public boolean moonrise$emptyCollisionShape();

    // indicates that occludesFullBlock is cached for the collision shape
    public boolean moonrise$hasCache();

    // note: this is HashCommon#murmurHash3(incremental id); and since murmurHash3 has an inverse function the returned
    // value is still unique
    public int moonrise$uniqueId1();

    // note: this is HashCommon#murmurHash3(incremental id); and since murmurHash3 has an inverse function the returned
    // value is still unique
    public int moonrise$uniqueId2();

    public VoxelShape moonrise$getConstantCollisionShape();

    public AABB moonrise$getConstantCollisionAABB();
}
