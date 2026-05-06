package application.gui.workbench;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultComboBoxModel;

import chess.core.Setup;

/**
 * Command metadata for the workbench builders.
 */
final class WorkbenchCommandTemplates {

    /**
     * Exclusive group for commands that accept one position source.
     */
    private static final String GROUP_POSITION_SOURCE = "position source";

    /**
     * Exclusive group for commands that accept one batch or position input source.
     */
    private static final String GROUP_INPUT_SOURCE = "input source";

    /**
     * Exclusive group for machine-readable output mode flags.
     */
    private static final String GROUP_OUTPUT_MODE = "output mode";

    /**
     * Exclusive group for move-format selectors.
     */
    private static final String GROUP_MOVE_FORMAT = "move format";

    /**
     * Exclusive group for WDL engine toggles.
     */
    private static final String GROUP_WDL = "wdl toggle";

    /**
     * Exclusive group for built-in evaluator selectors.
     */
    private static final String GROUP_EVALUATOR = "evaluator";

    /**
     * Exclusive group for PGN line-selection flags.
     */
    private static final String GROUP_PGN_LINE = "pgn line";

    /**
     * Exclusive group for right-hand position-diff aliases.
     */
    private static final String GROUP_DIFF_RIGHT = "diff right";

    /**
     * Prevents instantiation.
     */
    private WorkbenchCommandTemplates() {
        // utility
    }

    /**
     * Returns the command-template combo model.
     *
     * @return combo model
     */
    static DefaultComboBoxModel<CommandTemplate> commandModel() {
        return new DefaultComboBoxModel<>(commandTemplates().toArray(CommandTemplate[]::new));
    }

    /**
     * Returns the batch-task combo model.
     *
     * @return combo model
     */
    static DefaultComboBoxModel<BatchTask> batchModel() {
        return new DefaultComboBoxModel<>(batchTasks().toArray(BatchTask[]::new));
    }

