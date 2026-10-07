package ca.spottedleaf.moonrise.patches.chunk_gen.noise;

import net.minecraft.world.level.levelgen.synth.GradientNoise;

public final class NoiseGradients {

    public static final boolean DEBUG_NOISE = false;

    public static final int PERM_SHIFT = 8;
    public static final int PERM_SIZE = 1 << PERM_SHIFT;
    public static final int PERM_MASK = PERM_SIZE - 1;

    public static class CoordCache {

        public int floor;
        public float alpha;
        public float relative;

        public CoordCache() {}
    }

    public static final ThreadLocal<CoordCache[]> COORD_CACHE_X = new ThreadLocal<>();
    public static final ThreadLocal<CoordCache[]> COORD_CACHE_Y = new ThreadLocal<>();
    public static final ThreadLocal<CoordCache[]> COORD_CACHE_Z = new ThreadLocal<>();

    public static CoordCache[] getCoordCache(final ThreadLocal<CoordCache[]> cache, final int size) {
        final CoordCache[] ret = cache.get();
        if (ret != null && ret.length >= size) {
            return ret;
        }
        final CoordCache[] allocated = new CoordCache[size];
        for (int i = 0; i < size; ++i) {
            allocated[i] = new CoordCache();
        }
        cache.set(allocated);
        return allocated;
    }

    public static final GradientF[] GRADIENTF = new GradientF[GradientNoise.GRADIENT.length];
    public static final float[] GRADIENTF_FLAT = new float[GRADIENTF.length * 4];
    static {
        for (int i = 0 ; i < GRADIENTF.length; ++i) {
            GradientNoise.Gradient grad = GradientNoise.GRADIENT[i];
            GRADIENTF[i] = new GradientF((float)grad.x(), (float)grad.y(), (float)grad.z());
        }
        for (int i = 0 ; i < GRADIENTF.length; ++i) {
            final GradientF gradient = GRADIENTF[i];
            GRADIENTF_FLAT[(i << 2) + 0] = gradient.x;
            GRADIENTF_FLAT[(i << 2) + 1] = gradient.y;
            GRADIENTF_FLAT[(i << 2) + 2] = gradient.z;
        }
    }

    public static float dot(final int hash, final float x, final float y, final float z) {
        final float gz = GRADIENTF_FLAT[(hash << 2) + 2];
        final float gy = GRADIENTF_FLAT[(hash << 2) + 1];
        final float gx = GRADIENTF_FLAT[(hash << 2) + 0];
        if (false) {
            return Math.fma(gz, z, Math.fma(gy, y, gx * x));
        } else {
            return gx * x + gy * y + gz * z;
        }
    }

    public static float dotXz(final int hash, final float x, final float z) {
        final float gz = GRADIENTF_FLAT[(hash << 2) + 2];
        final float gx = GRADIENTF_FLAT[(hash << 2) + 0];
        if (false) {
            return Math.fma(gz, z, gx * x);
        } else {
            return gx * x + gz * z;
        }
    }

    public static float gradY(final int hash) {
        return GRADIENTF_FLAT[(hash << 2) + 1];
    }

    public record GradientF(float x, float y, float z) {
        public float dot(final float x, final float y, final float z) {
            if (false) {
                return Math.fma(this.z, z, Math.fma(this.y, y, this.x * x));
            } else {
                return this.x * x + this.y * y + this.z * z;
            }
        }
        public float dotXz(final float x, final float z) {
            if (false) {
                return Math.fma(this.z, z, this.x * x);
            } else {
                return this.x * x + this.z * z;
            }
        }
    }

    public static GradientF gradF(final int index) {
        return GRADIENTF[index];
    }

    public static float gradDotf(final int index, final float x, final float y, final float z) {
        return GRADIENTF[index].dot(x, y, z);
    }

    public static final GradientD[] GRADIENTD = new GradientD[GradientNoise.GRADIENT.length];
    static {
        for (int i = 0 ; i < GRADIENTF.length; ++i) {
            GradientNoise.Gradient grad = GradientNoise.GRADIENT[i];
            GRADIENTD[i] = new GradientD((double)grad.x(), (double)grad.y(), (double)grad.z());
        }
    }

    public record GradientD(double x, double y, double z) {
        public double dot(final double x, final double y, final double z) {
            if (false) {
                return Math.fma(this.z, z, Math.fma(this.y, y, this.x * x));
            } else {
                return this.x * x + this.y * y + this.z * z;
            }
        }
    }

    public static GradientD gradD(final int index) {
        return GRADIENTD[index];
    }

    public static double gradDotD(final int index, final double x, final double y, final double z) {
        return GRADIENTD[index].dot(x, y, z);
    }

    private NoiseGradients() {}
}
