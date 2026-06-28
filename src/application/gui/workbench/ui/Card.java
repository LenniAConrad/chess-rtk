package application.gui.workbench.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * A static raised card: an elevated rounded surface (painted via
 * {@link Theme#paintElevatedCard}) holding an optional header eyebrow and a body
 * component. Unlike the dashboard's interactive card it has no hover-lift, so it
 * suits grouped content that simply wants the shared card surface — chart
 * panels, setup forms, summaries — in either theme.
 */
final class Card extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Corner radius of the card surface, a touch softer than control chrome.
     */
    private static final int ARC = Theme.RADIUS + 1;

    /**
     * Creates a card without a trailing header component.
     *
     * @param title header text, or {@code null} for a headerless card
     * @param body card body component
     */
    public Card(String title, JComponent body) {
        this(title, null, body);
    }

    /**
     * Creates a card.
     *
     * @param title header text, or {@code null} for a headerless card
     * @param trailing optional right-aligned header component, or {@code null}
     * @param body card body component
     */
    public Card(String title, JComponent trailing, JComponent body) {
        setOpaque(false);
        setLayout(new BorderLayout(0, Theme.SPACE_SM));
        setBorder(Theme.pad(Theme.SPACE_MD));
        if (title != null) {
            add(Theme.cardHeader(title, trailing), BorderLayout.NORTH);
        }
        if (body != null) {
            if (body instanceof JPanel panel) {
                panel.setOpaque(false);
            }
            add(body, BorderLayout.CENTER);
        }
    }

    /**
     * Paints the elevated card surface behind the content.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            Theme.paintElevatedCard(g, getWidth(), getHeight(), ARC, 0f);
        } finally {
            g.dispose();
        }
        super.paintComponent(graphics);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dimension getMaximumSize() {
        Dimension preferred = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, preferred.height);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getAlignmentX() {
        return Component.LEFT_ALIGNMENT;
    }
}
