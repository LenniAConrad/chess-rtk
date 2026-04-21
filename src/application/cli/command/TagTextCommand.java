package application.cli.command;

import static application.cli.Constants.CMD_TAG_TEXT;
import static application.cli.Constants.OPT_ANALYZE;
import static application.cli.Constants.OPT_HASH;
import static application.cli.Constants.OPT_INCLUDE_FEN;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_MAX_DURATION;
import static application.cli.Constants.OPT_MAX_NODES;
import static application.cli.Constants.OPT_MULTIPV;
import static application.cli.Constants.OPT_NODES;
import static application.cli.Constants.OPT_NO_WDL;
import static application.cli.Constants.OPT_PROTOCOL_PATH;
import static application.cli.Constants.OPT_PROTOCOL_PATH_SHORT;
import static application.cli.Constants.OPT_THREADS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_WDL;
import static application.cli.EngineOps.analysePositionOrExit;
import static application.cli.EngineOps.configureEngine;
import static application.cli.EngineOps.resolveWdlFlag;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import application.Config;
import application.cli.EngineOps;
import application.console.Bar;
import chess.core.Position;
import chess.io.Reader;
import chess.nn.t5.BinLoader;
import chess.nn.t5.Model;
import chess.nn.t5.Runner;
import chess.nn.t5.TagPrompt;
import chess.tag.Tagging;
import chess.uci.Analysis;
import chess.uci.Engine;
import chess.uci.Protocol;
import utility.Argv;
import utility.Json;

/**
 * Tag -> T5 inference command.
 *
 * @since 2026
 */
public final class TagTextCommand {

     /**
     * Creates a new tag text command instance.
     */
     private TagTextCommand() {
        // utility
    }

     /**
     * Runs the tag text workflow.
     * @param a a
     */
     public static void runTagText(Argv a) {
        TagTextOptions opts = parseOptions(a);
        List<Position> positions = loadPositions(opts);
        if (positions.isEmpty()) {
            System.err.println(CMD_TAG_TEXT + ": no valid positions provided");
            System.exit(2);
        }

        Model model;
        try {
            model = BinLoader.load(opts.modelPath);
        } catch (Exception ex) {
            System.err.println(CMD_TAG_TEXT + ": failed to load model: " + ex.getMessage());
            if (opts.verbose) {
                ex.printStackTrace(System.err);
            }
            System.exit(2);
            return;
        }

        if (opts.analyze) {
            runWithAnalysis(opts, positions, model);
        } else {
            runWithoutAnalysis(opts, positions, model);
        }
    }

     /**
     * Parses the options.
     * @param a a
     * @return computed value
     */
     private static TagTextOptions parseOptions(Argv a) {
        boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
        Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
        boolean includeFen = a.flag(OPT_INCLUDE_FEN);
        boolean analyze = a.flag(OPT_ANALYZE);
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
        Integer maxNew = a.integer("--max-new");
        String modelPath = CommandSupport.optional(a.string("--model"), Config.getT5ModelPath());

        String fen = a.string(application.cli.Constants.OPT_FEN);
        List<String> rest = a.positionals();
        if (fen == null && !rest.isEmpty()) {
            fen = String.join(" ", rest);
        }
        a.ensureConsumed();
        if (modelPath == null || modelPath.isBlank()) {
            System.err.println(CMD_TAG_TEXT + ": missing --model and config key t5-model-path is empty");
            System.exit(2);
            return null;
        }

        return new TagTextOptions(verbose, includeFen, analyze, protoPath, nodesCap, durMs, multipv, threads, hash,
                wdl, noWdl, maxNew, modelPath, fen, input);
    }

     /**
     * Handles load positions.
     * @param opts opts
     * @return computed value
     */
     private static List<Position> loadPositions(TagTextOptions opts) {
        List<Position> positions = new ArrayList<>();
        if (opts.input != null) {
            try {
                for (String fen : Reader.readFenList(opts.input)) {
                    Position pos = EngineOps.parsePositionOrNull(fen, CMD_TAG_TEXT, opts.verbose);
                    if (pos != null) {
                        positions.add(pos);
                    }
                }
                return positions;
            } catch (Exception ex) {
                System.err.println(CMD_TAG_TEXT + ": failed to read input: " + ex.getMessage());
                if (opts.verbose) {
                    ex.printStackTrace(System.err);
                }
                System.exit(2);
                return List.of();
            }
        }
        if (opts.fen == null || opts.fen.isBlank()) {
            return List.of();
        }
        Position pos = EngineOps.parsePositionOrNull(opts.fen, CMD_TAG_TEXT, opts.verbose);
        if (pos != null) {
            positions.add(pos);
        }
        return positions;
    }

     /**
     * Runs the without analysis workflow.
     * @param opts opts
     * @param positions positions
     * @param model model
     */
     private static void runWithoutAnalysis(TagTextOptions opts, List<Position> positions, Model model) {
        try (Runner runner = new Runner(model)) {
            Bar bar = positionProgressBar(positions, CMD_TAG_TEXT);
            try {
                for (Position pos : positions) {
                    try {
                        List<String> tags = Tagging.tags(pos);
                        String prompt = TagPrompt.buildPositionPrompt(tags);
                        String summary = runner.generate(prompt, opts.maxNew);
                        printSummary(opts.includeFen, pos, summary);
                    } finally {
                        step(bar);
                    }
                }
            } finally {
                finish(bar);
            }
        } catch (Exception ex) {
            System.err.println(CMD_TAG_TEXT + ": inference failed: " + ex.getMessage());
            if (opts.verbose) {
                ex.printStackTrace(System.err);
            }
            System.exit(2);
        }
    }

