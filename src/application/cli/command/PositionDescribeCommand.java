package application.cli.command;

import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_FORMAT;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_JSON;
import static application.cli.Constants.OPT_JSONL;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_RANDOMPOS;
import static application.cli.Constants.OPT_STARTPOS;
import static application.cli.Constants.OPT_STDIN;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import application.Config;
import application.cli.command.CommandSupport.OutputMode;
import chess.core.Position;
import chess.describe.ClassicalPositionDescriptionGenerator;
import chess.describe.PositionDescriptionDetail;
import chess.describe.PositionDescriptionInput;
import chess.describe.T5PositionDescriptionGenerator;
import chess.io.Reader;
import utility.Argv;

/**
 * Implements {@code position describe}.
 */
public final class PositionDescribeCommand {

    /**
     * Stable command name used in diagnostics.
     */
    private static final String COMMAND = "position describe";

    /**
     * Engine selector option.
     */
    private static final String OPT_ENGINE = "--engine";

    /**
     * Detail-level selector option.
     */
    private static final String OPT_DETAIL = "--detail";

    /**
     * Legacy candidate-budget selector.
     */
    private static final String OPT_BUDGET = "--budget";

    /**
     * Candidate-budget selector option.
     */
    private static final String OPT_MAX_CANDIDATES = "--max-candidates";

    /**
     * T5 model-path selector option.
     */
    private static final String OPT_MODEL = "--model";

    /**
     * T5 maximum-new-token selector option.
     */
    private static final String OPT_MAX_NEW = "--max-new";

    /**
     * Prevents instantiation.
     */
    private PositionDescribeCommand() {
        // utility
    }

    /**
     * Handles {@code position describe}.
     *
     * @param a argument parser
     */
    public static void runDescribe(Argv a) {
        Options options = parseOptions(a);
        if (options.engine() == Engine.T5) {
            T5PositionDescriptionGenerator t5 = new T5PositionDescriptionGenerator(options.modelPath(),
                    options.maxNewTokens());
            throw new CommandFailure(COMMAND + ": " + t5.unavailableMessage(), 2);
        }
        List<String> fens = resolveFenRows(options);
        List<Row> rows = new ArrayList<>(fens.size());
        ClassicalPositionDescriptionGenerator generator = new ClassicalPositionDescriptionGenerator();
        for (int i = 0; i < fens.size(); i++) {
            Position position = CommandSupport.parsePositionOrExit(fens.get(i), COMMAND, options.verbose());
            PositionDescriptionInput input = PositionDescriptionInput.from(position);
            String text = generator.generate(input, options.detail(), options.candidateBudget());
            rows.add(new Row(i + 1, "pos-" + (i + 1), input, text));
        }
        write(render(rows, options), options.output(), options.verbose());
    }

    /**
     * Parses CLI options into the immutable execution configuration.
     *
     * @param a argument parser
     * @return parsed options
     */
    private static Options parseOptions(Argv a) {
        boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
        boolean stdin = a.flag(OPT_STDIN);
        Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
        Path output = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
        OutputMode outputMode = outputMode(a);
        Engine engine = Engine.parse(a.string(OPT_ENGINE));
        PositionDescriptionDetail detail = PositionDescriptionDetail.parse(a.string(OPT_DETAIL));
        Integer budget = a.integer(OPT_BUDGET, OPT_MAX_CANDIDATES);
        int candidateBudget = budget == null ? -1 : Math.max(0, budget);
        String model = CommandSupport.trimToNull(a.string(OPT_MODEL));
        Path modelPath = model == null ? Path.of(Config.getT5ModelPath()) : Path.of(model);
        Integer maxNew = a.integer(OPT_MAX_NEW);
        boolean startPos = a.flag(OPT_STARTPOS);
        boolean randomPos = a.flag(OPT_RANDOMPOS);
        String fen = a.string(OPT_FEN);
        List<String> rest = a.positionals();
        a.ensureConsumed();
        String selectedFen = CommandSupport.resolveSelectedFen(COMMAND, fen, rest, startPos, randomPos, false);
        validateInputSelectors(input, stdin, selectedFen);
        return new Options(verbose, stdin, input, output, selectedFen, outputMode, engine, detail, candidateBudget,
                modelPath, maxNew == null ? 128 : Math.max(1, maxNew));
    }

