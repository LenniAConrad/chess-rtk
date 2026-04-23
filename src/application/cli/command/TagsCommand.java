package application.cli.command;

import static application.cli.Constants.CMD_TAGS;
import static application.cli.Constants.OPT_ANALYZE;
import static application.cli.Constants.OPT_DELTA;
import static application.cli.Constants.OPT_HASH;
import static application.cli.Constants.OPT_INCLUDE_FEN;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_MAINLINE;
import static application.cli.Constants.OPT_MAX_DURATION;
import static application.cli.Constants.OPT_MAX_NODES;
import static application.cli.Constants.OPT_MULTIPV;
import static application.cli.Constants.OPT_NODES;
import static application.cli.Constants.OPT_NO_WDL;
import static application.cli.Constants.OPT_PGN;
import static application.cli.Constants.OPT_PROTOCOL_PATH;
import static application.cli.Constants.OPT_PROTOCOL_PATH_SHORT;
import static application.cli.Constants.OPT_SEQUENCE;
import static application.cli.Constants.OPT_SIDELINES;
import static application.cli.Constants.OPT_THREADS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_WDL;
import static application.cli.EngineOps.analysePositionOrExit;
import static application.cli.EngineOps.configureEngine;
import static application.cli.EngineOps.resolveWdlFlag;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import application.Config;
import application.cli.EngineOps;
import application.cli.PgnOps;
import application.console.Bar;
import chess.core.Move;
import chess.core.MoveInference;
import chess.core.Position;
import chess.core.SAN;
import chess.io.Reader;
import chess.struct.Game;
import chess.struct.Record;
import chess.tag.Delta;
import chess.tag.Tagging;
import chess.tag.Sort;
import chess.uci.Analysis;
import chess.uci.Evaluation;
import chess.uci.Engine;
import chess.uci.Output;
import chess.uci.Protocol;
import utility.Argv;
import utility.Json;

/**
 * Implements the {@code tags} CLI command (unified Tag format).
 *
 * @since 2026
 */
public final class TagsCommand {

     /**
     * Creates a new tags command instance.
     */
     private TagsCommand() {
        // utility
    }

     /**
     * Runs the tags workflow.
     * @param a a
     */
     public static void runTags(Argv a) {
        TagsOptions opts = parseOptions(a);
        TagsInputs inputs = loadInputs(opts);
        if (opts.flags.analyze) {
            runWithAnalysis(opts, inputs);
        } else {
            runWithoutAnalysis(opts, inputs);
        }
    }

     /**
     * Parses the options.
     * @param a a
     * @return computed value
     */
     private static TagsOptions parseOptions(Argv a) {
        boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
        Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
        Path pgn = a.path(OPT_PGN);
        boolean analyze = a.flag(OPT_ANALYZE);
        boolean sequence = a.flag(OPT_SEQUENCE);
        boolean delta = a.flag(OPT_DELTA);
        boolean includeFen = a.flag(OPT_INCLUDE_FEN);
        boolean mainline = a.flag(OPT_MAINLINE);
        boolean sidelines = a.flag(OPT_SIDELINES);

        String protoPath = CommandSupport.optional(a.string(OPT_PROTOCOL_PATH, OPT_PROTOCOL_PATH_SHORT),
                Config.getProtocolPath());
        long nodesCap = Math.max(1, CommandSupport.optional(a.lng(OPT_MAX_NODES, OPT_NODES), Config.getMaxNodes()));
        long durMs = Math.max(1,
                CommandSupport.optionalDurationMs(a.duration(OPT_MAX_DURATION), Config.getMaxDuration()));
        Integer multipv = a.integer(OPT_MULTIPV);
        Integer threads = a.integer(OPT_THREADS);
        Integer hash = a.integer(OPT_HASH);
        boolean wdl = a.flag(OPT_WDL);
        boolean noWdl = a.flag(OPT_NO_WDL);

        String fen = a.string(application.cli.Constants.OPT_FEN);
        List<String> rest = a.positionals();
        if (fen == null && !rest.isEmpty()) {
            fen = String.join(" ", rest);
        }
        a.ensureConsumed();

        if (pgn != null && (input != null || fen != null)) {
            System.err.println(CMD_TAGS + ": provide either --pgn or a FEN input, not both");
            System.exit(2);
        }
        if (input != null && fen != null) {
            System.err.println(CMD_TAGS + ": provide either --input or a single FEN, not both");
            System.exit(2);
        }
        if (mainline && sidelines) {
            System.err.println(CMD_TAGS + ": use only one of --mainline or --sidelines");
            System.exit(2);
        }

        TagsOptions.Limits limits = new TagsOptions.Limits(nodesCap, durMs);
        TagsOptions.EngineConfig engineConfig = new TagsOptions.EngineConfig(multipv, threads, hash);
        TagsOptions.WdlConfig wdlConfig = new TagsOptions.WdlConfig(wdl, noWdl);
        TagsOptions.Flags flags = new TagsOptions.Flags(verbose, analyze, sequence, delta, includeFen, mainline,
                sidelines);
        TagsOptions.InputConfig inputConfig = new TagsOptions.InputConfig(fen, input, pgn);
        return new TagsOptions(flags, protoPath, limits, engineConfig, wdlConfig, inputConfig);
    }

