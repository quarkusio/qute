package com.github.mkouba.qute.quarkus;

import com.github.mkouba.qute.Template;

/**
 * 
 * @see Variant
 */
public interface VariantTemplate extends Template {

    /**
     * Attribute key - all template {@link Variant}s found.
     */
    String VARIANTS = "variants";

    /**
     * Attribute key - a selected {@link Variant}.
     */
    String SELECTED_VARIANT = "selectedVariant";

}
