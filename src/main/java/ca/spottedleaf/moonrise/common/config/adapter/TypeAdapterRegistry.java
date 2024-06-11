package ca.spottedleaf.moonrise.common.config.adapter;

import ca.spottedleaf.moonrise.common.config.adapter.collection.CollectionTypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.collection.ListTypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.collection.SortedMapTypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.collection.UnsortedMapTypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.primitive.BooleanTypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.primitive.ByteTypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.primitive.DoubleTypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.primitive.FloatTypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.primitive.IntegerTypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.primitive.LongTypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.primitive.ShortTypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.primitive.StringTypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.type.BigDecimalTypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.type.BigIntegerTypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.type.DurationTypeAdapter;
import ca.spottedleaf.moonrise.common.config.annotation.Adaptable;
import ca.spottedleaf.moonrise.common.config.annotation.Serializable;
import ca.spottedleaf.moonrise.common.config.type.Duration;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class TypeAdapterRegistry {

    private final Map<Class<?>, TypeAdapter<?, ?>> adapters = new HashMap<>();
    {
        this.adapters.put(boolean.class, BooleanTypeAdapter.INSTANCE);
        this.adapters.put(byte.class, ByteTypeAdapter.INSTANCE);
        this.adapters.put(short.class, ShortTypeAdapter.INSTANCE);
        this.adapters.put(int.class, IntegerTypeAdapter.INSTANCE);
        this.adapters.put(long.class, LongTypeAdapter.INSTANCE);
        this.adapters.put(float.class, FloatTypeAdapter.INSTANCE);
        this.adapters.put(double.class, DoubleTypeAdapter.INSTANCE);

        this.adapters.put(Boolean.class, BooleanTypeAdapter.INSTANCE);
        this.adapters.put(Byte.class, ByteTypeAdapter.INSTANCE);
        this.adapters.put(Short.class, ShortTypeAdapter.INSTANCE);
        this.adapters.put(Integer.class, IntegerTypeAdapter.INSTANCE);
        this.adapters.put(Long.class, LongTypeAdapter.INSTANCE);
        this.adapters.put(Float.class, FloatTypeAdapter.INSTANCE);
        this.adapters.put(Double.class, DoubleTypeAdapter.INSTANCE);

        this.adapters.put(String.class, StringTypeAdapter.INSTANCE);

        this.adapters.put(Collection.class, CollectionTypeAdapter.INSTANCE);
        this.adapters.put(List.class, ListTypeAdapter.INSTANCE);
        this.adapters.put(Map.class, SortedMapTypeAdapter.SORTED_CASE_INSENSITIVE);
        this.adapters.put(LinkedHashMap.class, SortedMapTypeAdapter.SORTED_CASE_INSENSITIVE);

        this.adapters.put(BigInteger.class, BigIntegerTypeAdapter.INSTANCE);
        this.adapters.put(BigDecimal.class, BigDecimalTypeAdapter.INSTANCE);
        this.adapters.put(Duration.class, DurationTypeAdapter.INSTANCE);
    }

    public TypeAdapter<?, ?> putAdapter(final Class<?> clazz, final TypeAdapter<?, ?> adapter) {
        return this.adapters.put(clazz, adapter);
    }

    public TypeAdapter<?, ?> getAdapter(final Class<?> clazz) {
        return this.adapters.get(clazz);
    }

    public Object deserialize(final Object input, final Type type) {
        TypeAdapter<?, ?> adapter = null;
        if (type instanceof Class<?> clazz) {
            adapter = this.adapters.get(clazz);
        }
        if (adapter == null && (type instanceof ParameterizedType parameterizedType)) {
            adapter = this.adapters.get((Class<?>)parameterizedType.getRawType());
        }

        if (adapter == null) {
            throw new IllegalArgumentException("No adapter for " + input.getClass() + " with type " + type);
        }

        return ((TypeAdapter)adapter).deserialize(this, input, type);
    }

    public Object serialize(final Object input, final Type type) {
        TypeAdapter<?, ?> adapter = null;
        if (type instanceof Class<?> clazz) {
            adapter = this.adapters.get(clazz);
        }
        if (adapter == null && (type instanceof ParameterizedType parameterizedType)) {
            adapter = this.adapters.get((Class<?>)parameterizedType.getRawType());
        }
        if (adapter == null) {
            adapter = this.adapters.get(input.getClass());
        }

        if (adapter == null) {
            throw new IllegalArgumentException("No adapter for " + input.getClass() + " with type " + type);
        }

        return ((TypeAdapter)adapter).serialize(this, input, type);
    }

    public <T> TypeAdapter<T, Map<Object, Object>> makeAdapter(final Class<? extends T> clazz) throws Exception {
        final TypeAdapter<T, Map<Object, Object>> ret = new AutoTypeAdapter<>(this, clazz);

        this.putAdapter(clazz, ret);

        return ret;
    }

    private static final class AutoTypeAdapter<T> extends TypeAdapter<T, Map<Object, Object>> {

        private final TypeAdapterRegistry registry;
        private final Constructor<? extends T> constructor;
        private final SerializableField[] fields;

        public AutoTypeAdapter(final TypeAdapterRegistry registry, final Class<? extends T> clazz) throws Exception {
            this.registry = registry;
            this.constructor = clazz.getConstructor();
            this.fields = findSerializableFields(registry, clazz);
        }

        private static TypeAdapter<?, ?> findOrMakeAdapter(final TypeAdapterRegistry registry, final Class<?> clazz) throws Exception {
            final TypeAdapter<?, ?> ret = registry.getAdapter(clazz);
            if (ret != null) {
                return ret;
            }

            for (final Annotation annotation : clazz.getAnnotations()) {
                if (annotation instanceof Adaptable adaptable) {
                    return registry.makeAdapter(clazz);
                }
            }

            throw new IllegalArgumentException("No type adapter for " + clazz + " (Forgot @Adaptable?)");
        }

        private static String makeSerializedKey(final String input) {
            final StringBuilder ret = new StringBuilder();

            for (final char c : input.toCharArray()) {
                if (!Character.isUpperCase(c)) {
                    ret.append(c);
                    continue;
                }
                ret.append('-');
                ret.append(Character.toLowerCase(c));
            }

            return ret.toString();
        }

        private static record SerializableField(
                Field field,
                boolean required,
                String comment,
                TypeAdapter<?, ?> adapter,
                boolean serialize,
                String serializedKey
        ) {}

        private static SerializableField[] findSerializableFields(final TypeAdapterRegistry registry, Class<?> clazz) throws Exception {
            final List<SerializableField> ret = new ArrayList<>();
            do {
                for (final Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);

                    for (final Annotation annotation : field.getAnnotations()) {
                        if (!(annotation instanceof Serializable serializable)) {
                            continue;
                        }

                        final TypeAdapter<?, ?> adapter;

                        if (serializable.adapter() != TypeAdapter.class) {
                            adapter = serializable.adapter().getConstructor().newInstance();
                        } else {
                            adapter = findOrMakeAdapter(registry, field.getType());
                        }

                        ret.add(new SerializableField(
                                field, serializable.required(), serializable.comment(), adapter,
                                serializable.serialize(), makeSerializedKey(field.getName())
                        ));
                    }
                }
            } while ((clazz = clazz.getSuperclass()) != Object.class);

            ret.sort((final SerializableField c1, final SerializableField c2) -> {
                return c1.serializedKey.compareTo(c2.serializedKey);
            });

            return ret.toArray(new SerializableField[0]);
        }

        @Override
        public T deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
            if (!(input instanceof Map<?,?> inputMap)) {
                throw new IllegalArgumentException("Not a map type: " + input.getClass());
            }

            try {
                final T ret = this.constructor.newInstance();

                for (final SerializableField field : this.fields) {
                    final Object fieldValue = inputMap.get(field.serializedKey);

                    if (fieldValue == null) {
                        continue;
                    }

                    field.field.set(ret, field.adapter.deserialize(registry, fieldValue, field.field.getGenericType()));
                }

                return ret;
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public Map<Object, Object> serialize(final TypeAdapterRegistry registry, final T value, final Type type) {
            final LinkedHashMap<Object, Object> ret = new LinkedHashMap<>();

            for (final SerializableField field : this.fields) {
                if (!field.serialize) {
                    continue;
                }

                final Object fieldValue;
                try {
                    fieldValue = field.field.get(value);
                } catch (final Exception ex) {
                    throw new RuntimeException(ex);
                }

                if (fieldValue != null) {
                    ret.put(
                            field.comment.isBlank() ? field.serializedKey : new CommentedData(field.comment, field.serializedKey),
                            ((TypeAdapter)field.adapter).serialize(
                                    registry, fieldValue, field.field.getGenericType()
                            )
                    );
                }
            }

            return ret;
        }
    }

    public static final class CommentedData {

        public final String comment;
        public final Object data;

        public CommentedData(final String comment, final Object data) {
            this.comment = comment;
            this.data = data;
        }
    }
}
