package ca.spottedleaf.moonrise.common.util;

public final class MixinWorkarounds {

    // mixins tries to find the owner of the clone() method, which doesn't exist and NPEs
    public static long[] clone(final long[] values) {
        return values.clone();
    }

}
