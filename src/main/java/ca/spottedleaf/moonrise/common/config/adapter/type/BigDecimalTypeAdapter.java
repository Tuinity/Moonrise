package ca.spottedleaf.moonrise.common.config.adapter.type;

import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapterRegistry;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;

public final class BigDecimalTypeAdapter extends TypeAdapter<BigDecimal, String> {

    public static final BigDecimalTypeAdapter INSTANCE = new BigDecimalTypeAdapter();

    @Override
    public BigDecimal deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof Number number) {
            // safest to catch all number impls is to use toString
            return new BigDecimal(number.toString());
        }
        if (input instanceof String string) {
            return new BigDecimal(string);
        }

        throw new IllegalArgumentException("Not an BigDecimal type: " + input.getClass());
    }

    @Override
    public String serialize(final TypeAdapterRegistry registry, final BigDecimal value, final Type type) {
        return value.toString();
    }
}
