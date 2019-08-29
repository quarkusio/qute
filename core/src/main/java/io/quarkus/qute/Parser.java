package io.quarkus.qute;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.qute.SectionHelperFactory.ParametersInfo;

/**
 * Simple non-reusable parser.
 */
class Parser {

    private static final Logger LOGGER = LoggerFactory.getLogger(Parser.class);
    private static final String ROOT_HELPER_NAME = "$root";

    private final EngineImpl engine;

    private final char startDelimiter = '{';
    private final char endDelimiter = '}';

    private StringBuilder buffer;
    private State state;
    private final Deque<SectionNode.Builder> sectionStack;
    private final Deque<SectionBlock.Builder> sectionBlockStack;
    private final Deque<ParametersInfo> paramsStack;
    private int sectionBlockIdx;
    private boolean ignoreContent;

    public Parser(EngineImpl engine) {
        this.engine = engine;
        this.state = State.TEXT;
        this.buffer = new StringBuilder();
        this.sectionStack = new ArrayDeque<>();
        this.sectionStack
                .addFirst(SectionNode.builder(ROOT_HELPER_NAME).setEngine(engine)
                        .setHelperFactory(new SectionHelperFactory<SectionHelper>() {
                            @Override
                            public SectionHelper initialize(SectionInitContext context) {
                                return new SectionHelper() {

                                    @Override
                                    public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
                                        return context.execute();
                                    }
                                };
                            }

                        }));
        this.sectionBlockStack = new ArrayDeque<>();
        this.sectionBlockStack.addFirst(SectionBlock.builder("main"));
        this.sectionBlockIdx = 0;
        this.paramsStack = new ArrayDeque<>();
        this.paramsStack.addFirst(ParametersInfo.EMPTY);
    }

    Template parse(Reader reader) {
        long start = System.currentTimeMillis();
        reader = ensureBufferedReader(reader);
        try {
            int val;
            while ((val = reader.read()) != -1) {
                processCharacter((char) val);
            }

            if (buffer.length() > 0) {
                if (state == State.TEXT) {
                    // Flush the last text segment
                    flushText();
                } else {
                    throw new IllegalStateException(
                            "Unexpected non-text buffer at the end of the document (probably unterminated tag):" +
                                    buffer);
                }
            }

            SectionNode.Builder root = sectionStack.peek();
            if (root == null) {
                throw new IllegalStateException("No root section found!");
            }
            if (!root.helperName.equals(ROOT_HELPER_NAME)) {
                throw new IllegalStateException("The last section on the stack is not a root but: " + root.helperName);
            }
            SectionBlock.Builder part = sectionBlockStack.peek();
            if (part == null) {
                throw new IllegalStateException("No root section part found!");
            }
            root.addBlock(part.build());
            Template template = new TemplateImpl(engine, root.build());
            LOGGER.debug("Parsing finished in {} ms", System.currentTimeMillis() - start);
            return template;

        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void processCharacter(char character) {
        switch (state) {
            case TEXT:
                text(character);
                break;
            case TAG_INSIDE:
                tag(character);
                break;
            case TAG_CANDIDATE:
                tagCandidate(character);
                break;
            default:
                throw new IllegalStateException("Unknown parsing state");
        }
    }

    private void text(char character) {
        if (character == startDelimiter) {
            state = State.TAG_CANDIDATE;
        } else {
            buffer.append(character);
        }
    }

    private void tag(char character) {
        if (character == endDelimiter) {
            flushTag();
        } else {
            buffer.append(character);
        }
    }

    private void tagCandidate(char character) {
        if (Character.isWhitespace(character)) {
            buffer.append(startDelimiter);
            state = State.TEXT;
        } else if (character == startDelimiter) {
            buffer.append(startDelimiter).append(startDelimiter);
            state = State.TEXT;
        } else {
            // Real tag start, flush text if any
            flushText();
            state = State.TAG_INSIDE;
            buffer.append(character);
        }
    }

    private void flushText() {
        if (buffer.length() > 0 && !ignoreContent) {
            SectionBlock.Builder block = sectionBlockStack.peek();
            block.addNode(new TextNode(buffer.toString()));
        }
        this.buffer = new StringBuilder();
    }

    private void flushTag() {
        state = State.TEXT;
        String content = buffer.toString();

        if (content.charAt(0) == Tag.SECTION.getCommand()) {

            boolean isEmptySection = false;
            if (content.charAt(content.length() - 1) == Tag.SECTION_END.command) {
                content = content.substring(0, content.length() - 1);
                isEmptySection = true;
            }

            Iterator<String> iter = splitSectionParams(content);
            if (!iter.hasNext()) {
                throw new IllegalStateException("No helper name");
            }
            String helperName = iter.next();
            helperName = helperName.substring(1, helperName.length());
            SectionHelperFactory<?> factory = engine.getSectionHelperFactories().get(helperName);
            if (factory == null) {
                throw new IllegalStateException("No section helper for: " + helperName);
            }
            paramsStack.addFirst(factory.getParameters());
            // TODO main constant
            SectionBlock.Builder mainBlock = SectionBlock.builder("main").setLabel("main");
            sectionBlockStack.addFirst(mainBlock);
            processParams("main", iter);
            SectionNode.Builder sectionNode = SectionNode.builder(helperName).setEngine(engine).setHelperFactory(factory);

            if (isEmptySection) {
                sectionNode.addBlock(mainBlock.build());
                // Remove params from the stack
                paramsStack.pop();
                // Remove the block from the stack
                sectionBlockStack.pop();
                // Add node to the parent block
                sectionBlockStack.peek().addNode(sectionNode.build());
            } else {
                sectionStack.addFirst(sectionNode);
            }

        } else if (content.charAt(0) == Tag.SECTION_BLOCK.getCommand()) {
            if (!ignoreContent) {
                // E.g. {:else if valid}
                // Build the previous block
                sectionStack.peek().addBlock(sectionBlockStack.pop().build());
            }
            // Add the new block
            SectionBlock.Builder block = SectionBlock.builder("" + sectionBlockIdx++);
            Iterator<String> iter = splitSectionParams(content);
            if (!iter.hasNext()) {
                throw new IllegalStateException("No label for a section block");
            }
            String label = iter.next();
            label = label.substring(1, label.length());
            sectionBlockStack.addFirst(block.setLabel(label));
            processParams(label, iter);
            ignoreContent = false;

        } else if (content.charAt(0) == Tag.SECTION_END.getCommand()) {
            SectionBlock.Builder block = sectionBlockStack.peek();
            String name = content.substring(1, content.length());
            if (block != null && name.equals(block.getLabel())) {
                // Block end
                SectionNode.Builder section = sectionStack.peek();
                section.addBlock(sectionBlockStack.pop().build());
                ignoreContent = true;
            } else {
                // Section end
                SectionNode.Builder section = sectionStack.pop();
                if (!name.isEmpty() && !section.helperName.equals(name)) {
                    throw new IllegalStateException(
                            "Section eng tag does not match the start tag. Start: " + section.helperName + ", end: " + name);
                }
                if (!ignoreContent) {
                    section.addBlock(sectionBlockStack.pop().build());
                }
                sectionBlockStack.peek().addNode(section.build());
            }
        } else if (content.charAt(0) != '!') {
            sectionBlockStack.peek().addNode(new ExpressionNode(content));
        }
        this.buffer = new StringBuilder();
    }

    private void processParams(String label, Iterator<String> iter) {
        Map<String, String> params = new HashMap<>();
        List<Parameter> factoryParams = paramsStack.peek().get(label);
        List<String> paramValues = new ArrayList<>();

        while (iter.hasNext()) {
            paramValues.add(iter.next());
        }
        if (paramValues.size() > factoryParams.size()) {
            LOGGER.debug("Too many params [label={}, params={}, factoryParams={}]", label, params, factoryParams);
        }
        if (paramValues.size() < factoryParams.size()) {
            for (String param : paramValues) {
                int equalsPosition = getFirstDeterminingEqualsCharPosition(param);
                if (equalsPosition != -1) {
                    // Named param
                    params.put(param.substring(0, equalsPosition), param.substring(equalsPosition + 1,
                            param.length()));
                } else {
                    // Positional param - first non-default section param
                    for (Parameter factoryParam : factoryParams) {
                        if (factoryParam.defaultValue == null && !params.containsKey(factoryParam.name)) {
                            params.put(factoryParam.name, param);
                            break;
                        }
                    }
                }
            }
        } else {
            for (String param : paramValues) {
                int equalsPosition = getFirstDeterminingEqualsCharPosition(param);
                if (equalsPosition != -1) {
                    // Named param
                    params.put(param.substring(0, equalsPosition), param.substring(equalsPosition + 1,
                            param.length()));
                } else {
                    // Positional param - first non-default section param
                    for (Parameter factoryParam : factoryParams) {
                        if (!params.containsKey(factoryParam.name)) {
                            params.put(factoryParam.name, param);
                            break;
                        }
                    }
                }
            }
        }

        factoryParams.stream().filter(p -> p.defaultValue != null).forEach(p -> params.putIfAbsent(p.name, p.defaultValue));

        // TODO validate params
        List<Parameter> undeclaredParams = factoryParams.stream().filter(p -> !p.optional && !params.containsKey(p.name))
                .collect(Collectors.toList());
        if (!undeclaredParams.isEmpty()) {
            throw new IllegalStateException("Undeclared section params: " + undeclaredParams);
        }

        params.forEach(sectionBlockStack.peek()::addParameter);
    }

    /**
     *
     * @param part
     * @return the index of an equals char outside of any string literal,
     *         <code>-1</code> if no such char is found
     */
    static int getFirstDeterminingEqualsCharPosition(String part) {
        boolean stringLiteral = false;
        for (int i = 0; i < part.length(); i++) {
            if (isStringLiteralSeparator(part.charAt(i))) {
                if (i == 0) {
                    // The first char is a string literal separator
                    return -1;
                }
                stringLiteral = !stringLiteral;
            } else {
                if (!stringLiteral && part.charAt(i) == '=' && (i != 0) && (i < (part.length() - 1))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private Reader ensureBufferedReader(Reader reader) {
        return reader instanceof BufferedReader ? reader
                : new BufferedReader(
                        reader);
    }

    static Iterator<String> splitSectionParams(String content) {

        boolean stringLiteral = false;
        boolean listLiteral = false;
        boolean space = false;
        List<String> parts = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == ' ') {
                if (!space) {
                    if (!stringLiteral && !listLiteral) {
                        if (buffer.length() > 0) {
                            parts.add(buffer.toString());
                            buffer = new StringBuilder();
                        }
                        space = true;
                    } else {
                        buffer.append(content.charAt(i));
                    }
                }
            } else {
                if (!listLiteral
                        && isStringLiteralSeparator(content.charAt(i))) {
                    stringLiteral = !stringLiteral;
                } else if (!stringLiteral
                        && isListLiteralStart(content.charAt(i))) {
                    listLiteral = true;
                } else if (!stringLiteral
                        && isListLiteralEnd(content.charAt(i))) {
                    listLiteral = false;
                }
                space = false;
                buffer.append(content.charAt(i));
            }
        }

        if (buffer.length() > 0) {
            if (stringLiteral || listLiteral) {
                throw new IllegalStateException(
                        "Unterminated string or array literal detected");
            }
            parts.add(buffer.toString());
        }
        return parts.iterator();
    }

    static boolean isStringLiteralSeparator(char character) {
        return character == '"' || character == '\'';
    }

    static boolean isListLiteralStart(char character) {
        return character == '[';
    }

    static boolean isListLiteralEnd(char character) {
        return character == ']';
    }

    enum Tag {

        EXPRESSION(null),
        SECTION('#'),
        SECTION_END('/'),
        SECTION_BLOCK(':'),
        ;

        private final Character command;

        private Tag(Character command) {
            this.command = command;
        }

        public Character getCommand() {
            return command;
        }

    }

    enum State {

        TEXT,
        TAG_INSIDE,
        TAG_CANDIDATE,

    }

}
