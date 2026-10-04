package ca.spottedleaf.moonrise.mixin.chunk_gen;

import ca.spottedleaf.moonrise.patches.chunk_gen.ChunkGenPerlinNoise;
import ca.spottedleaf.moonrise.patches.chunk_gen.NoiseGradients;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.synth.GradientNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Arrays;

import static ca.spottedleaf.moonrise.patches.chunk_gen.NoiseGradients.DEBUG_NOISE;
import static ca.spottedleaf.moonrise.patches.chunk_gen.NoiseGradients.PERM_MASK;
import static ca.spottedleaf.moonrise.patches.chunk_gen.NoiseGradients.PERM_SIZE;
import static ca.spottedleaf.moonrise.patches.chunk_gen.NoiseGradients.PERM_SHIFT;

@Mixin(PerlinNoise.class)
abstract class PerlinNoiseMixin extends GradientNoise implements ChunkGenPerlinNoise {

    protected PerlinNoiseMixin(final RandomSource random) {
        super(random);
    }

    @Unique
    protected final char[] pAdj = new char[PERM_SIZE];

    /**
     * @reason Initialise dual p lookup table
     * @author Spottedleaf
     */
    @Inject(
        method = "<init>",
        at = @At(
            value = "RETURN"
        )
    )
    private void initAdjTable(final CallbackInfo ci) {
        for (int i = 0; i < PERM_SIZE; ++i) {
            this.pAdj[i] = (char)(this.p(i) | (this.p(i + 1) << PERM_SHIFT));
        }
    }

    @Override
    public final char[] moonrise$getPAdj() {
        return this.pAdj;
    }

    @Unique
    private final int p(final int x) {
        return this.perms[x & PERM_MASK] & PERM_MASK;
    }

    @Unique
    private float sampleAndLerpV(int x, int y, int z, float relativeX, float relativeY, float relativeZ, float originalRelativeY) {
        int x0 = this.permute(x);
        int x1 = this.permute(x + 1);
        int xy00 = this.permute(x0 + y);
        int xy01 = this.permute(x0 + y + 1);
        int xy10 = this.permute(x1 + y);
        int xy11 = this.permute(x1 + y + 1);
        float d000 = gradDot(this.permute(xy00 + z), relativeX, relativeY, relativeZ);
        float d100 = gradDot(this.permute(xy10 + z), relativeX - 1.0F, relativeY, relativeZ);
        float d010 = gradDot(this.permute(xy01 + z), relativeX, relativeY - 1.0F, relativeZ);
        float d110 = gradDot(this.permute(xy11 + z), relativeX - 1.0F, relativeY - 1.0F, relativeZ);
        float d001 = gradDot(this.permute(xy00 + z + 1), relativeX, relativeY, relativeZ - 1.0F);
        float d101 = gradDot(this.permute(xy10 + z + 1), relativeX - 1.0F, relativeY, relativeZ - 1.0F);
        float d011 = gradDot(this.permute(xy01 + z + 1), relativeX, relativeY - 1.0F, relativeZ - 1.0F);
        float d111 = gradDot(this.permute(xy11 + z + 1), relativeX - 1.0F, relativeY - 1.0F, relativeZ - 1.0F);
        float xAlpha = Mth.smoothstep(relativeX);
        float yAlpha = Mth.smoothstep(originalRelativeY);
        float zAlpha = Mth.smoothstep(relativeZ);
        return Mth.lerp3(xAlpha, yAlpha, zAlpha, d000, d100, d010, d110, d001, d101, d011, d111);
    }


