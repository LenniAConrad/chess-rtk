package application.gui.workbench.mcts;

import application.gui.workbench.board.BoardStyle;
import application.gui.workbench.network.TensorViz;
import application.gui.workbench.ui.NotationPainter;
import application.gui.workbench.ui.ScrollableSupport;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import chess.core.Move;
import chess.core.Position;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.Scrollable;
import javax.swing.ToolTipManager;

/**
 * Live renderer for MCTS edge weights in the Evaluator tab.
 *
 * <p>The neural-network weights are static during inference; MCTS updates the
 * tree edge weights. This panel renders those live search weights while the
 * evaluator views follow the currently evaluated leaf. It mirrors the full
 * search-table information (move, UCI, visits, share, policy prior, Q, U and
 * PUCT score plus the principal variation) in the compact bar-chart style,
 * scrolls to show every root edge, and exposes a per-row board preview.</p>
 */
public final class MctsWeightsPanel extends JComponent implements Scrollable {

    /**
     * Serialization identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Preferred height of the header block when the leaf board is shown.
     */
    private static final int HEIGHT = 286;

    /**
     * Preferred height of the header block when the host view supplies the board.
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
    private static final int ROW_HEIGHT = 16;

    /**
     * Gap between root edge rows.
     */
    private static final int ROW_GAP = 7;

    /**
     * Combined per-row vertical stride.
     */
    private static final int ROW_STRIDE = ROW_HEIGHT + ROW_GAP;

    /**
     * Bottom padding below the last row.
     */
    private static final int BOTTOM_PAD = 16;

    /**
     * Width of the clickable per-row board-preview glyph.
     */
    private static final int BOARD_BUTTON = 16;

    /**
     * Panel width below which the layout drops the side board and uses the full
     * width for the bar chart.
     */
    private static final int COMPACT_LAYOUT_THRESHOLD = 520;

    /**
     * Latest MCTS snapshot.
     */
    private MctsSearch.Snapshot snapshot;

    /**
     * Root FEN backing the per-row board previews.
     */
    private String rootFen;

    /**
     * Whether to reserve space for the currently explored leaf board.
     */
    private boolean leafBoardVisible = true;

    /**
     * Row index under the pointer, or {@code -1}.
     */
    private int hoverRow = -1;

    /**
     * Hit rectangles for the per-row board-preview glyphs (paint-order).
     */
    private final transient List<Rectangle> boardButtons = new ArrayList<>();

    /**
     * Full-width row rectangles for hover and tooltip hit-testing.
     */
    private final transient List<Rectangle> rowBounds = new ArrayList<>();

