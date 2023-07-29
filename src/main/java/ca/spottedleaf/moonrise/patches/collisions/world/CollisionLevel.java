package ca.spottedleaf.moonrise.patches.collisions.world;

import ca.spottedleaf.moonrise.patches.collisions.slices.EntityLookup;

public interface CollisionLevel {

    public EntityLookup getCollisionLookup();

    // avoid name conflicts by appending mod name

    public int getMinSectionMoonrise();

    public int getMaxSectionMoonrise();

}
