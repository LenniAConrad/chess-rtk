/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.publish;

/**
 * Publishing task type with stable identity and display label.
 */
public enum PublishTask {

    /** Direct diagram PDFs. */
    DIAGRAMS("Diagrams PDF"),
    /** Render a manifest PDF. */
    RENDER("Render Manifest PDF"),
    /** Build a puzzle collection. */
    COLLECTION("Puzzle Collection"),
    /** Render a study book. */
    STUDY("Study Book"),
    /** Render a cover PDF. */
    COVER("Cover PDF");

    /**
     * Combo-box label.
     */
    private final String label;

    /**
     * Creates a publish task.
     *
     * @param label display label
     */
    PublishTask(String label) {
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
