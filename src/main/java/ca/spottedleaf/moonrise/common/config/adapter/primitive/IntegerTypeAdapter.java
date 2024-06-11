package ca.spottedleaf.moonrise.common.config.adapter.primitive;

import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapterRegistry;
import java.lang.reflect.Type;
import java.math.BigInteger;

public final class IntegerTypeAdapter extends TypeAdapter<Integer, Integer> {

    public static final IntegerTypeAdapter INSTANCE = new IntegerTypeAdapter();

    private static Integer cast(final Object original, final long value) {
        if (value < (long)Integer.MIN_VALUE || value > (long)Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Integer value is out of range: " + original.toString());
        }
        return Integer.valueOf((int)value);
    }

    @Override
    public Integer deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof Number number) {
            // note: silently discard floating point significand
            return cast(input, number instanceof BigInteger bigInteger ? bigInteger.longValueExact() : number.longValue());
        }
        if (input instanceof String string) {
            return cast(input, (long)Double.parseDouble(string));
        }

        throw new IllegalArgumentException("Not an integer type: " + input.getClass());
    }

    @Override
    public Integer serialize(final TypeAdapterRegistry registry, final Integer value, final Type type) {
        return value;
    }
}
