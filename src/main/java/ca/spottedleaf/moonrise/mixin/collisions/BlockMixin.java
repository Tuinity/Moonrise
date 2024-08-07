package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Block.class)
abstract class BlockMixin {

    /**
     * @reason Replace with an implementation that does not use join AND one that caches the result per VoxelShape instance
     * @author Spottedleaf
     */
    @Overwrite
    public static boolean isShapeFullBlock(final VoxelShape shape) {
        return ((CollisionVoxelShape)shape).moonrise$isFullBlock();
    }
}
