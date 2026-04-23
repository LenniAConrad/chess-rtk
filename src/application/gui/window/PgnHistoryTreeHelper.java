package application.gui.window;

import java.util.List;
import java.util.function.Function;

/**
 * Tree traversal helpers for PGN history and variation display.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class PgnHistoryTreeHelper {

	/**
	 * PgnHistoryTreeHelper method.
	 */
	private PgnHistoryTreeHelper() {
	}

	/**
	 * collectVariationStarts method.
	 *
	 * @param node parameter.
	 * @param collector parameter.
	 */
	public static void collectVariationStarts(PgnNode node, List<PgnNode> collector) {
		if (node == null || collector == null) {
			return;
		}
		for (PgnNode variation : node.variations) {
			if (variation == null) {
				continue;
			}
			collector.add(variation);
			collectVariationStarts(variation, collector);
		}
		if (node.mainNext != null) {
			collectVariationStarts(node.mainNext, collector);
		}
	}

	/**
	 * buildVariationLine method.
	 *
	 * @param start parameter.
	 * @param sanFormatter parameter.
	 * @return return value.
	 */
	public static String buildVariationLine(PgnNode start, Function<String, String> sanFormatter) {
		if (start == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		PgnNode cursor = start;
		while (cursor != null && cursor.san != null && !cursor.san.isBlank()) {
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(sanFormatter != null ? sanFormatter.apply(cursor.san) : cursor.san);
			cursor = cursor.mainNext;
		}
		return sb.toString();
	}
}
