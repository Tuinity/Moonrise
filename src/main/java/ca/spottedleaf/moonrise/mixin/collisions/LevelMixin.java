package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.block_counting.BlockCountingChunkSection;
import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(Level.class)
abstract class LevelMixin implements LevelAccessor, AutoCloseable {

    @Shadow
    public abstract LevelChunk getChunk(int x, int z);

    @Shadow
    public abstract WorldBorder getWorldBorder();

    /**
     * Route to faster lookup.
     * See {@link EntityGetterMixin#isUnobstructed(Entity, VoxelShape)} for expected behavior
     * @author Spottedleaf
     */
    @Override
    public boolean isUnobstructed(final Entity entity) {
        final AABB boundingBox = entity.getBoundingBox();
        if (CollisionUtil.isEmpty(boundingBox)) {
            return false;
        }

        final List<Entity> entities = this.getEntities(
                entity,
                boundingBox.inflate(-CollisionUtil.COLLISION_EPSILON, -CollisionUtil.COLLISION_EPSILON, -CollisionUtil.COLLISION_EPSILON),
                null
        );

        for (int i = 0, len = entities.size(); i < len; ++i) {
            final Entity otherEntity = entities.get(i);

            if (otherEntity.isSpectator() || otherEntity.isRemoved() || !otherEntity.blocksBuilding || otherEntity.isPassengerOfSameVehicle(entity)) {
                continue;
            }

            return false;
        }

        return true;
    }


    @Unique
    private static BlockHitResult miss(final ClipContext clipContext) {
        final Vec3 to = clipContext.getTo();
        final Vec3 from = clipContext.getFrom();

        return BlockHitResult.miss(to, Direction.getApproximateNearest(from.x - to.x, from.y - to.y, from.z - to.z), BlockPos.containing(to.x, to.y, to.z));
    }

    @Unique
    private static final FluidState AIR_FLUIDSTATE = Fluids.EMPTY.defaultFluidState();

