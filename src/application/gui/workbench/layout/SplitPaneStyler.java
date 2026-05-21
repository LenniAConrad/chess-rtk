package application.gui.workbench.layout;

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
                     * Paints the themed divider background and separator line.
                     *
                     * @param graphics graphics context
                     */
                    @Override
                    public void paint(java.awt.Graphics graphics) {
                        graphics.setColor(WorkbenchTheme.BG);
                        graphics.fillRect(0, 0, getWidth(), getHeight());
                        graphics.setColor(WorkbenchTheme.LINE);
                        if (getWidth() <= getHeight()) {
                            int x = getWidth() / 2;
                            graphics.drawLine(x, 0, x, getHeight());
                        } else {
                            int y = getHeight() / 2;
                            graphics.drawLine(0, y, getWidth(), y);
                        }
                        super.paint(graphics);
                    }
                };
            }
        });
        pane.setOneTouchExpandable(false);
        pane.setDividerSize(5);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setBackground(WorkbenchTheme.BG);
    }
}
