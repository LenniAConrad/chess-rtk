package application.gui.workbench;

import javax.swing.BorderFactory;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/**
 * Shared VS Code-style split pane styling.
 */
final class WorkbenchSplitPanes {

    /**
     * Prevents instantiation.
     */
    private WorkbenchSplitPanes() {
        // utility
    }

    /**
     * Styles a split pane as a quiet resizable VS Code-style workbench divider.
     *
     * @param pane split pane
     */
    static void style(JSplitPane pane) {
        pane.setUI(new BasicSplitPaneUI() {
            /**
             * Creates a themed divider that paints only a hairline separator.
             *
             * @return divider
             */
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    private static final long serialVersionUID = 1L;

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
