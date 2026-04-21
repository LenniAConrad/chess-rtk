package application.gui.model;

import chess.eco.Entry;

/**
 * Display wrapper for ECO list entries.
 *
 * Couples the canonical classification data with a descriptive label so GUI lists and selectors can render a human-friendly name while preserving the underlying entry reference.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public record EcoListEntry(
	/**
	 * Stores the entry.
	 */
	Entry entry,
	/**
	 * Stores the label.
	 */
	String label
) {
		/**
	 * Returns the string representation.
	 * @return computed value
	 */
@Override
	/**
	 * toString method.
	 *
	 * @return return value.
	 */
	public String toString() {
		return label;
	}
}
