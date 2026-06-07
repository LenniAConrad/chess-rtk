package application.cli.command;

import static application.cli.Constants.OPT_CLASSICAL;
import static application.cli.Constants.OPT_EVALUATOR;
import static application.cli.Constants.OPT_JSON;
import static application.cli.Constants.OPT_LC0;
import static application.cli.Constants.OPT_OTIS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_WEIGHTS;
import static application.cli.Validation.requireNonNegative;

import chess.core.MoveInference;
import chess.core.MoveList;
import chess.core.Position;
import chess.eval.Classical;
import chess.nn.ActivationSink;
import chess.nn.lc0.bt4.PolicyEncoder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import utility.Argv;

/**
 * Implements {@code engine trace}: runs a neural network over a position and
 * prints the value, win/draw/loss probabilities, the top policy moves, and a
 * per-layer activation summary — the data the workbench Evaluator (neural trace)
 * panel visualizes, in a headless, reproducible form.
 *
 * <p>
 * Backends: {@code --classical} (value only), {@code --nnue} (value + layers),
 * {@code --lc0} (CNN: value, WDL, policy, layers), {@code --bt4}, and
 * {@code --otis}. Use {@code --json} for one machine-readable object.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class EngineTraceCommand {

    /**
     * {@code --nnue} backend shortcut flag.
     */
    private static final String OPT_NNUE = "--nnue";

    /**
     * {@code --bt4} backend shortcut flag.
     */
    private static final String OPT_BT4 = "--bt4";

    /**
     * Top-policy-move limit flag.
     */
    private static final String OPT_TOP = "--top";

    /**
     * Default number of top policy moves shown.
     */
    private static final int DEFAULT_TOP = 6;

    /**
     * Centipawn conversion scale matching the engine's value-to-centipawns map.
     */
    private static final double CP_SCALE = 600.0;

    /**
     * Neural-trace backends.
     */
    private enum Backend {

        /**
         * Classical handcrafted evaluator (value only).
         */
        CLASSICAL("classical"),

        /**
         * Pure-Java NNUE evaluator (value + layers).
         */
        NNUE("nnue"),

        /**
         * LC0 CNN policy/value network.
         */
        LC0("lc0"),

        /**
         * LC0 BT4 attention network.
         */
        BT4("bt4"),

        /**
         * OTIS policy/WDL network.
         */
        OTIS("otis");

        /**
         * Stable lowercase label.
         */
        private final String label;

        /**
         * Creates a backend.
         *
         * @param label stable label
         */
        Backend(String label) {
            this.label = label;
        }
    }

    /**
     * Utility class; prevent instantiation.
     */
    private EngineTraceCommand() {
        // utility
    }

    /**
     * Handles {@code engine trace}.
     *
     * @param a argument parser
     */
    public static void runTrace(Argv a) {
        String cmd = "engine trace";
        boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
        boolean json = a.flag(OPT_JSON);
        int top = a.integerOr(DEFAULT_TOP, OPT_TOP);
        Backend backend = resolveBackend(a, cmd);
        Path weights = a.path(OPT_WEIGHTS);
        Position position = CommandSupport.resolvePositionArgument(a, cmd, true, verbose);
        a.ensureConsumed();
        requireNonNegative(cmd, OPT_TOP, top);

        Trace trace = compute(backend, weights, position, top, cmd);
        if (json) {
            System.out.println(toJson(position, trace));
        } else {
            printText(position, trace);
        }
    }

    /**
     * Resolves the backend from evaluator flags.
     *
     * @param a argument parser
     * @param cmd command label
     * @return resolved backend
     */
    private static Backend resolveBackend(Argv a, String cmd) {
        String value = a.string(OPT_EVALUATOR);
        boolean classical = a.flag(OPT_CLASSICAL);
        boolean nnue = a.flag(OPT_NNUE);
        boolean lc0 = a.flag(OPT_LC0);
        boolean bt4 = a.flag(OPT_BT4);
        boolean otis = a.flag(OPT_OTIS);
        int flags = (classical ? 1 : 0) + (nnue ? 1 : 0) + (lc0 ? 1 : 0) + (bt4 ? 1 : 0) + (otis ? 1 : 0);
        if (value != null && flags > 0) {
            throw new CommandFailure(
                    cmd + ": use either " + OPT_EVALUATOR + " or a backend shortcut flag, not both", 2);
        }
        if (flags > 1) {
            throw new CommandFailure(cmd + ": choose only one backend flag", 2);
        }
        if (value != null) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "classical", "static" -> Backend.CLASSICAL;
                case "nnue" -> Backend.NNUE;
                case "lc0", "leela", "cnn" -> Backend.LC0;
                case "bt4" -> Backend.BT4;
                case "otis" -> Backend.OTIS;
                default -> throw new CommandFailure(cmd + ": unknown backend: " + value
                        + " (use classical, nnue, lc0, bt4, or otis)", 2);
            };
        }
        if (classical) {
            return Backend.CLASSICAL;
        }
        if (lc0) {
            return Backend.LC0;
        }
        if (bt4) {
            return Backend.BT4;
        }
        if (otis) {
            return Backend.OTIS;
        }
        return Backend.NNUE;
    }

    /**
     * Runs the selected backend and collects the trace.
     *
     * @param backend selected backend
     * @param weights explicit weights path, or {@code null}
     * @param position root position
     * @param top number of top policy moves
     * @param cmd command label
     * @return collected trace
     */
    private static Trace compute(Backend backend, Path weights, Position position, int top, String cmd) {
        try {
            return switch (backend) {
                case CLASSICAL -> classicalTrace(position);
                case NNUE -> nnueTrace(weights, position);
                case LC0 -> cnnTrace(weights, position, top);
                case BT4 -> bt4Trace(weights, position, top, cmd);
                case OTIS -> otisTrace(weights, position, top);
            };
        } catch (IOException ex) {
            throw new CommandFailure(cmd + ": could not load backend weights: " + ex.getMessage(), 2);
        }
    }

    /**
     * Traces the classical evaluator.
     *
     * @param position root position
     * @return trace
     */
    private static Trace classicalTrace(Position position) {
        int cp = new Classical().evaluate(position);
        return new Trace("classical", Integer.valueOf(cp), null, null, List.of(), List.of());
    }

    /**
     * Traces the NNUE evaluator.
     *
     * @param weights weights path, or {@code null} for the default
     * @param position root position
     * @return trace
     * @throws IOException if weights cannot be loaded
     */
    private static Trace nnueTrace(Path weights, Position position) throws IOException {
        chess.nn.nnue.Model model = weights == null
                ? chess.nn.nnue.Model.load(chess.nn.nnue.Model.DEFAULT_WEIGHTS)
                : chess.nn.nnue.Model.load(weights);
        int cp = model.evaluateCentipawns(position);
        RecordingSink sink = new RecordingSink();
        model.predict(position, sink);
        return new Trace("nnue", Integer.valueOf(cp), null, null, List.of(), sink.layers());
    }

    /**
     * Traces the LC0 CNN network.
     *
     * @param weights weights path, or {@code null} for the default
     * @param position root position
     * @param top number of top policy moves
     * @return trace
     * @throws IOException if weights cannot be loaded
     */
    private static Trace cnnTrace(Path weights, Position position, int top) throws IOException {
        Path resolved = weights == null ? chess.nn.lc0.cnn.Model.DEFAULT_WEIGHTS : weights;
        chess.nn.lc0.cnn.Network network = chess.nn.lc0.cnn.Network.loadCpu(resolved);
        RecordingSink sink = new RecordingSink();
        float[] planes = chess.nn.lc0.cnn.Encoder.encode(position);
        chess.nn.lc0.cnn.Network.Prediction prediction = network.predictEncoded(planes, sink);
        List<PolicyMove> policy = decodeCnnPolicy(position, prediction.policy(), top);
        return new Trace("lc0", scalarToCentipawns(prediction.value()), Double.valueOf(prediction.value()),
                prediction.wdl(), policy, sink.layers());
    }

    /**
     * Traces the LC0 BT4 network.
     *
     * @param weights weights path (required)
     * @param position root position
     * @param top number of top policy moves
     * @param cmd command label
     * @return trace
     * @throws IOException if weights cannot be loaded
     */
    private static Trace bt4Trace(Path weights, Position position, int top, String cmd) throws IOException {
        if (weights == null) {
            throw new CommandFailure(cmd + ": " + OPT_BT4 + " requires " + OPT_WEIGHTS + " <path>", 2);
        }
        chess.nn.lc0.bt4.Network network = chess.nn.lc0.bt4.Network.loadCpu(weights);
        RecordingSink sink = new RecordingSink();
        chess.nn.lc0.bt4.Network.Prediction prediction = network.predict(position, sink);
        int transform = (int) sink.firstValue("bt4.input.transform");
        List<PolicyMove> policy = decodeCompressedPolicy(position, prediction.policy(), transform, top);
        return new Trace("bt4", scalarToCentipawns(prediction.value()), Double.valueOf(prediction.value()),
                prediction.wdl(), policy, sink.layers());
    }

    /**
     * Traces the OTIS network.
     *
     * @param weights weights path, or {@code null} for the default
     * @param position root position
     * @param top number of top policy moves
     * @return trace
     * @throws IOException if weights cannot be loaded
     */
    private static Trace otisTrace(Path weights, Position position, int top) throws IOException {
        Path resolved = weights == null ? chess.nn.otis.Model.DEFAULT_WEIGHTS : weights;
        chess.nn.otis.Model model = chess.nn.otis.Model.loadCpu(resolved);
        RecordingSink sink = new RecordingSink();
        chess.nn.otis.Model.Prediction prediction = model.predict(position, sink);
        // OTIS policy uses the compressed attention layout with the identity transform.
        List<PolicyMove> policy = decodeCompressedPolicy(position, prediction.policy(), 0, top);
        return new Trace("otis", scalarToCentipawns(prediction.value()), Double.valueOf(prediction.value()),
                prediction.wdl(), policy, sink.layers());
    }

    /**
     * Decodes top legal moves from a raw CNN policy plane (73x64) by softmax over
     * legal moves, mirroring the workbench CNN view.
     *
     * @param position root position
     * @param policy raw CNN policy logits
     * @param top number of top moves
     * @return ranked policy moves
     */
    private static List<PolicyMove> decodeCnnPolicy(Position position, float[] policy, int top) {
        if (policy == null || top <= 0) {
            return List.of();
        }
        MoveList legal = position.legalMoves();
        List<short[]> kept = new ArrayList<>();
        List<Float> logits = new ArrayList<>();
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < legal.size(); i++) {
            short move = legal.get(i);
            int index = chess.nn.lc0.cnn.PolicyEncoder.rawPolicyIndex(position, move);
            if (index < 0 || index >= policy.length) {
                continue;
            }
            kept.add(new short[] { move });
            logits.add(policy[index]);
            max = Math.max(max, policy[index]);
        }
        if (kept.isEmpty()) {
            return List.of();
        }
        double sum = 0.0;
        for (float logit : logits) {
            sum += Math.exp(logit - max);
        }
        double inv = sum <= 0.0 ? 0.0 : 1.0 / sum;
        List<PolicyMove> out = new ArrayList<>(kept.size());
        for (int i = 0; i < kept.size(); i++) {
            double prob = Math.exp(logits.get(i) - max) * inv;
            out.add(policyMove(position, kept.get(i)[0], prob));
        }
        out.sort((x, y) -> Double.compare(y.probability(), x.probability()));
        return out.size() > top ? new ArrayList<>(out.subList(0, top)) : out;
    }

    /**
     * Decodes top legal moves from compressed attention-policy logits (BT4/OTIS).
     *
     * @param position root position
     * @param policy compressed policy logits
     * @param transform LC0 canonical transform used during inference
     * @param top number of top moves
     * @return ranked policy moves
     */
    private static List<PolicyMove> decodeCompressedPolicy(Position position, float[] policy, int transform,
            int top) {
        if (policy == null || top <= 0) {
            return List.of();
        }
        List<PolicyEncoder.ScoredMove> scored = PolicyEncoder.topLegalMoves(position, policy, transform, top);
        List<PolicyMove> out = new ArrayList<>(scored.size());
        for (PolicyEncoder.ScoredMove move : scored) {
            out.add(policyMove(position, move.move(), move.probability()));
        }
        return out;
    }

    /**
     * Builds a policy-move record with SAN/UCI notation.
     *
     * @param position root position
     * @param move encoded move
     * @param probability move probability
     * @return policy move
     */
    private static PolicyMove policyMove(Position position, short move, double probability) {
        MoveInference.Notation notation = MoveInference.notation(position, move);
        return new PolicyMove(notation.san(), notation.uci(), probability);
    }

    /**
     * Converts a value scalar in {@code [-1, 1]} to centipawns using the engine's
     * logistic mapping.
     *
     * @param value value scalar
     * @return centipawns
     */
    private static Integer scalarToCentipawns(double value) {
        double v = Math.max(-0.999, Math.min(0.999, value));
        return Integer.valueOf((int) Math.round(CP_SCALE * 0.5 * Math.log((1.0 + v) / (1.0 - v))));
    }

    /**
     * Prints the human-readable trace.
     *
     * @param position root position
     * @param trace collected trace
     */
    private static void printText(Position position, Trace trace) {
        System.out.println("FEN: " + position);
        System.out.println("backend: " + trace.backend());
        if (trace.centipawns() != null) {
            System.out.printf(Locale.ROOT, "value: %+dcp%s%n", trace.centipawns(),
                    trace.valueScalar() == null ? ""
                            : String.format(Locale.ROOT, "  (scalar %+.4f)", trace.valueScalar()));
        }
        if (trace.wdl() != null && trace.wdl().length >= 3) {
            System.out.printf(Locale.ROOT, "wdl: win %.1f%%  draw %.1f%%  loss %.1f%%%n",
                    trace.wdl()[0] * 100.0, trace.wdl()[1] * 100.0, trace.wdl()[2] * 100.0);
        }
        if (!trace.policy().isEmpty()) {
            System.out.println("policy (top legal moves):");
            for (PolicyMove move : trace.policy()) {
                System.out.printf(Locale.ROOT, "  %-7s %-6s %5.1f%%%n",
                        move.san(), move.uci(), move.probability() * 100.0);
            }
        }
        if (!trace.layers().isEmpty()) {
            System.out.println("layers (" + trace.layers().size() + "):");
            for (Layer layer : trace.layers()) {
                System.out.printf(Locale.ROOT, "  %-26s %-14s rms=%.4f mean=%+.4f min=%+.4f max=%+.4f%n",
                        layer.key(), shapeText(layer.shape()), layer.rms(), layer.mean(), layer.min(), layer.max());
            }
        }
    }

    /**
     * Renders the trace as one JSON object.
     *
     * @param position root position
     * @param trace collected trace
     * @return JSON object
     */
    private static String toJson(Position position, Trace trace) {
        StringBuilder out = new StringBuilder(512);
        out.append('{');
        out.append("\"fen\":").append(CommandSupport.jsonString(position.toString()));
        out.append(",\"backend\":").append(CommandSupport.jsonString(trace.backend()));
        out.append(",\"centipawns\":").append(trace.centipawns() == null ? "null" : trace.centipawns());
        out.append(",\"value\":").append(trace.valueScalar() == null ? "null"
                : String.format(Locale.ROOT, "%.5f", trace.valueScalar()));
        out.append(",\"wdl\":");
        if (trace.wdl() == null || trace.wdl().length < 3) {
            out.append("null");
        } else {
            out.append('{')
                    .append("\"win\":").append(String.format(Locale.ROOT, "%.5f", trace.wdl()[0]))
                    .append(",\"draw\":").append(String.format(Locale.ROOT, "%.5f", trace.wdl()[1]))
                    .append(",\"loss\":").append(String.format(Locale.ROOT, "%.5f", trace.wdl()[2]))
                    .append('}');
        }
        out.append(",\"policy\":[");
        for (int i = 0; i < trace.policy().size(); i++) {
            PolicyMove move = trace.policy().get(i);
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"san\":").append(CommandSupport.jsonString(move.san()))
                    .append(",\"uci\":").append(CommandSupport.jsonString(move.uci()))
                    .append(",\"probability\":").append(String.format(Locale.ROOT, "%.5f", move.probability()))
                    .append('}');
        }
        out.append("],\"layers\":[");
        for (int i = 0; i < trace.layers().size(); i++) {
            Layer layer = trace.layers().get(i);
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"key\":").append(CommandSupport.jsonString(layer.key()))
                    .append(",\"shape\":").append(shapeJson(layer.shape()))
                    .append(",\"rms\":").append(String.format(Locale.ROOT, "%.6f", layer.rms()))
                    .append(",\"mean\":").append(String.format(Locale.ROOT, "%.6f", layer.mean()))
                    .append(",\"min\":").append(String.format(Locale.ROOT, "%.6f", layer.min()))
                    .append(",\"max\":").append(String.format(Locale.ROOT, "%.6f", layer.max()))
                    .append('}');
        }
        out.append("]}");
        return out.toString();
    }

    /**
     * Formats a shape array as {@code [a x b x c]}.
     *
     * @param shape shape dimensions
     * @return text shape
     */
    private static String shapeText(int[] shape) {
        if (shape == null || shape.length == 0) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < shape.length; i++) {
            if (i > 0) {
                out.append('x');
            }
            out.append(shape[i]);
        }
        return out.append(']').toString();
    }

    /**
     * Formats a shape array as a JSON number array.
     *
     * @param shape shape dimensions
     * @return JSON array
     */
    private static String shapeJson(int[] shape) {
        if (shape == null) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < shape.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(shape[i]);
        }
        return out.append(']').toString();
    }

    /**
     * Collected neural trace for a position.
     *
     * @param backend backend label
     * @param centipawns centipawn value, or {@code null}
     * @param valueScalar value scalar in {@code [-1, 1]}, or {@code null}
     * @param wdl win/draw/loss probabilities, or {@code null}
     * @param policy ranked top policy moves
     * @param layers per-layer activation summaries
     */
    private record Trace(
            String backend,
            Integer centipawns,
            Double valueScalar,
            float[] wdl,
            List<PolicyMove> policy,
            List<Layer> layers) {
    }

    /**
     * One ranked policy move.
     *
     * @param san move SAN
     * @param uci move UCI
     * @param probability softmax probability over legal moves
     */
    private record PolicyMove(String san, String uci, double probability) {
    }

    /**
     * Summary statistics for one captured activation tensor.
     *
     * @param key activation key
     * @param shape tensor shape
     * @param rms root-mean-square of the values
     * @param mean mean of the values
     * @param min minimum value
     * @param max maximum value
     */
    private record Layer(String key, int[] shape, double rms, double mean, double min, double max) {
    }

    /**
     * Activation sink that records per-key summary statistics headlessly.
     */
    private static final class RecordingSink implements ActivationSink {

        /**
         * Captured layers in insertion order.
         */
        private final Map<String, Layer> byKey = new LinkedHashMap<>();

        /**
         * First recorded value per key, used for scalar metadata such as the BT4 transform.
         */
        private final Map<String, Float> firstByKey = new LinkedHashMap<>();

        /**
         * {@inheritDoc}
         */
        @Override
        public void put(String key, int[] shape, float[] data) {
            if (key == null || data == null) {
                return;
            }
            firstByKey.put(key, data.length > 0 ? data[0] : 0.0f);
            double sum = 0.0;
            double sumSquares = 0.0;
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (float value : data) {
                sum += value;
                sumSquares += (double) value * value;
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            int count = data.length;
            double mean = count == 0 ? 0.0 : sum / count;
            double rms = count == 0 ? 0.0 : Math.sqrt(sumSquares / count);
            int[] shapeCopy = shape == null ? new int[0] : shape.clone();
            byKey.put(key, new Layer(key, shapeCopy, rms, mean,
                    count == 0 ? 0.0 : min, count == 0 ? 0.0 : max));
        }

        /**
         * Returns the first recorded value for a key, or zero when absent.
         *
         * @param key activation key
         * @return first recorded value
         */
        float firstValue(String key) {
            Float value = firstByKey.get(key);
            return value == null ? 0.0f : value.floatValue();
        }

        /**
         * Returns the captured layers in insertion order.
         *
         * @return captured layers
         */
        List<Layer> layers() {
            return List.copyOf(byKey.values());
        }
    }
}