    /**
     * Returns command templates.
     *
     * @return command templates
     */
    private static List<CommandTemplate> commandTemplates() {
        return List.of(
                new CommandTemplate("Legal moves", List.of("move", "list"), positionOptions(
                        formatOption("both", true, "uci, san, or both"),
                        formatFlag("--san", false, "Alias for --format san"),
                        formatFlag("--both", false, "Alias for --format both"),
                        outputFlag("--json", false, "Emit a JSON array"),
                        outputFlag("--jsonl", false, "Emit one JSON object per line"),
                        opt("--fields", "both", false, "Output fields: uci, san, or both"),
                        commonNoHeader(),
                        commonQuiet(),
                        commonVerbose())),
                new CommandTemplate("Tags", List.of("fen", "tags"), fenInputOptions(
                        inputSource("--input", false, "Input FEN list path"),
                        inputSource("--pgn", false, "Input PGN path"),
                        flag("--include-fen", false, "Include FEN in output"),
                        flag("--sequence", false, "Interpret input as an ordered line"),
                        flag("--delta", false, "Emit per-move tag deltas"),
                        exclusiveFlag("--mainline", false, "Only export PGN mainline", GROUP_PGN_LINE),
                        exclusiveFlag("--sidelines", false, "Include PGN variations", GROUP_PGN_LINE),
                        flag("--analyze", false, "Run engine analysis to enrich tags"),
                        engineProtocol(),
                        nodesOption(false),
                        durationOption(false),
                        multipvOption(false),
                        threadsOption(false),
                        hashOption(false),
                        wdlFlag("--wdl", false, "Enable WDL output"),
                        wdlFlag("--no-wdl", false, "Disable WDL output"),
                        commonVerbose())),
                new CommandTemplate("Best move", List.of("engine", "bestmove"), positionOptions(
                        positionInputSource("--input", false, "Input FEN file"),
                        formatOption("both", true, "uci, san, or both"),
                        engineProtocol(),
                        nodesOption(false),
                        durationOption(true),
                        multipvOption(false),
                        threadsOption(false),
                        hashOption(false),
                        wdlFlag("--wdl", false, "Enable WDL output"),
                        wdlFlag("--no-wdl", false, "Disable WDL output"),
                        formatFlag("--san", false, "Print SAN output"),
                        formatFlag("--both", false, "Print UCI plus SAN output"),
                        commonVerbose())),
                new CommandTemplate("Analyze", List.of("engine", "analyze"), positionOptions(
                        positionInputSource("--input", false, "Input FEN file"),
                        engineProtocol(),
                        nodesOption(false),
                        durationOption(true),
                        multipvOption(true),
                        threadsOption(false),
                        hashOption(false),
                        wdlFlag("--wdl", false, "Enable WDL output"),
                        wdlFlag("--no-wdl", false, "Disable WDL output"),
                        commonVerbose())),
                new CommandTemplate("Built-in search", List.of("engine", "builtin"), positionOptions(
                        positionInputSource("--input", false, "Input FEN file"),
                        exclusiveOpt("--evaluator", "classical", false, "classical, nnue, or lc0", GROUP_EVALUATOR),
                        exclusiveFlag("--classical", false, "Use classical evaluator", GROUP_EVALUATOR),
                        exclusiveFlag("--nnue", false, "Use NNUE evaluator", GROUP_EVALUATOR),
                        exclusiveFlag("--lc0", false, "Use LC0 evaluator", GROUP_EVALUATOR),
                        opt("--weights", "", false, "NNUE or LC0 weights path"),
                        depthOption(true),
                        nodesOption(false),
                        durationOption(false),
                        opt("--format", "summary", true, "uci-info, uci, san, both, or summary"),
                        commonVerbose())),
                new CommandTemplate("Perft", List.of("engine", "perft"), positionOptions(
                        depthOption(true),
                        flag("--divide", false, "Print per-root-move table"),
                        opt("--format", "detail", false, "detail, table, or stockfish"),
                        threadsOption(true),
                        commonVerbose())),
                new CommandTemplate("Apply move", List.of("move", "after"), withTrailingArgument("e2e4",
                        positionOptions(
                                outputFlag("--json", false, "Emit one JSON object"),
                                outputFlag("--jsonl", false, "Emit one JSON object line"),
                                commonNoHeader(),
                                commonQuiet(),
                                commonVerbose()))),
                new CommandTemplate("Position diff", List.of("position", "diff"), List.of(
                        optSource("--fen", ValueSource.CURRENT_FEN, true, "Left/input FEN"),
                        exclusiveOpt("--other", Setup.getStandardStartFEN(), true, "Right/comparison FEN",
                                GROUP_DIFF_RIGHT),
                        exclusiveOpt("--right", "", false, "Alias for --other", GROUP_DIFF_RIGHT),
                        outputFlag("--json", false, "Emit one JSON object"),
                        outputFlag("--jsonl", false, "Emit one JSON object line"),
                        commonNoHeader(),
                        commonQuiet(),
                        commonVerbose())),
                new CommandTemplate("Generate FENs", List.of("fen", "generate"), generateFenOptions()));
    }

    /**
     * Returns batch tasks.
     *
     * @return batch tasks
     */
    private static List<BatchTask> batchTasks() {
        return List.of(
                new BatchTask("Bestmove batch", true, new WorkflowControls(true, false, false, true),
                        (input, ctx) -> engineBatchArgs("bestmove-batch", input, ctx, false)),
                new BatchTask("Analyze batch", true, new WorkflowControls(true, false, true, true),
                        (input, ctx) -> engineBatchArgs("analyze-batch", input, ctx, true)),
                new BatchTask("Tag FENs", true, new WorkflowControls(false, false, false, false),
                        (input, ctx) -> List.of("fen", "tags", "--input", input.toString())),
                new BatchTask("Perft suite", false, new WorkflowControls(false, true, false, true),
                        (input, ctx) -> List.of("engine", "perft-suite", "--depth",
                                ctx.depth(), "--threads", ctx.threads())),
                new BatchTask("Benchmark", false, new WorkflowControls(false, true, false, false),
                        (input, ctx) -> List.of("engine", "benchmark", "--depth",
                                ctx.depth(), "--iterations", "3")));
    }