    /**
     * @reason Optimise this function (reduce p-table lookups by 2x)
     * @author Spottedleaf
     */
    @Overwrite
    protected float sampleAndLerp(final int x, final int y, final int z,
                                  final float relativeX, final float relativeY, final float relativeZ,
                                  final float originalRelativeY) {
        final char[] p = this.pAdj;

        // note: since the p table is arithmetic mod PERM_SIZE, we do not need to mask out the upper
        final int x0 = (int)p[x & PERM_MASK];
        final int x1 = x0 >>> PERM_SHIFT;

        final int xy00 = (int)p[(x0 + y) & PERM_MASK];
        final int xy01 = xy00 >>> PERM_SHIFT;

        final int z000 = (int)p[(xy00 + z) & PERM_MASK];
        final int z001 = (int)p[(xy01 + z) & PERM_MASK];

        final float d000 = NoiseGradients.dot(z000 & 15, relativeX, relativeY, relativeZ);
        final float d001 = NoiseGradients.dot((z000 >>> PERM_SHIFT) & 15, relativeX, relativeY, relativeZ - 1.0F);

        final float d010 = NoiseGradients.dot(z001 & 15, relativeX, relativeY - 1.0F, relativeZ);
        final float d011 = NoiseGradients.dot((z001 >>> PERM_SHIFT) & 15, relativeX, relativeY - 1.0F, relativeZ - 1.0F);


        final int xy10 = (int)p[(x1 + y) & PERM_MASK];
        final int xy11 = xy10 >>> PERM_SHIFT;

        final int z100 = (int)p[(xy10 + z) & PERM_MASK];
        final int z101 = (int)p[(xy11 + z) & PERM_MASK];

        final float d100 = NoiseGradients.dot(z100 & 15, relativeX - 1.0F, relativeY, relativeZ);
        final float d101 = NoiseGradients.dot((z100 >>> PERM_SHIFT) & 15, relativeX - 1.0F, relativeY, relativeZ - 1.0F);

        final float d110 = NoiseGradients.dot(z101 & 15, relativeX - 1.0F, relativeY - 1.0F, relativeZ);
        final float d111 = NoiseGradients.dot((z101 >>> PERM_SHIFT) & 15, relativeX - 1.0F, relativeY - 1.0F, relativeZ - 1.0F);

        final float ret = Mth.lerp3(Mth.smoothstep(relativeX), Mth.smoothstep(originalRelativeY), Mth.smoothstep(relativeZ), d000, d100, d010, d110, d001, d101, d011, d111);
        if (!DEBUG_NOISE) {
            return ret;
        }
        if (ret != this.sampleAndLerpV(x, y, z, relativeX, relativeY, relativeZ, originalRelativeY)) {
            this.sampleAndLerpV(x, y, z, relativeX, relativeY, relativeZ, originalRelativeY);
            throw new IllegalStateException();
        }
        return ret;
    }

    @Unique
    private void addToVolumeV(DensityBuffer buffer, DensityVolume volume, double xzScale, double yScale, float amplitude) {
        float d000xz = 0.0F;
        float d100xz = 0.0F;
        float d010xz = 0.0F;
        float d110xz = 0.0F;
        float d001xz = 0.0F;
        float d101xz = 0.0F;
        float d011xz = 0.0F;
        float d111xz = 0.0F;
        float g000y = 0.0F;
        float g100y = 0.0F;
        float g010y = 0.0F;
        float g110y = 0.0F;
        float g001y = 0.0F;
        float g101y = 0.0F;
        float g011y = 0.0F;
        float g111y = 0.0F;
        int index = 0;

        for(int indexZ = 0; indexZ < volume.sizeZ(); ++indexZ) {
            double z = wrap((double)volume.blockZ(indexZ) * xzScale) + this.offsetZ;
            int floorZ = Mth.floor(z);
            float relativeZ = (float)(z - (double)floorZ);
            float alphaZ = Mth.smoothstep(relativeZ);

            for(int indexX = 0; indexX < volume.sizeX(); ++indexX) {
                double x = wrap((double)volume.blockX(indexX) * xzScale) + this.offsetX;
                int floorX = Mth.floor(x);
                float relativeX = (float)(x - (double)floorX);
                int x0 = this.permute(floorX);
                int x1 = this.permute(floorX + 1);
                float alphaX = Mth.smoothstep(relativeX);
                int lastFloorY = Integer.MIN_VALUE;

                for(int indexY = 0; indexY < volume.sizeY(); ++indexY) {
                    double y = wrap((double)volume.blockY(indexY) * yScale) + this.offsetY;
                    int floorY = Mth.floor(y);
                    float relativeY = (float)(y - (double)floorY);
                    float alphaY = Mth.smoothstep(relativeY);
                    if (lastFloorY != floorY) {
                        int xy00 = this.permute(x0 + floorY);
                        int xy01 = this.permute(x0 + floorY + 1);
                        int xy10 = this.permute(x1 + floorY);
                        int xy11 = this.permute(x1 + floorY + 1);
                        GradientNoise.Gradient g000 = this.permuteToGrad(xy00 + floorZ);
                        d000xz = g000.dotXz(relativeX, relativeZ);
                        g000y = (float)g000.y();
                        GradientNoise.Gradient g100 = this.permuteToGrad(xy10 + floorZ);
                        d100xz = g100.dotXz(relativeX - 1.0F, relativeZ);
                        g100y = (float)g100.y();
                        GradientNoise.Gradient g010 = this.permuteToGrad(xy01 + floorZ);
                        d010xz = g010.dotXz(relativeX, relativeZ);
                        g010y = (float)g010.y();
                        GradientNoise.Gradient g110 = this.permuteToGrad(xy11 + floorZ);
                        d110xz = g110.dotXz(relativeX - 1.0F, relativeZ);
                        g110y = (float)g110.y();
                        GradientNoise.Gradient g001 = this.permuteToGrad(xy00 + floorZ + 1);
                        d001xz = g001.dotXz(relativeX, relativeZ - 1.0F);
                        g001y = (float)g001.y();
                        GradientNoise.Gradient g101 = this.permuteToGrad(xy10 + floorZ + 1);
                        d101xz = g101.dotXz(relativeX - 1.0F, relativeZ - 1.0F);
                        g101y = (float)g101.y();
                        GradientNoise.Gradient g011 = this.permuteToGrad(xy01 + floorZ + 1);
                        d011xz = g011.dotXz(relativeX, relativeZ - 1.0F);
                        g011y = (float)g011.y();
                        GradientNoise.Gradient g111 = this.permuteToGrad(xy11 + floorZ + 1);
                        d111xz = g111.dotXz(relativeX - 1.0F, relativeZ - 1.0F);
                        g111y = (float)g111.y();
                        lastFloorY = floorY;
                    }

                    buffer.addTo(index, amplitude * Mth.lerp3(alphaX, alphaY, alphaZ, d000xz + g000y * relativeY, d100xz + g100y * relativeY, d010xz + g010y * (relativeY - 1.0F), d110xz + g110y * (relativeY - 1.0F), d001xz + g001y * relativeY, d101xz + g101y * relativeY, d011xz + g011y * (relativeY - 1.0F), d111xz + g111y * (relativeY - 1.0F)));
                    ++index;
                }
            }
        }
    }

