package ca.spottedleaf.moonrise.common.config.adapter.type;

import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapterRegistry;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;

public final class BigIntegerTypeAdapter extends TypeAdapter<BigInteger, String> {

    public static final BigIntegerTypeAdapter INSTANCE = new BigIntegerTypeAdapter();

    @Override
    public BigInteger deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof Number number) {
            if (number instanceof BigInteger bigInteger) {
                return bigInteger;
            }
            // note: silently discard floating point significand
            if (number instanceof BigDecimal bigDecimal) {
                return bigDecimal.toBigInteger();
            }

            return BigInteger.valueOf(number.longValue());
        }
        if (input instanceof String string) {
            return new BigDecimal(string).toBigInteger();
        }

        throw new IllegalArgumentException("Not an BigInteger type: " + input.getClass());
    }

    @Override
    public String serialize(final TypeAdapterRegistry registry, final BigInteger value, final Type type) {
        return value.toString();
    }
}