     /**
     * Handles load inputs.
     * @param opts opts
     * @return computed value
     */
     private static TagsInputs loadInputs(TagsOptions opts) {
        if (opts.inputConfig.pgn != null) {
            return loadPgnInputs(opts);
        }

        if (opts.inputConfig.input != null) {
            return loadRecordInputs(opts);
        }

        return loadSingleFenInput(opts);
    }

     /**
     * Handles load pgn inputs.
     * @param opts opts
     * @return computed value
     */
     private static TagsInputs loadPgnInputs(TagsOptions opts) {
        List<Game> games = PgnOps.readPgnOrExit(opts.inputConfig.pgn, opts.flags.verbose, CMD_TAGS);
        List<Record> records = new ArrayList<>();
        int gameIndex = 0;
        boolean includeSidelines = includePgnSidelines(opts.flags);
        for (Game game : games) {
            addGameRecords(records, game, gameIndex++, includeSidelines);
        }
        return new TagsInputs(records);
    }

     /**
     * Handles include pgn sidelines.
     * @param flags flags
     * @return computed value
     */
     private static boolean includePgnSidelines(TagsOptions.Flags flags) {
        if (flags.mainline) {
            return false;
        }
        return flags.sidelines;
    }

     /**
     * Handles add game records.
     * @param records records
     * @param game game
     * @param gameIndex game index
     * @param includeSidelines include sidelines
     */
     private static void addGameRecords(List<Record> records, Game game, int gameIndex, boolean includeSidelines) {
        List<Record> extracted = includeSidelines
                ? PgnOps.extractRecordsWithVariations(game)
                : PgnOps.extractRecordsMainline(game);
        for (Record rec : extracted) {
            rec.setDescription(Integer.toString(gameIndex));
            records.add(rec);
        }
    }

     /**
     * Handles load record inputs.
     * @param opts opts
     * @return computed value
     */
     private static TagsInputs loadRecordInputs(TagsOptions opts) {
        List<Record> records = readInputRecordsOrExit(opts);
        if (opts.flags.sequence) {
            applySequenceParents(records);
        }
        return new TagsInputs(records);
    }

     /**
     * Reads the input records or exit.
     * @param opts opts
     * @return computed value
     */
     private static List<Record> readInputRecordsOrExit(TagsOptions opts) {
        try {
            return Reader.readPositionRecords(opts.inputConfig.input);
        } catch (Exception ex) {
            System.err.println(CMD_TAGS + ": failed to read input: " + ex.getMessage());
            if (opts.flags.verbose) {
                ex.printStackTrace(System.err);
            }
            System.exit(2);
            return List.of();
        }
    }

     /**
     * Handles apply sequence parents.
     * @param records records
     */
     private static void applySequenceParents(List<Record> records) {
        Position prev = null;
        for (Record rec : records) {
            if (rec.getParent() == null && prev != null) {
                rec.withParent(prev.copy());
            }
            Position current = rec.getPosition();
            prev = current != null ? current.copy() : null;
        }
    }

