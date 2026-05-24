package application.gui.workbench.publish;

import application.gui.workbench.board.BoardStyle;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import chess.core.Piece;
import chess.images.assets.Shapes;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.Locale;
import javax.swing.JComponent;

/**
 * Lightweight PDF-style publishing preview for the workbench.
 */
public final class PublishPreview extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Page aspect ratio.
     */
    private static final double PAGE_RATIO = 0.707;

    /**
     * Maximum preview page count.
     */
    private static final int MAX_PAGES = 999;

    /**
     * Printed-paper preview fill.
     */
    private static final Color PAPER = new Color(0xFCFCFC);

    /**
     * Primary printed-paper text.
     */
    private static final Color PAPER_INK = new Color(0x242424);

    /**
     * Muted printed-paper text.
     */
    private static final Color PAPER_MUTED = new Color(0x666666);

    /**
     * Printed-paper divider line.
     */
    private static final Color PAPER_LINE = new Color(0xD8D8D8);

    /**
     * Printed-paper block fill.
     */
    private static final Color PAPER_BLOCK = new Color(0xF1F3F5);

    /**
     * Printed-paper primary accent.
     */
    private static final Color PAPER_ACCENT = new Color(0x005FB8);

    /**
     * Printed-paper positive accent.
     */
    private static final Color PAPER_GREEN = new Color(0x69D18E);

    /**
     * Printed-paper warning accent.
     */
    private static final Color PAPER_AMBER = new Color(0xFFD064);

    /**
     * Printed-paper policy/accent purple.
     */
    private static final Color PAPER_PURPLE = new Color(0xB986E8);

    /**
     * Fallback board placement used when no FEN is visible in the source text.
     */
    private static final String FALLBACK_FEN_PLACEMENT = "r2q1rk1/pp2bppp/2n1pn2/2bp4/3P4/2PBPN2/PP1NBPPP/R2Q1RK1";

    /**
     * Current preview model.
     */
    private Preview preview = Preview.empty();

    /**
     * Visible page number, one-based.
     */
    private int page = 1;

    /**
     * Creates the preview component.
     */
    public PublishPreview() {
        setOpaque(true);
        setBackground(Theme.ELEVATED_SOLID);
        setForeground(Theme.TEXT);
        setPreferredSize(new Dimension(320, 420));
        setMinimumSize(new Dimension(260, 320));
        setToolTipText("Publishing PDF preview");
    }

    /**
     * Updates the preview model.
     *
     * @param next next preview
     */
    public void setPreview(Preview next) {
        preview = next == null ? Preview.empty() : next.normalized();
        page = Math.max(1, Math.min(page, preview.pageCount()));
        repaint();
    }

    /**
     * Moves to the previous page.
     */
    public void previousPage() {
        if (page > 1) {
            page--;
            repaint();
        }
    }

    /**
     * Moves to the next page.
     */
    public void nextPage() {
        if (page < preview.pageCount()) {
            page++;
            repaint();
        }
    }

    /**
     * Returns current page label.
     *
     * @return page label
     */
    public String pageLabel() {
        return "page " + page + " / " + preview.pageCount();
    }

    /**
     * Returns current page number.
     *
     * @return page number
     */
    public int pageNumber() {
        return page;
    }

    /**
     * Paints the preview.
     *
     * @param graphics graphics
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            paintDocument(g);
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints the page and surrounding viewer chrome.
     *
     * @param g graphics
     */
    private void paintDocument(Graphics2D g) {
        Rectangle pageBounds = pageBounds();
        paintPaper(g, pageBounds);

        int pad = Math.max(14, pageBounds.width / 15);
        Rectangle content = new Rectangle(
                pageBounds.x + pad,
                pageBounds.y + pad,
                Math.max(1, pageBounds.width - pad * 2),
                Math.max(1, pageBounds.height - pad * 2));

        int footerHeight = Math.max(18, pageBounds.height / 22);
        Rectangle body = new Rectangle(content.x, content.y, content.width,
                Math.max(1, content.height - footerHeight));
        PreviewKind kind = preview.kind();
        switch (kind) {
            case COVER -> paintCoverSpread(g, body);
            case DIAGRAMS -> paintDiagramSheet(g, body);
            case RENDER -> paintRenderManifest(g, body);
            case COLLECTION -> paintCollectionPreview(g, body);
            case STUDY -> paintStudyPreview(g, body);
        }
        paintFooter(g, content.x, content.y + content.height - 2, content.width);
    }

    /**
     * Computes the visible page rectangle.
     *
     * @return page bounds
     */
    private Rectangle pageBounds() {
        int availableW = Math.max(1, getWidth() - 34);
        int availableH = Math.max(1, getHeight() - 34);
        int pageH = availableH;
        int pageW = (int) Math.round(pageH * PAGE_RATIO);
        if (pageW > availableW) {
            pageW = availableW;
            pageH = (int) Math.round(pageW / PAGE_RATIO);
        }
        return new Rectangle((getWidth() - pageW) / 2, (getHeight() - pageH) / 2, pageW, pageH);
    }

    /**
     * Paints the paper, border, and viewer shadow.
     *
     * @param g graphics
     * @param pageBounds page bounds
     */
    private static void paintPaper(Graphics2D g, Rectangle pageBounds) {
        g.setColor(Theme.withAlpha(Color.BLACK, Theme.isDark() ? 82 : 30));
        g.fillRoundRect(pageBounds.x + 8, pageBounds.y + 10, pageBounds.width, pageBounds.height, 6, 6);
        g.setColor(PAPER);
        g.fillRoundRect(pageBounds.x, pageBounds.y, pageBounds.width, pageBounds.height, 6, 6);
        g.setColor(PAPER_LINE);
        g.drawRoundRect(pageBounds.x, pageBounds.y,
                Math.max(0, pageBounds.width - 1),
                Math.max(0, pageBounds.height - 1), 6, 6);
    }

    /**
     * Paints the direct diagram PDF preview.
     *
     * @param g graphics
     * @param body body bounds
     */
    private void paintDiagramSheet(Graphics2D g, Rectangle body) {
        int y = paintDocumentHeader(g, body, "diagram sheet", preview.source());
        int gap = Math.max(9, body.width / 34);
        int columns = body.width < 190 ? 1 : 2;
        int captionHeight = preview.noFen() ? 6 : Math.max(14, body.height / 30);
        int availableH = Math.max(1, body.y + body.height - y - gap);
        int rows = Math.max(1, Math.min(3, availableH / Math.max(1, body.width / columns)));
        int board = Math.min((body.width - gap * (columns - 1)) / columns,
                Math.max(42, (availableH - gap * (rows - 1)) / rows - captionHeight));
        int totalW = columns * board + (columns - 1) * gap;
        int startX = body.x + Math.max(0, (body.width - totalW) / 2);
        int firstIndex = (page - 1) * columns * rows;
        for (int i = 0; i < columns * rows; i++) {
            int row = i / columns;
            int col = i % columns;
            int x = startX + col * (board + gap);
            int boardY = y + row * (board + captionHeight + gap);
            paintDiagramCard(g, x, boardY, board, firstIndex + i + 1);
        }
    }

    /**
     * Paints one diagram card.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param size board size
     * @param index one-based diagram index
     */
    private void paintDiagramCard(Graphics2D g, int x, int y, int size, int index) {
        paintMiniBoard(g, x, y, size, true);
        if (!preview.noFen()) {
            drawElided(g, "diagram " + index + "  " + compactBoardCaption(),
                    Theme.mono(Math.max(7, size / 18)), PAPER_MUTED,
                    x, y + size + Math.max(10, size / 10), size);
        }
    }

    /**
     * Paints the manifest render workflow preview.
     *
     * @param g graphics
     * @param body body bounds
     */
    private void paintRenderManifest(Graphics2D g, Rectangle body) {
        int y = paintDocumentHeader(g, body, "rendered manifest", preview.source());
        int board = Math.min(Math.max(54, body.width / 3), Math.max(54, body.height / 4));
        paintMiniBoard(g, body.x, y, board, false);
        paintTextBlock(g, body.x + board + 12, y + 3,
                Math.max(20, body.width - board - 12), board - 6, 5);

        int cardY = y + board + Math.max(16, body.height / 28);
        int cardH = Math.max(24, body.height / 12);
        for (int i = 0; i < 4 && cardY + cardH < body.y + body.height - 8; i++) {
            paintPuzzleRow(g, body.x, cardY + i * (cardH + 7), body.width, cardH, i + 1);
        }
    }

    /**
     * Paints the collection workflow preview.
     *
     * @param g graphics
     * @param body body bounds
     */
    private void paintCollectionPreview(Graphics2D g, Rectangle body) {
        int y = paintDocumentHeader(g, body, "collection package", preview.output());
        int gap = Math.max(8, body.width / 40);
        int cardW = Math.max(36, (body.width - gap * 2) / 3);
        paintMetricCard(g, body.x, y, cardW, "manifest", PAPER_ACCENT);
        paintMetricCard(g, body.x + cardW + gap, y, cardW, "interior", PAPER_GREEN);
        paintMetricCard(g, body.x + (cardW + gap) * 2, y, cardW, "cover", PAPER_PURPLE);

        int gridY = y + Math.max(42, body.height / 9);
        drawElided(g, "puzzle index", Theme.font(10, Font.BOLD), PAPER_INK, body.x, gridY, body.width);
        int tableY = gridY + 12;
        int rows = Math.max(4, Math.min(7, (body.y + body.height - tableY - 16) / 18));
        for (int i = 0; i < rows; i++) {
            int rowY = tableY + i * 18;
            g.setColor(i % 2 == 0 ? PAPER_BLOCK : PAPER);
            g.fillRect(body.x, rowY - 9, body.width, 15);
            paintTinyStatus(g, body.x + 3, rowY - 4, PAPER_ACCENT);
            paintTextLine(g, body.x + 15, rowY, body.width / 3, 2);
            paintTextLine(g, body.x + body.width / 2, rowY, body.width / 4, 2);
            paintTextLine(g, body.x + body.width * 3 / 4, rowY, body.width / 5, 2);
        }
    }

    /**
     * Paints the study workflow preview.
     *
     * @param g graphics
     * @param body body bounds
     */
    private void paintStudyPreview(Graphics2D g, Rectangle body) {
        int y = paintDocumentHeader(g, body, "study book", preview.subtitle());
        int gap = Math.max(10, body.width / 35);
        int board = Math.min(Math.max(58, body.width / 2 - gap), Math.max(58, body.height / 3));
        paintMiniBoard(g, body.x, y, board, true);
        int textX = body.x + board + gap;
        int textW = Math.max(24, body.width - board - gap);
        paintMoveLine(g, textX, y + 8, textW);
        paintTextBlock(g, textX, y + 32, textW, Math.max(28, board - 34), 6);

        int variationY = y + board + Math.max(16, body.height / 30);
        paintVariationBlock(g, body.x, variationY, body.width,
                Math.max(34, Math.min(body.height / 5, body.y + body.height - variationY - 48)));
        paintExerciseBlock(g, body.x, variationY + Math.max(54, body.height / 5), body.width,
                Math.max(34, body.y + body.height - variationY - Math.max(64, body.height / 5)));
    }

    /**
     * Paints the cover workflow preview.
     *
     * @param g graphics
     * @param body body bounds
     */
    private void paintCoverSpread(Graphics2D g, Rectangle body) {
        int spreadH = Math.max(1, body.height - 4);
        int spineW = Math.max(8, body.width / 12);
        int coverW = Math.max(1, (body.width - spineW) / 2);
        Rectangle back = new Rectangle(body.x, body.y, coverW, spreadH);
        Rectangle spine = new Rectangle(back.x + back.width, body.y, spineW, spreadH);
        Rectangle front = new Rectangle(spine.x + spine.width, body.y, coverW, spreadH);

        paintCoverPanel(g, back, PAPER_BLOCK, false);
        paintSpine(g, spine);
        paintCoverPanel(g, front, new Color(0xEAF3FC), true);
    }

    /**
     * Paints one cover panel.
     *
     * @param g graphics
     * @param bounds panel bounds
     * @param fill fill color
     * @param front true for front cover
     */
    private void paintCoverPanel(Graphics2D g, Rectangle bounds, Color fill, boolean front) {
        g.setColor(fill);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g.setColor(PAPER_LINE);
        g.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
        int pad = Math.max(8, bounds.width / 9);
        if (front) {
            g.setColor(PAPER_ACCENT);
            g.fillRect(bounds.x, bounds.y, Math.max(3, bounds.width / 24), bounds.height);
            drawElided(g, preview.title(), Theme.font(Math.max(12, bounds.width / 10), Font.BOLD),
                    PAPER_INK, bounds.x + pad, bounds.y + bounds.height / 5, bounds.width - pad * 2);
            if (!preview.subtitle().isBlank()) {
                drawElided(g, preview.subtitle(), Theme.font(Math.max(8, bounds.width / 17), Font.PLAIN),
                        PAPER_MUTED, bounds.x + pad, bounds.y + bounds.height / 5 + bounds.height / 12,
                        bounds.width - pad * 2);
            }
            int board = Math.min(bounds.width - pad * 2, bounds.height / 3);
            paintMiniBoard(g, bounds.x + (bounds.width - board) / 2,
                    bounds.y + bounds.height - board - pad, board, false);
        } else {
            paintTextBlock(g, bounds.x + pad, bounds.y + pad, bounds.width - pad * 2,
                    Math.max(32, bounds.height / 3), 7);
            drawElided(g, preview.output(), Theme.mono(Math.max(7, bounds.width / 18)), PAPER_MUTED,
                    bounds.x + pad, bounds.y + bounds.height - pad, bounds.width - pad * 2);
        }
    }

    /**
     * Paints the cover spine.
     *
     * @param g graphics
     * @param bounds spine bounds
     */
    private void paintSpine(Graphics2D g, Rectangle bounds) {
        g.setColor(PAPER_ACCENT);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g.setColor(Theme.withAlpha(Color.WHITE, 210));
        g.drawLine(bounds.x + bounds.width / 2, bounds.y + 8, bounds.x + bounds.width / 2,
                bounds.y + bounds.height - 8);
    }

    /**
     * Paints a common document header.
     *
     * @param g graphics
     * @param body body bounds
     * @param eyebrow small label
     * @param support supporting line
     * @return y coordinate below the header
     */
    private int paintDocumentHeader(Graphics2D g, Rectangle body, String eyebrow, String support) {
        drawElided(g, eyebrow.toUpperCase(Locale.ROOT), Theme.font(8, Font.BOLD),
                PAPER_ACCENT, body.x, body.y + 8, body.width);
        drawElided(g, preview.title(), Theme.font(Math.max(12, body.width / 18), Font.BOLD),
                PAPER_INK, body.x, body.y + Math.max(25, body.height / 18), body.width);
        if (!support.isBlank()) {
            drawElided(g, support, Theme.mono(Math.max(7, body.width / 34)), PAPER_MUTED,
                    body.x, body.y + Math.max(40, body.height / 10), body.width);
        }
        if (!preview.ready()) {
            int bannerY = body.y + Math.max(48, body.height / 8);
            paintBanner(g, body.x, bannerY, body.width, Math.max(18, body.height / 26), preview.issue());
            return bannerY + Math.max(26, body.height / 18);
        }
        int ruleY = body.y + Math.max(50, body.height / 8);
        drawRule(g, body.x, ruleY, body.width);
        return ruleY + Math.max(12, body.height / 35);
    }

    /**
     * Paints a warning banner on the paper preview.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     * @param issue issue text
     */
    private static void paintBanner(Graphics2D g, int x, int y, int w, int h, String issue) {
        g.setColor(new Color(0xFFF3CF));
        g.fillRoundRect(x, y, w, h, 5, 5);
        g.setColor(PAPER_AMBER);
        g.fillRoundRect(x, y, Math.max(3, w / 70), h, 5, 5);
        drawElided(g, issue == null || issue.isBlank() ? "Input required" : issue,
                Theme.font(Math.max(8, h / 2), Font.PLAIN), new Color(0x684E12),
                x + Math.max(8, h / 2), y + h / 2 + 4, w - Math.max(12, h));
    }

    /**
     * Paints footer status.
     *
     * @param g graphics
     * @param x x
     * @param y baseline y
     * @param w width
     */
    private void paintFooter(Graphics2D g, int x, int y, int w) {
        g.setFont(Theme.mono(9));
        g.setColor(preview.ready() ? new Color(0x197245) : new Color(0x8A6415));
        String status = preview.ready() ? "ready" : "needs input";
        g.drawString(status, x, y);
        String pages = pageLabel();
        g.setColor(PAPER_MUTED);
        g.drawString(pages, x + Math.max(0, w - g.getFontMetrics().stringWidth(pages)), y);
    }

    /**
     * Paints a tiny chessboard diagram.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param size square board size
     * @param withPieces true to paint pieces from the preview FEN when possible
     */
    private void paintMiniBoard(Graphics2D g, int x, int y, int size, boolean withPieces) {
        int board = Math.max(8, size - size % 8);
        int square = Math.max(1, board / 8);
        Rectangle bounds = new Rectangle(x, y, board, board);
        BoardStyle.drawBoardSurface(g, bounds, true);
        if (withPieces) {
            paintFenPieces(g, bounds, previewFenPlacement());
        }
        if (board >= 64) {
            BoardStyle.drawInsideCoordinates(g, bounds, !preview.flip(), Math.max(7, board / 17));
        }
        paintBoardArrow(g, x, y, square);
    }

    /**
     * Paints a quiet suggested-move arrow on a board.
     *
     * @param g graphics
     * @param x board x
     * @param y board y
     * @param square square size
     */
    private void paintBoardArrow(Graphics2D g, int x, int y, int square) {
        Stroke oldStroke = g.getStroke();
        try {
            g.setColor(preview.flip() ? Theme.BOARD_ARROW : PAPER_ACCENT);
            g.setStroke(new BasicStroke(Math.max(1.4f, square / 5.2f),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(x + square * 2, y + square * 6, x + square * 5, y + square * 3);
        } finally {
            g.setStroke(oldStroke);
        }
    }

    /**
     * Paints pieces from a FEN placement string.
     *
     * @param g graphics
     * @param board board bounds
     * @param placement FEN board placement
     */
    private void paintFenPieces(Graphics2D g, Rectangle board, String placement) {
        int rankRow = 0;
        int file = 0;
        int cell = Math.max(1, board.width / 8);
        boolean whiteDown = !preview.flip();
        for (int i = 0; i < placement.length() && rankRow < 8; i++) {
            char ch = placement.charAt(i);
            if (ch == '/') {
                rankRow++;
                file = 0;
            } else if (Character.isDigit(ch)) {
                file += ch - '0';
            } else {
                byte piece = pieceForFen(ch);
                if (piece != Piece.EMPTY && file < 8) {
                    int visualRow = whiteDown ? rankRow : 7 - rankRow;
                    int visualCol = whiteDown ? file : 7 - file;
                    Rectangle square = BoardStyle.cellBounds(board, visualRow, visualCol);
                    Shapes.drawPiece(piece, g, square.x, square.y, cell, cell);
                }
                file++;
            }
        }
    }

    /**
     * Resolves one FEN character to a piece code.
     *
     * @param ch FEN character
     * @return piece code or empty
     */
    private static byte pieceForFen(char ch) {
        return switch (ch) {
            case 'K' -> Piece.WHITE_KING;
            case 'Q' -> Piece.WHITE_QUEEN;
            case 'R' -> Piece.WHITE_ROOK;
            case 'B' -> Piece.WHITE_BISHOP;
            case 'N' -> Piece.WHITE_KNIGHT;
            case 'P' -> Piece.WHITE_PAWN;
            case 'k' -> Piece.BLACK_KING;
            case 'q' -> Piece.BLACK_QUEEN;
            case 'r' -> Piece.BLACK_ROOK;
            case 'b' -> Piece.BLACK_BISHOP;
            case 'n' -> Piece.BLACK_KNIGHT;
            case 'p' -> Piece.BLACK_PAWN;
            default -> Piece.EMPTY;
        };
    }

    /**
     * Extracts a board placement from the source preview, if one is available.
     *
     * @return FEN placement
     */
    private String previewFenPlacement() {
        String source = preview.source();
        int open = source.indexOf('(');
        int close = open < 0 ? -1 : source.indexOf(')', open + 1);
        if (open >= 0 && close > open) {
            String candidate = source.substring(open + 1, close).trim();
            String placement = candidate.split("\\s+")[0];
            if (placement.chars().filter(ch -> ch == '/').count() == 7) {
                return placement;
            }
        }
        return FALLBACK_FEN_PLACEMENT;
    }

    /**
     * Returns a compact board caption.
     *
     * @return caption
     */
    private String compactBoardCaption() {
        String source = preview.source();
        int open = source.indexOf('(');
        int close = open < 0 ? -1 : source.indexOf(')', open + 1);
        if (open >= 0 && close > open) {
            return source.substring(open + 1, close).trim();
        }
        return preview.source().isBlank() ? "workbench position" : preview.source();
    }

    /**
     * Paints a compact puzzle row.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     * @param index row index
     */
    private void paintPuzzleRow(Graphics2D g, int x, int y, int w, int h, int index) {
        g.setColor(index % 2 == 0 ? PAPER_BLOCK : PAPER);
        g.fillRoundRect(x, y, w, h, 4, 4);
        g.setColor(PAPER_LINE);
        g.drawRoundRect(x, y, w - 1, h - 1, 4, 4);
        paintTinyStatus(g, x + 6, y + h / 2 - 3, index % 2 == 0 ? PAPER_PURPLE : PAPER_ACCENT);
        paintTextLine(g, x + 18, y + h / 2 + 2, w / 3, 2);
        paintTextLine(g, x + w / 2, y + h / 2 + 2, w / 3, 2);
    }

    /**
     * Paints a metric card used by the collection preview.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     * @param label label
     * @param accent accent color
     */
    private static void paintMetricCard(Graphics2D g, int x, int y, int w, String label, Color accent) {
        int h = 34;
        g.setColor(PAPER_BLOCK);
        g.fillRoundRect(x, y, w, h, 5, 5);
        g.setColor(accent);
        g.fillRoundRect(x, y, Math.max(3, w / 14), h, 5, 5);
        drawElided(g, label, Theme.font(9, Font.BOLD), PAPER_INK, x + Math.max(8, w / 8), y + 16, w - 12);
        paintTextLine(g, x + Math.max(8, w / 8), y + 26, w - 16, 2);
    }

    /**
     * Paints a move-line strip.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     */
    private static void paintMoveLine(Graphics2D g, int x, int y, int w) {
        g.setColor(new Color(0xE9F2FB));
        g.fillRoundRect(x, y, w, 18, 5, 5);
        drawElided(g, "17. Nf3  Nc6  18. Bb5", Theme.font(9, Font.BOLD), PAPER_ACCENT,
                x + 6, y + 12, w - 12);
    }

    /**
     * Paints a variation block.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     */
    private static void paintVariationBlock(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(PAPER_BLOCK);
        g.fillRoundRect(x, y, w, h, 5, 5);
        g.setColor(PAPER_PURPLE);
        g.fillRect(x, y, Math.max(3, w / 70), h);
        drawElided(g, "candidate variations", Theme.font(9, Font.BOLD), PAPER_INK, x + 10, y + 14, w - 20);
        paintTextBlock(g, x + 10, y + 24, w - 20, h - 28, 4);
    }

    /**
     * Paints a practice block.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     */
    private static void paintExerciseBlock(Graphics2D g, int x, int y, int w, int h) {
        int safeH = Math.max(24, h);
        g.setColor(PAPER);
        g.fillRoundRect(x, y, w, safeH, 5, 5);
        g.setColor(PAPER_LINE);
        g.drawRoundRect(x, y, w - 1, safeH - 1, 5, 5);
        drawElided(g, "quick check", Theme.font(9, Font.BOLD), PAPER_INK, x + 8, y + 15, w - 16);
        paintTextBlock(g, x + 8, y + 25, w - 16, safeH - 30, 3);
    }

    /**
     * Paints a placeholder text block.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     * @param rows row count
     */
    private static void paintTextBlock(Graphics2D g, int x, int y, int w, int h, int rows) {
        int gap = Math.max(5, h / Math.max(8, rows + 2));
        g.setColor(Theme.withAlpha(PAPER_MUTED, 82));
        for (int i = 0; i < rows; i++) {
            int lineW = i == rows - 1 ? Math.max(12, w * 2 / 3) : w;
            g.fillRoundRect(x, y + i * gap, Math.max(8, lineW), 2, 2, 2);
        }
    }

    /**
     * Paints one placeholder text line.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     */
    private static void paintTextLine(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(Theme.withAlpha(PAPER_MUTED, 82));
        g.fillRoundRect(x, y - h, Math.max(8, w), h, h, h);
    }

    /**
     * Paints a small colored status marker.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param color marker color
     */
    private static void paintTinyStatus(Graphics2D g, int x, int y, Color color) {
        g.setColor(color);
        g.fillOval(x, y, 7, 7);
    }

    /**
     * Paints a horizontal rule.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     */
    private static void drawRule(Graphics2D g, int x, int y, int w) {
        g.setColor(PAPER_LINE);
        g.drawLine(x, y, x + w, y);
    }

    /**
     * Draws elided text.
     *
     * @param g graphics
     * @param text text
     * @param font font
     * @param color color
     * @param x x
     * @param y baseline y
     * @param maxW maximum width
     */
    private static void drawElided(Graphics2D g, String text, Font font, Color color, int x, int y, int maxW) {
        if (maxW <= 0) {
            return;
        }
        g.setFont(font);
        g.setColor(color);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(Ui.elide(text == null || text.isBlank() ? "Untitled" : text, metrics, maxW), x, y);
    }

    /**
     * Preview layout kind.
     */
    private enum PreviewKind {
        /**
         * Direct diagram PDF.
         */
        DIAGRAMS,

        /**
         * Manifest render PDF.
         */
        RENDER,

        /**
         * Puzzle collection package.
         */
        COLLECTION,

        /**
         * Study book.
         */
        STUDY,

        /**
         * Cover spread.
         */
        COVER
    }

    /**
     * Preview data.
     *
     * @param workflow workflow label
     * @param title title
     * @param subtitle subtitle
     * @param source source summary
     * @param output output summary
     * @param ready true when runnable
     * @param issue issue text
     * @param pageCount estimated page count
     * @param cover true for cover rendering
     * @param diagramLayout true for diagram layout
     * @param flip true when rendered black-down
     * @param noFen true when FEN captions are hidden
     */
    public record Preview(
            String workflow,
            String title,
            String subtitle,
            String source,
            String output,
            boolean ready,
            String issue,
            int pageCount,
            boolean cover,
            boolean diagramLayout,
            boolean flip,
            boolean noFen) {

        /**
         * Empty preview.
         *
         * @return preview
         */
        static Preview empty() {
            return new Preview("Publishing", "ChessRTK Workbench", "", "", "", false, "", 1,
                    false, true, false, false);
        }

        /**
         * Returns a normalized preview.
         *
         * @return normalized preview
         */
        Preview normalized() {
            int pages = Math.max(1, Math.min(MAX_PAGES, pageCount));
            String normalizedTitle = title == null || title.isBlank() ? "ChessRTK Workbench" : title;
            String normalizedWorkflow = workflow == null || workflow.isBlank() ? "Publishing" : workflow;
            return new Preview(normalizedWorkflow, normalizedTitle, safe(subtitle), safe(source), safe(output),
                    ready, safe(issue), pages, cover, diagramLayout, flip, noFen);
        }

        /**
         * Returns the task-specific preview layout.
         *
         * @return preview kind
         */
        private PreviewKind kind() {
            if (cover) {
                return PreviewKind.COVER;
            }
            if (diagramLayout) {
                return PreviewKind.DIAGRAMS;
            }
            String value = workflow.toLowerCase(Locale.ROOT);
            if (value.contains("collection")) {
                return PreviewKind.COLLECTION;
            }
            if (value.contains("study")) {
                return PreviewKind.STUDY;
            }
            return PreviewKind.RENDER;
        }

        /**
         * Normalizes nullable preview text.
         *
         * @param value raw value
         * @return non-null value
         */
        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }
}
