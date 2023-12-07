package ca.spottedleaf.moonrise.patches.collisions.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;

public interface CollisionEntity {

    public boolean moonrise$isHardColliding();

    // for mods to override
    public default boolean moonrise$isHardCollidingUncached() {
        return this instanceof Boat || this instanceof AbstractMinecart || this instanceof Shulker || ((Entity)this).canBeCollidedWith();
    }
}
