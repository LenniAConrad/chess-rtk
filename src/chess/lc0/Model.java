package chess.lc0;

import chess.core.Position;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Convenience wrapper around {@link Network} that encodes {@link Position} and runs inference.
 *
 * <p>This is the highest-level entry point for “evaluate this {@link Position} with LC0”.
 * It uses {@link Encoder} internally and returns {@link Network.Prediction}.
 *
 * <p>Backend selection is controlled by JVM system properties:
 * <ul>
 *   <li>{@code -Ducicli.lc0.backend=auto|cpu|cuda}</li>
 *   <li>{@code -Ducicli.lc0.threads=N} (CPU only)</li>
 *   <li>{@code -Djava.library.path=...} (required to load the CUDA JNI library)</li>
 * </ul>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Model implements AutoCloseable {
    
    /**
     * Default LC0J weights path used by the CLI helpers and examples.
     */
    public static final Path DEFAULT_WEIGHTS = Path.of("models/lc0_744706.bin");

    /**
     * Underlying evaluator (CPU or CUDA, selected at load time).
     */
    private final Network network;

    /**
     * Creates a wrapper around a loaded {@link Network}.
     *
     * @param network underlying evaluator (CPU or CUDA)
     */
    private Model(Network network) {
        this.network = network;
    }

    /**
     * Loads a model from an LC0J {@code .bin} file.
     *
     * @param weightsBin path to weights
     * @return model wrapper
     * @throws IOException if the weights cannot be read/parsed, or if CUDA is forced and initialization fails
     */
    public static Model load(Path weightsBin) throws IOException {
        return new Model(Network.load(weightsBin));
    }

    /**
     * Loads {@link #DEFAULT_WEIGHTS}.
     *
     * @return model wrapper
     * @throws IOException if the weights cannot be read/parsed, or if CUDA is forced and initialization fails
     */
    public static Model loadDefault() throws IOException {
        return load(DEFAULT_WEIGHTS);
    }

    /**
     * Returns basic network metadata (shape and parameter count).
     *
     * @return network metadata
     */
    public Network.Info info() {
        return network.info();
    }

    /**
     * Returns the active backend name ({@code "cpu"} or {@code "cuda"}).
     *
     * @return active backend identifier
     */
    public String backend() {
        return network.backend();
    }

    /**
     * Encodes a {@link Position} using {@link Encoder} and runs inference.
     *
     * @param position position to evaluate
     * @return policy logits, WDL probabilities, and scalar {@code W-L} value
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
     * Runs inference on already-encoded LC0 planes.
     *
     * @param encodedPlanes encoded planes, length {@code inputChannels * 64}
     * @return policy logits, WDL probabilities, and scalar {@code W-L} value
     */
    public Network.Prediction predictEncoded(float[] encodedPlanes) {
        return network.predictEncoded(encodedPlanes);
    }

    /**
     * Releases backend resources (notably CUDA device memory when CUDA is active).
     */
    @Override
    public void close() {
        network.close();
    }
}
