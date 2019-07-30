package com.github.mkouba.qute.quarkus.resteasy;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.enterprise.util.Nonbinding;
import javax.inject.Qualifier;

import com.github.mkouba.qute.quarkus.runtime.QuteConfig;

/**
 * Qualifies an injected template variant. The {@link #value()} is used as the template base name.
 */
@Qualifier
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER, METHOD })
public @interface Variant {

    /**
     * The value represents a path
     * relative from the base path. If no value is provided the field name of an injected field may be used instead.
     * 
     * @return the variant base path
     * @see QuteConfig#basePath
     */
    @Nonbinding
    String value() default "";
}