    /**
     * Builds an external-engine batch command with shared workbench settings.
     *
     * @param command batch subcommand
     * @param input input file
     * @param ctx current workbench context
     * @param includeMultipv whether to include MultiPV
     * @return command arguments
     */
    private static List<String> engineBatchArgs(String command, Path input, TemplateContext ctx,
            boolean includeMultipv) {
        List<String> args = new ArrayList<>();
        args.add("engine");
        args.add(command);
        args.add("--input");
        args.add(input.toString());
        addOptional(args, "--protocol-path", ctx.protocolPath());
        addOptional(args, "--max-nodes", ctx.nodes());
        args.add("--max-duration");
        args.add(ctx.duration());
        if (includeMultipv) {
            args.add("--multipv");
            args.add(ctx.multipv());
        }
        addOptional(args, "--threads", ctx.threads());
        addOptional(args, "--hash", ctx.hash());
        args.add("--jsonl");
        return List.copyOf(args);
    }

    /**
     * Appends an optional flag/value pair when the value is present.
     *
     * @param args destination args
     * @param flag command flag
     * @param value option value
     */
    private static void addOptional(List<String> args, String flag, String value) {
        if (value != null && !value.isBlank()) {
            args.add(flag);
            args.add(value.trim());
        }
    }

    /**
     * Returns command options plus standard position-source options.
     *
     * @param extras command-specific options
     * @return complete option list
     */
    private static List<CommandOption> positionOptions(CommandOption... extras) {
        List<CommandOption> options = new ArrayList<>();
        options.add(optSourceExclusive("--fen", ValueSource.CURRENT_FEN, true, "Input FEN", GROUP_POSITION_SOURCE));
        options.add(exclusiveFlag("--startpos", false, "Use the standard chess start position", GROUP_POSITION_SOURCE));
        options.add(exclusiveFlag("--randompos", false, "Use a reachable random legal standard position",
                GROUP_POSITION_SOURCE));
        options.addAll(List.of(extras));
        return List.copyOf(options);
    }

    /**
     * Returns command options plus a single-FEN input source.
     *
     * @param extras command-specific options
     * @return complete option list
     */
    private static List<CommandOption> fenInputOptions(CommandOption... extras) {
        List<CommandOption> options = new ArrayList<>();
        options.add(optSourceExclusive("--fen", ValueSource.CURRENT_FEN, true, "Input FEN", GROUP_INPUT_SOURCE));
        options.addAll(List.of(extras));
        return List.copyOf(options);
    }

    /**
     * Adds a trailing positional argument option to an option list.
     *
     * @param defaultValue default argument value
     * @param options base options
     * @return complete option list
     */
    private static List<CommandOption> withTrailingArgument(String defaultValue, List<CommandOption> options) {
        List<CommandOption> copy = new ArrayList<>(options);
        copy.add(new CommandOption("", true, defaultValue, true, ValueSource.STATIC, "Positional move argument"));
        return List.copyOf(copy);
    }

