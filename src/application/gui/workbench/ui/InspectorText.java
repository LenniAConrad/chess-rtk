package application.gui.workbench.ui;

/**
 * Shared text formatting helpers for tensor inspector readouts.
 */
final class InspectorText {

    /**
     * Maximum number of entries shown when rendering a flat slice.
     */
    private static final int MAX_FLAT_ENTRIES = 4096;

    /**
     * Prevents instantiation.
     */
    private InspectorText() {
        // utility
    }

    /**
     * Formats a flat value slice.
     *
     * @param data source data
     * @param off start offset
     * @param len element count
     * @return formatted values
     */
    public static String formatFlat(float[] data, int off, int len) {
        int show = Math.min(len, MAX_FLAT_ENTRIES);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < show; ++i) {
            sb.append(String.format("[%6d]   %+10.5f%n", i, data[off + i]));
        }
        if (len > show) {
            sb.append("   ... ").append(len - show).append(" more values\n");
        }
        return sb.toString();
    }
}
