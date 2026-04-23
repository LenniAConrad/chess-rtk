package application.cli.command;

import static application.cli.Constants.OPT_ANALYZE;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_HASH;
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
import chess.core.MoveInference;
import chess.core.Position;
import chess.core.SAN;
import chess.tag.Delta;
import chess.tag.Sort;
import chess.tag.Tagging;
import chess.uci.Analysis;
import chess.uci.Evaluation;
import chess.uci.Engine;
import chess.uci.Output;
import chess.uci.Protocol;
import utility.Argv;
import utility.Json;

/**
 * Generates per-move tags (with deltas) for a puzzle line including PV variations.
 */
public final class PuzzleTagsCommand {

    /**
     * Current command label used in diagnostics.
     */
    private static final String COMMAND_LABEL = "puzzle tags";

     /**
     * Creates a new puzzle tags command instance.
     */
     private PuzzleTagsCommand() {
        // utility
    }

     /**
     * Runs the puzzle tags workflow.
     * @param a a
     */
     public static void runPuzzleTags(Argv a) {
        PuzzleOptions opts = parseOptions(a);
        Position root = parsePositionOrExit(opts.fen, opts.verbose);

        Protocol protocol = EngineSupport.loadProtocolOrExit(opts.protoPath, opts.verbose);
        Optional<Boolean> wdlFlag = resolveWdlFlag(opts.wdl, opts.noWdl);

        try (Engine engine = new Engine(protocol)) {
            configureEngine(COMMAND_LABEL, engine, opts.threads, opts.hash, opts.multipv, wdlFlag);
            Analysis analysis = analysePositionOrExit(engine, root, opts.nodesCap, opts.durMs, COMMAND_LABEL,
                    opts.verbose);
            if (analysis == null) {
                return;
            }

            List<chess.struct.Record> records = PuzzleSupport.buildRecords(root, analysis, opts.pvPlies,
                    COMMAND_LABEL, opts.verbose);
            if (records.isEmpty()) {
                System.err.println(COMMAND_LABEL + ": no records extracted from PVs");
                System.exit(2);
            }

            if (opts.analyzeTags) {
                int tagMultipv = Math.max(1, opts.tagMultipv);
                configureEngine(COMMAND_LABEL, engine, opts.threads, opts.hash, tagMultipv, wdlFlag);
            }

            Map<String, TagEntry> cache = new HashMap<>();
            long index = 0;
            for (chess.struct.Record rec : records) {
                Position pos = rec.getPosition();
                if (pos == null) {
                    continue;
                }
                List<String> tags = tagsFor(pos, opts.analyzeTags ? engine : null, opts, cache);
                if (tags == null) {
                    continue;
                }
                Delta delta = null;
                Position parent = rec.getParent();
                if (parent != null) {
                    List<String> parentTags = tagsFor(parent, opts.analyzeTags ? engine : null, opts, cache);
                    if (parentTags != null) {
                        delta = Delta.diff(parentTags, tags);
                    }
                }
                printDeltaJson(index++, rec, tags, delta);
            }
        } catch (Exception ex) {
            System.err.println(COMMAND_LABEL + ": failed to initialize engine: " + ex.getMessage());
            if (opts.verbose) {
                ex.printStackTrace(System.err);
            }
            System.exit(2);
        }
    }

     /**
     * Parses the options.
     * @param a a
     * @return computed value
     */
     private static PuzzleOptions parseOptions(Argv a) {
        boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
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

        String fen = a.string(OPT_FEN);
        List<String> rest = a.positionals();
        if (fen == null && !rest.isEmpty()) {
            fen = String.join(" ", rest);
        }
        a.ensureConsumed();

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
        return new PuzzleOptions(verbose, analyzeTags, protoPath, nodesCap, durMs, mpv, tagMpv, plies, threads, hash,
                wdl, noWdl, fen);
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
     private static List<String> tagsFor(Position pos, Engine engine, PuzzleOptions opts, Map<String, TagEntry> cache) {
        String fen = pos.toString();
        TagEntry cached = cache.get(fen);
        if (cached != null) {
            return cached.tags;
        }
        Analysis analysis = null;
        if (engine != null) {
            analysis = analysePositionOrExit(engine, pos, opts.nodesCap, opts.durMs, COMMAND_LABEL, opts.verbose);
            if (analysis == null) {
                return null;
            }
        }
        List<String> tags = Tagging.tags(pos, analysis);
        if (engine != null) {
            List<String> threats = threatTagsForPuzzle(pos, analysis, engine, opts.nodesCap, opts.durMs, opts.verbose);
            if (!threats.isEmpty()) {
                List<String> merged = new ArrayList<>(tags.size() + threats.size());
                merged.addAll(tags);
                merged.addAll(threats);
                overrideInitiativeForPuzzle(merged);
                tags = Sort.sort(merged);
            }
        }
        cache.put(fen, new TagEntry(tags));
        return tags;
    }

     /**
     * Handles override initiative for puzzle.
     * @param tags tags
     */
     static void overrideInitiativeForPuzzle(List<String> tags) {
        boolean threatWhite = false;
        boolean threatBlack = false;
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String trimmed = tag.trim();
            if (!trimmed.startsWith("THREAT:")) {
                continue;
            }
            if (trimmed.contains("side=white")) {
                threatWhite = true;
            } else if (trimmed.contains("side=black")) {
                threatBlack = true;
            }
        }
        if (!threatWhite && !threatBlack) {
            return;
        }
        tags.removeIf(tag -> tag != null && tag.trim().startsWith("INITIATIVE:"));
        String side = threatWhite && !threatBlack ? "white" : (threatBlack && !threatWhite ? "black" : "equal");
        tags.add("INITIATIVE: side=" + side);
    }

