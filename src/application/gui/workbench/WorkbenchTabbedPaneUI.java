package application.gui.workbench;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.event.MouseInputAdapter;

/**
 * Native tabbed-pane UI with a compact, low-noise tab strip.
 */
final class WorkbenchTabbedPaneUI extends BasicTabbedPaneUI {

    /**
     * Tab corner radius.
     */
    private static final int RADIUS = 6;

    /**
     * Tab horizontal padding.
     */
    private static final int TAB_PAD_X = 11;

    /**
     * Tab vertical padding.
     */
    private static final int TAB_PAD_Y = 5;

    /**
     * Tab gap.
     */
    private static final int TAB_GAP = 2;

    /**
     * Rollover tab index.
     */
    private int rolloverTab = -1;

    /**
     * Mouse handler that tracks tab rollover.
     */
    private MouseInputAdapter rolloverHandler;

    /**
     * Installs tab layout defaults.
     */
    @Override
    protected void installDefaults() {
        super.installDefaults();
        tabAreaInsets = new Insets(0, 0, 4, 0);
        contentBorderInsets = new Insets(0, 0, 0, 0);
        selectedTabPadInsets = new Insets(0, 0, 0, 0);
    }

    /**
     * Installs rollover listeners.
     */
    @Override
    protected void installListeners() {
        super.installListeners();
        rolloverHandler = new MouseInputAdapter() {
            /**
             * Updates the hovered tab.
             *
             * @param event mouse event
             */
            @Override
            public void mouseMoved(MouseEvent event) {
                updateRolloverTab(tabForCoordinate(tabPane, event.getX(), event.getY()));
            }

            /**
             * Clears rollover when the pointer exits the tabs.
             *
             * @param event mouse event
             */
            @Override
            public void mouseExited(MouseEvent event) {
                updateRolloverTab(-1);
            }
        };
        tabPane.addMouseMotionListener(rolloverHandler);
        tabPane.addMouseListener(rolloverHandler);
    }

    /**
     * Removes rollover listeners.
     */
    @Override
    protected void uninstallListeners() {
        if (rolloverHandler != null) {
            tabPane.removeMouseMotionListener(rolloverHandler);
            tabPane.removeMouseListener(rolloverHandler);
            rolloverHandler = null;
        }
        super.uninstallListeners();
    }

    /**
     * Returns tab insets.
     *
     * @param tabPlacement tab placement
     * @param tabIndex tab index
     * @return tab insets
     */
    @Override
    protected Insets getTabInsets(int tabPlacement, int tabIndex) {
        return new Insets(TAB_PAD_Y, TAB_PAD_X, TAB_PAD_Y, TAB_PAD_X);
    }

    /**
     * Calculates tab height.
     *
     * @param tabPlacement tab placement
     * @param tabIndex tab index
     * @param fontHeight font height
     * @return tab height
     */
    @Override
    protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
        return fontHeight + TAB_PAD_Y * 2;
    }

    /**
     * Calculates tab width.
     *
     * @param tabPlacement tab placement
     * @param tabIndex tab index
     * @param metrics font metrics
     * @return tab width
     */
    @Override
    protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
        return super.calculateTabWidth(tabPlacement, tabIndex, metrics) + TAB_GAP;
    }

    /**
     * Paints the tab background.
     *
     * @param graphics graphics
     * @param tabPlacement tab placement
     * @param tabIndex tab index
     * @param x x
     * @param y y
     * @param width width
     * @param height height
     * @param selected whether the tab is selected
     */
    @Override
    protected void paintTabBackground(Graphics graphics, int tabPlacement, int tabIndex, int x, int y, int width,
            int height, boolean selected) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int bodyX = x + 2;
            int bodyY = y + 3;
            int bodyW = Math.max(0, width - 5);
            int bodyH = Math.max(0, height - 6);
            g.setColor(tabFill(tabIndex, selected));
            g.fillRoundRect(bodyX, bodyY, bodyW, bodyH, RADIUS, RADIUS);
            if (selected) {
                g.setColor(WorkbenchTheme.TAB_ACCENT_UNDERLINE);
                g.fillRect(bodyX + 8, bodyY + bodyH - 1, Math.max(0, bodyW - 16), 1);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints the tab border.
     *
     * @param graphics graphics
     * @param tabPlacement tab placement
     * @param tabIndex tab index
     * @param x x
     * @param y y
     * @param width width
     * @param height height
     * @param selected whether the tab is selected
     */
    @Override
    protected void paintTabBorder(Graphics graphics, int tabPlacement, int tabIndex, int x, int y, int width,
            int height, boolean selected) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int bodyX = x + 2;
            int bodyY = y + 3;
            int bodyW = Math.max(0, width - 5);
            int bodyH = Math.max(0, height - 6);
            if (selected) {
                g.setColor(WorkbenchTheme.LINE);
                g.drawRoundRect(bodyX, bodyY, bodyW, bodyH, RADIUS, RADIUS);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Skips the default content border.
     *
     * @param graphics graphics
     * @param tabPlacement tab placement
     * @param selectedIndex selected tab index
     */
    @Override
    protected void paintContentBorder(Graphics graphics, int tabPlacement, int selectedIndex) {
        // The workbench panes provide their own solid borders.
    }

    /**
     * Skips default dotted focus painting.
     *
     * @param graphics graphics
     * @param tabPlacement tab placement
     * @param rectangles tab rectangles
     * @param tabIndex tab index
     * @param iconRect icon rectangle
     * @param textRect text rectangle
     * @param selected whether selected
     */
    @Override
    protected void paintFocusIndicator(Graphics graphics, int tabPlacement, Rectangle[] rectangles,
            int tabIndex, Rectangle iconRect, Rectangle textRect, boolean selected) {
        if (!selected || tabPane == null || !tabPane.isFocusOwner() || tabIndex < 0 || tabIndex >= rectangles.length) {
            return;
        }
        Rectangle r = rectangles[tabIndex];
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(WorkbenchTheme.INPUT_FOCUS);
            int bodyX = r.x + 2;
            int bodyY = r.y + 3;
            int bodyW = Math.max(0, r.width - 5);
            int bodyH = Math.max(0, r.height - 6);
            g.drawRoundRect(bodyX, bodyY, bodyW, bodyH, RADIUS, RADIUS);
        } finally {
            g.dispose();
        }
    }

    /**
     * Updates rollover state.
     *
     * @param index new rollover tab
     */
    private void updateRolloverTab(int index) {
        if (rolloverTab == index) {
            return;
        }
        int old = rolloverTab;
        rolloverTab = index;
        repaintVisibleTab(old);
        repaintVisibleTab(rolloverTab);
    }

    /**
     * Repaints a tab if it exists.
     *
     * @param index tab index
     */
    private void repaintVisibleTab(int index) {
        if (index >= 0 && index < rects.length && tabPane != null) {
            tabPane.repaint(rects[index]);
        }
    }

    /**
     * Returns the fill color for a tab.
     *
     * @param tabIndex tab index
     * @param selected whether selected
     * @return tab fill
     */
    private Color tabFill(int tabIndex, boolean selected) {
        if (selected) {
            return WorkbenchTheme.ELEVATED;
        }
        if (tabIndex == rolloverTab) {
            return WorkbenchTheme.TAB_HOVER;
        }
        return WorkbenchTheme.TAB_IDLE;
    }

    /**
     * Updates component defaults before painting.
     *
     * @param graphics graphics
     * @param component component
     */
    @Override
    public void update(Graphics graphics, JComponent component) {
        component.setOpaque(false);
        super.update(graphics, component);
    }
}
