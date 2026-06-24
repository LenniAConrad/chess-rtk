package application.gui.workbench.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Shared centered empty-state block: a confident title, an optional hint, and
 * optional action buttons.
 *
 * <p>An empty state with actions is treated as a "primary" placeholder that owns
 * an otherwise empty surface — it gets a decorative mark and a minimum height so
 * it reads as designed rather than as stray text. An empty state without actions
 * is a quiet inline placeholder (e.g. "No setup issues") and stays compact: no
 * mark, no height floor, so it does not overweight a small card.</p>
 */
public final class EmptyState extends JPanel {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Title point size for empty states.
     */
    private static final int TITLE_SIZE = 16;

    /**
     * Hint point size for empty states.
     */
    private static final int HINT_SIZE = 12;

    /**
     * Width of the decorative "empty content" mark drawn above the title.
     */
    private static final int MARK_WIDTH = 48;

    /**
     * Height of the decorative mark.
     */
    private static final int MARK_HEIGHT = 38;

    /**
     * Gap between the mark and the title.
     */
    private static final int MARK_GAP = Theme.SPACE_MD;

    /**
     * Minimum height (at base density) so an action-bearing block holds the
     * center of a large empty panel.
     */
    private static final int MIN_HEIGHT = 160;

    /**
     * Whether this is a primary (action-bearing) empty state that earns a mark
     * and a height floor.
     */
    private final boolean rich;

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
        this.rich = actions != null && actions.length > 0;
        JPanel stack = UiLayout.transparentPanel(null);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));

        if (rich) {
            Mark mark = new Mark();
            mark.setAlignmentX(Component.CENTER_ALIGNMENT);
            stack.add(mark);
            stack.add(Box.createVerticalStrut(MARK_GAP));
        }

        JLabel titleLabel = new JLabel(title == null ? "" : title);
        Theme.foreground(titleLabel, Theme.ForegroundRole.TEXT);
        titleLabel.setFont(Theme.font(TITLE_SIZE, Font.BOLD));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        stack.add(titleLabel);
        if (hint != null && !hint.isBlank()) {
            stack.add(Box.createVerticalStrut(Theme.SPACE_XS));
            JLabel hintLabel = new JLabel(hint);
            Theme.foreground(hintLabel, Theme.ForegroundRole.MUTED);
            hintLabel.setFont(Theme.font(HINT_SIZE, Font.PLAIN));
            hintLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            stack.add(hintLabel);
        }
        if (rich) {
            stack.add(Box.createVerticalStrut(Theme.SPACE_LG));
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

    /**
     * Floors the preferred height (density-scaled) for primary empty states so
     * they keep presence in tall panels; compact ones size to content.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        if (rich) {
            size.height = Math.max(size.height, Theme.scaledPx(MIN_HEIGHT));
        }
        return size;
    }

    /**
     * Paints the decorative empty-state mark: a rounded "card" outline with two
     * short placeholder lines, reading as "no content here yet."
     *
     * @param graphics graphics context
     * @param centerX horizontal center
     * @param top top edge of the mark
     * @param color base (muted) color
     */
    static void paintMark(Graphics2D graphics, int centerX, int top, Color color) {
        Graphics2D scratch = (Graphics2D) graphics.create();
        try {
            scratch.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = Theme.scaledPx(MARK_WIDTH);
            int h = Theme.scaledPx(MARK_HEIGHT);
            int x = centerX - w / 2;
            int arc = Theme.scaledPx(13);
            scratch.setColor(Theme.withAlpha(Theme.ACCENT, Theme.isDark() ? 44 : 30));
            scratch.fillRoundRect(x, top, w, h, arc, arc);
            scratch.setStroke(new BasicStroke(1.2f));
            scratch.setColor(Theme.withAlpha(Theme.ACCENT, Theme.isDark() ? 130 : 96));
            scratch.drawRoundRect(x, top, w - 1, h - 1, arc, arc);

            int docW = Theme.scaledPx(25);
            int docH = Theme.scaledPx(20);
            int docX = centerX - docW / 2;
            int docY = top + Theme.scaledPx(9);
            scratch.setColor(Theme.withAlpha(Theme.PANEL_SOLID, Theme.isDark() ? 210 : 235));
            scratch.fillRoundRect(docX, docY, docW, docH, Theme.scaledPx(6), Theme.scaledPx(6));
            scratch.setColor(Theme.withAlpha(color, 150));
            scratch.drawRoundRect(docX, docY, docW - 1, docH - 1, Theme.scaledPx(6), Theme.scaledPx(6));

            int lineX = docX + Theme.scaledPx(6);
            int lineW = docW - Theme.scaledPx(12);
            scratch.setColor(Theme.withAlpha(Theme.ACCENT, Theme.isDark() ? 168 : 140));
            scratch.fillRoundRect(lineX, docY + Theme.scaledPx(7), lineW, 2, 2, 2);
            scratch.setColor(Theme.withAlpha(color, 118));
            scratch.fillRoundRect(lineX, docY + Theme.scaledPx(13), Math.round(lineW * 0.62f), 2, 2, 2);
        } finally {
            scratch.dispose();
        }
    }

    /**
     * Density-scaled height of the mark plus its gap below.
     *
     * @return mark block height in pixels
     */
    static int markBlockHeight() {
        return Theme.scaledPx(MARK_HEIGHT) + Theme.scaledPx(MARK_GAP);
    }

    /**
     * Paints a centered empty-state directly onto a graphics context.
     *
     * @param graphics graphics context
     * @param bounds area to center within
     * @param title short title
     * @param hint one-line hint, or {@code null}
     */
    static void paint(Graphics2D graphics, Rectangle bounds, String title, String hint) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        Graphics2D scratch = (Graphics2D) graphics.create();
        try {
            scratch.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            String titleText = title == null ? "" : title;
            Font titleFont = Theme.font(TITLE_SIZE, Font.BOLD);
            Font hintFont = Theme.font(HINT_SIZE, Font.PLAIN);
            FontMetrics titleMetrics = scratch.getFontMetrics(titleFont);
            FontMetrics hintMetrics = scratch.getFontMetrics(hintFont);
            boolean hasHint = hint != null && !hint.isBlank();
            int markBlock = markBlockHeight();
            int textHeight = titleMetrics.getHeight()
                    + (hasHint ? hintMetrics.getHeight() + Theme.SPACE_XS : 0);
            boolean showMark = bounds.height >= markBlock + textHeight + Theme.SPACE_SM;
            int blockHeight = (showMark ? markBlock : 0) + textHeight;
            int centerX = bounds.x + bounds.width / 2;
            int top = Math.max(bounds.y, bounds.y + (bounds.height - blockHeight) / 2);
            if (showMark) {
                paintMark(scratch, centerX, top, Theme.MUTED);
                top += markBlock;
            }
            int baseline = top + titleMetrics.getAscent();
            scratch.setFont(titleFont);
            scratch.setColor(Theme.TEXT);
            scratch.drawString(titleText, centerX - titleMetrics.stringWidth(titleText) / 2, baseline);
            if (hasHint) {
                scratch.setFont(hintFont);
                scratch.setColor(Theme.MUTED);
                scratch.drawString(hint, centerX - hintMetrics.stringWidth(hint) / 2,
                        baseline + Theme.SPACE_XS + hintMetrics.getHeight());
            }
        } finally {
            scratch.dispose();
        }
    }

    /**
     * The decorative "empty content" mark drawn above the title. Tracks the
     * active density so it stays proportional to the scaled title text.
     */
    private static final class Mark extends JComponent {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Creates the mark.
         */
        Mark() {
            setOpaque(false);
        }

        /**
         * Returns the density-scaled mark size (plus a one-pixel margin).
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Theme.scaledPx(MARK_WIDTH) + 2,
                    Theme.scaledPx(MARK_HEIGHT) + 2);
        }

        /**
         * Pins the maximum size to the preferred size so the mark never stretches
         * in the vertical box layout.
         *
         * @return maximum size
         */
        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        /**
         * Paints the centered mark.
         *
         * @param g graphics context
         */
        @Override
        protected void paintComponent(Graphics g) {
            EmptyState.paintMark((Graphics2D) g, getWidth() / 2, 1, Theme.MUTED);
        }
    }
}
