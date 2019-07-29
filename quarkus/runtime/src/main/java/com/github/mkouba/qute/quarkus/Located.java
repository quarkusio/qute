package com.github.mkouba.qute.quarkus;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.enterprise.util.Nonbinding;
import javax.inject.Qualifier;

/**
 * Qualifies an injected template. The {@link #value()} is used to locate the template; it represents the path relative from
 * {@code META-INF/resources/}. If no value is provided the field name of an injected field may be used to locate the template.
 */
@Qualifier
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER, METHOD })
public @interface Located {

    /**
     * 
     * @return the path relative from {@code META-INF/resources/} or an empty string
     */
    @Nonbinding
    String value() default "";
}
