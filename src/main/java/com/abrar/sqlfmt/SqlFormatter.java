package com.abrar.sqlfmt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Clause-aware SQL pretty-printer.
 *
 * <p>Style produced (4-space indent, upper-case keywords by default):
 *
 * <pre>
 * SELECT
 *     u.id,
 *     COUNT(o.id) AS orders
 * FROM users u
 *     LEFT JOIN orders o ON o.user_id = u.id
 * WHERE u.active = 1
 *     AND o.created_at &gt; '2024-01-01'
 * GROUP BY u.id
 * ORDER BY orders DESC
 * LIMIT 10;
 * </pre>
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Major clause keywords start a new line at the current parenthesis
 *       depth. The {@code SELECT} list goes on the following lines, indented
 *       one level deeper; other clauses keep their first item on the same
 *       line, with continuations indented one level deeper.</li>
 *   <li>Parentheses are expanded only for subqueries (a {@code SELECT} or
 *       {@code WITH} follows) and for {@code CREATE TABLE} column lists;
 *       function calls, {@code IN} lists and grouping parentheses stay inline.</li>
 *   <li>{@code AND} inside a {@code BETWEEN ... AND ...} stays inline.</li>
 *   <li>Comments are preserved: line comments keep their own line, block
 *       comments stay inline.</li>
 *   <li>Formatting is idempotent: formatting already-formatted SQL returns
 *       the same text.</li>
 * </ul>
 */
public final class SqlFormatter {

    private static final Set<String> MAJOR_CLAUSE = Set.of(
            "SELECT", "FROM", "WHERE", "GROUP BY", "HAVING", "ORDER BY",
            "LIMIT", "OFFSET", "VALUES", "UNION", "UNION ALL", "EXCEPT",
            "INTERSECT", "MINUS", "INSERT INTO", "UPDATE", "DELETE FROM",
            "SET", "RETURNING", "WITH"
    );

    /** Clauses whose contents stay on the same line (e.g. {@code LIMIT 10}). */
    private static final Set<String> INLINE_CONTENT_CLAUSE = Set.of(
            "LIMIT", "OFFSET", "UPDATE", "INSERT INTO");

    private static final Set<String> JOIN_KEYWORD = Set.of(
            "JOIN", "INNER JOIN", "LEFT JOIN", "LEFT OUTER JOIN",
            "RIGHT JOIN", "RIGHT OUTER JOIN", "FULL JOIN", "FULL OUTER JOIN",
            "CROSS JOIN"
    );

    private final FormatterOptions options;

    public SqlFormatter(FormatterOptions options) {
        this.options = options;
    }

