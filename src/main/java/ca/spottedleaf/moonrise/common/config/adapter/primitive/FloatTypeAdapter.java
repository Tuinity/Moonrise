package ca.spottedleaf.moonrise.common.config.adapter.primitive;

import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapterRegistry;
import java.lang.reflect.Type;

public final class FloatTypeAdapter extends TypeAdapter<Float, Float> {

    public static final FloatTypeAdapter INSTANCE = new FloatTypeAdapter();

    private static Float cast(final Object original, final double value) {
        if (value < -(double)Float.MAX_VALUE || value > (double)Float.MAX_VALUE) {
            throw new IllegalArgumentException("Byte value is out of range: " + original.toString());
        }
        // note: silently ignore precision loss
        return Float.valueOf((float)value);
    }

    @Override
    public Float deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof Number number) {
            return cast(input, number.doubleValue());
        }
        if (input instanceof String string) {
            return cast(input, Double.parseDouble(string));
        }

        throw new IllegalArgumentException("Not a byte type: " + input.getClass());
    }

    @Override
    public Float serialize(final TypeAdapterRegistry registry, final Float value, final Type type) {
        return value;
    }
}
