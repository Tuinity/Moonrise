package ca.spottedleaf.moonrise.common.config.adapter.primitive;

import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapterRegistry;
import java.lang.reflect.Type;

public final class BooleanTypeAdapter extends TypeAdapter<Boolean, Boolean> {

    public static final BooleanTypeAdapter INSTANCE = new BooleanTypeAdapter();

    @Override
    public Boolean deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof Boolean ret) {
            return ret;
        }
        if (input instanceof String str) {
            if (str.equalsIgnoreCase("false")) {
                return Boolean.FALSE;
            }
            if (str.equalsIgnoreCase("true")) {
                return Boolean.TRUE;
            }
            throw new IllegalArgumentException("Not a boolean: " + str);
        }

        throw new IllegalArgumentException("Not a boolean type: " + input.getClass());
    }

    @Override
    public Boolean serialize(final TypeAdapterRegistry registry, final Boolean value, final Type type) {
        return value;
    }
}
