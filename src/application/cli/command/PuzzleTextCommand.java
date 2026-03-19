package application.cli.command;

import static application.cli.Constants.CMD_PUZZLE_TEXT;
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

    private PuzzleTextCommand() {
        // utility
    }

    public static void runPuzzleText(Argv a) {
        PuzzleTextOptions opts = parseOptions(a);
        Position root = parsePositionOrExit(opts.inputConfig.fen, opts.flags.verbose);

        Model model;
        try {
            model = BinLoader.load(opts.inputConfig.modelPath);
        } catch (Exception ex) {
            System.err.println(CMD_PUZZLE_TEXT + ": failed to load model: " + ex.getMessage());
            if (opts.flags.verbose) {
                ex.printStackTrace(System.err);
            }
            System.exit(2);
            return;
        }

        Protocol protocol = EngineSupport.loadProtocolOrExit(opts.engineConfig.protoPath, opts.flags.verbose);
        Optional<Boolean> wdlFlag = resolveWdlFlag(opts.wdlConfig.wdl, opts.wdlConfig.noWdl);

        try (Engine engine = new Engine(protocol); Runner runner = new Runner(model)) {
            configureEngine(CMD_PUZZLE_TEXT, engine, opts.engineConfig.threads, opts.engineConfig.hash,
                    opts.engineConfig.multipv, wdlFlag);
            Analysis analysis = analysePositionOrExit(engine, root, opts.limits.nodesCap, opts.limits.durMs,
                    CMD_PUZZLE_TEXT, opts.flags.verbose);
            if (analysis == null) {
                return;
            }

            List<chess.struct.Record> records = PuzzleSupport.buildRecords(root, analysis, opts.limits.pvPlies,
                    CMD_PUZZLE_TEXT, opts.flags.verbose);
            if (records.isEmpty()) {
                System.err.println(CMD_PUZZLE_TEXT + ": no records extracted from PVs");
                System.exit(2);
            }

            if (opts.flags.analyzeTags) {
                int tagMultipv = Math.max(1, opts.engineConfig.tagMultipv);
                configureEngine(CMD_PUZZLE_TEXT, engine, opts.engineConfig.threads, opts.engineConfig.hash, tagMultipv,
                        wdlFlag);
            }

            Map<String, TagEntry> cache = new HashMap<>();
            for (chess.struct.Record rec : records) {
                Position pos = rec.getPosition();
                if (pos == null) {
                    continue;
                }
                List<String> tags = tagsFor(pos, opts.flags.analyzeTags ? engine : null, opts, cache);
                if (tags.isEmpty()) {
                    continue;
                }
                String prompt = TagPrompt.buildPositionPrompt(tags);
                String summary = runner.generate(prompt, opts.limits.maxNew);
                if (opts.flags.includeFen) {
                    MoveInfo moveInfo = null;
                    if (rec.getParent() != null && pos != null) {
                        moveInfo = inferMove(rec.getParent(), pos);
                    }
                    StringBuilder sb = new StringBuilder(256).append('{');
                    sb.append("\"fen\":\"").append(Json.esc(pos.toString())).append("\",");
                    sb.append("\"move_san\":").append(moveInfo == null ? "null" : "\"" + Json.esc(moveInfo.san) + "\"");
                    sb.append(',');
                    sb.append("\"move_uci\":").append(moveInfo == null ? "null" : "\"" + Json.esc(moveInfo.uci) + "\"");
                    sb.append(',');
                    sb.append("\"summary\":\"").append(Json.esc(summary)).append("\"}");
                    System.out.println(sb.toString());
                } else {
                    System.out.println(summary);
                }
            }
        } catch (Exception ex) {
            System.err.println(CMD_PUZZLE_TEXT + ": inference failed: " + ex.getMessage());
            if (opts.flags.verbose) {
                ex.printStackTrace(System.err);
            }
            System.exit(2);
        }
    }

    private static PuzzleTextOptions parseOptions(Argv a) {
        boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
        boolean includeFen = a.flag(OPT_INCLUDE_FEN);
        boolean analyze = a.flag(OPT_ANALYZE);
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
            System.err.println(CMD_PUZZLE_TEXT + ": missing --model and config key t5-model-path is empty");
            System.exit(2);
            return null;
        }

        if (wdl && noWdl) {
            System.err.println(CMD_PUZZLE_TEXT + ": only one of --wdl or --no-wdl may be set");
            System.exit(2);
        }
        if (fen == null || fen.isBlank()) {
            System.err.println(CMD_PUZZLE_TEXT + " requires --fen or a positional FEN");
            System.exit(2);
        }
        int mpv = multipv == null ? 3 : Math.max(1, multipv);
        int plies = pvPlies == null ? 12 : Math.max(1, pvPlies);
        int tagMpv = tagMultipv == null ? 1 : Math.max(1, tagMultipv);
        boolean analyzeTags = noAnalyze ? false : (analyze || true);
        int maxOut = maxNew == null ? 128 : Math.max(1, maxNew);
        PuzzleTextOptions.Flags flags = new PuzzleTextOptions.Flags(verbose, includeFen, analyzeTags);
        PuzzleTextOptions.EngineConfig engineConfig = new PuzzleTextOptions.EngineConfig(protoPath, mpv, tagMpv,
                threads, hash);
        PuzzleTextOptions.Limits limits = new PuzzleTextOptions.Limits(nodesCap, durMs, plies, maxOut);
        PuzzleTextOptions.WdlConfig wdlConfig = new PuzzleTextOptions.WdlConfig(wdl, noWdl);
        PuzzleTextOptions.InputConfig inputConfig = new PuzzleTextOptions.InputConfig(modelPath, fen);
        return new PuzzleTextOptions(flags, engineConfig, limits, wdlConfig, inputConfig);
    }

    private static Position parsePositionOrExit(String fen, boolean verbose) {
        Position pos = parsePositionOrNull(fen, CMD_PUZZLE_TEXT, verbose);
        if (pos == null) {
            System.exit(2);
        }
        return pos;
    }

    private static List<String> tagsFor(Position pos, Engine engine, PuzzleTextOptions opts,
            Map<String, TagEntry> cache) {
        String fen = pos.toString();
        TagEntry cached = cache.get(fen);
        if (cached != null) {
            return cached.tags;
        }
        Analysis analysis = null;
        if (engine != null) {
            analysis = analysePositionOrExit(engine, pos, opts.limits.nodesCap, opts.limits.durMs, CMD_PUZZLE_TEXT,
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
        cache.put(fen, new TagEntry(tags, analysis));
        return tags;
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

    private static final class PuzzleTextOptions {
        private final Flags flags;
        private final EngineConfig engineConfig;
        private final Limits limits;
        private final WdlConfig wdlConfig;
        private final InputConfig inputConfig;

        private PuzzleTextOptions(Flags flags, EngineConfig engineConfig, Limits limits, WdlConfig wdlConfig,
                InputConfig inputConfig) {
            this.flags = flags;
            this.engineConfig = engineConfig;
            this.limits = limits;
            this.wdlConfig = wdlConfig;
            this.inputConfig = inputConfig;
        }

        private static final class Flags {
            private final boolean verbose;
            private final boolean includeFen;
            private final boolean analyzeTags;

            private Flags(boolean verbose, boolean includeFen, boolean analyzeTags) {
                this.verbose = verbose;
                this.includeFen = includeFen;
                this.analyzeTags = analyzeTags;
            }
        }

        private static final class EngineConfig {
            private final String protoPath;
            private final int multipv;
            private final int tagMultipv;
            private final Integer threads;
            private final Integer hash;

            private EngineConfig(String protoPath, int multipv, int tagMultipv, Integer threads, Integer hash) {
                this.protoPath = protoPath;
                this.multipv = multipv;
                this.tagMultipv = tagMultipv;
                this.threads = threads;
                this.hash = hash;
            }
        }

        private static final class Limits {
            private final long nodesCap;
            private final long durMs;
            private final int pvPlies;
            private final int maxNew;

            private Limits(long nodesCap, long durMs, int pvPlies, int maxNew) {
                this.nodesCap = nodesCap;
                this.durMs = durMs;
                this.pvPlies = pvPlies;
                this.maxNew = maxNew;
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

        private static final class InputConfig {
            private final String modelPath;
            private final String fen;

            private InputConfig(String modelPath, String fen) {
                this.modelPath = modelPath;
                this.fen = fen;
            }
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

    private static final class MoveInfo {
        private final String san;
        private final String uci;

        private MoveInfo(String san, String uci) {
            this.san = san;
            this.uci = uci;
        }
    }
}
