package ca.spottedleaf.moonrise.common.config.adapter.type;

import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapter;
import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapterRegistry;
import ca.spottedleaf.moonrise.common.config.type.Duration;
import java.lang.reflect.Type;

public final class DurationTypeAdapter extends TypeAdapter<Duration, String> {

    public static final DurationTypeAdapter INSTANCE = new DurationTypeAdapter();

    @Override
    public Duration deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (!(input instanceof String string)) {
            throw new IllegalArgumentException("Not a string: " + input.getClass());
        }
        return Duration.parse(string);
    }

    @Override
    public String serialize(final TypeAdapterRegistry registry, final Duration value, final Type type) {
        return value.toString();
    }
}
