package chess.describe;

import java.nio.file.Path;

/**
 * Configuration-ready T5 position-description stub.
 *
 * <p>
 * The shared input and prompt path are in place, but no trained
 * position-description weights are shipped yet. This class deliberately refuses
 * to generate text so callers cannot accidentally present fake T5 output.
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
     * Returns the token budget.
     *
     * @return max new tokens
     */
    public int maxNewTokens() {
        return maxNewTokens;
    }

    /**
     * Returns whether a trained model is currently available.
     *
     * @return always false until a trained position-description export exists
     */
    public boolean available() {
        return false;
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
     * Refuses to generate T5 text until a trained model path is defined.
     *
     * @param input structured input
     * @param detail requested detail
     * @return never returns
     */
    public String generate(PositionDescriptionInput input, PositionDescriptionDetail detail) {
        throw new IllegalStateException(unavailableMessage());
    }

    /**
     * User-facing unavailable diagnostic.
     *
     * @return diagnostic text
     */
    public String unavailableMessage() {
        String path = modelPath == null ? "<unset>" : modelPath.toString();
        return "T5 position-description generation is unavailable: no trained position-description "
                + "T5 weights exist yet (configured model path: " + path + "). Use --engine classical.";
    }
}
