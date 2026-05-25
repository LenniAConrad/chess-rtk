package application.gui.workbench.layout;

import application.gui.workbench.ui.Theme;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

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
     * @param splitButtonHolder persistent split-button holder
     * @param splitButton split button
     * @param includeSplitButton true when this pane owns the split action
     */
    static void install(
            JPanel panePanel,
            JPanel header,
            JPanel strip,
            JPanel host,
            JPanel splitButtonHolder,
            JToggleButton splitButton,
            boolean includeSplitButton) {
        if (header.getParent() != panePanel || host.getParent() != panePanel) {
            panePanel.removeAll();
            header.removeAll();
            header.add(strip, BorderLayout.CENTER);
            if (includeSplitButton) {
                splitButtonHolder.removeAll();
                splitButtonHolder.add(splitButton);
                header.add(splitButtonHolder, BorderLayout.EAST);
            }
            panePanel.add(header, BorderLayout.NORTH);
            panePanel.add(host, BorderLayout.CENTER);
        }
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
            host.add(panel, BorderLayout.CENTER);
            return;
        }
        if (host.getComponentCount() > 0) {
            host.removeAll();
        }
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
