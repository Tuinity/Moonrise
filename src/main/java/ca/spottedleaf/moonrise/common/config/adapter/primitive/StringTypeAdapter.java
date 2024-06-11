package ca.spottedleaf.moonrise.common.config.adapter.primitive;

import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapterRegistry;
import java.lang.reflect.Type;

public final class StringTypeAdapter extends TypeAdapter<String, String> {

    public static final StringTypeAdapter INSTANCE = new StringTypeAdapter();

    @Override
    public String deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof Boolean bool) {
            return String.valueOf(bool.booleanValue());
        }
        if (input instanceof Number number) {
            return number.toString();
        }
        if (input instanceof String string) {
            return string;
        }
        throw new IllegalArgumentException("Not a string type: " + input.getClass());
    }

    @Override
    public String serialize(final TypeAdapterRegistry registry, final String value, final Type type) {
        return value;
    }
}
