package ca.spottedleaf.moonrise.mixin.fluid;

import ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState;
import ca.spottedleaf.moonrise.patches.collisions.util.CollisionDirection;
import ca.spottedleaf.moonrise.patches.collisions.util.FluidOcclusionCacheKey;
import ca.spottedleaf.moonrise.patches.fluids.FluidClassification;
import ca.spottedleaf.moonrise.patches.fluids.FluidFluid;
import ca.spottedleaf.moonrise.patches.fluids.FluidFluidState;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.shorts.Short2ByteOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin extends Fluid {

    @Shadow
    protected abstract int getDropOff(LevelReader levelReader);

    @Shadow
    public abstract Fluid getSource();

    @Shadow
    @Final
    public static BooleanProperty FALLING;

    @Shadow
    @Final
    public static IntegerProperty LEVEL;

    @Shadow
    protected abstract boolean canConvertToSource(Level level);

    @Shadow
    public abstract Fluid getFlowing();

    @Shadow
    protected abstract void spreadTo(LevelAccessor levelAccessor, BlockPos blockPos, BlockState blockState, Direction direction, FluidState fluidState);

    @Shadow
    protected abstract boolean canHoldFluid(BlockGetter blockGetter, BlockPos blockPos, BlockState blockState, Fluid fluid);

    @Shadow
    protected abstract int getSlopeFindDistance(LevelReader levelReader);


    @Shadow
    public static short getCacheKey(BlockPos blockPos, BlockPos blockPos2) {
        return (short)0;
    }


    @Unique
    private FluidState sourceFalling;

    @Unique
    private FluidState sourceNotFalling;

    @Unique
    private static final int TOTAL_FLOWING_STATES = FALLING.getPossibleValues().size() * LEVEL.getPossibleValues().size();

    @Unique
    private static final int MIN_LEVEL = LEVEL.getPossibleValues().stream().sorted().findFirst().get().intValue();

    // index = (falling ? 1 : 0) + level*2
    @Unique
    private FluidState[] flowingLookUp;

    @Unique
    private volatile boolean init;

    /**
     * Due to init order, we need to use callbacks to initialise our state
     */
    @Unique
    private void init() {
        for (final FluidState state : this.getFlowing().getStateDefinition().getPossibleStates()) {
            if (!state.isSource()) {
                if (this.flowingLookUp == null) {
                    this.flowingLookUp = new FluidState[TOTAL_FLOWING_STATES];
                }
                final int index = (state.getValue(FALLING).booleanValue() ? 1 : 0) | ((state.getValue(LEVEL).intValue() - MIN_LEVEL) << 1);
                if (this.flowingLookUp[index] != null) {
                    throw new IllegalStateException("Already inited");
                }
                this.flowingLookUp[index] = state;
            }
        }
        for (final FluidState state : this.getSource().getStateDefinition().getPossibleStates()) {
            if (state.isSource()) {
                if (state.getValue(FALLING).booleanValue()) {
                    if (this.sourceFalling != null) {
                        throw new IllegalStateException("Already inited");
                    }
                    this.sourceFalling = state;
                } else {
                    if (this.sourceNotFalling != null) {
                        throw new IllegalStateException("Already inited");
                    }
                    this.sourceNotFalling = state;
                }
            }
        }

        this.init = true;
    }

    /**
     * @reason Use cached result to avoid indirection
     * @author Spottedleaf
     */
    @Overwrite
    public FluidState getSource(final boolean falling) {
        if (!this.init) {
            this.init();
        }
        return falling ? this.sourceFalling : this.sourceNotFalling;
    }

    /**
     * @reason Use cached result to avoid indirection
     * @author Spottedleaf
     */
    @Overwrite
    public FluidState getFlowing(final int amount, final boolean falling) {
        if (!this.init) {
            this.init();
        }
        final int index = (falling ? 1 : 0) | ((amount - MIN_LEVEL) << 1);
        return this.flowingLookUp[index];
    }


    @Unique
    private static final int COLLISION_OCCLUSION_CACHE_SIZE = 2048;

    @Unique
    private static final FluidOcclusionCacheKey[] COLLISION_OCCLUSION_CACHE = new FluidOcclusionCacheKey[COLLISION_OCCLUSION_CACHE_SIZE];

    /**
     * @reason Try to avoid going to the cache for simple cases; additionally use better caching strategy
     * @author Spottedleaf
     */
    @Overwrite
    private boolean canPassThroughWall(final Direction direction, final BlockGetter level,
                                       final BlockPos fromPos, final BlockState fromState,
                                       final BlockPos toPos, final BlockState toState) {
        if (((CollisionBlockState)fromState).emptyCollisionShape() & ((CollisionBlockState)toState).emptyCollisionShape()) {
            // don't even try to cache simple cases
            return true;
        }

        if (((CollisionBlockState)fromState).occludesFullBlock() | ((CollisionBlockState)toState).occludesFullBlock()) {
            // don't even try to cache simple cases
            return false;
        }

        final FluidOcclusionCacheKey[] cache = ((CollisionBlockState)fromState).hasCache() & ((CollisionBlockState)toState).hasCache() ?
                COLLISION_OCCLUSION_CACHE : null;

        final int keyIndex
                = (((CollisionBlockState)fromState).uniqueId1() ^ ((CollisionBlockState)toState).uniqueId2() ^ ((CollisionDirection)(Object)direction).uniqueId())
                & (COLLISION_OCCLUSION_CACHE_SIZE - 1);

        if (cache != null) {
            final FluidOcclusionCacheKey cached = cache[keyIndex];
            if (cached != null && cached.first() == fromState && cached.second() == toState && cached.direction() == direction) {
                return cached.result();
            }
        }

        final VoxelShape shape1 = fromState.getCollisionShape(level, fromPos);
        final VoxelShape shape2 = toState.getCollisionShape(level, toPos);

        final boolean result = !Shapes.mergedFaceOccludes(shape1, shape2, direction);

        if (cache != null) {
            // we can afford to replace in-use keys more often due to the excessive caching the collision patch does in mergedFaceOccludes
            cache[keyIndex] = new FluidOcclusionCacheKey(fromState, toState, direction, result);
        }

        return result;
    }

    @Unique
    private static final Direction[] HORIZONTAL_ARRAY = Direction.Plane.HORIZONTAL.stream().toList().toArray(new Direction[0]);

    @Unique
    private static final FluidState EMPTY_FLUID_STATE = Fluids.EMPTY.defaultFluidState();

    /**
     * @reason Optimise method
     * @author Spottedleaf
     */
    @Overwrite
    public FluidState getNewLiquid(final Level level, final BlockPos fromPos, final BlockState fromState) {
        final FluidClassification thisClassification = ((FluidFluid)this).getClassification();

        LevelChunk lastChunk = null;
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;

        int newAmount = 0;
        int nearbySources = 0;

        final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();

        for (final Direction direction : HORIZONTAL_ARRAY) {
            tempPos.set(fromPos.getX() + direction.getStepX(), fromPos.getY(), fromPos.getZ() + direction.getStepZ());

            final int newChunkX = tempPos.getX() >> 4;
            final int newChunkZ = tempPos.getZ() >> 4;

            final int chunkDiff = ((newChunkX ^ lastChunkX) | (newChunkZ ^ lastChunkZ));

            if (chunkDiff != 0) {
                lastChunk = level.getChunk(newChunkX, newChunkZ);

                lastChunkX = newChunkX;
                lastChunkZ = newChunkZ;
            }

            final BlockState neighbourState = lastChunk.getBlockState(tempPos);
            final FluidState fluidState = neighbourState.getFluidState();

            if ((((FluidFluidState)(Object)fluidState).getClassification() == thisClassification) && this.canPassThroughWall(direction, level, fromPos, fromState, tempPos, neighbourState)) {
                if (fluidState.isSource()) {
                    ++nearbySources;
                }

                newAmount = Math.max(newAmount, fluidState.getAmount());
            }
        }

        tempPos.set(fromPos.getX(), fromPos.getY() - 1, fromPos.getZ());
        final int newChunkX = tempPos.getX() >> 4;
        final int newChunkZ = tempPos.getZ() >> 4;

        final int chunkDiff = ((newChunkX ^ lastChunkX) | (newChunkZ ^ lastChunkZ));

        if (chunkDiff != 0) {
            lastChunk = level.getChunk(newChunkX, newChunkZ);

            lastChunkX = newChunkX;
            lastChunkZ = newChunkZ;
        }

        if (nearbySources >= 2 && this.canConvertToSource(level)) {
            final BlockState belowState = lastChunk.getBlockState(tempPos);
            final FluidState belowFluid = belowState.getFluidState();

            if (belowState.isSolid() || (belowFluid.isSource() && ((FluidFluidState)(Object)belowFluid).getClassification() == thisClassification)) {
                return this.getSource(false);
            }
        }

        tempPos.setY(fromPos.getY() + 1);
        final BlockState aboveState = lastChunk.getBlockState(tempPos);
        final FluidState aboveFluid = aboveState.getFluidState();

        // drop empty check, we cannot be empty
        if ((((FluidFluidState)(Object)aboveFluid).getClassification() == thisClassification) && this.canPassThroughWall(Direction.UP, level, fromPos, fromState, tempPos, aboveState)) {
            return this.getFlowing(8, true);
        } else {
            final int finalAmount = newAmount - this.getDropOff(level);
            return finalAmount <= 0 ? EMPTY_FLUID_STATE : this.getFlowing(finalAmount, false);
        }
    }

    /**
     * @reason Optimise
     * @author Spottedleaf
     */
    @Overwrite
    public void spread(final Level level, final BlockPos fromPos, final FluidState fromFluid) {
        if (fromFluid.isEmpty()) {
            return;
        }

        final LevelChunk fromChunk = level.getChunk(fromPos.getX() >> 4, fromPos.getZ() >> 4);
        final BlockState fromState = fromChunk.getBlockState(fromPos);
        final BlockPos belowPos = fromPos.below();
        final BlockState belowState = fromChunk.getBlockState(belowPos);
        final FluidState belowFluid = belowState.getFluidState();
        final FluidState newFluid = this.getNewLiquid(level, belowPos, belowState);

        if (this.canSpreadTo(level, fromPos, fromState, Direction.DOWN, belowPos, belowState, belowFluid, newFluid.getType())) {
            this.spreadTo(level, belowPos, belowState, Direction.DOWN, newFluid);
            if (this.sourceNeighborCount(level, fromPos) >= 3) {
                this.spreadToSides(level, fromPos, fromFluid, fromState);
            }
        } else if (fromFluid.isSource() || !this.isWaterHole(level, newFluid.getType(), fromPos, fromState, belowPos, belowState)) {
            this.spreadToSides(level, fromPos, fromFluid, fromState);
        }
    }

    /**
     * @reason Optimise
     * @author Spottedleaf
     */
    @Overwrite
    private int sourceNeighborCount(final LevelReader level, final BlockPos fromPos) {
        final FluidClassification thisClassification = ((FluidFluid)this).getClassification();

        ChunkAccess lastChunk = null;
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;

        int ret = 0;

        final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();

        for (final Direction direction : HORIZONTAL_ARRAY) {
            tempPos.set(fromPos.getX() + direction.getStepX(), fromPos.getY(), fromPos.getZ() + direction.getStepZ());

            final int newChunkX = tempPos.getX() >> 4;
            final int newChunkZ = tempPos.getZ() >> 4;

            final int chunkDiff = ((newChunkX ^ lastChunkX) | (newChunkZ ^ lastChunkZ));

            if (chunkDiff != 0) {
                lastChunk = level.getChunk(newChunkX, newChunkZ);

                lastChunkX = newChunkX;
                lastChunkZ = newChunkZ;
            }

            FluidState fluidState = lastChunk.getBlockState(tempPos).getFluidState();
            if (fluidState.isSource() && (((FluidFluidState)(Object)fluidState).getClassification() == thisClassification)) {
                ++ret;
            }
        }

        return ret;
    }

    @Unique
    private static final byte UNCACHED_RESULT = (byte)-1;

    /**
     * @reason Optimise
     * @author Spottedleaf
     */
    @Overwrite
    public Map<Direction, FluidState> getSpread(final Level level, final BlockPos fromPos, final BlockState fromState) {
        ChunkAccess lastChunk = null;
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;

        int minSlope = 1000;

        final Map<Direction, FluidState> ret = Maps.newEnumMap(Direction.class);
        final Short2ObjectOpenHashMap<BlockState> blockLookupCache = new Short2ObjectOpenHashMap<>();
        final Short2ByteOpenHashMap waterHoleCache = new Short2ByteOpenHashMap();
        waterHoleCache.defaultReturnValue(UNCACHED_RESULT);

        final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();

        for (final Direction direction : HORIZONTAL_ARRAY) {
            tempPos.set(fromPos.getX() + direction.getStepX(), fromPos.getY(), fromPos.getZ() + direction.getStepZ());
            final short cacheKey = getCacheKey(fromPos, tempPos);

            BlockState neighbourState = blockLookupCache.get(cacheKey);
            if (neighbourState == null) {
                final int newChunkX = tempPos.getX() >> 4;
                final int newChunkZ = tempPos.getZ() >> 4;

                final int chunkDiff = ((newChunkX ^ lastChunkX) | (newChunkZ ^ lastChunkZ));

                if (chunkDiff != 0) {
                    lastChunk = level.getChunk(newChunkX, newChunkZ);

                    lastChunkX = newChunkX;
                    lastChunkZ = newChunkZ;
                }
                neighbourState = lastChunk.getBlockState(tempPos);
                blockLookupCache.put(cacheKey, neighbourState);
            }

            final FluidState neighbourFluid = neighbourState.getFluidState();
            final FluidState newNeighbourFluid = this.getNewLiquid(level, tempPos, neighbourState);

            if (this.canPassThrough(level, newNeighbourFluid.getType(), fromPos, fromState, direction, tempPos, neighbourState, neighbourFluid)) {
                byte isWaterHole = waterHoleCache.get(cacheKey);
                if (isWaterHole == UNCACHED_RESULT) {
                    final int newChunkX = tempPos.getX() >> 4;
                    final int newChunkZ = tempPos.getZ() >> 4;

                    final int chunkDiff = ((newChunkX ^ lastChunkX) | (newChunkZ ^ lastChunkZ));

                    if (chunkDiff != 0) {
                        lastChunk = level.getChunk(newChunkX, newChunkZ);

                        lastChunkX = newChunkX;
                        lastChunkZ = newChunkZ;
                    }
                    final BlockPos neighbourBelowPos = tempPos.below();
                    BlockState belowState = lastChunk.getBlockState(neighbourBelowPos);

                    isWaterHole = this.isWaterHole(level, this.getFlowing(), tempPos, neighbourState, neighbourBelowPos, belowState) ? (byte)1 : (byte)0;
                    waterHoleCache.put(cacheKey, isWaterHole);
                }

                final int slopeDistance = isWaterHole == (byte)1 ? 0 : this.getSlopeDistanceOptimised(
                        level, tempPos, 1, direction.getOpposite(), neighbourState, fromPos, blockLookupCache, waterHoleCache,
                        lastChunk, lastChunkX, lastChunkZ);

                if (slopeDistance < minSlope) {
                    ret.clear();
                }

                if (slopeDistance <= minSlope) {
                    ret.put(direction, newNeighbourFluid);
                    minSlope = slopeDistance;
                }
            }
        }

        return ret;
    }

    @Unique
    private static final Direction[][] HORIZONTAL_EXCEPT;
    static {
        final Direction[][] except = new Direction[Direction.values().length][];
        for (final Direction direction : HORIZONTAL_ARRAY) {
            final List<Direction> directionsWithout = new ArrayList<>(Arrays.asList(HORIZONTAL_ARRAY));
            directionsWithout.remove(direction);
            except[direction.ordinal()] = directionsWithout.toArray(new Direction[0]);
        }
        HORIZONTAL_EXCEPT = except;
    }

    @Unique
    private int getSlopeDistanceOptimised(final LevelReader level, final BlockPos fromPos, final int step, final Direction fromDirection,
                                          final BlockState fromState, final BlockPos originPos,
                                          final Short2ObjectOpenHashMap<BlockState> blockLookupCache,
                                          final Short2ByteOpenHashMap belowIsWaterHole,
                                          ChunkAccess lastChunk, int lastChunkX, int lastChunkZ) {
        final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();
        final Fluid flowing = this.getFlowing();

        int ret = 1000;

        for (final Direction direction : HORIZONTAL_EXCEPT[fromDirection.ordinal()]) {
            tempPos.set(fromPos.getX() + direction.getStepX(), fromPos.getY(), fromPos.getZ() + direction.getStepZ());
            final short cacheKey = getCacheKey(originPos, tempPos);

            BlockState neighbourState = blockLookupCache.get(cacheKey);
            if (neighbourState == null) {
                final int newChunkX = tempPos.getX() >> 4;
                final int newChunkZ = tempPos.getZ() >> 4;

                final int chunkDiff = ((newChunkX ^ lastChunkX) | (newChunkZ ^ lastChunkZ));

                if (chunkDiff != 0) {
                    lastChunk = level.getChunk(newChunkX, newChunkZ);

                    lastChunkX = newChunkX;
                    lastChunkZ = newChunkZ;
                }
                neighbourState = lastChunk.getBlockState(tempPos);
                blockLookupCache.put(cacheKey, neighbourState);
            }

            FluidState neighbourFluid = neighbourState.getFluidState();
            if (this.canPassThrough(level, flowing, fromPos, fromState, direction, tempPos, neighbourState, neighbourFluid)) {
                byte isWaterHole = belowIsWaterHole.get(cacheKey);
                if (isWaterHole == UNCACHED_RESULT) {
                    final int newChunkX = tempPos.getX() >> 4;
                    final int newChunkZ = tempPos.getZ() >> 4;

                    final int chunkDiff = ((newChunkX ^ lastChunkX) | (newChunkZ ^ lastChunkZ));

                    if (chunkDiff != 0) {
                        lastChunk = level.getChunk(newChunkX, newChunkZ);

                        lastChunkX = newChunkX;
                        lastChunkZ = newChunkZ;
                    }

                    final BlockPos belowPos = tempPos.below();
                    final BlockState belowState = lastChunk.getBlockState(belowPos);
                    isWaterHole = this.isWaterHole(level, flowing, tempPos, neighbourState, belowPos, belowState) ? (byte)1 : (byte)0;
                    belowIsWaterHole.put(cacheKey, isWaterHole);
                }
                if (isWaterHole == (byte)1) {
                    return step;
                }

                if (step < this.getSlopeFindDistance(level)) {
                    final int slopeNeighbour = this.getSlopeDistanceOptimised(
                            level, tempPos, step + 1, direction.getOpposite(), neighbourState, originPos, blockLookupCache, belowIsWaterHole,
                            lastChunk, lastChunkX, lastChunkZ
                    );
                    ret = Math.min(slopeNeighbour, ret);
                }
            }
        }

        return ret;
    }

    /**
     * @reason Avoid indirection for empty/air case
     * @author Spottedleaf
     */
    @Overwrite
    public boolean canSpreadTo(final BlockGetter level,
                               final BlockPos fromPos, final BlockState fromState, final Direction direction,
                               final BlockPos toPos, final BlockState toState, final FluidState toFluid, final Fluid toType) {
        return (toFluid.isEmpty() || toFluid.canBeReplacedWith(level, toPos, toType, direction)) &&
                this.canPassThroughWall(direction, level, fromPos, fromState, toPos, toState) &&
                (toState.isAir() || this.canHoldFluid(level, toPos, toState, toType));
    }

    /**
     * @reason Optimise
     * @author Spottedleaf
     */
    @Overwrite
    private void spreadToSides(final Level level, final BlockPos fromPos, final FluidState fromFluid, final BlockState fromState) {
        final int amount;
        if (fromFluid.getValue(FALLING).booleanValue()) {
            amount = 7;
        } else {
            amount = fromFluid.getAmount() - this.getDropOff(level);
        }

        if (amount <= 0) {
            return;
        }

        ChunkAccess lastChunk = null;
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;

        final Map<Direction, FluidState> spread = this.getSpread(level, fromPos, fromState);
        final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();

        for (final Map.Entry<Direction, FluidState> entry : spread.entrySet()) {
            final Direction direction = entry.getKey();
            final FluidState newFluid = entry.getValue();

            tempPos.set(fromPos.getX() + direction.getStepX(), fromPos.getY() + direction.getStepY(), fromPos.getZ() + direction.getStepZ());

            final int newChunkX = tempPos.getX() >> 4;
            final int newChunkZ = tempPos.getZ() >> 4;

            final int chunkDiff = ((newChunkX ^ lastChunkX) | (newChunkZ ^ lastChunkZ));

            if (chunkDiff != 0) {
                lastChunk = level.getChunk(newChunkX, newChunkZ);

                lastChunkX = newChunkX;
                lastChunkZ = newChunkZ;
            }

            final BlockState currentState = lastChunk.getBlockState(tempPos);
            final FluidState currentFluid = currentState.getFluidState();
            if (this.canSpreadTo(level, fromPos, fromState, direction, tempPos, currentState, currentFluid, newFluid.getType())) {
                this.spreadTo(level, tempPos, currentState, direction, newFluid);
            }
        }
    }

    /**
     * @reason Optimise
     * @author Spottedleaf
     */
    @Overwrite
    private boolean isWaterHole(final BlockGetter level, final Fluid type,
                                final BlockPos fromPos, final BlockState fromState,
                                final BlockPos toPos, final BlockState toState) {
        final FluidClassification classification = ((FluidFluid)this).getClassification();
        final FluidClassification otherClassification = ((FluidFluidState)(Object)toState.getFluidState()).getClassification();
        return this.canPassThroughWall(Direction.DOWN, level, fromPos, fromState, toPos, toState) &&
                (
                        (otherClassification == classification)
                        || (toState.isAir() || this.canHoldFluid(level, toPos, toState, type))
                );
    }

    /**
     * @reason Optimise
     * @author Spottedleaf
     */
    @Overwrite
    private boolean canPassThrough(final BlockGetter level, final Fluid type,
                                   final BlockPos fromPos, final BlockState fromState, final Direction direction,
                                   final BlockPos toPos, final BlockState toState, final FluidState toFluid) {
        final FluidClassification classification = ((FluidFluid)this).getClassification();
        final FluidClassification otherClassification = ((FluidFluidState)(Object)toFluid).getClassification();

        return (!toFluid.isSource() || classification != otherClassification) &&
                this.canPassThroughWall(direction, level, fromPos, fromState, toPos, toState) &&
                (toState.isAir() || this.canHoldFluid(level, toPos, toState, type));
    }
}
