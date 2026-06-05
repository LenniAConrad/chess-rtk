package application.gui.workbench.network;

import application.gui.workbench.board.BoardStyle;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import chess.classical.Wdl;
import chess.core.Position;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JComponent;

/**
 * Workbench Evaluator view for the classical hand-crafted evaluator.
 *
 * <p>Unlike the neural views this consumes no activation snapshot: it reads the
 * current FEN and renders {@link Wdl}'s per-term centipawn breakdown (material,
 * piece-square tables, king safety, pawn structure, mobility, threats, space,
 * tempo, check) as signed bars whose sum equals the engine's real score, plus
 * the WDL projection, a game-phase indicator, and per-piece piece-square-table
 * heatmaps in the detailed modes.</p>
 */
public final class ClassicalView extends JComponent {

    /**
     * Serialization identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Outer padding.
     */
    private static final int PAD = 14;

    /**
     * Labels and accessors for the eleven evaluation terms.
     */
    private static final String[] TERM_LABELS = {
        "material", "piece-square", "bishop pair", "pawn structure", "rook files",
        "king safety", "activity", "threats", "space", "tempo", "check"
    };

    /**
     * Piece-type codes and labels for the PST heatmaps.
     */
    private static final int[] PST_TYPES = { 1, 2, 3, 4, 5, 6 };

    /**
     * PST heatmap labels aligned with {@link #PST_TYPES}.
     */
    private static final String[] PST_LABELS = { "Pawn", "Knight", "Bishop", "Rook", "Queen", "King" };

    /**
     * Current FEN.
     */
    private String fen;

    /**
     * Active view mode.
     */
    private ViewMode mode = ViewMode.ABSTRACT;

    /**
     * Cached FEN whose breakdown is held.
     */
    private transient String cachedFen;

    /**
     * Cached breakdown for {@link #cachedFen}.
     */
    private transient Wdl.Breakdown cachedBreakdown;

    /**
     * Cached WDL triplet for {@link #cachedFen}.
     */
    private transient Wdl cachedWdl;

    /**
     * Creates the view.
     */
    public ClassicalView() {
        setOpaque(true);
        setBackground(Theme.BG);
        setPreferredSize(new Dimension(780, 720));
    }

    /**
     * Sets the current FEN and repaints.
     *
     * @param value position FEN
     */
    public void setFen(String value) {
        fen = value;
        repaint();
    }

    /**
     * Sets the active rendering mode.
     *
     * @param value view mode (ignored when null)
     */
    public void setViewMode(ViewMode value) {
        if (value != null && value != mode) {
            mode = value;
            repaint();
        }
    }

    /**
     * Ensures the cached breakdown matches the current FEN.
     */
    private void ensureBreakdown() {
        if (fen == null || fen.isBlank()) {
            cachedFen = null;
            cachedBreakdown = null;
            cachedWdl = null;
            return;
        }
        if (fen.equals(cachedFen) && cachedBreakdown != null) {
            return;
        }
        try {
            Position position = new Position(fen);
            cachedBreakdown = Wdl.evaluateWhiteBreakdown(position);
            cachedWdl = Wdl.evaluate(position, false);
            cachedFen = fen;
        } catch (RuntimeException ex) {
            cachedFen = null;
            cachedBreakdown = null;
            cachedWdl = null;
        }
    }

