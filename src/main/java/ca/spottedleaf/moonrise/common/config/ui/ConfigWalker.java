package ca.spottedleaf.moonrise.common.config.ui;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.AbstractFieldBuilder;
import net.minecraft.network.chat.Component;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class ConfigWalker {

    private ConfigWalker() {}

    public static <T> void walk(final T dfl, final T instance, final ConfigBuilder builder) throws Exception {
        for (final Field field : dfl.getClass().getDeclaredFields()) {
            if ((field.getModifiers() & Modifier.STATIC) != 0) {
                continue;
            }

            try {
                field.setAccessible(true);
            } catch (final Exception exception) {
                continue;
            }

            final Object dflField = field.get(dfl);
            final Object instanceField = field.get(instance);

            for (final Annotation annotation : field.getAnnotations()) {
                if (!(annotation instanceof ClothConfig config)) {
                    walk(dflField, instanceField, builder);
                    continue;
                }

                createFieldBuilder(builder, config, field, dfl, instance);
            }
        }
    }

    private static void createFieldBuilder(final ConfigBuilder builder,
                                           final ClothConfig config,
                                           final Field field,
                                           final Object dfl, final Object dst) {
        final ConfigEntryBuilder configEntryBuilder = builder.entryBuilder();
        final ConfigCategory category = builder.getOrCreateCategory(Component.translatable(config.section()));

        final Component name = Component.translatable(config.fieldKeyName());

        final Class<?> clazz = field.getType();
        final AbstractFieldBuilder<?, ?, ?> ret;
        try {
            if (clazz == boolean.class || clazz == Boolean.class) {
                ret = configEntryBuilder.startBooleanToggle(name, (boolean)field.get(dst));
            } else if (clazz == double.class || clazz == Double.class) {
                ret = configEntryBuilder.startDoubleField(name, (double)field.get(dst));
            } else if (clazz == long.class || clazz == Long.class) {
                ret = configEntryBuilder.startLongField(name, (long)field.get(dst));
            } else if (clazz == int.class || clazz == Integer.class) {
                ret = configEntryBuilder.startIntField(name, (int)field.get(dst));
            } else if (clazz == String.class) {
                ret = configEntryBuilder.startStrField(name, (String)field.get(dst));
            } else {
                throw new IllegalArgumentException("Unknown type: " + clazz);
            }
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }

        updateFields(ret, config, field, dfl, dst);

        category.addEntry(ret.build());
    }

    private static <E> void updateFields(final AbstractFieldBuilder<E, ?, ?> builder,
                                         final ClothConfig config,
                                         final Field field,
                                         final Object dfl, final Object dst) {
        try {
            builder.setDefaultValue((E)field.get(dfl));
            builder.setTooltip(Component.translatable(config.tooltip()));
            builder.setSaveConsumer((final E element) -> {
                try {
                    field.set(dst, element);
                } catch (final Exception ex) {
                    throw new RuntimeException();
                }
            });
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