     /**
     * Handles load single fen input.
     * @param opts opts
     * @return computed value
     */
     private static TagsInputs loadSingleFenInput(TagsOptions opts) {
        if (opts.inputConfig.fen == null || opts.inputConfig.fen.isBlank()) {
            System.err.println(CMD_TAGS + " requires a FEN (use --fen or --input)");
            System.exit(2);
        }
        List<Record> single = new ArrayList<>(1);
        single.add(new Record().withPosition(
                EngineOps.parsePositionOrNull(opts.inputConfig.fen, CMD_TAGS, opts.flags.verbose)));
        return new TagsInputs(single);
    }

     /**
     * Runs the without analysis workflow.
     * @param opts opts
     * @param inputs inputs
     */
     private static void runWithoutAnalysis(TagsOptions opts, TagsInputs inputs) {
        Map<String, List<String>> cache = new HashMap<>();
        long index = 0;
        boolean includeTagFen = opts.flags.includeFen && !opts.flags.delta;
        Bar bar = recordProgressBar(inputs, CMD_TAGS);
        try {
            for (Record rec : inputs.records) {
                try {
                    Position pos = rec.getPosition();
                    if (pos == null) {
                        continue;
                    }
                    List<String> tags = tagsFor(pos, null, cache, includeTagFen);
                    if (opts.flags.delta) {
                        Delta delta = null;
                        Position parent = rec.getParent();
                        if (parent != null) {
                            List<String> parentTags = tagsFor(parent, null, cache, false);
                            delta = Delta.diff(parentTags, tags);
                        }
                        printDeltaJson(index++, rec, tags, delta);
                    } else {
                        System.out.println(Json.stringArray(tags.toArray(new String[0])));
                    }
                } finally {
                    CommandSupport.step(bar);
                }
            }
        } finally {
            CommandSupport.finish(bar);
        }
    }

     /**
     * Runs the with analysis workflow.
     * @param opts opts
     * @param inputs inputs
     */
     private static void runWithAnalysis(TagsOptions opts, TagsInputs inputs) {
        Protocol protocol = EngineSupport.loadProtocolOrExit(opts.protoPath, opts.flags.verbose);
        Optional<Boolean> wdlFlag = resolveWdlFlag(opts.wdlConfig.wdl, opts.wdlConfig.noWdl);
        try (Engine engine = new Engine(protocol)) {
            configureEngine(CMD_TAGS, engine, opts.engineConfig.threads, opts.engineConfig.hash,
                    opts.engineConfig.multipv, wdlFlag);
            Map<String, TagEntry> cache = new HashMap<>();
            long index = 0;
            boolean includeTagFen = opts.flags.includeFen && !opts.flags.delta;
            Bar bar = recordProgressBar(inputs, CMD_TAGS);
            try {
                for (Record rec : inputs.records) {
                    try {
                        index = processAnalyzedRecord(rec, index, engine, opts, cache, includeTagFen);
                    } finally {
                        CommandSupport.step(bar);
                    }
                }
            } finally {
                CommandSupport.finish(bar);
            }
        } catch (Exception ex) {
            System.err.println(CMD_TAGS + ": failed to initialize engine: " + ex.getMessage());
            if (opts.flags.verbose) {
                ex.printStackTrace(System.err);
            }
            System.exit(2);
        }
    }

     /**
     * Handles record progress bar.
     * @param inputs inputs
     * @param label label
     * @return computed value
     */
     private static Bar recordProgressBar(TagsInputs inputs, String label) {
        int size = inputs == null || inputs.records == null ? 0 : inputs.records.size();
        return size > 1 ? new Bar(size, label, false, System.err) : null;
    }

     /**
     * Handles process analyzed record.
     * @param rec rec
     * @param index index
     * @param engine engine
     * @param opts opts
     * @param cache cache
     * @param includeTagFen include tag fen
     * @return computed value
     */
     private static long processAnalyzedRecord(Record rec, long index, Engine engine, TagsOptions opts,
            Map<String, TagEntry> cache, boolean includeTagFen) {
        Position pos = rec.getPosition();
        if (pos == null) {
            return index;
        }
        List<String> tags = tagsFor(pos, engine, opts, cache, includeTagFen);
        if (opts.flags.delta) {
            printDeltaJson(index, rec, tags, deltaFor(rec, tags, engine, opts, cache));
            return index + 1;
        }
        System.out.println(Json.stringArray(tags.toArray(new String[0])));
        return index;
    }

