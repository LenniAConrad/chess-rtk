package application.gui.workbench.layout;

import application.gui.workbench.ui.Theme;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/**
 * Shared VS Code-style split pane styling.
 */
public final class SplitPaneStyler {

    /**
     * VS Code's sash uses a compact four-pixel interaction strip.
     */
    private static final int SASH_SIZE = 4;

    /**
     * Idle separator alpha. Kept barely visible so the divider remains
     * discoverable in Swing while still reading as a VS Code-style sash.
     */
    private static final int IDLE_SEPARATOR_ALPHA = 95;

    /**
     * Hover separator alpha.
     */
    private static final int HOVER_SEPARATOR_ALPHA = 205;

    /**
     * Prevents instantiation.
     */
    private SplitPaneStyler() {
        // utility
    }

    /**
     * Styles a split pane as a quiet resizable VS Code-style workbench divider.
     *
     * @param pane split pane
     */
    public static void style(JSplitPane pane) {
        pane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
    return new SashDivider(this);
            }
        });
        pane.setOneTouchExpandable(false);
        pane.setDividerSize(SASH_SIZE);
        pane.setContinuousLayout(true);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setBackground(Theme.BG);
    }

    /**
     * Thin split-pane divider modelled after VS Code's transparent sash: no
     * grip, quiet at rest, accent colored only on hover/drag.
     */
    private static final class SashDivider extends BasicSplitPaneDivider {

        /**
         * Serialization identifier for Swing divider compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Whether the pointer is hovering over the sash.
         */
        private boolean hover;

        /**
         * Whether the sash is currently being dragged.
         */
        private boolean active;

        /**
         * Creates a sash divider.
         *
         * @param ui owning split pane UI
         */
        SashDivider(BasicSplitPaneUI ui) {
            super(ui);
            setBorder(BorderFactory.createEmptyBorder());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    setHover(true);
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    setHover(false);
                }

                @Override
                public void mousePressed(MouseEvent event) {
                    setActive(true);
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    setActive(false);
                }
            });
        }

        /**
         * Updates hover state.
         *
         * @param value true when hovered
         */
        private void setHover(boolean value) {
            if (hover != value) {
                hover = value;
                repaint();
            }
        }

        /**
         * Updates drag state.
         *
         * @param value true while dragging
         */
        private void setActive(boolean value) {
            if (active != value) {
                active = value;
                repaint();
            }
        }

        /**
         * Paints a transparent VS Code-style sash.
         *
         * @param graphics graphics context
         */
        @Override
        public void paint(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color color = active || hover
                        ? Theme.withAlpha(Theme.ACCENT, HOVER_SEPARATOR_ALPHA)
                        : Theme.withAlpha(Theme.LINE, IDLE_SEPARATOR_ALPHA);
                g.setColor(color);
                if (orientation == JSplitPane.HORIZONTAL_SPLIT) {
                    int x = Math.max(0, (getWidth() - 1) / 2);
                    g.drawLine(x, 0, x, getHeight());
                } else {
                    int y = Math.max(0, (getHeight() - 1) / 2);
                    g.drawLine(0, y, getWidth(), y);
                }
            } finally {
                g.dispose();
            }
        }
    }
}
