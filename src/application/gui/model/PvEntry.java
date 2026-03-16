package application.gui.model;

/**
 * Captures a single PV line for rendering.
 *
 * Holds the PV index, SAN move string, evaluation label, and depth so the renderer can display each entry in the list with the right chips.
 *
 * @param pv PV index.
 * @param moves SAN move string.
 * @param eval evaluation label.
 * @param depth depth label.
  * @since 2026
  * @author Lennart A. Conrad
 */
public record PvEntry(int pv, String moves, String eval, String depth) {
}
