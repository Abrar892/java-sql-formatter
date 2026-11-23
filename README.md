# sqlfmt — SQL Formatter CLI

A zero-dependency SQL formatter for the command line. Feed it messy,
one-line SQL and get back consistently indented, readable SQL — the kind of
output you actually want to paste into a migration, a code review, or a
runbook.

```sql
-- before
select u.id, COUNT(o.id) as orders from users u left join orders o on o.user_id = u.id where u.active = 1 and o.created_at > '2024-01-01' group by u.id order by orders desc limit 10;

-- after
SELECT
    u.id,
    COUNT(o.id) AS orders
FROM users u
    LEFT JOIN orders o ON o.user_id = u.id
WHERE u.active = 1
    AND o.created_at > '2024-01-01'
GROUP BY u.id
ORDER BY orders DESC
LIMIT 10;
```

## Features

- **Clause-aware layout** — major clauses start a new line; the `SELECT`
  list, joins, `AND`/`OR` conditions and `CASE` branches are indented one
  level deeper.
- **Subqueries expand, calls stay inline** — a `SELECT` inside parentheses
  gets its own block, while function calls, `IN (...)` lists and `CREATE
  TABLE` column lists are handled sensibly.
- **Comments preserved** — line comments keep their own line, block comments
  stay inline.
- **Keyword casing** — upper (default), lower, or preserve.
- **CI-friendly** — `--check` exits non-zero when input is not already
  formatted, so it can gate a pre-commit hook.
- **Idempotent** — formatting already-formatted SQL returns the same text.

## Requirements

- JDK 17 or newer (no other dependencies, no build tool needed).

## Build

```bash
./build.sh          # compiles and builds out/sqlfmt.jar
./build.sh test     # compiles and runs the JUnit 5 test suite
```

## Usage

```bash
# Format from stdin
echo "select id from users;" | java -jar out/sqlfmt.jar

# Format a file
java -jar out/sqlfmt.jar query.sql

# Format several files
java -jar out/sqlfmt.jar migrations/*.sql

# Rewrite files in place
java -jar out/sqlfmt.jar --in-place query.sql

# Fail if the file is not already formatted (CI gate)
java -jar out/sqlfmt.jar --check query.sql
```

### Options

| Option | Description |
| --- | --- |
| `--indent N` | Spaces per indent level, 1–8 (default `4`) |
| `--keyword-case CASE` | `upper` (default), `lower`, or `preserve` |
| `--leading-comma` | Put commas at the start of the next line |
| `--check` | Exit 1 if any input is not already formatted |
| `--in-place` | Rewrite the given files in place |
| `--help` | Show help |
| `--version` | Print the version |

Exit codes: `0` ok, `1` `--check` found unformatted input, `2` usage or I/O error.

## How it works

Three small pieces, each independently testable:

1. **`SqlLexer`** — tokenizes SQL: keywords (matched case-insensitively),
   identifiers, quoted identifiers (`"x"`, `` `x` ``, `[x]`), string literals
   with doubled-quote escapes, numbers, placeholders (`?`, `:name`, `$1`,
   `@name`), line/block comments, operators and punctuation.
2. **`SqlFormatter`** — walks the token stream with a small state machine
   (parenthesis stack, `CASE` depth, `BETWEEN` tracking). Multi-word
   keywords (`GROUP BY`, `LEFT OUTER JOIN`, `INSERT INTO`, ...) are merged
   first so they are treated as one unit.
3. **`Main`** — argument parsing, file/stdin handling, `--check` and
   `--in-place` modes.

Two deliberate trade-offs:

- Parentheses are expanded only for subqueries and `CREATE TABLE` column
  lists. Short grouping parentheses (`WHERE (a = 1 OR b = 2)`) stay inline
  rather than exploding into five lines.
- The formatter is syntax-agnostic about dialect: it pretty-prints tokens,
  it does not validate SQL. A typo in your SQL will be formatted, not
  rejected.

## Tests

51 JUnit 5 tests cover the lexer, the formatter (clauses, joins, subqueries,
`CASE`, comments, options, idempotency) and the CLI (`--check`,
`--in-place`, error paths):

```bash
./build.sh test
```

## License

MIT — see [LICENSE](LICENSE).
