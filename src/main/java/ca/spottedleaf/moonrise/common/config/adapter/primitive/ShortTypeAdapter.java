package ca.spottedleaf.moonrise.common.config.adapter.primitive;

import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapterRegistry;
import java.lang.reflect.Type;
import java.math.BigInteger;

public final class ShortTypeAdapter extends TypeAdapter<Short, Short> {

    public static final ShortTypeAdapter INSTANCE = new ShortTypeAdapter();

    private static Short cast(final Object original, final long value) {
        if (value < (long)Short.MIN_VALUE || value > (long)Short.MAX_VALUE) {
            throw new IllegalArgumentException("Short value is out of range: " + original.toString());
        }
        return Short.valueOf((short)value);
    }

    @Override
    public Short deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof Number number) {
            // note: silently discard floating point significand
            return cast(input, number instanceof BigInteger bigInteger ? bigInteger.longValueExact() : number.longValue());
        }
        if (input instanceof String string) {
            return cast(input, (long)Double.parseDouble(string));
        }

        throw new IllegalArgumentException("Not a short type: " + input.getClass());
    }

    @Override
    public Short serialize(final TypeAdapterRegistry registry, final Short value, final Type type) {
        return value;
    }
}
