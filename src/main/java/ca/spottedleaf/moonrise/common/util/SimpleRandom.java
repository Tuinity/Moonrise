package ca.spottedleaf.moonrise.common.util;

import net.minecraft.world.level.levelgen.LegacyRandomSource;

/**
 * Avoid costly CAS of superclass
 */
public final class SimpleRandom extends LegacyRandomSource {

    private static final long MULTIPLIER = 25214903917L;
    private static final long ADDEND = 11L;
    private static final int BITS = 48;
    private static final long MASK = (1L << BITS) - 1;

    private long value;

    public SimpleRandom(final long seed) {
        super(0L);
        this.value = seed;
    }

    @Override
    public void setSeed(final long seed) {
        this.value = (seed ^ MULTIPLIER) & MASK;
    }

    private long advanceSeed() {
        return this.value = ((this.value * MULTIPLIER) + ADDEND) & MASK;
    }

    @Override
    public int next(final int bits) {
        return (int)(this.advanceSeed() >>> (BITS - bits));
    }

    @Override
    public int nextInt() {
        final long seed = this.advanceSeed();
        return (int)(seed >>> (BITS - Integer.SIZE));
    }

    @Override
    public int nextInt(final int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException();
        }

        // https://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
        final long value = this.advanceSeed() >>> (BITS - Integer.SIZE);
        return (int)((value * (long)bound) >>> Integer.SIZE);
    }
}
