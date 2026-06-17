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

    String title() {
        return number + ". " + compactAnnotationName(markup);
    }

    String detail() {
        return annotationDetail(markup);
    }

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

    private static String compactAnnotationName(BoardMarkup markup) {
        return switch (markup.tool()) {
            case CIRCLE -> "Circle";
            case RECTANGLE -> "Rectangle";
            case GLYPH -> "Glyph";
            default -> "Arrow";
        };
    }

}