    /**
     * Creates the panel.
     */
    public MctsWeightsPanel() {
        setOpaque(false);
        setVisible(false);
        setPreferredSize(new Dimension(720, COMPACT_HEIGHT));
        MouseAdapter mouse = new MouseAdapter() {
            /**
             * Updates row hover state while the pointer moves.
             *
             * @param event mouse move event
             */
            @Override
            public void mouseMoved(MouseEvent event) {
                updateHover(event.getPoint().y);
            }

            /**
             * Clears row hover state when the pointer leaves.
             *
             * @param event mouse exit event
             */
            @Override
            public void mouseExited(MouseEvent event) {
                updateHover(-1);
            }

            /**
             * Handles clicks on weight rows and leaf-board controls.
             *
             * @param event mouse click event
             */
            @Override
            public void mouseClicked(MouseEvent event) {
                onClick(event);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    /**
     * Shows a new MCTS snapshot.
     *
     * @param next snapshot
     */
    public void setSnapshot(MctsSearch.Snapshot next) {
        snapshot = next;
        rootFen = next == null ? null : next.rootFen();
        setVisible(next != null);
        revalidate();
        repaint();
    }

    /**
     * Clears the panel.
     */
    public void clear() {
        snapshot = null;
        rootFen = null;
        hoverRow = -1;
        setVisible(false);
        revalidate();
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
     * Returns the row count in the current snapshot.
     *
     * @return root edge count
     */
    private int rowCount() {
        return snapshot == null ? 0 : snapshot.rows().size();
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
     * Returns the height appropriate for the current content and board mode.
     *
     * @return preferred height
     */
    private int preferredHeight() {
        int base = leafBoardVisible ? HEIGHT : COMPACT_HEIGHT;
        if (snapshot == null) {
            return base;
        }
        int rowsTop = headerHeight() + ROW_HEIGHT;
        int rowsBlock = Math.max(ROW_STRIDE, rowCount() * ROW_STRIDE);
        return Math.max(base, rowsTop + rowsBlock + BOTTOM_PAD);
    }

    /**
     * Returns the pixel height of the fixed summary header block.
     *
     * @return header height
     */
    private int headerHeight() {
        boolean twoChipRows = getWidth() > 0 && getWidth() < 620;
        int chipRows = twoChipRows ? 2 : 1;
        return SUMMARY_TOP + chipRows * SUMMARY_CHIP_HEIGHT
                + (chipRows - 1) * SUMMARY_GAP + SUMMARY_TABLE_GAP;
    }

    // --- Scrollable -------------------------------------------------------

    /**
     * Returns the preferred viewport size for scrolling.
     *
     * @return preferred scrollable viewport size
     */
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(720, leafBoardVisible ? HEIGHT : COMPACT_HEIGHT);
    }

    /**
     * Returns the row-sized unit scroll increment.
     *
     * @param visibleRect visible viewport rectangle
     * @param orientation scroll orientation
     * @param direction scroll direction
     * @return unit scroll increment
     */
    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return ROW_STRIDE;
    }

    /**
     * Returns the block scroll increment.
     *
     * @param visibleRect visible viewport rectangle
     * @param orientation scroll orientation
     * @param direction scroll direction
     * @return block scroll increment
     */
    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return ScrollableSupport.verticalBlockIncrement(visibleRect, ROW_STRIDE);
    }

    /**
     * Returns whether the panel tracks viewport width.
     *
     * @return true because rows should fill the viewport width
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    /**
     * Returns whether the panel tracks viewport height.
     *
     * @return false so vertical scrolling can expose all rows
     */
    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    // --- Painting ---------------------------------------------------------

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
                    "every root edge: visits, share, policy prior, Q, U and PUCT score",
                    Theme.ACCENT);
            boardButtons.clear();
            rowBounds.clear();
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
        Ui.paintEmptyState(g, bounds,
                "No search running", "Start MCTS to stream root edge weights here.");
    }

