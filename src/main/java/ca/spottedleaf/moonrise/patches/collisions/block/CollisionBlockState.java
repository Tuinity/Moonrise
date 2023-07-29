package ca.spottedleaf.moonrise.patches.collisions.block;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface CollisionBlockState {

    // note: this does not consider canOcclude, it is only based on the cached collision shape (i.e hasCache())
    // and whether Shapes.faceShapeOccludes(EMPTY, cached shape) is true
    public boolean occludesFullBlock();

    // whether the cached collision shape exists and is empty
    public boolean emptyCollisionShape();

    // indicates that occludesFullBlock is cached for the collision shape
    public boolean hasCache();

    // note: this is HashCommon#murmurHash3(incremental id); and since murmurHash3 has an inverse function the returned
    // value is still unique
    public int uniqueId1();

    // note: this is HashCommon#murmurHash3(incremental id); and since murmurHash3 has an inverse function the returned
    // value is still unique
    public int uniqueId2();

    public VoxelShape getConstantCollisionShape();

    public AABB getConstantCollisionAABB();

}