    /**
     * Returns a compact but complete option list for FEN generation.
     *
     * @return command options
     */
    private static List<CommandOption> generateFenOptions() {
        List<CommandOption> options = new ArrayList<>();
        options.add(opt("--output", "workbench-fens", true, "Output directory"));
        options.add(opt("--files", "1", true, "Number of shard files"));
        options.add(opt("--per-file", "100", true, "FENs per shard file"));
        options.add(opt("--batch", "256", true, "Positions per RNG batch"));
        options.add(opt("--chess960-files", "0", true, "First shards seeded from Chess960 starts"));
        options.add(opt("--max-attempts", "5000", true, "Candidate cap per shard"));
        options.add(flag("--ascii", false, "Use an ASCII progress bar"));
        options.add(commonVerbose());
        options.add(opt("--stage", "", false, "endgame, late-endgame, king-pawn, minor, rook, queenless"));
        options.add(flag("--endgame", false, "Queenless positions with at most 14 pieces"));
        options.add(flag("--late-endgame", false, "Queenless positions with at most 8 pieces"));
        options.add(flag("--king-pawn-endgame", false, "No queens, rooks, bishops, or knights"));
        options.add(flag("--minor-endgame", false, "Queenless minor-piece endgames without rooks"));
        options.add(flag("--rook-endgame", false, "Queenless rook endgames without minor pieces"));
        options.add(flag("--queenless", false, "No queens for either side"));
        options.add(flag("--opposite-bishops", false, "Require opposite-colored bishops"));
        options.add(opt("--side", "", false, "white, black, w, or b"));
        options.add(conflictingFlag("--in-check", false, "Side to move is in check",
                "--not-in-check", "--stalemate"));
        options.add(conflictingFlag("--not-in-check", false, "Side to move is not in check",
                "--in-check", "--checkmate"));
        options.add(conflictingFlag("--checkmate", false, "Side to move is checkmated",
                "--not-in-check", "--stalemate"));
        options.add(conflictingFlag("--stalemate", false, "Side to move is stalemated",
                "--in-check", "--checkmate"));
        options.add(flag("--en-passant", false, "Legal en-passant capture is available"));
        options.add(flag("--promotion", false, "Legal promotion is available"));
        options.add(flag("--underpromotion", false, "Legal underpromotion is available"));
        options.add(flag("--capture", false, "Legal capture is available"));
        options.add(flag("--castle-rights", false, "Any castling right is present"));
        options.add(flag("--legal-castle", false, "Legal castling move is available"));
        addRange(options, "--pieces", "--min-pieces", "--max-pieces", "Total piece count");
        addRange(options, "--white-pieces", "--min-white-pieces", "--max-white-pieces", "White piece count");
        addRange(options, "--black-pieces", "--min-black-pieces", "--max-black-pieces", "Black piece count");
        addRange(options, "--pawns", "--min-pawns", "--max-pawns", "Total pawn count");
        addRange(options, "--knights", "--min-knights", "--max-knights", "Total knight count");
        addRange(options, "--bishops", "--min-bishops", "--max-bishops", "Total bishop count");
        addRange(options, "--rooks", "--min-rooks", "--max-rooks", "Total rook count");
        addRange(options, "--queens", "--min-queens", "--max-queens", "Total queen count");
        addRange(options, "--white-pawns", "--min-white-pawns", "--max-white-pawns", "White pawn count");
        addRange(options, "--black-pawns", "--min-black-pawns", "--max-black-pawns", "Black pawn count");
        addRange(options, "--white-knights", "--min-white-knights", "--max-white-knights", "White knight count");
        addRange(options, "--black-knights", "--min-black-knights", "--max-black-knights", "Black knight count");
        addRange(options, "--white-bishops", "--min-white-bishops", "--max-white-bishops", "White bishop count");
        addRange(options, "--black-bishops", "--min-black-bishops", "--max-black-bishops", "Black bishop count");
        addRange(options, "--white-rooks", "--min-white-rooks", "--max-white-rooks", "White rook count");
        addRange(options, "--black-rooks", "--min-black-rooks", "--max-black-rooks", "Black rook count");
        addRange(options, "--white-queens", "--min-white-queens", "--max-white-queens", "White queen count");
        addRange(options, "--black-queens", "--min-black-queens", "--max-black-queens", "Black queen count");
        addRange(options, "--material", "--min-material", "--max-material", "Total material in centipawns");
        addRange(options, "--white-material", "--min-white-material", "--max-white-material",
                "White material in centipawns");
        addRange(options, "--black-material", "--min-black-material", "--max-black-material",
                "Black material in centipawns");
        addRange(options, "--material-diff", "--min-material-diff", "--max-material-diff",
                "White minus Black material centipawns");
        options.add(opt("--max-material-imbalance", "", false, "Maximum absolute material difference"));
        addRange(options, "--legal-moves", "--min-legal-moves", "--max-legal-moves", "Legal move count");
        addRange(options, "--fullmove", "--min-fullmove", "--max-fullmove", "Fullmove number");
        addRange(options, "--halfmove", "--min-halfmove", "--max-halfmove", "Halfmove clock");
        return List.copyOf(options);
    }

    /**
     * Adds exact/min/max numeric range options.
     *
     * @param options destination option list
     * @param exact exact-value flag
     * @param min minimum-value flag
     * @param max maximum-value flag
     * @param description base description
     */
    private static void addRange(List<CommandOption> options, String exact, String min, String max,
            String description) {
        options.add(conflictingOpt(exact, "", false, "Exact " + description, min, max));
        options.add(conflictingOpt(min, "", false, "Minimum " + description, exact));
        options.add(conflictingOpt(max, "", false, "Maximum " + description, exact));
    }

    /**
     * Creates an option that takes a value.
     *
     * @param flag command flag
     * @param value default value
     * @param enabled whether the option starts enabled
     * @param description option description
     * @return option
     */
    private static CommandOption opt(String flag, String value, boolean enabled, String description) {
        return new CommandOption(flag, true, value, enabled, ValueSource.STATIC, description);
    }

