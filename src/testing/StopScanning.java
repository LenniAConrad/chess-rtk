package testing;




/**
 * Internal control-flow signal used to stop scanning after a sample cap.
 */
final class StopScanning extends RuntimeException {

    /**
     * Avoids warning noise for this local control-flow exception.
     */
    private static final long serialVersionUID = 1L;
}
