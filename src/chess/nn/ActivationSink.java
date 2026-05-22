/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package chess.nn;

/**
 * Optional collector for intermediate activation tensors emitted during a
 * forward pass.
 *
 * <p>
 * Network classes accept an optional {@code ActivationSink} parameter on
 * their inference entry points. When non-null, the network calls
 * {@link #put(String, int[], float[])} at well-known intermediate points
 * (input embeddings, per-block attention/FFN outputs, policy logits, etc.).
 * Production callers pass {@code null} and pay only the null-check cost.
 * </p>
 *
 * <p>
 * Sinks must defensively copy the data they want to keep; the network is free
 * to recycle buffers immediately after the call.
 * </p>
 */
@FunctionalInterface
public interface ActivationSink {

    /**
     * Stores one captured tensor.
     *
     * @param key stable activation key (network-specific namespace)
     * @param shape logical tensor shape in row-major order
     * @param data flat row-major values; length equals the product of shape
     */
    void put(String key, int[] shape, float[] data);
}