    /**
     * Creates an option that is part of a mutual-exclusion group.
     *
     * @param flag command flag
     * @param value default value
     * @param enabled whether the option starts enabled
     * @param description option description
     * @param group exclusive group name
     * @return option
     */
    private static CommandOption exclusiveOpt(String flag, String value, boolean enabled, String description,
            String group) {
        return new CommandOption(flag, true, value, enabled, ValueSource.STATIC, description, group, List.of());
    }

    /**
     * Creates a value option with explicit conflicting flags.
     *
     * @param flag command flag
     * @param value default value
     * @param enabled whether the option starts enabled
     * @param description option description
     * @param conflicts conflicting flags
     * @return option
     */
    private static CommandOption conflictingOpt(String flag, String value, boolean enabled, String description,
            String... conflicts) {
        return new CommandOption(flag, true, value, enabled, ValueSource.STATIC, description, "", List.of(conflicts));
    }

    /**
     * Creates a dynamic value option.
     *
     * @param flag command flag
     * @param source dynamic value source
     * @param enabled whether the option starts enabled
     * @param description option description
     * @return option
     */
    private static CommandOption optSource(String flag, ValueSource source, boolean enabled, String description) {
        return new CommandOption(flag, true, "", enabled, source, description);
    }

    /**
     * Creates a dynamic value option that belongs to a mutual-exclusion group.
     *
     * @param flag command flag
     * @param source dynamic value source
     * @param enabled whether the option starts enabled
     * @param description option description
     * @param group exclusive group name
     * @return option
     */
    private static CommandOption optSourceExclusive(String flag, ValueSource source, boolean enabled,
            String description, String group) {
        return new CommandOption(flag, true, "", enabled, source, description, group, List.of());
    }

    /**
     * Creates a flag option.
     *
     * @param flag command flag
     * @param enabled whether the option starts enabled
     * @param description option description
     * @return option
     */
    private static CommandOption flag(String flag, boolean enabled, String description) {
        return new CommandOption(flag, false, "", enabled, ValueSource.STATIC, description);
    }

    /**
     * Creates a flag that belongs to a mutual-exclusion group.
     *
     * @param flag command flag
     * @param enabled whether the option starts enabled
     * @param description option description
     * @param group exclusive group name
     * @return option
     */
    private static CommandOption exclusiveFlag(String flag, boolean enabled, String description, String group) {
        return new CommandOption(flag, false, "", enabled, ValueSource.STATIC, description, group, List.of());
    }

    /**
     * Creates a flag with explicit conflicting flags.
     *
     * @param flag command flag
     * @param enabled whether the option starts enabled
     * @param description option description
     * @param conflicts conflicting flags
     * @return option
     */
    private static CommandOption conflictingFlag(String flag, boolean enabled, String description,
            String... conflicts) {
        return new CommandOption(flag, false, "", enabled, ValueSource.STATIC, description, "", List.of(conflicts));
    }

    /**
     * Creates an input-source option.
     *
     * @param flag command flag
     * @param enabled whether the option starts enabled
     * @param description option description
     * @return option
     */
    private static CommandOption inputSource(String flag, boolean enabled, String description) {
        return exclusiveOpt(flag, "", enabled, description, GROUP_INPUT_SOURCE);
    }

    /**
     * Creates a position-input source option.
     *
     * @param flag command flag
     * @param enabled whether the option starts enabled
     * @param description option description
     * @return option
     */
    private static CommandOption positionInputSource(String flag, boolean enabled, String description) {
        return exclusiveOpt(flag, "", enabled, description, GROUP_POSITION_SOURCE);
    }

    /**
     * Creates an output-mode flag.
     *
     * @param flag command flag
     * @param enabled whether the option starts enabled
     * @param description option description
     * @return option
     */
    private static CommandOption outputFlag(String flag, boolean enabled, String description) {
        return exclusiveFlag(flag, enabled, description, GROUP_OUTPUT_MODE);
    }

    /**
     * Creates a move-format value option.
     *
     * @param value default value
     * @param enabled whether the option starts enabled
     * @param description option description
     * @return option
     */
    private static CommandOption formatOption(String value, boolean enabled, String description) {
        return exclusiveOpt("--format", value, enabled, description, GROUP_MOVE_FORMAT);
    }

