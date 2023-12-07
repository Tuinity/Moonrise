package ca.spottedleaf.moonrise.patches.collisions.world;

import ca.spottedleaf.moonrise.patches.collisions.slices.EntityLookup;

public interface CollisionLevel {

    public EntityLookup moonrise$getCollisionLookup();

    // avoid name conflicts by appending mod name

    public int moonrise$getMinSection();

    public int moonrise$getMaxSection();

}
