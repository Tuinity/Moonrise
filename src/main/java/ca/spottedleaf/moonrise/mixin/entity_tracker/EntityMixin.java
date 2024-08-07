package ca.spottedleaf.moonrise.mixin.entity_tracker;

import ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity;
import com.google.common.collect.ImmutableList;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
abstract class EntityMixin implements EntityTrackerEntity {

    @Shadow
    private ImmutableList<Entity> passengers;

    @Unique
    private ChunkMap.TrackedEntity trackedEntity;

    @Override
    public final ChunkMap.TrackedEntity moonrise$getTrackedEntity() {
        return this.trackedEntity;
    }

    @Override
    public final void moonrise$setTrackedEntity(final ChunkMap.TrackedEntity trackedEntity) {
        this.trackedEntity = trackedEntity;
    }

    @Unique
    private static void collectIndirectPassengers(final List<Entity> into, final List<Entity> from) {
        for (final Entity passenger : from) {
            into.add(passenger);
            collectIndirectPassengers(into, ((EntityMixin)(Object)passenger).passengers);
        }
    }

    /**
     * @reason Replace with more optimised method
     * @author Spottedleaf
     */
    @Overwrite
    public Iterable<Entity> getIndirectPassengers() {
        final List<Entity> ret = new ArrayList<>();

        if (this.passengers.isEmpty()) {
            return ret;
        }

        collectIndirectPassengers(ret, this.passengers);

        return ret;
    }
}
