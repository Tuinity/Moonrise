package ca.spottedleaf.moonrise.patches.collisions.world;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.function.Predicate;

public interface CollisionEntityGetter {

    public List<Entity> getHardCollidingEntities(final Entity entity, final AABB box, final Predicate<? super Entity> predicate);

}
