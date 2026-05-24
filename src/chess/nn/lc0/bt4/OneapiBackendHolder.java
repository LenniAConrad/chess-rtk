/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package chess.nn.lc0.bt4;

/**
 * Helper that owns a oneAPI backend until detached or closed.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class OneapiBackendHolder implements AutoCloseable {

    /**
     * Owned backend instance.
     */
    chess.nn.lc0.bt4.oneapi.Backend backend;

    /**
     * Creates a holder.
     *
     * @param backend backend instance
     */
    OneapiBackendHolder(chess.nn.lc0.bt4.oneapi.Backend backend) {
        this.backend = backend;
    }

    /**
     * Transfers ownership to the caller.
     */
    void detach() {
        backend = null;
    }

    /**
     * Closes the backend when ownership was not detached.
     */
    @Override
    public void close() {
        if (backend != null) {
            backend.close();
        }
    }
}
