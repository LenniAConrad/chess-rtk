package chess.nn.classifier;

import chess.core.Position;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Convenience wrapper around {@link Network} that encodes {@link Position} and
 * runs inference.
 */
public final class Model implements AutoCloseable {

    /**
     * Default classifier weights path used by helpers and examples.
     */
    public static final Path DEFAULT_WEIGHTS = Path.of("models/classifier_21planes-6blocksx64-head32-logit1.bin");

    /**
     * Underlying evaluator.
     */
    private final Network network;

    /**
     * Creates a wrapper around a loaded {@link Network}.
     *
     * @param network underlying evaluator
     */
    private Model(Network network) {
        this.network = network;
    }

    /**
     * Loads a model from a classifier {@code .bin} file.
     *
     * @param weightsBin path to weights
     * @return model wrapper
     * @throws IOException if the weights cannot be read or parsed
     */
    public static Model load(Path weightsBin) throws IOException {
        return new Model(Network.load(weightsBin));
    }

    /**
     * Loads {@link #DEFAULT_WEIGHTS}.
     *
     * @return model wrapper
     * @throws IOException if the weights cannot be read or parsed
     */
    public static Model loadDefault() throws IOException {
        return load(DEFAULT_WEIGHTS);
    }

    /**
     * Returns basic network metadata.
     *
     * @return network metadata
     */
    public Network.Info info() {
        return network.info();
    }

    /**
     * Returns the active backend name.
     *
     * @return active backend identifier
     */
    public String backend() {
        return Network.BACKEND;
    }

    /**
     * Encodes a {@link Position} using {@link Encoder} and runs inference.
     *
     * @param position position to classify
     * @return classifier output
     */
    public Network.Prediction predict(Position position) {
        float[] encoded = Encoder.encode(position);
        int expected = network.info().inputChannels() * 64;
        if (encoded.length != expected) {
            throw new IllegalStateException("Encoder produced " + encoded.length + " floats, expected " + expected);
        }
        return network.predictEncoded(encoded);
    }

    /**
     * Runs inference on already-encoded planes.
     *
     * @param encodedPlanes encoded planes, length {@code inputChannels * 64}
     * @return classifier output
     */
    public Network.Prediction predictEncoded(float[] encodedPlanes) {
        return network.predictEncoded(encodedPlanes);
    }

    /**
     * Releases backend resources.
     */
    @Override
    public void close() {
        network.close();
    }
}
