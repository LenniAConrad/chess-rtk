package application.cli.command;

import java.util.Locale;

import chess.pdf.document.PageSize;

/**
 * Shared helpers for commands that render PDF output.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class PdfCommandSupport {

	/**
	 * Utility class; prevent instantiation.
	 */
	private PdfCommandSupport() {
		// utility
	}

	/**
	 * Parses the command-line page-size value.
	 *
	 * @param text page-size option text
	 * @return matching page size
	 */
	static PageSize parsePageSize(String text) {
		if (text == null) {
			return PageSize.A4;
		}
		return switch (text.trim().toLowerCase(Locale.ROOT)) {
			case "a4" -> PageSize.A4;
			case "a5" -> PageSize.A5;
			case "letter" -> PageSize.LETTER;
			default -> throw new IllegalArgumentException("unsupported page size: " + text
					+ " (use a4, a5, or letter)");
		};
	}
}
