package ca.spottedleaf.moonrise.patches.collisions.shape;

import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public record CachedToAABBs(
        List<AABB> aabbs,
        double offX, double offY, double offZ
) {

    public List<AABB> removeOffset() {
        final List<AABB> toOffset = this.aabbs;
        final double offX = this.offX;
        final double offY = this.offY;
        final double offZ = this.offZ;

        final List<AABB> ret = new ArrayList<>(toOffset.size());

        for (int i = 0, len = toOffset.size(); i < len; ++i) {
            ret.add(toOffset.get(i).move(offX, offY, offZ));
        }

        return ret;
    }

    public CachedToAABBs offset(final double offX, final double offY, final double offZ) {
        if (offX == 0.0 && offY == 0.0 && offZ == 0.0) {
            return this;
        }

        return new CachedToAABBs(
            this.aabbs,
            this.offX + offX,
            this.offY + offY,
            this.offZ + offZ
        );
    }
}
