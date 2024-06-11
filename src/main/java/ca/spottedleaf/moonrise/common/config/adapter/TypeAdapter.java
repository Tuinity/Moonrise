package ca.spottedleaf.moonrise.common.config.adapter;

import java.lang.reflect.Type;

public abstract class TypeAdapter<T, S> {

    public abstract T deserialize(final TypeAdapterRegistry registry, final Object input, final Type type);

    public abstract S serialize(final TypeAdapterRegistry registry, final T value, final Type type);

}
