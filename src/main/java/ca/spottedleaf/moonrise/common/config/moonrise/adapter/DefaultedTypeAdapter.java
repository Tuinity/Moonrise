package ca.spottedleaf.moonrise.common.config.moonrise.adapter;

import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapterRegistry;
import ca.spottedleaf.moonrise.common.config.moonrise.type.DefaultedValue;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public final class DefaultedTypeAdapter extends TypeAdapter<DefaultedValue<?>, Object> {

    private static final String DEFAULT_STRING = "default";

    @Override
    public DefaultedValue<?> deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof String string && string.equalsIgnoreCase(DEFAULT_STRING)) {
            return new DefaultedValue<>();
        }

        if (!(type instanceof ParameterizedType parameterizedType)) {
            throw new IllegalArgumentException("DefaultedValue field must specify generic type");
        }
        final Type valueType = parameterizedType.getActualTypeArguments()[0];

        return new DefaultedValue<>(registry.deserialize(input, valueType));
    }

    @Override
    public Object serialize(final TypeAdapterRegistry registry, final DefaultedValue<?> value, final Type type) {
        final Object raw = value.getValueRaw();
        if (raw == null) {
            return DEFAULT_STRING;
        }

        final Type valueType = type instanceof ParameterizedType parameterizedType ? parameterizedType.getActualTypeArguments()[0] : null;

        return registry.serialize(raw, valueType);
    }
}
