package ca.spottedleaf.moonrise.common.config.annotation;

import ca.spottedleaf.moonrise.common.config.adapter.TypeAdapter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Function;

/**
 * Annotation indicating that a field should be deserialized or serialized from the config.
 * By default, this annotation is not assumed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Serializable {

    /**
     * Indicates whether this field is required to be present in the config. If the field is not present,
     * and {@code required = true}, then an exception will be thrown during deserialization. If {@code required = false}
     * and the field is not present, then the field value will remain unmodified.
     */
    public boolean required() default false;

    /**
     * The comment to apply before the element when serializing.
     */
    public String comment() default "";

    /**
     * Adapter override class. The class must have a public no-args constructor.
     */
    public Class<? extends TypeAdapter> adapter() default TypeAdapter.class;

    /**
     * Whether to serialize the value to the config.
     */
    public boolean serialize() default true;

    /**
     * When not empty, this value overrides the auto generated serialized key in the config.
     */
    public String serializedKey() default "";

}