     /**
     * Runs the with analysis workflow.
     * @param opts opts
     * @param positions positions
     * @param model model
     */
     private static void runWithAnalysis(TagTextOptions opts, List<Position> positions, Model model) {
        Protocol protocol = EngineSupport.loadProtocolOrExit(opts.protoPath, opts.verbose);
        Optional<Boolean> wdlFlag = resolveWdlFlag(opts.wdl, opts.noWdl);
        try (Engine engine = new Engine(protocol); Runner runner = new Runner(model)) {
            configureEngine(CMD_TAG_TEXT, engine, opts.threads, opts.hash, opts.multipv, wdlFlag);
            Bar bar = positionProgressBar(positions, CMD_TAG_TEXT);
            try {
                for (Position pos : positions) {
                    try {
                        Analysis analysis = analysePositionOrExit(engine, pos, opts.nodesCap, opts.durMs, CMD_TAG_TEXT,
                                opts.verbose);
                        if (analysis == null) {
                            continue;
                        }
                        List<String> tags = Tagging.tags(pos, analysis);
                        String prompt = TagPrompt.buildPositionPrompt(tags);
                        String summary = runner.generate(prompt, opts.maxNew);
                        printSummary(opts.includeFen, pos, summary);
                    } finally {
                        step(bar);
                    }
                }
            } finally {
                finish(bar);
            }
        } catch (Exception ex) {
            System.err.println(CMD_TAG_TEXT + ": inference failed: " + ex.getMessage());
            if (opts.verbose) {
                ex.printStackTrace(System.err);
            }
            System.exit(2);
        }
    }

     /**
     * Handles position progress bar.
     * @param positions positions
     * @param label label
     * @return computed value
     */
     private static Bar positionProgressBar(List<Position> positions, String label) {
        return positions != null && positions.size() > 1 ? new Bar(positions.size(), label, false, System.err) : null;
    }

     /**
     * Handles step.
     * @param bar bar
     */
     private static void step(Bar bar) {
        if (bar != null) {
            bar.step();
        }
    }

     /**
     * Handles finish.
     * @param bar bar
     */
     private static void finish(Bar bar) {
        if (bar != null) {
            bar.finish();
        }
    }

     /**
     * Handles print summary.
     * @param includeFen include fen
     * @param pos pos
     * @param summary summary
     */
     private static void printSummary(boolean includeFen, Position pos, String summary) {
        if (!includeFen) {
            System.out.println(summary);
            return;
        }
        StringBuilder sb = new StringBuilder(256).append('{');
        sb.append("\"fen\":\"").append(Json.esc(pos.toString())).append("\",");
        sb.append("\"summary\":\"").append(Json.esc(summary)).append("\"}");
        System.out.println(sb.toString());
    }

     /**
     * Provides tag text options behavior.
     */
     private static final class TagTextOptions {
         /**
         * Stores the verbose.
         */
         private final boolean verbose;
         /**
         * Stores the include fen.
         */
         private final boolean includeFen;
         /**
         * Stores the analyze.
         */
         private final boolean analyze;
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
         * Stores the wdl.
         */
         private final boolean wdl;
         /**
         * Stores the no wdl.
         */
         private final boolean noWdl;
         /**
         * Stores the max new.
         */
         private final int maxNew;
         /**
         * Stores the model path.
         */
         private final String modelPath;
         /**
         * Stores the fen.
         */
         private final String fen;
         /**
         * Stores the input.
         */
         private final Path input;

         /**
         * Creates a new tag text options instance.
         * @param verbose verbose
         * @param includeFen include fen
         * @param analyze analyze
         * @param protoPath proto path
         * @param nodesCap nodes cap
         * @param durMs dur ms
         * @param multipv multipv
         * @param threads threads
         * @param hash hash
         * @param wdl wdl
         * @param noWdl no wdl
         * @param maxNew max new
         * @param modelPath model path
         * @param fen fen
         * @param input input
         */
         private TagTextOptions(boolean verbose, boolean includeFen, boolean analyze, String protoPath, long nodesCap,
                long durMs, Integer multipv, Integer threads, Integer hash, boolean wdl, boolean noWdl, Integer maxNew,
                String modelPath, String fen, Path input) {
            this.verbose = verbose;
            this.includeFen = includeFen;
            this.analyze = analyze;
            this.protoPath = protoPath;
            this.nodesCap = nodesCap;
            this.durMs = durMs;
            this.multipv = multipv;
            this.threads = threads;
            this.hash = hash;
            this.wdl = wdl;
            this.noWdl = noWdl;
            this.maxNew = maxNew == null ? 128 : Math.max(1, maxNew);
            this.modelPath = modelPath;
            this.fen = fen;
            this.input = input;
        }
    }
}
