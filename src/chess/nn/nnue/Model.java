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
 * {@link #load(Path)} auto-detects the simple CRTK NNUE format and supported
 * Stockfish NNUE files.
 * </p>
 */
public final class Model implements AutoCloseable {

    /**
     * Default NNUE weights path used by helpers and examples.
     */
    public static final Path DEFAULT_WEIGHTS = Path.of("models/crtk-halfkp.nnue");

    /**
     * Underlying CRTK NNUE network.
     */
    private final Network network;

    /**
     * Underlying Stockfish NNUE network.
     */
    private final StockfishNnueNetwork stockfishNetwork;

    /**
     * Creates a wrapper around a loaded CRTK network.
     *
     * @param network underlying network
     */
    private Model(Network network) {
        this.network = network;
        this.stockfishNetwork = null;
    }

    /**
     * Creates a wrapper around a loaded Stockfish network.
     *
     * @param stockfishNetwork underlying Stockfish network
     */
    private Model(StockfishNnueNetwork stockfishNetwork) {
        this.network = null;
        this.stockfishNetwork = stockfishNetwork;
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
        if (StockfishNnueNetwork.hasStockfishHeader(header)) {
            return new Model(StockfishNnueNetwork.load(weightsPath));
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
        if (Files.exists(DEFAULT_WEIGHTS)) {
            return loadDefault();
        }
        return fallback();
    }

    /**
     * Creates a tiny neutral fallback model.
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
        if (stockfishNetwork != null) {
            StockfishNnueNetwork.Info info = stockfishNetwork.info();
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
    public StockfishNnueNetwork.Info stockfishInfo() {
        return stockfishNetwork == null ? null : stockfishNetwork.info();
    }

    /**
     * Returns the active backend name.
     *
     * @return backend identifier
     */
    public String backend() {
        if (stockfishNetwork != null) {
            return stockfishNetwork.backend();
        }
        return network.backend();
    }

    /**
     * Evaluates a position and returns the raw prediction.
     *
     * @param position position to evaluate
     * @return NNUE prediction
     */
    public Network.Prediction predict(Position position) {
        if (stockfishNetwork != null) {
            return new Network.Prediction(stockfishNetwork.predict(position).centipawns());
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
     * Creates an accumulator for repeated or incremental evaluation.
     *
     * @return fresh accumulator
     */
    public Accumulator newAccumulator() {
        if (stockfishNetwork != null) {
            throw new UnsupportedOperationException("Stockfish NNUE accumulators are internal to StockfishNnueNetwork.");
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
        if (stockfishNetwork != null) {
            throw new UnsupportedOperationException("Stockfish NNUE accumulators are internal to StockfishNnueNetwork.");
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
        if (stockfishNetwork != null) {
            throw new UnsupportedOperationException("Stockfish NNUE accumulator prediction is not exposed by Model.");
        }
        return network.predict(accumulator, whiteToMove);
    }

    /**
     * Releases backend resources.
     */
    @Override
    public void close() {
        if (stockfishNetwork != null) {
            stockfishNetwork.close();
        } else {
            network.close();
        }
    }
}
