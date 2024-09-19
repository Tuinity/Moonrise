package ca.spottedleaf.moonrise.neoforge.patches.collisions;

import net.minecraft.world.phys.Vec3;

public final class FluidPushCalculation {

    public Vec3 pushVector = Vec3.ZERO;
    public double totalPushes = 0.0;
    public double maxHeightDiff = 0.0;
    public Boolean isPushed = null;

}
