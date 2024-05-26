package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_getblock.GetBlockChunk;
import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import ca.spottedleaf.moonrise.patches.collisions.world.CollisionLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(Level.class)
public abstract class LevelMixin implements CollisionLevel, LevelAccessor, AutoCloseable {

    @Shadow
    public abstract ProfilerFiller getProfiler();

    @Shadow
    public abstract LevelChunk getChunk(int x, int z);



    @Unique
    private int minSection;

    @Unique
    private int maxSection;

    @Override
    public final int moonrise$getMinSection() {
        return this.minSection;
    }

    @Override
    public final int moonrise$getMaxSection() {
        return this.maxSection;
    }

    /**
     * @reason Init min/max section
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void init(final CallbackInfo ci) {
        this.minSection = WorldUtil.getMinSection(this);
        this.maxSection = WorldUtil.getMaxSection(this);
    }

    /**
     * Route to faster lookup.
     * See {@link EntityGetterMixin#isUnobstructed(Entity, VoxelShape)} for expected behavior
     * @author Spottedleaf
     */
    @Override
    public final boolean isUnobstructed(final Entity entity) {
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

        return BlockHitResult.miss(to, Direction.getNearest(from.x - to.x, from.y - to.y, from.z - to.z), BlockPos.containing(to.x, to.y, to.z));
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

        final int minSection = ((CollisionLevel)level).moonrise$getMinSection();

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
    public final BlockHitResult clip(final ClipContext clipContext) {
        // can only do this in this class, as not everything that implements BlockGetter can retrieve chunks
        return fastClip(clipContext.getFrom(), clipContext.getTo(), (Level)(Object)this, clipContext);
    }

    /**
     * @reason Route to faster logic
     * @author Spottedleaf
     */
    @Override
    public final boolean noCollision(final Entity entity, final AABB box) {
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
    public final boolean collidesWithSuffocatingBlock(final Entity entity, final AABB box) {
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
    public final Optional<Vec3> findFreePosition(final Entity entity, final VoxelShape boundsShape, final Vec3 fromPosition,
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
    public final Optional<BlockPos> findSupportingBlock(final Entity entity, final AABB aabb) {
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

        LevelChunk lastChunk = null;
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;

        final ChunkSource chunkSource = this.getChunkSource();

        for (int currZ = minBlockZ; currZ <= maxBlockZ; ++currZ) {
            pos.setZ(currZ);
            for (int currX = minBlockX; currX <= maxBlockX; ++currX) {
                pos.setX(currX);

                final int newChunkX = currX >> 4;
                final int newChunkZ = currZ >> 4;

                if (((newChunkX ^ lastChunkX) | (newChunkZ ^ lastChunkZ)) != 0) {
                    lastChunk = (LevelChunk)chunkSource.getChunk(newChunkX, newChunkZ, ChunkStatus.FULL, false);
                }

                if (lastChunk == null) {
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

                    final BlockState state = ((GetBlockChunk)lastChunk).moonrise$getBlock(currX, currY, currZ);
                    if (((CollisionBlockState)state).moonrise$emptyCollisionShape()) {
                        continue;
                    }

                    VoxelShape blockCollision = ((CollisionBlockState)state).moonrise$getConstantCollisionShape();

                    if ((edgeCount != 1 || state.hasLargeCollisionShape()) && (edgeCount != 2 || state.getBlock() == Blocks.MOVING_PISTON)) {
                        if (collisionContext == null) {
                            collisionContext = new CollisionUtil.LazyEntityCollisionContext(entity);
                        }

                        if (blockCollision == null) {
                            blockCollision = state.getCollisionShape((Level)(Object)this, pos, collisionContext);
                        }

                        if (blockCollision.isEmpty()) {
                            continue;
                        }

                        // avoid VoxelShape#move by shifting the entity collision shape instead
                        final AABB shiftedAABB = aabb.move(-(double)currX, -(double)currY, -(double)currZ);

                        final AABB singleAABB = ((CollisionVoxelShape)blockCollision).moonrise$getSingleAABBRepresentation();
                        if (singleAABB != null) {
                            if (!CollisionUtil.voxelShapeIntersect(singleAABB, shiftedAABB)) {
                                continue;
                            }

                            selected = pos.immutable();
                            selectedDistance = distance;
                            continue;
                        }

                        if (!CollisionUtil.voxelShapeIntersectNoEmpty(blockCollision, shiftedAABB)) {
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
