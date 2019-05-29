package com.github.mkouba.qute;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Builder for {@link Engine}.
 */
public final class EngineBuilder {

    private final Map<String, SectionHelperFactory<?>> sectionHelperFactories;
    private final List<ValueResolver> valueResolvers;
    private final List<NamespaceResolver> namespaceResolvers;
    private final List<Function<String, Optional<Reader>>> locators;

    EngineBuilder() {
        this.sectionHelperFactories = new HashMap<>();
        this.valueResolvers = new ArrayList<>();
        this.namespaceResolvers = new ArrayList<>();
        this.locators = new ArrayList<>();
    }

    public EngineBuilder addSectionHelper(SectionHelperFactory<?> factory) {
        for (String alias : factory.getDefaultAliases()) {
            sectionHelperFactories.put(alias, factory);
        }
        return this;
    }

    public EngineBuilder addSectionHelpers(SectionHelperFactory<?>... factories) {
        for (SectionHelperFactory<?> factory : factories) {
            addSectionHelper(factory);
        }
        return this;
    }

    public EngineBuilder addSectionHelper(String name, SectionHelperFactory<?> factory) {
        addSectionHelper(factory);
        sectionHelperFactories.put(name, factory);
        return this;
    }

    public EngineBuilder addValueResolver(Supplier<ValueResolver> resolverSupplier) {
        return addValueResolver(resolverSupplier.get());
    }

    public EngineBuilder addValueResolvers(ValueResolver... resolvers) {
        for (ValueResolver valueResolver : resolvers) {
            addValueResolver(valueResolver);
        }
        return this;
    }

    public EngineBuilder addValueResolver(ValueResolver resolver) {
        this.valueResolvers.add(resolver);
        return this;
    }

    public EngineBuilder addNamespaceResolver(NamespaceResolver resolver) {
        this.namespaceResolvers.add(resolver);
        return this;
    }

    /**
     * 
     * @param locator
     * @return self
     * @see Engine#getTemplate(String)
     */
    public EngineBuilder addLocator(Function<String, Optional<Reader>> locator) {
        this.locators.add(locator);
        return this;
    }

    public Engine build() {
        return new EngineImpl(sectionHelperFactories, valueResolvers, namespaceResolvers, locators);
    }

}