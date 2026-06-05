package application.gui.workbench.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A rounded, hairline-bordered group that binds an optional caption and a block
 * of controls into one boxed unit — the same "chip" chrome the command builder
 * uses for its flag cells, lifted into a reusable widget so other tabs can group
 * related controls the same way.
 *
 * <p>It paints its own surface (so a theme toggle cannot restamp it) and hugs
 * its content height, so it stays tight inside wrapping or stacking parents.</p>
 */
public final class GroupBox extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Corner radius — matches the app-wide control radius so the box reads as
     * the same family as buttons and cards.
     */
    private static final int ARC = Theme.RADIUS;

    /**
     * Wraps a content block with rounded chrome, padding, and an optional caption.
     *
     * @param title caption shown above the content, or null/blank for none
     * @param content content component
     */
    public GroupBox(String title, JComponent content) {
        super(new BorderLayout(0, 2));
        setOpaque(false);
        setBorder(Theme.pad(Theme.SPACE_XS + 1, Theme.SPACE_SM + 1, Theme.SPACE_XS + 1, Theme.SPACE_SM + 1));
        setAlignmentX(LEFT_ALIGNMENT);
        if (title != null && !title.isBlank()) {
            JLabel caption = new JLabel(title);
            caption.setFont(Theme.font(10, Font.BOLD));
            caption.setForeground(Theme.MUTED);
            caption.setAlignmentX(LEFT_ALIGNMENT);
            add(caption, BorderLayout.NORTH);
        }
        content.setAlignmentX(LEFT_ALIGNMENT);
        add(content, BorderLayout.CENTER);
    }

    /**
     * Hugs the content height so a BoxLayout parent cannot stretch the box.
     *
     * @return maximum size with content height
     */
    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    /**
     * Paints the rounded surface and hairline border.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g.setColor(Theme.ELEVATED_SOLID);
            g.fillRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
            g.setColor(Theme.CARD_BORDER);
            g.drawRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
        } finally {
            g.dispose();
        }
        super.paintComponent(graphics);
    }

    /**
     * Builds a boxed group from a caption and a horizontal row of controls.
     *
     * @param title caption shown above the controls, or null/blank for none
     * @param controls controls laid out left-to-right inside the box
     * @return boxed group
     */
    public static GroupBox of(String title, Component... controls) {
        JPanel row = new JPanel(new WrappingFlowLayout(java.awt.FlowLayout.LEFT, Theme.SPACE_SM, Theme.SPACE_XS));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        for (Component control : controls) {
            if (control != null) {
                row.add(control);
            }
        }
        return new GroupBox(title, row);
    }
}
