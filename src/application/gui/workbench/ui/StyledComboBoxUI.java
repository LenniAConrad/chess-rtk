package application.gui.workbench.ui;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;

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
     * Creates a popup that uses the same list and scrollbar chrome as workbench
     * scroll panes instead of the platform default combo popup.
     *
     * @return styled combo popup
     */
    @Override
    protected ComboPopup createPopup() {
        BasicComboPopup popup = new BasicComboPopup(comboBox) {

            /**
             * Serialization identifier for Swing popup compatibility.
             */
            private static final long serialVersionUID = 1L;

            /**
             * Creates the popup list.
             *
             * @return popup list
             */
            @Override
            protected JList<Object> createList() {
                JList<Object> list = super.createList();
                stylePopupList(list);
                return list;
            }

            /**
             * Creates the popup scroller.
             *
             * @return popup scroller
             */
            @Override
            protected JScrollPane createScroller() {
                JScrollPane pane = super.createScroller();
                stylePopupScroller(pane);
                return pane;
            }

            /**
             * Shows the popup after refreshing colours for the active theme.
             */
            @Override
            public void show() {
                stylePopupList(list);
                stylePopupScroller(scroller);
                super.show();
            }
        };
        stylePopupList(popup.getList());
        popup.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        popup.setOpaque(true);
        popup.setBackground(Theme.PANEL_SOLID);
        return popup;
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

    /**
     * Applies shared workbench list colours to a combo popup list.
     *
     * @param list popup list
     */
    private static void stylePopupList(JList<?> list) {
        if (list == null) {
            return;
        }
        Theme.list(list);
        list.setFont(Theme.font(13, java.awt.Font.PLAIN));
        list.setFixedCellHeight(Math.max(28, list.getFixedCellHeight()));
        list.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
    }

    /**
     * Applies shared workbench scrollbar chrome to a combo popup scroller.
     *
     * @param pane popup scroller
     */
    private static void stylePopupScroller(JScrollPane pane) {
        if (pane == null) {
            return;
        }
        Ui.styleScrollPane(pane);
        pane.setBorder(BorderFactory.createEmptyBorder());
        if (pane.getViewport() != null) {
            pane.getViewport().setBackground(Theme.ELEVATED_SOLID);
            pane.getViewport().setOpaque(true);
        }
    }
}
