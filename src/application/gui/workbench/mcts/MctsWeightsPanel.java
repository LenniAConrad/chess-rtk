package application.gui.workbench.mcts;

import application.gui.workbench.board.BoardStyle;
import application.gui.workbench.network.TensorViz;
import application.gui.workbench.ui.NotationPainter;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import chess.core.Move;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.List;

/**
 * Compact live renderer for MCTS edge weights in the Network tab.
 *
 * <p>The neural-network weights are static during inference; MCTS updates the
 * tree edge weights. This panel renders those live search weights while the
 * network views follow the currently evaluated leaf.</p>
 */
public final class MctsWeightsPanel extends javax.swing.JComponent {

    /**
     * Serialization identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Preferred height.
     */
    private static final int HEIGHT = 286;

    /**
     * Preferred height when the host view already supplies the position board.
     */
    private static final int COMPACT_HEIGHT = 226;

    /**
     * Top coordinate for the live summary chips.
     */
    private static final int SUMMARY_TOP = 42;

    /**
     * Height of one live summary chip.
     */
    private static final int SUMMARY_CHIP_HEIGHT = 24;

    /**
     * Gap between live summary chips.
     */
    private static final int SUMMARY_GAP = 8;

    /**
     * Extra space between the summary chips and the edge table.
     */
    private static final int SUMMARY_TABLE_GAP = 24;

    /**
     * Row height for root edge metrics.
     */
    private static final int ROW_HEIGHT = 15;

    /**
     * Gap between root edge rows.
     */
    private static final int ROW_GAP = 7;

    /**
     * Rows rendered before the panel becomes visually dense.
     */
    private static final int MAX_ROWS = 9;

    /**
     * Latest MCTS snapshot.
     */
    private MctsSearch.Snapshot snapshot;

    /**
     * Whether to reserve space for the currently explored leaf board.
     */
    private boolean leafBoardVisible = true;

    /**
     * Creates the panel.
     */
    public MctsWeightsPanel() {
        setOpaque(false);
        setVisible(false);
        setPreferredSize(new Dimension(720, preferredHeight()));
    }

    /**
     * Shows a new MCTS snapshot.
     *
     * @param next snapshot
     */
    public void setSnapshot(MctsSearch.Snapshot next) {
        snapshot = next;
        setVisible(next != null);
        repaint();
    }

    /**
     * Clears the panel.
     */
    public void clear() {
        snapshot = null;
        setVisible(false);
        repaint();
    }

    /**
     * Shows or hides the embedded MCTS leaf board.
     *
     * @param visible true to paint the leaf board
     */
    public void setLeafBoardVisible(boolean visible) {
        if (leafBoardVisible == visible) {
            return;
        }
        leafBoardVisible = visible;
        revalidate();
        repaint();
    }

    /**
     * Returns whether the embedded MCTS leaf board is visible.
     *
     * @return true when the leaf board is painted
     */
    public boolean isLeafBoardVisible() {
        return leafBoardVisible;
    }

