package ca.spottedleaf.moonrise.patches.chunk_system.level.chunk;

import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;

public final class ChunkData {

    private int referenceCount = 0;
    public NearbyPlayers.TrackedChunk nearbyPlayers; // Moonrise - nearby players

    public ChunkData() {

    }

    public int increaseRef() {
        return ++this.referenceCount;
    }

    public int decreaseRef() {
        return --this.referenceCount;
    }
}
