package ca.spottedleaf.moonrise.mixin.collisions;

import ca.spottedleaf.moonrise.patches.chunk_system.world.ChunkSystemEntityGetter;
import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity;
import ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Mixin(EntityGetter.class)
public interface EntityGetterMixin {

    @Shadow
    List<Entity> getEntities(final Entity entity, final AABB box, final Predicate<? super Entity> predicate);

    @Shadow
    List<Entity> getEntities(final Entity entity, final AABB box);

    /**
     * @reason Route to faster lookup, and fix behavioral issues
     * See {@link CollisionUtil#getEntityHardCollisions} for expected behavior
     * @author Spottedleaf
     */
    @Overwrite
    default List<VoxelShape> getEntityCollisions(final Entity entity, AABB box) {
        // first behavior change is to correctly check for empty AABB
        if (CollisionUtil.isEmpty(box)) {
            // reduce indirection by always returning type with same class
            return new ArrayList<>();
        }

        // to comply with vanilla intersection rules, expand by -epsilon so that we only get stuff we definitely collide with.
        // Vanilla for hard collisions has this backwards, and they expand by +epsilon but this causes terrible problems
        // specifically with boat collisions.
        box = box.inflate(-CollisionUtil.COLLISION_EPSILON, -CollisionUtil.COLLISION_EPSILON, -CollisionUtil.COLLISION_EPSILON);

        final List<Entity> entities;
        if (entity != null && ((ChunkSystemEntity)entity).moonrise$isHardColliding()) {
            entities = this.getEntities(entity, box, null);
        } else {
            entities = ((ChunkSystemEntityGetter)this).moonrise$getHardCollidingEntities(entity, box, null);
        }

        final List<VoxelShape> ret = new ArrayList<>(Math.min(25, entities.size()));

        for (int i = 0, len = entities.size(); i < len; ++i) {
            final Entity otherEntity = entities.get(i);

            if (otherEntity.isSpectator()) {
                continue;
            }

            if ((entity == null && otherEntity.canBeCollidedWith()) || (entity != null && entity.canCollideWith(otherEntity))) {
                ret.add(Shapes.create(otherEntity.getBoundingBox()));
            }
        }

        return ret;
    }

    /**
     * @reason Use faster intersection checks
     * @author Spottedleaf
     */
    @Overwrite
    default boolean isUnobstructed(final Entity entity, final VoxelShape voxel) {
        if (voxel.isEmpty()) {
            return false;
        }

        final AABB singleAABB = ((CollisionVoxelShape)voxel).moonrise$getSingleAABBRepresentation();
        final List<Entity> entities = this.getEntities(
                entity,
                singleAABB == null ? voxel.bounds() : singleAABB.inflate(-CollisionUtil.COLLISION_EPSILON, -CollisionUtil.COLLISION_EPSILON, -CollisionUtil.COLLISION_EPSILON)
        );

        for (int i = 0, len = entities.size(); i < len; ++i) {
            final Entity otherEntity = entities.get(i);

            if (otherEntity.isRemoved() || !otherEntity.blocksBuilding || (entity != null && otherEntity.isPassengerOfSameVehicle(entity))) {
                continue;
            }

            if (singleAABB == null) {
                final AABB entityBB = otherEntity.getBoundingBox();
                if (CollisionUtil.isEmpty(entityBB) || !CollisionUtil.voxelShapeIntersectNoEmpty(voxel, entityBB)) {
                    continue;
                }
            }

            return false;
        }

        return true;
    }
}
