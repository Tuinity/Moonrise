package ca.spottedleaf.moonrise.common.config.adapter.primitive;

import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapterRegistry;
import java.lang.reflect.Type;
import java.math.BigInteger;

public final class ByteTypeAdapter extends TypeAdapter<Byte, Byte> {

    public static final ByteTypeAdapter INSTANCE = new ByteTypeAdapter();

    private static Byte cast(final Object original, final long value) {
        if (value < (long)Byte.MIN_VALUE || value > (long)Byte.MAX_VALUE) {
            throw new IllegalArgumentException("Byte value is out of range: " + original.toString());
        }
        return Byte.valueOf((byte)value);
    }

    @Override
    public Byte deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof Number number) {
            // note: silently discard floating point significand
            return cast(input, number instanceof BigInteger bigInteger ? bigInteger.longValueExact() : number.longValue());
        }
        if (input instanceof String string) {
            return cast(input, (long)Double.parseDouble(string));
        }

        throw new IllegalArgumentException("Not a byte type: " + input.getClass());
    }

    @Override
    public Byte serialize(final TypeAdapterRegistry registry, final Byte value, final Type type) {
        return value;
    }
}
