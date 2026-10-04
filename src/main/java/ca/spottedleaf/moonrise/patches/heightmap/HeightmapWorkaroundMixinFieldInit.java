package ca.spottedleaf.moonrise.patches.heightmap;

/**
 * Avoids an issue where placing this field in HeightmapMixin causes the {@link ca.spottedleaf.moonrise.mixin.heightmap.HeightmapMixin#heightmapRaw}
 * field to not initialise
 */
public final class HeightmapWorkaroundMixinFieldInit {

    public static final ThreadLocal<int[]> TEMP_ARRAY = ThreadLocal.withInitial(() -> {
        return new int[16*16];
    });

}
