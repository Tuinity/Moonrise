package ca.spottedleaf.moonrise.patches.entity_tracker;

import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;

public interface EntityTrackerTrackedEntity {

    public void moonrise$tick(final NearbyPlayers.TrackedChunk chunk);

    public void moonrise$removeNonTickThreadPlayers();

    public void moonrise$clearPlayers();

}
