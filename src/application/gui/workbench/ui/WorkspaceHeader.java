package application.gui.workbench.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;

/**
 * Top band for a workbench surface.
 *
 * <p>The header carries the page or mode title on the left, a compact live
 * context summary through the middle, and primary actions on the right. It is a
 * shell primitive, not a card, so it can sit directly under the editor tab strip
 * without adding nested panels.</p>
 */
public final class WorkspaceHeader extends JPanel {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Header border that repaints from current theme tokens after a theme change.
     */
    private static final Border HEADER_BORDER = new AbstractBorder() {
        private static final long serialVersionUID = 1L;

        /**
         * {@inheritDoc}
         */
        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(Theme.SPACE_SM, Theme.SPACE_MD, Theme.SPACE_SM, Theme.SPACE_MD);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Insets getBorderInsets(Component component, Insets insets) {
            insets.top = Theme.SPACE_SM;
            insets.left = Theme.SPACE_MD;
            insets.bottom = Theme.SPACE_SM;
            insets.right = Theme.SPACE_MD;
            return insets;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            graphics.setColor(Theme.LINE);
            graphics.drawLine(x, y + height - 1, x + width, y + height - 1);
        }
    };

    /**
     * Title label.
     */
    private final JLabel titleLabel = new JLabel();

    /**
     * Eliding summary label.
     */
    private final JLabel contextLabel = new ElidingLabel();

    /**
     * Right-side action host.
     */
    private final JPanel actionHost = Ui.transparentPanel(new BorderLayout());

    /**
     * Creates a workspace header.
     *
     * @param title title text
     * @param context one-line context summary
     * @param actions optional right-side actions
     */
    public WorkspaceHeader(String title, String context, JComponent actions) {
        super(new GridBagLayout());
        setName("workbench.workspaceHeader");
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setBorder(HEADER_BORDER);

        titleLabel.setFont(Theme.font(Theme.FONT_PAGE_TITLE, Font.BOLD));
        Theme.foreground(titleLabel, Theme.ForegroundRole.TEXT);
        titleLabel.setVerticalAlignment(JLabel.CENTER);

        contextLabel.setFont(Theme.font(Theme.FONT_CONTROL, Font.PLAIN));
        Theme.foreground(contextLabel, Theme.ForegroundRole.MUTED);
        contextLabel.setVerticalAlignment(JLabel.CENTER);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;

        c.gridx = 0;
        c.weightx = 0.0d;
        c.insets = new Insets(0, 0, 0, Theme.SPACE_LG);
        add(titleLabel, c);

        c.gridx = 1;
        c.weightx = 1.0d;
        c.insets = new Insets(0, 0, 0, Theme.SPACE_LG);
        add(contextLabel, c);

        c.gridx = 2;
        c.weightx = 0.0d;
        c.insets = new Insets(0, 0, 0, 0);
        add(actionHost, c);

        setTitle(title);
        setContext(context);
        setActions(actions);
    }

    /**
     * Sets the header title.
     *
     * @param title title text
     */
    public void setTitle(String title) {
        titleLabel.setText(title == null ? "" : title.trim());
    }

    /**
     * Sets the context summary.
     *
     * @param context context text
     */
    public void setContext(String context) {
        String text = context == null ? "" : context.trim();
        contextLabel.setText(text);
        contextLabel.setToolTipText(text.isBlank() ? null : text);
    }

    /**
     * Installs right-side actions.
     *
     * @param actions action component, or null
     */
    public void setActions(JComponent actions) {
        actionHost.removeAll();
        if (actions != null) {
            actionHost.add(actions, BorderLayout.CENTER);
        }
        actionHost.setVisible(actions != null);
        revalidate();
        repaint();
    }

    /**
     * Returns the title text.
     *
     * @return title
     */
    public String title() {
        return titleLabel.getText();
    }

    /**
     * Returns the context summary text.
     *
     * @return context summary
     */
    public String context() {
        return contextLabel.getText();
    }

    /**
     * Label that paints one elided line instead of letting long context strings
     * push action buttons off the header.
     */
    private static final class ElidingLabel extends JLabel {
        private static final long serialVersionUID = 1L;

        /**
         * {@inheritDoc}
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(getFont());
                g.setColor(getForeground());
                FontMetrics fm = g.getFontMetrics();
                String text = Ui.elide(getText(), fm, getWidth());
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g.drawString(text, 0, y);
            } finally {
                g.dispose();
            }
        }
    }
}
