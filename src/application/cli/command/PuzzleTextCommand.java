package application.cli.command;

import static application.cli.Constants.OPT_ANALYZE;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_HASH;
import static application.cli.Constants.OPT_INCLUDE_FEN;
import static application.cli.Constants.OPT_MAX_DURATION;
import static application.cli.Constants.OPT_MAX_NODES;
import static application.cli.Constants.OPT_MULTIPV;
import static application.cli.Constants.OPT_NODES;
import static application.cli.Constants.OPT_NO_ANALYZE;
import static application.cli.Constants.OPT_NO_WDL;
import static application.cli.Constants.OPT_PROTOCOL_PATH;
import static application.cli.Constants.OPT_PROTOCOL_PATH_SHORT;
import static application.cli.Constants.OPT_PV_PLIES;
import static application.cli.Constants.OPT_TAG_MULTIPV;
import static application.cli.Constants.OPT_THREADS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_WDL;
import static application.cli.EngineOps.analysePositionOrExit;
import static application.cli.EngineOps.configureEngine;
import static application.cli.EngineOps.parsePositionOrNull;
import static application.cli.EngineOps.resolveWdlFlag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import application.Config;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;
import chess.nn.t5.BinLoader;
import chess.nn.t5.Model;
import chess.nn.t5.Runner;
import chess.nn.t5.TagPrompt;
import chess.tag.Sort;
import chess.tag.Tagging;
import chess.uci.Analysis;
import chess.uci.Engine;
import chess.uci.Protocol;
import utility.Argv;
import utility.Json;

/**
 * Runs T5 inference over a full puzzle line (main PV + variations).
 */
public final class PuzzleTextCommand {

    /**
     * Current command label used in diagnostics.
     */
    private static final String COMMAND_LABEL = "puzzle text";

     /**
     * Creates a new puzzle text command instance.
     */
     private PuzzleTextCommand() {
        // utility
    }

     /**
     * Runs the puzzle text workflow.
     * @param a a
     */
     public static void runPuzzleText(Argv a) {
        PuzzleTextOptions opts = parseOptions(a);
        Position root = parsePositionOrExit(opts.inputConfig.fen, opts.flags.verbose);
        Model model = loadModelOrExit(opts.inputConfig.modelPath, opts.flags.verbose);
        Protocol protocol = EngineSupport.loadProtocolOrExit(opts.engineConfig.protoPath, opts.flags.verbose);
        Optional<Boolean> wdlFlag = resolveWdlFlag(opts.wdlConfig.wdl, opts.wdlConfig.noWdl);

        try (Engine engine = new Engine(protocol); Runner runner = new Runner(model)) {
            List<chess.struct.Record> records = buildRecordsOrExit(root, engine, opts, wdlFlag);
            if (opts.flags.analyzeTags) {
                configureTagAnalysis(engine, opts, wdlFlag);
            }
            emitSummaries(records, opts, engine, runner);
        } catch (Exception ex) {
            System.err.println(COMMAND_LABEL + ": inference failed: " + ex.getMessage());
            if (opts.flags.verbose) {
                ex.printStackTrace(System.err);
            }
            System.exit(2);
        }
    }

     /**
     * Loads the T5 model or exits with a command error.
     * @param modelPath model path
     * @param verbose verbose mode flag
     * @return loaded model
     */
     private static Model loadModelOrExit(String modelPath, boolean verbose) {
        try {
            return BinLoader.load(modelPath);
        } catch (Exception ex) {
            System.err.println(COMMAND_LABEL + ": failed to load model: " + ex.getMessage());
            if (verbose) {
                ex.printStackTrace(System.err);
            }
            System.exit(2);
            return null;
        }
    }

     /**
     * Builds record samples from the root position analysis.
     * @param root root position
     * @param engine engine instance
     * @param opts parsed options
     * @param wdlFlag resolved WDL option
     * @return extracted records
     */
     private static List<chess.struct.Record> buildRecordsOrExit(Position root, Engine engine, PuzzleTextOptions opts,
            Optional<Boolean> wdlFlag) {
        configureEngine(COMMAND_LABEL, engine, opts.engineConfig.threads, opts.engineConfig.hash,
                opts.engineConfig.multipv, wdlFlag);
        Analysis analysis = analysePositionOrExit(engine, root, opts.limits.nodesCap, opts.limits.durMs,
                COMMAND_LABEL, opts.flags.verbose);
        if (analysis == null) {
            return List.of();
        }

        List<chess.struct.Record> records = PuzzleSupport.buildRecords(root, analysis, opts.limits.pvPlies,
                COMMAND_LABEL, opts.flags.verbose);
        if (records.isEmpty()) {
            System.err.println(COMMAND_LABEL + ": no records extracted from PVs");
            System.exit(2);
        }
        return records;
    }

