package application.gui.workbench.mcts;

import application.gui.workbench.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.JComponent;

/**
 * Faint node-count-over-time sparkline for the search-tree growth scrubber.
 */
final class TreeGrowthSparkline extends JComponent {
    /**
     * Serialization identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Session that owns the recorded tree-growth frames.
     */
    private final transient MctsSession session;

    TreeGrowthSparkline(MctsSession session) {
        this.session = session;
        setOpaque(false);
        setPreferredSize(new Dimension(0, 14));
    }

    /**
     * Paints the search-history sparkline.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        int n = session.historySize();
        if (n < 2 || getWidth() <= 1) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int maxNodes = 1;
            for (int i = 0; i < n; i++) {
                MctsSearch.TreeSnapshot frame = session.historyFrame(i);
                if (frame != null) {
                    maxNodes = Math.max(maxNodes, frame.nodes().size());
                }
            }
            Path2D.Double path = new Path2D.Double();
            for (int i = 0; i < n; i++) {
                MctsSearch.TreeSnapshot frame = session.historyFrame(i);
                int count = frame == null ? 0 : frame.nodes().size();
                double x = (double) i / (n - 1) * (w - 1);
                double y = h - 1 - (double) count / maxNodes * (h - 2);
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            g.setColor(Theme.withAlpha(Theme.ACCENT, 95));
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path);
        } finally {
            g.dispose();
        }
    }
}
