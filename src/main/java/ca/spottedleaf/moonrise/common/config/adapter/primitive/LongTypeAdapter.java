package ca.spottedleaf.moonrise.common.config.adapter.primitive;

import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapterRegistry;
import java.lang.reflect.Type;
import java.math.BigInteger;

public final class LongTypeAdapter extends TypeAdapter<Long, Long> {

    public static final LongTypeAdapter INSTANCE = new LongTypeAdapter();

    @Override
    public Long deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof Number number) {
            // note: silently discard floating point significand
            return number instanceof BigInteger bigInteger ? bigInteger.longValueExact() : number.longValue();
        }
        if (input instanceof String string) {
            try {
                return Long.valueOf(Long.parseLong(string));
            } catch (final NumberFormatException ex) {
                return Long.valueOf((long)Double.parseDouble(string));
            }
        }

        throw new IllegalArgumentException("Not a long type: " + input.getClass());
    }

    @Override
    public Long serialize(final TypeAdapterRegistry registry, final Long value, final Type type) {
        return value;
    }
}
