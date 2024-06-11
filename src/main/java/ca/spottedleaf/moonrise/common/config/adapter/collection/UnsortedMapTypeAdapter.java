package ca.spottedleaf.moonrise.common.config.adapter.collection;

import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapterRegistry;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UnsortedMapTypeAdapter extends TypeAdapter<Map<String, Object>, Map<String, Object>> {

    public static final UnsortedMapTypeAdapter INSTANCE = new UnsortedMapTypeAdapter();

    @Override
    public Map<String, Object> deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (!(type instanceof ParameterizedType parameterizedType)) {
            throw new IllegalArgumentException("Collection field must specify generic type");
        }
        final Type valueType = parameterizedType.getActualTypeArguments()[1];
        if (input instanceof Map<?,?> inputMap) {
            final Map<String, Object> castedInput = (Map<String, Object>)inputMap;

            final LinkedHashMap<String, Object> ret = new LinkedHashMap<>();

            for (final Map.Entry<String, Object> entry : castedInput.entrySet()) {
                ret.put(entry.getKey(), registry.deserialize(entry.getValue(), valueType));
            }

            return ret;
        }

        throw new IllegalArgumentException("Not a map type: " + input.getClass());
    }

    @Override
    public Map<String, Object> serialize(final TypeAdapterRegistry registry, final Map<String, Object> value, final Type type) {
        final LinkedHashMap<String, Object> ret = new LinkedHashMap<>();

        final Type valueType = type instanceof ParameterizedType parameterizedType ? parameterizedType.getActualTypeArguments()[1] : null;

        for (final Map.Entry<String, Object> entry : value.entrySet()) {
            ret.put(entry.getKey(), registry.serialize(entry.getValue(), valueType));
        }

        return ret;
    }
}
