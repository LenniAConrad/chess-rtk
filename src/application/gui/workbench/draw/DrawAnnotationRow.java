package application.gui.workbench.draw;

import application.gui.workbench.board.BoardMarkup;
import chess.core.Field;

/**
 * Lightweight row model for the Draw rail annotation history.
 *
 * @param number one-based row number
 * @param markup board annotation
 * @param details true to show square and color metadata
 */
record DrawAnnotationRow(int number, BoardMarkup markup, boolean details) {

    /**
     * Returns the numbered title shown for this annotation row.
     *
     * @return row title text
     */
    String title() {
        return number + ". " + compactAnnotationName(markup);
    }

    /**
     * Returns the tool-specific detail text without the color label.
     *
     * @return annotation detail text
     */
    String detail() {
        return annotationDetail(markup);
    }

    /**
     * Returns the detail text with the brush color label appended.
     *
     * @return detail text with color
     */
    String detailWithColor() {
        return detail() + " · " + DrawColorFormat.colorLabel(markup.brush().displayColor());
    }

    /**
     * Returns the textual row representation used by accessibility,
     * component dumps, and copy/debug paths.
     *
     * @return row text
     */
    @Override
    public String toString() {
        if (details) {
            return title() + " · " + detail() + " · "
                    + DrawColorFormat.colorLabel(markup.brush().displayColor());
        }
        return title();
    }

    /**
     * Formats the coordinate payload for one board markup.
     *
     * @param markup board markup
     * @return annotation detail text
     */
    private static String annotationDetail(BoardMarkup markup) {
        String from = Field.toString(markup.from());
        String target = markup.to() == Field.NO_SQUARE ? from : Field.toString(markup.to());
        return switch (markup.tool()) {
            case CIRCLE -> from;
            case RECTANGLE -> from + " - " + target;
            case GLYPH -> markup.brush().glyph() + " " + from;
            default -> from + " -> " + target;
        };
    }

    /**
     * Returns the short list label for a markup tool.
     *
     * @param markup board markup
     * @return compact annotation name text
     */
    private static String compactAnnotationName(BoardMarkup markup) {
        return switch (markup.tool()) {
            case CIRCLE -> "Circle";
            case RECTANGLE -> "Rectangle";
            case GLYPH -> "Glyph";
            default -> "Arrow";
        };
    }

}
