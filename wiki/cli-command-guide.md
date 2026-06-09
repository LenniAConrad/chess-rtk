# CLI Command Guide

This guide explains how to add or change a ChessRTK CLI command. The CLI is not a loose argument parser. It is a deterministic noun-then-verb command surface backed by one shared chess core, central command metadata, built-in help, regression tests, and generated documentation.

Use this guide whenever you add a new command, rename a command, add a flag, change output, or expose a workflow through the Workbench command builder.

## Command Design Rules

| Rule | Requirement |
| --- | --- |
| Shape | Commands are `crtk <area> <action> [options] [args]`. |
| Names | Use canonical noun/action tokens from `application.cli.Constants` when a token is reused. |
| Areas | Add a new area only for a durable domain. Prefer adding an action to `fen`, `move`, `engine`, `record`, `puzzle`, `book`, `position`, `batch`, or `config`. |
| Flags | Use named flags for structured values: `--fen`, `--input`, `--output`, `--format`, `--max-nodes`, `--max-duration`. |
| Positionals | Use positionals only when they are natural command arguments, such as a move token or short move line. |
| Outputs | Keep stdout stable and parseable. Put diagnostics on stderr through the command boundary. |
| Determinism | No wall-clock-dependent output, hidden random defaults, unstable ordering, or unbounded engine work. |
| Core logic | Route legality, FEN, SAN, UCI, Chess960, tagging, and move application through `chess.core` and existing helpers. |
| Help | `crtk help <area> <action>` is the ground-truth user surface. Every flag must appear there. |
| Tests | Every command needs regression coverage for success, failure, help, and machine-readable output when supported. |

Never reintroduce legacy single-word commands like `moves`, `bestmove`, `eval-static`, `pgn-to-fens`, `mine-puzzles`, `record-to-csv`, or `stats-tags`. If an alias is unavoidable, make it a compatibility alias that resolves to the canonical command and prints canonical help.

## Source Files

| Need | File |
| --- | --- |
| CLI entry point and dispatch | `src/application/Main.java` |
| Command tree, aliases, examples, related commands | `src/application/cli/CliRegistry.java` |
| Command metadata node | `src/application/cli/CliCommand.java` |
| Shared tokens and flag names | `src/application/cli/Constants.java` |
| Argument parser | `src/utility/Argv.java` |
| Shared command helpers | `src/application/cli/command/CommandSupport.java` |
| User-facing failures | `src/application/cli/command/CommandFailure.java` |
| Built-in help text and option blocks | `src/application/cli/command/HelpCommand.java` |
| Command implementations | `src/application/cli/command/` |
| Book command implementations | `src/application/cli/command/book/` |
| CLI regression tests | `src/testing/CLICommandRegressionTest.java` |
| Docs source | `wiki/command-reference.md`, `wiki/example-commands.md`, `wiki/command-cheatsheet.md` |

Common helper classes:

| Helper | Use |
| --- | --- |
| `CommandSupport.resolveFenArgument(...)` | Resolve `--fen`, `--startpos`, `--randompos`, or positional FEN text. |
| `CommandSupport.resolvePositionArgument(...)` | Resolve and parse one position. |
| `CommandSupport.resolveFenInputs(...)` | Resolve either `--input` or one FEN string. |
| `CommandSupport.resolveOutputMode(...)` | Handle mutually-exclusive `--json` / `--jsonl`. |
| `CommandSupport.resolveDefaultEnabledFlag(...)` | Handle default-on flags with `--flag` and `--no-flag`. |
| `EngineOps`, `EvalOps`, `ConfigOps` | Reuse engine, evaluator, and config plumbing. |
| `PathOps` | Create output paths, local temp files, and safe dump paths. |
| `PgnOps`, `RecordIO` | Reuse PGN and record I/O helpers. |
| `Format` | Reuse CLI formatting for moves, evals, WDL, backend labels, and PVs. |
| `Validation` | Reuse validation helpers instead of scattering range checks. |
| `utility.Json` | Emit JSON/JSONL without adding dependencies. |