    @Unique
    private static BlockHitResult fastClip(final Vec3 from, final Vec3 to, final Level level,
                                           final ClipContext clipContext) {
        final double adjX = CollisionUtil.COLLISION_EPSILON * (from.x - to.x);
        final double adjY = CollisionUtil.COLLISION_EPSILON * (from.y - to.y);
        final double adjZ = CollisionUtil.COLLISION_EPSILON * (from.z - to.z);

        if (adjX == 0.0 && adjY == 0.0 && adjZ == 0.0) {
            return miss(clipContext);
        }

        final double toXAdj = to.x - adjX;
        final double toYAdj = to.y - adjY;
        final double toZAdj = to.z - adjZ;
        final double fromXAdj = from.x + adjX;
        final double fromYAdj = from.y + adjY;
        final double fromZAdj = from.z + adjZ;

        int currX = Mth.floor(fromXAdj);
        int currY = Mth.floor(fromYAdj);
        int currZ = Mth.floor(fromZAdj);

        final BlockPos.MutableBlockPos currPos = new BlockPos.MutableBlockPos();

        final double diffX = toXAdj - fromXAdj;
        final double diffY = toYAdj - fromYAdj;
        final double diffZ = toZAdj - fromZAdj;

        final double dxDouble = Math.signum(diffX);
        final double dyDouble = Math.signum(diffY);
        final double dzDouble = Math.signum(diffZ);

        final int dx = (int)dxDouble;
        final int dy = (int)dyDouble;
        final int dz = (int)dzDouble;

        final double normalizedDiffX = diffX == 0.0 ? Double.MAX_VALUE : dxDouble / diffX;
        final double normalizedDiffY = diffY == 0.0 ? Double.MAX_VALUE : dyDouble / diffY;
        final double normalizedDiffZ = diffZ == 0.0 ? Double.MAX_VALUE : dzDouble / diffZ;

        double normalizedCurrX = normalizedDiffX * (diffX > 0.0 ? (1.0 - Mth.frac(fromXAdj)) : Mth.frac(fromXAdj));
        double normalizedCurrY = normalizedDiffY * (diffY > 0.0 ? (1.0 - Mth.frac(fromYAdj)) : Mth.frac(fromYAdj));
        double normalizedCurrZ = normalizedDiffZ * (diffZ > 0.0 ? (1.0 - Mth.frac(fromZAdj)) : Mth.frac(fromZAdj));

        LevelChunkSection[] lastChunk = null;
        PalettedContainer<BlockState> lastSection = null;
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkY = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;

        final int minSection = WorldUtil.getMinSection(level);

        for (;;) {
            currPos.set(currX, currY, currZ);

            final int newChunkX = currX >> 4;
            final int newChunkY = currY >> 4;
            final int newChunkZ = currZ >> 4;

            final int chunkDiff = ((newChunkX ^ lastChunkX) | (newChunkZ ^ lastChunkZ));
            final int chunkYDiff = newChunkY ^ lastChunkY;

            if ((chunkDiff | chunkYDiff) != 0) {
                if (chunkDiff != 0) {
                    lastChunk = level.getChunk(newChunkX, newChunkZ).getSections();
                }
                final int sectionY = newChunkY - minSection;
                lastSection = sectionY >= 0 && sectionY < lastChunk.length ? lastChunk[sectionY].states : null;

                lastChunkX = newChunkX;
                lastChunkY = newChunkY;
                lastChunkZ = newChunkZ;
            }

            final BlockState blockState;
            if (lastSection != null && !(blockState = lastSection.get((currX & 15) | ((currZ & 15) << 4) | ((currY & 15) << (4+4)))).isAir()) {
                final VoxelShape blockCollision = clipContext.getBlockShape(blockState, level, currPos);

                final BlockHitResult blockHit = blockCollision.isEmpty() ? null : level.clipWithInteractionOverride(from, to, currPos, blockCollision, blockState);

                final VoxelShape fluidCollision;
                final FluidState fluidState;
                if (clipContext.fluid != ClipContext.Fluid.NONE && (fluidState = blockState.getFluidState()) != AIR_FLUIDSTATE) {
                    fluidCollision = clipContext.getFluidShape(fluidState, level, currPos);

                    final BlockHitResult fluidHit = fluidCollision.clip(from, to, currPos);

                    if (fluidHit != null) {
                        if (blockHit == null) {
                            return fluidHit;
                        }

                        return from.distanceToSqr(blockHit.getLocation()) <= from.distanceToSqr(fluidHit.getLocation()) ? blockHit : fluidHit;
                    }
                }

                if (blockHit != null) {
                    return blockHit;
                }
            } // else: usually fall here

            if (normalizedCurrX > 1.0 && normalizedCurrY > 1.0 && normalizedCurrZ > 1.0) {
                return miss(clipContext);
            }

            // inc the smallest normalized coordinate

            if (normalizedCurrX < normalizedCurrY) {
                if (normalizedCurrX < normalizedCurrZ) {
                    currX += dx;
                    normalizedCurrX += normalizedDiffX;
                } else {
                    // x < y && x >= z <--> z < y && z <= x
                    currZ += dz;
                    normalizedCurrZ += normalizedDiffZ;
                }
            } else if (normalizedCurrY < normalizedCurrZ) {
                // y <= x && y < z
                currY += dy;
                normalizedCurrY += normalizedDiffY;
            } else {
                // y <= x && z <= y <--> z <= y && z <= x
                currZ += dz;
                normalizedCurrZ += normalizedDiffZ;
            }
        }
    }

    /**
     * @reason Route to optimized call
     * @author Spottedleaf
     */
    @Override
    public BlockHitResult clip(final ClipContext clipContext) {
        // can only do this in this class, as not everything that implements BlockGetter can retrieve chunks
        return fastClip(clipContext.getFrom(), clipContext.getTo(), (Level)(Object)this, clipContext);
    }

    /**
     * @reason Route to faster logic
     * @author Spottedleaf
     */
    @Override
    public boolean noCollision(final Entity entity, final AABB box) {
        final int flags = entity == null ? (CollisionUtil.COLLISION_FLAG_CHECK_BORDER | CollisionUtil.COLLISION_FLAG_CHECK_ONLY) : CollisionUtil.COLLISION_FLAG_CHECK_ONLY;
        if (CollisionUtil.getCollisionsForBlocksOrWorldBorder((Level)(Object)this, entity, box, null, null, flags, null)) {
            return false;
        }

        return !CollisionUtil.getEntityHardCollisions((Level)(Object)this, entity, box, null, flags, null);
    }

    /**
     * @reason Route to faster logic
     * @author Spottedleaf
     */
    @Override
    public boolean collidesWithSuffocatingBlock(final Entity entity, final AABB box) {
        return CollisionUtil.getCollisionsForBlocksOrWorldBorder((Level)(Object)this, entity, box, null, null,
                CollisionUtil.COLLISION_FLAG_CHECK_ONLY,
                (final BlockState state, final BlockPos pos) -> {
                    return state.isSuffocating((Level)(Object)LevelMixin.this, pos);
                }
        );
    }

    @Unique
    private static VoxelShape inflateAABBToVoxel(final AABB aabb, final double x, final double y, final double z) {
        return Shapes.create(
                aabb.minX - x,
                aabb.minY - y,
                aabb.minZ - z,

                aabb.maxX + x,
                aabb.maxY + y,
                aabb.maxZ + z
        );
    }

