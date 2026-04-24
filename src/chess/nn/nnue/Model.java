package chess.nn.nnue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import chess.core.Position;

/**
 * Convenience wrapper for evaluating {@link Position} objects with NNUE
 * weights.
 *
 * <p>
 * See the package documentation for the architecture overview. This wrapper
 * auto-detects the compact CRTK NNUE format and supported Stockfish NNUE
 * files, then exposes one prediction API over both.
 * </p>
 *
 * <p>
 * When used by the built-in engine without explicit weights,
 * {@link #loadDefaultOrFallback()} can supply a neutral fallback model for
 * smoke testing.
 * </p>
 */
public final class Model implements AutoCloseable {

    /**
     * Optional per-search NNUE state for incremental evaluation.
     */
    public interface SearchState extends AutoCloseable {

        /**
         * Notifies the state that a normal move has just been played.
         *
         * @param position child position after the move
         * @param move encoded move that was played
         * @param state undo state filled by the move application
         * @param ply child ply from the root
         */
        void movePlayed(Position position, short move, Position.State state, int ply);

        /**
         * Notifies the state that a null move has just been played.
         *
         * @param ply child ply from the root
         */
        default void nullMovePlayed(int ply) {
            // default search state does not need null-move handling
        }

        /**
         * Evaluates a position using the incremental state at one ply.
         *
         * @param position current position
         * @param ply current ply from the root
         * @return centipawn score
         */
        int evaluate(Position position, int ply);

        /**
         * Releases per-search resources.
         */
        @Override
        default void close() {
            // default search state has no resources
        }
    }

    /**
     * Default NNUE weights path used by helpers and examples.
     */
    public static final Path DEFAULT_WEIGHTS = Path.of("models/crtk-halfkp.nnue");

    /**
     * Underlying CRTK-format NNUE network. This field is null when the wrapper
     * is backed by a Stockfish-format network.
     */
    private final Network network;

    /**
     * Underlying Stockfish-format NNUE network. This field is null when the
     * wrapper is backed by a CRTK-format network.
     */
    private final UpstreamNetwork upstreamNetwork;

    /**
     * Creates a wrapper around a loaded CRTK network.
     *
     * @param network underlying network
     */
    private Model(Network network) {
        this.network = network;
        this.upstreamNetwork = null;
    }

    /**
     * Creates a wrapper around a loaded Stockfish network.
     *
     * @param upstreamNetwork underlying Stockfish network
     */
    private Model(UpstreamNetwork upstreamNetwork) {
        this.network = null;
        this.upstreamNetwork = upstreamNetwork;
    }

    /**
     * Loads a model from NNUE weights.
     *
     * @param weightsPath path to CRTK or supported Stockfish NNUE weights
     * @return loaded model
     * @throws IOException if the weights cannot be read or parsed
     */
    public static Model load(Path weightsPath) throws IOException {
        byte[] header = Files.readAllBytes(weightsPath);
        if (UpstreamNetwork.hasUpstreamHeader(header)) {
            return new Model(UpstreamNetwork.load(weightsPath));
        }
        return new Model(Network.load(weightsPath));
    }

    /**
     * Loads {@link #DEFAULT_WEIGHTS}.
     *
     * @return loaded model
     * @throws IOException if the weights cannot be read or parsed
     */
    public static Model loadDefault() throws IOException {
        return load(DEFAULT_WEIGHTS);
    }

    /**
     * Loads {@link #DEFAULT_WEIGHTS} when present, otherwise returns a tiny
     * deterministic fallback network.
     *
     * <p>
     * The fallback keeps NNUE-backed commands usable in clean checkouts where no
     * local NNUE artifact has been downloaded yet. It is a neutral smoke-test
     * evaluator, not a trained chess network.
     * </p>
     *
     * @return loaded default model or fallback model
     * @throws IOException if the default file exists but cannot be read or parsed
     */
    public static Model loadDefaultOrFallback() throws IOException {
        if (Files.isRegularFile(DEFAULT_WEIGHTS)) {
            return loadDefault();
        }
        return fallback();
    }

