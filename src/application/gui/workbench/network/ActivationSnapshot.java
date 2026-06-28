package application.gui.workbench.network;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keyed collection of activation tensors emitted by an NN forward pass.
 *
 * <p>Mirrors the layout of the cpp-nn-visualizer ActivationSnapshot so the
 * Workbench network views can be swapped from synthetic to real inference
 * later by replacing the producer only.</p>
 *
 * <p>Implements {@link chess.nn.ActivationSink} so the snapshot doubles as a
 * collector that real network forward passes can write into.</p>
 */
public final class ActivationSnapshot implements chess.nn.ActivationSink {

    /**
     * One captured tensor: logical shape plus flat row-major values.
     */
    public static final class Entry {

        /**
         * Tensor shape in row-major logical order.
         */
        private final int[] shape;

        /**
         * Flat tensor values. Length equals the product of shape.
         */
        private final float[] data;

        /**
         * Creates a new entry.
         *
         * @param shape tensor shape (each dimension &gt; 0)
         * @param data flat row-major values
         */
        Entry(int[] shape, float[] data) {
            this.shape = shape.clone();
            this.data = data;
        }

        /**
         * Returns the tensor shape.
         *
         * @return shape
         */
    int[] shape() {
            return shape;
        }

        /**
         * Returns the flat values.
         *
         * @return data
         */
    float[] data() {
            return data;
        }

        /**
         * Returns a compact shape label such as "32x64x64".
         *
         * @return shape label
         */
    String shapeText() {
            if (shape.length == 0) {
                return "-";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < shape.length; ++i) {
                if (i > 0) {
                    sb.append('x');
                }
                sb.append(shape[i]);
            }
            return sb.toString();
        }
    }

    /**
     * Keyed entries. Linked to keep insertion order in debug iteration.
     */
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /**
     * When true the snapshot is frozen: {@link #put} and {@link #clear} throw.
     *
     * <p>Inference runs off the EDT, then the finished snapshot is handed to a
     * view that paints it on the EDT. Sealing the snapshot the moment it leaves
     * the producer turns any accidental later mutation into a loud, immediate
     * failure instead of a silent cross-thread data race or a torn read during
     * paint.</p>
     */
    private boolean sealed;

    /**
     * Freezes the snapshot so it can be safely published to another thread.
     * Idempotent; once sealed a snapshot stays sealed for life.
     */
    public void seal() {
        sealed = true;
    }

    /**
     * Returns whether the snapshot has been frozen by {@link #seal()}.
     *
     * @return true when sealed
     */
    public boolean isSealed() {
        return sealed;
    }

    /**
     * Removes every captured tensor.
     *
     * @throws IllegalStateException when the snapshot has been sealed
     */
    public void clear() {
        requireMutable();
        entries.clear();
    }

    /**
     * Throws when the snapshot has been sealed against further mutation.
     */
    private void requireMutable() {
        if (sealed) {
    throw new IllegalStateException("activation snapshot is sealed and cannot be mutated");
        }
    }

    /**
     * Stores an entry under a stable key. Replaces any previous entry. Also
     * the {@link chess.nn.ActivationSink} implementation entry point.
     *
     * @param key stable activation key
     * @param shape tensor shape
     * @param data flat values; length must equal the product of shape
     * @throws IllegalStateException when the snapshot has been sealed
     */
    @Override
    public void put(String key, int[] shape, float[] data) {
        requireMutable();
        int expected = 1;
        for (int dim : shape) {
            expected *= dim;
        }
        if (expected != data.length) {
    throw new IllegalArgumentException(
                    "shape product " + expected + " != data length " + data.length + " for key " + key);
        }
        entries.put(key, new Entry(shape, data));
    }

    /**
     * Stores a scalar entry.
     *
     * @param key stable activation key
     * @param value scalar value
     */
    public void putScalar(String key, float value) {
        put(key, new int[] { 1 }, new float[] { value });
    }

    /**
     * Tests whether a key is present.
     *
     * @param key activation key
     * @return true when present
     */
    public boolean has(String key) {
        return entries.containsKey(key);
    }

    /**
     * Returns the entry for a key.
     *
     * @param key activation key
     * @return entry or null when missing
     */
    public Entry get(String key) {
        return entries.get(key);
    }

    /**
     * Returns the flat values for a key.
     *
     * @param key activation key
     * @return data or null when missing
     */
    public float[] data(String key) {
        Entry entry = entries.get(key);
        return entry == null ? null : entry.data();
    }

    /**
     * Returns the shape for a key.
     *
     * @param key activation key
     * @return shape or an empty array when missing
     */
    public int[] shape(String key) {
        Entry entry = entries.get(key);
        return entry == null ? new int[0] : entry.shape();
    }

    /**
     * Exposes the read-only entry map for generic inspection.
     *
     * @return entries
     */
    public Map<String, Entry> entries() {
        return Collections.unmodifiableMap(entries);
    }

    /**
     * Returns whether the snapshot has any entries.
     *
     * @return true when empty
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

}
