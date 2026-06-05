package application.gui.workbench.ui;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicComboBoxUI;

/**
 * Minimal combo-box UI without platform-gray arrow-button artifacts.
 */
final class StyledComboBoxUI extends BasicComboBoxUI {

    /**
     * Paints the full input well before the current value and arrow button are
     * painted, avoiding unfilled platform-background gaps between the value
     * renderer and the chevron area.
     *
     * @param graphics graphics context
     * @param component combo component
     */
    @Override
    public void paint(Graphics graphics, JComponent component) {
        graphics.setColor(comboBox.isEnabled() ? Theme.INPUT : Theme.INPUT_DISABLED);
        graphics.fillRect(1, 1,
                Math.max(0, component.getWidth() - 2),
                Math.max(0, component.getHeight() - 2));
        super.paint(graphics, component);
    }

    /**
     * Creates the combo arrow button.
     *
     * @return arrow button
     */
    @Override
    protected JButton createArrowButton() {
        return new ArrowButton(SwingConstants.SOUTH);
    }

    /**
     * Paints the current value background.
     *
     * @param graphics graphics context
     * @param bounds value bounds
     * @param hasFocus whether the combo has focus
     */
    @Override
    public void paintCurrentValueBackground(Graphics graphics, Rectangle bounds, boolean hasFocus) {
        graphics.setColor(comboBox.isEnabled() ? Theme.INPUT : Theme.INPUT_DISABLED);
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    /**
     * Paints the selected value with workbench disabled-state colors rather than
     * the platform look-and-feel's default disabled combo colors.
     *
     * @param graphics graphics context
     * @param bounds value bounds
     * @param hasFocus whether the combo has focus
     */
    @Override
    public void paintCurrentValue(Graphics graphics, Rectangle bounds, boolean hasFocus) {
        ListCellRenderer<Object> renderer = comboBox.getRenderer();
        Component component = renderer.getListCellRendererComponent(listBox,
                comboBox.getSelectedItem(), -1, false, false);
        component.setFont(comboBox.getFont());
        component.setForeground(comboBox.isEnabled() ? Theme.TEXT : Theme.BUTTON_DISABLED_TEXT);
        component.setBackground(comboBox.isEnabled() ? Theme.INPUT : Theme.INPUT_DISABLED);
        currentValuePane.paintComponent(graphics, component, comboBox, bounds.x, bounds.y,
                bounds.width, bounds.height, true);
    }
}
