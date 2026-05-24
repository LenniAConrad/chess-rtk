package application.gui.workbench.ui;

import java.awt.Graphics;
import javax.swing.plaf.basic.BasicTextFieldUI;

/**
 * Text-field UI that paints placeholder copy when empty.
 */
final class PlaceholderTextFieldUI extends BasicTextFieldUI {

    /**
     * Paints the text field and its placeholder.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintSafely(Graphics graphics) {
        super.paintSafely(graphics);
        PlaceholderPainter.paint(graphics, getComponent(), true);
    }
}
