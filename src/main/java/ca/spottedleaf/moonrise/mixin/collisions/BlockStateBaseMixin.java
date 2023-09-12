package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.HashCommon;
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
public abstract class BlockStateBaseMixin extends StateHolder<Block, BlockState> implements CollisionBlockState {

    @Shadow
    protected BlockBehaviour.BlockStateBase.Cache cache;

    @Shadow
    public abstract VoxelShape getCollisionShape(BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext);

    protected BlockStateBaseMixin(Block object, ImmutableMap<Property<?>, Comparable<?>> immutableMap, MapCodec<BlockState> mapCodec) {
        super(object, immutableMap, mapCodec);
    }


    @Unique
    private static final int RANDOM_OFFSET = 704237939;

    @Unique
    private static final Direction[] DIRECTIONS_CACHED = Direction.values();

    @Unique
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger();

    @Unique
    private int id1, id2;

    @Unique
    private boolean occludesFullBlock;

    @Unique
    private boolean emptyCollisionShape;

    @Unique
    private VoxelShape constantCollisionShape;

    @Unique
    private AABB constantAABBCollision;

    @Unique
    private static void initCaches(final VoxelShape shape) {
        ((CollisionVoxelShape)shape).isFullBlock();
        ((CollisionVoxelShape)shape).occludesFullBlock();
        shape.toAabbs();
        if (!shape.isEmpty()) {
            shape.bounds();
        }
    }

    @Inject(
            method = "<init>",
            at = @At(
                    value = "RETURN"
            )
    )
    private void init(final CallbackInfo ci) {
        // note: murmurHash3 has an inverse, so the field is still unique
        this.id1 = HashCommon.murmurHash3(HashCommon.murmurHash3(ID_GENERATOR.getAndIncrement() + RANDOM_OFFSET) + RANDOM_OFFSET);
        this.id2 = HashCommon.murmurHash3(HashCommon.murmurHash3(ID_GENERATOR.getAndIncrement() + RANDOM_OFFSET) + RANDOM_OFFSET);
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
                this.constantAABBCollision = this.constantCollisionShape == null ? null : ((CollisionVoxelShape)this.constantCollisionShape).getSingleAABBRepresentation();
            } catch (final Throwable throwable) {
                this.constantCollisionShape = null;
                this.constantAABBCollision = null;
            }
            this.occludesFullBlock = ((CollisionVoxelShape)collisionShape).occludesFullBlock();
            this.emptyCollisionShape = collisionShape.isEmpty();
            // init caches
            initCaches(collisionShape);
            if (collisionShape != Shapes.empty() && collisionShape != Shapes.block()) {
                for (final Direction direction : DIRECTIONS_CACHED) {
                    // initialise the directional face shape cache as well
                    final VoxelShape shape = Shapes.getFaceShape(collisionShape, direction);
                    initCaches(shape);
                }
            }
            if (this.cache.occlusionShapes != null) {
                for (final VoxelShape shape : this.cache.occlusionShapes) {
                    initCaches(shape);
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
    public final boolean hasCache() {
        return this.cache != null;
    }

    @Override
    public final boolean occludesFullBlock() {
        return this.occludesFullBlock;
    }

    @Override
    public final boolean emptyCollisionShape() {
        return this.emptyCollisionShape;
    }

    @Override
    public final int uniqueId1() {
        return this.id1;
    }

    @Override
    public final int uniqueId2() {
        return this.id2;
    }

    @Override
    public final VoxelShape getConstantCollisionShape() {
        return this.constantCollisionShape;
    }

    @Override
    public final AABB getConstantCollisionAABB() {
        return this.constantAABBCollision;
    }
}
