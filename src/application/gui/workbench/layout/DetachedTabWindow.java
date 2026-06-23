package application.gui.workbench.layout;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * One OS-level workbench tab window.
 */
final class DetachedTabWindow {

    /**
     * Default detached-window width.
     */
    private static final int DEFAULT_WIDTH = 1040;

    /**
     * Default detached-window height.
     */
    private static final int DEFAULT_HEIGHT = 680;

    /**
     * Minimum detached-window width.
     */
    private static final int MIN_WIDTH = 760;

    /**
     * Minimum detached-window height.
     */
    private static final int MIN_HEIGHT = 520;

    /**
     * Detached tab title.
     */
    private final String title;

    /**
     * Hosted tab panel.
     */
    private final JComponent panel;

    /**
     * Detached window root, also used in headless regression tests.
     */
    private final JPanel root = new JPanel(new BorderLayout(0, 0));

    /**
     * Header row.
     */
    private final JPanel header = new JPanel(new BorderLayout(8, 0));

    /**
     * Native top-level window, absent in headless tests.
     */
    private final JFrame frame;

    /**
     * True after the handle has been disposed.
     */
    private boolean disposed;

    /**
     * Opens one detached tab window.
     *
     * @param title display title
     * @param panel panel to host
     * @param reattach reattach action
     * @param close close action
     * @param owner owner component used for placement and icons
     * @return detached window handle
     */
    static DetachedTabWindow open(String title, JComponent panel, Runnable reattach, Runnable close, Component owner) {
        DetachedTabWindow window = new DetachedTabWindow(title, panel, reattach, close, owner);
        window.show();
        return window;
    }

    /**
     * Creates one detached tab handle.
     *
     * @param title display title
     * @param panel panel to host
     * @param reattach reattach action
     * @param close close action
     * @param owner owner component used for placement and icons
     */
    private DetachedTabWindow(String title, JComponent panel, Runnable reattach, Runnable close, Component owner) {
        this.title = title == null || title.isBlank() ? "Workbench" : title;
        this.panel = panel;
        buildRoot(reattach, close);
        frame = GraphicsEnvironment.isHeadless() ? null : buildFrame(owner, close);
    }

    /**
     * Builds the detachable window content.
     *
     * @param reattach reattach action
     * @param close close action
     */
    private void buildRoot(Runnable reattach, Runnable close) {
        root.setOpaque(true);
        root.setBackground(Theme.PANEL_SOLID);
        header.setOpaque(true);
        header.setBackground(Theme.BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                Theme.pad(6, 10, 6, 8)));

        JLabel label = new JLabel(title);
        label.setFont(Theme.font(12, java.awt.Font.BOLD));
        label.setForeground(Theme.TEXT);
        header.add(label, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        JButton reattachButton = Ui.button("Reattach", false, event -> reattach.run());
        reattachButton.setActionCommand(EditorLayoutCommands.TAB_REATTACH);
        reattachButton.setName(EditorLayoutCommands.TAB_REATTACH);
        reattachButton.setToolTipText("Reattach this window as a workbench tab");
        JButton closeButton = Ui.button("Close", Theme.ButtonVariant.GHOST, event -> close.run());
        closeButton.setActionCommand(EditorLayoutCommands.TAB_CLOSE);
        closeButton.setName(EditorLayoutCommands.TAB_CLOSE);
        closeButton.setToolTipText("Close this detached tab");
        actions.add(reattachButton);
        actions.add(closeButton);
        header.add(actions, BorderLayout.EAST);

        root.add(header, BorderLayout.NORTH);
        root.add(panel, BorderLayout.CENTER);
    }

    /**
     * Builds the native frame for interactive use.
     *
     * @param owner owner component
     * @param close close action
     * @return frame
     */
    private JFrame buildFrame(Component owner, Runnable close) {
        JFrame detached = new JFrame("ChessRTK Workbench - " + title);
        detached.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        detached.setContentPane(root);
        detached.setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        Window ownerWindow = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        if (ownerWindow instanceof JFrame ownerFrame && !ownerFrame.getIconImages().isEmpty()) {
            detached.setIconImages(ownerFrame.getIconImages());
        }
        detached.addWindowListener(new WindowAdapter() {
            /**
             * Closes the detached workbench tab.
             *
             * @param event window event
             */
            @Override
            public void windowClosing(WindowEvent event) {
                close.run();
            }
        });
        sizeAndPlace(detached, owner);
        return detached;
    }

    /**
     * Sizes and places the detached frame.
     *
     * @param detached frame
     * @param owner owner component
     */
    private static void sizeAndPlace(JFrame detached, Component owner) {
        Window ownerWindow = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        Dimension base = ownerWindow == null ? new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT) : ownerWindow.getSize();
        int width = Math.max(MIN_WIDTH, Math.min(DEFAULT_WIDTH, base.width - 120));
        int height = Math.max(MIN_HEIGHT, Math.min(DEFAULT_HEIGHT, base.height - 120));
        detached.setSize(width, height);
        if (ownerWindow != null) {
            detached.setLocation(ownerWindow.getX() + 48, ownerWindow.getY() + 48);
        } else {
            detached.setLocationByPlatform(true);
        }
    }

    /**
     * Shows the native frame when available.
     */
    private void show() {
        if (frame != null) {
            frame.setVisible(true);
        }
    }

    /**
     * Brings the detached window to the front.
     */
    void focus() {
        if (frame != null) {
            frame.toFront();
            frame.requestFocus();
        }
    }

    /**
     * Refreshes the detached chrome and hosted panel for the active theme.
     */
    void refreshTheme() {
        root.setBackground(Theme.PANEL_SOLID);
        header.setBackground(Theme.BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                Theme.pad(6, 10, 6, 8)));
        Theme.refreshComponentTree(root);
        root.revalidate();
        root.repaint();
    }

    /**
     * Rescales detached content after a density switch.
     *
     * @param ratio font-scale ratio
     */
    void rescaleFonts(double ratio) {
        Theme.rescaleFonts(root, ratio);
        root.revalidate();
        root.repaint();
    }

    /**
     * Disposes the detached window and removes the hosted panel from its temporary
     * root.
     */
    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        root.remove(panel);
        if (frame != null) {
            frame.dispose();
        }
    }

    /**
     * Returns whether this detached handle is still active.
     *
     * @return true when active
     */
    boolean isOpen() {
        return !disposed;
    }

    /**
     * Returns the headless-test root component.
     *
     * @return root component
     */
    JComponent root() {
        return root;
    }
}
