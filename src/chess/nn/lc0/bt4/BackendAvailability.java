package chess.nn.lc0.bt4;

/**
 * Detected native BT4 backend availability.
 *
 * @param cuda CUDA availability
 * @param rocm ROCm availability
 * @param oneapi oneAPI availability
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
record BackendAvailability(
        /**
         * Whether CUDA is available.
         */
        boolean cuda,
        /**
         * Whether ROCm is available.
         */
        boolean rocm,
        /**
         * Whether oneAPI is available.
         */
        boolean oneapi) {

    /**
     * Detects native backend availability.
     *
     * @return availability snapshot
     */
    static BackendAvailability detect() {
        return new BackendAvailability(
                chess.nn.lc0.bt4.cuda.Backend.isAvailable(),
                chess.nn.lc0.bt4.rocm.Backend.isAvailable(),
                chess.nn.lc0.bt4.oneapi.Backend.isAvailable());
    }
}
