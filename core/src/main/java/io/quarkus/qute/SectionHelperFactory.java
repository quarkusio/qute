package io.quarkus.qute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Factory to create a new {@link SectionHelper} based on the {@link SectionInitContextImpl}.
 */
public interface SectionHelperFactory<T extends SectionHelper> {

    /**
     * 
     * @return the list of default aliases
     */
    default List<String> getDefaultAliases() {
        return Collections.emptyList();
    }

    /**
     * 
     * @return the info about the expected parameters
     */
    default ParametersInfo getParameters() {
        return ParametersInfo.EMPTY;
    }

    /**
     * 
     * @param context
     * @return a new helper instance
     */
    T initialize(SectionInitContext context);
    
    public interface SectionInitContext {
        
        default Map<String, String> getParameters() {
            return getBlocks().get(0).parameters;
        }

        default boolean hasParameter(String name) {
            return getParameters().containsKey(name);
        }

        default String getParameter(String name) {
            return getParameters().get(name);
        }

        List<SectionBlock> getBlocks();
        
        Engine getEngine();
        
    }
    

    public static final class ParametersInfo implements Iterable<List<Parameter>> {

        public static Builder builder() {
            return new Builder();
        }

        public static final ParametersInfo EMPTY = builder().build();

        private final Map<String, List<Parameter>> parameters;

        private ParametersInfo(Map<String, List<Parameter>> parameters) {
            this.parameters = new HashMap<>(parameters);
        }

        public List<Parameter> get(String sectionPart) {
            return parameters.getOrDefault(sectionPart, Collections.emptyList());
        }

        @Override
        public Iterator<List<Parameter>> iterator() {
            return parameters.values().iterator();
        }

        public static class Builder {

            private final Map<String, List<Parameter>> parameters;

            public Builder() {
                this.parameters = new HashMap<>();
            }

            public Builder addParameter(String name) {
                return addParameter(Parser.MAIN_BLOCK_NAME, name, null);
            }

            public Builder addParameter(String name, String defaultValue) {
                return addParameter(Parser.MAIN_BLOCK_NAME, name, defaultValue);
            }

            public Builder addParameter(Parameter param) {
                return addParameter(Parser.MAIN_BLOCK_NAME, param);
            }

            public Builder addParameter(String sectionPartLabel, String name, String defaultValue) {
                parameters.computeIfAbsent(sectionPartLabel, c -> new ArrayList<>())
                        .add(new Parameter(name, defaultValue, false));
                return this;
            }

            public Builder addParameter(String sectionPartLabel, Parameter parameter) {
                parameters.computeIfAbsent(sectionPartLabel, c -> new ArrayList<>()).add(parameter);
                return this;
            }

            public ParametersInfo build() {
                return new ParametersInfo(parameters);
            }
        }

    }

}
