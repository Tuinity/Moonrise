package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseMixin extends StateHolder<Block, BlockState> implements CollisionBlockState {

    @Shadow
    protected BlockBehaviour.BlockStateBase.Cache cache;

    @Shadow
    public abstract VoxelShape getCollisionShape(BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext);

    protected BlockStateBaseMixin(Block object, Reference2ObjectArrayMap<Property<?>, Comparable<?>> reference2ObjectArrayMap, MapCodec<BlockState> mapCodec) {
        super(object, reference2ObjectArrayMap, mapCodec);
    }

    @Unique
    private static final int RANDOM_OFFSET = 704237939;

    @Unique
    private static final Direction[] DIRECTIONS_CACHED = Direction.values();

    @Unique
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger();

    @Unique
    private final int id1 = HashCommon.murmurHash3(HashCommon.murmurHash3(ID_GENERATOR.getAndIncrement() + RANDOM_OFFSET) + RANDOM_OFFSET);
    @Unique
    private final int id2 = HashCommon.murmurHash3(HashCommon.murmurHash3(ID_GENERATOR.getAndIncrement() + RANDOM_OFFSET) + RANDOM_OFFSET);

    @Unique
    private boolean occludesFullBlock;

    @Unique
    private boolean emptyCollisionShape;

    @Unique
    private VoxelShape constantCollisionShape;

    @Unique
    private AABB constantAABBCollision;

    @Unique
    private static void initCaches(final VoxelShape shape, final boolean neighbours) {
        ((CollisionVoxelShape)shape).moonrise$isFullBlock();
        ((CollisionVoxelShape)shape).moonrise$occludesFullBlock();
        shape.toAabbs();
        if (!shape.isEmpty()) {
            shape.bounds();
        }
        if (neighbours) {
            for (final Direction direction : DIRECTIONS_CACHED) {
                initCaches(Shapes.getFaceShape(shape, direction), false);
                initCaches(shape.getFaceShape(direction), false);
            }
        }
    }

    /**
     * @reason Init collision state only after cache is set up
     * @author Spottedleaf
     */
    @Inject(
            method = "initCache",
            at = @At(
                    value = "RETURN"
            )
    )
    private void initCollisionState(final CallbackInfo ci) {
        if (this.cache != null) {
            final VoxelShape collisionShape = this.cache.collisionShape;
            try {
                this.constantCollisionShape = this.getCollisionShape(null, null, null);
                this.constantAABBCollision = this.constantCollisionShape == null ? null : ((CollisionVoxelShape)this.constantCollisionShape).moonrise$getSingleAABBRepresentation();
            } catch (final Throwable throwable) {
                // :(
                this.constantCollisionShape = null;
                this.constantAABBCollision = null;
            }
            this.occludesFullBlock = ((CollisionVoxelShape)collisionShape).moonrise$occludesFullBlock();
            this.emptyCollisionShape = collisionShape.isEmpty();
            // init caches
            initCaches(collisionShape, true);
            if (this.constantCollisionShape != null) {
                initCaches(this.constantCollisionShape, true);
            }
            if (this.cache.occlusionShapes != null) {
                for (final VoxelShape shape : this.cache.occlusionShapes) {
                    initCaches(shape, false);
                }
            }
        } else {
            this.occludesFullBlock = false;
            this.emptyCollisionShape = false;
            this.constantCollisionShape = null;
            this.constantAABBCollision = null;
        }
    }

    @Override
    public final boolean moonrise$hasCache() {
        return this.cache != null;
    }

    @Override
    public final boolean moonrise$occludesFullBlock() {
        return this.occludesFullBlock;
    }

    @Override
    public final boolean moonrise$emptyCollisionShape() {
        return this.emptyCollisionShape;
    }

    @Override
    public final int moonrise$uniqueId1() {
        return this.id1;
    }

    @Override
    public final int moonrise$uniqueId2() {
        return this.id2;
    }

    @Override
    public final VoxelShape moonrise$getConstantCollisionShape() {
        return this.constantCollisionShape;
    }

    @Override
    public final AABB moonrise$getConstantCollisionAABB() {
        return this.constantAABBCollision;
    }
}
