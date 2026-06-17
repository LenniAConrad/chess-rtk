package application.gui.workbench.engine;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;

/**
 * Candidate-perspective win/draw/loss distribution chart for gauntlet results.
 */
final class GauntletDistributionChart extends JComponent {

    /**
     * Serialization identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Summary.
     */
    private GauntletResultSummary result = GauntletResultSummary.empty();

    /**
     * Sets result.
     *
     * @param next result
     */
    void setResult(GauntletResultSummary next) {
        result = next == null ? GauntletResultSummary.empty() : next;
        repaint();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(320, 150);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Show the distribution over the games played so far, so the bar
            // fills in live as a gauntlet runs rather than waiting for the end.
            int played = result.wins() + result.draws() + result.losses();
            if (played <= 0) {
                Ui.paintEmptyState(g, new java.awt.Rectangle(0, 0, getWidth(), getHeight()),
                        "No result yet", "Win/draw/loss distribution fills in as games finish.");
                return;
            }
            int x = Theme.SPACE_MD;
            int y = getHeight() / 2 - 14;
            int w = Math.max(1, getWidth() - Theme.SPACE_MD * 2);
            int h = 28;
            int winW = Math.round(w * result.wins() / (float) played);
            int drawW = Math.round(w * result.draws() / (float) played);
            int lossW = Math.max(0, w - winW - drawW);
            // Clip the whole bar to a single rounded rectangle so only the
            // outer left and right corners are rounded; the interior segment
            // boundaries stay square.
            java.awt.Shape oldClip = g.getClip();
            g.clip(new java.awt.geom.RoundRectangle2D.Float(x, y, w, h, Theme.RADIUS, Theme.RADIUS));
            paintSegment(g, x, y, winW, h, Theme.STATUS_SUCCESS_BORDER);
            paintSegment(g, x + winW, y, drawW, h, Theme.STATUS_WARNING_BORDER);
            paintSegment(g, x + winW + drawW, y, lossW, h, Theme.STATUS_ERROR_BORDER);
            g.setClip(oldClip);
            g.setFont(Theme.font(11, java.awt.Font.BOLD));
            g.setColor(Theme.TEXT);
            g.drawString("wins " + result.wins() + "   draws " + result.draws()
                    + "   losses " + result.losses(), x, y + h + 24);
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints one segment.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     * @param color color
     */
    private static void paintSegment(Graphics2D g, int x, int y, int w, int h, Color color) {
        if (w <= 0) {
            return;
        }
        g.setColor(color);
        g.fillRect(x, y, w, h);
    }
}
