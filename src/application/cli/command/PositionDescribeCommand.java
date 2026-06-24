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
import chess.core.Position;
import chess.describe.ClassicalPositionDescriptionGenerator;
import chess.describe.EngineEvaluator;
import chess.describe.PositionDescriptionDetail;
import chess.describe.PositionDescriptionInput;
import chess.describe.PositionDescriptionVerifier;
import chess.describe.PositionDescriptionVerifier.Verification;
import chess.describe.PositionDescriptionVerifier.Violation;
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
     * Classical renderer selector option.
     */
    private static final String OPT_ENGINE = "--engine";

    /**
     * Detail-level selector option.
     */
    private static final String OPT_DETAIL = "--detail";

    /**
     * Audience preset selector option.
     */
    private static final String OPT_AUDIENCE = "--audience";

    /**
     * Evaluation-source selector option ({@code static} or {@code engine}).
     */
    private static final String OPT_EVAL = "--eval";

    /**
     * Engine-evaluation search-depth selector option.
     */
    private static final String OPT_EVAL_DEPTH = "--eval-depth";

    /**
     * Machine-readable facts-only output selector.
     */
    private static final String OPT_FACTS_ONLY = "--facts-only";

    /**
     * Legacy candidate-budget selector.
     */
    private static final String OPT_BUDGET = "--budget";

    /**
     * Candidate-budget selector option.
     */
    private static final String OPT_MAX_CANDIDATES = "--max-candidates";

    /**
     * Training-prompt model-path metadata option.
     */
    private static final String OPT_MODEL = "--model";

    /**
     * Training-prompt maximum-new-token metadata option.
     */
    private static final String OPT_MAX_NEW = "--max-new";

    /**
     * Dedicated JSONL format for future position-description model training.
     */
    private static final String FORMAT_TRAINING_JSONL = "training-jsonl";

    /**
     * Stable schema marker for position-description training rows.
     */
    private static final String TRAINING_SCHEMA = "crtk.position_description.training.v1";

    /**
     * Stable schema marker for facts-only position-description rows.
     */
    private static final String FACTS_SCHEMA = "crtk.position_description.facts.v1";

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
        List<String> fens = resolveFenRows(options);
        List<Row> rows = new ArrayList<>(fens.size());
        ClassicalPositionDescriptionGenerator generator = new ClassicalPositionDescriptionGenerator();
        for (int i = 0; i < fens.size(); i++) {
            Position position = CommandSupport.parsePositionOrExit(fens.get(i), COMMAND, options.verbose());
            PositionDescriptionInput input = PositionDescriptionInput.from(position);
            if (options.evalEngine()) {
                input = input.withEvaluation(EngineEvaluator.evaluate(position, options.evalDepth()));
            }
            String text = generator.generate(input, options.detail(), options.candidateBudget());
            Verification verification = PositionDescriptionVerifier.verify(input, text);
            rows.add(new Row(i + 1, "pos-" + (i + 1), input, text, verification));
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
        String format = CommandSupport.trimToNull(a.string(OPT_FORMAT));
        boolean json = a.flag(OPT_JSON);
        boolean jsonl = a.flag(OPT_JSONL);
        boolean explicitOutputFormat = format != null || json || jsonl;
        OutputFormat outputFormat = outputFormat(format, json, jsonl);
        Engine engine = Engine.parse(a.string(OPT_ENGINE));
        Audience audience = Audience.parse(a.string(OPT_AUDIENCE));
        String detailValue = a.string(OPT_DETAIL);
        PositionDescriptionDetail detail = detailValue == null || detailValue.isBlank()
                ? audience.detail()
                : PositionDescriptionDetail.parse(detailValue);
        Integer budget = a.integer(OPT_BUDGET, OPT_MAX_CANDIDATES);
        int candidateBudget = budget == null ? -1 : Math.max(0, budget);
        String model = CommandSupport.trimToNull(a.string(OPT_MODEL));
        Path modelPath = model == null ? Path.of(Config.getT5ModelPath()) : Path.of(model);
        Integer maxNew = a.integer(OPT_MAX_NEW);
        Integer evalDepthBox = a.integer(OPT_EVAL_DEPTH);
        boolean evalEngine = parseEvalEngine(CommandSupport.trimToNull(a.string(OPT_EVAL)), evalDepthBox != null);
        int evalDepth = evalDepthBox == null ? EngineEvaluator.DEFAULT_DEPTH : Math.max(1, evalDepthBox);
        boolean explicitFactsOnly = a.flag(OPT_FACTS_ONLY);
        boolean factsOnly = explicitFactsOnly || audience.factsOnly();
        if (factsOnly && !explicitFactsOnly && !explicitOutputFormat && outputFormat == OutputFormat.TEXT) {
            outputFormat = OutputFormat.JSON;
        }
        boolean startPos = a.flag(OPT_STARTPOS);
        boolean randomPos = a.flag(OPT_RANDOMPOS);
        String fen = a.string(OPT_FEN);
        List<String> rest = a.positionals();
        a.ensureConsumed();
        String selectedFen = CommandSupport.resolveSelectedFen(COMMAND, fen, rest, startPos, randomPos, false);
        validateInputSelectors(input, stdin, selectedFen);
        validateFactsOnly(outputFormat, factsOnly);
        return new Options(verbose, stdin, input, output, selectedFen, outputFormat, engine, detail, candidateBudget,
                modelPath, maxNew == null ? 128 : Math.max(1, maxNew), evalEngine, evalDepth, factsOnly, audience);
    }

    /**
     * Resolves the evaluation source from the {@code --eval} value.
     *
     * <p>
     * Supplying {@code --eval-depth} without an explicit {@code --eval} implies the
     * engine source, since the depth only applies to a real search.
     * </p>
     *
     * @param value raw {@code --eval} value, or null
     * @param depthGiven true when {@code --eval-depth} was supplied
     * @return true to use a real engine-search evaluation
     */
    private static boolean parseEvalEngine(String value, boolean depthGiven) {
        if (value == null) {
            return depthGiven;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "static", "classical" -> false;
            case "engine", "search", "alphabeta" -> true;
            default -> throw new CommandFailure(COMMAND
                    + ": unsupported --eval " + value + " (expected static or engine)", 2);
        };
    }

    /**
     * Resolves the requested output format from legacy and explicit selectors.
     *
     * @param a argument parser
     * @param format requested output format
     * @param json whether JSON output is requested
     * @param jsonl whether JSON Lines output is requested
     * @return output format
     */
    private static OutputFormat outputFormat(String format, boolean json, boolean jsonl) {
        int selectors = (format == null ? 0 : 1) + (json ? 1 : 0) + (jsonl ? 1 : 0);
        if (selectors > 1) {
            throw new CommandFailure(COMMAND + ": use only one of --format, --json, or --jsonl", 2);
        }
        if (json) {
            return OutputFormat.JSON;
        }
        if (jsonl) {
            return OutputFormat.JSONL;
        }
        if (format == null) {
            return OutputFormat.TEXT;
        }
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "text", "txt" -> OutputFormat.TEXT;
            case "json" -> OutputFormat.JSON;
            case "jsonl", "ndjson" -> OutputFormat.JSONL;
            case FORMAT_TRAINING_JSONL, "training", "train-jsonl" -> OutputFormat.TRAINING_JSONL;
            default -> throw new CommandFailure(COMMAND
                    + ": unsupported --format " + format
                    + " (expected text, json, jsonl, or training-jsonl)", 2);
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
     * Ensures facts-only mode stays a structured data contract.
     *
     * @param outputFormat selected output format
     * @param factsOnly true when prose should be suppressed
     */
    private static void validateFactsOnly(OutputFormat outputFormat, boolean factsOnly) {
        if (!factsOnly) {
            return;
        }
        if (outputFormat == OutputFormat.TEXT) {
            throw new CommandFailure(COMMAND + ": --facts-only requires --json or --format jsonl", 2);
        }
        if (outputFormat == OutputFormat.TRAINING_JSONL) {
            throw new CommandFailure(COMMAND + ": --facts-only is not valid with training-jsonl", 2);
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
        return switch (options.outputFormat()) {
            case TEXT -> renderText(rows);
            case JSON -> renderJson(rows, options);
            case JSONL -> renderJsonl(rows, options);
            case TRAINING_JSONL -> renderTrainingJsonl(rows, options);
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
     * Renders deterministic training rows for future T5 distillation.
     *
     * @param rows generated rows
     * @param options parsed options
     * @return newline-delimited training JSON
     */
    private static String renderTrainingJsonl(List<Row> rows, Options options) {
        return String.join(System.lineSeparator(),
                rows.stream().map(row -> trainingRowJson(row, options)).toList())
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
        if (options.factsOnly()) {
            return factsRowJson(row, options);
        }
        return "{\"ok\":true"
                + ",\"index\":" + row.index()
                + ",\"id\":" + CommandSupport.jsonString(row.id())
                + ",\"engine\":" + CommandSupport.jsonString(options.engine().label())
                + ",\"detail\":" + CommandSupport.jsonString(options.detail().label())
                + ",\"audience\":" + CommandSupport.jsonString(options.audience().label())
                + ",\"fen\":" + CommandSupport.jsonString(row.input().fen())
                + ",\"description\":" + CommandSupport.jsonString(row.description())
                + ",\"grounding\":" + groundingJson(row.verification())
                + ",\"input\":" + row.input().toJson()
                + "}";
    }

    /**
     * Renders one generated row as structured facts without prose.
     *
     * @param row generated row
     * @param options parsed options
     * @return JSON object
     */
    private static String factsRowJson(Row row, Options options) {
        return "{\"ok\":true"
                + ",\"schema\":" + CommandSupport.jsonString(FACTS_SCHEMA)
                + ",\"index\":" + row.index()
                + ",\"id\":" + CommandSupport.jsonString(row.id())
                + ",\"engine\":" + CommandSupport.jsonString(options.engine().label())
                + ",\"detail\":" + CommandSupport.jsonString(options.detail().label())
                + ",\"audience\":" + CommandSupport.jsonString(options.audience().label())
                + ",\"fen\":" + CommandSupport.jsonString(row.input().fen())
                + ",\"candidate_budget\":" + candidateBudgetJson(options.candidateBudget())
                + ",\"grounding\":" + groundingJson(row.verification())
                + ",\"facts\":" + row.input().toJson()
                + "}";
    }

    /**
     * Renders one deterministic training row.
     *
     * @param row generated row
     * @param options parsed options
     * @return JSON object
     */
    private static String trainingRowJson(Row row, Options options) {
        T5PositionDescriptionGenerator promptBuilder =
                new T5PositionDescriptionGenerator(options.modelPath(), options.maxNewTokens());
        return "{\"schema\":" + CommandSupport.jsonString(TRAINING_SCHEMA)
                + ",\"index\":" + row.index()
                + ",\"id\":" + CommandSupport.jsonString(row.id())
                + ",\"task\":\"describe_position\""
                + ",\"detail\":" + CommandSupport.jsonString(options.detail().label())
                + ",\"audience\":" + CommandSupport.jsonString(options.audience().label())
                + ",\"fen\":" + CommandSupport.jsonString(row.input().fen())
                + ",\"prompt\":" + CommandSupport.jsonString(promptBuilder.prompt(row.input(), options.detail()))
                + ",\"target\":" + CommandSupport.jsonString(row.description())
                + ",\"classical_text\":" + CommandSupport.jsonString(row.description())
                + ",\"grounding\":" + groundingJson(row.verification())
                + ",\"input\":" + row.input().toJson()
                + ",\"metadata\":" + trainingMetadataJson(options)
                + "}";
    }

    /**
     * Renders the grounding-verifier verdict for machine-readable outputs.
     *
     * @param verification verifier result
     * @return JSON object
     */
    private static String groundingJson(Verification verification) {
        return "{\"grounded\":" + verification.grounded()
                + ",\"violations\":" + violationsJson(verification.violations())
                + "}";
    }

    /**
     * Renders grounding violations.
     *
     * @param violations verifier violations
     * @return JSON array
     */
    private static String violationsJson(List<Violation> violations) {
        if (violations.isEmpty()) {
            return "[]";
        }
        List<String> entries = new ArrayList<>(violations.size());
        for (Violation violation : violations) {
            entries.add("{\"kind\":" + CommandSupport.jsonString(violation.kind())
                    + ",\"message\":" + CommandSupport.jsonString(violation.message())
                    + "}");
        }
        return "[" + String.join(",", entries) + "]";
    }

    /**
     * Renders deterministic metadata shared by all rows in one export.
     *
     * @param options parsed options
     * @return JSON object
     */
    private static String trainingMetadataJson(Options options) {
        return "{\"source\":\"position describe\""
                + ",\"source_engine\":" + CommandSupport.jsonString(options.engine().label())
                + ",\"audience\":" + CommandSupport.jsonString(options.audience().label())
                + ",\"candidate_budget\":" + candidateBudgetJson(options.candidateBudget())
                + ",\"max_new_tokens\":" + options.maxNewTokens()
                + ",\"model_path\":" + CommandSupport.jsonString(options.modelPath().toString())
                + "}";
    }

    /**
     * Renders the candidate budget as JSON.
     *
     * @param budget parsed candidate budget
     * @return number or null
     */
    private static String candidateBudgetJson(int budget) {
        return budget < 0 ? "null" : Integer.toString(budget);
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
     * Position-description renderer selector.
     */
    private enum Engine {
        /**
         * Deterministic template-based generator.
         */
        CLASSICAL("classical");

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
                case "t5" -> throw new CommandFailure(COMMAND
                        + ": --engine t5 is not a position-describe runtime. "
                        + "Use fen text or puzzle text for the T5 tag-to-text runtime; "
                        + "position describe is classical-only.", 2);
                default -> throw new CommandFailure(COMMAND
                        + ": unsupported --engine " + value + " (expected classical)", 2);
            };
        }
    }

    /**
     * Position-description audience presets.
     */
    private enum Audience {
        /**
         * Compact language for a first-pass explanation.
         */
        BEGINNER("beginner", PositionDescriptionDetail.BRIEF, false),

        /**
         * Default practical club-player prose.
         */
        CLUB("club", PositionDescriptionDetail.NORMAL, false),

        /**
         * More complete prose for stronger users.
         */
        ADVANCED("advanced", PositionDescriptionDetail.FULL, false),

        /**
         * Coaching-style prose using the current full-detail signal set.
         */
        COACH("coach", PositionDescriptionDetail.FULL, false),

        /**
         * Research-oriented structured facts without prose by default.
         */
        RESEARCHER("researcher", PositionDescriptionDetail.FULL, true),

        /**
         * ML/data-oriented structured facts without prose by default.
         */
        ML("ml", PositionDescriptionDetail.FULL, true),

        /**
         * Verbose engine-debug prose.
         */
        ENGINE_DEBUG("engine-debug", PositionDescriptionDetail.FULL, false);

        /**
         * Stable CLI label.
         */
        private final String label;

        /**
         * Default detail level.
         */
        private final PositionDescriptionDetail detail;

        /**
         * Whether the preset defaults to facts-only output.
         */
        private final boolean factsOnly;

        /**
         * Creates an audience preset.
         *
         * @param label stable CLI label
         * @param detail default detail level
         * @param factsOnly true to default to structured facts
         */
        Audience(String label, PositionDescriptionDetail detail, boolean factsOnly) {
            this.label = label;
            this.detail = detail;
            this.factsOnly = factsOnly;
        }

        /**
         * Returns the stable CLI label.
         *
         * @return label
         */
        String label() {
            return label;
        }

        /**
         * Returns the preset detail level.
         *
         * @return detail level
         */
        PositionDescriptionDetail detail() {
            return detail;
        }

        /**
         * Returns whether this preset defaults to structured facts.
         *
         * @return true for facts-only presets
         */
        boolean factsOnly() {
            return factsOnly;
        }

        /**
         * Parses a CLI audience label.
         *
         * @param value raw label
         * @return parsed audience
         */
        static Audience parse(String value) {
            if (value == null || value.isBlank()) {
                return CLUB;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "beginner", "new" -> BEGINNER;
                case "club", "default" -> CLUB;
                case "advanced", "expert" -> ADVANCED;
                case "coach", "coaching" -> COACH;
                case "researcher", "research" -> RESEARCHER;
                case "ml", "machine", "data" -> ML;
                case "engine-debug", "debug" -> ENGINE_DEBUG;
                default -> throw new CommandFailure(COMMAND + ": unsupported --audience " + value
                        + " (expected beginner, club, advanced, coach, researcher, ml, or engine-debug)", 2);
            };
        }
    }

    /**
     * Position-description output format.
     */
    private enum OutputFormat {
        /**
         * Existing human-readable text output.
         */
        TEXT,

        /**
         * One JSON value.
         */
        JSON,

        /**
         * One JSON object per position.
         */
        JSONL,

        /**
         * One model-training JSON object per position.
         */
        TRAINING_JSONL
    }

    /**
     * Parsed command options.
     *
     * @param verbose true to include verbose diagnostics
     * @param stdin true when FEN rows come from standard input
     * @param input input file path
     * @param output output file path
     * @param fen selected single-position FEN
     * @param outputFormat source output format
     * @param engine description engine
     * @param detail requested detail level
     * @param candidateBudget candidate output budget
     * @param modelPath optional model path
     * @param maxNewTokens T5 generation budget
     * @param evalEngine true to evaluate with a real engine search
     * @param evalDepth engine-evaluation search depth in plies
     * @param factsOnly true to suppress prose in JSON/JSONL outputs
     * @param audience selected audience preset
     */
    private record Options(
            boolean verbose,
            boolean stdin,
            Path input,
            Path output,
            String fen,
            OutputFormat outputFormat,
            Engine engine,
            PositionDescriptionDetail detail,
            int candidateBudget,
            Path modelPath,
            int maxNewTokens,
            boolean evalEngine,
            int evalDepth,
            boolean factsOnly,
            Audience audience) {
    }

    /**
     * Generated output row.
     *
     * @param index one-based input row index
     * @param id stable generated row identifier
     * @param input extracted structured signals
     * @param description generated description text
     * @param verification grounding verifier result
     */
    private record Row(
            int index,
            String id,
            PositionDescriptionInput input,
            String description,
            Verification verification) {
    }
}
