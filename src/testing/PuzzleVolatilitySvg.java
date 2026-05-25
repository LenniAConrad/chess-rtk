package testing;

import static testing.PuzzleVolatilityOutput.*;
import static testing.PuzzleVolatilityReport.*;

import java.util.List;
import java.util.Locale;

/**
 * SVG chart renderer for {@link PuzzleVolatilityReport}.
 */
final class PuzzleVolatilitySvg {

    /**
     * Utility class; prevent instantiation.
     */
    private PuzzleVolatilitySvg() {
        // utility
    }

    /**
     * Builds the root-stack swing distribution as SVG.
     * @param reports report data rows
     * @return build svg result
     */
    static String buildSvg(List<RootReport> reports) {
        List<RootReport> changedReports = changedReports(reports);
        int displayMax = displayMaxSwing(changedReports);
        int[] bins = bins(changedReports, displayMax);
        double[] percentages = percentages(bins, changedReports.size());
        double maxPercent = max(percentages);
        double percentStep = percentTickStep(maxPercent);
        double axisMaxPercent = Math.max(percentStep, Math.ceil(maxPercent / percentStep) * percentStep);
        int width = REPORT_WIDTH;
        int height = REPORT_HEIGHT;
        int left = 70;
        int right = 42;
        int top = 86;
        int bottom = 82;
        int plotW = width - left - right;
        int plotH = height - top - bottom;
        double bw = plotW / (double) bins.length;

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width).append("\" height=\"")
                .append(height).append("\" viewBox=\"0 0 ").append(width).append(' ').append(height).append("\">\n");
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#fbfaf7\"/>\n");
        appendText(sb, left, 32, null, 22, "#17202a", "Puzzle depth volatility");
        appendText(sb, left, 56, null, 12, MUTED_TEXT_FILL,
                reports.size() + " puzzle stacks; " + changedReports.size() + " changed; "
                        + reversalCount(reports) + " reversals; changed p50 "
                        + percentileSwing(changedReports, 0.50)
                        + ", p90 " + percentileSwing(changedReports, 0.90)
                        + ", p99 " + percentileSwing(changedReports, 0.99)
                        + "; " + BIN_WIDTH + "-point bars show % of changed stacks; axis max " + displayMax);
        drawVolatilityBands(sb, changedReports, left, top, plotW, plotH, displayMax);
        drawAxes(sb, left, top, plotW, plotH, axisMaxPercent, percentStep);
        for (int i = 0; i < percentages.length; i++) {
            double x = left + i * bw + 1.0;
            double h = plotH * percentages[i] / axisMaxPercent;
            double y = top + plotH - h;
            sb.append("<rect x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y)).append("\" width=\"")
                    .append(fmt(Math.max(0.20, bw * 0.85))).append("\" height=\"").append(fmt(h))
                    .append("\" fill=\"#2f6f9f\" opacity=\"0.62\"/>\n");
        }
        drawPercentileMarkers(sb, changedReports, left, top, plotW, plotH, displayMax);
        drawSwingTicks(sb, left, top, plotW, plotH, displayMax);
        drawLegend(sb, (double) width - 305.0, 30);
        sb.append("</svg>\n");
        return sb.toString();
    }

    private static void appendLine(StringBuilder sb, double x1, double y1, double x2, double y2, String stroke,
            String dashArray) {
        sb.append("<line x1=\"").append(fmt(x1)).append("\" y1=\"").append(fmt(y1))
                .append("\" x2=\"").append(fmt(x2)).append("\" y2=\"").append(fmt(y2))
                .append("\" stroke=\"").append(stroke).append('"');
        if (dashArray != null) {
            sb.append(" stroke-dasharray=\"").append(dashArray).append('"');
        }
        sb.append("/>\n");
    }

    /**
     * Appends one SVG text element.
     * @param sb string builder
     * @param x x coordinate
     * @param y y coordinate
     * @param anchor anchor value
     * @param size size value
     * @param fill fill color
     * @param text text value
     */
    private static void appendText(StringBuilder sb, double x, double y, String anchor, int size, String fill,
            String text) {
        sb.append("<text x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y)).append('"');
        if (anchor != null) {
            sb.append(" text-anchor=\"").append(anchor).append('"');
        }
        sb.append(" font-family=\"").append(SVG_FONT).append("\" font-size=\"").append(size)
                .append("\" fill=\"").append(fill).append("\">").append(escapeXml(text)).append("</text>\n");
    }

    /**
     * Escapes SVG text.
     * @param text text value
     * @return escape xml result
     */
    private static String escapeXml(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Draws volatility severity bands.
     * @param sb string builder
     * @param reports report data rows
     * @param left left coordinate
     * @param top top coordinate
     * @param plotW plot width in pixels
     * @param plotH plot height in pixels
     * @param displayMax display maximum value
     */
    private static void drawVolatilityBands(StringBuilder sb, List<RootReport> reports, int left, int top, int plotW,
            int plotH, int displayMax) {
        int[] starts = { 0, 150, VOLATILE_SWING, SHARP_SWING, EXTREME_SWING };
        int[] ends = { 149, VOLATILE_SWING - 1, SHARP_SWING - 1, EXTREME_SWING - 1, displayMax };
        String[] fills = { "#edf7ef", "#f3f7ed", "#fff7e6", "#fff0e4", "#f9e8ec" };
        String[] labels = { "Stable", "Noisy", "Volatile", "Sharp", "Extreme" };
        for (int i = 0; i < starts.length; i++) {
            if (starts[i] > displayMax) {
                continue;
            }
            double x1 = xForSwing(starts[i], left, plotW, displayMax);
            double x2 = xForSwing(Math.min(ends[i], displayMax) + 1, left, plotW, displayMax);
            sb.append("<rect x=\"").append(fmt(x1)).append("\" y=\"").append(fmt(top))
                    .append("\" width=\"").append(fmt(Math.max(0.0, x2 - x1)))
                    .append("\" height=\"").append(fmt(plotH))
                    .append("\" fill=\"").append(fills[i]).append("\" opacity=\"0.38\"/>\n");
            double width = Math.max(0.0, x2 - x1);
            String anchor = width < 135.0 ? "middle" : null;
            double labelX = width < 135.0 ? x1 + width / 2.0 : x1 + 8.0;
            double labelY = top + 16.0 + (width < 135.0 ? (i % 2) * 16.0 : 0.0);
            appendText(sb, labelX, labelY, anchor, 11, "#69737d",
                    labels[i] + " " + String.format(Locale.ROOT, "%.1f%%",
                            percentInSwingRange(reports, starts[i], ends[i])));
        }
    }

    /**
     * Draws graph axes.
     * @param sb string builder
     * @param left left coordinate
     * @param top top coordinate
     * @param plotW plot width in pixels
     * @param plotH plot height in pixels
     * @param maxPercent maximum percentage value
     * @param percentStep percent step value
     */
    private static void drawAxes(StringBuilder sb, int left, int top, int plotW, int plotH, double maxPercent,
            double percentStep) {
        double plotBottom = top + (double) plotH;
        double plotRight = left + (double) plotW;
        appendLine(sb, left, top, left, plotBottom, AXIS_STROKE, null);
        appendLine(sb, left, plotBottom, plotRight, plotBottom, AXIS_STROKE, null);
        for (double percent = 0.0; percent <= maxPercent + percentStep / 2.0; percent += percentStep) {
            double y = top + plotH - plotH * percent / maxPercent;
            appendLine(sb, left, y, plotRight, y, "#d7dde3", "3 5");
            appendText(sb, left - 10.0, y + 4.0, "end", 11, MUTED_TEXT_FILL, formatPercent(percent));
        }
    }

    /**
     * Draws percentile guide lines.
     * @param sb string builder
     * @param reports report data rows
     * @param left left coordinate
     * @param top top coordinate
     * @param plotW plot width in pixels
     * @param plotH plot height in pixels
     * @param displayMax display maximum value
     */
    private static void drawPercentileMarkers(StringBuilder sb, List<RootReport> reports, int left, int top, int plotW,
            int plotH, int displayMax) {
        double[] qs = { 0.10, 0.25, 0.50, 0.75, 0.90, 0.99 };
        String[] labels = { "p10", "p25", "median", "p75", "p90", "p99" };
        for (int i = 0; i < qs.length; i++) {
            int swing = percentileSwing(reports, qs[i]);
            double x = xForSwing(swing, left, plotW, displayMax);
            appendLine(sb, x, top, x, top + (double) plotH, "#7b8794", "2 7");
            appendText(sb, x, top - 8.0 - (i % 2) * 14.0, "middle", 11, MUTED_TEXT_FILL,
                    labels[i] + " " + swing);
        }
    }

    /**
     * Draws x-axis swing ticks.
     * @param sb string builder
     * @param left left coordinate
     * @param top top coordinate
     * @param plotW plot width in pixels
     * @param plotH plot height in pixels
     * @param displayMax display maximum value
     */
    private static void drawSwingTicks(StringBuilder sb, int left, int top, int plotW, int plotH,
            int displayMax) {
        double plotBottom = top + (double) plotH;
        int step = displayMax <= 3000 ? 100 : 200;
        for (int swing = 0; swing <= displayMax; swing += step) {
            double x = xForSwing(swing, left, plotW, displayMax);
            appendLine(sb, x, plotBottom, x, plotBottom + 6.0, AXIS_STROKE, null);
            appendText(sb, x, plotBottom + 24.0, "middle", 11, MUTED_TEXT_FILL, Integer.toString(swing));
        }
    }

    /**
     * Draws a small legend.
     * @param sb string builder
     * @param x x coordinate
     * @param y y coordinate
     */
    private static void drawLegend(StringBuilder sb, double x, double y) {
        sb.append("<rect x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y - 17.0))
                .append("\" width=\"275.00\" height=\"34.00\" fill=\"#fbfaf7\" opacity=\"0.88\"/>\n");
        sb.append("<rect x=\"").append(fmt(x + 8.0)).append("\" y=\"").append(fmt(y - 4.0))
                .append("\" width=\"24.00\" height=\"10.00\" fill=\"#2f6f9f\" opacity=\"0.62\"/>\n");
        appendText(sb, x + 38.0, y + 5.0, null, 11, MUTED_TEXT_FILL,
                BIN_WIDTH + "-point bars (% changed)");
    }

    /**
     * Maps a swing value to an x-coordinate.
     * @param swing swing value
     * @param left left coordinate
     * @param plotW plot width in pixels
     * @param displayMax display maximum value
     * @return x for swing result
     */
    private static double xForSwing(int swing, int left, int plotW, int displayMax) {
        return left + plotW * swing / (double) (displayMax + BIN_WIDTH);
    }
}
