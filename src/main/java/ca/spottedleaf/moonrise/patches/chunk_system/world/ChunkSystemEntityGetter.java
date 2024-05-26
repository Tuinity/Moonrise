package ca.spottedleaf.moonrise.patches.chunk_system.world;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.function.Predicate;

public interface ChunkSystemEntityGetter {

    public List<Entity> moonrise$getHardCollidingEntities(final Entity entity, final AABB box, final Predicate<? super Entity> predicate);

}
