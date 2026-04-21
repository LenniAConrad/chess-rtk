package chess.gpu;

/**
 * Shared neural-network backend identifiers.
 */
public final class BackendNames {

    /**
     * CPU backend identifier.
     */
    public static final String CPU = "cpu";

    /**
     * CUDA backend identifier.
     */
    public static final String CUDA = "cuda";

    /**
     * ROCm backend identifier.
     */
    public static final String ROCM = "rocm";

    /**
     * oneAPI backend identifier.
     */
    public static final String ONEAPI = "oneapi";

     /**
     * Creates a new backend names instance.
     */
     private BackendNames() {
    }
}
