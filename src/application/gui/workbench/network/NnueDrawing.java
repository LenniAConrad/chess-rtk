package application.gui.workbench.network;

import application.gui.workbench.board.BoardStyle;
import application.gui.workbench.ui.HitRegions;
import application.gui.workbench.ui.NotationPainter;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Stateless tooltip, vector, and trace drawing helpers for {@link NnueView}.
 */
public final class NnueDrawing {

    /**
     * Uniform Trace edge stroke.
     */
    private static final float TRACE_EDGE_WIDTH = 1.05f;

    /**
     * Lowest alpha used for visible Trace connections.
     */
    private static final int TRACE_EDGE_ALPHA_MIN = 20;

    /**
     * Highest alpha used for non-focused Trace connections.
     */
    private static final int TRACE_EDGE_ALPHA_MAX = 168;

    /**
     * Extra alpha for the currently selected lane's connections.
     */
    private static final int TRACE_EDGE_FOCUS_BOOST = 58;

    /**
     * Utility class.
     */
    private NnueDrawing() {
    }

    /**
     * Returns a hover tooltip for overview mode without the raw-inspection hint.
     *
     * @param r hit region
     * @return tooltip HTML
     */
    public static String overviewTooltip(HitRegions.Region r) {
        StringBuilder sb = new StringBuilder("<html>");
        sb.append("<b>").append(htmlEscape(r.title)).append("</b>");
        if (r.value != null && !r.value.isEmpty()) {
            sb.append("<br><span style='color:").append(Theme.css(Theme.MUTED)).append(";'>")
                    .append(htmlEscape(r.value)).append("</span>");
        }
        if (r.description != null && !r.description.isEmpty()) {
            sb.append("<br>").append(htmlEscape(r.description));
        }
        sb.append("</html>");
        return sb.toString();
    }