     /**
     * Handles delta for.
     * @param rec rec
     * @param tags tags
     * @param engine engine
     * @param opts opts
     * @param cache cache
     * @return computed value
     */
     private static Delta deltaFor(Record rec, List<String> tags, Engine engine, TagsOptions opts,
            Map<String, TagEntry> cache) {
        Position parent = rec.getParent();
        if (parent == null) {
            return null;
        }
        List<String> parentTags = tagsFor(parent, engine, opts, cache, false);
        return Delta.diff(parentTags, tags);
    }

     /**
     * Handles tags for.
     * @param pos pos
     * @param engine engine
     * @param opts opts
     * @param cache cache
     * @param includeFen include fen
     * @return computed value
     */
     private static List<String> tagsFor(Position pos, Engine engine, TagsOptions opts, Map<String, TagEntry> cache,
            boolean includeFen) {
        String fen = pos.toString();
        TagEntry cached = cache.get(fen);
        if (cached != null) {
            return includeFen ? withFen(cached.tags, fen) : cached.tags;
        }
        Analysis analysis = null;
        if (engine != null) {
            analysis = analysePositionOrExit(engine, pos, opts.limits.nodesCap, opts.limits.durMs, CMD_TAGS,
                    opts.flags.verbose);
            if (analysis == null) {
                return List.of();
            }
        }
        List<String> tags = Tagging.tags(pos, analysis);
        if (engine != null) {
            List<String> threats = threatTags(pos, analysis, engine, opts.limits.nodesCap, opts.limits.durMs,
                    opts.flags.verbose);
            if (!threats.isEmpty()) {
                List<String> merged = new ArrayList<>(tags.size() + threats.size());
                merged.addAll(tags);
                merged.addAll(threats);
                overrideInitiative(merged);
                tags = Sort.sort(merged);
            }
        }
        cache.put(fen, new TagEntry(tags, analysis));
        return includeFen ? withFen(tags, fen) : tags;
    }

     /**
     * Handles tags for.
     * @param pos pos
     * @param analysis analysis
     * @param cache cache
     * @param includeFen include fen
     * @return computed value
     */
     private static List<String> tagsFor(Position pos, Analysis analysis, Map<String, List<String>> cache,
            boolean includeFen) {
        String fen = pos.toString();
        List<String> cached = cache.get(fen);
        if (cached != null) {
            return includeFen ? withFen(cached, fen) : cached;
        }
        List<String> tags = Tagging.tags(pos, analysis);
        cache.put(fen, tags);
        return includeFen ? withFen(tags, fen) : tags;
    }

     /**
     * Handles with fen.
     * @param tags tags
     * @param fen fen
     * @return computed value
     */
     private static List<String> withFen(List<String> tags, String fen) {
        List<String> withFen = new ArrayList<>(tags.size() + 1);
        withFen.add("META: fen=\"" + fen.replace("\"", "\\\"") + "\"");
        withFen.addAll(tags);
        return withFen;
    }

     /**
     * Handles override initiative.
     * @param tags tags
     */
     private static void overrideInitiative(List<String> tags) {
        boolean threatWhite = false;
        boolean threatBlack = false;
        for (String tag : tags) {
            if (tag != null) {
                String trimmed = tag.trim();
                if (trimmed.startsWith("THREAT:")) {
                    if (trimmed.contains("side=white")) {
                        threatWhite = true;
                    } else if (trimmed.contains("side=black")) {
                        threatBlack = true;
                    }
                }
            }
        }
        if (!threatWhite && !threatBlack) {
            return;
        }
        tags.removeIf(tag -> tag != null && tag.trim().startsWith("INITIATIVE:"));
        String side = initiativeSide(threatWhite, threatBlack);
        tags.add("INITIATIVE: side=" + side);
    }

     /**
     * Handles initiative side.
     * @param threatWhite threat white
     * @param threatBlack threat black
     * @return computed value
     */
     private static String initiativeSide(boolean threatWhite, boolean threatBlack) {
        if (threatWhite && !threatBlack) {
            return "white";
        }
        if (threatBlack && !threatWhite) {
            return "black";
        }
        return "equal";
    }

