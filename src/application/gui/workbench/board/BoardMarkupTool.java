package application.gui.workbench.board;

import java.util.ArrayList;
import java.util.List;

/**
 * Shape created by direct board-annotation gestures.
 */
public enum BoardMarkupTool {
    /**
     * Draw an arrow from the origin square to the target square.
     */
    ARROW("Arrow"),

    /**
     * Draw a circle on the origin square.
     */
    CIRCLE("Circle"),

    /**
     * Draw a filled rectangular region between two board squares.
     */
    RECTANGLE("Rectangle"),

    /**
     * Place a chess annotation glyph on one board square.
     */
    GLYPH("Glyph");

    /**
     * Tool labels in picker order.
     */
    private static final List<String> LABELS = createLabels();

    /**
     * Human-readable label for UI pickers.
     */
    private final String displayName;

    /**
     * Creates an annotation tool.
     *
     * @param displayName human-readable picker label
     */
    BoardMarkupTool(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the human-readable label for UI pickers.
     *
     * @return picker label
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Returns labels for every annotation tool in enum order.
     *
     * @return immutable picker labels
     */
    public static List<String> labels() {
        return LABELS;
    }

    /**
     * Resolves a picker index to an annotation tool, defaulting invalid indices
     * to the primary arrow tool.
     *
     * @param index picker index
     * @return annotation tool
     */
    public static BoardMarkupTool forIndex(int index) {
        BoardMarkupTool[] tools = values();
        return index >= 0 && index < tools.length ? tools[index] : ARROW;
    }

    /**
     * Builds immutable picker labels from the enum entries.
     *
     * @return picker labels
     */
    private static List<String> createLabels() {
        BoardMarkupTool[] tools = values();
        List<String> labels = new ArrayList<>(tools.length);
        for (BoardMarkupTool tool : tools) {
            labels.add(tool.displayName());
        }
        return List.copyOf(labels);
    }
}