    /**
     * Escapes text for a small tooltip fragment.
     *
     * @param value raw text
     * @return escaped text
     */
    public static String htmlEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Paints one compact metric chip in the NNUE header.
     *
     * @param g graphics
     * @param r chip bounds
     * @param label label
     * @param value value
     * @param accent accent strip colour
     */
    public static void paintHeaderChip(Graphics2D g, Rectangle r,
            String label, String value, Color accent) {
        g.setColor(Theme.ELEVATED_SOLID);
        g.fillRoundRect(r.x, r.y, r.width, r.height,
                Theme.RADIUS, Theme.RADIUS);
        g.setColor(Theme.LINE);
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1,
                Theme.RADIUS, Theme.RADIUS);
        g.setColor(accent == null ? TensorViz.FOCUS : accent);
        g.fillRoundRect(r.x + 3, r.y + 5, 3, r.height - 10, 3, 3);
        g.setFont(Theme.font(9, Font.BOLD));
        FontMetrics labelMetrics = g.getFontMetrics();
        g.setColor(Theme.MUTED);
        g.drawString(Ui.elide(label, labelMetrics, r.width - 18), r.x + 11, r.y + 11);
        g.setFont(Theme.font(r.width < 96 ? 11 : 12, Font.BOLD));
        g.setColor(Theme.TEXT);
        NotationPainter.draw(g, value, r.x + 11, r.y + 24, r.width - 18, Theme.TEXT);
    }

    /**
     * Builds the Stockfish stack dimension summary from the actual captured
     * tensor shapes.
     *
     * @param transformed full transformed vector
     * @param transformedUs side-to-move transformed half
     * @param fc0 FC0 raw output including fwd row
     * @param fc1 FC1 clipped output
     * @return compact stack summary
     */
    public static String stockfishStackSummary(float[] transformed, float[] transformedUs,
            float[] fc0, float[] fc1) {
        int input = safeLength(transformed);
        if (input == 0) {
            input = safeLength(transformedUs);
        }
        int fc0Hidden = Math.max(0, safeLength(fc0) - 1);
        int fc1Out = safeLength(fc1);
        return input + " / " + fc0Hidden + " / " + fc1Out;
    }

    /**
     * Paints one score summary chip.
     *
     * @param g graphics context
     * @param r chip bounds
     * @param label label text
     * @param value primary value text
     * @param detail detail text
     * @param accent accent color
     */
    public static void paintSummaryChip(Graphics2D g, Rectangle r, String label,
            String value, String detail, Color accent) {
        g.setColor(Theme.ELEVATED_SOLID);
        g.fillRoundRect(r.x, r.y, r.width, r.height, Theme.RADIUS, Theme.RADIUS);
        g.setColor(Theme.LINE);
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1,
                Theme.RADIUS, Theme.RADIUS);
        g.setColor(accent == null ? TensorViz.FOCUS : accent);
        g.fillRoundRect(r.x + 4, r.y + 7, 4, r.height - 14, 4, 4);
        g.setFont(Theme.font(10, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Theme.MUTED);
        g.drawString(Ui.elide(label, fm, Math.max(20, r.width - 18)),
                r.x + 15, r.y + 15);
        g.setFont(Theme.font(r.width < 140 ? 15 : 18, Font.BOLD));
        fm = g.getFontMetrics();
        g.setColor(Theme.TEXT);
        NotationPainter.draw(g, value, r.x + 15, r.y + 36,
                Math.max(20, r.width - 18), Theme.TEXT);
        if (r.height >= 58) {
            g.setFont(Theme.font(10, Font.PLAIN));
            fm = g.getFontMetrics();
            g.setColor(Theme.MUTED);
            g.drawString(Ui.elide(detail, fm, Math.max(20, r.width - 18)),
                    r.x + 15, r.y + r.height - 10);
        }
    }

    /**
     * Returns the one-based absolute-impact rank for an active feature row.
     *
     * @param row feature row
     * @param impact feature impact array
     * @return one-based rank, or 0
     */
    public static int featureImpactRank(int row, float[] impact) {
        if (impact == null || row < 0 || row >= impact.length) {
            return 0;
        }
        float abs = Math.abs(impact[row]);
        int rank = 1;
        for (float value : impact) {
            if (Math.abs(value) > abs) {
                rank++;
            }
        }
        return rank;
    }

    /**
     * Fills one board square with a translucent halo.
     *
     * @param g graphics
     * @param board mini-board rectangle
     * @param square 0..63 LERF index
     * @param tint translucent fill colour
     */
    public static void highlightSquare(java.awt.Graphics2D g, Rectangle board, int square, java.awt.Color tint) {
        highlightSquare(g, board, square, tint, true);
    }

    /**
     * Fills one board square with a translucent halo.
     *
     * @param g graphics
     * @param board mini-board rectangle
     * @param square 0..63 LERF index
     * @param tint translucent fill colour
     * @param whiteDown whether White is rendered at the bottom
     */
    public static void highlightSquare(java.awt.Graphics2D g, Rectangle board, int square,
            java.awt.Color tint, boolean whiteDown) {
        Rectangle cell = BoardStyle.lerfSquareBounds(board, square, whiteDown);
        g.setColor(tint);
        g.fillRect(cell.x, cell.y, cell.width, cell.height);
    }


    /**
     * Returns {@code a - b}, allowing either side to be absent.
     *
     * @param a first vector
     * @param b second vector
     * @return difference vector, or null when both inputs are absent
     */
    public static float[] subtractVectors(float[] a, float[] b) {
        int len = Math.max(safeLength(a), safeLength(b));
        if (len == 0) {
            return null;
        }
        float[] out = new float[len];
        for (int i = 0; i < len; i++) {
            out[i] = valueAt(a, i) - valueAt(b, i);
        }
        return out;
    }

    /**
     * Returns {@code a + b}, allowing either side to be absent.
     *
     * @param a first vector
     * @param b second vector
     * @return sum vector, or null when both inputs are absent
     */
    public static float[] addVectors(float[] a, float[] b) {
        int len = Math.max(safeLength(a), safeLength(b));
        if (len == 0) {
            return null;
        }
        float[] out = new float[len];
        for (int i = 0; i < len; i++) {
            out[i] = valueAt(a, i) + valueAt(b, i);
        }
        return out;
    }

    /**
     * Returns a copy of a vector without its final fwd/bias slot.
     *
     * @param values source values
     * @return copy without last value, or null when absent
     */
    public static float[] withoutLast(float[] values) {
        if (values == null || values.length == 0) {
            return null;
        }
        float[] out = new float[Math.max(0, values.length - 1)];
        System.arraycopy(values, 0, out, 0, out.length);
        return out;
    }

    /**
     * Returns compact stats text for a Trace ribbon strip.
     *
     * @param values values
     * @param signed true when the strip is signed
     * @return stats label
     */
    public static String traceStats(float[] values, boolean signed) {
        if (values == null || values.length == 0) {
            return "no data";
        }
        float[] s = TensorViz.summarize(values);
        if (signed) {
            return String.format("mean %+.3f | rms %.3f | max %.3f",
    s[0], s[2], maxAbs(values));
        }
        return String.format("mean %.3f | max %.3f", s[0], s[4]);
    }

    /**
     * Safe array length helper.
     *
     * @param values values
     * @return length, or 0 for null
     */
    public static int safeLength(float[] values) {
        return values == null ? 0 : values.length;
    }

    /**
     * Picks a pitch that prefers the nominal spacing but compresses enough to
     * keep a column inside the current pane.
     *
     * @param usable usable vertical pixels
     * @param count node count
     * @param nominal preferred pitch
     * @return adaptive pitch
     */
    public static int adaptivePitch(int usable, int count, int nominal) {
        if (count <= 1) {
            return 0;
        }
        int fit = Math.max(6, usable / (count - 1));
        return Math.min(nominal, fit);
    }

    /**
     * Draws a numbered stage label above a Trace column.
     *
     * @param g graphics
     * @param cx center x
     * @param y title baseline
     * @param width maximum label width
     * @param number stage number
     * @param title title
     * @param detail detail line
     */
    public static void drawStageHeader(Graphics2D g, int cx, int y, int width,
            String number, String title, String detail) {
        String heading = number + " " + title;
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(10, Font.BOLD));
        drawCenteredFittedLabel(g, heading, cx, y, width);
        if (width >= 64) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(9, Font.PLAIN));
            drawCenteredFittedLabel(g, detail, cx, y + 12, width);
        }
    }

    /**
     * Draws a muted placeholder for an inactive feature lane.
     *
     * @param g graphics
     * @param cx center x
     * @param y center y
     * @param radius node radius
     */
    public static void drawInactiveFeatureLane(Graphics2D g, int cx, int y, int radius) {
        g.setColor(Theme.withAlpha(Theme.LINE, 90));
        g.fillOval(cx - radius, y - radius, radius * 2, radius * 2);
        g.setColor(Theme.withAlpha(Theme.MUTED, 130));
        g.drawOval(cx - radius, y - radius, radius * 2, radius * 2);
    }

    /**
     * Draws a Trace edge using one fixed stroke style for every layer. Colour
     * carries sign and opacity carries magnitude.
     *
     * @param g graphics
     * @param x1 source x
     * @param y1 source y
     * @param x2 target x
     * @param y2 target y
     * @param strength signed magnitude in [-1, 1]
     * @param emphasised true to highlight a selected slot's incoming edges
     */
    public static void drawTraceEdge(Graphics2D g, int x1, int y1, int x2, int y2,
            float strength, boolean emphasised) {
        float s = Math.max(-1.0f, Math.min(1.0f, strength));
        float mag = (float) Math.sqrt(Math.abs(s));
        Color base = Math.abs(s) < 0.004f ? Theme.MUTED
                : s >= 0.0f ? TensorViz.POSITIVE : TensorViz.NEGATIVE;
        int alpha = TRACE_EDGE_ALPHA_MIN
                + Math.round((TRACE_EDGE_ALPHA_MAX - TRACE_EDGE_ALPHA_MIN) * mag);
        if (emphasised) {
            alpha = Math.min(238, alpha + TRACE_EDGE_FOCUS_BOOST);
        }
        g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
        g.setStroke(new BasicStroke(TRACE_EDGE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(x1, y1, x2, y2);
        g.setStroke(new BasicStroke(1.0f));
    }

    /**
     * Draws the Stockfish FC0 forward-skip edge as a normal Trace line.
     *
     * @param g graphics
     * @param x1 source x
     * @param y1 source y
     * @param x2 target x
     * @param y2 target y
     * @param strength signed magnitude in [-1, 1]
     * @return approximate bounds for hover hit-testing
     */
    public static Rectangle drawTraceSkipEdge(Graphics2D g, int x1, int y1, int x2, int y2,
            float strength) {
        int left = Math.min(x1, x2);
        int right = Math.max(x1, x2);
        int top = Math.min(y1, y2);
        int bottom = Math.max(y1, y2);
        drawTraceEdge(g, x1, y1, x2, y2, strength, false);
        return new Rectangle(left - 6, top - 6, Math.max(1, right - left + 12),
                Math.max(1, bottom - top + 12));
    }

    /**
     * Draws a centered label, trimming it to fit a narrow Trace stage band.
     *
     * @param g graphics
     * @param text label
     * @param cx center x
     * @param y baseline y
     * @param maxWidth maximum text width
     */
    public static void drawCenteredFittedLabel(Graphics2D g, String text, int cx, int y, int maxWidth) {
        FontMetrics fm = g.getFontMetrics();
        String fitted = fitText(fm, text, Math.max(0, maxWidth));
        if (fitted.isEmpty()) {
            return;
        }
        int w = fm.stringWidth(fitted);
        g.drawString(fitted, cx - w / 2, y);
    }

    /**
     * Trims text with an ASCII ellipsis when it is wider than the available
     * label width.
     *
     * @param fm font metrics
     * @param text text
     * @param maxWidth maximum width
     * @return fitted text
     */
    public static String fitText(FontMetrics fm, String text, int maxWidth) {
        if (maxWidth <= 0 || text == null || text.isEmpty()) {
            return "";
        }
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = fm.stringWidth(ellipsis);
        if (maxWidth <= ellipsisWidth) {
            return "";
        }
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return end <= 0 ? "" : text.substring(0, end) + ellipsis;
    }

    /**
     * Returns the index with the largest absolute value.
     *
     * @param values values
     * @return index or -1
     */
    public static int strongestAbsIndex(float[] values) {
        if (values == null || values.length == 0) {
            return -1;
        }
        int best = 0;
        float bestAbs = Math.abs(values[0]);
        for (int i = 1; i < values.length; i++) {
            float value = Math.abs(values[i]);
            if (value > bestAbs) {
                bestAbs = value;
                best = i;
            }
        }
        return best;
    }

    /**
     * Outlines one row-major cell in a heatmap grid.
     *
     * @param g graphics
     * @param grid grid rectangle
     * @param index row-major cell index
     * @param cols column count
     * @param rows row count
     * @param color outline colour
     */
    public static void drawGridCellRing(Graphics2D g, Rectangle grid, int index,
            int cols, int rows, Color color) {
        if (index < 0 || cols <= 0 || rows <= 0 || index >= cols * rows) {
            return;
        }
        int col = index % cols;
        int row = index / cols;
        double cellW = grid.width / (double) cols;
        double cellH = grid.height / (double) rows;
        int x = (int) Math.floor(grid.x + col * cellW);
        int y = (int) Math.floor(grid.y + row * cellH);
        int w = Math.max(2, (int) Math.ceil(cellW));
        int h = Math.max(2, (int) Math.ceil(cellH));
        g.setColor(Theme.withAlpha(Theme.PANEL_SOLID, 224));
        g.drawRect(x, y, w - 1, h - 1);
        g.setColor(color);
        g.drawRect(x + 1, y + 1, Math.max(1, w - 3), Math.max(1, h - 3));
    }

    /**
     * Computes a non-zero absolute scale for a dense matrix/vector.
     *
     * @param values values
     * @return scale
     */
    public static float matrixScale(float[] values) {
        float scale = maxAbs(values);
        return scale <= 0.0f ? 1.0f : scale;
    }

    /**
     * Computes a robust visible-slot scale for signed us-minus-them lane values.
     *
     * @param us side-to-move values
     * @param them opponent values
     * @param slots visible slots
     * @return non-zero scale
     */
    public static float visiblePairScale(float[] us, float[] them, int[] slots) {
        float scale = 0.0f;
        if (slots != null) {
            for (int idx : slots) {
                scale = Math.max(scale, Math.abs(valueAt(us, idx) - valueAt(them, idx)));
            }
        }
        if (scale <= 0.0f) {
            scale = Math.max(maxAbs(us), maxAbs(them));
        }
        return scale <= 0.0f ? 1.0f : scale;
    }

    /**
     * Computes a robust visible-slot scale for one signed vector.
     *
     * @param values values
     * @param slots visible slots
     * @return non-zero scale
     */
    public static float visibleAbsScale(float[] values, int[] slots) {
        float scale = 0.0f;
        if (slots != null) {
            for (int idx : slots) {
                scale = Math.max(scale, Math.abs(valueAt(values, idx)));
            }
        }
        if (scale <= 0.0f) {
            scale = maxAbs(values);
        }
        return scale <= 0.0f ? 1.0f : scale;
    }

    /**
     * Reads an array value, returning zero when the array is absent or too
     * short for a selected slot.
     *
     * @param values values
     * @param index index
     * @return value or 0
     */
    public static float valueAt(float[] values, int index) {
        return values == null || index < 0 || index >= values.length ? 0.0f : values[index];
    }

    /**
     * Delegates to the shared network-view absolute maximum helper.
     *
     * @param data data
     * @return maximum absolute value
     */
    private static float maxAbs(float[] data) {
        return NetworkView.maxAbs(data);
    }
}
