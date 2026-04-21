package application.gui.model;

import java.util.Locale;

/**
 * Command palette entry with search metadata.
 *
 * Wraps a descriptive label, hint, normalized keywords, and the runnable action so the palette can filter commands and invoke them when chosen.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public record PaletteCommand(
	/**
	 * Stores the label.
	 */
	String label,
	/**
	 * Stores the hint.
	 */
	String hint,
	/**
	 * Stores the search text.
	 */
	String searchText,
	/**
	 * Stores the action.
	 */
	Runnable action
) {
	/**
	 * Creates a palette entry while normalizing the searchable keywords.
	 *
	 * @param label primary label shown in the dialog.
	 * @param hint optional hint text rendered below the label.
	 * @param keywords extra keywords used during filtering.
	 * @param action runnable to execute when the command is chosen.
	 * @return palette command ready for insertion into the command model.
	 */
	public static PaletteCommand of(String label, String hint, String[] keywords, Runnable action) {
		StringBuilder sb = new StringBuilder();
		if (label != null) {
			sb.append(label).append(' ');
		}
		if (hint != null) {
			sb.append(hint).append(' ');
		}
		if (keywords != null) {
			for (String keyword : keywords) {
				if (keyword != null && !keyword.isBlank()) {
					sb.append(keyword).append(' ');
				}
			}
		}
		String search = sb.toString().toLowerCase(Locale.ROOT);
		return new PaletteCommand(label, hint, search, action);
	}
}
