package application.gui.model;

/**
 * Move list pair for underboard history (white/black).
 *
 * Captures SAN strings and list indexes for both colors so the underboard table can render synchronized rows and highlight the current ply.
 *
 * @param moveNo move number.
 * @param whiteSan white SAN string.
 * @param blackSan black SAN string.
 * @param whiteIndex index of the white move in the history list.
 * @param blackIndex index of the black move in the history list.
  * @since 2026
  * @author Lennart A. Conrad
 */
public record MovePair(
	/**
	 * Stores the move no.
	 */
	int moveNo,
	/**
	 * Stores the white san.
	 */
	String whiteSan,
	/**
	 * Stores the black san.
	 */
	String blackSan,
	/**
	 * Stores the white index.
	 */
	int whiteIndex,
	/**
	 * Stores the black index.
	 */
	int blackIndex
) {
}
