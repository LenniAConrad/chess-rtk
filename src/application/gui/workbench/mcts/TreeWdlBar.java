package application.gui.workbench.mcts;

import application.gui.workbench.network.TensorViz;
import application.gui.workbench.ui.Theme;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;

/**
 * Compact win/draw/loss and value visualization for the search-tree inspector.
 */
final class TreeWdlBar extends JComponent {
    /**
     * Serialization identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Win / draw / loss probabilities and mean value of the shown node.
     */
    private double win;
    /**
     * Draw probability for the shown node.
     */
    private double draw;
    /**
     * Loss probability for the shown node.
     */
    private double loss;
    /**
     * Mean value for the shown node, in root perspective.
     */
    private double q;

    /**
     * True once a node's values have been set.
     */
    private boolean has;

    /**
     * Creates the tree WDL bar.
     */
    TreeWdlBar() {
        setOpaque(false);
        setPreferredSize(new Dimension(0, 36));
    }

    /**
     * Updates the displayed WDL distribution and mean value.
     *
     * @param w win probability
     * @param d draw probability
     * @param l loss probability
     * @param value mean value
     */
    void set(double w, double d, double l, double value) {
        win = w;
        draw = d;
        loss = l;
        q = value;
        has = true;
        repaint();
    }

    /**
     * Clears value.
     */
    void clear() {
        has = false;
        repaint();
    }

    /**
     * Paints the WDL distribution and centered evaluation bar.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        if (!has) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = Math.max(1, getWidth());
            int barH = 13;
            double total = Math.max(1e-6, win + draw + loss);
            int ww = (int) Math.round(w * win / total);
            int dw = (int) Math.round(w * draw / total);
            int x = 0;
            g.setColor(TensorViz.POSITIVE);
            g.fillRect(x, 0, ww, barH);
            x += ww;
            g.setColor(Theme.withAlpha(Theme.MUTED, 150));
            g.fillRect(x, 0, dw, barH);
            x += dw;
            g.setColor(TensorViz.NEGATIVE);
            g.fillRect(x, 0, Math.max(0, w - x), barH);
            g.setFont(Theme.font(9, Font.BOLD));
            g.setColor(Theme.TEXT);
            g.drawString(String.format("%.0f", win * 100), 3, barH - 3);
            String lossText = String.format("%.0f", loss * 100);
            g.drawString(lossText, w - g.getFontMetrics().stringWidth(lossText) - 3, barH - 3);
            int evalY = barH + 7;
            int evalH = 8;
            g.setColor(Theme.withAlpha(Theme.LINE, 120));
            g.fillRoundRect(0, evalY, w, evalH, evalH, evalH);
            int mid = w / 2;
            int qx = (int) Math.round(mid + Math.max(-1, Math.min(1, q)) * (w / 2.0));
            g.setColor(q >= 0 ? TensorViz.POSITIVE : TensorViz.NEGATIVE);
            if (q >= 0) {
                g.fillRoundRect(mid, evalY, Math.max(0, qx - mid), evalH, evalH, evalH);
            } else {
                g.fillRoundRect(qx, evalY, Math.max(0, mid - qx), evalH, evalH, evalH);
            }
            g.setColor(Theme.withAlpha(Theme.TEXT, 180));
            g.fillRect(mid - 1, evalY - 1, 2, evalH + 2);
        } finally {
            g.dispose();
        }
    }
}
