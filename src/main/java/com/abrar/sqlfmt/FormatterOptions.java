package com.abrar.sqlfmt;

/** Formatting knobs for {@link SqlFormatter}. */
public final class FormatterOptions {

    public enum KeywordCase {
        UPPER,
        LOWER,
        PRESERVE
    }

    private final int indentSize;
    private final KeywordCase keywordCase;
    private final boolean leadingComma;

    public FormatterOptions(int indentSize, KeywordCase keywordCase, boolean leadingComma) {
        if (indentSize < 1 || indentSize > 8) {
            throw new IllegalArgumentException("indent size must be between 1 and 8");
        }
        this.indentSize = indentSize;
        this.keywordCase = keywordCase;
        this.leadingComma = leadingComma;
    }

    public static FormatterOptions defaults() {
        return new FormatterOptions(4, KeywordCase.UPPER, false);
    }

    public int indentSize() {
        return indentSize;
    }

    public KeywordCase keywordCase() {
        return keywordCase;
    }

    public boolean leadingComma() {
        return leadingComma;
    }
}
