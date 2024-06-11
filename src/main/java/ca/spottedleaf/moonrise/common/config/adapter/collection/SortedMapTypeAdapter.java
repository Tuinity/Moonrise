package ca.spottedleaf.moonrise.common.config.adapter.collection;

import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapterRegistry;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class SortedMapTypeAdapter extends TypeAdapter<Map<String, Object>, Map<String, Object>> {

    public static final SortedMapTypeAdapter SORTED_CASE_INSENSITIVE = new SortedMapTypeAdapter(String.CASE_INSENSITIVE_ORDER);
    public static final SortedMapTypeAdapter SORTED_CASE_SENSITIVE = new SortedMapTypeAdapter(null);

    private final Comparator<String> keyComparator;

    public SortedMapTypeAdapter(final Comparator<String> keyComparator) {
        this.keyComparator = keyComparator;
    }

    @Override
    public Map<String, Object> deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (!(type instanceof ParameterizedType parameterizedType)) {
            throw new IllegalArgumentException("Collection field must specify generic type");
        }
        final Type valueType = parameterizedType.getActualTypeArguments()[1];
        if (input instanceof Map<?,?> inputMap) {
            final Map<String, Object> castedInput = (Map<String, Object>)inputMap;

            final TreeMap<String, Object> ret = new TreeMap<>(this.keyComparator);

            for (final Map.Entry<String, Object> entry : castedInput.entrySet()) {
                ret.put(entry.getKey(), registry.deserialize(entry.getValue(), valueType));
            }

            // transform to linked so that get() is O(1)
            return new LinkedHashMap<>(ret);
        }

        throw new IllegalArgumentException("Not a map type: " + input.getClass());
    }

    @Override
    public Map<String, Object> serialize(final TypeAdapterRegistry registry, final Map<String, Object> value, final Type type) {
        final TreeMap<String, Object> ret = new TreeMap<>(this.keyComparator);

        final Type valueType = type instanceof ParameterizedType parameterizedType ? parameterizedType.getActualTypeArguments()[1] : null;

        for (final Map.Entry<String, Object> entry : value.entrySet()) {
            ret.put(entry.getKey(), registry.serialize(entry.getValue(), valueType));
        }

        // transform to linked so that get() is O(1)
        return new LinkedHashMap<>(ret);
    }

}
