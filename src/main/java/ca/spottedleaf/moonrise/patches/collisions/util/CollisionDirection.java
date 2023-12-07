package ca.spottedleaf.moonrise.patches.collisions.util;

public interface CollisionDirection {

    // note: this is HashCommon#murmurHash3(some unique id) and since murmurHash3 has an inverse function the returned
    // value is still unique
    public int moonrise$uniqueId();

}
