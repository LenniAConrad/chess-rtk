package testing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import chess.classical.EvalWeights;
import chess.classical.Wdl;
import chess.eval.Classical;
import chess.core.Fen;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.Setup;
import chess.engine.AlphaBeta;
import chess.engine.Limits;
import chess.engine.Result;

/**
 * Texel-style tuner for the classical evaluation weights ({@link Wdl} via
 * {@link EvalWeights}).
 *
 * <p>
 * Two modes:
 * </p>
 *
 * <pre>
 *   gen  --games N --nodes K --out data.txt [--seed S] [--opening P] [--threads T]
 *   tune --data data.txt --out weights.txt [--positions M] [--steps 8,4,2,1] [--seed S]
 * </pre>
 *
 * <p>
 * {@code gen} plays fixed-node self-play games (the current classical alpha-beta
 * engine against itself from randomised openings) and writes quiet,
 * game-result-labelled positions as {@code FEN|r} lines, where {@code r} is the
 * White-perspective outcome (1.0 win, 0.5 draw, 0.0 loss). {@code tune} loads
 * that set, fits the logistic scale {@code K} that best explains the labels with
 * the current weights, then minimises the mean-squared logistic loss
 * {@code E = mean((r - sigmoid(K*eval/400))^2)} by coordinate descent over every
 * tunable weight, and writes the result in {@link EvalWeights} text form.
 * </p>
 *
 * <p>
 * The optimiser re-evaluates the exact {@link Wdl} centipawn function (no
 * linearised feature model), so non-linear terms such as phase blending and king
 * safety are tuned faithfully. Loss evaluation is parallelised across the common
 * fork-join pool; weight mutation happens single-threaded between loss passes.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class EvalTune {

    /**
     * Logistic divisor matching the centipawn scale (Texel convention).
     */
    private static final double SCALE_DIVISOR = 400.0;

    /**
     * Centipawn advantage, sustained over {@link #ADJUDICATION_PLIES}, at which a
     * self-play game is adjudicated decisive to keep dataset generation fast.
     */
    private static final int ADJUDICATION_CP = 1000;

    /**
     * Consecutive plies the adjudication margin must hold before a game is called.
     */
    private static final int ADJUDICATION_PLIES = 6;

    /**
     * Hard ply cap; a game reaching it without a result is scored a draw.
     */
    private static final int MAX_PLIES = 240;

    /**
     * Opening plies skipped before sampling, so the dataset is not dominated by a
     * handful of book-like positions.
     */
    private static final int SAMPLE_SKIP_PLIES = 8;

    /**
     * Maximum coordinate-descent passes per step size, so a step bounds its time
     * even when small improvements keep trickling in.
     */
    private static final int MAX_PASSES_PER_STEP = 8;

    /**
     * Minimum loss reduction for a parameter change to be kept, so the optimiser
     * does not chase label noise. Loss is order 0.1, so this is a real signal.
     */
    private static final double IMPROVE_THRESHOLD = 1e-7;

    /**
     * Every Nth position is held out as the validation set; the optimiser
     * minimises training loss but the weights are kept at their best validation
     * loss, so overfitting cannot be retained.
     */
    private static final int VAL_EVERY = 7;

    /**
     * Stop the whole descent after this many passes without a validation-loss
     * improvement.
     */
    private static final int VAL_PATIENCE = 3;

    /**
     * Centipawn scale that normalises the L2 shrinkage penalty, so a deviation of
     * this many centipawns from a default contributes one unit before the
     * {@code --reg} multiplier.
     */
    private static final double REG_SCALE = 100.0;

    /**
     * Not instantiable.
     */
    private EvalTune() {
    }

    /**
     * Entry point.
     *
     * @param args mode and options
     * @throws IOException on dataset IO failure
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("usage: EvalTune gen|tune [options]");
            System.exit(2);
            return;
        }
        switch (args[0]) {
            case "gen" -> generate(args);
            case "tune" -> tune(args);
            case "bake" -> bake(args);
            default -> {
                System.err.println("unknown mode: " + args[0]);
                System.exit(2);
            }
        }
    }

    /**
     * Rewrites a Java source file's tunable {@link Wdl} constant initialisers with
     * the values from a weights file, so a confirmed tuning result can be baked
     * into the compiled defaults (no runtime file dependency, full speed).
     *
     * @param args options ({@code --weights}, {@code --source})
     * @throws IOException if the weights or source cannot be read or written
     */
    private static void bake(String[] args) throws IOException {
        Path weights = Path.of(stringArg(args, "--weights", "eval-weights.txt"));
        Path source = Path.of(stringArg(args, "--source",
                "/home/lennart/Code/chess-rtk/src/chess/classical/Wdl.java"));
        EvalWeights.load(weights);
        String text = Files.readString(source, StandardCharsets.UTF_8);
        int baked = 0;
        for (String line : EvalWeights.serialize().split("\\R")) {
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String name = line.substring(0, eq).trim();
            String[] values = line.substring(eq + 1).trim().split(",");
            String valuePart;
            java.util.regex.Pattern pattern;
            if (values.length > 1) {
                StringBuilder joined = new StringBuilder("{");
                if (values.length >= 32) {
                    // Grid-format large tables (piece-square tables) eight per row,
                    // matching the hand-written layout, instead of one long line.
                    for (int i = 0; i < values.length; i++) {
                        joined.append(i % 8 == 0 ? "\n            " : " ");
                        joined.append(values[i].trim());
                        if (i < values.length - 1) {
                            joined.append(',');
                        }
                    }
                    joined.append("\n    }");
                } else {
                    joined.append(' ');
                    for (int i = 0; i < values.length; i++) {
                        if (i > 0) {
                            joined.append(", ");
                        }
                        joined.append(values[i].trim());
                    }
                    joined.append(" }");
                }
                valuePart = joined.toString();
                pattern = java.util.regex.Pattern.compile(
                        "(\\b" + name + "\\s*=\\s*)\\{[^}]*\\}", java.util.regex.Pattern.DOTALL);
            } else {
                valuePart = values[0].trim();
                pattern = java.util.regex.Pattern.compile("(\\b" + name + "\\s*=\\s*)-?\\d+");
            }
            java.util.regex.Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                text = text.substring(0, matcher.start())
                        + matcher.group(1) + valuePart
                        + text.substring(matcher.end());
                baked++;
            } else {
                System.out.println("bake: WARNING no match for " + name);
            }
        }
        Files.writeString(source, text, StandardCharsets.UTF_8);
        System.out.printf(Locale.ROOT, "bake: rewrote %d initialisers in %s%n", baked, source);
    }

    // ---------------------------------------------------------------- dataset

    /**
     * Generates a self-play, result-labelled quiet-position dataset.
     *
     * @param args options
     * @throws IOException if the output cannot be written
     */
    private static void generate(String[] args) throws IOException {
        int games = intArg(args, "--games", 4000);
        long nodes = longArg(args, "--nodes", 1200);
        int openingPlies = intArg(args, "--opening", 8);
        long seed = longArg(args, "--seed", 20260610L);
        int threads = intArg(args, "--threads", Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        Path out = Path.of(stringArg(args, "--out", "eval-data.txt"));

        System.out.printf(Locale.ROOT, "gen: games=%d nodes=%d openingPlies=%d threads=%d seed=%d -> %s%n",
                games, nodes, openingPlies, threads, seed, out);

        // Each thread streams to its own shard, flushing per game, so a timeout or
        // crash keeps every completed game instead of losing the whole run.
        AtomicInteger done = new AtomicInteger();
        final int gamesFinal = games;
        final long nodesFinal = nodes;
        final int openingFinal = openingPlies;
        IntStream.range(0, threads).parallel().forEach(t -> {
            Random rng = new Random(seed * 1_000_003L + t);
            // Persistent transposition table: reused across the thread's searches
            // instead of allocating and zeroing a fresh table on every move.
            AlphaBeta engine = new AlphaBeta(new Classical(), true);
            Path shard = Path.of(out + ".part" + t);
            List<String> buffer = new ArrayList<>();
            try (BufferedWriter writer = Files.newBufferedWriter(shard, StandardCharsets.UTF_8)) {
                for (int g = t; g < gamesFinal; g += threads) {
                    buffer.clear();
                    playGame(engine, rng, nodesFinal, openingFinal, buffer);
                    for (String line : buffer) {
                        writer.write(line);
                        writer.write('\n');
                    }
                    writer.flush();
                    int n = done.incrementAndGet();
                    if (n % 200 == 0) {
                        System.out.printf(Locale.ROOT, "  %d/%d games%n", n, gamesFinal);
                    }
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            engine.close();
        });

        // Merge shards into the final dataset, then drop them.
        int total = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            for (int t = 0; t < threads; t++) {
                Path shard = Path.of(out + ".part" + t);
                if (!Files.exists(shard)) {
                    continue;
                }
                for (String line : Files.readAllLines(shard, StandardCharsets.UTF_8)) {
                    writer.write(line);
                    writer.write('\n');
                    total++;
                }
                Files.delete(shard);
            }
        }
        System.out.printf(Locale.ROOT, "gen: wrote %d positions to %s%n", total, out);
    }

    /**
     * Plays one self-play game and appends its quiet, labelled positions.
     *
     * @param engine reusable searcher
     * @param rng randomness for the opening and tie-breaks
     * @param nodes per-move node budget
     * @param openingPlies random opening plies
     * @param sink output line accumulator ({@code FEN|r})
     */
    private static void playGame(AlphaBeta engine, Random rng, long nodes, int openingPlies, List<String> sink) {
        Position pos = new Position(Setup.getStandardStartFEN());
        // Randomised opening for game diversity.
        for (int i = 0; i < openingPlies; i++) {
            MoveList legal = pos.legalMoves();
            if (legal.isEmpty()) {
                return;
            }
            pos.play(legal.get(rng.nextInt(legal.size())));
        }

        Limits limits = new Limits(AlphaBeta.MAX_DEPTH, nodes, 0L);
        List<String> pending = new ArrayList<>();
        java.util.HashMap<Long, Integer> seen = new java.util.HashMap<>();
        double result = 0.5;
        int decisiveStreak = 0;
        int decisiveSign = 0;
        boolean resolved = false;

        for (int ply = 0; ply < MAX_PLIES; ply++) {
            MoveList legal = pos.legalMoves();
            if (legal.isEmpty()) {
                result = pos.inCheck() ? (pos.isWhiteToMove() ? 0.0 : 1.0) : 0.5;
                resolved = true;
                break;
            }
            if (pos.isInsufficientMaterial()) {
                result = 0.5;
                resolved = true;
                break;
            }
            long key = pos.signatureCore();
            int count = seen.merge(key, 1, Integer::sum);
            if (count >= 3) {
                result = 0.5;
                resolved = true;
                break;
            }

            Result search = engine.search(pos, limits);
            if (!search.hasBestMove()) {
                result = 0.5;
                resolved = true;
                break;
            }
            short move = search.bestMove();

            // Sample quiet, non-opening, not-in-check positions whose chosen move
            // is itself quiet (so the static eval is a fair label for the result).
            if (ply >= SAMPLE_SKIP_PLIES && !pos.inCheck()
                    && !Move.isPromotion(move) && !isCapture(pos, move)) {
                pending.add(Fen.format(pos));
            }

            // Adjudicate clearly decisive games early.
            int whiteCp = Wdl.evaluateWhiteCentipawns(pos);
            int sign = Integer.signum(whiteCp);
            if (Math.abs(whiteCp) >= ADJUDICATION_CP && sign == decisiveSign && sign != 0) {
                if (++decisiveStreak >= ADJUDICATION_PLIES) {
                    result = sign > 0 ? 1.0 : 0.0;
                    resolved = true;
                    break;
                }
            } else if (Math.abs(whiteCp) >= ADJUDICATION_CP) {
                decisiveSign = sign;
                decisiveStreak = 1;
            } else {
                decisiveSign = 0;
                decisiveStreak = 0;
            }

            pos.play(move);
        }

        if (!resolved) {
            result = 0.5;
        }
        String label = formatResult(result);
        for (String fen : pending) {
            sink.add(fen + "|" + label);
        }
    }

    /**
     * Returns whether a move captures (its destination is occupied), used as a
     * cheap quietness filter; en-passant and promotions are handled separately.
     *
     * @param pos position before the move
     * @param move move to test
     * @return true when the destination square holds a piece
     */
    private static boolean isCapture(Position pos, short move) {
        int to = Move.getToIndex(move);
        return pos.getBoard()[to] != Piece.EMPTY;
    }

    // ------------------------------------------------------------------- tune

    /**
     * Runs the coordinate-descent tuning pass.
     *
     * @param args options
     * @throws IOException if the dataset cannot be read or weights written
     */
    private static void tune(String[] args) throws IOException {
        Path data = Path.of(stringArg(args, "--data", "eval-data.txt"));
        Path out = Path.of(stringArg(args, "--out", "eval-weights.txt"));
        int limit = intArg(args, "--positions", Integer.MAX_VALUE);
        int[] steps = parseSteps(stringArg(args, "--steps", "8,4,2,1"));
        // Comma-separated name substrings to hold fixed (e.g. "--freeze PST" keeps
        // the hand-set piece-square tables, which overfit sparse squares).
        String freeze = stringArg(args, "--freeze", "");
        List<String> freezeSubs = new ArrayList<>();
        for (String s : freeze.split(",")) {
            if (!s.isBlank()) {
                freezeSubs.add(s.trim());
            }
        }
        // L2 shrinkage strength toward the default weights (0 = off).
        double regLambda = Double.parseDouble(stringArg(args, "--reg", "0"));

        System.out.printf(Locale.ROOT, "tune: loading %s%n", data);
        Sample[] samples = loadSamples(data, limit);
        System.out.printf(Locale.ROOT, "tune: %d positions%n", samples.length);

        // Hold out a validation set. The optimiser minimises TRAINING loss but the
        // weights are kept at their best VALIDATION loss, so memorised label noise
        // is discarded instead of shipped (a lower training loss that plays worse
        // is exactly the overfitting this guards against).
        List<Sample> trainList = new ArrayList<>();
        List<Sample> valList = new ArrayList<>();
        for (int i = 0; i < samples.length; i++) {
            (i % VAL_EVERY == 0 ? valList : trainList).add(samples[i]);
        }
        Sample[] train = trainList.toArray(new Sample[0]);
        Sample[] val = valList.toArray(new Sample[0]);

        double k = fitK(train);
        int[] params = EvalWeights.readParameters();
        List<String> names = EvalWeights.parameterNames();
        boolean[] frozen = new boolean[params.length];
        int frozenCount = 0;
        for (int i = 0; i < params.length; i++) {
            for (String sub : freezeSubs) {
                if (names.get(i).contains(sub)) {
                    frozen[i] = true;
                    frozenCount++;
                    break;
                }
            }
        }
        if (frozenCount > 0) {
            System.out.printf(Locale.ROOT, "tune: froze %d/%d params matching %s%n",
                    frozenCount, params.length, freezeSubs);
        }
        EvalWeights.writeParameters(params);
        double trainLoss = loss(train, k);
        double valStart = loss(val, k);
        double bestVal = valStart;
        int[] bestParams = params.clone();
        int noImprove = 0;
        boolean stop = false;
        // L2 shrinkage toward the (hand-tuned, sane) defaults: a weight may move
        // far from its default only when the data strongly justifies it, which
        // blocks degenerate fits (negative king-attack weights, collapsed pawn
        // value) that lower dataset loss but lose games.
        double regCoef = regLambda / (REG_SCALE * REG_SCALE * params.length);
        long regSumSq = sumSquaredDeviation(params);
        double bestObj = trainLoss + regCoef * regSumSq;
        System.out.printf(Locale.ROOT, "tune: K=%.4f train=%d val=%d trainLoss=%.6f valLoss=%.6f reg=%.2f%n",
                k, train.length, val.length, trainLoss, valStart, regLambda);

        // Coordinate descent on the regularised objective. The live weights mirror
        // params via single-parameter writes, so each trial costs one loss pass.
        for (int step : steps) {
            if (stop) {
                break;
            }
            boolean improvedAny = true;
            int pass = 0;
            while (improvedAny && pass < MAX_PASSES_PER_STEP && !stop) {
                improvedAny = false;
                pass++;
                int improvements = 0;
                for (int i = 0; i < params.length; i++) {
                    if (frozen[i]) {
                        continue;
                    }
                    int original = params[i];
                    long baseDevSq = sq(original - BASELINE[i]);
                    EvalWeights.setParameter(i, original + step);
                    double up = loss(train, k);
                    long upReg = regSumSq - baseDevSq + sq(original + step - BASELINE[i]);
                    double upObj = up + regCoef * upReg;
                    if (upObj < bestObj - IMPROVE_THRESHOLD) {
                        params[i] = original + step;
                        regSumSq = upReg;
                        trainLoss = up;
                        bestObj = upObj;
                        improvedAny = true;
                        improvements++;
                        continue;
                    }
                    EvalWeights.setParameter(i, original - step);
                    double down = loss(train, k);
                    long downReg = regSumSq - baseDevSq + sq(original - step - BASELINE[i]);
                    double downObj = down + regCoef * downReg;
                    if (downObj < bestObj - IMPROVE_THRESHOLD) {
                        params[i] = original - step;
                        regSumSq = downReg;
                        trainLoss = down;
                        bestObj = downObj;
                        improvedAny = true;
                        improvements++;
                    } else {
                        EvalWeights.setParameter(i, original);
                    }
                }
                double valLoss = loss(val, k);
                boolean valImproved = valLoss < bestVal - 1e-9;
                if (valImproved) {
                    bestVal = valLoss;
                    bestParams = params.clone();
                    noImprove = 0;
                } else {
                    noImprove++;
                }
                System.out.printf(Locale.ROOT, "  step=%d pass=%d train=%.6f val=%.6f%s improvements=%d%n",
                        step, pass, trainLoss, valLoss, valImproved ? " *best" : "", improvements);
                if (noImprove >= VAL_PATIENCE) {
                    System.out.println("  early stop: validation loss stopped improving");
                    stop = true;
                }
            }
        }

        EvalWeights.writeParameters(bestParams);
        EvalWeights.save(out);
        System.out.printf(Locale.ROOT, "tune: valStart=%.6f bestVal=%.6f (%.2f%% lower) -> %s%n",
                valStart, bestVal, 100.0 * (valStart - bestVal) / valStart, out);
        reportLargestChanges(names, bestParams);
    }

    /**
     * Mean squared logistic loss over the dataset for the current live weights.
     *
     * @param samples dataset
     * @param k logistic scale
     * @return mean squared error
     */
    private static double loss(Sample[] samples, double k) {
        double sum = IntStream.range(0, samples.length).parallel().mapToDouble(i -> {
            Sample s = samples[i];
            int cp = Wdl.evaluateWhiteCentipawns(s.position);
            double p = sigmoid(k * cp / SCALE_DIVISOR);
            double d = s.result - p;
            return d * d;
        }).sum();
        return sum / samples.length;
    }

    /**
     * Fits the logistic scale {@code K} that minimises loss with the current
     * weights, via a coarse scan refined by golden-section search.
     *
     * @param samples dataset
     * @return best K
     */
    private static double fitK(Sample[] samples) {
        double lo = 0.2;
        double hi = 3.0;
        for (int iter = 0; iter < 40; iter++) {
            double m1 = lo + (hi - lo) / 3.0;
            double m2 = hi - (hi - lo) / 3.0;
            if (loss(samples, m1) < loss(samples, m2)) {
                hi = m2;
            } else {
                lo = m1;
            }
        }
        return (lo + hi) / 2.0;
    }

    /**
     * Logistic sigmoid.
     *
     * @param x x-coordinate
     * @return value in (0, 1)
     */
    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.pow(10.0, -x));
    }

    // ------------------------------------------------------------- IO/helpers

    /**
     * One labelled training position.
     *
     * @param position parsed position
     * @param result White-perspective outcome in {0, 0.5, 1}
     */
    private record Sample(Position position, double result) {
    }

    /**
     * Loads up to {@code limit} samples from a {@code FEN|r} dataset file.
     *
     * @param data dataset path
     * @param limit maximum samples
     * @return parsed samples
     * @throws IOException if the file cannot be read
     */
    private static Sample[] loadSamples(Path data, int limit) throws IOException {
        List<Sample> out = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(data, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null && out.size() < limit) {
                int bar = line.lastIndexOf('|');
                if (bar < 0) {
                    continue;
                }
                String fen = line.substring(0, bar).trim();
                double result = Double.parseDouble(line.substring(bar + 1).trim());
                out.add(new Sample(new Position(fen), result));
            }
        }
        return out.toArray(new Sample[0]);
    }

    /**
     * Prints the tunable parameters whose value moved the most, for a sanity read
     * on what the tuner changed.
     *
     * @param names parameter names
     * @param tuned tuned values
     */
    private static void reportLargestChanges(List<String> names, int[] tuned) {
        int[] base = baselineParameters();
        Integer[] order = new Integer[tuned.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Integer.compare(Math.abs(tuned[b] - base[b]), Math.abs(tuned[a] - base[a])));
        System.out.println("tune: largest changes:");
        for (int i = 0; i < Math.min(20, order.length); i++) {
            int idx = order[i];
            System.out.printf(Locale.ROOT, "  %-22s %d -> %d (%+d)%n",
                    names.get(idx), base[idx], tuned[idx], tuned[idx] - base[idx]);
        }
    }

    /**
     * Reads the compiled-in default parameter vector by reloading defaults from a
     * fresh classloader view is not available here, so the baseline is captured
     * once at process start before any tuning mutates the live weights.
     *
     * @return baseline parameter vector
     */
    private static int[] baselineParameters() {
        return BASELINE.clone();
    }

    /**
     * Baseline (compiled default) parameter vector, captured at class load before
     * any tuning mutates the live weights.
     */
    private static final int[] BASELINE = EvalWeights.readParameters();

    /**
     * Squared value widened to {@code long} to avoid overflow when summing.
     *
     * @param value value to square
     * @return {@code value * value}
     */
    private static long sq(int value) {
        return (long) value * value;
    }

    /**
     * Sum of squared deviations of the parameters from the compiled-in defaults.
     *
     * @param params current parameter vector
     * @return total squared deviation
     */
    private static long sumSquaredDeviation(int[] params) {
        long sum = 0;
        for (int i = 0; i < params.length; i++) {
            sum += sq(params[i] - BASELINE[i]);
        }
        return sum;
    }

    /**
     * Formats a White-perspective result.
     *
     * @param result outcome
     * @return "1.0", "0.5", or "0.0"
     */
    private static String formatResult(double result) {
        if (result >= 0.99) {
            return "1.0";
        }
        if (result <= 0.01) {
            return "0.0";
        }
        return "0.5";
    }

    /**
     * Parses a comma-separated step schedule.
     *
     * @param text e.g. "8,4,2,1"
     * @return step sizes
     */
    private static int[] parseSteps(String text) {
        String[] parts = text.split(",");
        int[] steps = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            steps[i] = Integer.parseInt(parts[i].trim());
        }
        return steps;
    }

    /**
     * Returns a string option value or a default.
     *
     * @param args argument array
     * @param name option name
     * @param fallback default value
     * @return resolved value
     */
    private static String stringArg(String[] args, String name, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    /**
     * Returns an int option value or a default.
     *
     * @param args argument array
     * @param name option name
     * @param fallback default value
     * @return resolved value
     */
    private static int intArg(String[] args, String name, int fallback) {
        String value = stringArg(args, name, null);
        return value == null ? fallback : Integer.parseInt(value);
    }

    /**
     * Returns a long option value or a default.
     *
     * @param args argument array
     * @param name option name
     * @param fallback default value
     * @return resolved value
     */
    private static long longArg(String[] args, String name, long fallback) {
        String value = stringArg(args, name, null);
        return value == null ? fallback : Long.parseLong(value);
    }
}
