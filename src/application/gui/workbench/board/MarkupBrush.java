/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.board;

import java.awt.Color;

/**
 * Annotation brush.
 *
 * @param name brush name
 * @param color brush color
 * @param lineWidth Chessground-style line width
 */
public record MarkupBrush(String name, Color color, int lineWidth) { }
