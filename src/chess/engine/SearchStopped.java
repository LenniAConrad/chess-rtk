package chess.engine;



/**
 * Exception used to stop recursive search without stack traces.
 */
final class SearchStopped extends RuntimeException {

    /**
     * Serialization identifier required by {@link RuntimeException}.
     */
static final long serialVersionUID = 1L;

    /**
     * Creates the singleton stop exception.
     */
SearchStopped() {
        super(null, null, false, false);
    }
}