    public String format(String sql) {
        List<Token> tokens = mergePhrases(new SqlLexer(sql).tokenize());
        StringBuilder out = new StringBuilder();
        State st = new State(out);

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            Token next = nextSignificant(tokens, i);
            switch (token.type()) {
                case LINE_COMMENT -> {
                    if (st.atLineStart) {
                        st.write(token.text());
                    } else {
                        st.space();
                        st.write(token.text());
                    }
                    st.newline(st.indentLevel);
                }
                case BLOCK_COMMENT -> {
                    st.spaceIfNeeded(token);
                    st.write(token.text());
                }
                case SEMICOLON -> {
                    st.write(";");
                    st.resetStatement();
                    if (next != null) {
                        out.append("\n\n");
                        st.newline(0);
                    }
                }
                case COMMA -> {
                    if ((!st.parenStack.isEmpty() && !st.parenStack.peek().block) || st.noBreak) {
                        st.write(",");
                        st.space();
                    } else if (options.leadingComma()) {
                        st.newline(st.indentLevel);
                        st.write(",");
                        st.space();
                    } else {
                        st.write(",");
                        st.newline(st.indentLevel);
                    }
                }
                case OPEN_PAREN -> {
                    st.noBreak = false;
                    boolean block = isBlockParen(tokens, i, st);
                    if (block) {
                        st.spaceIfNeeded(token);
                        st.write("(");
                        int contentLevel = st.lineLevel + 1;
                        st.parenStack.push(new ParenFrame(true, st.lineLevel, st.baseLevel, st.indentLevel));
                        st.baseLevel = contentLevel;
                        st.newline(contentLevel);
                        st.indentLevel = contentLevel;
                    } else {
                        if (st.prev != null && st.prev.type() == Token.Type.IDENTIFIER
                                && st.prevPrev != null && st.prevPrev.type() == Token.Type.KEYWORD
                                && st.prevPrev.upper().endsWith("INTO")) {
                            // INSERT INTO users (id, name) — column list, not a call.
                            st.spaceIfNeeded(token);
                            st.write("(");
                        } else if (st.prev != null && st.prev.type() == Token.Type.IDENTIFIER
                                || st.prev != null && st.prev.type() == Token.Type.KEYWORD
                                && st.prev.upper().equals("CAST")) {
                            // Function call: COUNT(, CAST( — no space.
                            st.write("(");
                        } else {
                            st.spaceIfNeeded(token);
                            st.write("(");
                        }
                        st.parenStack.push(new ParenFrame(false, st.lineLevel, st.baseLevel, st.indentLevel));
                    }
                }
                case CLOSE_PAREN -> {
                    st.noBreak = false;
                    ParenFrame frame = st.parenStack.isEmpty()
                            ? new ParenFrame(false, 0, 0, 0) : st.parenStack.pop();
                    if (frame.block) {
                        st.baseLevel = frame.savedBase;
                        st.indentLevel = frame.savedIndent;
                        if (st.prev != null && st.prev.type() == Token.Type.OPEN_PAREN) {
                            st.write(")");
                        } else {
                            st.newline(frame.lineLevel);
                            st.write(")");
                        }
                    } else {
                        st.write(")");
                    }
                }
                case KEYWORD -> {
                    String word = keywordText(token);
                    if (MAJOR_CLAUSE.contains(word.toUpperCase(java.util.Locale.ROOT))) {
                        st.noBreak = false;
                        st.newline(st.baseLevel);
                        st.write(word);
                        if (INLINE_CONTENT_CLAUSE.contains(word.toUpperCase(java.util.Locale.ROOT))) {
                            // LIMIT 10, UPDATE users, INSERT INTO users (...) — the
                            // first item stays on the keyword's line, commas stay inline.
                            st.noBreak = true;
                        } else if (word.equalsIgnoreCase("SELECT")) {
                            // SELECT DISTINCT / SELECT ALL stay on one line.
                            if (next != null && next.type() == Token.Type.KEYWORD
                                    && (next.upper().equals("DISTINCT") || next.upper().equals("ALL"))) {
                                st.space();
                                st.write(keywordText(next));
                                i++;
                            }
                            st.newline(st.baseLevel + 1);
                            st.indentLevel = st.baseLevel + 1;
                        } else {
                            // FROM users, WHERE active = 1, ... — the first item stays
                            // on the keyword's line; later items indent one level.
                            st.indentLevel = st.baseLevel + 1;
                        }
                    } else if (JOIN_KEYWORD.contains(word.toUpperCase(java.util.Locale.ROOT))) {
                        st.noBreak = false;
                        st.newline(st.baseLevel + 1);
                        st.write(word);
                        st.indentLevel = st.baseLevel + 1;
                    } else if (word.equalsIgnoreCase("AND") || word.equalsIgnoreCase("OR")) {
                        if (st.betweenDepth > 0) {
                            st.space();
                            st.write(word);
                            st.space();
                            st.betweenDepth--;
                        } else {
                            st.noBreak = false;
                            st.newline(st.baseLevel + 1 + st.caseDepth);
                            st.write(word);
                            st.indentLevel = st.baseLevel + 1 + st.caseDepth;
                        }
                    } else if (word.equalsIgnoreCase("BETWEEN")) {
                        st.spaceIfNeeded(token);
                        st.write(word);
                        st.betweenDepth++;
                    } else if (word.equalsIgnoreCase("CASE")) {
                        st.spaceIfNeeded(token);
                        st.write(word);
                        st.caseDepth++;
                    } else if (word.equalsIgnoreCase("WHEN") || word.equalsIgnoreCase("ELSE")) {
                        st.newline(st.baseLevel + st.caseDepth + 1);
                        st.write(word);
                        st.indentLevel = st.baseLevel + st.caseDepth + 1;
                    } else if (word.equalsIgnoreCase("END")) {
                        st.caseDepth = Math.max(0, st.caseDepth - 1);
                        st.newline(st.baseLevel + st.caseDepth + 1);
                        st.write(word);
                        st.indentLevel = st.baseLevel + st.caseDepth + 1;
                    } else {
                        st.spaceIfNeeded(token);
                        st.write(word);
                    }
                }
                default -> {
                    st.spaceIfNeeded(token);
                    st.write(token.text());
                }
            }
            if (token.type() != Token.Type.LINE_COMMENT) {
                st.prevPrev = st.prev;
                st.prev = token;
            }
        }

