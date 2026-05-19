package application.gui.workbench;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.List;

import chess.core.Move;

/**
 * Compact live renderer for MCTS edge weights in the Network tab.
 *
 * <p>The neural-network weights are static during inference; MCTS updates the
 * tree edge weights. This panel renders those live search weights while the
 * network views follow the currently evaluated leaf.</p>
 */
final class WorkbenchMctsWeightsPanel extends javax.swing.JComponent {

    /**
     * Serialization identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Preferred height.
     */
    private static final int HEIGHT = 260;

    /**
     * Rows rendered before the panel becomes visually dense.
     */
    private static final int MAX_ROWS = 9;

    /**
     * Latest MCTS snapshot.
     */
    private WorkbenchMctsSearch.Snapshot snapshot;

    /**
     * Creates the panel.
     */
    WorkbenchMctsWeightsPanel() {
        setOpaque(false);
        setVisible(false);
        setPreferredSize(new Dimension(720, HEIGHT));
    }

    /**
     * Shows a new MCTS snapshot.
     *
     * @param next snapshot
     */
    void setSnapshot(WorkbenchMctsSearch.Snapshot next) {
        snapshot = next;
        setVisible(next != null);
        repaint();
    }

    /**
     * Clears the panel.
     */
    void clear() {
        snapshot = null;
        setVisible(false);
        repaint();
    }