    /**
     * @reason Use optimised OR operator join strategy, avoid streams
     * @author Spottedleaf
     */
    @Override
    public Optional<Vec3> findFreePosition(final Entity entity, final VoxelShape boundsShape, final Vec3 fromPosition,
                                           final double rangeX, final double rangeY, final double rangeZ) {
        if (boundsShape.isEmpty()) {
            return Optional.empty();
        }

        final double expandByX = rangeX * 0.5;
        final double expandByY = rangeY * 0.5;
        final double expandByZ = rangeZ * 0.5;

        // note: it is useless to look at shapes outside of range / 2.0
        final AABB collectionVolume = boundsShape.bounds().inflate(expandByX, expandByY, expandByZ);

        final List<AABB> aabbs = new ArrayList<>();
        final List<VoxelShape> voxels = new ArrayList<>();

        CollisionUtil.getCollisionsForBlocksOrWorldBorder(
                (Level)(Object)this, entity, collectionVolume, voxels, aabbs,
                CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
                null
        );

        final WorldBorder worldBorder = this.getWorldBorder();
        if (worldBorder != null) {
            aabbs.removeIf((final AABB aabb) -> {
                return !worldBorder.isWithinBounds(aabb);
            });
            voxels.removeIf((final VoxelShape shape) -> {
                return !worldBorder.isWithinBounds(shape.bounds());
            });
        }

        // push voxels into aabbs
        for (int i = 0, len = voxels.size(); i < len; ++i) {
            aabbs.addAll(voxels.get(i).toAabbs());
        }

        // expand AABBs
        final VoxelShape first = aabbs.isEmpty() ? Shapes.empty() : inflateAABBToVoxel(aabbs.get(0), expandByX, expandByY, expandByZ);
        final VoxelShape[] rest = new VoxelShape[Math.max(0, aabbs.size() - 1)];

        for (int i = 1, len = aabbs.size(); i < len; ++i) {
            rest[i - 1] = inflateAABBToVoxel(aabbs.get(i), expandByX, expandByY, expandByZ);
        }

        // use optimized implementation of ORing the shapes together
        final VoxelShape joined = Shapes.or(first, rest);

        // find free space
        // can use unoptimized join here (instead of join()), as closestPointTo uses toAabbs()
        final VoxelShape freeSpace = Shapes.joinUnoptimized(boundsShape, joined, BooleanOp.ONLY_FIRST);

        return freeSpace.closestPointTo(fromPosition);
    }

