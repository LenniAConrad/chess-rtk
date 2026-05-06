package application.gui.workbench;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

/**
 * Lightweight PDF-style publishing preview for the workbench.
 */
final class WorkbenchPublishPreview extends JComponent {

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
    WorkbenchPublishPreview() {
        setOpaque(true);
        setBackground(WorkbenchTheme.ELEVATED_SOLID);
        setForeground(WorkbenchTheme.TEXT);
        setPreferredSize(new Dimension(280, 360));
        setMinimumSize(new Dimension(220, 280));
        setToolTipText("Publishing PDF preview");
    }

    /**
     * Updates the preview model.
     *
     * @param next next preview
     */
    void setPreview(Preview next) {
        preview = next == null ? Preview.empty() : next.normalized();
        page = Math.max(1, Math.min(page, preview.pageCount()));
        repaint();
    }

    /**
     * Moves to the previous page.
     */
    void previousPage() {
        if (page > 1) {
            page--;
            repaint();
        }
    }

    /**
     * Moves to the next page.
     */
    void nextPage() {
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
    String pageLabel() {
        return "page " + page + " / " + preview.pageCount();
    }

    /**
     * Returns current page number.
     *
     * @return page number
     */
    int pageNumber() {
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
        int availableW = Math.max(1, getWidth() - 34);
        int availableH = Math.max(1, getHeight() - 34);
        int pageH = availableH;
        int pageW = (int) Math.round(pageH * PAGE_RATIO);
        if (pageW > availableW) {
            pageW = availableW;
            pageH = (int) Math.round(pageW / PAGE_RATIO);
        }
        int x = (getWidth() - pageW) / 2;
        int y = (getHeight() - pageH) / 2;

        g.setColor(WorkbenchTheme.withAlpha(Color.BLACK, 24));
        g.fillRoundRect(x + 8, y + 10, pageW, pageH, 8, 8);
        g.setColor(Color.WHITE);
        g.fillRoundRect(x, y, pageW, pageH, 8, 8);
        g.setColor(WorkbenchTheme.LINE);
        g.drawRoundRect(x, y, Math.max(0, pageW - 1), Math.max(0, pageH - 1), 8, 8);

        int pad = Math.max(12, pageW / 14);
        if (preview.cover()) {
            paintCover(g, x + pad, y + pad, pageW - pad * 2, pageH - pad * 2);
        } else if (preview.diagramLayout()) {
            paintDiagramPage(g, x + pad, y + pad, pageW - pad * 2, pageH - pad * 2);
        } else {
            paintBookPage(g, x + pad, y + pad, pageW - pad * 2, pageH - pad * 2);
        }
        paintFooter(g, x + pad, y + pageH - pad, pageW - pad * 2);
    }

    /**
     * Paints a cover preview.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     */
    private void paintCover(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(WorkbenchTheme.withAlpha(WorkbenchTheme.ACCENT, 28));
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.setColor(WorkbenchTheme.ACCENT);
        g.fillRect(x, y, Math.max(4, w / 30), h);
        drawElided(g, preview.title(), WorkbenchTheme.font(20, Font.BOLD), WorkbenchTheme.TEXT,
                x + w / 10, y + h / 3, w - w / 5);
        if (!preview.subtitle().isBlank()) {
            drawElided(g, preview.subtitle(), WorkbenchTheme.font(12, Font.PLAIN), WorkbenchTheme.MUTED,
                    x + w / 10, y + h / 3 + 24, w - w / 5);
        }
        paintMiniBoard(g, x + w / 2 - Math.min(w, h) / 5, y + h * 3 / 5, Math.min(w, h) / 3);
    }

    /**
     * Paints a diagram page preview.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     */
    private void paintDiagramPage(Graphics2D g, int x, int y, int w, int h) {
        drawElided(g, preview.title(), WorkbenchTheme.font(14, Font.BOLD), WorkbenchTheme.TEXT, x, y + 12, w);
        int board = Math.min(w, Math.max(48, h / 3));
        int gap = Math.max(10, h / 28);
        paintMiniBoard(g, x + (w - board) / 2, y + 30, board);
        if (!preview.noFen()) {
            drawRule(g, x, y + 38 + board, w);
            drawElided(g, preview.source(), WorkbenchTheme.mono(9), WorkbenchTheme.MUTED,
                    x, y + 52 + board, w);
        }
        int small = Math.max(28, board / 3);
        int top = y + board + gap * 3;
        for (int i = 0; i < 3 && top + small < y + h - 24; i++) {
            paintMiniBoard(g, x + i * (small + gap), top, small);
        }
    }

    /**
     * Paints a book-interior page preview.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     */
    private void paintBookPage(Graphics2D g, int x, int y, int w, int h) {
        drawElided(g, preview.title(), WorkbenchTheme.font(13, Font.BOLD), WorkbenchTheme.TEXT, x, y + 10, w);
        drawRule(g, x, y + 22, w);
        int board = Math.min(w / 2, h / 4);
        paintMiniBoard(g, x, y + 36, board);
        paintTextBlock(g, x + board + 12, y + 38, Math.max(20, w - board - 12), board - 4, 6);
        paintTextBlock(g, x, y + board + 58, w, Math.max(40, h / 4), 8);
        paintTextBlock(g, x, y + board + 126, w, Math.max(30, h / 5), 5);
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
        g.setFont(WorkbenchTheme.mono(9));
        g.setColor(preview.ready() ? WorkbenchTheme.STATUS_SUCCESS_TEXT : WorkbenchTheme.STATUS_WARNING_TEXT);
        String status = preview.ready() ? "ready" : "needs input";
        g.drawString(status, x, y);
        String pages = pageLabel();
        g.setColor(WorkbenchTheme.MUTED);
        g.drawString(pages, x + Math.max(0, w - g.getFontMetrics().stringWidth(pages)), y);
    }

    /**
     * Paints a tiny chessboard diagram.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param size square board size
     */
    private void paintMiniBoard(Graphics2D g, int x, int y, int size) {
        int square = Math.max(1, size / 8);
        int board = square * 8;
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                g.setColor(((rank + file) & 1) == 0 ? WorkbenchTheme.BOARD_LIGHT : WorkbenchTheme.BOARD_DARK);
                g.fillRect(x + file * square, y + rank * square, square, square);
            }
        }
        g.setColor(preview.flip() ? WorkbenchTheme.BOARD_ARROW : WorkbenchTheme.ACCENT);
        g.setStroke(new BasicStroke(Math.max(1.4f, square / 5.0f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(x + square * 2, y + square * 6, x + square * 5, y + square * 3);
        g.setColor(WorkbenchTheme.withAlpha(Color.BLACK, 55));
        g.drawRect(x, y, board, board);
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
        g.setColor(WorkbenchTheme.withAlpha(WorkbenchTheme.MUTED, 70));
        for (int i = 0; i < rows; i++) {
            int lineW = i == rows - 1 ? Math.max(12, w * 2 / 3) : w;
            g.fillRoundRect(x, y + i * gap, Math.max(8, lineW), 2, 2, 2);
        }
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
        g.setColor(WorkbenchTheme.LINE);
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
        g.setFont(font);
        g.setColor(color);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(WorkbenchUi.elide(text == null || text.isBlank() ? "Untitled" : text, metrics, maxW), x, y);
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
    record Preview(
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

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }
}
