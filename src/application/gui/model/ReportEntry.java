package application.gui.model;

import application.gui.window.PgnNode;

/**
 * Row in the game analysis report list.
 *
 * Packages the PGN node with evaluation, loss, NAG, and loss-cap information so report renderers can describe why a move was flagged.
 *
 * @param node PGN node referenced by this row.
 * @param prefix move number prefix.
 * @param san SAN text shown in the row.
 * @param eval evaluation label.
 * @param loss loss label.
 * @param nag NAG string.
 * @param lossCp loss in centipawns when available.
  * @since 2026
  * @author Lennart A. Conrad
 */
public record ReportEntry(PgnNode node, String prefix, String san, String eval, String loss, String nag, Integer lossCp) {
}
