package application.gui.workbench;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-paint registry of interactive regions inside a workbench network view.
 *
 * <p>Each region carries the rectangle to hit-test against plus the metadata
 * needed for two interactions: a hover tooltip (title / value / description)
 * and an optional click-to-inspect target that points back into the snapshot
 * data so the tensor inspector can show the raw slice.</p>
 */
final class WorkbenchHitRegions {

    /**
     * One interactive region.
     */
    static final class Region {

        /**
         * Bounds in component-local pixels.
         */
        final Rectangle bounds;

        /**
         * Short title shown in bold at the top of the tooltip.
         */
        final String title;

        /**
         * Optional one-line description shown under the value.
         */
        final String description;

        /**
         * Optional current numeric value, formatted for display.
         */
        final String value;

        /**
         * Optional snapshot key to inspect on click; null disables click.
         */
        final String dataKey;

        /**
         * Optional computed values that are not stored in the activation
         * snapshot. Used for gathered or combined trace slices.
         */
        final float[] inlineData;

        /**
         * Offset into the snapshot data array.
         */
        final int dataOffset;

        /**
         * Number of values to show; 0 means "the whole tensor".
         */
        final int dataLength;

        /**
         * Row stride for matrix-shaped slices; 0 means flat.
         */
        final int dataStride;

        /**
         * Optional shape label shown in the inspector header.
         */
        final String shapeText;

        Region(Rectangle bounds, String title, String description, String value,
                String dataKey, int dataOffset, int dataLength, int dataStride,
                String shapeText) {
            this(bounds, title, description, value, dataKey, null,
                    dataOffset, dataLength, dataStride, shapeText);
        }

        Region(Rectangle bounds, String title, String description, String value,
                String dataKey, float[] inlineData, int dataOffset, int dataLength,
                int dataStride, String shapeText) {
            this.bounds = new Rectangle(bounds);
            this.title = title == null ? "" : title;
            this.description = description == null ? "" : description;
            this.value = value == null ? "" : value;
            this.dataKey = dataKey;
            this.inlineData = inlineData == null ? null : inlineData.clone();
            this.dataOffset = Math.max(0, dataOffset);
            this.dataLength = Math.max(0, dataLength);
            this.dataStride = Math.max(0, dataStride);
            this.shapeText = shapeText;
        }

        /**
         * Returns whether this region can be inspected for raw values.
         *
         * @return true when snapshot or inline data is available
         */
        boolean hasData() {
            return dataKey != null || inlineData != null;
        }

        /**
         * Returns the HTML-formatted tooltip string.
         *
         * @return tooltip HTML
         */
        String tooltipHtml() {
            StringBuilder sb = new StringBuilder("<html>");
            sb.append("<b>").append(escape(title)).append("</b>");
            if (!value.isEmpty()) {
                sb.append("<br><span style='color:#9aa0a6;'>")
                  .append(escape(value)).append("</span>");
            }
            if (!description.isEmpty()) {
                sb.append("<br>").append(escape(description));
            }
            if (hasData()) {
                sb.append("<br><i>click to inspect raw values</i>");
            }
            sb.append("</html>");
            return sb.toString();
        }

        private static String escape(String s) {
            if (s == null) {
                return "";
            }
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    /**
     * Registered regions, in insertion order. Hit-tested in reverse so smaller
     * inner regions take priority over larger background regions added earlier.
     */
    private final List<Region> regions = new ArrayList<>();

    /**
     * Clears every region. Call at the start of each paint pass.
     */
    void clear() {
        regions.clear();
    }

    /**
     * Adds a hover-only region (no click target).
     *
     * @param bounds rectangle
     * @param title bold title
     * @param description one-line description
     * @param value formatted current value
     */
    void add(Rectangle bounds, String title, String description, String value) {
        regions.add(new Region(bounds, title, description, value, null, 0, 0, 0, null));
    }

    /**
     * Adds a hover+click region pointing at a snapshot tensor slice.
     *
     * @param bounds rectangle
     * @param title bold title
     * @param description one-line description
     * @param value formatted current value
     * @param dataKey snapshot key (non-null)
     * @param dataOffset offset into the tensor data
     * @param dataLength number of values; 0 = whole tensor
     * @param dataStride row stride for matrix display; 0 = flat
     * @param shapeText shape label for the inspector header
     */
    void addInspectable(Rectangle bounds, String title, String description, String value,
            String dataKey, int dataOffset, int dataLength, int dataStride, String shapeText) {
        regions.add(new Region(bounds, title, description, value,
                dataKey, dataOffset, dataLength, dataStride, shapeText));
    }

    /**
     * Adds a hover+click region backed by values computed during painting
     * rather than by one contiguous snapshot tensor slice.
     *
     * @param bounds rectangle
     * @param title bold title
     * @param description one-line description
     * @param value formatted current value
     * @param data computed values
     * @param shapeText shape label for the inspector header
     */
    void addInline(Rectangle bounds, String title, String description, String value,
            float[] data, String shapeText) {
        regions.add(new Region(bounds, title, description, value,
                null, data, 0, data == null ? 0 : data.length, 0, shapeText));
    }

    /**
     * Returns the topmost (most-recently-added) region containing the point.
     *
     * @param x x in component pixels
     * @param y y in component pixels
     * @return region or null
     */
    Region hitTest(int x, int y) {
        for (int i = regions.size() - 1; i >= 0; --i) {
            Region r = regions.get(i);
            if (r.bounds.contains(x, y)) {
                return r;
            }
        }
        return null;
    }
}
