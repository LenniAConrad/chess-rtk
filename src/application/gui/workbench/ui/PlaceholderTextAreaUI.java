/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.ui;

import java.awt.Graphics;
import javax.swing.plaf.basic.BasicTextAreaUI;

/**
 * Text-area UI that paints placeholder copy when empty.
 */
final class PlaceholderTextAreaUI extends BasicTextAreaUI {

    /**
     * Paints the text area and its placeholder.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintSafely(Graphics graphics) {
        super.paintSafely(graphics);
        PlaceholderPainter.paint(graphics, getComponent(), false);
    }
}
