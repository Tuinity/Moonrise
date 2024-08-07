package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import ca.spottedleaf.moonrise.patches.collisions.util.EmptyStreamForMoveCall;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Mixin(Entity.class)
abstract class EntityMixin {

    @Shadow
    private Level level;

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract void move(MoverType moverType, Vec3 vec3);

    @Shadow
    public abstract float maxUpStep();

    @Shadow
    private boolean onGround;

    @Shadow
    public boolean noPhysics;

    @Shadow
    private EntityDimensions dimensions;

    @Shadow
    public abstract Vec3 getEyePosition();

    @Shadow
    public abstract Vec3 getEyePosition(float f);

    @Shadow
    public abstract Vec3 getViewVector(float f);

    @Shadow
    private int remainingFireTicks;

    @Shadow
    public abstract void setRemainingFireTicks(int i);

    @Shadow
    protected abstract int getFireImmuneTicks();

    @Shadow
    public boolean wasOnFire;

    @Shadow
    public boolean isInPowderSnow;

    @Shadow
    public abstract boolean isInWaterRainOrBubble();

    @Shadow
    protected abstract void playEntityOnFireExtinguishedSound();

    @Shadow
    protected abstract void onInsideBlock(BlockState blockState);

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

        final Level world = this.level;
        final AABB currBoundingBox = this.getBoundingBox();

        if (CollisionUtil.isEmpty(currBoundingBox)) {
            return movement;
        }

        final List<AABB> potentialCollisionsBB = new ArrayList<>();
        final List<VoxelShape> potentialCollisionsVoxel = new ArrayList<>();
        final double stepHeight = (double)this.maxUpStep();
        final AABB collisionBox;
        final boolean onGround = this.onGround;

        if (xZero & zZero) {
            if (movement.y > 0.0) {
                collisionBox = CollisionUtil.cutUpwards(currBoundingBox, movement.y);
            } else {
                collisionBox = CollisionUtil.cutDownwards(currBoundingBox, movement.y);
            }
        } else {
            // note: xZero == false or zZero == false
            if (stepHeight > 0.0 && (onGround || (movement.y < 0.0))) {
                // don't bother getting the collisions if we don't need them.
                if (movement.y <= 0.0) {
                    collisionBox = CollisionUtil.expandUpwards(currBoundingBox.expandTowards(movement.x, movement.y, movement.z), stepHeight);
                } else {
                    collisionBox = currBoundingBox.expandTowards(movement.x, Math.max(stepHeight, movement.y), movement.z);
                }
            } else {
                collisionBox = currBoundingBox.expandTowards(movement.x, movement.y, movement.z);
            }
        }

        CollisionUtil.getCollisions(
                world, (Entity)(Object)this, collisionBox, potentialCollisionsVoxel, potentialCollisionsBB,
                CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
                null, null
        );

        if (potentialCollisionsVoxel.isEmpty() && potentialCollisionsBB.isEmpty()) {
            return movement;
        }

        final Vec3 limitedMoveVector = CollisionUtil.performCollisions(movement, currBoundingBox, potentialCollisionsVoxel, potentialCollisionsBB);

        if (stepHeight > 0.0
                && (onGround || (limitedMoveVector.y != movement.y && movement.y < 0.0))
                && (limitedMoveVector.x != movement.x || limitedMoveVector.z != movement.z)) {
            Vec3 vec3d2 = CollisionUtil.performCollisions(new Vec3(movement.x, stepHeight, movement.z), currBoundingBox, potentialCollisionsVoxel, potentialCollisionsBB);
            final Vec3 vec3d3 = CollisionUtil.performCollisions(new Vec3(0.0, stepHeight, 0.0), currBoundingBox.expandTowards(movement.x, 0.0, movement.z), potentialCollisionsVoxel, potentialCollisionsBB);

            if (vec3d3.y < stepHeight) {
                final Vec3 vec3d4 = CollisionUtil.performCollisions(new Vec3(movement.x, 0.0D, movement.z), currBoundingBox.move(vec3d3), potentialCollisionsVoxel, potentialCollisionsBB).add(vec3d3);

                if (vec3d4.horizontalDistanceSqr() > vec3d2.horizontalDistanceSqr()) {
                    vec3d2 = vec3d4;
                }
            }

            if (vec3d2.horizontalDistanceSqr() > limitedMoveVector.horizontalDistanceSqr()) {
                return vec3d2.add(CollisionUtil.performCollisions(new Vec3(0.0D, -vec3d2.y + movement.y, 0.0D), currBoundingBox.move(vec3d2), potentialCollisionsVoxel, potentialCollisionsBB));
            }

            return limitedMoveVector;
        } else {
            return limitedMoveVector;
        }
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
