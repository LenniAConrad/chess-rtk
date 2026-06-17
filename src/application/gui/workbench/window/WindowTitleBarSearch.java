package application.gui.workbench.window;

import application.gui.workbench.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;

/**
 * VS Code-style title-bar command-palette search affordance.
 */
final class WindowTitleBarSearch {

    /**
     * Prevents instantiation.
     */
    private WindowTitleBarSearch() {
        // utility
    }

    static JComponent create(Runnable onOpenPalette) {
        final int radius = Theme.RADIUS + 2;
        final int preferredWidth = 380;
        final int preferredHeight = 24;
        final boolean[] hovered = { false };
        JComponent box = new JComponent() {
            private static final long serialVersionUID = 1L;

            /**
             * {@inheritDoc}
             */
            @Override
            public java.awt.Dimension getPreferredSize() {
                return new java.awt.Dimension(preferredWidth, preferredHeight);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(preferredWidth, preferredHeight);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth();
                    int h = getHeight();
                    Color fill = hovered[0] ? Theme.TAB_HOVER : Theme.ELEVATED_SOLID;
                    g.setColor(fill);
                    g.fillRoundRect(0, 0, w - 1, h - 1, radius, radius);
                    g.setColor(Theme.LINE);
                    g.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);
                    paintSearchIcon(g, h);
                    paintLabel(g, w, h);
                } finally {
                    g.dispose();
                }
            }
        };
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        box.setToolTipText("Open command palette (Ctrl+K)");
        box.setOpaque(false);
        box.addMouseListener(new MouseAdapter() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void mouseEntered(MouseEvent event) {
                hovered[0] = true;
                box.repaint();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void mouseExited(MouseEvent event) {
                hovered[0] = false;
                box.repaint();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void mousePressed(MouseEvent event) {
                if (onOpenPalette != null) {
                    onOpenPalette.run();
                }
            }
        });
        return box;
    }

    private static void paintSearchIcon(Graphics2D g, int height) {
        int iconCx = 12;
        int iconCy = height / 2;
        int circleR = 4;
        g.setColor(Theme.MUTED);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawOval(iconCx - circleR, iconCy - circleR, circleR * 2, circleR * 2);
        g.drawLine(iconCx + circleR - 1, iconCy + circleR - 1,
                iconCx + circleR + 3, iconCy + circleR + 3);
    }

    private static void paintLabel(Graphics2D g, int width, int height) {
        g.setFont(Theme.font(11, Font.PLAIN));
        g.setColor(Theme.MUTED);
        FontMetrics metrics = g.getFontMetrics();
        String label = "Command Palette";
        int textX = (width - metrics.stringWidth(label)) / 2;
        int textY = (height + metrics.getAscent() - metrics.getDescent()) / 2 - 1;
        g.drawString(label, textX, textY);
    }
}