     /**
     * Handles threat tags for puzzle.
     * @param base base
     * @param baseAnalysis base analysis
     * @param engine engine
     * @param nodesCap nodes cap
     * @param durMs dur ms
     * @param verbose verbose
     * @return computed value
     */
     static List<String> threatTagsForPuzzle(Position base, Analysis baseAnalysis, Engine engine, long nodesCap,
            long durMs, boolean verbose) {
        if (base.inCheck()) {
            return List.of();
        }
        Position threatPos;
        try {
            threatPos = ThreatsSupport.nullMovePosition(base);
        } catch (IllegalArgumentException ex) {
            if (verbose) {
                System.err.println(COMMAND_LABEL + ": threat analysis skipped: " + ex.getMessage());
            }
            return List.of();
        }
        Analysis threatAnalysis = analysePositionOrExit(engine, threatPos, nodesCap, durMs, COMMAND_LABEL, verbose);
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
        if (Math.abs(threatWhiteCp) >= Math.abs(baseWhiteCp)) {
            return false;
        }
        return true;
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
     * Handles print delta json.
     * @param index index
     * @param rec rec
     * @param tags tags
     * @param delta delta
     */
     private static void printDeltaJson(long index, chess.struct.Record rec, List<String> tags, Delta delta) {
        Position parent = rec.getParent();
        Position pos = rec.getPosition();
        MoveInference.Notation moveInfo = (parent != null && pos != null) ? MoveInference.notation(parent, pos) : null;
        StringBuilder sb = new StringBuilder(256).append('{');
        appendField(sb, "index", Long.toString(index));
        appendField(sb, "game_index", jsonString(null));
        appendField(sb, "parent", jsonString(parent == null ? null : parent.toString()));
        appendField(sb, "fen", jsonString(pos == null ? null : pos.toString()));
        appendField(sb, "move_san", jsonString(moveInfo == null ? null : moveInfo.san()));
        appendField(sb, "move_uci", jsonString(moveInfo == null ? null : moveInfo.uci()));
        appendField(sb, "tags", Json.stringArray(tags.toArray(new String[0])));
        appendField(sb, "delta", delta == null ? "null" : delta.toJson());
        System.out.println(sb.append('}'));
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
     * Provides puzzle options behavior.
     */
     private static final class PuzzleOptions {
         /**
         * Stores the verbose.
         */
         private final boolean verbose;
         /**
         * Stores the analyze tags.
         */
         private final boolean analyzeTags;
         /**
         * Stores the proto path.
         */
         private final String protoPath;
         /**
         * Stores the nodes cap.
         */
         private final long nodesCap;
         /**
         * Stores the dur ms.
         */
         private final long durMs;
         /**
         * Stores the multipv.
         */
         private final int multipv;
         /**
         * Stores the tag multipv.
         */
         private final int tagMultipv;
         /**
         * Stores the pv plies.
         */
         private final int pvPlies;
         /**
         * Stores the threads.
         */
         private final Integer threads;
         /**
         * Stores the hash.
         */
         private final Integer hash;
         /**
         * Stores the wdl.
         */
         private final boolean wdl;
         /**
         * Stores the no wdl.
         */
         private final boolean noWdl;
         /**
         * Stores the fen.
         */
         private final String fen;

         /**
         * Creates a new puzzle options instance.
         * @param verbose verbose
         * @param analyzeTags analyze tags
         * @param protoPath proto path
         * @param nodesCap nodes cap
         * @param durMs dur ms
         * @param multipv multipv
         * @param tagMultipv tag multipv
         * @param pvPlies pv plies
         * @param threads threads
         * @param hash hash
         * @param wdl wdl
         * @param noWdl no wdl
         * @param fen fen
         */
         private PuzzleOptions(boolean verbose, boolean analyzeTags, String protoPath, long nodesCap, long durMs,
                int multipv, int tagMultipv, int pvPlies, Integer threads, Integer hash, boolean wdl, boolean noWdl,
                String fen) {
            this.verbose = verbose;
            this.analyzeTags = analyzeTags;
            this.protoPath = protoPath;
            this.nodesCap = nodesCap;
            this.durMs = durMs;
            this.multipv = multipv;
            this.tagMultipv = tagMultipv;
            this.pvPlies = pvPlies;
            this.threads = threads;
            this.hash = hash;
            this.wdl = wdl;
            this.noWdl = noWdl;
            this.fen = fen;
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
