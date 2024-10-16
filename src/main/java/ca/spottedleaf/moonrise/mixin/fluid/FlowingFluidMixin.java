package ca.spottedleaf.moonrise.mixin.fluid;

import ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState;
import ca.spottedleaf.moonrise.patches.collisions.util.CollisionDirection;
import ca.spottedleaf.moonrise.patches.collisions.util.FluidOcclusionCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(FlowingFluid.class)
abstract class FlowingFluidMixin extends Fluid {

    @Shadow
    public abstract Fluid getSource();

    @Shadow
    @Final
    public static BooleanProperty FALLING;

    @Shadow
    @Final
    public static IntegerProperty LEVEL;

    @Shadow
    public abstract Fluid getFlowing();


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
        synchronized (this) {
            if (this.init) {
                return;
            }
            this.flowingLookUp = new FluidState[TOTAL_FLOWING_STATES];
            final FluidState defaultFlowState = this.getFlowing().defaultFluidState();
            for (int i = 0; i < TOTAL_FLOWING_STATES; ++i) {
                final int falling = i & 1;
                final int level = (i >>> 1) + MIN_LEVEL;

                this.flowingLookUp[i] = defaultFlowState.setValue(FALLING, falling == 1 ? Boolean.TRUE : Boolean.FALSE)
                    .setValue(LEVEL, Integer.valueOf(level));
            }

            final FluidState defaultFallState = this.getSource().defaultFluidState();
            this.sourceFalling = defaultFallState.setValue(FALLING, Boolean.TRUE);
            this.sourceNotFalling = defaultFallState.setValue(FALLING, Boolean.FALSE);

            this.init = true;
        }
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
    public static boolean canPassThroughWall(final Direction direction, final BlockGetter level,
                                             final BlockPos fromPos, final BlockState fromState,
                                             final BlockPos toPos, final BlockState toState) {
        if (((CollisionBlockState)fromState).moonrise$emptyCollisionShape() & ((CollisionBlockState)toState).moonrise$emptyCollisionShape()) {
            // don't even try to cache simple cases
            return true;
        }

        if (((CollisionBlockState)fromState).moonrise$occludesFullBlock() | ((CollisionBlockState)toState).moonrise$occludesFullBlock()) {
            // don't even try to cache simple cases
            return false;
        }

        final FluidOcclusionCacheKey[] cache = ((CollisionBlockState)fromState).moonrise$hasCache() & ((CollisionBlockState)toState).moonrise$hasCache() ?
                COLLISION_OCCLUSION_CACHE : null;

        final int keyIndex
                = (((CollisionBlockState)fromState).moonrise$uniqueId1() ^ ((CollisionBlockState)toState).moonrise$uniqueId2() ^ ((CollisionDirection)(Object)direction).moonrise$uniqueId())
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
}