    /**
     * Creates a tiny neutral fallback model.
     *
     * <p>
     * The fallback has one hidden unit, zero feature weights, and unit output
     * scale, so every position evaluates to a neutral score.
     * </p>
     *
     * @return fallback model
     */
    public static Model fallback() {
        int hidden = 1;
        return new Model(Network.create(
                hidden,
                new float[hidden],
                new float[FeatureEncoder.FEATURE_COUNT * hidden],
                new float[hidden * 2],
                0.0f,
                1.0f));
    }

    /**
     * Returns model metadata.
     *
     * @return network metadata
     */
    public Network.Info info() {
        if (upstreamNetwork != null) {
            UpstreamNetwork.Info info = upstreamNetwork.info();
            return new Network.Info(info.inputFeatures(), info.transformedDimensions(), 0L);
        }
        return network.info();
    }

    /**
     * Returns Stockfish-specific metadata when this model wraps a Stockfish NNUE
     * file.
     *
     * @return Stockfish metadata, or {@code null} for CRTK-format networks
     */
    public UpstreamNetwork.Info upstreamInfo() {
        return upstreamNetwork == null ? null : upstreamNetwork.info();
    }

    /**
     * Returns the active backend name.
     *
     * @return backend identifier
     */
    public String backend() {
        if (upstreamNetwork != null) {
            return upstreamNetwork.backendName();
        }
        return network.backendName();
    }

    /**
     * Evaluates a position and returns the raw prediction.
     *
     * @param position position to evaluate
     * @return NNUE prediction
     */
    public Network.Prediction predict(Position position) {
        if (upstreamNetwork != null) {
            return new Network.Prediction(upstreamNetwork.predict(position).centipawns());
        }
        return network.predict(position);
    }

    /**
     * Evaluates a position and returns rounded centipawns.
     *
     * @param position position to evaluate
     * @return centipawn score from the side-to-move perspective
     */
    public int evaluateCentipawns(Position position) {
        return predict(position).roundedCentipawns();
    }

    /**
     * Opens optional per-search incremental state.
     *
     * @param root root position at ply 0
     * @param searchPlies maximum ply count the search may reach
     * @return incremental state, or {@code null} when unsupported
     */
    public SearchState newSearchState(Position root, int searchPlies) {
        if (root == null) {
            throw new IllegalArgumentException("root == null");
        }
        if (searchPlies <= 0) {
            throw new IllegalArgumentException("searchPlies must be positive");
        }
        if (upstreamNetwork != null) {
            return upstreamNetwork.newSearchState(root, searchPlies);
        }
        return network.newSearchState(root, searchPlies);
    }

    /**
     * Creates an accumulator for repeated or incremental evaluation.
     *
     * @return fresh accumulator
     */
    public Accumulator newAccumulator() {
        if (upstreamNetwork != null) {
            throw new UnsupportedOperationException("Stockfish NNUE accumulators are internal to UpstreamNetwork.");
        }
        return network.newAccumulator();
    }

    /**
     * Creates an accumulator initialized from a position.
     *
     * @param position position to encode
     * @return initialized accumulator
     */
    public Accumulator newAccumulator(Position position) {
        if (upstreamNetwork != null) {
            throw new UnsupportedOperationException("Stockfish NNUE accumulators are internal to UpstreamNetwork.");
        }
        return network.newAccumulator(position);
    }

    /**
     * Evaluates a prepared accumulator.
     *
     * @param accumulator accumulator created by this model
     * @param whiteToMove true when White is the side to move
     * @return NNUE prediction
     */
    public Network.Prediction predict(Accumulator accumulator, boolean whiteToMove) {
        if (upstreamNetwork != null) {
            throw new UnsupportedOperationException("Stockfish NNUE accumulator prediction is not exposed by Model.");
        }
        return network.predict(accumulator, whiteToMove);
    }

    /**
     * Releases backend resources.
     */
    @Override
    public void close() {
        if (upstreamNetwork != null) {
            upstreamNetwork.close();
        } else {
            network.close();
        }
    }
}