     /**
     * Handles print delta json.
     * @param index index
     * @param rec rec
     * @param tags tags
     * @param delta delta
     */
     private static void printDeltaJson(long index, Record rec, List<String> tags, Delta delta) {
        Position parent = rec.getParent();
        Position pos = rec.getPosition();
        MoveInference.Notation moveInfo = (parent != null && pos != null) ? MoveInference.notation(parent, pos) : null;
        StringBuilder sb = new StringBuilder(256).append('{');
        appendField(sb, "index", Long.toString(index));
        String gameIndex = rec.getDescription();
        appendField(sb, "game_index", jsonString(gameIndex == null || gameIndex.isBlank() ? null : gameIndex));
        appendField(sb, "parent", jsonString(parent == null ? null : parent.toString()));
        appendField(sb, "fen", jsonString(pos == null ? null : pos.toString()));
        appendField(sb, "move_san", jsonString(moveInfo == null ? null : moveInfo.san()));
        appendField(sb, "move_uci", jsonString(moveInfo == null ? null : moveInfo.uci()));
        appendField(sb, "tags", Json.stringArray(tags.toArray(new String[0])));
        appendField(sb, "delta", delta == null ? "null" : delta.toJson());
        System.out.println(sb.append('}'));
    }

     /**
     * Handles threat tags.
     * @param base base
     * @param baseAnalysis base analysis
     * @param engine engine
     * @param nodesCap nodes cap
     * @param durMs dur ms
     * @param verbose verbose
     * @return computed value
     */
     private static List<String> threatTags(Position base, Analysis baseAnalysis, Engine engine, long nodesCap,
            long durMs, boolean verbose) {
        if (base.inCheck()) {
            return List.of();
        }
        Position threatPos;
        try {
            threatPos = ThreatsSupport.nullMovePosition(base);
        } catch (IllegalArgumentException ex) {
            if (verbose) {
                System.err.println(CMD_TAGS + ": threat analysis skipped: " + ex.getMessage());
            }
            return List.of();
        }
        Analysis threatAnalysis = analysePositionOrExit(engine, threatPos, nodesCap, durMs, CMD_TAGS, verbose);
        if (threatAnalysis == null || threatAnalysis.isEmpty()) {
            return List.of();
        }
        Output best = threatAnalysis.getBestOutput();
        if (best == null) {
            return List.of();
        }
        short bestMove = threatAnalysis.getBestMove();
        if (bestMove == Move.NO_MOVE) {
            return List.of();
        }
        String san;
        try {
            san = SAN.toAlgebraic(threatPos, bestMove);
        } catch (RuntimeException ex) {
            san = Move.toString(bestMove);
        }
        String side = base.isWhiteToMove() ? "black" : "white";
        boolean strong = isThreatStrong(best.getEvaluation());
        boolean equalizing = isEqualizingThreat(base, baseAnalysis, threatPos, threatAnalysis);
        if (!strong && !equalizing) {
            return List.of();
        }
        ThreatInfo info = classifyThreatMove(san);
        String line = "THREAT: side=" + side + " severity=" + info.severity + " type=" + info.type
                + " move=\"" + san.replace("\"", "\\\"") + "\"";
        return List.of(line);
    }

     /**
     * Returns whether threat strong.
     * @param eval eval
     * @return true when threat strong
     */
     private static boolean isThreatStrong(Evaluation eval) {
        if (eval == null) {
            return false;
        }
        if (eval.isMate()) {
            return eval.getValue() > 0;
        }
        return eval.getValue() >= Config.getThreatMinCp();
    }

