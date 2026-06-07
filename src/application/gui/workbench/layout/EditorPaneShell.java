package application.gui.workbench.layout;

import application.gui.workbench.ui.Theme;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Maintains the persistent chrome around an editor group.
 */
final class EditorPaneShell {

    /**
     * Prevents instantiation.
     */
    private EditorPaneShell() {
        // utility class
    }

    /**
     * Ensures a pane wrapper contains its persistent header and selected-panel
     * host.
     *
     * @param panePanel editor-group wrapper
     * @param header persistent header
     * @param strip tab strip
     * @param host selected-panel host
     * @param actions persistent editor-group action controls
     */
    static void install(
            JPanel panePanel,
            JPanel header,
            JPanel strip,
            JPanel host,
            JComponent actions) {
        ensureHeader(header, strip, actions);
        if (header.getParent() != panePanel || host.getParent() != panePanel) {
            panePanel.removeAll();
            panePanel.add(header, BorderLayout.NORTH);
            panePanel.add(host, BorderLayout.CENTER);
        }
    }

    /**
     * Ensures the tab strip and action controls are attached to a pane header.
     *
     * @param header pane header
     * @param strip tab strip
     * @param actions pane actions
     */
    private static void ensureHeader(JPanel header, JPanel strip, JComponent actions) {
        if (strip.getParent() == header && actions.getParent() == header) {
            return;
        }
        header.removeAll();
        header.add(strip, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);
    }

    /**
     * Replaces only the selected child inside an editor host, avoiding
     * add/remove churn when the requested panel is already attached.
     *
     * @param host selected-panel host
     * @param panel selected panel, or null for an empty host
     */
    static void updateHost(JPanel host, JComponent panel) {
        if (panel != null) {
            if (host.getComponentCount() == 1 && host.getComponent(0) == panel) {
                return;
            }
            host.removeAll();
            host.setOpaque(true);
            host.setBackground(Theme.PANEL_SOLID);
            host.add(panel, BorderLayout.CENTER);
            refreshHost(host);
            return;
        }
        if (host.getComponentCount() > 0) {
            host.removeAll();
        }
        host.setOpaque(true);
        host.setBackground(Theme.PANEL_SOLID);
        refreshHost(host);
    }

    /**
     * Forces the host bounds dirty after a selected panel changes so old double
     * buffered pixels cannot survive behind transparent child surfaces.
     *
     * @param host selected-panel host
     */
    private static void refreshHost(JPanel host) {
        host.revalidate();
        host.repaint();
        SwingUtilities.invokeLater(host::repaint);
    }

    /**
     * Applies current theme colors to an editor-group header.
     *
     * @param header header component
     */
    static void styleHeader(JPanel header) {
        header.setOpaque(true);
        header.setBackground(Theme.BG);
        // No strip-wide bottom border: each tab paints its own bottom 1px
        // line directly beneath itself, and the active tab skips that line
        // so it merges visually with the content area below (VS Code-style).
    }
}
