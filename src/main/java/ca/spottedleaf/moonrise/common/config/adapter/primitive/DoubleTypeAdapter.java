package ca.spottedleaf.moonrise.common.config.adapter.primitive;

import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapterRegistry;
import java.lang.reflect.Type;

public final class DoubleTypeAdapter extends TypeAdapter<Double, Double> {

    public static final DoubleTypeAdapter INSTANCE = new DoubleTypeAdapter();

    @Override
    public Double deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof Number number) {
            return Double.valueOf(number.doubleValue());
        }
        if (input instanceof String string) {
            return Double.valueOf(Double.parseDouble(string));
        }

        throw new IllegalArgumentException("Not a byte type: " + input.getClass());
    }

    @Override
    public Double serialize(final TypeAdapterRegistry registry, final Double value, final Type type) {
        return value;
    }
}
