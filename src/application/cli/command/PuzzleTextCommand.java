package application.cli.command;

import static application.cli.Constants.OPT_ANALYZE;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_HASH;
import static application.cli.Constants.OPT_INCLUDE_FEN;
import static application.cli.Constants.OPT_MAX_DURATION;
import static application.cli.Constants.OPT_MAX_NODES;
import static application.cli.Constants.OPT_MULTIPV;
import static application.cli.Constants.OPT_NODES;
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
import chess.core.MoveInference;
import chess.core.Position;
import chess.nn.t5.BinLoader;
import chess.nn.t5.Model;
import chess.nn.t5.Runner;
import chess.nn.t5.TagPrompt;
import chess.tag.Sort;
import chess.tag.Generator;
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
     * @param a first value
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
        } catch (CommandFailure failure) {
            throw failure;
        } catch (Exception ex) {
            throw new CommandFailure(COMMAND_LABEL + ": inference failed: " + ex.getMessage(),
                    ex, 2, opts.flags.verbose);
        }
    }

     /**
     * Loads the T5 model or exits with a command error.
     * @param modelPath configured model path
     * @param verbose verbose mode flag
     * @return loaded model
     */
     private static Model loadModelOrExit(String modelPath, boolean verbose) {
        try {
            return BinLoader.load(modelPath);
        } catch (Exception ex) {
            throw new CommandFailure(COMMAND_LABEL + ": failed to load model: " + ex.getMessage(),
                    ex, 2, verbose);
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
                COMMAND_LABEL);
        if (records.isEmpty()) {
            throw new CommandFailure(COMMAND_LABEL + ": no records extracted from PVs", 2);
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
        MoveInference.Notation moveInfo = moveInfoFor(rec, pos);
        StringBuilder sb = new StringBuilder(256).append('{');
        sb.append("\"fen\":\"").append(Json.esc(pos.toString())).append("\",");
        sb.append("\"move_san\":").append(moveInfo == null ? "null" : "\"" + Json.esc(moveInfo.san()) + "\"");
        sb.append(',');
        sb.append("\"move_uci\":").append(moveInfo == null ? "null" : "\"" + Json.esc(moveInfo.uci()) + "\"");
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
     private static MoveInference.Notation moveInfoFor(chess.struct.Record rec, Position pos) {
        return rec.getParent() == null ? null : MoveInference.notation(rec.getParent(), pos);
    }

     /**
     * Parses the options.
     * @param a first value
     * @return parsed the options
     */
     private static PuzzleTextOptions parseOptions(Argv a) {
        boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
        boolean includeFen = a.flag(OPT_INCLUDE_FEN);
        boolean analyzeTags = CommandSupport.resolveDefaultEnabledFlag(a, COMMAND_LABEL, OPT_ANALYZE);
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
            throw new CommandFailure(COMMAND_LABEL + ": missing --model and config key t5-model-path is empty", 2);
        }

        if (wdl && noWdl) {
            throw new CommandFailure(COMMAND_LABEL + ": only one of --wdl or --no-wdl may be set", 2);
        }
        if (fen == null || fen.isBlank()) {
            throw new CommandFailure(COMMAND_LABEL + " requires --fen or a positional FEN", 2);
        }
        int mpv = multipv == null ? 3 : Math.max(1, multipv);
        int plies = pvPlies == null ? 12 : Math.max(1, pvPlies);
        int tagMpv = tagMultipv == null ? 1 : Math.max(1, tagMultipv);
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
     * @param fen FEN string
     * @param verbose whether verbose output is enabled
     * @return parsed the position or exit
     */
     private static Position parsePositionOrExit(String fen, boolean verbose) {
        Position pos = parsePositionOrNull(fen, COMMAND_LABEL, verbose);
        if (pos == null) {
            throw new CommandFailure("", 2);
        }
        return pos;
    }

     /**
     * Handles tags for.
     * @param pos chess position
     * @param engine engine name or instance
     * @param opts parsed options
     * @param cache analysis cache
     * @return handles tags for
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
        List<String> tags = Generator.tags(pos, analysis);
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
         * @param flags option flags
         * @param engineConfig source engine config
         * @param limits search limits
         * @param wdlConfig WDL configuration
         * @param inputConfig source input config
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
             * @param verbose whether verbose output is enabled
             * @param includeFen whether to include FEN text
             * @param analyzeTags whether to run engine-backed tag analysis
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
             * @param protoPath external engine protocol path
             * @param multipv requested MultiPV count
             * @param tagMultipv MultiPV count used for tag analysis
             * @param threads worker thread count
             * @param hash engine hash size in megabytes
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
             * @param nodesCap engine node cap
             * @param durMs duration cap in milliseconds
             * @param pvPlies source pv plies
             * @param maxNew maximum number of generated tags
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
             * @param wdl whether WDL output is enabled
             * @param noWdl whether WDL output is disabled
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
             * @param modelPath configured model path
             * @param fen FEN string
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
         * @param tags tag collection
         */
         private TagEntry(List<String> tags) {
            this.tags = tags;
        }
    }

}
