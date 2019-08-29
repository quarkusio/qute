package io.quarkus.qute;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.quarkus.qute.Results.Result;

/**
 * Represents a value expression. It could be a literal such as {@code 'foo'}. It could have a namespace such as {@code data}
 * for {@code data:name}</li>. It could have several parts such as {@code item} and {@code name} for {@code item.name}.
 * 
 * @see Evaluator
 */
public final class Expression {

    static final Expression EMPTY = new Expression(null, Collections.emptyList(), null);

    public static Expression single(String value) {
        if (value == null || value.isEmpty()) {
            return EMPTY;
        }
        // No literal, no namespace, single part
        return new Expression(null, Collections.singletonList(value), LiteralSupport.getLiteral(value));
    }

    public static Expression parse(String value) {
        if (value == null || value.isEmpty()) {
            return EMPTY;
        }
        String namespace = null;
        List<String> parts;
        Object literal = Result.NOT_FOUND;
        int namespaceIdx = value.indexOf(':');
        int spaceIdx = value.indexOf(' ');
        int brackerIdx = value.indexOf('(');
        if (namespaceIdx != -1 && (spaceIdx == -1 || namespaceIdx < spaceIdx)
                && (brackerIdx == -1 || namespaceIdx < brackerIdx)) {
            parts = split(value.substring(namespaceIdx + 1, value.length()));
            namespace = value.substring(0, namespaceIdx);
        } else {
            parts = split(value);
            if (parts.size() == 1) {
                literal = LiteralSupport.getLiteral(parts.get(0));
            }
        }
        return new Expression(namespace, parts, literal);
    }

    public final String namespace;
    public final List<String> parts;
    public final CompletableFuture<Object> literal;

    Expression(String namespace, List<String> parts, Object literal) {
        this.namespace = namespace;
        this.parts = parts;
        this.literal = literal != Result.NOT_FOUND ? CompletableFuture.completedFuture(literal) : null;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Expression [namespace=").append(namespace).append(", parts=").append(parts).append(", literal=")
                .append(literal).append("]");
        return builder.toString();
    }

    private static List<String> split(String value) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        boolean stringLiteral = false;
        boolean separator = false;
        boolean infix = false;
        boolean brackets = false;
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (isSeparator(c)) {
                // Adjacent separators are ignored
                if (!separator) {
                    if (!stringLiteral) {
                        if (buffer.length() > 0) {
                            builder.add(buffer.toString());
                            buffer = new StringBuilder();
                        }
                        separator = true;
                    } else {
                        buffer.append(c);
                    }
                }
            } else {
                if (isStringLiteralSeparator(c)) {
                    stringLiteral = !stringLiteral;
                }
                // Non-separator char
                if (!stringLiteral) {
                    if (!brackets && c == ' ') {
                        if (infix) {
                            buffer.append('(');
                        } else {
                            // Separator
                            infix = true;
                            if (buffer.length() > 0) {
                                builder.add(buffer.toString());
                                buffer = new StringBuilder();
                            }
                        }
                    } else {
                        if (isBracket(c)) {
                            brackets = !brackets;
                        }
                        buffer.append(c);
                    }
                    separator = false;
                } else {
                    buffer.append(c);
                    separator = false;
                }
            }
        }
        if (infix) {
            buffer.append(')');
        }
        if (buffer.length() > 0) {
            builder.add(buffer.toString());
        }
        return builder.build();
    }

    static boolean isSeparator(char candidate) {
        return candidate == '.' || candidate == '[' || candidate == ']';
    }

    /**
     *
     * @param character
     * @return <code>true</code> if the char is a string literal separator,
     *         <code>false</code> otherwise
     */
    static boolean isStringLiteralSeparator(char character) {
        return character == '"' || character == '\'';
    }

    static boolean isBracket(char character) {
        return character == '(' || character == ')';
    }

}
