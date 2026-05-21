package application.gui.workbench.layout;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import application.gui.workbench.WorkbenchTheme;

/**
 * Shared VS Code-style split pane styling.
 */
public final class SplitPaneStyler {

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
            /**
             * Creates a themed divider that paints only a hairline separator.
             *
             * @return divider
             */
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    /**
                     * Serialization identifier for Swing divider compatibility.
                     */
                    private static final long serialVersionUID = 1L;

                    /**
                     * Paints the themed divider background, separator line, and
                     * grip affordance.
                     *
                     * @param graphics graphics context
                     */
                    @Override
                    public void paint(Graphics graphics) {
                        Graphics2D g = (Graphics2D) graphics.create();
                        try {
                            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                            g.setColor(WorkbenchTheme.BG);
                            g.fillRect(0, 0, getWidth(), getHeight());
                            g.setColor(WorkbenchTheme.LINE);
                            if (orientation == JSplitPane.HORIZONTAL_SPLIT) {
                                int x = getWidth() / 2;
                                g.drawLine(x, 0, x, getHeight());
                                paintGrip(g, x - 1, getHeight() / 2 - 12, false);
                            } else {
                                int y = getHeight() / 2;
                                g.drawLine(0, y, getWidth(), y);
                                paintGrip(g, getWidth() / 2 - 12, y - 1, true);
                            }
                        } finally {
                            g.dispose();
                        }
                    }
                };
            }
        });
        pane.setOneTouchExpandable(false);
        pane.setDividerSize(8);
        pane.setContinuousLayout(true);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setBackground(WorkbenchTheme.BG);
    }

    /**
     * Paints a quiet divider grip.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param horizontal true for a horizontal grip
     */
    private static void paintGrip(Graphics2D g, int x, int y, boolean horizontal) {
        g.setColor(WorkbenchTheme.withAlpha(WorkbenchTheme.MUTED, 120));
        for (int i = 0; i < 3; i++) {
            int dx = horizontal ? i * 8 : 0;
            int dy = horizontal ? 0 : i * 8;
            g.fillRoundRect(x + dx, y + dy, 3, 3, 3, 3);
        }
    }
}