## Implementation Process

1. **Name the command.** Pick the canonical area/action path and decide whether it belongs under an existing group.
2. **Define flags and output.** Write down required inputs, optional flags, defaults, output format, failure modes, and whether JSON/JSONL is supported.
3. **Add constants if reused.** Put shared command tokens and option names in `Constants.java`.
4. **Write the command handler.** Add or update a focused class under `src/application/cli/command/`.
5. **Register the command.** Add a `CliCommand.leaf(...)` or group entry in `CliRegistry.java` with usage, about text, detail help key, examples, and related commands.
6. **Add the help block.** Add a marker and option section in `HelpCommand.java`; map the command's `detailHelpKey(...)` to that marker.
7. **Update docs.** Update `wiki/command-reference.md`; update examples/cheatsheet/config docs when the command affects those surfaces.
8. **Expose through Workbench if useful.** Make sure command forms can discover or represent it when the command is user-facing.
9. **Add tests.** Extend `CLICommandRegressionTest` or a more focused regression class.
10. **Run verification.** Compile, run CLI tests, run docs if docs changed, then run the recommended suite before publishing broad changes.

## Handler Pattern

Command handlers are static methods that accept `Argv` and return by printing to stdout or throwing a failure. They do not call `System.exit`.

```java
package application.cli.command;

import application.cli.command.CommandSupport.OutputMode;
import chess.core.Position;
import utility.Argv;
import utility.Json;

/**
 * Example command implementation.
 */
public final class ExampleCommand {

    private ExampleCommand() {
        // utility
    }

    public static void runExample(Argv a) {
        boolean verbose = a.flag("--verbose");
        OutputMode output = CommandSupport.resolveOutputMode(a, "fen example");
        Position position = CommandSupport.resolvePositionArgument(a, "fen example", true, verbose);

        String value = position.toString();
        String json = "{\"fen\":\"" + Json.esc(value) + "\"}";
        if (output == OutputMode.JSON) {
            System.out.println(json);
            return;
        }
        if (output == OutputMode.JSONL) {
            System.out.println(json);
            return;
        }
        System.out.println(value);
    }
}
```

Parsing rules:

- Call typed `Argv` accessors once for every flag you support.
- Call `positionals()` before `ensureConsumed()` only if the command accepts positional arguments.
- Read all non-position flags before `CommandSupport.resolveFenArgument(...)` or `CommandSupport.resolvePositionArgument(...)`; those helpers consume position input and call `ensureConsumed()`.
- Make sure `ensureConsumed()` runs after parsing. Call it yourself unless a shared resolver already did it.
- Reject mutually-exclusive inputs before doing engine/model/file work.
- Prefer `CommandFailure` for command-specific failures with a clear command label and exit code.
- Let `IllegalArgumentException` handle simple parser failures from `Argv`; `Main` maps it to exit code `2`.

Default-on flag pattern:

```java
boolean analyze = CommandSupport.resolveDefaultEnabledFlag(a, "puzzle tags", "--analyze");
```

This accepts `--analyze` and `--no-analyze`, defaults to enabled, and rejects contradictory repeats.

## Registry Pattern

Register every executable command in `CliRegistry.java`. The registry controls dispatch, summary help, contextual help, aliases, examples, related commands, and command form discovery.

```java
fen.add(CliCommand.leaf("example", "Print a deterministic example for a FEN",
        ExampleCommand::runExample)
        .detailHelpKey("fen example")
        .usage("[options]")
        .about("Resolve one position and print a deterministic example value.")
        .example("crtk fen example --startpos")
        .example("crtk fen example --fen \"<FEN>\" --json")
        .related("fen print")
        .related("move list"));
```

Registry rules:

- The leaf name is the canonical action token.
- `summary` is one short sentence fragment for grouped help.
- `usage(...)` is only the tail after the command path.
- `about(...)` explains the command in one or two direct sentences.
- `detailHelpKey(...)` must match the help marker mapping in `HelpCommand`.
- `example(...)` entries must be copy-pasteable and deterministic.
- `related(...)` entries are command paths without the `crtk` prefix.
- Use aliases sparingly and only under the same parent. Aliases are for compatibility, not new naming.

