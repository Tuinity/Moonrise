package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
abstract class EntityMixin {

    @Shadow
    private Level level;

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract float maxUpStep();

    @Shadow
    public boolean noPhysics;

    @Shadow
    private EntityDimensions dimensions;

    @Shadow
    public abstract Vec3 getEyePosition();

    @Shadow
    public abstract boolean onGround();

    @Unique
    private static float[] calculateStepHeights(final AABB box, final List<VoxelShape> voxels, final List<AABB> aabbs, final float stepHeight,
                                                final float collidedY) {
        final FloatArraySet ret = new FloatArraySet();

        for (int i = 0, len = voxels.size(); i < len; ++i) {
            final VoxelShape shape = voxels.get(i);

            final double[] yCoords = ((CollisionVoxelShape)shape).moonrise$rootCoordinatesY();
            final double yOffset = ((CollisionVoxelShape)shape).moonrise$offsetY();

            for (final double yUnoffset : yCoords) {
                final double y = yUnoffset + yOffset;

                final float step = (float)(y - box.minY);

                if (step > stepHeight) {
                    break;
                }

                if (step < 0.0f || !(step != collidedY)) {
                    continue;
                }

                ret.add(step);
            }
        }

        for (int i = 0, len = aabbs.size(); i < len; ++i) {
            final AABB shape = aabbs.get(i);

            final float step1 = (float)(shape.minY - box.minY);
            final float step2 = (float)(shape.maxY - box.minY);

            if (!(step1 < 0.0f) && step1 != collidedY && !(step1 > stepHeight)) {
                ret.add(step1);
            }

            if (!(step2 < 0.0f) && step2 != collidedY && !(step2 > stepHeight)) {
                ret.add(step2);
            }
        }

        final float[] steps = ret.toFloatArray();
        FloatArrays.unstableSort(steps);
        return steps;
    }

    /**
     * @author Spottedleaf
     * @reason Optimise entire method
     */
    @Overwrite
    private Vec3 collide(final Vec3 movement) {
        final boolean xZero = movement.x == 0.0;
        final boolean yZero = movement.y == 0.0;
        final boolean zZero = movement.z == 0.0;
        if (xZero & yZero & zZero) {
            return movement;
        }

        final AABB currentBox = this.getBoundingBox();

        final List<VoxelShape> potentialCollisionsVoxel = new ArrayList<>();
        final List<AABB> potentialCollisionsBB = new ArrayList<>();

        final AABB initialCollisionBox;
        if (xZero & zZero) {
            // note: xZero & zZero -> collision on x/z == 0 -> no step height calculation
            // this specifically optimises entities standing still
            initialCollisionBox = movement.y < 0.0 ?
                CollisionUtil.cutDownwards(currentBox, movement.y) : CollisionUtil.cutUpwards(currentBox, movement.y);
        } else {
            initialCollisionBox = currentBox.expandTowards(movement);
        }

        final List<AABB> entityAABBs = new ArrayList<>();
        CollisionUtil.getEntityHardCollisions(
            this.level, (Entity)(Object)this, initialCollisionBox, entityAABBs, 0, null
        );

        CollisionUtil.getCollisionsForBlocksOrWorldBorder(
            this.level, (Entity)(Object)this, initialCollisionBox, potentialCollisionsVoxel, potentialCollisionsBB,
            CollisionUtil.COLLISION_FLAG_CHECK_BORDER, null
        );
        potentialCollisionsBB.addAll(entityAABBs);
        final Vec3 collided = CollisionUtil.performCollisions(movement, currentBox, potentialCollisionsVoxel, potentialCollisionsBB);

        final boolean collidedX = collided.x != movement.x;
        final boolean collidedY = collided.y != movement.y;
        final boolean collidedZ = collided.z != movement.z;

        final boolean collidedDownwards = collidedY && movement.y < 0.0;

        final double stepHeight = (double)this.maxUpStep();

        if (stepHeight <= 0.0 || (!collidedDownwards && !this.onGround()) || (!collidedX && !collidedZ)) {
            return collided;
        }

        final AABB collidedYBox = collidedDownwards ? currentBox.move(0.0, collided.y, 0.0) : currentBox;
        AABB stepRetrievalBox = collidedYBox.expandTowards(movement.x, stepHeight, movement.z);
        if (!collidedDownwards) {
            stepRetrievalBox = stepRetrievalBox.expandTowards(0.0, (double)-1.0E-5F, 0.0);
        }

        final List<VoxelShape> stepVoxels = new ArrayList<>();
        final List<AABB> stepAABBs = entityAABBs;

        CollisionUtil.getCollisionsForBlocksOrWorldBorder(
            this.level, (Entity)(Object)this, stepRetrievalBox, stepVoxels, stepAABBs,
            CollisionUtil.COLLISION_FLAG_CHECK_BORDER, null
        );

        for (final float step : calculateStepHeights(collidedYBox, stepVoxels, stepAABBs, (float)stepHeight, (float)collided.y)) {
            final Vec3 stepResult = CollisionUtil.performCollisions(new Vec3(movement.x, (double)step, movement.z), collidedYBox, stepVoxels, stepAABBs);
            if (stepResult.horizontalDistanceSqr() > collided.horizontalDistanceSqr()) {
                return stepResult.add(0.0, collidedYBox.minY - currentBox.minY, 0.0);
            }
        }

        return collided;
    }

