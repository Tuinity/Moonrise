package ca.spottedleaf.moonrise.patches.chunk_system.entity;

import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;

public interface ChunkSystemEntity {

    public boolean moonrise$isHardColliding();

    // for mods to override
    public default boolean moonrise$isHardCollidingUncached() {
        return this instanceof Boat || this instanceof AbstractMinecart || this instanceof Shulker || ((Entity)this).canBeCollidedWith();
    }

    public FullChunkStatus moonrise$getChunkStatus();

    public void moonrise$setChunkStatus(final FullChunkStatus status);

    public int moonrise$getSectionX();

    public void moonrise$setSectionX(final int x);

    public int moonrise$getSectionY();

    public void moonrise$setSectionY(final int y);

    public int moonrise$getSectionZ();

    public void moonrise$setSectionZ(final int z);

    public boolean moonrise$isUpdatingSectionStatus();

    public void moonrise$setUpdatingSectionStatus(final boolean to);

    public boolean moonrise$hasAnyPlayerPassengers();
}
