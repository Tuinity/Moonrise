package ca.spottedleaf.moonrise.patches.collisions.shape;

import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

public record CachedToAABBs(
        List<AABB> aabbs,
        boolean isOffset,
        double offX, double offY, double offZ
) {

    public CachedToAABBs removeOffset() {
        final List<AABB> toOffset = this.aabbs;
        final double offX = this.offX;
        final double offY = this.offY;
        final double offZ = this.offZ;

        final List<AABB> ret = new ArrayList<>(toOffset.size());

        for (int i = 0, len = toOffset.size(); i < len; ++i) {
            ret.add(toOffset.get(i).move(offX, offY, offZ));
        }

        return new CachedToAABBs(ret, false, 0.0, 0.0, 0.0);
    }

    public static CachedToAABBs offset(final CachedToAABBs cache, final double offX, final double offY, final double offZ) {
        if (offX == 0.0 && offY == 0.0 && offZ == 0.0) {
            return cache;
        }

        final double resX = cache.offX + offX;
        final double resY = cache.offY + offY;
        final double resZ = cache.offZ + offZ;

        return new CachedToAABBs(cache.aabbs, true, resX, resY, resZ);
    }
}