    /**
     * Creates a move-format shortcut flag.
     *
     * @param flag command flag
     * @param enabled whether the option starts enabled
     * @param description option description
     * @return option
     */
    private static CommandOption formatFlag(String flag, boolean enabled, String description) {
        return exclusiveFlag(flag, enabled, description, GROUP_MOVE_FORMAT);
    }

    /**
     * Creates a WDL toggle flag.
     *
     * @param flag command flag
     * @param enabled whether the option starts enabled
     * @param description option description
     * @return option
     */
    private static CommandOption wdlFlag(String flag, boolean enabled, String description) {
        return exclusiveFlag(flag, enabled, description, GROUP_WDL);
    }

    /**
     * Creates the common duration option.
     *
     * @param enabled whether the option starts enabled
     * @return option
     */
    private static CommandOption durationOption(boolean enabled) {
        return optSource("--max-duration", ValueSource.DURATION, enabled, "Max duration per position");
    }

    /**
     * Creates the common depth option.
     *
     * @param enabled whether the option starts enabled
     * @return option
     */
    private static CommandOption depthOption(boolean enabled) {
        return optSource("--depth", ValueSource.DEPTH, enabled, "Search or perft depth");
    }

    /**
     * Creates the common MultiPV option.
     *
     * @param enabled whether the option starts enabled
     * @return option
     */
    private static CommandOption multipvOption(boolean enabled) {
        return optSource("--multipv", ValueSource.MULTIPV, enabled, "Number of PVs");
    }

    /**
     * Creates the common threads option.
     *
     * @param enabled whether the option starts enabled
     * @return option
     */
    private static CommandOption threadsOption(boolean enabled) {
        return optSource("--threads", ValueSource.THREADS, enabled, "Engine or worker threads");
    }

    /**
     * Creates the common protocol option.
     *
     * @return option
     */
    private static CommandOption engineProtocol() {
        return optSource("--protocol-path", ValueSource.PROTOCOL, false, "Engine protocol TOML path");
    }

    /**
     * Creates the common max-nodes option.
     *
     * @param enabled whether the option starts enabled
     * @return option
     */
    private static CommandOption nodesOption(boolean enabled) {
        return optSource("--max-nodes", ValueSource.NODES, enabled, "Max nodes per position");
    }

    /**
     * Creates the common engine hash option.
     *
     * @param enabled whether the option starts enabled
     * @return option
     */
    private static CommandOption hashOption(boolean enabled) {
        return optSource("--hash", ValueSource.HASH, enabled, "Engine hash MB");
    }

    /**
     * Creates the common quiet option.
     *
     * @return option
     */
    private static CommandOption commonQuiet() {
        return flag("--quiet", false, "Suppress non-row chatter where supported");
    }

    /**
     * Creates the common no-header option.
     *
     * @return option
     */
    private static CommandOption commonNoHeader() {
        return flag("--no-header", false, "Accepted for script-friendly consistency");
    }

    /**
     * Creates the common verbose option.
     *
     * @return option
     */
    private static CommandOption commonVerbose() {
        return flag("--verbose", false, "Print stack trace on failure");
    }

    /**
     * Template context.
     *
     * @param fen current FEN
     * @param duration engine search duration
     * @param depth search or perft depth
     * @param multipv MultiPV count
     * @param threads worker or engine thread count
     * @param protocolPath engine protocol TOML path
     * @param nodes max node budget
     * @param hash engine hash MB
     */
    record TemplateContext(String fen, String duration, String depth, String multipv, String threads,
            String protocolPath, String nodes, String hash) {
    }

    /**
     * Dynamic option value source.
     */
    enum ValueSource {
        /**
         * Use the option default value.
         */
        STATIC,

        /**
         * Use the current board FEN.
         */
        CURRENT_FEN,

        /**
         * Use the shared duration value.
         */
        DURATION,

        /**
         * Use the shared depth value.
         */
        DEPTH,

        /**
         * Use the shared MultiPV value.
         */
        MULTIPV,

        /**
         * Use the shared thread-count value.
         */
        THREADS,

        /**
         * Use the shared external-engine protocol path.
         */
        PROTOCOL,

        /**
         * Use the shared node-budget value.
         */
        NODES,

        /**
         * Use the shared engine hash value.
         */
        HASH
    }