     /**
     * Reconfigures the engine for per-position tag analysis.
     * @param engine engine instance
     * @param opts parsed options
     * @param wdlFlag resolved WDL option
     */
     private static void configureTagAnalysis(Engine engine, PuzzleTextOptions opts, Optional<Boolean> wdlFlag) {
        int tagMultipv = Math.max(1, opts.engineConfig.tagMultipv);
        configureEngine(COMMAND_LABEL, engine, opts.engineConfig.threads, opts.engineConfig.hash, tagMultipv,
                wdlFlag);
    }

     /**
     * Emits generated summaries for all usable records.
     * @param records records extracted from the analyzed PV
     * @param opts parsed options
     * @param engine engine used for optional tag analysis
     * @param runner loaded T5 runner
     */
     private static void emitSummaries(List<chess.struct.Record> records, PuzzleTextOptions opts, Engine engine,
            Runner runner) {
        Map<String, TagEntry> cache = new HashMap<>();
        Engine tagEngine = opts.flags.analyzeTags ? engine : null;
        for (chess.struct.Record rec : records) {
            Position pos = rec.getPosition();
            if (pos != null) {
                List<String> tags = tagsFor(pos, tagEngine, opts, cache);
                if (!tags.isEmpty()) {
                    String prompt = TagPrompt.buildPositionPrompt(tags);
                    String summary = runner.generate(prompt, opts.limits.maxNew);
                    writeSummary(rec, pos, summary, opts.flags.includeFen);
                }
            }
        }
    }

     /**
     * Writes one generated summary in text or JSONL form.
     * @param rec source record
     * @param pos record position
     * @param summary generated summary text
     * @param includeFen whether to emit JSONL with FEN and move metadata
     */
     private static void writeSummary(chess.struct.Record rec, Position pos, String summary, boolean includeFen) {
        if (!includeFen) {
            System.out.println(summary);
            return;
        }
        MoveInfo moveInfo = moveInfoFor(rec, pos);
        StringBuilder sb = new StringBuilder(256).append('{');
        sb.append("\"fen\":\"").append(Json.esc(pos.toString())).append("\",");
        sb.append("\"move_san\":").append(moveInfo == null ? "null" : "\"" + Json.esc(moveInfo.san) + "\"");
        sb.append(',');
        sb.append("\"move_uci\":").append(moveInfo == null ? "null" : "\"" + Json.esc(moveInfo.uci) + "\"");
        sb.append(',');
        sb.append("\"summary\":\"").append(Json.esc(summary)).append("\"}");
        System.out.println(sb.toString());
    }

     /**
     * Infers the move metadata for a record when parent and child positions exist.
     * @param rec source record
     * @param pos child position
     * @return move metadata or null
     */
     private static MoveInfo moveInfoFor(chess.struct.Record rec, Position pos) {
        return rec.getParent() == null ? null : inferMove(rec.getParent(), pos);
    }

