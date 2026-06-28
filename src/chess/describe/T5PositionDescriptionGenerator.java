package chess.describe;

import java.nio.file.Path;

/**
 * Configuration-ready T5 position-description prompt helper and unavailable sentinel.
 *
 * <p>
 * The shared input and prompt path are in place for training exports, but no
 * trained position-description weights are shipped yet. This class deliberately
 * refuses to generate text so callers cannot accidentally present fake T5 output.
 * </p>
 */
public final class T5PositionDescriptionGenerator {

    /**
     * Optional model path configured by CLI or application config.
     */
    private final Path modelPath;

    /**
     * Maximum generated token budget.
     */
    private final int maxNewTokens;

    /**
     * Creates the T5 stub.
     *
     * @param modelPath configured model path
     * @param maxNewTokens token budget
     */
    public T5PositionDescriptionGenerator(Path modelPath, int maxNewTokens) {
        this.modelPath = modelPath;
        this.maxNewTokens = Math.max(1, maxNewTokens);
    }

    /**
     * Returns the configured model path.
     *
     * @return model path, or null
     */
    public Path modelPath() {
        return modelPath;
    }

    /**
     * Builds the compact feature prompt intended for future T5 training/inference.
     *
     * @param input structured input
     * @param detail requested detail
     * @return prompt text
     */
    public String prompt(PositionDescriptionInput input, PositionDescriptionDetail detail) {
        PositionDescriptionDetail resolved = detail == null ? PositionDescriptionDetail.NORMAL : detail;
        return "describe_position detail=" + resolved.label()
                + " max_new=" + maxNewTokens
                + "\nfeatures: " + input.toJson()
                + "\ntext:";
    }

    /**
     * User-facing unavailable diagnostic.
     *
     * @return diagnostic text
     */
    public String unavailableMessage() {
        String path = modelPath == null ? "<unset>" : modelPath.toString();
        return "T5 position-description generation is unavailable: no trained position-description "
                + "T5 weights exist yet (configured model path: " + path + "). Use position describe for "
                + "classical prose, or fen text / puzzle text for the T5 tag-to-text runtime.";
    }
}