    /**
     * @reason Route to faster logic
     * @author Spottedleaf
     */
    @Override
    public Optional<BlockPos> findSupportingBlock(final Entity entity, final AABB aabb) {
        final int minSection = WorldUtil.getMinSection((Level)(Object)this);

        final int minBlockX = Mth.floor(aabb.minX - CollisionUtil.COLLISION_EPSILON) - 1;
        final int maxBlockX = Mth.floor(aabb.maxX + CollisionUtil.COLLISION_EPSILON) + 1;

        final int minBlockY = Math.max((minSection << 4) - 1, Mth.floor(aabb.minY - CollisionUtil.COLLISION_EPSILON) - 1);
        final int maxBlockY = Math.min((WorldUtil.getMaxSection((Level)(Object)this) << 4) + 16, Mth.floor(aabb.maxY + CollisionUtil.COLLISION_EPSILON) + 1);

        final int minBlockZ = Mth.floor(aabb.minZ - CollisionUtil.COLLISION_EPSILON) - 1;
        final int maxBlockZ = Mth.floor(aabb.maxZ + CollisionUtil.COLLISION_EPSILON) + 1;

        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        final CollisionContext collisionShape = new CollisionUtil.LazyEntityCollisionContext(entity);
        BlockPos selected = null;
        double selectedDistance = Double.MAX_VALUE;
        final Vec3 entityPos = entity.position();

        // special cases:
        if (minBlockY > maxBlockY) {
            // no point in checking
            return Optional.empty();
        }

        final int minChunkX = minBlockX >> 4;
        final int maxChunkX = maxBlockX >> 4;

        final int minChunkY = minBlockY >> 4;
        final int maxChunkY = maxBlockY >> 4;

        final int minChunkZ = minBlockZ >> 4;
        final int maxChunkZ = maxBlockZ >> 4;

        final ChunkSource chunkSource = this.getChunkSource();

        for (int currChunkZ = minChunkZ; currChunkZ <= maxChunkZ; ++currChunkZ) {
            for (int currChunkX = minChunkX; currChunkX <= maxChunkX; ++currChunkX) {
                final ChunkAccess chunk = chunkSource.getChunk(currChunkX, currChunkZ, ChunkStatus.FULL, false);

                if (chunk == null) {
                    continue;
                }

                final LevelChunkSection[] sections = chunk.getSections();

                // bound y
                for (int currChunkY = minChunkY; currChunkY <= maxChunkY; ++currChunkY) {
                    final int sectionIdx = currChunkY - minSection;
                    if (sectionIdx < 0 || sectionIdx >= sections.length) {
                        continue;
                    }
                    final LevelChunkSection section = sections[sectionIdx];
                    if (section.hasOnlyAir()) {
                        // empty
                        continue;
                    }

                    final boolean hasSpecial = ((BlockCountingChunkSection)section).moonrise$hasSpecialCollidingBlocks();
                    final int sectionAdjust = !hasSpecial ? 1 : 0;

                    final PalettedContainer<BlockState> blocks = section.states;

                    final int minXIterate = currChunkX == minChunkX ? (minBlockX & 15) + sectionAdjust : 0;
                    final int maxXIterate = currChunkX == maxChunkX ? (maxBlockX & 15) - sectionAdjust : 15;
                    final int minZIterate = currChunkZ == minChunkZ ? (minBlockZ & 15) + sectionAdjust : 0;
                    final int maxZIterate = currChunkZ == maxChunkZ ? (maxBlockZ & 15) - sectionAdjust : 15;
                    final int minYIterate = currChunkY == minChunkY ? (minBlockY & 15) + sectionAdjust : 0;
                    final int maxYIterate = currChunkY == maxChunkY ? (maxBlockY & 15) - sectionAdjust : 15;

                    for (int currY = minYIterate; currY <= maxYIterate; ++currY) {
                        final int blockY = currY | (currChunkY << 4);
                        mutablePos.setY(blockY);
                        for (int currZ = minZIterate; currZ <= maxZIterate; ++currZ) {
                            final int blockZ = currZ | (currChunkZ << 4);
                            mutablePos.setZ(blockZ);
                            for (int currX = minXIterate; currX <= maxXIterate; ++currX) {
                                final int localBlockIndex = (currX) | (currZ << 4) | ((currY) << 8);
                                final int blockX = currX | (currChunkX << 4);
                                mutablePos.setX(blockX);

                                final int edgeCount = hasSpecial ? ((blockX == minBlockX || blockX == maxBlockX) ? 1 : 0) +
                                    ((blockY == minBlockY || blockY == maxBlockY) ? 1 : 0) +
                                    ((blockZ == minBlockZ || blockZ == maxBlockZ) ? 1 : 0) : 0;
                                if (edgeCount == 3) {
                                    continue;
                                }

                                final double distance = mutablePos.distToCenterSqr(entityPos);
                                if (distance > selectedDistance || (distance == selectedDistance && selected.compareTo(mutablePos) >= 0)) {
                                    continue;
                                }

                                final BlockState blockData = blocks.get(localBlockIndex);

                                if (((CollisionBlockState)blockData).moonrise$emptyContextCollisionShape()) {
                                    continue;
                                }

                                VoxelShape blockCollision = ((CollisionBlockState)blockData).moonrise$getConstantContextCollisionShape();

                                if (edgeCount == 0 || ((edgeCount != 1 || blockData.hasLargeCollisionShape()) && (edgeCount != 2 || blockData.getBlock() == Blocks.MOVING_PISTON))) {
                                    if (blockCollision == null) {
                                        blockCollision = blockData.getCollisionShape((Level)(Object)this, mutablePos, collisionShape);

                                        if (blockCollision.isEmpty()) {
                                            continue;
                                        }
                                    }

                                    // avoid VoxelShape#move by shifting the entity collision shape instead
                                    final AABB shiftedAABB = aabb.move(-(double)blockX, -(double)blockY, -(double)blockZ);

                                    final AABB singleAABB = ((CollisionVoxelShape)blockCollision).moonrise$getSingleAABBRepresentation();
                                    if (singleAABB != null) {
                                        if (!CollisionUtil.voxelShapeIntersect(singleAABB, shiftedAABB)) {
                                            continue;
                                        }

                                        selected = mutablePos.immutable();
                                        selectedDistance = distance;
                                        continue;
                                    }

                                    if (!CollisionUtil.voxelShapeIntersectNoEmpty(blockCollision, shiftedAABB)) {
                                        continue;
                                    }

                                    selected = mutablePos.immutable();
                                    selectedDistance = distance;
                                    continue;
                                }
                            }
                        }
                    }
                }
            }
        }

        return Optional.ofNullable(selected);
    }
}