     /**
     * Parses the options.
     * @param a a
     * @return computed value
     */
     private static PuzzleTextOptions parseOptions(Argv a) {
        boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
        boolean includeFen = a.flag(OPT_INCLUDE_FEN);
        a.flag(OPT_ANALYZE);
        boolean noAnalyze = a.flag(OPT_NO_ANALYZE);
        String protoPath = CommandSupport.optional(a.string(OPT_PROTOCOL_PATH, OPT_PROTOCOL_PATH_SHORT),
                Config.getProtocolPath());
        long nodesCap = Math.max(1, CommandSupport.optional(a.lng(OPT_MAX_NODES, OPT_NODES), Config.getMaxNodes()));
        long durMs = Math.max(1,
                CommandSupport.optionalDurationMs(a.duration(OPT_MAX_DURATION), Config.getMaxDuration()));
        Integer multipv = a.integer(OPT_MULTIPV);
        Integer tagMultipv = a.integer(OPT_TAG_MULTIPV);
        Integer pvPlies = a.integer(OPT_PV_PLIES);
        Integer threads = a.integer(OPT_THREADS);
        Integer hash = a.integer(OPT_HASH);
        boolean wdl = a.flag(OPT_WDL);
        boolean noWdl = a.flag(OPT_NO_WDL);
        Integer maxNew = a.integer("--max-new");
        String modelPath = CommandSupport.optional(a.string("--model"), Config.getT5ModelPath());

        String fen = a.string(OPT_FEN);
        List<String> rest = a.positionals();
        if (fen == null && !rest.isEmpty()) {
            fen = String.join(" ", rest);
        }
        a.ensureConsumed();
        if (modelPath == null || modelPath.isBlank()) {
            System.err.println(COMMAND_LABEL + ": missing --model and config key t5-model-path is empty");
            System.exit(2);
            return null;
        }

        if (wdl && noWdl) {
            System.err.println(COMMAND_LABEL + ": only one of --wdl or --no-wdl may be set");
            System.exit(2);
        }
        if (fen == null || fen.isBlank()) {
            System.err.println(COMMAND_LABEL + " requires --fen or a positional FEN");
            System.exit(2);
        }
        int mpv = multipv == null ? 3 : Math.max(1, multipv);
        int plies = pvPlies == null ? 12 : Math.max(1, pvPlies);
        int tagMpv = tagMultipv == null ? 1 : Math.max(1, tagMultipv);
        boolean analyzeTags = !noAnalyze;
        int maxOut = maxNew == null ? 128 : Math.max(1, maxNew);
        PuzzleTextOptions.Flags flags = new PuzzleTextOptions.Flags(verbose, includeFen, analyzeTags);
        PuzzleTextOptions.EngineConfig engineConfig = new PuzzleTextOptions.EngineConfig(protoPath, mpv, tagMpv,
                threads, hash);
        PuzzleTextOptions.Limits limits = new PuzzleTextOptions.Limits(nodesCap, durMs, plies, maxOut);
        PuzzleTextOptions.WdlConfig wdlConfig = new PuzzleTextOptions.WdlConfig(wdl, noWdl);
        PuzzleTextOptions.InputConfig inputConfig = new PuzzleTextOptions.InputConfig(modelPath, fen);
        return new PuzzleTextOptions(flags, engineConfig, limits, wdlConfig, inputConfig);
    }

     /**
     * Parses the position or exit.
     * @param fen fen
     * @param verbose verbose
     * @return computed value
     */
     private static Position parsePositionOrExit(String fen, boolean verbose) {
        Position pos = parsePositionOrNull(fen, COMMAND_LABEL, verbose);
        if (pos == null) {
            System.exit(2);
        }
        return pos;
    }

     /**
     * Handles tags for.
     * @param pos pos
     * @param engine engine
     * @param opts opts
     * @param cache cache
     * @return computed value
     */
     private static List<String> tagsFor(Position pos, Engine engine, PuzzleTextOptions opts,
            Map<String, TagEntry> cache) {
        String fen = pos.toString();
        TagEntry cached = cache.get(fen);
        if (cached != null) {
            return cached.tags;
        }
        Analysis analysis = null;
        if (engine != null) {
            analysis = analysePositionOrExit(engine, pos, opts.limits.nodesCap, opts.limits.durMs, COMMAND_LABEL,
                    opts.flags.verbose);
            if (analysis == null) {
                return List.of();
            }
        }
        List<String> tags = Tagging.tags(pos, analysis);
        if (engine != null) {
            List<String> threats = PuzzleTagsCommand.threatTagsForPuzzle(pos, analysis, engine, opts.limits.nodesCap,
                    opts.limits.durMs, opts.flags.verbose);
            if (!threats.isEmpty()) {
                List<String> merged = new ArrayList<>(tags.size() + threats.size());
                merged.addAll(tags);
                merged.addAll(threats);
                PuzzleTagsCommand.overrideInitiativeForPuzzle(merged);
                tags = Sort.sort(merged);
            }
        }
        cache.put(fen, new TagEntry(tags));
        return tags;
    }

     /**
     * Handles infer move.
     * @param parent parent
     * @param child child
     * @return computed value
     */
     private static MoveInfo inferMove(Position parent, Position child) {
        short move = inferMoveCode(parent, child);
        if (move == Move.NO_MOVE) {
            return null;
        }
        String san;
        try {
            san = SAN.toAlgebraic(parent, move);
        } catch (RuntimeException ex) {
            san = Move.toString(move);
        }
        return new MoveInfo(san, Move.toString(move));
    }

