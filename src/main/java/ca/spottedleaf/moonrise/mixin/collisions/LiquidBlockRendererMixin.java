package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidBlockRendererMixin {

    /**
     * @reason Eliminate uncached extrusion of the water block height shape
     * @author Spottedleaf
     */
    @Overwrite
    private static boolean isFaceOccludedByState(final BlockGetter world, final Direction direction, final float height,
                                                 final BlockPos pos, final BlockState state) {
        if (!state.canOcclude()) {
            return false;
        }

        // check for created shape is empty
        if (height < (float)CollisionUtil.COLLISION_EPSILON) {
            return false;
        }

        final boolean isOne = Math.abs(height - 1.0f) <= (float)CollisionUtil.COLLISION_EPSILON;

        final double heightDouble = (double)height;
        final VoxelShape heightShape;

        // create extruded shape directly
        if (isOne || direction == Direction.DOWN) {
            // if height is one, then obviously it's a block
            // otherwise, extrusion from DOWN will not use the height, in which case it is a block
            heightShape = Shapes.block();
        } else if (direction == Direction.UP) {
            // up is positive, so the first shape passed to blockOccudes must have 1.0 height
            return false;
        } else {
            // the extrusion includes the height
            heightShape = new ArrayVoxelShape(
                    Shapes.block().shape,
                    CollisionUtil.ZERO_ONE,
                    DoubleArrayList.wrap(new double[] { 0.0, heightDouble }),
                    CollisionUtil.ZERO_ONE
            );
        }

        final VoxelShape stateShape = ((CollisionVoxelShape)state.getOcclusionShape(world, pos)).getFaceShapeClamped(direction.getOpposite());

        if (stateShape.isEmpty()) {
            // cannot occlude
            return false;
        }

        // fast check for box
        if (heightShape == stateShape) {
            return true;
        }

        return !Shapes.joinIsNotEmpty(heightShape, stateShape, BooleanOp.ONLY_FIRST);
    }
}
