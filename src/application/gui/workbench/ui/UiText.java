package application.gui.workbench.ui;

import java.awt.FontMetrics;

/**
 * Text measurement helpers used by custom-painted Workbench UI components.
 */
final class UiText {

    /**
     * Prevents instantiation.
     */
    private UiText() {
        // utility
    }

    /**
     * Shortens text to fit a fixed pixel width using a binary search over the
     * visible prefix. Null text is treated as blank so paint paths can call this
     * directly without defensive checks.
     *
     * @param text source text
     * @param metrics font metrics
     * @param maxWidth maximum width
     * @return fitted text
     */
    static String elide(String text, FontMetrics metrics, int maxWidth) {
        if (text == null || text.isEmpty() || metrics.stringWidth(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String suffix = "...";
        int suffixWidth = metrics.stringWidth(suffix);
        if (suffixWidth >= maxWidth) {
            return "";
        }
        int budget = maxWidth - suffixWidth;
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (metrics.stringWidth(text.substring(0, mid)) <= budget) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low <= 0 ? "" : text.substring(0, low) + suffix;
    }
}
