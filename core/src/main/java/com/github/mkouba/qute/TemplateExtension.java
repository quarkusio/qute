package com.github.mkouba.qute;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * A value resolver is automatically generated for template extension methods.
 * 
 * The method must be static, must not return {@code void} and must accept at least one parameter. The class of the first
 * parameter is used to match the base object. The method name is used to match the property name.
 * 
 * <pre>
 * {@literal @}TemplateExtension
 * static BigDecimal discountedPrice(Item item) {
 *    return item.getPrice().multiply(new BigDecimal("0.9"));
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface TemplateExtension {
    
    static final String ANY = "*";
    
    String matchName() default "";

}