    /**
     * Resolves the requested output mode from legacy and explicit selectors.
     *
     * @param a argument parser
     * @return output mode
     */
    private static OutputMode outputMode(Argv a) {
        String format = CommandSupport.trimToNull(a.string(OPT_FORMAT));
        boolean json = a.flag(OPT_JSON);
        boolean jsonl = a.flag(OPT_JSONL);
        int selectors = (format == null ? 0 : 1) + (json ? 1 : 0) + (jsonl ? 1 : 0);
        if (selectors > 1) {
            throw new CommandFailure(COMMAND + ": use only one of --format, --json, or --jsonl", 2);
        }
        if (json) {
            return OutputMode.JSON;
        }
        if (jsonl) {
            return OutputMode.JSONL;
        }
        if (format == null) {
            return OutputMode.TEXT;
        }
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "text", "txt" -> OutputMode.TEXT;
            case "json" -> OutputMode.JSON;
            case "jsonl", "ndjson" -> OutputMode.JSONL;
            default -> throw new CommandFailure(COMMAND
                    + ": unsupported --format " + format + " (expected text, json, or jsonl)", 2);
        };
    }

    /**
     * Ensures exactly one position source was selected.
     *
     * @param input input file
     * @param stdin true when standard input is selected
     * @param fen selected single-position FEN
     */
    private static void validateInputSelectors(Path input, boolean stdin, String fen) {
        int count = 0;
        count += input == null ? 0 : 1;
        count += stdin ? 1 : 0;
        count += fen == null || fen.isBlank() ? 0 : 1;
        if (count != 1) {
            throw new CommandFailure(COMMAND
                    + ": provide exactly one of --fen, --input, --stdin, --startpos, --randompos, or positional FEN",
                    2);
        }
    }

    /**
     * Reads or returns all requested FEN rows.
     *
     * @param options parsed options
     * @return FEN rows
     */
    private static List<String> resolveFenRows(Options options) {
        if (options.stdin()) {
            return CommandSupport.readStdinLines(COMMAND, options.verbose());
        }
        if (options.input() != null) {
            try {
                List<String> rows = Reader.readFenList(options.input());
                if (rows.isEmpty()) {
                    throw new CommandFailure(COMMAND + ": input file has no FENs", 2);
                }
                return rows;
            } catch (IOException ex) {
                throw new CommandFailure(COMMAND + ": failed to read input: " + ex.getMessage(), ex, 2,
                        options.verbose());
            }
        }
        return List.of(options.fen());
    }

    /**
     * Renders all generated rows in the requested output format.
     *
     * @param rows generated rows
     * @param options parsed options
     * @return rendered output body
     */
    private static String render(List<Row> rows, Options options) {
        return switch (options.outputMode()) {
            case TEXT -> renderText(rows);
            case JSON -> renderJson(rows, options);
            case JSONL -> renderJsonl(rows, options);
        };
    }

    /**
     * Renders generated rows as human-readable text.
     *
     * @param rows generated rows
     * @return text output
     */
    private static String renderText(List<Row> rows) {
        StringBuilder sb = new StringBuilder(rows.size() * 180);
        boolean multi = rows.size() > 1;
        for (Row row : rows) {
            if (multi) {
                sb.append(row.index()).append('\t');
            }
            sb.append(row.description()).append(System.lineSeparator());
        }
        return sb.toString();
    }

    /**
     * Renders generated rows as JSON.
     *
     * @param rows generated rows
     * @param options parsed options
     * @return JSON output
     */
    private static String renderJson(List<Row> rows, Options options) {
        if (rows.size() == 1) {
            return rowJson(rows.get(0), options) + System.lineSeparator();
        }
        List<String> objects = rows.stream().map(row -> rowJson(row, options)).toList();
        return "[" + String.join(",", objects) + "]" + System.lineSeparator();
    }

    /**
     * Renders generated rows as newline-delimited JSON.
     *
     * @param rows generated rows
     * @param options parsed options
     * @return JSONL output
     */
    private static String renderJsonl(List<Row> rows, Options options) {
        return String.join(System.lineSeparator(), rows.stream().map(row -> rowJson(row, options)).toList())
                + System.lineSeparator();
    }

    /**
     * Renders one generated row as a compact JSON object.
     *
     * @param row generated row
     * @param options parsed options
     * @return JSON object
     */
    private static String rowJson(Row row, Options options) {
        return "{\"ok\":true"
                + ",\"index\":" + row.index()
                + ",\"id\":" + CommandSupport.jsonString(row.id())
                + ",\"engine\":" + CommandSupport.jsonString(options.engine().label())
                + ",\"detail\":" + CommandSupport.jsonString(options.detail().label())
                + ",\"fen\":" + CommandSupport.jsonString(row.input().fen())
                + ",\"description\":" + CommandSupport.jsonString(row.description())
                + ",\"input\":" + row.input().toJson()
                + "}";
    }

    /**
     * Writes rendered output to standard output or a file.
     *
     * @param body output body
     * @param output output path, or null for standard output
     * @param verbose true to include command failure stack context
     */
    private static void write(String body, Path output, boolean verbose) {
        if (output == null) {
            System.out.print(body);
            return;
        }
        try {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(output, body, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new CommandFailure(COMMAND + ": failed to write output: " + ex.getMessage(), ex, 2, verbose);
        }
    }

    /**
     * Position-description engine selector.
     */
    private enum Engine {
        /**
         * Deterministic template-based generator.
         */
        CLASSICAL("classical"),

        /**
         * Reserved T5 generator path.
         */
        T5("t5");

        /**
         * Stable CLI label.
         */
        private final String label;

        /**
         * Creates an engine selector.
         *
         * @param label stable CLI label
         */
        Engine(String label) {
            this.label = label;
        }

        /**
         * Returns the stable CLI label.
         *
         * @return CLI label
         */
        String label() {
            return label;
        }

        /**
         * Parses a CLI engine label.
         *
         * @param value raw label
         * @return parsed engine
         */
        static Engine parse(String value) {
            if (value == null || value.isBlank()) {
                return CLASSICAL;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "classical", "static" -> CLASSICAL;
                case "t5" -> T5;
                default -> throw new CommandFailure(COMMAND
                        + ": unsupported --engine " + value + " (expected classical or t5)", 2);
            };
        }
    }

    /**
     * Parsed command options.
     *
     * @param verbose true to include verbose diagnostics
     * @param stdin true when FEN rows come from standard input
     * @param input input file path
     * @param output output file path
     * @param fen selected single-position FEN
     * @param outputMode output format
     * @param engine description engine
     * @param detail requested detail level
     * @param candidateBudget candidate output budget
     * @param modelPath optional model path
     * @param maxNewTokens T5 generation budget
     */
    private record Options(
            boolean verbose,
            boolean stdin,
            Path input,
            Path output,
            String fen,
            OutputMode outputMode,
            Engine engine,
            PositionDescriptionDetail detail,
            int candidateBudget,
            Path modelPath,
            int maxNewTokens) {
    }

    /**
     * Generated output row.
     *
     * @param index one-based input row index
     * @param id stable generated row identifier
     * @param input extracted structured signals
     * @param description generated description text
     */
    private record Row(
            int index,
            String id,
            PositionDescriptionInput input,
            String description) {
    }
}