    /**
     * Draws the top summary row.
     *
     * @param g graphics context
     * @param bounds drawing bounds
     * @return top coordinate for the edge rows
     */
    private int drawHeader(Graphics2D g, Rectangle bounds) {
        String best = snapshot.bestMove() == Move.NO_MOVE
                ? "-"
                : Move.toString(snapshot.bestMove());
        String leaf = snapshot.exploringLineText();
        if (leaf == null || leaf.isBlank()) {
            leaf = "root";
        }
        Font font = Theme.font(10, Font.BOLD);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int left = bounds.x + 14;
        int top = bounds.y + SUMMARY_TOP;
        int right = contentRight(bounds);
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
        if (snapshot.exploringPosition() == null || bounds.width < COMPACT_LAYOUT_THRESHOLD) {
            return;
        }
        int side = Math.min(204, COMPACT_HEIGHT - 54);
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
     * Column layout for one root-edge row.
     *
     * @param boardX board-glyph x
     * @param moveX move-label x
     * @param visitsX visits-bar x
     * @param visitsW visits-bar width
     * @param priorX prior-bar x
     * @param colW shared bar width for prior/Q/U/PUCT
     * @param qX Q-bar x
     * @param uX U-bar x (or {@code -1} when hidden)
     * @param puctX PUCT-bar x
     * @param pvX PV-text x (or {@code -1} when hidden)
     * @param pvW PV-text width
     */
    private record Columns(int boardX, int moveX, int visitsX, int visitsW,
            int priorX, int colW, int qX, int uX, int puctX, int pvX, int pvW) {
    }

    /**
     * Computes responsive column positions for the current width.
     *
     * @param bounds drawing bounds
     * @return column layout
     */
    private Columns columns(Rectangle bounds) {
        int left = bounds.x + 14;
        int right = contentRight(bounds);
        int boardX = left;
        int moveX = boardX + BOARD_BUTTON + 6;
        int moveW = 50;
        int visitsX = moveX + moveW + 6;
        int total = Math.max(180, right - visitsX);
        boolean showPv = total >= 470;
        boolean showU = total >= 300;
        boolean showPuct = total >= 210;
        int bars = 1 /* prior */ + 1 /* q */ + (showU ? 1 : 0) + (showPuct ? 1 : 0);
        int gaps = bars; // one gap after visits plus between bars
        int pvReserve = showPv ? Math.max(80, total * 30 / 100) : 0;
        int barsWidth = Math.max(120, total - pvReserve - gaps * 8);
        int visitsW = Math.max(96, barsWidth * 34 / 100);
        int colW = Math.max(52, (barsWidth - visitsW) / Math.max(1, bars));
        int priorX = visitsX + visitsW + 8;
        int qX = priorX + colW + 8;
        int uX = showU ? qX + colW + 8 : -1;
        int puctX = showPuct ? (showU ? uX + colW + 8 : qX + colW + 8) : -1;
        int afterBars = showPuct ? puctX + colW : (showU ? uX + colW : qX + colW);
        int pvX = showPv ? afterBars + 10 : -1;
        int pvW = showPv ? Math.max(40, right - pvX) : 0;
        return new Columns(boardX, moveX, visitsX, visitsW, priorX, colW, qX, uX, puctX, pvX, pvW);
    }

    /**
     * Draws all root edge rows with the full search-table information.
     *
     * @param g graphics context
     * @param bounds drawing bounds
     * @param top top coordinate for the column header
     */
    private void drawRows(Graphics2D g, Rectangle bounds, int top) {
        List<MctsSearch.Row> rows = snapshot.rows();
        if (rows.isEmpty()) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(12, Font.PLAIN));
            g.drawString("No expanded root edges yet.", bounds.x + 14, top + 10);
            return;
        }
        Columns c = columns(bounds);
        int totalVisits = Math.max(1, snapshot.rootVisits());

        g.setFont(Theme.font(10, Font.BOLD));
        g.setColor(Theme.MUTED);
        g.drawString("move", c.moveX(), top - 8);
        g.drawString("visits · share", c.visitsX(), top - 8);
        g.drawString("prior", c.priorX(), top - 8);
        g.drawString("Q", c.qX(), top - 8);
        if (c.uX() >= 0) {
            g.drawString("U", c.uX(), top - 8);
        }
        if (c.puctX() >= 0) {
            g.drawString("PUCT", c.puctX(), top - 8);
        }
        if (c.pvX() >= 0) {
            g.drawString("pv", c.pvX(), top - 8);
        }

