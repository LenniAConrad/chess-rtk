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
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;
import chess.io.Reader;
import chess.struct.Game;
import chess.struct.Record;
import chess.tag.TagDelta;
import chess.tag.Tagging;
import chess.tag.TagSort;
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

    private TagsCommand() {
        // utility
    }

    public static void runTags(Argv a) {
        TagsOptions opts = parseOptions(a);
        TagsInputs inputs = loadInputs(opts);
        if (opts.flags.analyze) {
            runWithAnalysis(opts, inputs);
        } else {
            runWithoutAnalysis(opts, inputs);
        }
    }

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
        return new TagsOptions(flags, protoPath, limits, engineConfig, wdlConfig, fen, input, pgn);
    }

    private static TagsInputs loadInputs(TagsOptions opts) {
        if (opts.pgn != null) {
            return loadPgnInputs(opts);
        }

        if (opts.input != null) {
            return loadRecordInputs(opts);
        }

        return loadSingleFenInput(opts);
    }

    private static TagsInputs loadPgnInputs(TagsOptions opts) {
        List<Game> games = PgnOps.readPgnOrExit(opts.pgn, opts.flags.verbose, CMD_TAGS);
        List<Record> records = new ArrayList<>();
        int gameIndex = 0;
        for (Game game : games) {
            addGameRecords(records, game, gameIndex++, opts.flags.sidelines);
        }
        return new TagsInputs(records);
    }

    private static void addGameRecords(List<Record> records, Game game, int gameIndex, boolean includeSidelines) {
        List<Record> extracted = includeSidelines
                ? PgnOps.extractRecordsWithVariations(game)
                : PgnOps.extractRecordsMainline(game);
        for (Record rec : extracted) {
            rec.setDescription(Integer.toString(gameIndex));
            records.add(rec);
        }
    }

    private static TagsInputs loadRecordInputs(TagsOptions opts) {
        List<Record> records = readInputRecordsOrExit(opts);
        if (opts.flags.sequence) {
            applySequenceParents(records);
        }
        return new TagsInputs(records);
    }

    private static List<Record> readInputRecordsOrExit(TagsOptions opts) {
        try {
            return Reader.readPositionRecords(opts.input);
        } catch (Exception ex) {
            System.err.println(CMD_TAGS + ": failed to read input: " + ex.getMessage());
            if (opts.flags.verbose) {
                ex.printStackTrace(System.err);
            }
            System.exit(2);
            return List.of();
        }
    }

    private static void applySequenceParents(List<Record> records) {
        Position prev = null;
        for (Record rec : records) {
            if (rec.getParent() == null && prev != null) {
                rec.withParent(prev.copyOf());
            }
            Position current = rec.getPosition();
            prev = current != null ? current.copyOf() : null;
        }
    }

    private static TagsInputs loadSingleFenInput(TagsOptions opts) {
        if (opts.fen == null || opts.fen.isBlank()) {
            System.err.println(CMD_TAGS + " requires a FEN (use --fen or --input)");
            System.exit(2);
        }
        List<Record> single = new ArrayList<>(1);
        single.add(new Record().withPosition(EngineOps.parsePositionOrNull(opts.fen, CMD_TAGS, opts.flags.verbose)));
        return new TagsInputs(single);
    }

    private static void runWithoutAnalysis(TagsOptions opts, TagsInputs inputs) {
        Map<String, List<String>> cache = new HashMap<>();
        long index = 0;
        boolean includeTagFen = opts.flags.includeFen && !opts.flags.delta;
        for (Record rec : inputs.records) {
            Position pos = rec.getPosition();
            if (pos == null) {
                continue;
            }
            List<String> tags = tagsFor(pos, null, cache, includeTagFen);
            if (opts.flags.delta) {
                TagDelta delta = null;
                Position parent = rec.getParent();
                if (parent != null) {
                    List<String> parentTags = tagsFor(parent, null, cache, false);
                    delta = TagDelta.diff(parentTags, tags);
                }
                printDeltaJson(index++, rec, tags, delta);
            } else {
                System.out.println(Json.stringArray(tags.toArray(new String[0])));
            }
        }
    }

    private static void runWithAnalysis(TagsOptions opts, TagsInputs inputs) {
        Protocol protocol = EngineSupport.loadProtocolOrExit(opts.protoPath, opts.flags.verbose);
        Optional<Boolean> wdlFlag = resolveWdlFlag(opts.wdlConfig.wdl, opts.wdlConfig.noWdl);
        try (Engine engine = new Engine(protocol)) {
            configureEngine(CMD_TAGS, engine, opts.engineConfig.threads, opts.engineConfig.hash,
                    opts.engineConfig.multipv, wdlFlag);
            Map<String, TagEntry> cache = new HashMap<>();
            long index = 0;
            boolean includeTagFen = opts.flags.includeFen && !opts.flags.delta;
            for (Record rec : inputs.records) {
                index = processAnalyzedRecord(rec, index, engine, opts, cache, includeTagFen);
            }
        } catch (Exception ex) {
            System.err.println(CMD_TAGS + ": failed to initialize engine: " + ex.getMessage());
            if (opts.flags.verbose) {
                ex.printStackTrace(System.err);
            }
            System.exit(2);
        }
    }

    private static long processAnalyzedRecord(Record rec, long index, Engine engine, TagsOptions opts,
            Map<String, TagEntry> cache, boolean includeTagFen) {
        Position pos = rec.getPosition();
        if (pos == null) {
            return index;
        }
        List<String> tags = tagsFor(pos, engine, opts, cache, includeTagFen);
        if (tags == null) {
            return index;
        }
        if (opts.flags.delta) {
            printDeltaJson(index, rec, tags, deltaFor(rec, tags, engine, opts, cache));
            return index + 1;
        }
        System.out.println(Json.stringArray(tags.toArray(new String[0])));
        return index;
    }

    private static TagDelta deltaFor(Record rec, List<String> tags, Engine engine, TagsOptions opts,
            Map<String, TagEntry> cache) {
        Position parent = rec.getParent();
        if (parent == null) {
            return null;
        }
        List<String> parentTags = tagsFor(parent, engine, opts, cache, false);
        if (parentTags == null) {
            return null;
        }
        return TagDelta.diff(parentTags, tags);
    }

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
                return null;
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
                tags = TagSort.sort(merged);
            }
        }
        cache.put(fen, new TagEntry(tags, analysis));
        return includeFen ? withFen(tags, fen) : tags;
    }

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

    private static List<String> withFen(List<String> tags, String fen) {
        List<String> withFen = new ArrayList<>(tags.size() + 1);
        withFen.add("META: fen=\"" + fen.replace("\"", "\\\"") + "\"");
        withFen.addAll(tags);
        return withFen;
    }

    private static void overrideInitiative(List<String> tags) {
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
        String side = initiativeSide(threatWhite, threatBlack);
        tags.add("INITIATIVE: side=" + side);
    }

    private static String initiativeSide(boolean threatWhite, boolean threatBlack) {
        if (threatWhite && !threatBlack) {
            return "white";
        }
        if (threatBlack && !threatWhite) {
            return "black";
        }
        return "equal";
    }

    private static void printDeltaJson(long index, Record rec, List<String> tags, TagDelta delta) {
        Position parent = rec.getParent();
        Position pos = rec.getPosition();
        MoveInfo moveInfo = (parent != null && pos != null) ? inferMove(parent, pos) : null;
        StringBuilder sb = new StringBuilder(256).append('{');
        appendField(sb, "index", Long.toString(index));
        String gameIndex = rec.getDescription();
        appendField(sb, "game_index", jsonString(gameIndex == null || gameIndex.isBlank() ? null : gameIndex));
        appendField(sb, "parent", jsonString(parent == null ? null : parent.toString()));
        appendField(sb, "fen", jsonString(pos == null ? null : pos.toString()));
        appendField(sb, "move_san", jsonString(moveInfo == null ? null : moveInfo.san));
        appendField(sb, "move_uci", jsonString(moveInfo == null ? null : moveInfo.uci));
        appendField(sb, "tags", Json.stringArray(tags.toArray(new String[0])));
        appendField(sb, "delta", delta == null ? "null" : delta.toJson());
        System.out.println(sb.append('}'));
    }

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
        String side = base.isWhiteTurn() ? "black" : "white";
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

    private static boolean isThreatStrong(Evaluation eval) {
        if (eval == null) {
            return false;
        }
        if (eval.isMate()) {
            return eval.getValue() > 0;
        }
        return eval.getValue() >= Config.getThreatMinCp();
    }

    private static boolean isEqualizingThreat(Position base, Analysis baseAnalysis, Position threatPos,
            Analysis threatAnalysis) {
        if (baseAnalysis == null || baseAnalysis.isEmpty()) {
            return false;
        }
        Integer baseWhiteCp = evalToWhiteCp(baseAnalysis, base.isWhiteTurn());
        Integer threatWhiteCp = evalToWhiteCp(threatAnalysis, threatPos.isWhiteTurn());
        if (baseWhiteCp == null || threatWhiteCp == null) {
            return false;
        }
        int min = Math.max(0, Config.getThreatEqualizeMinCp());
        int target = Math.max(0, Config.getThreatEqualizeTargetCp());
        if (Math.abs(baseWhiteCp) < min) {
            return false;
        }
        boolean threatByBlack = base.isWhiteTurn();
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

    private static int mateAsCp(int mateValue) {
        if (mateValue == 0) {
            return 0;
        }
        return (mateValue > 0 ? 1 : -1) * 100_000;
    }

    private static void appendField(StringBuilder sb, String name, String value) {
        if (sb.length() > 1) {
            sb.append(',');
        }
        sb.append('"').append(name).append("\":").append(value);
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + Json.esc(value) + "\"";
    }

    private static final class TagsInputs {
        private final List<Record> records;

        private TagsInputs(List<Record> records) {
            this.records = records;
        }
    }

    private static final class TagEntry {
        private final List<String> tags;
        @SuppressWarnings("unused")
        private final Analysis analysis;

        private TagEntry(List<String> tags, Analysis analysis) {
            this.tags = tags;
            this.analysis = analysis;
        }
    }

    private static final class TagsOptions {

        private final Flags flags;
        private final String protoPath;
        private final Limits limits;
        private final EngineConfig engineConfig;
        private final WdlConfig wdlConfig;
        private final String fen;
        private final Path input;
        private final Path pgn;

        private TagsOptions(Flags flags, String protoPath, Limits limits, EngineConfig engineConfig,
                WdlConfig wdlConfig, String fen, Path input, Path pgn) {
            this.flags = flags;
            this.protoPath = protoPath;
            this.limits = limits;
            this.engineConfig = engineConfig;
            this.wdlConfig = wdlConfig;
            this.fen = fen;
            this.input = input;
            this.pgn = pgn;
        }

        private static final class Flags {
            private final boolean verbose;
            private final boolean analyze;
            private final boolean sequence;
            private final boolean delta;
            private final boolean includeFen;
            private final boolean mainline;
            private final boolean sidelines;

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

        private static final class Limits {
            private final long nodesCap;
            private final long durMs;

            private Limits(long nodesCap, long durMs) {
                this.nodesCap = nodesCap;
                this.durMs = durMs;
            }
        }

        private static final class EngineConfig {
            private final Integer multipv;
            private final Integer threads;
            private final Integer hash;

            private EngineConfig(Integer multipv, Integer threads, Integer hash) {
                this.multipv = multipv;
                this.threads = threads;
                this.hash = hash;
            }
        }

        private static final class WdlConfig {
            private final boolean wdl;
            private final boolean noWdl;

            private WdlConfig(boolean wdl, boolean noWdl) {
                this.wdl = wdl;
                this.noWdl = noWdl;
            }
        }
    }

    private static final class ThreatInfo {
        private final String severity;
        private final String type;

        private ThreatInfo(String severity, String type) {
            this.severity = severity;
            this.type = type;
        }
    }

    private static final class MoveInfo {
        private final String san;
        private final String uci;

        private MoveInfo(String san, String uci) {
            this.san = san;
            this.uci = uci;
        }
    }
}