    /**
     * Paints the classical evaluation view.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Theme.BG);
            g.fillRect(0, 0, getWidth(), getHeight());
            ensureBreakdown();
            Rectangle bounds = new Rectangle(0, 0, getWidth(), getHeight());
            if (cachedBreakdown == null) {
                Ui.paintEmptyState(g, bounds, "Classical evaluator",
                        "Load a position or run PUCT to see the handcrafted term breakdown.");
                return;
            }
            int boardSide = Math.min(220, Math.max(150, getHeight() - 2 * PAD - 430));
            Rectangle board = new Rectangle(getWidth() - boardSide - PAD, PAD, boardSide, boardSide);
            drawHeader(g, bounds);
            drawBoard(g, board);
            int termsTop = 108;
            int termsRight = board.x - PAD;
            int termsBottom = drawTerms(g,
                    new Rectangle(PAD, termsTop, termsRight - PAD, getHeight() - termsTop - PAD));
            if (mode != ViewMode.ABSTRACT) {
                int heatTop = Math.max(board.y + board.height + 20, termsBottom + 20);
                drawPstHeatmaps(g, new Rectangle(PAD, heatTop, getWidth() - 2 * PAD,
                        getHeight() - heatTop - PAD));
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Draws the score and WDL header.
     *
     * @param g graphics
     * @param bounds full bounds
     */
    private void drawHeader(Graphics2D g, Rectangle bounds) {
        int stm = cachedBreakdown.stmTotal();
        g.setFont(Theme.font(16, Font.BOLD));
        g.setColor(Theme.TEXT);
        g.drawString("Classical evaluator", PAD, 28);
        g.setFont(Theme.font(12, Font.PLAIN));
        g.setColor(Theme.MUTED);
        String side = cachedBreakdown.whiteToMove() ? "white" : "black";
        g.drawString(String.format("handcrafted centipawns · %s to move · %+d cp (side to move)", side, stm),
                PAD, 48);

        // Phase indicator.
        int phaseW = Math.min(220, bounds.width - 2 * PAD);
        Rectangle phase = new Rectangle(PAD, 58, phaseW, 14);
        g.setColor(Theme.ELEVATED_SOLID);
        g.fillRoundRect(phase.x, phase.y, phase.width, phase.height, 7, 7);
        int fill = (int) Math.round(phase.width * (1.0 - cachedBreakdown.phase()));
        g.setColor(Theme.withAlpha(Theme.ACCENT, 200));
        g.fillRoundRect(phase.x, phase.y, Math.max(2, fill), phase.height, 7, 7);
        g.setFont(Theme.font(9, Font.BOLD));
        g.setColor(Theme.TEXT);
        g.drawString("opening", phase.x + 4, phase.y + 11);
        String eg = "endgame";
        g.drawString(eg, phase.x + phase.width - g.getFontMetrics().stringWidth(eg) - 4, phase.y + 11);

        // WDL bar.
        if (cachedWdl != null) {
            Rectangle wdl = new Rectangle(PAD, 78, phaseW, 12);
            double total = Math.max(1.0, cachedWdl.win() + cachedWdl.draw() + cachedWdl.loss());
            int wWin = (int) Math.round(wdl.width * cachedWdl.win() / total);
            int wDraw = (int) Math.round(wdl.width * cachedWdl.draw() / total);
            g.setColor(TensorViz.POSITIVE);
            g.fillRect(wdl.x, wdl.y, wWin, wdl.height);
            g.setColor(Theme.withAlpha(Theme.MUTED, 160));
            g.fillRect(wdl.x + wWin, wdl.y, wDraw, wdl.height);
            g.setColor(TensorViz.NEGATIVE);
            g.fillRect(wdl.x + wWin + wDraw, wdl.y, wdl.width - wWin - wDraw, wdl.height);
        }
    }