        int right = contentRight(bounds);
        for (int i = 0; i < rows.size(); i++) {
            MctsSearch.Row row = rows.get(i);
            int y = top + i * ROW_STRIDE;
            Rectangle rowRect = new Rectangle(bounds.x + 8, y - 2, right - bounds.x - 2, ROW_HEIGHT + 4);
            rowBounds.add(rowRect);
            if (i == hoverRow) {
                g.setColor(Theme.withAlpha(Theme.ACCENT, 28));
                g.fillRoundRect(rowRect.x, rowRect.y, rowRect.width, rowRect.height, 6, 6);
            }
            drawBoardButton(g, c.boardX(), y, i == hoverRow);
            drawMoveLabel(g, row, c.moveX(), y, 50, ROW_HEIGHT);
            double share = row.visits() / (double) totalVisits;
            drawUnsignedBar(g, c.visitsX(), y, c.visitsW(), ROW_HEIGHT,
                    share, TensorViz.POSITIVE,
                    String.format("%,d · %.0f%%", row.visits(), share * 100.0));
            drawUnsignedBar(g, c.priorX(), y, c.colW(), ROW_HEIGHT,
                    row.prior(), Theme.ACCENT,
                    String.format("%.1f%%", row.prior() * 100.0));
            drawSignedBar(g, c.qX(), y, c.colW(), ROW_HEIGHT, row.q(), String.format("%+.2f", row.q()));
            if (c.uX() >= 0) {
                drawUnsignedBar(g, c.uX(), y, c.colW(), ROW_HEIGHT,
                        Math.min(1.0, row.u()), TensorViz.POLICY, String.format("%.2f", row.u()));
            }
            if (c.puctX() >= 0) {
                drawSignedBar(g, c.puctX(), y, c.colW(), ROW_HEIGHT,
                        row.score(), String.format("%+.2f", row.score()));
            }
            if (c.pvX() >= 0) {
                g.setFont(Theme.font(10, Font.PLAIN));
                FontMetrics fm = g.getFontMetrics();
                g.setColor(Theme.MUTED);
                g.drawString(Ui.elide(row.pvText() == null ? "" : row.pvText(), fm, c.pvW()),
                        c.pvX(), y + ROW_HEIGHT - 3);
            }
        }
    }

    /**
     * Draws the clickable per-row board-preview glyph and records its hit box.
     *
     * @param g graphics context
     * @param x left coordinate
     * @param y top coordinate
     * @param hovered true when the row is hovered
     */
    private void drawBoardButton(Graphics2D g, int x, int y, boolean hovered) {
        Rectangle box = new Rectangle(x, y + (ROW_HEIGHT - BOARD_BUTTON) / 2, BOARD_BUTTON, BOARD_BUTTON);
        boardButtons.add(box);
        // A tiny 4x4 checker reads as "board"; brighten on hover so the
        // click affordance is obvious.
        Color light = hovered ? Theme.withAlpha(Theme.ACCENT, 235) : Theme.withAlpha(Theme.TEXT, 150);
        Color dark = hovered ? Theme.withAlpha(Theme.ACCENT, 150) : Theme.withAlpha(Theme.TEXT, 70);
        int cell = BOARD_BUTTON / 4;
        for (int r = 0; r < 4; r++) {
            for (int cc = 0; cc < 4; cc++) {
                g.setColor((r + cc) % 2 == 0 ? light : dark);
                g.fillRect(box.x + cc * cell, box.y + r * cell, cell, cell);
            }
        }
        g.setColor(Theme.withAlpha(Theme.LINE, 200));
        g.drawRect(box.x, box.y, BOARD_BUTTON - 1, BOARD_BUTTON - 1);
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
    private int contentRight(Rectangle bounds) {
        if (!leafBoardVisible || bounds.width < COMPACT_LAYOUT_THRESHOLD) {
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
        drawTinyLabel(g, label, x + 3, y + h - 3);
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
        drawTinyLabel(g, label, x + 3, y + h - 3);
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

    // --- Interaction ------------------------------------------------------

    /**
     * Updates the hovered row from a pointer y-coordinate.
     *
     * @param y pointer y, or {@code -1} when outside
     */
    private void updateHover(int y) {
        int next = y < 0 ? -1 : rowAt(y);
        boolean overButton = next >= 0 && next < boardButtons.size();
        setCursor(Cursor.getPredefinedCursor(overButton ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        if (next != hoverRow) {
            hoverRow = next;
            repaint();
        }
    }

    /**
     * Returns the row index containing a y-coordinate, or {@code -1}.
     *
     * @param y pointer y
     * @return row index or {@code -1}
     */
    private int rowAt(int y) {
        for (int i = 0; i < rowBounds.size(); i++) {
            if (rowBounds.get(i).contains(rowBounds.get(i).x + 1, y)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Opens the board preview for a clicked row.
     *
     * @param event mouse event
     */
    private void onClick(MouseEvent event) {
        if (snapshot == null) {
            return;
        }
        List<MctsSearch.Row> rows = snapshot.rows();
        for (int i = 0; i < boardButtons.size() && i < rows.size(); i++) {
            if (boardButtons.get(i).contains(event.getPoint())) {
                showBoardPreview(rows.get(i), event.getX(), event.getY());
                return;
            }
        }
        int row = rowAt(event.getY());
        if (row >= 0 && row < rows.size()) {
            showBoardPreview(rows.get(row), event.getX(), event.getY());
        }
    }

    /**
     * Shows the position reached after a root edge in a board-preview popup.
     *
     * @param row root edge
     * @param x anchor x
     * @param y anchor y
     */
    private void showBoardPreview(MctsSearch.Row row, int x, int y) {
        String fen = childFen(row);
        if (fen == null) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        Ui.stylePopupMenu(menu);
        menu.add(new BoardPreview(fen, row));
        menu.show(this, x, y);
    }

    /**
     * Returns the FEN reached after playing a root edge from the root FEN.
     *
     * @param row root edge
     * @return resulting FEN, or null when it cannot be played
     */
    private String childFen(MctsSearch.Row row) {
        if (rootFen == null || rootFen.isBlank() || row.move() == Move.NO_MOVE) {
            return rootFen;
        }
        try {
            Position position = new Position(rootFen);
            position.play(row.move());
            return position.toString();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Returns a tooltip with the full UCI and PV for the hovered row.
     *
     * @param event mouse event
     * @return tooltip text, or null
     */
    @Override
    public String getToolTipText(MouseEvent event) {
        if (snapshot == null) {
            return null;
        }
        int row = rowAt(event.getY());
        List<MctsSearch.Row> rows = snapshot.rows();
        if (row < 0 || row >= rows.size()) {
            return null;
        }
        MctsSearch.Row r = rows.get(row);
        String pv = r.pvText() == null || r.pvText().isBlank() ? "-" : r.pvText();
        return "<html><b>" + escape(r.san()) + "</b>  " + escape(r.uci())
                + "<br>visits " + String.format("%,d", r.visits())
                + " · Q " + String.format("%+.3f", r.q())
                + " · U " + String.format("%.3f", r.u())
                + " · PUCT " + String.format("%+.3f", r.score())
                + "<br>pv: " + escape(pv) + "<br><i>click for board preview</i></html>";
    }

    /**
     * Escapes HTML metacharacters for tooltip text.
     *
     * @param text raw text
     * @return escaped text
     */
    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Compact board-preview popup content for a single root edge.
     */
    private static final class BoardPreview extends JComponent {
        /**
         * Serialization identifier.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Position FEN after the edge move.
         */
        private final String fen;

        /**
         * Source edge row.
         */
        private final transient MctsSearch.Row row;

        /**
         * Creates the popup content.
         *
         * @param fen resulting FEN
         * @param row source edge row
         */
        BoardPreview(String fen, MctsSearch.Row row) {
            this.fen = fen;
            this.row = row;
            setOpaque(true);
            setPreferredSize(new Dimension(248, 290));
        }

        /**
         * Paints the board preview.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.PANEL_SOLID);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Theme.TEXT);
                g.setFont(Theme.font(12, Font.BOLD));
                g.drawString(row.san() + "  ·  " + row.uci(), 14, 20);
                g.setColor(Theme.MUTED);
                g.setFont(Theme.font(10, Font.PLAIN));
                g.drawString(String.format("visits %,d   Q %+.2f   PUCT %+.2f",
                        row.visits(), row.q(), row.score()), 14, 36);
                int side = Math.min(getWidth() - 28, getHeight() - 56);
                Rectangle board = new Rectangle((getWidth() - side) / 2, 46, side, side);
                BoardStyle.drawBoardSurface(g, board, true);
                boolean whiteDown = TensorViz.whiteDownForSideToMove(fen);
                short move = row.move();
                if (move != Move.NO_MOVE) {
                    BoardStyle.drawInsetSquareHighlight(g,
                            BoardStyle.fieldSquareBounds(board, Move.getFromIndex(move), whiteDown),
                            Theme.withAlpha(Theme.ACCENT, 230));
                    BoardStyle.drawInsetSquareHighlight(g,
                            BoardStyle.fieldSquareBounds(board, Move.getToIndex(move), whiteDown),
                            Theme.withAlpha(TensorViz.POSITIVE, 230));
                }
                TensorViz.drawPositionPieces(g, board, fen, whiteDown);
                TensorViz.drawBoardCoordinates(g, board, whiteDown);
            } finally {
                g.dispose();
            }
        }
    }
}
