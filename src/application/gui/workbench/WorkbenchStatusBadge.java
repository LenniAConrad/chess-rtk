package application.gui.workbench;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

/**
 * Compact status indicator for the workbench network toolbar.
 *
 * <p>Replaces the previous bare italic {@link javax.swing.JLabel}: a small
 * coloured state dot plus a short message, so the user can tell at a glance
 * whether the panel is idle, working, finished, or in error — instead of
 * having to read grey italic text in the corner.</p>
 */
final class WorkbenchStatusBadge extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Diameter of the state dot in pixels.
     */
    private static final int DOT = 8;

    /**
     * Gap between the dot and the message text.
     */
    private static final int GAP = WorkbenchTheme.SPACE_SM;

    /**
     * The severity of a status message, which selects the dot colour.
     */
    enum Kind {

        /**
         * Idle / informational — a muted dot.
         */
        IDLE(WorkbenchTheme.MUTED),

        /**
         * Work in progress — an accent dot.
         */
        BUSY(WorkbenchTheme.ACCENT),

        /**
         * Finished successfully — a green dot.
         */
        SUCCESS(WorkbenchTheme.STATUS_SUCCESS_TEXT),

        /**
         * Failed — a red dot.
         */
        ERROR(WorkbenchTheme.STATUS_ERROR_TEXT);

        /**
         * Dot colour for this kind.
         */
        private final Color dot;

        /**
         * Creates a badge kind.
         *
         * @param dot dot color
         */
        Kind(Color dot) {
            this.dot = dot;
        }
    }

    /**
     * Current message text.
     */
    private String text = "";

    /**
     * Current message severity.
     */
    private Kind kind = Kind.IDLE;

    /**
     * Creates an empty status badge.
     */
    WorkbenchStatusBadge() {
        setOpaque(false);
        setFont(WorkbenchTheme.font(11, Font.PLAIN));
    }

    /**
     * Shows an idle / informational message.
     *
     * @param message message text
     */
    void idle(String message) {
        set(message, Kind.IDLE);
    }

    /**
     * Shows a work-in-progress message.
     *
     * @param message message text
     */
    void busy(String message) {
        set(message, Kind.BUSY);
    }

    /**
     * Shows a success message.
     *
     * @param message message text
     */
    void success(String message) {
        set(message, Kind.SUCCESS);
    }

    /**
     * Shows an error message.
     *
     * @param message message text
     */
    void error(String message) {
        set(message, Kind.ERROR);
    }

    /**
     * Sets the message text and severity together.
     *
     * @param message message text (null treated as empty)
     * @param newKind severity
     */
    void set(String message, Kind newKind) {
        this.text = message == null ? "" : message;
        this.kind = newKind == null ? Kind.IDLE : newKind;
        revalidate();
        repaint();
    }

    /**
     * Returns the preferred size from the current text plus the dot and gap.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int width = DOT + GAP + fm.stringWidth(text);
        return new Dimension(width, Math.max(WorkbenchTheme.CONTROL_HEIGHT, fm.getHeight()));
    }

    /**
     * Paints the state dot and the message text.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        if (text.isEmpty()) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            int cy = getHeight() / 2;
            g.setColor(kind.dot);
            g.fillOval(0, cy - DOT / 2, DOT, DOT);
            g.setColor(WorkbenchTheme.MUTED);
            int baseline = cy + fm.getAscent() / 2 - 1;
            g.drawString(text, DOT + GAP, baseline);
        } finally {
            g.dispose();
        }
    }
}
