package com.abrar.sqlfmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SqlFormatterTest {

    private static final SqlFormatter FORMATTER = new SqlFormatter(FormatterOptions.defaults());

    private static String format(String sql) {
        return FORMATTER.format(sql);
    }

    @Test
    void simpleSelectBreaksClausesOntoOwnLines() {
        assertEquals(
                "SELECT\n    id,\n    name\nFROM users;\n",
                format("select id, name from users;"));
    }

    @Test
    void whereAndConditionsEachGetTheirOwnLine() {
        assertEquals(
                "SELECT\n    id\nFROM users\nWHERE active = 1\n    AND age > 18\n    OR role = 'admin';\n",
                format("SELECT id FROM users WHERE active = 1 AND age > 18 OR role = 'admin';"));
    }

    @Test
    void joinsAreIndentedUnderFrom() {
        assertEquals(
                "SELECT\n    u.id,\n    o.total\nFROM users u\n    LEFT JOIN orders o ON o.user_id = u.id\n    INNER JOIN payments p ON p.order_id = o.id;\n",
                format("SELECT u.id, o.total FROM users u LEFT JOIN orders o ON o.user_id = u.id "
                        + "INNER JOIN payments p ON p.order_id = o.id;"));
    }

    @Test
    void groupByHavingAndOrderByAreSeparateClauses() {
        // count is a function name, not a keyword, so its case is preserved.
        assertEquals(
                "SELECT\n    status,\n    count(*) AS n\nFROM orders\nGROUP BY status\nHAVING count(*) > 5\nORDER BY n DESC;\n",
                format("select status, count(*) as n from orders group by status "
                        + "having count(*) > 5 order by n desc;"));
    }

    @Test
    void limitStaysOnOneLine() {
        assertEquals(
                "SELECT\n    id\nFROM users\nORDER BY created_at DESC\nLIMIT 10;\n",
                format("SELECT id FROM users ORDER BY created_at DESC LIMIT 10;"));
    }

    @Test
    void subqueryInFromIsExpanded() {
        assertEquals(
                "SELECT\n    id\nFROM (\n    SELECT\n        id\n    FROM users\n    WHERE active = 1\n) AS active_users;\n",
                format("SELECT id FROM (SELECT id FROM users WHERE active = 1) AS active_users;"));
    }

    @Test
    void scalarSubqueryInSelectIsExpanded() {
        assertEquals(
                "SELECT\n    id,\n    (\n        SELECT\n            MAX(score)\n        FROM results r\n        WHERE r.user_id = u.id\n    ) AS best\nFROM users u;\n",
                format("SELECT id, (SELECT MAX(score) FROM results r WHERE r.user_id = u.id) "
                        + "AS best FROM users u;"));
    }

    @Test
    void functionCallsAndInListsStayInline() {
        assertEquals(
                "SELECT\n    UPPER(name),\n    COALESCE(nickname, 'n/a')\nFROM users\nWHERE id IN (1, 2, 3)\n    AND score BETWEEN 10 AND 20;\n",
                format("SELECT UPPER(name), COALESCE(nickname, 'n/a') FROM users "
                        + "WHERE id IN (1, 2, 3) AND score BETWEEN 10 AND 20;"));
    }

    @Test
    void betweenAndDoesNotBreakTheLine() {
        String out = format("SELECT id FROM t WHERE x BETWEEN 1 AND 5;");
        assertTrue(out.contains("x BETWEEN 1 AND 5"), out);
    }

    @Test
    void caseExpressionIsIndented() {
        assertEquals(
                "SELECT\n    CASE status\n        WHEN 1 THEN 'new'\n        WHEN 2 THEN 'open'\n        ELSE 'done'\n    END AS label\nFROM tickets;\n",
                format("SELECT CASE status WHEN 1 THEN 'new' WHEN 2 THEN 'open' ELSE 'done' END AS label FROM tickets;"));
    }

    @Test
    void insertValuesRowsEachGetTheirOwnLine() {
        assertEquals(
                "INSERT INTO users (id, name)\nVALUES (1, 'amy'),\n    (2, 'bo');\n",
                format("INSERT INTO users (id, name) VALUES (1, 'amy'), (2, 'bo');"));
    }

    @Test
    void updateStatementFormatsSetClause() {
        assertEquals(
                "UPDATE users\nSET name = 'amy',\n    active = 0\nWHERE id = 7;\n",
                format("UPDATE users SET name = 'amy', active = 0 WHERE id = 7;"));
    }

    @Test
    void createTableColumnListIsExpanded() {
        assertEquals(
                "CREATE TABLE users (\n    id INT PRIMARY KEY,\n    name TEXT NOT NULL\n);\n",
                format("CREATE TABLE users (id INT PRIMARY KEY, name TEXT NOT NULL);"));
    }

    @Test
    void multipleStatementsAreSeparatedByABlankLine() {
        assertEquals(
                "SELECT\n    1;\n\nSELECT\n    2;\n",
                format("SELECT 1; SELECT 2;"));
    }

    @Test
    void lineCommentsKeepTheirOwnLine() {
        assertEquals(
                "SELECT\n    id -- the user id\nFROM users;\n",
                format("SELECT id -- the user id\nFROM users;"));
    }

    @Test
    void blockCommentsStayInline() {
        assertEquals(
                "SELECT\n    /* all columns */ id\nFROM users;\n",
                format("SELECT /* all columns */ id FROM users;"));
    }

    @Test
    void lowerKeywordCaseOption() {
        SqlFormatter lower = new SqlFormatter(
                new FormatterOptions(4, FormatterOptions.KeywordCase.LOWER, false));
        assertEquals(
                "select\n    id\nfrom users;\n",
                lower.format("SELECT id FROM users;"));
    }

    @Test
    void preserveKeywordCaseOption() {
        SqlFormatter preserve = new SqlFormatter(
                new FormatterOptions(4, FormatterOptions.KeywordCase.PRESERVE, false));
        assertEquals(
                "SeLeCt\n    id\nFrOm users;\n",
                preserve.format("SeLeCt id FrOm users;"));
    }

    @Test
    void indentSizeOption() {
        SqlFormatter two = new SqlFormatter(
                new FormatterOptions(2, FormatterOptions.KeywordCase.UPPER, false));
        assertEquals(
                "SELECT\n  id\nFROM users;\n",
                two.format("SELECT id FROM users;"));
    }

    @Test
    void leadingCommaOption() {
        SqlFormatter leading = new SqlFormatter(
                new FormatterOptions(4, FormatterOptions.KeywordCase.UPPER, true));
        assertEquals(
                "SELECT\n    id\n    , name\nFROM users;\n",
                leading.format("SELECT id, name FROM users;"));
    }

    @Test
    void formattingIsIdempotent() {
        String sql = "SELECT u.id, COUNT(o.id) AS n FROM users u "
                + "LEFT JOIN orders o ON o.user_id = u.id "
                + "WHERE u.active = 1 AND o.total > 100 "
                + "GROUP BY u.id HAVING COUNT(o.id) > 2 ORDER BY n DESC LIMIT 5;";
        String once = format(sql);
        assertEquals(once, format(once));
    }

    @Test
    void emptyInputProducesEmptyOutput() {
        assertEquals("", format(""));
        assertEquals("", format("   \n  "));
    }

    @Test
    void stringLiteralsAreNotReformatted() {
        assertEquals(
                "SELECT\n    'a  b -- not a comment'\nFROM t;\n",
                format("SELECT 'a  b -- not a comment' FROM t;"));
    }

    @Test
    void quotedIdentifiersAndQualifiedNamesSurvive() {
        assertEquals(
                "SELECT\n    \"my col\",\n    u.id\nFROM \"my table\" u;\n",
                format("SELECT \"my col\", u.id FROM \"my table\" u;"));
    }

    @Test
    void castOperatorHasNoSpaces() {
        assertEquals(
                "SELECT\n    x::INT\nFROM t;\n",
                format("SELECT x::int FROM t;"));
    }

    @Test
    void unaryMinusStaysAttachedToItsNumber() {
        String out = format("SELECT * FROM t WHERE x = -1 AND y > -2.5;");
        assertTrue(out.contains("x = -1"), out);
        assertTrue(out.contains("y > -2.5"), out);
    }

    @Test
    void distinctStaysOnTheSelectLine() {
        assertEquals(
                "SELECT DISTINCT\n    status\nFROM orders;\n",
                format("SELECT DISTINCT status FROM orders;"));
    }
}
