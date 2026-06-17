package application.gui.workbench.engine;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;

/**
 * Running candidate score chart for gauntlet game streams.
 */
final class GauntletCumulativeScoreChart extends JComponent {

    /**
     * Serialization identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Progress points.
     */
    private List<GauntletProgressPoint> points = List.of();

    /**
     * Sets progress points.
     *
     * @param next points
     */
    void setPoints(List<GauntletProgressPoint> next) {
        points = next == null ? List.of() : List.copyOf(next);
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
            if (points.isEmpty()) {
                Ui.paintEmptyState(g, new java.awt.Rectangle(0, 0, getWidth(), getHeight()),
                        "No games yet", "The candidate's running score plots here as each game finishes.");
                return;
            }
            int left = Theme.SPACE_LG;
            int top = Theme.SPACE_MD;
            int width = Math.max(1, getWidth() - Theme.SPACE_LG * 2);
            int height = Math.max(1, getHeight() - Theme.SPACE_LG * 2);
            int midY = top + height / 2;
            g.setColor(Theme.LINE);
            g.drawLine(left, midY, left + width, midY);
            g.setColor(Theme.ACCENT);
            int lastX = left;
            int lastY = scoreY(points.get(0), top, height);
            for (int i = 1; i < points.size(); i++) {
                int x = left + Math.round(i * width / (float) Math.max(1, points.size() - 1));
                int y = scoreY(points.get(i), top, height);
                g.drawLine(lastX, lastY, x, y);
                lastX = x;
                lastY = y;
            }
            g.fillOval(lastX - 3, lastY - 3, 6, 6);
            g.setFont(Theme.font(11, java.awt.Font.BOLD));
            g.setColor(Theme.TEXT);
            GauntletProgressPoint latest = points.get(points.size() - 1);
            g.drawString(String.format(Locale.ROOT, "score %.1f%% after %d games",
                    latest.score() * 100.0d, latest.games()), left, top + height + 16);
        } finally {
            g.dispose();
        }
    }

    /**
     * Returns y coordinate for a score.
     *
     * @param point point
     * @param top chart top
     * @param height chart height
     * @return y
     */
    private static int scoreY(GauntletProgressPoint point, int top, int height) {
        double score = Math.max(0.0d, Math.min(1.0d, point.score()));
        return top + (int) Math.round((1.0d - score) * height);
    }
}