    /**
     * Draws the position board.
     *
     * @param g graphics
     * @param board board rectangle
     */
    private void drawBoard(Graphics2D g, Rectangle board) {
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(10, Font.BOLD));
        g.drawString("position", board.x, board.y - 6);
        BoardStyle.drawBoardSurface(g, board, true);
        boolean whiteDown = TensorViz.whiteDownForSideToMove(fen);
        TensorViz.drawPositionPieces(g, board, fen, whiteDown);
        TensorViz.drawBoardCoordinates(g, board, whiteDown);
    }

    /**
     * Draws the signed per-term bar chart.
     *
     * @param g graphics
     * @param area chart area
     * @return the y coordinate just below the total row
     */
    private int drawTerms(Graphics2D g, Rectangle area) {
        int[] terms = {
            cachedBreakdown.material(), cachedBreakdown.pieceSquare(), cachedBreakdown.bishopPair(),
            cachedBreakdown.pawnStructure(), cachedBreakdown.rookFile(), cachedBreakdown.kingSafety(),
            cachedBreakdown.activity(), cachedBreakdown.threats(), cachedBreakdown.space(),
            cachedBreakdown.tempo(), cachedBreakdown.checkPenalty()
        };
        int maxAbs = 1;
        for (int term : terms) {
            maxAbs = Math.max(maxAbs, Math.abs(term));
        }
        maxAbs = Math.max(maxAbs, Math.abs(cachedBreakdown.whiteTotal()));
        g.setFont(Theme.font(10, Font.BOLD));
        g.setColor(Theme.MUTED);
        g.drawString("term contributions (White − Black, centipawns)", area.x, area.y - 6);

        int labelW = 104;
        int rowH = 18;
        int gap = 6;
        int barLeft = area.x + labelW;
        int barW = Math.max(80, area.width - labelW);
        int mid = barLeft + barW / 2;
        for (int i = 0; i < terms.length; i++) {
            int y = area.y + i * (rowH + gap);
            if (y + rowH > area.y + area.height) {
                break;
            }
            g.setFont(Theme.font(10, Font.PLAIN));
            g.setColor(Theme.TEXT);
            g.drawString(TERM_LABELS[i], area.x, y + rowH - 5);
            drawSignedBar(g, barLeft, y, barW, rowH, terms[i] / (double) maxAbs,
                    String.format("%+d", terms[i]), mid);
        }
        // Total row.
        int totalY = area.y + terms.length * (rowH + gap) + 4;
        if (totalY + rowH <= area.y + area.height) {
            g.setColor(Theme.LINE);
            g.drawLine(area.x, totalY - 4, barLeft + barW, totalY - 4);
            g.setFont(Theme.font(11, Font.BOLD));
            g.setColor(Theme.TEXT);
            g.drawString("total", area.x, totalY + rowH - 5);
            drawSignedBar(g, barLeft, totalY, barW, rowH,
                    cachedBreakdown.whiteTotal() / (double) maxAbs,
                    String.format("%+d", cachedBreakdown.whiteTotal()), mid);
        }
        return totalY + rowH;
    }

    /**
     * Draws a signed centered bar with a label.
     *
     * @param g graphics
     * @param x left
     * @param y top
     * @param w width
     * @param h height
     * @param value signed normalized value
     * @param label label
     * @param mid bar midpoint x
     */
    private void drawSignedBar(Graphics2D g, int x, int y, int w, int h,
            double value, String label, int mid) {
        double clamped = Math.max(-1.0, Math.min(1.0, value));
        g.setColor(Theme.ELEVATED_SOLID);
        g.fillRect(x, y, w, h);
        g.setColor(clamped >= 0 ? TensorViz.POSITIVE : TensorViz.NEGATIVE);
        int len = Math.max(1, (int) Math.round((w / 2.0) * Math.abs(clamped)));
        if (clamped >= 0) {
            g.fillRect(mid, y, len, h);
        } else {
            g.fillRect(mid - len, y, len, h);
        }
        g.setColor(Theme.LINE);
        g.drawLine(mid, y, mid, y + h);
        g.setFont(Theme.font(9, Font.BOLD));
        g.setColor(Theme.TEXT);
        FontMetrics fm = g.getFontMetrics();
        int lx = clamped >= 0 ? mid + 4 : mid - 4 - fm.stringWidth(label);
        g.drawString(label, lx, y + h - 4);
    }

    /**
     * Draws the per-piece piece-square-table heatmaps.
     *
     * @param g graphics
     * @param area heatmap area
     */
    private void drawPstHeatmaps(Graphics2D g, Rectangle area) {
        if (area.height < 80) {
            return;
        }
        g.setFont(Theme.font(10, Font.BOLD));
        g.setColor(Theme.MUTED);
        g.drawString("piece-square tables (White perspective)", area.x, area.y - 6);
        int count = PST_TYPES.length;
        int gap = 16;
        int cell = Math.min((area.width - (count - 1) * gap) / count, area.height - 22) / 8;
        cell = Math.max(6, cell);
        int boardPx = cell * 8;
        int x = area.x;
        for (int t = 0; t < count; t++) {
            int[] table = Wdl.pieceSquareTable(PST_TYPES[t]);
            int maxAbs = 1;
            for (int v : table) {
                maxAbs = Math.max(maxAbs, Math.abs(v));
            }
            g.setFont(Theme.font(9, Font.BOLD));
            g.setColor(Theme.TEXT);
            g.drawString(PST_LABELS[t], x, area.y + 10);
            int top = area.y + 16;
            for (int i = 0; i < 64; i++) {
                int col = i % 8;
                int row = i / 8;
                double norm = table[i] / (double) maxAbs;
                Color base = norm >= 0 ? TensorViz.POSITIVE : TensorViz.NEGATIVE;
                g.setColor(Theme.withAlpha(base, (int) Math.round(40 + 200 * Math.min(1.0, Math.abs(norm)))));
                g.fillRect(x + col * cell, top + row * cell, cell, cell);
            }
            g.setColor(Theme.withAlpha(Theme.LINE, 160));
            g.drawRect(x, top, boardPx, boardPx);
            x += boardPx + gap;
        }
    }
}
