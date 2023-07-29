package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import java.util.Optional;

@Mixin(CollisionGetter.class)
public interface CollisionGetterMixin extends BlockGetter {

    @Shadow
    @Nullable
    BlockGetter getChunkForCollisions(final int chunkX, final int chunkZ);

    /**
     * @reason Route to faster logic
     * @author Spottedleaf
     */
    @Overwrite
    default Optional<BlockPos> findSupportingBlock(final Entity entity, final AABB aabb) {
        final int minBlockX = Mth.floor(aabb.minX - CollisionUtil.COLLISION_EPSILON) - 1;
        final int maxBlockX = Mth.floor(aabb.maxX + CollisionUtil.COLLISION_EPSILON) + 1;

        final int minBlockY = Mth.floor(aabb.minY - CollisionUtil.COLLISION_EPSILON) - 1;
        final int maxBlockY = Mth.floor(aabb.maxY + CollisionUtil.COLLISION_EPSILON) + 1;

        final int minBlockZ = Mth.floor(aabb.minZ - CollisionUtil.COLLISION_EPSILON) - 1;
        final int maxBlockZ = Mth.floor(aabb.maxZ + CollisionUtil.COLLISION_EPSILON) + 1;

        CollisionUtil.LazyEntityCollisionContext collisionContext = null;

        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos selected = null;
        double selectedDistance = Double.MAX_VALUE;

        final Vec3 entityPos = entity.position();

        for (int currZ = minBlockZ; currZ <= maxBlockZ; ++currZ) {
            for (int currX = minBlockX; currX <= maxBlockX; ++currX) {
                pos.set(currX, 0, currZ);
                final BlockGetter chunk = this.getChunkForCollisions(currX >> 4, currZ >> 4);
                if (chunk == null) {
                    continue;
                }
                for (int currY = minBlockY; currY <= maxBlockY; ++currY) {
                    int edgeCount = ((currX == minBlockX || currX == maxBlockX) ? 1 : 0) +
                            ((currY == minBlockY || currY == maxBlockY) ? 1 : 0) +
                            ((currZ == minBlockZ || currZ == maxBlockZ) ? 1 : 0);
                    if (edgeCount == 3) {
                        continue;
                    }

                    pos.setY(currY);

                    final double distance = pos.distToCenterSqr(entityPos);
                    if (distance > selectedDistance || (distance == selectedDistance && selected.compareTo(pos) >= 0)) {
                        continue;
                    }

                    final BlockState state = chunk.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }

                    if ((edgeCount != 1 || state.hasLargeCollisionShape()) && (edgeCount != 2 || state.getBlock() == Blocks.MOVING_PISTON)) {
                        if (collisionContext == null) {
                            collisionContext = new CollisionUtil.LazyEntityCollisionContext(entity);
                        }
                        final VoxelShape blockCollision = state.getCollisionShape(chunk, pos, collisionContext);
                        if (blockCollision.isEmpty()) {
                            continue;
                        }

                        AABB singleAABB = ((CollisionVoxelShape)blockCollision).getSingleAABBRepresentation();
                        if (singleAABB != null) {
                            singleAABB = singleAABB.move((double)currX, (double)currY, (double)currZ);
                            if (!CollisionUtil.voxelShapeIntersect(aabb, singleAABB)) {
                                continue;
                            }

                            selected = pos.immutable();
                            selectedDistance = distance;
                            continue;
                        }

                        final VoxelShape blockCollisionOffset = blockCollision.move((double)currX, (double)currY, (double)currZ);

                        if (!CollisionUtil.voxelShapeIntersectNoEmpty(blockCollisionOffset, aabb)) {
                            continue;
                        }

                        selected = pos.immutable();
                        selectedDistance = distance;
                        continue;
                    }
                }
            }
        }

        return Optional.ofNullable(selected);
    }
}
