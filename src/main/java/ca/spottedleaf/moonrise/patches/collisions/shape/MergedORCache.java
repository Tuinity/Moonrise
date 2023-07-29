package ca.spottedleaf.moonrise.patches.collisions.shape;

import net.minecraft.world.phys.shapes.VoxelShape;

public record MergedORCache(
    VoxelShape key,
    VoxelShape result
) {

}
