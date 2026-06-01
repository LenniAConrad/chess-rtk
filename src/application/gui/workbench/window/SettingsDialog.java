package application.gui.workbench.window;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.ModalOverlay;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * Single settings home for the workbench. The content lives inside the main
 * workbench window via {@link ModalOverlay}; opening Settings no longer
 * spawns a separate OS window.
 */
public final class SettingsDialog extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Section tabs.
     */
    private final JTabbedPane tabs;

    /**
     * Overlay backing the in-workbench presentation.
     */
    private final ModalOverlay overlay;

    /**
     * Preferred overlay width.
     */
    private static final int PREFERRED_WIDTH = 760;

    /**
     * Preferred overlay height.
     */
    private static final int PREFERRED_HEIGHT = 560;

    /**
     * Creates the settings dialog.
     *
     * @param overlay workbench overlay to mount into
     */
    public SettingsDialog(ModalOverlay overlay) {
        super(new BorderLayout());
        this.overlay = overlay;
        setOpaque(true);
        setBackground(Theme.BG);
        setBorder(Theme.pad(Theme.SPACE_MD));

        JPanel header = Ui.transparentPanel(new BorderLayout());
        JPanel titleColumn = Ui.transparentPanel(new BorderLayout(0, 2));
        JLabel title = new JLabel("Settings");
        title.setFont(Theme.font(15, Font.BOLD));
        title.setForeground(Theme.TEXT);
        JLabel subtitle = new JLabel("Workbench appearance, board, sound, and engine options");
        subtitle.setFont(Theme.font(11, Font.PLAIN));
        Theme.foreground(subtitle, Theme.ForegroundRole.MUTED);
        titleColumn.add(title, BorderLayout.NORTH);
        titleColumn.add(subtitle, BorderLayout.CENTER);
        header.add(titleColumn, BorderLayout.WEST);
        JButton close = Ui.button("Close", false, event -> overlay.hide());
        JPanel closeWrap = Ui.transparentPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeWrap.add(close);
        header.add(closeWrap, BorderLayout.EAST);
        // A hairline under the header separates the title band from the tabbed
        // sections so the dialog reads with a clear top-level hierarchy.
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                Theme.pad(0, 0, Theme.SPACE_SM, 0)));
        add(header, BorderLayout.NORTH);

        tabs = Ui.tabbedPane();
        add(tabs, BorderLayout.CENTER);
    }

    /**
     * Registers one settings section.
     *
     * @param sectionTitle section title shown on the tab
     * @param body section content
     */
    public void addSection(String sectionTitle, JComponent body) {
        tabs.addTab(sectionTitle, body);
    }

    /**
     * Selects the section with the supplied title when present.
     *
     * @param sectionTitle section title
     */
    public void selectSection(String sectionTitle) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (sectionTitle.equals(tabs.getTitleAt(i))) {
                tabs.setSelectedIndex(i);
                return;
            }
        }
    }

    /**
     * Shows the settings panel as a centred overlay on the workbench.
     */
    public void showCentered() {
        overlay.show(this, PREFERRED_WIDTH, PREFERRED_HEIGHT);
    }
}
