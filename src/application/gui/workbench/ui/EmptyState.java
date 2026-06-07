package application.gui.workbench.ui;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Shared centered empty-state block.
 */
public final class EmptyState extends JPanel {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates an empty-state block.
     *
     * @param title short title
     * @param hint one-line hint
     * @param actions optional actions
     */
    public EmptyState(String title, String hint, JButton... actions) {
        super(new GridBagLayout());
        setOpaque(false);
        JPanel stack = UiLayout.transparentPanel(null);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title == null ? "" : title);
        Theme.foreground(titleLabel, Theme.ForegroundRole.TEXT);
        titleLabel.setFont(Theme.font(Theme.FONT_SECTION_TITLE, Font.BOLD));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        stack.add(titleLabel);
        if (hint != null && !hint.isBlank()) {
            stack.add(Box.createVerticalStrut(Theme.SPACE_XS));
            JLabel hintLabel = new JLabel(hint);
            Theme.foreground(hintLabel, Theme.ForegroundRole.MUTED);
            hintLabel.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
            hintLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            stack.add(hintLabel);
        }
        if (actions != null && actions.length > 0) {
            stack.add(Box.createVerticalStrut(Theme.SPACE_MD));
            JPanel row = UiLayout.transparentPanel(new FlowLayout(FlowLayout.CENTER, Theme.SPACE_SM, 0));
            for (JButton action : actions) {
                if (action != null) {
                    row.add(action);
                }
            }
            row.setAlignmentX(Component.CENTER_ALIGNMENT);
            stack.add(row);
        }
        add(stack, new GridBagConstraints());
    }
}
