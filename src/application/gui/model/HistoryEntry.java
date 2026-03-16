package application.gui.model;

import java.util.Locale;

import application.gui.window.PgnNode;

/**
 * Move tree row with SAN and indentation depth.
 *
 * Wraps a parsed PGN node so the UI can show indentation, SAN, and comment text in move lists while keeping track of the active row.
 *
 * @param node PGN node referenced by this row.
 * @param san SAN string for display.
 * @param prefix move number prefix (e.g., "12.").
 * @param depth depth level for indentation.
 * @param current whether the row represents the current ply.
  * @since 2026
  * @author Lennart A. Conrad
 */
public record HistoryEntry(PgnNode node, String san, String prefix, int depth, boolean current) {
	/**
	 * label method.
	 * @return return value.
	 */
	public String label() {
		if (prefix == null || prefix.isBlank()) {
			return san == null ? "" : san;
		}
		return prefix + " " + (san == null ? "" : san);
	}

	/**
	 * searchText method.
	 * @return return value.
	 */
	public String searchText() {
		StringBuilder sb = new StringBuilder();
		if (prefix != null) {
			sb.append(prefix).append(' ');
		}
		if (san != null) {
			sb.append(san);
		}
		if (node != null && node.getComment() != null && !node.getComment().isBlank()) {
			sb.append(' ').append(node.getComment());
		}
		return sb.toString().toLowerCase(Locale.ROOT);
	}

	@Override
	/**
	 * toString method.
	 *
	 * @return return value.
	 */
	public String toString() {
		return label();
	}
}
