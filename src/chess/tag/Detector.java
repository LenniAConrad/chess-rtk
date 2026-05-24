package chess.tag;

/**
 * Contract for one focused tag detector.
 * <p>
 * Detectors should be deterministic functions over the supplied context and
 * should write canonical tag lines through the emitter. Engine or config access
 * belongs only in detectors explicitly built for those sources.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
@FunctionalInterface
interface Detector {

    /**
     * Adds this detector's tags to the emitter.
     *
     * @param context shared tagging context
     * @param out tag emitter
     */
    void addTags(Context context, Emitter out);
}