     /**
     * Returns whether equalizing threat.
     * @param base base
     * @param baseAnalysis base analysis
     * @param threatPos threat pos
     * @param threatAnalysis threat analysis
     * @return true when equalizing threat
     */
     private static boolean isEqualizingThreat(Position base, Analysis baseAnalysis, Position threatPos,
            Analysis threatAnalysis) {
        if (baseAnalysis == null || baseAnalysis.isEmpty()) {
            return false;
        }
        Integer baseWhiteCp = evalToWhiteCp(baseAnalysis, base.isWhiteToMove());
        Integer threatWhiteCp = evalToWhiteCp(threatAnalysis, threatPos.isWhiteToMove());
        if (baseWhiteCp == null || threatWhiteCp == null) {
            return false;
        }
        int min = Math.max(0, Config.getThreatEqualizeMinCp());
        int target = Math.max(0, Config.getThreatEqualizeTargetCp());
        if (Math.abs(baseWhiteCp) < min) {
            return false;
        }
        boolean threatByBlack = base.isWhiteToMove();
        if (threatByBlack && baseWhiteCp <= 0) {
            return false;
        }
        if (!threatByBlack && baseWhiteCp >= 0) {
            return false;
        }
        if (Math.abs(threatWhiteCp) > target) {
            return false;
        }
        return Math.abs(threatWhiteCp) < Math.abs(baseWhiteCp);
    }

     /**
     * Handles classify threat move.
     * @param move move
     * @return computed value
     */
     private static ThreatInfo classifyThreatMove(String move) {
        if (move.contains("#")) {
            return new ThreatInfo("immediate", "mate");
        }
        if (move.contains("=")) {
            return new ThreatInfo("soon", "promote");
        }
        if (move.contains("x")) {
            return new ThreatInfo("soon", "material");
        }
        return new ThreatInfo("latent", "tactic");
    }

     /**
     * Handles eval to white cp.
     * @param analysis analysis
     * @param whiteToMove white to move
     * @return computed value
     */
     private static Integer evalToWhiteCp(Analysis analysis, boolean whiteToMove) {
        if (analysis == null) {
            return null;
        }
        Output best = analysis.getBestOutput();
        if (best == null) {
            return null;
        }
        Evaluation eval = best.getEvaluation();
        if (eval == null || !eval.isValid()) {
            return null;
        }
        int value = eval.isMate() ? mateAsCp(eval.getValue()) : eval.getValue();
        return whiteToMove ? value : -value;
    }

     /**
     * Handles mate as cp.
     * @param mateValue mate value
     * @return computed value
     */
     private static int mateAsCp(int mateValue) {
        if (mateValue == 0) {
            return 0;
        }
        return (mateValue > 0 ? 1 : -1) * 100_000;
    }

     /**
     * Handles append field.
     * @param sb sb
     * @param name name
     * @param value value
     */
     private static void appendField(StringBuilder sb, String name, String value) {
        if (sb.length() > 1) {
            sb.append(',');
        }
        sb.append('"').append(name).append("\":").append(value);
    }

