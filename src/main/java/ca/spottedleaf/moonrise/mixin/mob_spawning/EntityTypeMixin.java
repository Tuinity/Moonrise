package ca.spottedleaf.moonrise.mixin.mob_spawning;

import ca.spottedleaf.moonrise.patches.mob_spawning.MobSpawningEntityType;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityType.class)
abstract class EntityTypeMixin implements MobSpawningEntityType {

    @Unique
    private boolean hasBiomeCost = false;

    @Override
    public final boolean moonrise$hasAnyBiomeCost() {
        return this.hasBiomeCost;
    }

    @Override
    public final void moonrise$setHasBiomeCost() {
        this.hasBiomeCost = true;
    }
}
