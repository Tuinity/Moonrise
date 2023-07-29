package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.chunk_getblock.GetBlockChunk;
import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface;
import ca.spottedleaf.moonrise.patches.starlight.light.StarLightLightingProvider;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.ArrayList;
import java.util.List;

@Mixin(Particle.class)
public abstract class ParticleMixin {

    @Shadow
    protected double x;

    @Shadow
    protected double y;

    @Shadow
    protected double z;

    @Shadow
    @Final
    protected ClientLevel level;



    /**
     * @reason Optimise the collision for particles
     * @author Spottedleaf
     */
    @Redirect(
            method = "move(DDD)V",
            at = @At(
                    target = "Lnet/minecraft/world/entity/Entity;collideBoundingBox(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;",
                    value = "INVOKE"
            )
    )
    private Vec3 optimiseParticleCollisions(final Entity entity, final Vec3 movement, final AABB entityBoundingBox, final Level world, final List<VoxelShape> list) {
        final AABB collisionBox;

        final boolean xEmpty = Math.abs(movement.x) < CollisionUtil.COLLISION_EPSILON;
        final boolean yEmpty = Math.abs(movement.y) < CollisionUtil.COLLISION_EPSILON;
        final boolean zEmpty = Math.abs(movement.z) < CollisionUtil.COLLISION_EPSILON;

        // try and get the smallest collision box possible by taking advantage of single-axis move
        if (!xEmpty & (yEmpty | zEmpty)) {
            if (movement.x < 0.0) {
                collisionBox = CollisionUtil.cutLeft(entityBoundingBox, movement.x);
            } else {
                collisionBox = CollisionUtil.cutRight(entityBoundingBox, movement.x);
            }
        } else if (!yEmpty & (xEmpty | zEmpty)) {
            if (movement.y < 0.0) {
                collisionBox = CollisionUtil.cutDownwards(entityBoundingBox, movement.y);
            } else {
                collisionBox = CollisionUtil.cutUpwards(entityBoundingBox, movement.y);
            }
        } else if (!zEmpty & (xEmpty | yEmpty)) {
            if (movement.z < 0.0) {
                collisionBox = CollisionUtil.cutBackwards(entityBoundingBox, movement.z);
            } else {
                collisionBox = CollisionUtil.cutForwards(entityBoundingBox, movement.z);
            }
        } else {
            collisionBox = entityBoundingBox.expandTowards(movement.x, movement.y, movement.z);
        }

        final List<AABB> boxes = new ArrayList<>();
        final List<VoxelShape> voxels = new ArrayList<>();
        final boolean collided = CollisionUtil.getCollisionsForBlocksOrWorldBorder(
                world, entity, collisionBox, voxels, boxes,
                0,
                null
        );

        if (!collided) {
            // most of the time we fall here.
            return movement;
        }

        return CollisionUtil.performCollisions(movement, entityBoundingBox, voxels, boxes);
    }

    /**
     * @reason Optimise impl
     * @author Spottedleaf
     */
    @Overwrite
    public int getLightColor(final float f) {
        final int blockX = Mth.floor(this.x);
        final int blockY = Mth.floor(this.y);
        final int blockZ = Mth.floor(this.z);

        final BlockPos pos = new BlockPos(blockX, blockY, blockZ);

        final LevelChunk chunk = this.level.getChunkSource().getChunk(blockX >> 4, blockZ >> 4, ChunkStatus.FULL, false);
        final BlockState blockState = chunk == null ? null : ((GetBlockChunk)chunk).getBlock(blockX, blockY, blockZ);

        if (blockState != null && !blockState.isAir()) {
            if (blockState.emissiveRendering(this.level, pos)) {
                return 15728880;
            }
        }

        final StarLightInterface lightEngine = ((StarLightLightingProvider)this.level.getLightEngine()).getLightEngine();

        final int blockLight = Math.max(lightEngine.getBlockLightValue(pos, chunk), blockState == null ? 0 : blockState.getLightEmission());
        final int skyLight = lightEngine.getSkyLightValue(pos, chunk);

        return blockLight << 20 | skyLight << 4;
    }
}