     /**
     * Handles json string.
     * @param value value
     * @return computed value
     */
     private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + Json.esc(value) + "\"";
    }

     /**
     * Provides tags inputs behavior.
     */
     private static final class TagsInputs {
         /**
         * Stores the records.
         */
         private final List<Record> records;

         /**
         * Creates a new tags inputs instance.
         * @param records records
         */
         private TagsInputs(List<Record> records) {
            this.records = records;
        }
    }

     /**
     * Provides tag entry behavior.
     */
     private static final class TagEntry {
         /**
         * Stores the tags.
         */
         private final List<String> tags;
         /**
         * Stores the analysis.
         */
         @SuppressWarnings("unused")
        private final Analysis analysis;

         /**
         * Creates a new tag entry instance.
         * @param tags tags
         * @param analysis analysis
         */
         private TagEntry(List<String> tags, Analysis analysis) {
            this.tags = tags;
            this.analysis = analysis;
        }
    }

     /**
     * Provides tags options behavior.
     */
     private static final class TagsOptions {

         /**
         * Stores the flags.
         */
         private final Flags flags;
         /**
         * Stores the proto path.
         */
         private final String protoPath;
         /**
         * Stores the limits.
         */
         private final Limits limits;
         /**
         * Stores the engine config.
         */
         private final EngineConfig engineConfig;
         /**
         * Stores the wdl config.
         */
         private final WdlConfig wdlConfig;
         /**
         * Stores the input config.
         */
         private final InputConfig inputConfig;

         /**
         * Creates a new tags options instance.
         * @param flags flags
         * @param protoPath proto path
         * @param limits limits
         * @param engineConfig engine config
         * @param wdlConfig wdl config
         * @param inputConfig input config
         */
         private TagsOptions(Flags flags, String protoPath, Limits limits, EngineConfig engineConfig,
                WdlConfig wdlConfig, InputConfig inputConfig) {
            this.flags = flags;
            this.protoPath = protoPath;
            this.limits = limits;
            this.engineConfig = engineConfig;
            this.wdlConfig = wdlConfig;
            this.inputConfig = inputConfig;
        }

         /**
         * Provides flags behavior.
         */
         private static final class Flags {
             /**
             * Stores the verbose.
             */
             private final boolean verbose;
             /**
             * Stores the analyze.
             */
             private final boolean analyze;
             /**
             * Stores the sequence.
             */
             private final boolean sequence;
             /**
             * Stores the delta.
             */
             private final boolean delta;
             /**
             * Stores the include fen.
             */
             private final boolean includeFen;
             /**
             * Stores the mainline.
             */
             private final boolean mainline;
             /**
             * Stores the sidelines.
             */
             private final boolean sidelines;

             /**
             * Creates a new flags instance.
             * @param verbose verbose
             * @param analyze analyze
             * @param sequence sequence
             * @param delta delta
             * @param includeFen include fen
             * @param mainline mainline
             * @param sidelines sidelines
             */
             private Flags(boolean verbose, boolean analyze, boolean sequence, boolean delta, boolean includeFen,
                    boolean mainline, boolean sidelines) {
                this.verbose = verbose;
                this.analyze = analyze;
                this.sequence = sequence;
                this.delta = delta;
                this.includeFen = includeFen;
                this.mainline = mainline;
                this.sidelines = sidelines;
            }
        }

         /**
         * Provides limits behavior.
         */
         private static final class Limits {
             /**
             * Stores the nodes cap.
             */
             private final long nodesCap;
             /**
             * Stores the dur ms.
             */
             private final long durMs;

             /**
             * Creates a new limits instance.
             * @param nodesCap nodes cap
             * @param durMs dur ms
             */
             private Limits(long nodesCap, long durMs) {
                this.nodesCap = nodesCap;
                this.durMs = durMs;
            }
        }

         /**
         * Provides engine config behavior.
         */
         private static final class EngineConfig {
             /**
             * Stores the multipv.
             */
             private final Integer multipv;
             /**
             * Stores the threads.
             */
             private final Integer threads;
             /**
             * Stores the hash.
             */
             private final Integer hash;

             /**
             * Creates a new engine config instance.
             * @param multipv multipv
             * @param threads threads
             * @param hash hash
             */
             private EngineConfig(Integer multipv, Integer threads, Integer hash) {
                this.multipv = multipv;
                this.threads = threads;
                this.hash = hash;
            }
        }

         /**
         * Provides wdl config behavior.
         */
         private static final class WdlConfig {
             /**
             * Stores the wdl.
             */
             private final boolean wdl;
             /**
             * Stores the no wdl.
             */
             private final boolean noWdl;

             /**
             * Creates a new wdl config instance.
             * @param wdl wdl
             * @param noWdl no wdl
             */
             private WdlConfig(boolean wdl, boolean noWdl) {
                this.wdl = wdl;
                this.noWdl = noWdl;
            }
        }

         /**
         * Provides input config behavior.
         */
         private static final class InputConfig {
             /**
             * Stores the fen.
             */
             private final String fen;
             /**
             * Stores the input.
             */
             private final Path input;
             /**
             * Stores the pgn.
             */
             private final Path pgn;

             /**
             * Creates a new input config instance.
             * @param fen fen
             * @param input input
             * @param pgn pgn
             */
             private InputConfig(String fen, Path input, Path pgn) {
                this.fen = fen;
                this.input = input;
                this.pgn = pgn;
            }
        }
    }

     /**
     * Provides threat info behavior.
     */
     private static final class ThreatInfo {
         /**
         * Stores the severity.
         */
         private final String severity;
         /**
         * Stores the type.
         */
         private final String type;

         /**
         * Creates a new threat info instance.
         * @param severity severity
         * @param type type
         */
         private ThreatInfo(String severity, String type) {
            this.severity = severity;
            this.type = type;
        }
    }

}
