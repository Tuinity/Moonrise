package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.collisions.entity.CollisionEntity;
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
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
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
public abstract class EntityMixin implements CollisionEntity {

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

    @Unique
    private final boolean isHardColliding = this.isHardCollidingUncached();

    @Override
    public final boolean isHardColliding() {
        return this.isHardColliding;
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
                (0),
                null, null
        );

        if (CollisionUtil.isCollidingWithBorderEdge(world.getWorldBorder(), collisionBox)) {
            potentialCollisionsVoxel.add(world.getWorldBorder().getCollisionShape());
        }

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
     * @reason Redirects the vanilla move() call fire checks to short circuit to false, as we want to avoid the rather
     * expensive stream usage here. The below method then reinserts the logic, without using streams.
     * @author Spottedleaf
     */
    @Redirect(
            method = "move",
            at = @At(
                    target = "Lnet/minecraft/world/level/Level;getBlockStatesIfLoaded(Lnet/minecraft/world/phys/AABB;)Ljava/util/stream/Stream;",
                    value = "INVOKE",
                    ordinal = 0
            )
    )
    public Stream<BlockState> shortCircuitStreamLogic(final Level level, final AABB box) {
        return EmptyStreamForMoveCall.INSTANCE;
    }

    /**
     * @reason Merge fire and block checking logic, so that we can combine the chunk loaded check and the chunk cache
     * @author Spottedleaf
     */
    @Redirect(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;tryCheckInsideBlocks()V"
            )
    )
    public void checkInsideBlocks(final Entity instance) {
        final AABB boundingBox = this.getBoundingBox();
        final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();

        final int minX = Mth.floor(boundingBox.minX - 1.0E-6);
        final int minY = Mth.floor(boundingBox.minY - 1.0E-6);
        final int minZ = Mth.floor(boundingBox.minZ - 1.0E-6);
        final int maxX = Mth.floor(boundingBox.maxX + 1.0E-6);
        final int maxY = Mth.floor(boundingBox.maxY + 1.0E-6);
        final int maxZ = Mth.floor(boundingBox.maxZ + 1.0E-6);

        long lastChunkKey = ChunkPos.INVALID_CHUNK_POS;
        LevelChunk lastChunk = null;

        boolean noneMatch = true;
        boolean isLoaded = true;

        fire_search:
        for (int fz = minZ; fz <= maxZ; ++fz) {
            tempPos.setZ(fz);
            for (int fx = minX; fx <= maxX; ++fx) {
                final int newChunkX = fx >> 4;
                final int newChunkZ = fz >> 4;
                final LevelChunk chunk = lastChunkKey == (lastChunkKey = CoordinateUtils.getChunkKey(newChunkX, newChunkZ)) ?
                        lastChunk : (lastChunk = (LevelChunk)this.level.getChunk(fx >> 4, fz >> 4, ChunkStatus.FULL, false));
                if (chunk == null) {
                    // would have returned empty stream
                    noneMatch = true;
                    isLoaded = false;
                    break fire_search;
                }
                tempPos.setX(fx);
                for (int fy = minY; fy <= maxY; ++fy) {
                    tempPos.setY(fy);
                    final BlockState state = chunk.getBlockState(tempPos);
                    if (state.is(BlockTags.FIRE) || state.is(Blocks.LAVA)) {
                        noneMatch = false;
                        // need to continue to check for loaded chunks
                    }
                }
            }
        }

        if (isLoaded) {
            final int minX2 = Mth.floor(boundingBox.minX + CollisionUtil.COLLISION_EPSILON);
            final int minY2 = Mth.floor(boundingBox.minY + CollisionUtil.COLLISION_EPSILON);
            final int minZ2 = Mth.floor(boundingBox.minZ + CollisionUtil.COLLISION_EPSILON);
            final int maxX2 = Mth.floor(boundingBox.maxX - CollisionUtil.COLLISION_EPSILON);
            final int maxY2 = Mth.floor(boundingBox.maxY - CollisionUtil.COLLISION_EPSILON);
            final int maxZ2 = Mth.floor(boundingBox.maxZ - CollisionUtil.COLLISION_EPSILON);

            for (int fx = minX2; fx <= maxX2; ++fx) {
                tempPos.setX(fx);
                for (int fy = minY2; fy <= maxY2; ++fy) {
                    tempPos.setY(fy);
                    for (int fz = minZ2; fz <= maxZ2; ++fz) {
                        tempPos.setZ(fz);

                        final int newChunkX = fx >> 4;
                        final int newChunkZ = fz >> 4;
                        final LevelChunk chunk = lastChunkKey == (lastChunkKey = CoordinateUtils.getChunkKey(newChunkX, newChunkZ)) ?
                                lastChunk : (lastChunk = this.level.getChunk(fx >> 4, fz >> 4));
                        final BlockState blockState = chunk.getBlockState(tempPos);

                        if (blockState.isAir()) {
                            continue;
                        }

                        try {
                            blockState.entityInside(this.level, tempPos, (Entity)(Object)this);
                            this.onInsideBlock(blockState);
                        } catch (Throwable var12) {
                            CrashReport crashReport = CrashReport.forThrowable(var12, "Colliding entity with block");
                            CrashReportCategory crashReportCategory = crashReport.addCategory("Block being collided with");
                            CrashReportCategory.populateBlockDetails(crashReportCategory, this.level, tempPos, blockState);
                            throw new ReportedException(crashReport);
                        }
                    }
                }
            }
        }

        // to preserve order with tryCheckInsideBlocks, we need to move the fire logic _after_
        if (noneMatch) {
            if (this.remainingFireTicks <= 0) {
                this.setRemainingFireTicks(-this.getFireImmuneTicks());
            }

            if (this.wasOnFire && (this.isInPowderSnow || this.isInWaterRainOrBubble())) {
                this.playEntityOnFireExtinguishedSound();
            }
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

        final float reducedWith = this.dimensions.width * 0.8F;
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

        long lastChunkKey = ChunkPos.INVALID_CHUNK_POS;
        LevelChunk lastChunk = null;
        for (int fz = minZ; fz <= maxZ; ++fz) {
            tempPos.setZ(fz);
            for (int fx = minX; fx <= maxX; ++fx) {
                final int newChunkX = fx >> 4;
                final int newChunkZ = fz >> 4;
                final LevelChunk chunk = lastChunkKey == (lastChunkKey = CoordinateUtils.getChunkKey(newChunkX, newChunkZ)) ?
                        lastChunk : (lastChunk = this.level.getChunk(fx >> 4, fz >> 4));
                tempPos.setX(fx);
                for (int fy = minY; fy <= maxY; ++fy) {
                    tempPos.setY(fy);

                    final BlockState state = chunk.getBlockState(tempPos);

                    if (state.isAir() || !state.isSuffocating(this.level, tempPos)) {
                        continue;
                    }

                    // Yes, it does not use the Entity context stuff.
                    final VoxelShape collisionShape = state.getCollisionShape(this.level, tempPos);

                    if (collisionShape.isEmpty()) {
                        continue;
                    }

                    final AABB singleAABB = ((CollisionVoxelShape)collisionShape).getSingleAABBRepresentation();
                    if (singleAABB != null) {
                        if (CollisionUtil.voxelShapeIntersect(box, singleAABB)) {
                            return true;
                        }
                        continue;
                    }

                    if (CollisionUtil.voxelShapeIntersectNoEmpty(collisionShape, box)) {
                        return true;
                    }
                    continue;
                }
            }
        }

        return false;
    }
}