     /**
     * Handles infer move code.
     * @param from from
     * @param to to
     * @return computed value
     */
     private static short inferMoveCode(Position from, Position to) {
        long target = to.signatureCore();
        MoveList moves = from.getMoves();
        short found = Move.NO_MOVE;
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.get(i);
            Position candidate = from.copyOf().play(move);
            if (candidate.signatureCore() == target) {
                if (found != Move.NO_MOVE) {
                    return Move.NO_MOVE;
                }
                found = move;
            }
        }
        return found;
    }

     /**
     * Provides puzzle text options behavior.
     */
     private static final class PuzzleTextOptions {
         /**
         * Stores the flags.
         */
         private final Flags flags;
         /**
         * Stores the engine config.
         */
         private final EngineConfig engineConfig;
         /**
         * Stores the limits.
         */
         private final Limits limits;
         /**
         * Stores the wdl config.
         */
         private final WdlConfig wdlConfig;
         /**
         * Stores the input config.
         */
         private final InputConfig inputConfig;

         /**
         * Creates a new puzzle text options instance.
         * @param flags flags
         * @param engineConfig engine config
         * @param limits limits
         * @param wdlConfig wdl config
         * @param inputConfig input config
         */
         private PuzzleTextOptions(Flags flags, EngineConfig engineConfig, Limits limits, WdlConfig wdlConfig,
                InputConfig inputConfig) {
            this.flags = flags;
            this.engineConfig = engineConfig;
            this.limits = limits;
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
             * Stores the include fen.
             */
             private final boolean includeFen;
             /**
             * Stores the analyze tags.
             */
             private final boolean analyzeTags;

             /**
             * Creates a new flags instance.
             * @param verbose verbose
             * @param includeFen include fen
             * @param analyzeTags analyze tags
             */
             private Flags(boolean verbose, boolean includeFen, boolean analyzeTags) {
                this.verbose = verbose;
                this.includeFen = includeFen;
                this.analyzeTags = analyzeTags;
            }
        }

         /**
         * Provides engine config behavior.
         */
         private static final class EngineConfig {
             /**
             * Stores the proto path.
             */
             private final String protoPath;
             /**
             * Stores the multipv.
             */
             private final int multipv;
             /**
             * Stores the tag multipv.
             */
             private final int tagMultipv;
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
             * @param protoPath proto path
             * @param multipv multipv
             * @param tagMultipv tag multipv
             * @param threads threads
             * @param hash hash
             */
             private EngineConfig(String protoPath, int multipv, int tagMultipv, Integer threads, Integer hash) {
                this.protoPath = protoPath;
                this.multipv = multipv;
                this.tagMultipv = tagMultipv;
                this.threads = threads;
                this.hash = hash;
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
             * Stores the pv plies.
             */
             private final int pvPlies;
             /**
             * Stores the max new.
             */
             private final int maxNew;

             /**
             * Creates a new limits instance.
             * @param nodesCap nodes cap
             * @param durMs dur ms
             * @param pvPlies pv plies
             * @param maxNew max new
             */
             private Limits(long nodesCap, long durMs, int pvPlies, int maxNew) {
                this.nodesCap = nodesCap;
                this.durMs = durMs;
                this.pvPlies = pvPlies;
                this.maxNew = maxNew;
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
             * Stores the model path.
             */
             private final String modelPath;
             /**
             * Stores the fen.
             */
             private final String fen;

             /**
             * Creates a new input config instance.
             * @param modelPath model path
             * @param fen fen
             */
             private InputConfig(String modelPath, String fen) {
                this.modelPath = modelPath;
                this.fen = fen;
            }
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
         * Creates a new tag entry instance.
         * @param tags tags
         */
         private TagEntry(List<String> tags) {
            this.tags = tags;
        }
    }

     /**
     * Provides move info behavior.
     */
     private static final class MoveInfo {
         /**
         * Stores the san.
         */
         private final String san;
         /**
         * Stores the uci.
         */
         private final String uci;

         /**
         * Creates a new move info instance.
         * @param san san
         * @param uci uci
         */
         private MoveInfo(String san, String uci) {
            this.san = san;
            this.uci = uci;
        }
    }
}
