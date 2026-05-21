package application.gui.workbench;

import java.awt.Color;

/**
 * Annotation brush.
 *
 * @param name brush name
 * @param color brush color
 * @param lineWidth Chessground-style line width
 */
record MarkupBrush(String name, Color color, int lineWidth) { }