    /**
     * @reason Optimise this function (reduce p-table lookups by 2x, eliminate redundant coordinate calculation)
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public void addToVolume(final DensityBuffer buffer, final DensityVolume volume,
                            final double xzScale, final double yScale, final float amplitude) {
        final float[] vanillaNoise;
        if (DEBUG_NOISE) {
            final float[] cpy = buffer.values.clone();
            this.addToVolumeV(buffer, volume, xzScale, yScale, amplitude);
            vanillaNoise = buffer.values.clone();
            System.arraycopy(cpy, 0, buffer.values, 0, cpy.length);
        }
        float d000xz = 0.0F;
        float d100xz = 0.0F;
        float d010xz = 0.0F;
        float d110xz = 0.0F;
        float d001xz = 0.0F;
        float d101xz = 0.0F;
        float d011xz = 0.0F;
        float d111xz = 0.0F;
        float g000y = 0.0F;
        float g100y = 0.0F;
        float g010y = 0.0F;
        float g110y = 0.0F;
        float g001y = 0.0F;
        float g101y = 0.0F;
        float g011y = 0.0F;
        float g111y = 0.0F;

        final int sX = volume.sizeX();
        final int sY = volume.sizeY();
        final int sZ = volume.sizeZ();

        final NoiseGradients.CoordCache[] cacheX = NoiseGradients.getCoordCache(NoiseGradients.COORD_CACHE_X, sX);
        final NoiseGradients.CoordCache[] cacheY = NoiseGradients.getCoordCache(NoiseGradients.COORD_CACHE_Y, sY);
        final NoiseGradients.CoordCache[] cacheZ = NoiseGradients.getCoordCache(NoiseGradients.COORD_CACHE_Z, sZ);

        // only compute coordinate ONCE
        // note: we do not need the floating point value of the coordinate, all we need is floor, alpha, and relative

        double xv = (double)volume.minBlockX(), xi = (double)volume.stepBlockX();
        for (int x = 0; x < sX; ++x, xv += xi) {
            final double val = wrap(xv * xzScale) + this.offsetX;
            final double floorD = Math.floor(val);
            final int floor = (int)floorD;
            final float relative = (float)(val - floorD);
            final float alpha = Mth.smoothstep(relative);

            final NoiseGradients.CoordCache cx = cacheX[x];

            cx.floor = floor;
            cx.alpha = alpha;
            cx.relative = relative;
        }
        double yv = (double)volume.minBlockY(), yi = (double)volume.stepBlockY();
        for (int y = 0; y < sY; ++y, yv += yi) {
            final double val = wrap(yv * yScale) + this.offsetY;
            final double floorD = Math.floor(val);
            final int floor = (int)floorD;
            final float relative = (float)(val - floorD);
            final float alpha = Mth.smoothstep(relative);

            final NoiseGradients.CoordCache cy = cacheY[y];

            cy.floor = floor;
            cy.alpha = alpha;
            cy.relative = relative;
        }
        double zv = (double)volume.minBlockZ(), zi = (double)volume.stepBlockZ();
        for (int z = 0; z < sZ; ++z, zv += zi) {
            final double val = wrap(zv * xzScale) + this.offsetZ;
            final double floorD = Math.floor(val);
            final int floor = (int)floorD;
            final float relative = (float)(val - floorD);
            final float alpha = Mth.smoothstep(relative);

            final NoiseGradients.CoordCache cz = cacheZ[z];

            cz.floor = floor;
            cz.alpha = alpha;
            cz.relative = relative;
        }

        // index = indexY + indexX*(sizeY) + indexZ*(sizeY*sizeX)

        final char[] p = this.pAdj;

        // re-order X to be primary so we never repeat the p table lookup
        // note: this means we can no longer store index
        for (int indexX = 0; indexX < sX; ++indexX) {
            final NoiseGradients.CoordCache cx = cacheX[indexX];
            final int floorX = cx.floor;
            final float alphaX = cx.alpha;
            final float relativeX = cx.relative;
            // note: since the p table is arithmetic mod PERM_SIZE, we do not need to mask out the upper
            final int x0 = (int)p[floorX & PERM_MASK];
            final int x1 = x0 >>> PERM_SHIFT;

            for (int indexZ = 0; indexZ < sZ; ++indexZ) {
                final NoiseGradients.CoordCache cz = cacheZ[indexZ];
                final int floorZ = cz.floor;
                final float alphaZ = cz.alpha;
                final float relativeZ = cz.relative;
                int lastFloorY = Integer.MIN_VALUE;

                int index = sY*(indexX + indexZ*sX);
                for (int indexY = 0; indexY < sY; ++indexY) {
                    final NoiseGradients.CoordCache cy = cacheY[indexY];
                    final int floorY = cy.floor;
                    final float alphaY = cy.alpha;
                    final float relativeY = cy.relative;
                    if (lastFloorY != floorY) {
                        lastFloorY = floorY;
                        final int xy00 = (int)p[(x0 + floorY) & PERM_MASK];
                        final int xy01 = xy00 >>> PERM_SHIFT;

                        final int z000 = (int)p[(xy00 + floorZ) & PERM_MASK];
                        final int z010 = (int)p[(xy01 + floorZ) & PERM_MASK];
                        final int g000 = z000 & 15;
                        final int g001 = (z000 >>> PERM_SHIFT) & 15;
                        d000xz = NoiseGradients.dotXz(g000, relativeX, relativeZ);
                        g000y = NoiseGradients.gradY(g000);
                        d001xz = NoiseGradients.dotXz(g001, relativeX, relativeZ - 1.0F);
                        g001y = NoiseGradients.gradY(g001);

                        final int g010 = z010 & 15;
                        final int g011 = (z010 >>> PERM_SHIFT) & 15;
                        d010xz = NoiseGradients.dotXz(g010, relativeX, relativeZ);
                        g010y = NoiseGradients.gradY(g010);
                        d011xz = NoiseGradients.dotXz(g011, relativeX, relativeZ - 1.0F);
                        g011y = NoiseGradients.gradY(g011);

                        final int xy10 = (int)p[(x1 + floorY) & PERM_MASK];
                        final int xy11 = xy10 >>> PERM_SHIFT;

                        final int z100 = (int)p[(xy10 + floorZ) & PERM_MASK];
                        final int z110 = (int)p[(xy11 + floorZ) & PERM_MASK];
                        final int g100 = z100 & 15;
                        final int g101 = (z100 >>> PERM_SHIFT) & 15;
                        d100xz = NoiseGradients.dotXz(g100, relativeX - 1.0F, relativeZ);
                        g100y = NoiseGradients.gradY(g100);
                        d101xz = NoiseGradients.dotXz(g101, relativeX - 1.0F, relativeZ - 1.0F);
                        g101y = NoiseGradients.gradY(g101);

                        final int g110 = z110 & 15;
                        final int g111 = (z110 >>> PERM_SHIFT) & 15;
                        d110xz = NoiseGradients.dotXz(g110, relativeX - 1.0F, relativeZ);
                        g110y = NoiseGradients.gradY(g110);
                        d111xz = NoiseGradients.dotXz(g111, relativeX - 1.0F, relativeZ - 1.0F);
                        g111y = NoiseGradients.gradY(g111);
                    }

                    buffer.values[index++] += (amplitude * Mth.lerp3(alphaX, alphaY, alphaZ, d000xz + g000y * relativeY, d100xz + g100y * relativeY, d010xz + g010y * (relativeY - 1.0F), d110xz + g110y * (relativeY - 1.0F), d001xz + g001y * relativeY, d101xz + g101y * relativeY, d011xz + g011y * (relativeY - 1.0F), d111xz + g111y * (relativeY - 1.0F)));
                }
            }
        }

        if (DEBUG_NOISE) {
            if (!Arrays.equals(vanillaNoise, buffer.values)) {
                throw new IllegalStateException();
            }
        }
    }
}
