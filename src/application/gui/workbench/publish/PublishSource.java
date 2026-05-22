/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.publish;

/**
 * Diagram source type with stable identity and display label.
 */
public enum PublishSource {

    /** The current board position. */
    CURRENT_FEN("Current FEN"),
    /** The workbench game PGN. */
    GAME_PGN("Game PGN"),
    /** The batch FEN editor. */
    BATCH_FENS("Batch FENs"),
    /** A user-selected file. */
    EXISTING_FILE("Existing File");

    /**
     * Combo-box label.
     */
    private final String label;

    /**
     * Creates a publish source.
     *
     * @param label display label
     */
    PublishSource(String label) {
        this.label = label;
    }

    /**
     * Returns the display label.
     *
     * @return display label
     */
    @Override
    public String toString() {
        return label;
    }
}
