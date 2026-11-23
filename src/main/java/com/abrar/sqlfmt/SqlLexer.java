package com.abrar.sqlfmt;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tokenizes a SQL string.
 *
 * <p>Recognises keywords (case-insensitively), identifiers, quoted identifiers
 * ({@code "x"}, {@code `x`}, {@code [x]}), string literals with doubled-quote
 * escapes, numbers, placeholders ({@code ?}, {@code :name}, {@code $1},
 * {@code @name}), line and block comments, operators and punctuation.
 * Whitespace is discarded; comments are kept as tokens so the formatter can
 * preserve them.
 */
public final class SqlLexer {

    private static final Set<String> KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "LIKE", "ILIKE",
            "BETWEEN", "IS", "NULL", "TRUE", "FALSE", "JOIN", "INNER", "LEFT",
            "RIGHT", "FULL", "CROSS", "OUTER", "ON", "GROUP", "BY", "ORDER",
            "HAVING", "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT", "AS",
            "CASE", "WHEN", "THEN", "ELSE", "END", "INSERT", "INTO", "VALUES",
            "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "PRIMARY", "KEY",
            "REFERENCES", "INDEX", "UNIQUE", "DROP", "ALTER", "ADD", "COLUMN",
            "EXISTS", "ASC", "DESC", "RETURNING", "WITH", "RECURSIVE", "OVER",
            "PARTITION", "WINDOW", "CAST", "EXCEPT", "INTERSECT", "MINUS",
            "CONSTRAINT", "FOREIGN", "CHECK", "DEFAULT", "IF", "VIEW",
            "TRUNCATE", "GRANT", "REVOKE", "BEGIN", "COMMIT", "ROLLBACK",
            "EXPLAIN", "ANALYZE", "VACUUM", "TEMP", "TEMPORARY", "GLOBAL",
            "LOCAL", "CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME",
            "INTERVAL", "DATE", "TIME", "TIMESTAMP", "INT", "INTEGER", "BIGINT",
            "SMALLINT", "DECIMAL", "NUMERIC", "REAL", "DOUBLE", "PRECISION",
            "VARCHAR", "CHAR", "TEXT", "BOOLEAN", "BLOB", "SERIAL",
            "AUTO_INCREMENT", "CONFLICT", "DO", "NOTHING", "EXCLUDED",
            "USING", "NATURAL", "LATERAL", "FETCH", "FIRST", "NEXT", "ONLY",
            "ROWS", "ROW", "TIES", "NULLS", "LAST", "FILTER", "WITHIN",
            "GROUPING", "SETS", "CUBE", "ROLLUP", "PIVOT", "QUALIFY"
    );

    private final String input;

    public SqlLexer(String input) {
        this.input = input;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = input.length();
        while (i < n) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '-' && i + 1 < n && input.charAt(i + 1) == '-') {
                int end = input.indexOf('\n', i);
                if (end < 0) {
                    end = n;
                }
                tokens.add(new Token(Token.Type.LINE_COMMENT, input.substring(i, end).stripTrailing()));
                i = end;
            } else if (c == '/' && i + 1 < n && input.charAt(i + 1) == '*') {
                int end = input.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                tokens.add(new Token(Token.Type.BLOCK_COMMENT, input.substring(i, end)));
                i = end;
            } else if (c == '\'') {
                int j = i + 1;
                while (j < n) {
                    if (input.charAt(j) == '\'') {
                        if (j + 1 < n && input.charAt(j + 1) == '\'') {
                            j += 2;
                        } else {
                            j++;
                            break;
                        }
                    } else {
                        j++;
                    }
                }
                tokens.add(new Token(Token.Type.STRING, input.substring(i, j)));
                i = j;
            } else if (c == '"' || c == '`') {
                char quote = c;
                int j = i + 1;
                while (j < n) {
                    if (input.charAt(j) == quote) {
                        if (j + 1 < n && input.charAt(j + 1) == quote) {
                            j += 2;
                        } else {
                            j++;
                            break;
                        }
                    } else {
                        j++;
                    }
                }
                tokens.add(new Token(Token.Type.QUOTED_IDENTIFIER, input.substring(i, j)));
                i = j;
            } else if (c == '[') {
                int end = input.indexOf(']', i + 1);
                end = end < 0 ? n : end + 1;
                tokens.add(new Token(Token.Type.QUOTED_IDENTIFIER, input.substring(i, end)));
                i = end;
            } else if (c == '?' || c == '@'
                    || (c == ':' && i + 1 < n && isIdentStart(input.charAt(i + 1)))
                    || (c == '$' && i + 1 < n && Character.isDigit(input.charAt(i + 1)))) {
                int j = i + 1;
                while (j < n && isIdentPart(input.charAt(j))) {
                    j++;
                }
                tokens.add(new Token(Token.Type.PLACEHOLDER, input.substring(i, j)));
                i = j;
            } else if (Character.isDigit(c)
                    || (c == '.' && i + 1 < n && Character.isDigit(input.charAt(i + 1)))) {
                int j = i;
                while (j < n && (Character.isDigit(input.charAt(j)) || input.charAt(j) == '.')) {
                    j++;
                }
                if (j < n && (input.charAt(j) == 'e' || input.charAt(j) == 'E')) {
                    int k = j + 1;
                    if (k < n && (input.charAt(k) == '+' || input.charAt(k) == '-')) {
                        k++;
                    }
                    if (k < n && Character.isDigit(input.charAt(k))) {
                        j = k;
                        while (j < n && Character.isDigit(input.charAt(j))) {
                            j++;
                        }
                    }
                }
                tokens.add(new Token(Token.Type.NUMBER, input.substring(i, j)));
                i = j;
            } else if (isIdentStart(c)) {
                int j = i + 1;
                while (j < n && isIdentPart(input.charAt(j))) {
                    j++;
                }
                String word = input.substring(i, j);
                if (KEYWORDS.contains(word.toUpperCase(java.util.Locale.ROOT))) {
                    tokens.add(new Token(Token.Type.KEYWORD, word));
                } else {
                    tokens.add(new Token(Token.Type.IDENTIFIER, word));
                }
                i = j;
            } else if (c == ',') {
                tokens.add(new Token(Token.Type.COMMA, ","));
                i++;
            } else if (c == '(') {
                tokens.add(new Token(Token.Type.OPEN_PAREN, "("));
                i++;
            } else if (c == ')') {
                tokens.add(new Token(Token.Type.CLOSE_PAREN, ")"));
                i++;
            } else if (c == ';') {
                tokens.add(new Token(Token.Type.SEMICOLON, ";"));
                i++;
            } else if (c == '.') {
                tokens.add(new Token(Token.Type.DOT, "."));
                i++;
            } else {
                // Multi-character operators first, then single characters.
                String two = i + 1 < n ? input.substring(i, i + 2) : "";
                if (two.equals("<=") || two.equals(">=") || two.equals("<>")
                        || two.equals("!=") || two.equals("||") || two.equals("::")
                        || two.equals("->") || two.equals("=>")) {
                    tokens.add(new Token(Token.Type.OPERATOR, two));
                    i += 2;
                } else {
                    tokens.add(new Token(Token.Type.OPERATOR, String.valueOf(c)));
                    i++;
                }
            }
        }
        return tokens;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
