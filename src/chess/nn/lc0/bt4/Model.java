package chess.nn.lc0.bt4;

import java.io.IOException;
import java.nio.file.Path;

import chess.core.Position;

/**
 * Convenience wrapper around a BT4 {@link Network}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Model implements AutoCloseable {

    /**
     * Underlying network.
     */
    private final Network network;

    /**
     * Creates a model wrapper.
     *
     * @param network underlying network
     */
    private Model(Network network) {
        this.network = network;
    }

    /**
     * Wraps an already-created BT4 network.
     *
     * @param network underlying network
     * @return model wrapper
     */
    public static Model of(Network network) {
        if (network == null) {
            throw new IllegalArgumentException("network == null");
        }
        return new Model(network);
    }

    /**
     * Loads a model from the compact CRTK BT4 binary format.
     *
     * @param weightsBin path to the model file
     * @return loaded model
     * @throws IOException if the file cannot be read or parsed
     */
    public static Model load(Path weightsBin) throws IOException {
        return new Model(Network.load(weightsBin));
    }

    /**
     * Returns architecture and shape metadata.
     *
     * @return metadata
     */
    public Network.Info info() {
        return network.info();
    }

    /**
     * Encodes and evaluates a position.
     *
     * @param position source position
     * @return prediction
     */
    public Network.Prediction predict(Position position) {
        return network.predict(position);
    }

    /**
     * Evaluates already-encoded LC0 planes.
     *
     * @param encodedPlanes channel-major input planes
     * @return prediction
     */
    public Network.Prediction predictEncoded(float[] encodedPlanes) {
        return network.predictEncoded(encodedPlanes);
    }

    /**
     * Releases resources.
     */
    @Override
    public void close() {
        network.close();
    }
}
