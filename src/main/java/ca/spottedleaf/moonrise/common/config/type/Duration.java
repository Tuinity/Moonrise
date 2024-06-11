package ca.spottedleaf.moonrise.common.config.type;

import java.math.BigDecimal;

public final class Duration {

    private final String string;
    private final long timeNS;

    private Duration(final String string, final long timeNS) {
        this.string = string;
        this.timeNS = timeNS;
    }

    public static Duration parse(final String value) {
        if (value.length() < 2) {
            throw new IllegalArgumentException("Invalid duration: " + value);
        }

        final char last = value.charAt(value.length() - 1);

        final long multiplier;

        switch (last) {
            case 's': {
                multiplier = (1000L * 1000L * 1000L) * 1L;
                break;
            }
            case 't': {
                multiplier = (1000L * 1000L * 1000L) / 20L;
                break;
            }
            case 'm': {
                multiplier = (1000L * 1000L * 1000L) * 60L;
                break;
            }
            case 'h': {
                multiplier = (1000L * 1000L * 1000L) * 60L * 60L;
                break;
            }
            case 'd': {
                multiplier = (1000L * 1000L * 1000L) * 24L * 60L * 60L;
                break;
            }
            default: {
                throw new IllegalArgumentException("Duration must end with one of: [s, t, m, h, d]");
            }
        }

        final BigDecimal parsed = new BigDecimal(value.substring(0, value.length() - 1))
                .multiply(new BigDecimal(multiplier));

        return new Duration(value, parsed.toBigInteger().longValueExact());
    }

    public long getTimeNS() {
        return this.timeNS;
    }

    public long getTimeMS() {
        return this.timeNS / (1000L * 1000L);
    }

    public long getTimeS() {
        return this.timeNS / (1000L * 1000L * 1000L);
    }

    public long getTimeTicks() {
        return this.timeNS / ((1000L * 1000L * 1000L) / (20L));
    }

    @Override
    public String toString() {
        return this.string;
    }
}
