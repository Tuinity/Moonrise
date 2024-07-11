package ca.spottedleaf.moonrise.patches.entity_tracker;

import net.minecraft.server.level.ChunkMap;

public interface EntityTrackerEntity {

    public ChunkMap.TrackedEntity moonrise$getTrackedEntity();

    public void moonrise$setTrackedEntity(final ChunkMap.TrackedEntity trackedEntity);

}
