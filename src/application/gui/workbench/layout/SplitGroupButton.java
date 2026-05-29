package application.gui.workbench.layout;

import application.gui.workbench.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JToggleButton;

/**
 * Compact split-editor action button used in editor group title strips.
 *
 * <p>VS Code presents editor-group actions as small icon controls rather than
 * text pills. Keeping this button outside {@link EditorSplitArea} leaves the
 * split layout class focused on tab/group state.</p>
 */
final class SplitGroupButton extends JToggleButton {

    /**
     * Serialization identifier for Swing button compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Fixed action button size.
     */
    private static final int SIZE = 28;

    /**
     * Whether the pointer is hovering over the button.
     */
    private boolean hover;

    /**
     * Creates the split-group action.
     */
    SplitGroupButton() {
        setFocusPainted(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setFocusable(true);
        setActionCommand("workbench.editor.split.right");
        setName("workbench.editor.split.right");
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(SIZE, SIZE));
        setMinimumSize(new Dimension(SIZE, SIZE));
        setMaximumSize(new Dimension(SIZE, SIZE));
        getAccessibleContext().setAccessibleName("Split active tab right");
        getAccessibleContext().setAccessibleDescription(
                "Creates an editor group to the right using the active tab.");
        addPropertyChangeListener("enabled", event -> {
            setCursor(isEnabled()
                    ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    : Cursor.getDefaultCursor());
            repaint();
        });
        addMouseListener(new MouseAdapter() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void mouseEntered(MouseEvent event) {
                setHover(true);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void mouseExited(MouseEvent event) {
                setHover(false);
            }
        });
    }

    /**
     * Updates hover state.
     *
     * @param value true while hovered
     */
    private void setHover(boolean value) {
        if (hover != value) {
            hover = value;
            repaint();
        }
    }

    /**
     * Paints the split-group icon.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            boolean enabled = isEnabled();
            if (enabled && (isSelected() || hover)) {
                g.setColor(isSelected()
                        ? Theme.withAlpha(Theme.ACCENT, 30)
                        : Theme.TAB_HOVER);
                g.fillRect(2, 2, Math.max(0, w - 4), Math.max(0, h - 4));
            }
            if (enabled && hasFocus()) {
                g.setColor(Theme.withAlpha(Theme.ACCENT, 190));
                g.drawRoundRect(2, 2, Math.max(0, w - 5), Math.max(0, h - 5), 5, 5);
            }
            Color stroke = !enabled ? Theme.BUTTON_DISABLED_TEXT : isSelected() ? Theme.ACCENT : Theme.MUTED;
            g.setColor(stroke);
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int x = (w - 16) / 2;
            int y = (h - 14) / 2;
            g.drawRect(x, y, 16, 14);
            g.drawLine(x + 8, y, x + 8, y + 14);
            if (isSelected()) {
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(x + 10, y + 3, x + 14, y + 3);
                g.drawLine(x + 10, y + 7, x + 14, y + 7);
                g.drawLine(x + 10, y + 11, x + 14, y + 11);
            }
        } finally {
            g.dispose();
        }
    }
}
