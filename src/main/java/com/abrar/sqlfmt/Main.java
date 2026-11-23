package com.abrar.sqlfmt;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line entry point for the SQL formatter.
 *
 * <p>Usage:
 * <pre>
 *   sqlfmt [options] [file ...]
 * </pre>
 * With no files, SQL is read from standard input. Options:
 * <ul>
 *   <li>{@code --indent N} — spaces per indent level (1–8, default 4)</li>
 *   <li>{@code --keyword-case upper|lower|preserve} (default upper)</li>
 *   <li>{@code --leading-comma} — put commas at the start of the next line</li>
 *   <li>{@code --check} — exit 1 if any input is not already formatted</li>
 *   <li>{@code --in-place} — rewrite the given files (requires files)</li>
 * </ul>
 * Exit codes: 0 ok, 1 {@code --check} found unformatted input, 2 usage or I/O error.
 */
public final class Main {

    public static final String VERSION = "1.0.0";

    Main() {
    }

    public static void main(String[] args) {
        System.exit(new Main().run(args, System.in, System.out, System.err));
    }

    int run(String[] args, InputStream stdin, PrintStream stdout, PrintStream stderr) {
        List<String> files = new ArrayList<>();
        int indent = 4;
        FormatterOptions.KeywordCase keywordCase = FormatterOptions.KeywordCase.UPPER;
        boolean leadingComma = false;
        boolean check = false;
        boolean inPlace = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> {
                    printUsage(stdout);
                    return 0;
                }
                case "--version", "-V" -> {
                    stdout.println("sqlfmt " + VERSION);
                    return 0;
                }
                case "--indent" -> {
                    if (i + 1 >= args.length) {
                        stderr.println("sqlfmt: --indent requires a number");
                        return 2;
                    }
                    try {
                        indent = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        stderr.println("sqlfmt: --indent requires a number, got '" + args[i] + "'");
                        return 2;
                    }
                    if (indent < 1 || indent > 8) {
                        stderr.println("sqlfmt: --indent must be between 1 and 8");
                        return 2;
                    }
                }
                case "--keyword-case" -> {
                    if (i + 1 >= args.length) {
                        stderr.println("sqlfmt: --keyword-case requires a value");
                        return 2;
                    }
                    String value = args[++i].toLowerCase(java.util.Locale.ROOT);
                    keywordCase = switch (value) {
                        case "upper" -> FormatterOptions.KeywordCase.UPPER;
                        case "lower" -> FormatterOptions.KeywordCase.LOWER;
                        case "preserve" -> FormatterOptions.KeywordCase.PRESERVE;
                        default -> {
                            stderr.println("sqlfmt: unknown keyword case '" + value
                                    + "' (expected upper, lower or preserve)");
                            yield null;
                        }
                    };
                    if (keywordCase == null) {
                        return 2;
                    }
                }
                case "--leading-comma" -> leadingComma = true;
                case "--check" -> check = true;
                case "--in-place" -> inPlace = true;
                default -> {
                    if (arg.startsWith("-")) {
                        stderr.println("sqlfmt: unknown option '" + arg + "'");
                        printUsage(stderr);
                        return 2;
                    }
                    files.add(arg);
                }
            }
        }

        if (inPlace && files.isEmpty()) {
            stderr.println("sqlfmt: --in-place requires at least one file");
            return 2;
        }
        if (inPlace && check) {
            stderr.println("sqlfmt: --in-place and --check cannot be combined");
            return 2;
        }

        FormatterOptions options;
        try {
            options = new FormatterOptions(indent, keywordCase, leadingComma);
        } catch (IllegalArgumentException e) {
            stderr.println("sqlfmt: " + e.getMessage());
            return 2;
        }
        SqlFormatter formatter = new SqlFormatter(options);

        if (files.isEmpty()) {
            String input;
            try {
                input = new String(stdin.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                stderr.println("sqlfmt: failed to read standard input: " + e.getMessage());
                return 2;
            }
            String formatted = formatter.format(input);
            if (check) {
                return formatted.equals(input) ? 0 : 1;
            }
            stdout.print(formatted);
            return 0;
        }

        int exit = 0;
        for (String file : files) {
            Path path = Path.of(file);
            String input;
            try {
                input = Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException e) {
                stderr.println("sqlfmt: cannot read '" + file + "': " + e.getMessage());
                exit = 2;
                continue;
            }
            String formatted = formatter.format(input);
            if (check) {
                if (!formatted.equals(input)) {
                    stderr.println("sqlfmt: unformatted: " + file);
                    exit = 1;
                }
            } else if (inPlace) {
                if (!formatted.equals(input)) {
                    try {
                        Files.writeString(path, formatted, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        stderr.println("sqlfmt: cannot write '" + file + "': " + e.getMessage());
                        exit = 2;
                    }
                }
            } else {
                stdout.print(formatted);
            }
        }
        return exit;
    }

    private void printUsage(PrintStream out) {
        out.println("Usage: sqlfmt [options] [file ...]");
        out.println();
        out.println("Formats SQL from the given files, or from standard input when no");
        out.println("file is given. Formatted SQL is written to standard output.");
        out.println();
        out.println("Options:");
        out.println("  --indent N            spaces per indent level, 1-8 (default 4)");
        out.println("  --keyword-case CASE   upper, lower or preserve (default upper)");
        out.println("  --leading-comma       put commas at the start of the next line");
        out.println("  --check               exit 1 if any input is not already formatted");
        out.println("  --in-place            rewrite the given files in place");
        out.println("  --help                show this help");
        out.println("  --version             print the version");
    }
}
