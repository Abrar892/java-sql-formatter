package com.abrar.sqlfmt;

/** A single lexical unit produced by {@link SqlLexer}. */
public final class Token {

    public enum Type {
        KEYWORD,
        IDENTIFIER,
        QUOTED_IDENTIFIER,
        STRING,
        NUMBER,
        PLACEHOLDER,
        OPERATOR,
        COMMA,
        OPEN_PAREN,
        CLOSE_PAREN,
        SEMICOLON,
        DOT,
        LINE_COMMENT,
        BLOCK_COMMENT
    }

    private final Type type;
    private final String text;

    public Token(Type type, String text) {
        this.type = type;
        this.text = text;
    }

    public Type type() {
        return type;
    }

    public String text() {
        return text;
    }

    /** Upper-cased text, handy for case-insensitive keyword comparison. */
    public String upper() {
        return text.toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    public String toString() {
        return type + "(" + text + ")";
    }
}