    /**
     * Command option metadata.
     *
     * @param flag command flag, or blank for positional arguments
     * @param takesValue whether this option requires a value
     * @param defaultValue static default value
     * @param enabledByDefault whether the option starts enabled
     * @param source dynamic value source
     * @param description option description
     * @param exclusiveGroup named group where only one option may be active
     * @param conflicts explicit conflicting flags
     */
    record CommandOption(String flag, boolean takesValue, String defaultValue, boolean enabledByDefault,
            ValueSource source, String description, String exclusiveGroup, List<String> conflicts) {

        /**
         * Creates command option metadata without exclusivity rules.
         *
         * @param flag command flag, or blank for positional arguments
         * @param takesValue whether this option requires a value
         * @param defaultValue static default value
         * @param enabledByDefault whether the option starts enabled
         * @param source dynamic value source
         * @param description option description
         */
        CommandOption(String flag, boolean takesValue, String defaultValue, boolean enabledByDefault,
                ValueSource source, String description) {
            this(flag, takesValue, defaultValue, enabledByDefault, source, description, "", List.of());
        }

        /**
         * Normalizes optional relationship metadata.
         *
         * @param flag command flag, or blank for positional arguments
         * @param takesValue whether this option requires a value
         * @param defaultValue static default value
         * @param enabledByDefault whether the option starts enabled
         * @param source dynamic value source
         * @param description option description
         * @param exclusiveGroup named group where only one option may be active
         * @param conflicts explicit conflicting flags
         */
        CommandOption {
            exclusiveGroup = exclusiveGroup == null ? "" : exclusiveGroup;
            conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        }

        /**
         * Resolves the initial value for this option.
         *
         * @param context template context
         * @return initial value
         */
        String initialValue(TemplateContext context) {
            return switch (source) {
                case CURRENT_FEN -> context.fen();
                case DURATION -> context.duration();
                case DEPTH -> context.depth();
                case MULTIPV -> context.multipv();
                case THREADS -> context.threads();
                case PROTOCOL -> context.protocolPath();
                case NODES -> context.nodes();
                case HASH -> context.hash();
                case STATIC -> defaultValue;
            };
        }

        /**
         * Returns whether this option conflicts with another option.
         *
         * @param other other option
         * @return true when both options should not be enabled together
         */
        boolean conflictsWith(CommandOption other) {
            if (other == null) {
                return false;
            }
            if (!exclusiveGroup.isBlank() && exclusiveGroup.equals(other.exclusiveGroup())) {
                return true;
            }
            return referencesConflict(other) || other.referencesConflict(this);
        }

        /**
         * Returns whether this option explicitly references another flag as a conflict.
         *
         * @param other other option
         * @return true when the other flag is listed as a conflict
         */
        private boolean referencesConflict(CommandOption other) {
            return !other.flag().isBlank() && conflicts.contains(other.flag());
        }
    }

    /**
     * Tunable controls used by a batch workflow.
     *
     * @param duration whether duration is used
     * @param depth whether depth is used
     * @param multipv whether MultiPV is used
     * @param threads whether thread count is used
     */
    record WorkflowControls(boolean duration, boolean depth, boolean multipv, boolean threads) {
    }

    /**
     * Command template.
     *
     * @param name display name
     * @param baseArgs fixed command arguments
     * @param options command options
     */
    record CommandTemplate(String name, List<String> baseArgs, List<CommandOption> options) {

        /**
         * Returns the combo-box label for this template.
         *
         * @return display label
         */
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Batch task.
     *
     * @param name display name
     * @param usesFenInput whether the task requires a temporary FEN input file
     * @param controls controls used by the task
     * @param builder command argument builder
     */
    record BatchTask(String name, boolean usesFenInput, WorkflowControls controls, BatchBuilder builder) {

        /**
         * Builds command args.
         *
         * @param input input path
         * @param context template context
         * @return args
         */
        List<String> build(Path input, TemplateContext context) {
            return builder.build(input, context);
        }

        /**
         * Returns the combo-box label for this batch task.
         *
         * @return display label
         */
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Batch builder.
     */
    @FunctionalInterface
    interface BatchBuilder {

        /**
         * Builds command args.
         *
         * @param input input path
         * @param context context
         * @return args
         */
        List<String> build(Path input, TemplateContext context);
    }
}
