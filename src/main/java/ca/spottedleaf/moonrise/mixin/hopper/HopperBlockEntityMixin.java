package ca.spottedleaf.moonrise.mixin.hopper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import java.util.List;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin extends RandomizableContainerBlockEntity implements Hopper {
    protected HopperBlockEntityMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @Unique
    private static final AABB HOPPER_ITEM_SUCK_OVERALL = Hopper.SUCK.bounds();
    @Unique
    private static final AABB[] HOPPER_ITEM_SUCK_INDIVIDUAL = Hopper.SUCK.toAabbs().toArray(new AABB[0]);

    /**
     * @reason Multiple getEntities calls + the streams here are bad. We can
     * use the overall suck shape AABB for the getEntities call, and check the individual shapes in the predicate.
     * The suck shape is static, so we can cache the overall AABB and the individual AABBs.
     * @author Spottedleaf
     */
    @Overwrite
    public static List<ItemEntity> getItemsAtAndAbove(final Level level, final Hopper hopper) {
        final VoxelShape suckShape = hopper.getSuckShape();
        if (suckShape == Hopper.SUCK) { // support custom mod shapes (????)
            final double shiftX = hopper.getLevelX() - 0.5D;
            final double shiftY = hopper.getLevelY() - 0.5D;
            final double shiftZ = hopper.getLevelZ() - 0.5D;
            return level.getEntitiesOfClass(ItemEntity.class, HOPPER_ITEM_SUCK_OVERALL.move(shiftX, shiftY, shiftZ), (final Entity entity) -> {
                if (!entity.isAlive()) { // EntitySelector.ENTITY_STILL_ALIVE
                    return false;
                }

                for (final AABB aabb : HOPPER_ITEM_SUCK_INDIVIDUAL) {
                    if (aabb.move(shiftX, shiftY, shiftZ).intersects(entity.getBoundingBox())) {
                        return true;
                    }
                }

                return false;
            });
        } else {
            return getItemsAtAndAboveSlow(level, hopper, suckShape);
        }
    }

    @Unique
    private static List<ItemEntity> getItemsAtAndAboveSlow(Level level, Hopper hopper, VoxelShape suckShape) {
        final double shiftX = hopper.getLevelX() - 0.5D;
        final double shiftY = hopper.getLevelY() - 0.5D;
        final double shiftZ = hopper.getLevelZ() - 0.5D;

        suckShape = suckShape.move(shiftX, shiftY, shiftZ);

        final List<AABB> individual = suckShape.toAabbs();

        return level.getEntitiesOfClass(ItemEntity.class, suckShape.bounds(), (final Entity entity) -> {
            if (!entity.isAlive()) { // EntitySelector.ENTITY_STILL_ALIVE
                return false;
            }

            for (final AABB aabb : individual) {
                if (aabb.intersects(entity.getBoundingBox())) {
                    return true;
                }
            }

            return false;
        });
    }
}
