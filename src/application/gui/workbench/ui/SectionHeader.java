package application.gui.workbench.ui;

import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Shared compact section header with optional detail text and trailing controls.
 */
final class SectionHeader extends JPanel {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a section header.
     *
     * @param title section title
     * @param detail optional one-line detail
     * @param trailing optional trailing control/status component
     */
    public SectionHeader(String title, String detail, JComponent trailing) {
        super(new BorderLayout(Theme.SPACE_MD, 0));
        setOpaque(false);
        JPanel identity = UiLayout.transparentPanel(new BorderLayout(0, 1));
        JLabel titleLabel = Theme.section(title);
        identity.add(titleLabel, BorderLayout.NORTH);
        if (detail != null && !detail.isBlank()) {
            identity.add(UiFormControls.caption(detail), BorderLayout.CENTER);
        }
        add(identity, BorderLayout.CENTER);
        if (trailing != null) {
            trailing.setOpaque(false);
            add(trailing, BorderLayout.EAST);
        }
    }
}