## Help Block Pattern

For each command with options, update `HelpCommand.java`.

1. Add a marker constant:

```java
private static final String FEN_EXAMPLE_OPTIONS_MARKER = "fen example options:";
```

2. Add it to `HELP_MARKERS`:

```java
Map.entry("fen example", FEN_EXAMPLE_OPTIONS_MARKER),
```

3. Add a section to `HELP_FULL_TEXT`:

```text
fen example options:
  --fen FEN                  Position to inspect
  --startpos                 Use the standard starting position
  --randompos                Use a deterministic random playable sample
  --json                     Emit one JSON object
  --jsonl                    Emit one JSON object per row
  --verbose                  Include debug details for parse failures
```

Help block rules:

- The marker line must exactly match the mapped marker string.
- Option rows use stable spacing and short descriptions.
- The help block must list every public flag and short alias.
- If the command supports JSON/JSONL, say so.
- If a command has aliases, contextual help should still show the canonical command path.
- Run `crtk help <area> <action>` after the change and read it as a user would.

## Input Rules

| Input type | Preferred API | Rule |
| --- | --- | --- |
| One FEN | `CommandSupport.resolveFenArgument(...)` | Accept `--fen`, `--startpos`, `--randompos`, and optional positional FEN only when the command convention allows it. |
| Parsed position | `CommandSupport.resolvePositionArgument(...)` | Parse once, fail early, and pass `Position` through the shared core. |
| Many FENs | `CommandSupport.resolveFenInputs(...)` | Accept either `--input` or one FEN, not both. |
| File path | `a.path(...)` / `a.pathRequired(...)` | Use `PathOps.ensureParentDir(...)` before writing. |
| Duration | `a.duration(...)` | Accept `500ms`, `60s`, `2m`, `1h`, or milliseconds. |
| Repeated values | `a.strings(...)` | Preserve order; document whether repeated flags are allowed. |
| Free-form tail | `a.positionals()` | Require `--` when a value could look like a flag. |

Do not parse FENs, SAN, UCI, PGN, TOML, JSON, paths, or durations by hand when a shared parser exists.

## Output Rules

Text output:

- Keep line order stable.
- Keep columns and separators stable.
- Prefer tabs for simple machine-readable text rows.
- Do not print progress or warnings to stdout if stdout is the data stream.
- Include enough context for a human to understand the result without hidden state.

JSON/JSONL output:

- Use `utility.Json`.
- Keep field names lowercase, stable, and explicit.
- Use JSON for one object or summary; use JSONL for rows.
- Do not mix human prose with JSON stdout.
- Add regression assertions for important fields, not just substring smoke tests.

Errors:

- Use `CommandFailure` for domain failures: invalid combination, missing input, unreadable file, unsupported format.
- Use exit code `2` for usage/input failures unless an existing command family uses a more specific code.
- Keep stderr concise. Include `--verbose` only when stack traces or internal details are useful.

## Determinism Checklist

Before shipping a command, verify:

- Same input and flags produce the same stdout.
- Row ordering is sorted or follows core deterministic order.
- Engine work is bounded by nodes, depth, duration, or explicit user budget.
- Random sampling is explicit, seeded, or named as random in the command.
- Output paths are deterministic or user-provided.
- JSON field order is stable enough for regression tests.
- No wall-clock timestamp appears unless the user requested a run log or artifact metadata.
- Optional engines/models report availability honestly and fail or fall back consistently.

## Workbench Command Forms

The Workbench command builder mirrors the CLI. A new command should be easy to discover from the GUI when it is user-facing.

Check these files when a command should appear in forms:

| File | Why |
| --- | --- |
| `src/application/gui/workbench/command/CommandTemplates.java` | Command templates and generated forms. |
| `src/application/gui/workbench/command/CommandForm.java` | Required fields, optional flags, validation, and preview behavior. |
| `src/application/gui/workbench/command/CommandRunner.java` | Running command text and collecting output. |
| `src/testing/WorkbenchCommandRegression.java` | Command-form regression coverage. |

The generated command preview must be the exact runnable command. Do not add UI-only defaults that the CLI cannot reproduce.

## Regression Tests

Use `TestSupport.runMain(...)` for successful command checks and `TestSupport.runMainExpectFailure(...)` for failures.

```java
private static void testFenExampleCommand() {
    String text = TestSupport.runMain("fen", "example", "--startpos").strip();
    assertTrue(text.contains(" w "), "fen example prints a FEN-like value");

    String json = TestSupport.runMain("fen", "example", "--startpos", "--json").strip();
    assertTrue(json.contains("\"fen\""), "fen example json includes fen field");

    TestSupport.FailureResult failure = TestSupport.runMainExpectFailure(
            "fen", "example", "--json", "--jsonl");
    assertTrue(failure.stderr().contains("use either --json or --jsonl"),
            "fen example rejects conflicting output modes");
}
```

Minimum coverage:

| Case | What to test |
| --- | --- |
| Routing | `crtk <area> <action>` reaches the handler. |
| Help | `crtk help <area> <action>` lists the command and new flags. |
| Success | A simple deterministic invocation returns exact or strongly asserted output. |
| Failure | Missing input, bad value, and mutually-exclusive flags fail cleanly. |
| JSON/JSONL | Machine-readable output is valid and stable when supported. |
| Unknown flags | `ensureConsumed()` rejects stale options. |
| Aliases | Alias routes to the same command only when compatibility requires it. |
| Docs-sensitive commands | Command appears in command reference/examples when user-facing. |

## Verification Commands

Compile:

```bash
find src -name '*.java' | sort > /tmp/crtk-srcs.txt
javac --release 17 -d out @/tmp/crtk-srcs.txt
```

Run focused CLI tests:

```bash
java -cp out testing.CLICommandRegressionTest
./scripts/run_regression_suite.sh cli
```

Check the new command manually:

```bash
java -cp out application.Main help <area> <action>
java -cp out application.Main <area> <action> --help
java -cp out application.Main <area> <action> [options]
java -cp out application.Main <area> <action> [bad-options]
```

Regenerate docs after wiki changes:

```bash
./scripts/run_regression_suite.sh docs
git diff --check
```

Before publishing a broad CLI change:

```bash
./scripts/run_regression_suite.sh recommended
```

## Review Checklist

A CLI command change is ready only if every answer is clear:

| Question | Pass condition |
| --- | --- |
| What is the canonical path? | It is `crtk <area> <action>` and not a legacy single-word command. |
| Where is it registered? | `CliRegistry` has the group/leaf metadata, examples, detail help key, and related commands. |
| How are args parsed? | `Argv` typed accessors and shared helpers are used; `ensureConsumed()` is called. |
| What core code does it use? | Rules, notation, engine, records, config, and paths go through shared APIs. |
| How does it fail? | Bad input throws `CommandFailure` or parser errors before expensive work. |
| What does stdout mean? | Text/JSON/JSONL shapes are stable and free of diagnostics. |
| Is work bounded? | Search, mining, export, and batch work have explicit limits or user-provided budgets. |
| Is help current? | `crtk help <area> <action>` lists all flags and useful examples. |
| Are docs current? | Command reference and examples are updated when user-facing behavior changed. |
| Is it tested? | Success, failure, help, routing, and machine-readable output are covered. |

## Related Pages

- [Command Reference](command-reference.md) - every current command, action, and flag.
- [Command Cheatsheet](command-cheatsheet.md) - common commands by task.
- [Example Commands](example-commands.md) - end-to-end command recipes.
- [Architecture](architecture.md) - how the CLI sits over the shared core.
- [Development Notes](development-notes.md) - broader contributor conventions.
- [Quality and Testing](quality-and-testing.md) - regression suite details.
