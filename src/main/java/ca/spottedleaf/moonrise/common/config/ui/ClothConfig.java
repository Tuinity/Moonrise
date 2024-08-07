package ca.spottedleaf.moonrise.common.config.ui;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ClothConfig {

    public String tooltip();

    public String fieldKeyName();

    public String section();

}