    /**
     * Returns the preferred size for the current display density.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(720, preferredHeight());
    }

    /**
     * Returns the height appropriate for the current board-visibility mode.
     *
     * @return preferred height
     */
    private int preferredHeight() {
        return leafBoardVisible ? HEIGHT : COMPACT_HEIGHT;
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
            TensorViz.drawCard(g, bounds,
                    "live MCTS weights",
                    "tree edges: visits, policy prior, Q, U and PUCT score",
                    Theme.ACCENT);
            if (snapshot == null) {
                drawEmpty(g, bounds);
                return;
            }
            int rowTop = drawHeader(g, bounds);
            if (leafBoardVisible) {
                drawLiveBoard(g, bounds);
            }
            drawRows(g, bounds, rowTop);
        } finally {
            g.dispose();
        }
    }

    /**
     * Draws the idle state.
     *
     * @param g graphics context
     * @param bounds drawing bounds
     */
    private void drawEmpty(Graphics2D g, Rectangle bounds) {
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(12, Font.PLAIN));
        g.drawString("Start MCTS to stream edge weights here.",
                bounds.x + 14, bounds.y + 62);
    }

    /**
     * Draws the top summary row.
     *
     * @param g graphics context
     * @param bounds drawing bounds
     * @return top coordinate for the edge rows
     */
    private int drawHeader(Graphics2D g, Rectangle bounds) {
        String best = snapshot.bestMove() == chess.core.Move.NO_MOVE
                ? "-"
                : chess.core.Move.toString(snapshot.bestMove());
        String leaf = snapshot.exploringLineText();
        if (leaf == null || leaf.isBlank()) {
            leaf = "root";
        }
        Font font = Theme.font(10, Font.BOLD);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int left = bounds.x + 14;
        int top = bounds.y + SUMMARY_TOP;
        int right = contentRight(bounds, leafBoardVisible);
        int width = Math.max(120, right - left);
        boolean compact = width < 620;
        int columns = compact ? 2 : 4;
        int chipW = Math.max(80, (width - SUMMARY_GAP * (columns - 1)) / columns);
        int leafMax = compact ? chipW - 58 : chipW - 50;
        String visits = String.format("%,d", snapshot.playouts());
        String shortLeaf = Ui.elide(leaf, fm, Math.max(44, leafMax));

        drawMetricChip(g, left, top, chipW, "visits", visits, TensorViz.POSITIVE);
        drawMetricChip(g, left + chipW + SUMMARY_GAP, top, chipW,
                "root", snapshot.rootScoreLabel(), Theme.ACCENT);
        if (compact) {
            int secondTop = top + SUMMARY_CHIP_HEIGHT + SUMMARY_GAP;
            drawMetricChip(g, left, secondTop, chipW, "best", best, TensorViz.POLICY);
            drawMetricChip(g, left + chipW + SUMMARY_GAP, secondTop, chipW,
                    "leaf", shortLeaf, TensorViz.TRUNK);
            return secondTop + SUMMARY_CHIP_HEIGHT + SUMMARY_TABLE_GAP;
        }

        drawMetricChip(g, left + (chipW + SUMMARY_GAP) * 2, top, chipW,
                "best", best, TensorViz.POLICY);
        drawMetricChip(g, left + (chipW + SUMMARY_GAP) * 3, top, chipW,
                "leaf", shortLeaf, TensorViz.TRUNK);
        return top + SUMMARY_CHIP_HEIGHT + SUMMARY_TABLE_GAP;
    }

    /**
     * Draws the board for the MCTS leaf currently being evaluated.
     *
     * @param g graphics context
     * @param bounds drawing bounds
     */
    private void drawLiveBoard(Graphics2D g, Rectangle bounds) {
        if (snapshot.exploringPosition() == null || bounds.width < 520) {
            return;
        }
        int side = Math.min(204, bounds.height - 54);
        int x = bounds.x + bounds.width - side - 16;
        int y = bounds.y + 42;
        Rectangle board = new Rectangle(x, y, side, side);
        g.setFont(Theme.font(10, Font.BOLD));
        g.setColor(Theme.MUTED);
        // Labelled "MCTS leaf" so users do not read it as a duplicate of the
        // main inspection board; this is the position the search is
        // currently evaluating, not the user-selected position.
        g.drawString("MCTS leaf", board.x, board.y - 7);
        TensorViz.drawMiniBoard(g, board);
        TensorViz.drawPositionPieces(g, board, snapshot.exploringPosition().toString());
        short[] line = snapshot.exploringLine();
        if (line != null && line.length > 0) {
            short last = line[line.length - 1];
            BoardStyle.drawInsetSquareHighlight(g, BoardStyle.fieldSquareBounds(board, Move.getFromIndex(last), true),
                    Theme.withAlpha(Theme.ACCENT, 220));
            BoardStyle.drawInsetSquareHighlight(g, BoardStyle.fieldSquareBounds(board, Move.getToIndex(last), true),
                    Theme.withAlpha(TensorViz.POSITIVE, 230));
        }
        TensorViz.drawBoardCoordinates(g, board);
    }

    /**
     * Draws root edge rows.
     *
     * @param g graphics context
     * @param bounds drawing bounds
     */
    private void drawRows(Graphics2D g, Rectangle bounds, int top) {
        List<MctsSearch.Row> rows = snapshot.rows();
        if (rows.isEmpty()) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(12, Font.PLAIN));
            g.drawString("No expanded root edges yet.", bounds.x + 14, top + 10);
            return;
        }
        int left = bounds.x + 14;
        int moveW = 54;
        int right = contentRight(bounds, leafBoardVisible);
        int available = Math.max(300, right - left - moveW - 42);
        int visitW = Math.max(110, available * 36 / 100);
        int priorW = Math.max(72, available * 20 / 100);
        int qW = Math.max(72, available * 18 / 100);
        int scoreW = Math.max(72, right - left - moveW - visitW - priorW - qW - 44);
        int totalVisits = Math.max(1, snapshot.rootVisits());
        g.setFont(Theme.font(10, Font.BOLD));
        g.setColor(Theme.MUTED);
        g.drawString("move", left, top - 8);
        g.drawString("visits", left + moveW, top - 8);
        g.drawString("prior", left + moveW + visitW + 10, top - 8);
        g.drawString("Q", left + moveW + visitW + priorW + 20, top - 8);
        g.drawString("PUCT", left + moveW + visitW + priorW + qW + 30, top - 8);

        int availableHeight = Math.max(ROW_HEIGHT, bounds.y + bounds.height - 16 - top);
        int count = Math.min(Math.min(MAX_ROWS, rows.size()),
                Math.max(1, (availableHeight + ROW_GAP) / (ROW_HEIGHT + ROW_GAP)));
        for (int i = 0; i < count; i++) {
            MctsSearch.Row row = rows.get(i);
            int y = top + i * (ROW_HEIGHT + ROW_GAP);
            drawMoveLabel(g, row, left, y, moveW, ROW_HEIGHT);
            drawUnsignedBar(g, left + moveW, y, visitW, ROW_HEIGHT,
                    row.visits() / (double) totalVisits,
                    TensorViz.POSITIVE,
                    String.format("%d", row.visits()));
            drawUnsignedBar(g, left + moveW + visitW + 10, y, priorW, ROW_HEIGHT,
                    row.prior(), Theme.ACCENT,
                    String.format("%.1f%%", row.prior() * 100.0));
            drawSignedBar(g, left + moveW + visitW + priorW + 20, y, qW, ROW_HEIGHT,
                    row.q(), String.format("%+.2f", row.q()));
            drawSignedBar(g, left + moveW + visitW + priorW + qW + 30, y, scoreW, ROW_HEIGHT,
                    row.score(), String.format("%+.2f", row.score()));
        }
    }

    /**
     * Draws one key-value metric chip in the summary row.
     *
     * @param g graphics context
     * @param x left coordinate
     * @param y top coordinate
     * @param width chip width
     * @param label metric label
     * @param value metric value
     * @param accent metric accent
     */
    private static void drawMetricChip(Graphics2D g, int x, int y, int width,
            String label, String value, Color accent) {
        TensorViz.drawInfoChip(g, new Rectangle(x, y, width, SUMMARY_CHIP_HEIGHT),
                label, value, accent);
    }

    /**
     * Returns the right edge available to the bar chart, leaving space for the
     * live board on wide layouts.
     *
     * @param bounds drawing bounds
     * @return right edge for content
     */
    private static int contentRight(Rectangle bounds, boolean leafBoardVisible) {
        if (!leafBoardVisible || bounds.width < 520) {
            return bounds.x + bounds.width - 16;
        }
        return bounds.x + bounds.width - 236;
    }

    /**
     * Draws one move label.
     *
     * @param g graphics context
     * @param row MCTS row
     * @param x left coordinate
     * @param y top coordinate
     * @param w width
     * @param h height
     */
    private void drawMoveLabel(Graphics2D g, MctsSearch.Row row,
            int x, int y, int w, int h) {
        g.setFont(Theme.font(10, Font.BOLD));
        g.setColor(Theme.TEXT);
        NotationPainter.draw(g, row.san(), x, y + h - 2, w - 2, Theme.TEXT);
    }

    /**
     * Draws an unsigned horizontal bar.
     *
     * @param g graphics context
     * @param x left coordinate
     * @param y top coordinate
     * @param w width
     * @param h height
     * @param value normalized value
     * @param fill fill color
     * @param label bar label
     */
    private void drawUnsignedBar(Graphics2D g, int x, int y, int w, int h,
            double value, Color fill, String label) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        g.setColor(Theme.ELEVATED_SOLID);
        g.fillRect(x, y, w, h);
        g.setColor(fill);
        g.fillRect(x, y, Math.max(1, (int) Math.round(w * clamped)), h);
        drawTinyLabel(g, label, x + 3, y + h - 2);
    }

    /**
     * Draws a signed centered bar.
     *
     * @param g graphics context
     * @param x left coordinate
     * @param y top coordinate
     * @param w width
     * @param h height
     * @param value signed value
     * @param label bar label
     */
    private void drawSignedBar(Graphics2D g, int x, int y, int w, int h,
            double value, String label) {
        double clamped = Math.max(-1.0, Math.min(1.0, value));
        int mid = x + w / 2;
        g.setColor(Theme.ELEVATED_SOLID);
        g.fillRect(x, y, w, h);
        g.setColor(clamped >= 0.0 ? TensorViz.POSITIVE : TensorViz.NEGATIVE);
        int len = Math.max(1, (int) Math.round((w / 2.0) * Math.abs(clamped)));
        if (clamped >= 0.0) {
            g.fillRect(mid, y, len, h);
        } else {
            g.fillRect(mid - len, y, len, h);
        }
        g.setColor(Theme.LINE);
        g.drawLine(mid, y, mid, y + h);
        drawTinyLabel(g, label, x + 3, y + h - 2);
    }

    /**
     * Draws small text on a bar.
     *
     * @param g graphics context
     * @param label text label
     * @param x left coordinate
     * @param baseline text baseline
     */
    private void drawTinyLabel(Graphics2D g, String label, int x, int baseline) {
        g.setFont(Theme.font(9, Font.BOLD));
        g.setColor(Theme.TEXT);
        g.drawString(label, x, baseline);
    }
}
