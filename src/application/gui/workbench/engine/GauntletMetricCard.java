package application.gui.workbench.engine;

import application.gui.workbench.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Compact metric card used by the gauntlet result dashboard.
 */
final class GauntletMetricCard extends JPanel {

    /**
     * Serialization identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Title.
     */
    private final JLabel title = new JLabel();

    /**
     * Value.
     */
    private final JLabel value = new JLabel("-");

    /**
     * Detail.
     */
    private final JLabel detail = new JLabel("-");

    /**
     * Creates a metric card.
     *
     * @param label label
     */
    GauntletMetricCard(String label) {
        super(new BorderLayout(0, 3));
        setOpaque(false);
        setBorder(Theme.pad(8, 10, 8, 10));
        title.setText(label);
        Theme.foreground(title, Theme.ForegroundRole.MUTED);
        title.setFont(Theme.font(11, Font.BOLD));
        Theme.foreground(value, Theme.ForegroundRole.TEXT);
        value.setFont(Theme.font(18, Font.BOLD));
        Theme.foreground(detail, Theme.ForegroundRole.MUTED);
        detail.setFont(Theme.font(11, Font.PLAIN));
        add(title, BorderLayout.NORTH);
        add(value, BorderLayout.CENTER);
        add(detail, BorderLayout.SOUTH);
    }

    /**
     * Sets metric content.
     *
     * @param nextValue value
     * @param nextDetail detail
     */
    void setValue(String nextValue, String nextDetail) {
        value.setText(nextValue == null || nextValue.isBlank() ? "-" : nextValue);
        detail.setText(nextDetail == null || nextDetail.isBlank() ? "-" : nextDetail);
        setToolTipText(title.getText() + ": " + value.getText() + " - " + detail.getText());
        repaint();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension base = super.getPreferredSize();
        return new Dimension(Math.max(142, base.width), Math.max(76, base.height));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Theme.ELEVATED_SOLID);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
            g.setColor(Theme.LINE);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
        } finally {
            g.dispose();
        }
        super.paintComponent(graphics);
    }
}
