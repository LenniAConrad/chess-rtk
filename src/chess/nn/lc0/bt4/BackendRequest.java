package chess.nn.lc0.bt4;

import java.io.IOException;

/**
 * Parsed BT4 backend preference from system properties.
 *
 * <p>
 * The BT4-specific property has priority. If absent, BT4 follows the shared
 * LC0 backend property used by the CNN evaluator.
 * </p>
 *
 * @param preferCuda whether CUDA should be attempted
 * @param preferRocm whether ROCm should be attempted
 * @param preferOneapi whether oneAPI should be attempted
 * @param forceCuda whether CUDA was explicitly requested
 * @param forceRocm whether ROCm was explicitly requested
 * @param forceOneapi whether oneAPI was explicitly requested
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
record BackendRequest(
        /**
         * Whether CUDA should be attempted.
         */
        boolean preferCuda,
        /**
         * Whether ROCm should be attempted.
         */
        boolean preferRocm,
        /**
         * Whether oneAPI should be attempted.
         */
        boolean preferOneapi,
        /**
         * Whether CUDA was explicitly requested.
         */
        boolean forceCuda,
        /**
         * Whether ROCm was explicitly requested.
         */
        boolean forceRocm,
        /**
         * Whether oneAPI was explicitly requested.
         */
        boolean forceOneapi) {

    /**
     * BT4-specific backend selection property.
     */
    private static final String BT4_BACKEND_PROPERTY = "crtk.lc0.bt4.backend";

    /**
     * Shared LC0 backend selection property used as a fallback.
     */
    private static final String LC0_BACKEND_PROPERTY = "crtk.lc0.backend";

    /**
     * Automatic backend-selection token.
     */
    private static final String BACKEND_AUTO = "auto";

    /**
     * ROCm compatibility token accepted by earlier command lines.
     */
    private static final String BACKEND_AMD = "amd";

    /**
     * HIP compatibility token accepted as a ROCm alias.
     */
    private static final String BACKEND_HIP = "hip";

    /**
     * Intel compatibility token accepted as a oneAPI alias.
     */
    private static final String BACKEND_INTEL = "intel";

    /**
     * Parses backend system properties.
     *
     * @return parsed backend request
     */
    static BackendRequest fromSystemProperties() {
        String backend = System.getProperty(BT4_BACKEND_PROPERTY);
        if (backend == null) {
            backend = System.getProperty(LC0_BACKEND_PROPERTY);
        }
        if (backend == null) {
            backend = BACKEND_AUTO;
        }
        backend = backend.trim().toLowerCase();
        boolean auto = backend.equals(BACKEND_AUTO);
        boolean cuda = backend.equals(Network.BACKEND_CUDA);
        boolean rocm = backend.equals(Network.BACKEND_ROCM)
                || backend.equals(BACKEND_AMD)
                || backend.equals(BACKEND_HIP);
        boolean oneapi = backend.equals(Network.BACKEND_ONEAPI) || backend.equals(BACKEND_INTEL);
        boolean cpu = backend.equals(Network.BACKEND_CPU);
        if (cpu) {
            return new BackendRequest(false, false, false, false, false, false);
        }
        return new BackendRequest(auto || cuda, auto || rocm, auto || oneapi, cuda, rocm, oneapi);
    }

    /**
     * Throws when a forced backend is unavailable.
     *
     * @param availability detected backend availability
     * @throws IOException if a forced backend is unavailable
     */
    void requireAvailable(BackendAvailability availability) throws IOException {
        requireBackendAvailable(forceCuda, availability.cuda(),
                "BT4 CUDA backend requested but unavailable (JNI library not loaded and/or no CUDA device).");
        requireBackendAvailable(forceRocm, availability.rocm(),
                "BT4 ROCm backend requested but unavailable (JNI library not loaded and/or no ROCm device).");
        requireBackendAvailable(forceOneapi, availability.oneapi(),
                "BT4 oneAPI backend requested but unavailable (JNI library not loaded and/or no Intel GPU device).");
    }

    /**
     * Throws when a forced backend is unavailable.
     *
     * @param forced whether the backend was explicitly requested
     * @param available whether the backend is available
     * @param message exception message
     * @throws IOException if the forced backend is unavailable
     */
    private static void requireBackendAvailable(boolean forced, boolean available, String message) throws IOException {
        if (forced && !available) {
            throw new IOException(message);
        }
    }
}
