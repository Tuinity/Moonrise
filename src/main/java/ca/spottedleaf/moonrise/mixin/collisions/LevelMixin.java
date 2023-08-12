package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.collisions.slices.EntityLookup;
import ca.spottedleaf.moonrise.patches.collisions.world.CollisionEntityGetter;
import ca.spottedleaf.moonrise.patches.collisions.world.CollisionLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Mixin(Level.class)
public abstract class LevelMixin implements CollisionLevel, CollisionEntityGetter, LevelAccessor, AutoCloseable {

    @Shadow
    public abstract ProfilerFiller getProfiler();

    @Shadow
    public abstract LevelChunk getChunk(int x, int z);



    @Unique
    private final EntityLookup collisionLookup = new EntityLookup((Level)(Object)this);

    @Unique
    private int minSection;

    @Unique
    private int maxSection;

    @Override
    public final EntityLookup getCollisionLookup() {
        return this.collisionLookup;
    }

    @Override
    public final int getMinSectionMoonrise() {
        return this.minSection;
    }

    @Override
    public final int getMaxSectionMoonrise() {
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
     * @reason Route to faster lookup
     * @author Spottedleaf
     */
    @Overwrite
    public List<Entity> getEntities(final Entity entity, final AABB boundingBox, final Predicate<? super Entity> predicate) {
        this.getProfiler().incrementCounter("getEntities");
        final List<Entity> ret = new ArrayList<>();

        this.collisionLookup.getEntities(entity, boundingBox, ret, predicate);

        return ret;
    }

    /**
     * @reason Route to faster lookup
     * @author Spottedleaf
     */
    @Overwrite
    public <T extends Entity> void getEntities(final EntityTypeTest<Entity, T> entityTypeTest,
                                               final AABB boundingBox, final Predicate<? super T> predicate,
                                               final List<? super T> into, final int maxCount) {
        this.getProfiler().incrementCounter("getEntities");
        if (entityTypeTest instanceof EntityType<T> byType) {
            if (maxCount != Integer.MAX_VALUE) {
                this.collisionLookup.getEntities(byType, boundingBox, into, predicate, maxCount);
                return;
            } else {
                this.collisionLookup.getEntities(byType, boundingBox, into, predicate);
                return;
            }
        }

        if (entityTypeTest == null) {
            if (maxCount != Integer.MAX_VALUE) {
                this.collisionLookup.getEntities((Entity)null, boundingBox, (List)into, (Predicate)predicate, maxCount);
                return;
            } else {
                this.collisionLookup.getEntities((Entity)null, boundingBox, (List)into, (Predicate)predicate);
                return;
            }
        }

        final Class<? extends Entity> base = entityTypeTest.getBaseClass();

        final Predicate<? super T> modifiedPredicate;
        if (predicate == null) {
            modifiedPredicate = (final T obj) -> {
                return entityTypeTest.tryCast(obj) != null;
            };
        } else {
            modifiedPredicate = (final Entity obj) -> {
                final T casted = entityTypeTest.tryCast(obj);
                if (casted == null) {
                    return false;
                }

                return predicate.test(casted);
            };
        }

        if (base == null || base == Entity.class) {
            if (maxCount != Integer.MAX_VALUE) {
                this.collisionLookup.getEntities((Entity)null, boundingBox, (List)into, (Predicate)modifiedPredicate, maxCount);
                return;
            } else {
                this.collisionLookup.getEntities((Entity)null, boundingBox, (List)into, (Predicate)modifiedPredicate);
                return;
            }
        } else {
            if (maxCount != Integer.MAX_VALUE) {
                this.collisionLookup.getEntities(base, null, boundingBox, (List)into, (Predicate)modifiedPredicate, maxCount);
                return;
            } else {
                this.collisionLookup.getEntities(base, null, boundingBox, (List)into, (Predicate)modifiedPredicate);
                return;
            }
        }
    }

    /**
     * Route to faster lookup
     * @author Spottedleaf
     */
    @Override
    public <T extends Entity> List<T> getEntitiesOfClass(final Class<T> entityClass, final AABB boundingBox, final Predicate<? super T> predicate) {
        this.getProfiler().incrementCounter("getEntities");
        final List<T> ret = new ArrayList<>();

        this.collisionLookup.getEntities(entityClass, null, boundingBox, ret, predicate);

        return ret;
    }

    /**
     * Route to faster lookup
     * @author Spottedleaf
     */
    @Override
    public List<Entity> getHardCollidingEntities(final Entity entity, final AABB box, final Predicate<? super Entity> predicate) {
        this.getProfiler().incrementCounter("getEntities");
        final List<Entity> ret = new ArrayList<>();

        this.collisionLookup.getHardCollidingEntities(entity, box, ret, predicate);

        return ret;
    }

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
}
