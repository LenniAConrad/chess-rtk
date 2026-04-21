package application.gui.model;

import java.util.List;

/**
 * Result container for parsed PGN mainline.
 *
 * Holds the canonical sequence of FENs together with any parsing error so callers can detect invalid PGNs and still examine what succeeded.
 *
 * @param fens canonical FEN sequence.
 * @param error parsing error message, if any.
  * @since 2026
  * @author Lennart A. Conrad
 */
public record PgnMainline(
	/**
	 * Stores the fens.
	 */
	List<String> fens,
	/**
	 * Stores the error.
	 */
	String error
) {
}