    /**
     * Returns the fixed preferred size for the weights card.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(720, HEIGHT);
    }

    /**
     * Paints the latest MCTS edge-weight snapshot.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Rectangle bounds = new Rectangle(0, 0, getWidth(), getHeight());
            WorkbenchTensorViz.drawCard(g, bounds,
                    "live MCTS weights",
                    "tree edges: visits, policy prior, Q, U and PUCT score",
                    WorkbenchTheme.ACCENT);
            if (snapshot == null) {
                drawEmpty(g, bounds);
                return;
            }
            drawHeader(g, bounds);
            drawLiveBoard(g, bounds);
            drawRows(g, bounds);
        } finally {
            g.dispose();
        }
    }

    /**
     * Draws the idle state.
     */
    private void drawEmpty(Graphics2D g, Rectangle bounds) {
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(12, Font.PLAIN));
        g.drawString("Start MCTS to stream edge weights here.",
                bounds.x + 14, bounds.y + 62);
    }

    /**
     * Draws the top summary row.
     */
    private void drawHeader(Graphics2D g, Rectangle bounds) {
        String best = snapshot.bestMove() == chess.core.Move.NO_MOVE
                ? "-"
                : chess.core.Move.toString(snapshot.bestMove());
        String leaf = snapshot.exploringLineText();
        if (leaf == null || leaf.isBlank()) {
            leaf = "root";
        }
        Font font = WorkbenchTheme.font(11, Font.PLAIN);
        g.setFont(font);
        g.setColor(WorkbenchTheme.MUTED);
        FontMetrics fm = g.getFontMetrics();
        String text = String.format("%,d visits | root %s | best %s | leaf %s",
                snapshot.playouts(), snapshot.rootScoreLabel(), best,
                WorkbenchUi.elide(leaf, fm, Math.max(80, contentRight(bounds) - bounds.x - 180)));
        g.drawString(text, bounds.x + 14, bounds.y + 58);
    }

    /**
     * Draws the board for the MCTS leaf currently being evaluated.
     */
    private void drawLiveBoard(Graphics2D g, Rectangle bounds) {
        if (snapshot.exploringPosition() == null || bounds.width < 520) {
            return;
        }
        int side = Math.min(204, bounds.height - 54);
        int x = bounds.x + bounds.width - side - 16;
        int y = bounds.y + 42;
        Rectangle board = new Rectangle(x, y, side, side);
        g.setFont(WorkbenchTheme.font(10, Font.BOLD));
        g.setColor(WorkbenchTheme.MUTED);
        g.drawString("current board", board.x, board.y - 7);
        WorkbenchTensorViz.drawMiniBoard(g, board);
        WorkbenchTensorViz.drawPositionPieces(g, board, snapshot.exploringPosition().toString());
        short[] line = snapshot.exploringLine();
        if (line != null && line.length > 0) {
            short last = line[line.length - 1];
            WorkbenchTensorViz.drawBoardSquareRing(g, board, Move.getFromIndex(last),
                    WorkbenchTheme.withAlpha(WorkbenchTheme.ACCENT, 220));
            WorkbenchTensorViz.drawBoardSquareRing(g, board, Move.getToIndex(last),
                    WorkbenchTheme.withAlpha(WorkbenchTensorViz.POSITIVE, 230));
        }
    }

    /**
     * Draws root edge rows.
     */
    private void drawRows(Graphics2D g, Rectangle bounds) {
        List<WorkbenchMctsSearch.Row> rows = snapshot.rows();
        if (rows.isEmpty()) {
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(12, Font.PLAIN));
            g.drawString("No expanded root edges yet.", bounds.x + 14, bounds.y + 86);
            return;
        }
        int left = bounds.x + 14;
        int top = bounds.y + 72;
        int rowH = 14;
        int gap = 6;
        int moveW = 54;
        int right = contentRight(bounds);
        int available = Math.max(300, right - left - moveW - 42);
        int visitW = Math.max(110, available * 36 / 100);
        int priorW = Math.max(72, available * 20 / 100);
        int qW = Math.max(72, available * 18 / 100);
        int scoreW = Math.max(72, right - left - moveW - visitW - priorW - qW - 44);
        int totalVisits = Math.max(1, snapshot.rootVisits());
        g.setFont(WorkbenchTheme.font(10, Font.BOLD));
        g.setColor(WorkbenchTheme.MUTED);
        g.drawString("move", left, top - 8);
        g.drawString("visits", left + moveW, top - 8);
        g.drawString("prior", left + moveW + visitW + 10, top - 8);
        g.drawString("Q", left + moveW + visitW + priorW + 20, top - 8);
        g.drawString("PUCT", left + moveW + visitW + priorW + qW + 30, top - 8);

        int count = Math.min(MAX_ROWS, rows.size());
        for (int i = 0; i < count; i++) {
            WorkbenchMctsSearch.Row row = rows.get(i);
            int y = top + i * (rowH + gap);
            drawMoveLabel(g, row, left, y, moveW, rowH);
            drawUnsignedBar(g, left + moveW, y, visitW, rowH,
                    row.visits() / (double) totalVisits,
                    WorkbenchTensorViz.POSITIVE,
                    String.format("%d", row.visits()));
            drawUnsignedBar(g, left + moveW + visitW + 10, y, priorW, rowH,
                    row.prior(), WorkbenchTheme.ACCENT,
                    String.format("%.1f%%", row.prior() * 100.0));
            drawSignedBar(g, left + moveW + visitW + priorW + 20, y, qW, rowH,
                    row.q(), String.format("%+.2f", row.q()));
            drawSignedBar(g, left + moveW + visitW + priorW + qW + 30, y, scoreW, rowH,
                    row.score(), String.format("%+.2f", row.score()));
        }
    }

    /**
     * Returns the right edge available to the bar chart, leaving space for the
     * live board on wide layouts.
     */
    private static int contentRight(Rectangle bounds) {
        if (bounds.width < 520) {
            return bounds.x + bounds.width - 16;
        }
        return bounds.x + bounds.width - 236;
    }

    /**
     * Draws one move label.
     */
    private void drawMoveLabel(Graphics2D g, WorkbenchMctsSearch.Row row,
            int x, int y, int w, int h) {
        g.setFont(WorkbenchTheme.font(10, Font.BOLD));
        g.setColor(WorkbenchTheme.TEXT);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(WorkbenchUi.elide(row.san(), fm, w - 2), x, y + h - 2);
    }

    /**
     * Draws an unsigned horizontal bar.
     */
    private void drawUnsignedBar(Graphics2D g, int x, int y, int w, int h,
            double value, Color fill, String label) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        g.setColor(WorkbenchTheme.ELEVATED_SOLID);
        g.fillRect(x, y, w, h);
        g.setColor(fill);
        g.fillRect(x, y, Math.max(1, (int) Math.round(w * clamped)), h);
        drawTinyLabel(g, label, x + 3, y + h - 2);
    }

    /**
     * Draws a signed centered bar.
     */
    private void drawSignedBar(Graphics2D g, int x, int y, int w, int h,
            double value, String label) {
        double clamped = Math.max(-1.0, Math.min(1.0, value));
        int mid = x + w / 2;
        g.setColor(WorkbenchTheme.ELEVATED_SOLID);
        g.fillRect(x, y, w, h);
        g.setColor(clamped >= 0.0 ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE);
        int len = Math.max(1, (int) Math.round((w / 2.0) * Math.abs(clamped)));
        if (clamped >= 0.0) {
            g.fillRect(mid, y, len, h);
        } else {
            g.fillRect(mid - len, y, len, h);
        }
        g.setColor(WorkbenchTheme.LINE);
        g.drawLine(mid, y, mid, y + h);
        drawTinyLabel(g, label, x + 3, y + h - 2);
    }

    /**
     * Draws small text on a bar.
     */
    private void drawTinyLabel(Graphics2D g, String label, int x, int baseline) {
        g.setFont(WorkbenchTheme.font(9, Font.BOLD));
        g.setColor(WorkbenchTheme.TEXT);
        g.drawString(label, x, baseline);
    }
}