        String result = out.toString().stripTrailing();
        return result.isEmpty() ? "" : result + "\n";
    }

    /** A parenthesis opens a new block (expanded over several lines) for a
     * subquery, a FROM/JOIN subquery, or a CREATE TABLE column list. */
    private boolean isBlockParen(List<Token> tokens, int index, State st) {
        Token next = nextSignificant(tokens, index);
        if (next != null && next.type() == Token.Type.KEYWORD
                && (next.upper().equals("SELECT") || next.upper().equals("WITH"))) {
            return true;
        }
        if (st.prev != null && st.prev.type() == Token.Type.KEYWORD
                && (st.prev.upper().equals("FROM") || JOIN_KEYWORD.contains(st.prev.upper()))) {
            return true;
        }
        // CREATE TABLE t ( ... )
        if (st.prev != null && st.prev.type() == Token.Type.IDENTIFIER
                && st.prevPrev != null && st.prevPrev.type() == Token.Type.KEYWORD
                && st.prevPrev.upper().equals("TABLE")) {
            return true;
        }
        return false;
    }

    /**
     * Merges consecutive keyword tokens that form a multi-word keyword
     * ({@code GROUP BY}, {@code LEFT OUTER JOIN}, {@code INSERT INTO}, ...).
     */
    static List<Token> mergePhrases(List<Token> tokens) {
        List<Token> merged = new ArrayList<>();
        int i = 0;
        while (i < tokens.size()) {
            Token t = tokens.get(i);
            if (t.type() == Token.Type.KEYWORD) {
                String two = phrase(tokens, i, 2);
                String three = phrase(tokens, i, 3);
                if (three != null && (three.equals("LEFT OUTER JOIN")
                        || three.equals("RIGHT OUTER JOIN")
                        || three.equals("FULL OUTER JOIN"))) {
                    merged.add(new Token(Token.Type.KEYWORD, joinOriginal(tokens, i, 3)));
                    i += 3;
                    continue;
                }
                if (two != null && (two.equals("GROUP BY") || two.equals("ORDER BY")
                        || two.equals("INSERT INTO") || two.equals("DELETE FROM")
                        || two.equals("UNION ALL") || two.equals("LEFT JOIN")
                        || two.equals("RIGHT JOIN") || two.equals("FULL JOIN")
                        || two.equals("INNER JOIN") || two.equals("CROSS JOIN"))) {
                    merged.add(new Token(Token.Type.KEYWORD, joinOriginal(tokens, i, 2)));
                    i += 2;
                    continue;
                }
            }
            merged.add(t);
            i++;
        }
        return merged;
    }

    private static String phrase(List<Token> tokens, int i, int count) {
        if (i + count > tokens.size()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < count; k++) {
            Token t = tokens.get(i + k);
            if (t.type() != Token.Type.KEYWORD) {
                return null;
            }
            if (k > 0) {
                sb.append(' ');
            }
            sb.append(t.upper());
        }
        return sb.toString();
    }

    private static String joinOriginal(List<Token> tokens, int i, int count) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < count; k++) {
            if (k > 0) {
                sb.append(' ');
            }
            sb.append(tokens.get(i + k).text());
        }
        return sb.toString();
    }

    private static Token nextSignificant(List<Token> tokens, int i) {
        for (int j = i + 1; j < tokens.size(); j++) {
            Token t = tokens.get(j);
            if (t.type() != Token.Type.LINE_COMMENT && t.type() != Token.Type.BLOCK_COMMENT) {
                return t;
            }
        }
        return null;
    }

    private String keywordText(Token token) {
        return switch (options.keywordCase()) {
            case UPPER -> token.text().toUpperCase(java.util.Locale.ROOT);
            case LOWER -> token.text().toLowerCase(java.util.Locale.ROOT);
            case PRESERVE -> token.text();
        };
    }

    /** One open parenthesis, with the state needed to restore on close. */
    private record ParenFrame(boolean block, int lineLevel, int savedBase, int savedIndent) {
    }

    /** Mutable formatting state carried across tokens. */
    private final class State {
        private final StringBuilder out;
        private boolean atLineStart = true;
        private int pendingIndent = 0;
        /** Indent level of the line currently being written. */
        private int lineLevel = 0;
        /** Level at which clause keywords print (content level of the innermost block paren). */
        private int baseLevel = 0;
        /** Level for continuation lines (commas, AND/OR, ...). */
        private int indentLevel = 0;
        private int caseDepth = 0;
        private int betweenDepth = 0;
        private boolean noBreak = false;
        private final Deque<ParenFrame> parenStack = new ArrayDeque<>();
        private Token prev;
        private Token prevPrev;

        State(StringBuilder out) {
            this.out = out;
        }

        void newline(int level) {
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                out.append('\n');
            }
            pendingIndent = level;
            lineLevel = level;
            atLineStart = true;
        }

        void write(String text) {
            if (atLineStart) {
                out.append(" ".repeat(Math.max(0, pendingIndent) * options.indentSize()));
                atLineStart = false;
            }
            out.append(text);
        }

        void space() {
            if (!atLineStart && out.length() > 0 && out.charAt(out.length() - 1) != ' ') {
                out.append(' ');
            }
        }

        /** Adds a single space before the token unless punctuation rules forbid it. */
        void spaceIfNeeded(Token token) {
            if (atLineStart) {
                return;
            }
            if (prev == null) {
                return;
            }
            Token.Type prevType = prev.type();
            Token.Type type = token.type();
            if (type == Token.Type.DOT || type == Token.Type.COMMA
                    || type == Token.Type.CLOSE_PAREN || type == Token.Type.SEMICOLON) {
                return;
            }
            if (prevType == Token.Type.DOT || prevType == Token.Type.OPEN_PAREN) {
                return;
            }
            if (prevType == Token.Type.OPERATOR && prev.text().equals("::")) {
                return;
            }
            if (type == Token.Type.OPERATOR && token.text().equals("::")) {
                return;
            }
            // Unary minus/plus: "-1" after "=", "(", "," or a keyword.
            if (type == Token.Type.NUMBER && prevType == Token.Type.OPERATOR
                    && (prev.text().equals("-") || prev.text().equals("+"))) {
                if (prevPrev == null || prevPrev.type() == Token.Type.OPERATOR
                        || prevPrev.type() == Token.Type.OPEN_PAREN
                        || prevPrev.type() == Token.Type.COMMA
                        || prevPrev.type() == Token.Type.KEYWORD) {
                    return;
                }
            }
            space();
        }

        void resetStatement() {
            indentLevel = 0;
            baseLevel = 0;
            lineLevel = 0;
            caseDepth = 0;
            betweenDepth = 0;
            noBreak = false;
            parenStack.clear();
            atLineStart = true;
            pendingIndent = 0;
        }
    }
}
