package com.abrar.sqlfmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    private record Result(int exit, String stdout, String stderr) {
    }

    private Result run(String[] args, String stdin) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        int exit = new Main().run(args,
                new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));
        return new Result(exit,
                outBytes.toString(StandardCharsets.UTF_8),
                errBytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void formatsStdinToStdout() {
        Result r = run(new String[]{}, "select id from users;");
        assertEquals(0, r.exit());
        assertEquals("SELECT\n    id\nFROM users;\n", r.stdout());
    }

    @Test
    void emptyStdinProducesEmptyOutput() {
        Result r = run(new String[]{}, "");
        assertEquals(0, r.exit());
        assertEquals("", r.stdout());
    }

    @Test
    void checkModePassesForFormattedInput() {
        Result r = run(new String[]{"--check"}, "SELECT\n    id\nFROM users;\n");
        assertEquals(0, r.exit());
        assertEquals("", r.stdout());
    }

    @Test
    void checkModeFailsForUnformattedInput() {
        Result r = run(new String[]{"--check"}, "select id from users;");
        assertEquals(1, r.exit());
    }

    @Test
    void checkModeReportsUnformattedFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("q.sql");
        Files.writeString(file, "select 1;");
        Result r = run(new String[]{"--check", file.toString()}, "");
        assertEquals(1, r.exit());
        assertTrue(r.stderr().contains("unformatted"));
    }

    @Test
    void formatsAFileToStdout(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("q.sql");
        Files.writeString(file, "select id from users;");
        Result r = run(new String[]{file.toString()}, "");
        assertEquals(0, r.exit());
        assertEquals("SELECT\n    id\nFROM users;\n", r.stdout());
    }

    @Test
    void inPlaceRewritesTheFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("q.sql");
        Files.writeString(file, "select id from users;");
        Result r = run(new String[]{"--in-place", file.toString()}, "");
        assertEquals(0, r.exit());
        assertEquals("SELECT\n    id\nFROM users;\n", Files.readString(file));
    }

    @Test
    void inPlaceAndCheckCannotCombine() {
        Result r = run(new String[]{"--in-place", "--check", "q.sql"}, "");
        assertEquals(2, r.exit());
    }

    @Test
    void unknownOptionIsAnError() {
        Result r = run(new String[]{"--frobnicate"}, "");
        assertEquals(2, r.exit());
        assertTrue(r.stderr().contains("unknown option"));
    }

    @Test
    void missingFileIsAnError() {
        Result r = run(new String[]{"no-such-file.sql"}, "");
        assertEquals(2, r.exit());
        assertTrue(r.stderr().contains("cannot read"));
    }

    @Test
    void indentAndKeywordCaseOptionsApply() {
        Result r = run(new String[]{"--indent", "2", "--keyword-case", "lower"}, "SELECT id FROM users;");
        assertEquals(0, r.exit());
        assertEquals("select\n  id\nfrom users;\n", r.stdout());
    }

    @Test
    void badKeywordCaseIsAnError() {
        Result r = run(new String[]{"--keyword-case", "shouty"}, "");
        assertEquals(2, r.exit());
    }

    @Test
    void helpExitsZero() {
        Result r = run(new String[]{"--help"}, "");
        assertEquals(0, r.exit());
        assertTrue(r.stdout().contains("Usage: sqlfmt"));
    }

    @Test
    void versionIsReported() {
        Result r = run(new String[]{"--version"}, "");
        assertEquals(0, r.exit());
        assertTrue(r.stdout().startsWith("sqlfmt "));
    }
}
