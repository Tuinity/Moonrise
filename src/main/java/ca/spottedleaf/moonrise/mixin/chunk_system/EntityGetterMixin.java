package ca.spottedleaf.moonrise.mixin.chunk_system;

import ca.spottedleaf.moonrise.patches.chunk_system.world.ChunkSystemEntityGetter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.List;
import java.util.function.Predicate;

@Mixin(EntityGetter.class)
public interface EntityGetterMixin extends ChunkSystemEntityGetter {

    @Shadow
    List<Entity> getEntities(Entity entity, AABB aABB, Predicate<? super Entity> predicate);

    @Override
    default List<Entity> moonrise$getHardCollidingEntities(final Entity entity, final AABB box, final Predicate<? super Entity> predicate) {
        return this.getEntities(entity, box, predicate);
    }
}