    /**
     * @author Spottedleaf
     * @reason Optimise entire method - removed the stream + use optimised collisions
     */
    @Overwrite
    public boolean isInWall() {
        if (this.noPhysics) {
            return false;
        }

        final float reducedWith = this.dimensions.width() * 0.8F;
        final AABB box = AABB.ofSize(this.getEyePosition(), reducedWith, 1.0E-6D, reducedWith);

        if (CollisionUtil.isEmpty(box)) {
            return false;
        }

        final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();

        final int minX = Mth.floor(box.minX);
        final int minY = Mth.floor(box.minY);
        final int minZ = Mth.floor(box.minZ);
        final int maxX = Mth.floor(box.maxX);
        final int maxY = Mth.floor(box.maxY);
        final int maxZ = Mth.floor(box.maxZ);

        final ChunkSource chunkProvider = this.level.getChunkSource();

        long lastChunkKey = ChunkPos.INVALID_CHUNK_POS;
        LevelChunk lastChunk = null;
        for (int fz = minZ; fz <= maxZ; ++fz) {
            tempPos.setZ(fz);
            for (int fx = minX; fx <= maxX; ++fx) {
                final int newChunkX = fx >> 4;
                final int newChunkZ = fz >> 4;
                final LevelChunk chunk = lastChunkKey == (lastChunkKey = CoordinateUtils.getChunkKey(newChunkX, newChunkZ)) ?
                        lastChunk : (lastChunk = (LevelChunk)chunkProvider.getChunk(newChunkX, newChunkZ, ChunkStatus.FULL, true));
                tempPos.setX(fx);
                for (int fy = minY; fy <= maxY; ++fy) {
                    tempPos.setY(fy);

                    final BlockState state = chunk.getBlockState(tempPos);

                    if (((CollisionBlockState)state).moonrise$emptyCollisionShape() || !state.isSuffocating(this.level, tempPos)) {
                        continue;
                    }

                    // Yes, it does not use the Entity context stuff.
                    final VoxelShape collisionShape = state.getCollisionShape(this.level, tempPos);

                    if (collisionShape.isEmpty()) {
                        continue;
                    }

                    final AABB toCollide = box.move(-(double)fx, -(double)fy, -(double)fz);

                    final AABB singleAABB = ((CollisionVoxelShape)collisionShape).moonrise$getSingleAABBRepresentation();
                    if (singleAABB != null) {
                        if (CollisionUtil.voxelShapeIntersect(singleAABB, toCollide)) {
                            return true;
                        }
                        continue;
                    }

                    if (CollisionUtil.voxelShapeIntersectNoEmpty(collisionShape, toCollide)) {
                        return true;
                    }
                    continue;
                }
            }
        }

        return false;
    }
}
