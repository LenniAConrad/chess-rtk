package application.gui.workbench.ui;

import java.awt.Component;
import java.awt.Graphics;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicSpinnerUI;

/**
 * Minimal spinner UI with solid arrow buttons.
 */
final class StyledSpinnerUI extends BasicSpinnerUI {

    /**
     * Paints the full input well before the editor and arrow buttons are painted,
     * avoiding unfilled platform-background gaps inside focused spinner borders.
     *
     * @param graphics graphics context
     * @param component spinner component
     */
    @Override
    public void paint(Graphics graphics, JComponent component) {
        graphics.setColor(component.isEnabled() ? Theme.INPUT : Theme.INPUT_DISABLED);
        graphics.fillRect(1, 1,
                Math.max(0, component.getWidth() - 2),
                Math.max(0, component.getHeight() - 2));
        super.paint(graphics, component);
    }

    /**
     * Creates the next-value button.
     *
     * @return next button
     */
    @Override
    protected Component createNextButton() {
        JButton button = new ArrowButton(SwingConstants.NORTH);
        installNextButtonListeners(button);
        return button;
    }

    /**
     * Creates the previous-value button.
     *
     * @return previous button
     */
    @Override
    protected Component createPreviousButton() {
        JButton button = new ArrowButton(SwingConstants.SOUTH);
        installPreviousButtonListeners(button);
        return button;
    }
}
