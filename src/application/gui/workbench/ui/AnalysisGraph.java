package application.gui.workbench.ui;

import chess.core.Move;
import chess.uci.Evaluation;
import chess.uci.Output;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import javax.swing.SwingConstants;

/**
 * Compact live-engine data visualization and report source.
 */
public final class AnalysisGraph extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Maximum number of samples kept for drawing.
     */
    private static final int MAX_SAMPLES = 180;

    /**
     * Mate score used for scaling graph samples.
     */
    private static final int MATE_CP = 3000;

    /**
     * Minimum dynamic eval range.
     */
    private static final int MIN_EVAL_RANGE_CP = 300;

    /**
     * Maximum visible eval range.
     */
    private static final int MAX_EVAL_RANGE_CP = 2000;

    /**
     * Internal padding.
     */
    private static final int PAD = 16;

    /**
     * Header height.
     */
    private static final int HEADER_H = 72;

    /**
     * Gap between chart bands.
     */
    private static final int BAND_GAP = 8;

    /**
     * Header lane reserved inside each chart band for labels.
     */
    private static final int BAND_LABEL_H = 20;

    /**
     * Inner edge reserved for the chart border.
     */
    private static final int PLOT_EDGE_INSET = 1;

    /**
     * Evaluation line emitted by the text-mode engine analysis command.
     */
    private static final Pattern SUMMARY_EVAL_PATTERN =
            Pattern.compile("(?m)^\\s*eval:\\s*(#-?\\d+|[+-]?\\d+)\\b");

    /**
     * Search-depth line emitted by the text-mode engine analysis command.
     */
    private static final Pattern SUMMARY_DEPTH_PATTERN =
            Pattern.compile("(?m)^\\s*depth:\\s*(\\d+)\\b");

    /**
     * Search work line emitted by the text-mode engine analysis command.
     */
    private static final Pattern SUMMARY_WORK_PATTERN =
            Pattern.compile("(?m)^\\s*nodes:\\s*([^\\s]+)\\s+nps:\\s*([^\\s]+)\\s+time:\\s*([^\\s]+)");

    /**
     * Best-move line emitted by the text-mode engine analysis command.
     */
    private static final Pattern SUMMARY_BEST_PATTERN =
            Pattern.compile("(?m)^\\s*best:\\s*([a-h][1-8][a-h][1-8][qrbn]?)\\b");

    /**
     * Raw UCI score pattern used by bestmove-style command output.
     */
    private static final Pattern UCI_SCORE_PATTERN =
            Pattern.compile("\\bscore\\s+(cp|mate)\\s+(-?\\d+)\\b");

    /**
     * Raw UCI bestmove pattern.
     */
    private static final Pattern UCI_BESTMOVE_PATTERN =
            Pattern.compile("\\bbestmove\\s+([a-h][1-8][a-h][1-8][qrbn]?)\\b");

    /**
     * Printable graph snapshot width.
     */
    private static final int PRINT_GRAPH_W = 900;

    /**
     * Printable graph snapshot height.
     */
    private static final int PRINT_GRAPH_H = 520;

    /**
     * Graph samples in insertion order.
     */
    private final List<Sample> samples = new ArrayList<>(MAX_SAMPLES);

    /**
     * Current position label.
     */
    private String position = "";

    /**
     * Last valid evaluation, used to smooth sparse UCI info lines.
     */
    private Integer lastEvalCp;

    /**
     * Last valid search depth.
     */
    private int lastDepth;

    /**
     * Last valid node count.
     */
    private long lastNodes;

    /**
     * Last valid nodes-per-second value.
     */
    private long lastNps;

    /**
     * Last valid search time value.
     */
    private long lastTimeMs;

    /**
     * Creates a graph.
     */
    public AnalysisGraph() {
        setOpaque(true);
        setBackground(Theme.ELEVATED_SOLID);
        setForeground(Theme.TEXT);
        setPreferredSize(new Dimension(420, 420));
        setMinimumSize(new Dimension(320, 300));
        setToolTipText("Live engine analysis data");
    }

    /**
     * Resets graph data for a new visible position.
     *
     * @param fen position FEN
     */
    public void resetForPosition(String fen) {
        samples.clear();
        position = fen == null ? "" : fen;
        resetCarriedValues();
        firePropertyChange("analysisSamples", null, Integer.valueOf(samples.size()));
        repaint();
    }

    /**
     * Clears all graph samples.
     */
    public void clearSamples() {
        samples.clear();
        resetCarriedValues();
        firePropertyChange("analysisSamples", null, Integer.valueOf(samples.size()));
        repaint();
    }

    /**
     * Adds one live engine sample.
     *
     * @param whiteToMove true when White was to move in the analyzed position
     * @param output engine output
     * @param bestMove current best move
     */
    public void addSample(boolean whiteToMove, Output output, short bestMove) {
        if (output == null) {
            return;
        }
        Sample sample = sampleFrom(whiteToMove, output, bestMove);
        addSample(sample);
    }

    /**
     * Adds one completed command sample parsed from text output.
     *
     * @param whiteToMove true when White was to move in the analyzed position
     * @param output completed command output
     * @return true when a visible sample was added
     */
    public boolean addCommandOutput(boolean whiteToMove, String output) {
        return addSample(sampleFromCommandOutput(whiteToMove, output));
    }

    /**
     * Returns sample count.
     *
     * @return sample count
     */
    public int sampleCount() {
        return samples.size();
    }

    /**
     * Returns the latest eval label.
     *
     * @return latest eval label or blank
     */
    public String latestEvalLabel() {
        if (samples.isEmpty()) {
            return "";
        }
    return formatEval(samples.get(samples.size() - 1));
    }

    /**
     * Returns whether graph data is available.
     *
     * @return true when samples are available
     */
    public boolean hasSamples() {
        return !samples.isEmpty();
    }

    /**
     * Returns a UI-friendly summary of the latest analysis sample.
     *
     * @return latest summary with dash placeholders when no samples exist
     */
    public LatestSummary latestSummaryValues() {
        if (samples.isEmpty()) {
            return new LatestSummary("-", "-", "-", "-", "-", "0");
        }
        Sample latest = latest();
        return new LatestSummary(
                formatEval(latest),
                moveLabel(latest.bestMove()).isBlank() ? "-" : moveLabel(latest.bestMove()),
                Integer.toString(latest.depth()),
                formatCount(latest.nodes()),
                formatCount(latest.nps()),
                Integer.toString(samples.size()));
    }

    /**
     * Summary values shown outside the graph in the Board / Analyze inspector.
     *
     * @param eval latest white-relative evaluation
     * @param bestMove latest best move, if available
     * @param depth latest search depth
     * @param nodes latest node count
     * @param nps latest nodes per second
     * @param samples retained sample count
     */
    public record LatestSummary(String eval, String bestMove, String depth, String nodes,
            String nps, String samples) {
    }

    /**
     * Builds a CSV export for downstream data analysis.
     *
     * @return CSV text
     */
    public String csvText() {
        StringBuilder sb = new StringBuilder(samples.size() * 48 + 96);
        sb.append("index,eval_cp,eval,depth,nodes,nps,time_ms,best_move\n");
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            sb.append(i + 1).append(',')
                    .append(sample.evalCp()).append(',')
                    .append(formatEval(sample)).append(',')
                    .append(sample.depth()).append(',')
                    .append(sample.nodes()).append(',')
                    .append(sample.nps()).append(',')
                    .append(sample.timeMs()).append(',')
                    .append(moveLabel(sample.bestMove())).append('\n');
        }
        return sb.toString();
    }

    /**
     * Builds a compact plain-text report for copying or printing.
     *
     * @return report text
     */
    public String reportText() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("CRTK Workbench Analysis Report\n");
        sb.append("Position: ").append(position == null || position.isBlank() ? "(not set)" : position).append('\n');
        sb.append("Samples: ").append(samples.size()).append('\n');
        if (samples.isEmpty()) {
            sb.append("No analysis data collected.\n");
            return sb.toString();
        }

        Stats stats = stats();
        Sample latest = latest();
        sb.append("Latest: ").append(formatEval(latest))
                .append(", depth ").append(latest.depth())
                .append(", nodes ").append(formatCount(latest.nodes()))
                .append(", nps ").append(formatCount(latest.nps()))
                .append(", best ").append(moveLabel(latest.bestMove())).append('\n');
        sb.append("Evaluation: min ").append(formatEvalCp(stats.minEvalCp()))
                .append(", max ").append(formatEvalCp(stats.maxEvalCp()))
                .append(", swing ").append(formatEvalCp(Math.abs(stats.maxEvalCp() - stats.minEvalCp())))
                .append('\n');
        sb.append("Depth: avg ").append(String.format(Locale.ROOT, "%.1f", stats.avgDepth()))
                .append(", max ").append(stats.maxDepth()).append('\n');
        sb.append("Throughput: avg ").append(formatCount(stats.avgNps()))
                .append(" nps, max ").append(formatCount(stats.maxNps()))
                .append(" nps, max nodes ").append(formatCount(stats.maxNodes())).append('\n');
        sb.append("Graph stacks: evaluation, depth, nodes/nps\n");
        return sb.toString();
    }

    /**
     * Opens the print dialog and prints the current graph report.
     *
     * @return true when print was submitted
     * @throws PrinterException when printing fails
     */
    public boolean printReport() throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("CRTK Workbench Analysis Report");
        job.setPrintable(this::printReportPage);
        if (!job.printDialog()) {
            return false;
        }
        job.print();
        return true;
    }

    /**
     * Paints graph surface.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintSurface(g, getWidth(), getHeight());
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints the full graph surface at the requested size.
     *
     * @param g graphics
     * @param width width in pixels
     * @param height height in pixels
     */
    private void paintSurface(Graphics2D g, int width, int height) {
        paintBackground(g, width, height);
        paintHeader(g, width);
        if (samples.isEmpty()) {
            paintEmpty(g, width, height);
        } else {
            paintCharts(g, width, height);
        }
    }

    /**
     * Prints the first report page.
     *
     * @param graphics graphics context
     * @param page page format
     * @param pageIndex zero-based page index
     * @return printable page status
     */
    private int printReportPage(Graphics graphics, PageFormat page, int pageIndex) {
        if (pageIndex > 0) {
            return Printable.NO_SUCH_PAGE;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            int x = (int) Math.round(page.getImageableX());
            int y = (int) Math.round(page.getImageableY());
            int w = (int) Math.round(page.getImageableWidth());
            int h = (int) Math.round(page.getImageableHeight());
            g.translate(x, y);
            paintReportText(g, w);
            paintReportGraph(g, w, h);
            return Printable.PAGE_EXISTS;
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints solid background.
     *
     * @param g graphics
     * @param width width in pixels
     * @param height height in pixels
     */
    private void paintBackground(Graphics2D g, int width, int height) {
        g.setColor(getBackground());
        g.fillRect(0, 0, width, height);
    }

    /**
     * Paints header metrics.
     *
     * @param g graphics
     * @param width width in pixels
     */
    private void paintHeader(Graphics2D g, int width) {
        g.setFont(Theme.font(12, Font.BOLD));
        g.setColor(Theme.TEXT);
        g.drawString("Analysis", PAD, 20);

        g.setFont(Theme.mono(11));
        g.setColor(Theme.MUTED);
        String detail = samples.isEmpty() ? compactPosition() : latestSummary();
        g.drawString(Ui.elide(detail, g.getFontMetrics(), Math.max(0, width - PAD * 2)), PAD, 38);
        if (!samples.isEmpty()) {
            g.drawString(Ui.elide(statsSummary(), g.getFontMetrics(), Math.max(0, width - PAD * 2)),
                    PAD, 54);
        }
    }

    /**
     * Paints empty state.
     *
     * @param g graphics
     * @param width width in pixels
     * @param height height in pixels
     */
    private void paintEmpty(Graphics2D g, int width, int height) {
        int x = PAD;
        int y = HEADER_H + PAD;
        int w = Math.max(0, width - PAD * 2);
        int h = Math.max(0, height - y - PAD);
        paintGrid(g, x, y, w, h, 3);
        Ui.paintEmptyState(g, new java.awt.Rectangle(x, y, w, h),
                "No analysis yet", "Run Analyze or Search to see evaluation history, depth, and candidate moves.");
    }

    /**
     * Paints all chart bands.
     *
     * @param g graphics
     * @param width width in pixels
     * @param height height in pixels
     */
    private void paintCharts(Graphics2D g, int width, int height) {
        int x = PAD;
        int y = HEADER_H + PAD;
        int w = Math.max(0, width - PAD * 2);
        int h = Math.max(0, height - y - PAD);
        if (w <= 0 || h <= 0) {
            return;
        }
        int evalH = Math.max(78, (int) Math.round(h * 0.46));
        int stackedSpace = Math.max(0, h - evalH - BAND_GAP * 2);
        int depthH = stackedSpace / 2;
        int speedH = stackedSpace - depthH;
        if (depthH < 36 || speedH < 36) {
            evalH = Math.max(42, h / 2);
            stackedSpace = Math.max(0, h - evalH - BAND_GAP * 2);
            depthH = Math.max(1, stackedSpace / 2);
            speedH = Math.max(1, stackedSpace - depthH);
        }

        paintEvalBand(g, x, y, w, evalH);
        paintDepthBand(g, x, y + evalH + BAND_GAP, w, depthH);
        paintSpeedBand(g, x, y + evalH + BAND_GAP + depthH + BAND_GAP, w, speedH);
    }

    /**
     * Paints evaluation band.
     *
     * @param g graphics
     * @param x x-coordinate
     * @param y y-coordinate
     * @param w width
     * @param h height
     */
    private void paintEvalBand(Graphics2D g, int x, int y, int w, int h) {
        paintGrid(g, x, y, w, h, 4);
        paintBandLabel(g, "Evaluation", x, y);
        int plotX = x + PLOT_EDGE_INSET;
        int plotY = y + BAND_LABEL_H;
        int plotW = Math.max(1, w - PLOT_EDGE_INSET * 2);
        int plotH = Math.max(1, h - BAND_LABEL_H - PLOT_EDGE_INSET);
        int range = evalRange();
        int zeroY = evalY(plotY, plotH, 0, range);
        g.setColor(zeroColor());
        g.drawLine(plotX, zeroY, plotX + plotW, zeroY);
        paintAxisLabel(g, formatEvalCp(range), x + w - 8, y + 14, SwingConstants.RIGHT);
        paintAxisLabel(g, "0.00", x + w - 8, zeroY - 3, SwingConstants.RIGHT);
        paintAxisLabel(g, formatEvalCp(-range), x + w - 8, plotY + plotH - 4, SwingConstants.RIGHT);

        if (samples.size() < 2) {
            paintPoint(g, (int) Math.round(sampleX(plotX, plotW, samples.size() - 1)),
                    evalY(plotY, plotH, latest().evalCp(), range), evalLineColor());
            paintValueLabel(g, formatEval(latest()), x + w - 8, y + 28, Math.max(40, w - 16));
            return;
        }
        Path2D.Double line = new Path2D.Double();
        Path2D.Double fill = new Path2D.Double();
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            double px = sampleX(plotX, plotW, i);
            double py = evalY(plotY, plotH, sample.evalCp(), range);
            if (i == 0) {
                line.moveTo(px, py);
                fill.moveTo(px, zeroY);
                fill.lineTo(px, py);
            } else {
                line.lineTo(px, py);
                fill.lineTo(px, py);
            }
        }
        fill.lineTo(sampleX(plotX, plotW, samples.size() - 1), zeroY);
        fill.closePath();

        Graphics2D plot = clippedPlot(g, plotX, plotY, plotW, plotH);
        try {
            plot.setColor(evalFillColor());
            plot.fill(fill);
            plot.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            plot.setColor(evalLineColor());
            plot.draw(line);
            paintPoint(plot, (int) Math.round(sampleX(plotX, plotW, samples.size() - 1)),
                    evalY(plotY, plotH, latest().evalCp(), range), evalLineColor());
        } finally {
            plot.dispose();
        }
        paintValueLabel(g, formatEval(latest()), x + w - 8, y + 28, Math.max(40, w - 16));
    }

    /**
     * Paints the search-depth band.
     *
     * @param g graphics
     * @param x x-coordinate
     * @param y y-coordinate
     * @param w width
     * @param h height
     */
    private void paintDepthBand(Graphics2D g, int x, int y, int w, int h) {
        paintGrid(g, x, y, w, h, 2);
        paintBandLabel(g, "Depth", x, y);
        int plotX = x + PLOT_EDGE_INSET;
        int plotY = y + BAND_LABEL_H;
        int plotW = Math.max(1, w - PLOT_EDGE_INSET * 2);
        int plotH = Math.max(1, h - BAND_LABEL_H - PLOT_EDGE_INSET);
        int maxDepth = Math.max(1, samples.stream().mapToInt(Sample::depth).max().orElse(1));
        double slotW = plotW / (double) Math.max(1, samples.size());

        Graphics2D plot = clippedPlot(g, plotX, plotY, plotW, plotH);
        try {
            plot.setColor(depthBarColor());
            for (int i = 0; i < samples.size(); i++) {
                Sample sample = samples.get(i);
                int barH = (int) Math.round((sample.depth() / (double) maxDepth) * plotH);
                int bx = (int) Math.round(plotX + i * slotW);
                int nextX = (int) Math.round(plotX + (i + 1) * slotW);
                int gap = i == samples.size() - 1 ? 0 : 1;
                int bw = Math.max(1, nextX - bx - gap);
                plot.fillRoundRect(bx, plotY + plotH - barH, bw, barH, 3, 3);
            }
        } finally {
            plot.dispose();
        }
        paintValueLabel(g, latest().depth() + " / " + maxDepth, x + w - 8, y + 14, Math.max(40, w - 96));
    }

    /**
     * Paints the node and speed band.
     *
     * @param g graphics
     * @param x x-coordinate
     * @param y y-coordinate
     * @param w width
     * @param h height
     */
    private void paintSpeedBand(Graphics2D g, int x, int y, int w, int h) {
        paintGrid(g, x, y, w, h, 2);
        paintBandLabel(g, "Nodes / NPS", x, y);
        int plotX = x + PLOT_EDGE_INSET;
        int plotY = y + BAND_LABEL_H;
        int plotW = Math.max(1, w - PLOT_EDGE_INSET * 2);
        int plotH = Math.max(1, h - BAND_LABEL_H - PLOT_EDGE_INSET);
        long maxNodes = Math.max(1L, samples.stream().mapToLong(Sample::nodes).max().orElse(1L));
        long maxNps = Math.max(1L, samples.stream().mapToLong(Sample::nps).max().orElse(1L));
        double slotW = plotW / (double) Math.max(1, samples.size());

        Graphics2D plot = clippedPlot(g, plotX, plotY, plotW, plotH);
        try {
            plot.setColor(nodeBarColor());
            for (int i = 0; i < samples.size(); i++) {
                Sample sample = samples.get(i);
                int barH = (int) Math.round((sample.nodes() / (double) maxNodes) * plotH);
                int bx = (int) Math.round(plotX + i * slotW);
                int nextX = (int) Math.round(plotX + (i + 1) * slotW);
                int gap = i == samples.size() - 1 ? 0 : 1;
                int bw = Math.max(1, nextX - bx - gap);
                plot.fillRoundRect(bx, plotY + plotH - barH, bw, barH, 3, 3);
            }

            if (samples.size() >= 2) {
                Path2D.Double nps = new Path2D.Double();
                for (int i = 0; i < samples.size(); i++) {
                    double px = sampleX(plotX, plotW, i);
                    double py = plotY + plotH - (samples.get(i).nps() / (double) maxNps) * plotH;
                    if (i == 0) {
                        nps.moveTo(px, py);
                    } else {
                        nps.lineTo(px, py);
                    }
                }
                plot.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                plot.setColor(npsLineColor());
                plot.draw(nps);
                int pointY = plotY + plotH
                        - (int) Math.round((latest().nps() / (double) maxNps) * plotH);
                paintPoint(plot, (int) Math.round(sampleX(plotX, plotW, samples.size() - 1)),
                        Math.max(plotY + 4, Math.min(plotY + plotH - 4, pointY)), npsLineColor());
            }
        } finally {
            plot.dispose();
        }
        paintValueLabel(g, formatCount(latest().nodes()) + " / " + formatCount(latest().nps()) + " nps",
                x + w - 8, y + 14, Math.max(44, w - 108));
    }

    /**
     * Creates a clipped graphics copy for data inside a chart plot.
     *
     * @param g source graphics
     * @param x plot x
     * @param y plot y
     * @param w plot width
     * @param h plot height
     * @return clipped graphics
     */
    private static Graphics2D clippedPlot(Graphics2D g, int x, int y, int w, int h) {
        Graphics2D plot = (Graphics2D) g.create();
        plot.clipRect(x, y, Math.max(1, w), Math.max(1, h));
        return plot;
    }

    /**
     * Paints subtle grid lines.
     *
     * @param g graphics
     * @param x x-coordinate
     * @param y y-coordinate
     * @param w width
     * @param h height
     * @param rows row count
     */
    private static void paintGrid(Graphics2D g, int x, int y, int w, int h, int rows) {
        g.setPaint(new GradientPaint(x, y, Theme.withAlpha(Theme.PANEL_SOLID, 220),
                x, (float) y + h, Theme.withAlpha(Theme.ELEVATED_SOLID, 220)));
        g.fillRoundRect(x, y, w, h, 7, 7);
        g.setColor(gridColor());
        for (int i = 0; i <= rows; i++) {
            int yy = y + Math.round(i * h / (float) rows);
            g.drawLine(x, yy, x + w, yy);
        }
        g.setColor(Theme.LINE);
        g.drawRoundRect(x, y, Math.max(0, w - 1), Math.max(0, h - 1), 7, 7);
    }

    /**
     * Returns the active evaluation line color.
     *
     * @return evaluation line color
     */
    private static Color evalLineColor() {
        return Theme.ACCENT;
    }

    /**
     * Returns the active evaluation area fill color.
     *
     * @return evaluation fill color
     */
    private static Color evalFillColor() {
        return Theme.withAlpha(Theme.ACCENT, 34);
    }

    /**
     * Returns the active depth-bar color.
     *
     * @return depth-bar color
     */
    private static Color depthBarColor() {
        return Theme.withAlpha(Theme.STATUS_SUCCESS_TEXT, 142);
    }

    /**
     * Returns the active node-count bar color.
     *
     * @return node-count color
     */
    private static Color nodeBarColor() {
        return Theme.withAlpha(Theme.ACCENT, 92);
    }

    /**
     * Returns the active NPS trend-line color.
     *
     * @return NPS line color
     */
    private static Color npsLineColor() {
        return Theme.STATUS_WARNING_TEXT;
    }

    /**
     * Returns the active grid-line color.
     *
     * @return grid color
     */
    private static Color gridColor() {
        return Theme.withAlpha(Theme.LINE, 150);
    }

    /**
     * Returns the active zero-line color.
     *
     * @return zero-line color
     */
    private static Color zeroColor() {
        return Theme.withAlpha(Theme.ACCENT, 150);
    }

    /**
     * Paints a small band label.
     *
     * @param g graphics
     * @param label display label
     * @param x x-coordinate
     * @param y y-coordinate
     */
    private static void paintBandLabel(Graphics2D g, String label, int x, int y) {
        g.setFont(Theme.font(11, Font.BOLD));
        g.setColor(Theme.MUTED);
        g.drawString(label, x + 8, y + 14);
    }

    /**
     * Paints a right-aligned value label constrained to available width.
     *
     * @param g graphics
     * @param label display label
     * @param x right x
     * @param y baseline y
     * @param maxWidth maximum text width
     */
    private static void paintValueLabel(Graphics2D g, String label, int x, int y, int maxWidth) {
        g.setFont(Theme.mono(11));
        String fitted = Ui.elide(label, g.getFontMetrics(), Math.max(12, maxWidth));
        paintAxisLabel(g, fitted, x, y, SwingConstants.RIGHT);
    }

    /**
     * Paints a small axis label.
     *
     * @param g graphics
     * @param label display label
     * @param x x-coordinate
     * @param y baseline y
     * @param alignment Swing alignment
     */
    private static void paintAxisLabel(Graphics2D g, String label, int x, int y, int alignment) {
        g.setFont(Theme.mono(11));
        g.setColor(Theme.MUTED);
        int drawX = alignment == SwingConstants.RIGHT ? x - g.getFontMetrics().stringWidth(label) : x;
        g.drawString(label, drawX, y);
    }

    /**
     * Paints latest point.
     *
     * @param g graphics
     * @param x x-coordinate
     * @param y y-coordinate
     * @param color display color
     */
    private static void paintPoint(Graphics2D g, int x, int y, Color color) {
        g.setColor(Theme.withAlpha(color, 50));
        g.fillOval(x - 6, y - 6, 12, 12);
        g.setColor(color);
        g.fillOval(x - 3, y - 3, 6, 6);
    }

    /**
     * Returns latest sample.
     *
     * @return latest sample
     */
    private Sample latest() {
        return samples.get(samples.size() - 1);
    }

    /**
     * Returns whether a candidate repeats the latest stored point.
     *
     * @param candidate candidate sample
     * @return true when unchanged
     */
    private boolean isDuplicate(Sample candidate) {
        return !samples.isEmpty() && latest().samePoint(candidate);
    }

    /**
     * Adds one graph sample after duplicate filtering.
     *
     * @param sample candidate sample
     * @return true when stored
     */
    private boolean addSample(Sample sample) {
        if (sample == null || isDuplicate(sample)) {
            return false;
        }
        samples.add(sample);
        while (samples.size() > MAX_SAMPLES) {
            samples.remove(0);
        }
        firePropertyChange("analysisSamples", null, Integer.valueOf(samples.size()));
        repaint();
        return true;
    }

    /**
     * Returns x coordinate for a sample index.
     *
     * @param x chart x
     * @param w chart width
     * @param index sample index
     * @return x coordinate
     */
    private double sampleX(int x, int w, int index) {
        int span = Math.max(0, w - 1);
        if (samples.size() <= 1) {
            return (double) x + span;
        }
        return x + (index / (double) (samples.size() - 1)) * span;
    }

    /**
     * Returns eval y coordinate.
     *
     * @param y chart y
     * @param h chart height
     * @param cp centipawns
     * @param range visible centipawn range
     * @return y coordinate
     */
    private static int evalY(int y, int h, int cp, int range) {
        int clamped = Math.max(-range, Math.min(range, cp));
        double normalized = (range - clamped) / (double) (range * 2);
        return y + (int) Math.round(normalized * h);
    }

    /**
     * Returns dynamic eval range.
     *
     * @return centipawn range
     */
    private int evalRange() {
        int max = samples.stream().mapToInt(sample -> Math.abs(sample.evalCp())).max().orElse(MIN_EVAL_RANGE_CP);
        return Math.max(MIN_EVAL_RANGE_CP, Math.min(MAX_EVAL_RANGE_CP, max + 80));
    }

    /**
     * Returns latest summary text.
     *
     * @return summary
     */
    private String latestSummary() {
        Sample latest = latest();
        return "eval " + formatEval(latest)
                + "   depth " + latest.depth()
                + "   nodes " + formatCount(latest.nodes())
                + "   nps " + formatCount(latest.nps())
                + (latest.bestMove() == Move.NO_MOVE ? "" : "   best " + Move.toString(latest.bestMove()))
                + "   samples " + samples.size();
    }

    /**
     * Returns compact statistics text.
     *
     * @return summary text
     */
    private String statsSummary() {
        Stats stats = stats();
        return "range " + formatEvalCp(stats.minEvalCp()) + ".." + formatEvalCp(stats.maxEvalCp())
                + "   swing " + formatEvalCp(Math.abs(stats.maxEvalCp() - stats.minEvalCp()))
                + "   avg depth " + String.format(Locale.ROOT, "%.1f", stats.avgDepth())
                + "   max nps " + formatCount(stats.maxNps());
    }

    /**
     * Returns compact position label.
     *
     * @return compact FEN
     */
    private String compactPosition() {
        if (position == null || position.isBlank()) {
            return "";
        }
        int firstSpace = position.indexOf(' ');
        return firstSpace < 0 ? position : position.substring(0, firstSpace);
    }

    /**
     * Creates a sample from engine output.
     *
     * @param whiteToMove true when White was to move
     * @param output engine output
     * @param bestMove source best move
     * @return graph sample or null when output is not plottable
     */
    private Sample sampleFrom(boolean whiteToMove, Output output, short bestMove) {
        if (!output.hasContent()) {
            return null;
        }
        Integer evalCp = output.hasEvaluation()
                ? Integer.valueOf(whiteCentipawns(whiteToMove, output.getEvaluation()))
                : lastEvalCp;
        if (evalCp == null) {
            return null;
        }
        int depth = output.hasDepth() ? output.getDepth() : lastDepth;
        long nodes = output.hasNodes() && output.getNodes() > 0L ? output.getNodes() : lastNodes;
        long nps = output.hasNodesPerSecond() && output.getNodesPerSecond() > 0L
                ? output.getNodesPerSecond()
                : lastNps;
        long timeMs = output.hasTime() && output.getTime() > 0L ? output.getTime() : lastTimeMs;
        rememberValues(evalCp, depth, nodes, nps, timeMs);
    return new Sample(evalCp, depth, nodes, nps, timeMs, bestMove);
    }

    /**
     * Creates a graph sample from completed CLI engine output.
     *
     * @param whiteToMove true when White was to move
     * @param output command output
     * @return graph sample or null when output is not plottable
     */
    private Sample sampleFromCommandOutput(boolean whiteToMove, String output) {
        if (output == null || output.isBlank()) {
            return null;
        }
        Integer eval = summaryEvaluation(output);
        if (eval == null) {
            eval = uciEvaluation(output);
        }
        if (eval == null) {
            return null;
        }
        int evalCp = whiteToMove ? eval.intValue() : -eval.intValue();
        int depth = firstInt(SUMMARY_DEPTH_PATTERN, output, lastTokenInt(output, "depth", lastDepth));
        long nodes = firstWorkCount(output, 1, lastTokenCount(output, "nodes", lastNodes));
        long nps = firstWorkCount(output, 2, lastTokenCount(output, "nps", lastNps));
        long timeMs = firstWorkDuration(output, 3, lastTokenDuration(output, "time", lastTimeMs));
        short bestMove = firstMove(SUMMARY_BEST_PATTERN, output, firstMove(UCI_BESTMOVE_PATTERN, output, Move.NO_MOVE));
        rememberValues(evalCp, depth, nodes, nps, timeMs);
        return new Sample(evalCp, depth, nodes, nps, timeMs, bestMove);
    }

    /**
     * Parses the first formatted summary evaluation.
     *
     * @param output command output
     * @return side-to-move centipawns, or null
     */
    private static Integer summaryEvaluation(String output) {
        Matcher matcher = SUMMARY_EVAL_PATTERN.matcher(output);
        if (!matcher.find()) {
            return null;
        }
        return evalTokenCentipawns(matcher.group(1));
    }

    /**
     * Parses the last raw UCI score token in command output.
     *
     * @param output command output
     * @return side-to-move centipawns, or null
     */
    private static Integer uciEvaluation(String output) {
        Integer result = null;
        Matcher matcher = UCI_SCORE_PATTERN.matcher(output);
        while (matcher.find()) {
            int value = parseInt(matcher.group(2), 0);
            result = "mate".equals(matcher.group(1))
                    ? Integer.valueOf(value >= 0 ? MATE_CP : -MATE_CP)
                    : Integer.valueOf(value);
        }
        return result;
    }

    /**
     * Converts one formatted eval token to plottable centipawns.
     *
     * @param token eval token
     * @return centipawns, or null
     */
    private static Integer evalTokenCentipawns(String token) {
        if (token == null || token.isBlank() || "-".equals(token)) {
            return null;
        }
        try {
            if (token.startsWith("#")) {
                int mate = Integer.parseInt(token.substring(1));
                return Integer.valueOf(mate >= 0 ? MATE_CP : -MATE_CP);
            }
            return Integer.valueOf(Integer.parseInt(token));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Reads the first integer captured by a pattern.
     *
     * @param pattern pattern with an integer group 1
     * @param text input text
     * @param fallback default used when input is absent or invalid
     * @return parsed value
     */
    private static int firstInt(Pattern pattern, String text, int fallback) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? parseInt(matcher.group(1), fallback) : fallback;
    }

    /**
     * Reads one count from the formatted work line.
     *
     * @param text command output
     * @param group regex group
     * @param fallback default used when input is absent or invalid
     * @return parsed count
     */
    private static long firstWorkCount(String text, int group, long fallback) {
        Matcher matcher = SUMMARY_WORK_PATTERN.matcher(text);
        return matcher.find() ? parseCount(matcher.group(group), fallback) : fallback;
    }

    /**
     * Reads one duration from the formatted work line.
     *
     * @param text command output
     * @param group regex group
     * @param fallback default used when input is absent or invalid
     * @return parsed duration in milliseconds
     */
    private static long firstWorkDuration(String text, int group, long fallback) {
        Matcher matcher = SUMMARY_WORK_PATTERN.matcher(text);
        return matcher.find() ? parseDurationMillis(matcher.group(group), fallback) : fallback;
    }

    /**
     * Reads the last count-like token following a marker word.
     *
     * @param text command output
     * @param marker marker word
     * @param fallback default used when input is absent or invalid
     * @return parsed count
     */
    private static long lastTokenCount(String text, String marker, long fallback) {
        String token = lastTokenAfter(text, marker);
        return token.isEmpty() ? fallback : parseCount(token, fallback);
    }

    /**
     * Reads the last integer token following a marker word.
     *
     * @param text command output
     * @param marker marker word
     * @param fallback default used when input is absent or invalid
     * @return parsed integer
     */
    private static int lastTokenInt(String text, String marker, int fallback) {
        String token = lastTokenAfter(text, marker);
        return token.isEmpty() ? fallback : parseInt(token, fallback);
    }

    /**
     * Reads the last duration token following a marker word.
     *
     * @param text command output
     * @param marker marker word
     * @param fallback default used when input is absent or invalid
     * @return parsed duration in milliseconds
     */
    private static long lastTokenDuration(String text, String marker, long fallback) {
        String token = lastTokenAfter(text, marker);
        return token.isEmpty() ? fallback : parseDurationMillis(token, fallback);
    }

    /**
     * Finds the last token following one marker.
     *
     * @param text command output
     * @param marker marker word
     * @return token, or empty
     */
    private static String lastTokenAfter(String text, String marker) {
        String result = "";
        for (String line : text.split("\\R")) {
            String[] parts = line.trim().split("\\s+");
            for (int i = 0; i < parts.length - 1; i++) {
                if (marker.equals(parts[i])) {
                    result = parts[i + 1];
                }
            }
        }
        return result;
    }

    /**
     * Parses a move captured by a pattern.
     *
     * @param pattern pattern with move group 1
     * @param text input text
     * @param fallback fallback move
     * @return parsed move or fallback
     */
    private static short firstMove(Pattern pattern, String text, short fallback) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return fallback;
        }
        String token = matcher.group(1);
        return Move.isMove(token) ? Move.parse(token) : fallback;
    }

    /**
     * Parses an integer token.
     *
     * @param token input token
     * @param fallback default used when input is absent or invalid
     * @return parsed integer
     */
    private static int parseInt(String token, int fallback) {
        try {
            return Integer.parseInt(stripNumericToken(token));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * Parses a count token, including comma-separated and compact suffix forms.
     *
     * @param token input token
     * @param fallback default used when input is absent or invalid
     * @return parsed count
     */
    private static long parseCount(String token, long fallback) {
        String compact = stripNumericToken(token);
        if (compact.isEmpty()) {
            return fallback;
        }
        char suffix = Character.toLowerCase(compact.charAt(compact.length() - 1));
        long multiplier = switch (suffix) {
            case 'k' -> 1_000L;
            case 'm' -> 1_000_000L;
            case 'b' -> 1_000_000_000L;
            default -> 1L;
        };
        if (multiplier != 1L) {
            compact = compact.substring(0, compact.length() - 1);
        }
        try {
            return Math.max(0L, Math.round(Double.parseDouble(compact) * multiplier));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * Parses a duration token into milliseconds.
     *
     * @param token input token
     * @param fallback default used when input is absent or invalid
     * @return parsed milliseconds
     */
    private static long parseDurationMillis(String token, long fallback) {
        String compact = token == null ? "" : token.trim().toLowerCase(Locale.ROOT).replace(",", "");
        if (compact.isEmpty()) {
            return fallback;
        }
        if (compact.endsWith("ms")) {
            return parseScaledDuration(compact.substring(0, compact.length() - 2), 1.0, fallback);
        }
        if (compact.endsWith("s")) {
            return parseScaledDuration(compact.substring(0, compact.length() - 1), 1_000.0, fallback);
        }
        return parseScaledDuration(compact, 1.0, fallback);
    }

    /**
     * Parses one scaled decimal duration.
     *
     * @param token numeric token
     * @param scale scale factor
     * @param fallback default used when input is absent or invalid
     * @return scaled long
     */
    private static long parseScaledDuration(String token, double scale, long fallback) {
        try {
            return Math.max(0L, Math.round(Double.parseDouble(token) * scale));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * Removes punctuation that appears in formatted numeric tokens.
     *
     * @param token input token
     * @return compact token
     */
    private static String stripNumericToken(String token) {
        return token == null ? "" : token.trim().replace(",", "").replace("_", "");
    }

    /**
     * Stores values used to smooth sparse engine output.
     *
     * @param evalCp eval in centipawns
     * @param depth search depth
     * @param nodes source nodes
     * @param nps nodes per second
     * @param timeMs time in milliseconds
     */
    private void rememberValues(int evalCp, int depth, long nodes, long nps, long timeMs) {
        lastEvalCp = evalCp;
        lastDepth = depth;
        lastNodes = nodes;
        lastNps = nps;
        lastTimeMs = timeMs;
    }

    /**
     * Resets carried output values.
     */
    private void resetCarriedValues() {
        lastEvalCp = null;
        lastDepth = 0;
        lastNodes = 0L;
        lastNps = 0L;
        lastTimeMs = 0L;
    }

    /**
     * Computes summary statistics.
     *
     * @return statistics
     */
    private Stats stats() {
        int minEval = Integer.MAX_VALUE;
        int maxEval = Integer.MIN_VALUE;
        int maxDepth = 0;
        long maxNodes = 0L;
        long maxNps = 0L;
        long totalDepth = 0L;
        long totalNps = 0L;
        for (Sample sample : samples) {
            minEval = Math.min(minEval, sample.evalCp());
            maxEval = Math.max(maxEval, sample.evalCp());
            maxDepth = Math.max(maxDepth, sample.depth());
            maxNodes = Math.max(maxNodes, sample.nodes());
            maxNps = Math.max(maxNps, sample.nps());
            totalDepth += sample.depth();
            totalNps += sample.nps();
        }
        int count = Math.max(1, samples.size());
    return new Stats(minEval, maxEval, maxDepth, totalDepth / (double) count,
                maxNodes, maxNps, totalNps / count);
    }

    /**
     * Paints printable report text.
     *
     * @param g graphics
     * @param width printable width
     */
    private void paintReportText(Graphics2D g, int width) {
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(13, Font.BOLD));
        g.drawString("CRTK Workbench Analysis Report", 0, 14);
        g.setFont(Theme.mono(9));
        String[] lines = reportText().split("\\R");
        int y = 34;
        FontMetrics metrics = g.getFontMetrics();
        for (String line : lines) {
            String visible = Ui.elide(line, metrics, Math.max(0, width));
            g.drawString(visible, 0, y);
            y += 12;
        }
    }

    /**
     * Paints a graph snapshot onto the report page.
     *
     * @param g graphics
     * @param width printable width
     * @param height printable height
     */
    private void paintReportGraph(Graphics2D g, int width, int height) {
        BufferedImage image = new BufferedImage(PRINT_GRAPH_W, PRINT_GRAPH_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D imageGraphics = image.createGraphics();
        try {
            imageGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintSurface(imageGraphics, PRINT_GRAPH_W, PRINT_GRAPH_H);
        } finally {
            imageGraphics.dispose();
        }
        int top = Math.min(Math.max(150, height / 3), Math.max(0, height - 180));
        int availableH = Math.max(80, height - top);
        double scale = Math.min(width / (double) PRINT_GRAPH_W, availableH / (double) PRINT_GRAPH_H);
        int graphW = Math.max(1, (int) Math.round(PRINT_GRAPH_W * scale));
        int graphH = Math.max(1, (int) Math.round(PRINT_GRAPH_H * scale));
        g.drawImage(image, 0, top, graphW, graphH, null);
    }

    /**
     * Converts an engine evaluation to White-relative centipawns.
     *
     * @param whiteToMove true when White was to move
     * @param evaluation engine evaluation
     * @return White-relative centipawns
     */
    private static int whiteCentipawns(boolean whiteToMove, Evaluation evaluation) {
        if (evaluation == null || !evaluation.isValid()) {
            return 0;
        }
        int value = evaluation.isMate()
                ? (evaluation.getValue() >= 0 ? MATE_CP : -MATE_CP)
                : evaluation.getValue();
        return whiteToMove ? value : -value;
    }

    /**
     * Formats a sample evaluation.
     *
     * @param sample sample row
     * @return label
     */
    private static String formatEval(Sample sample) {
        if (Math.abs(sample.evalCp()) >= MATE_CP) {
            return sample.evalCp() > 0 ? "#+" : "#-";
        }
    return formatEvalCp(sample.evalCp());
    }

    /**
     * Formats centipawns.
     *
     * @param cp centipawns
     * @return label
     */
    private static String formatEvalCp(int cp) {
        return (cp > 0 ? "+" : "") + String.format(Locale.ROOT, "%.2f", cp / 100.0);
    }

    /**
     * Formats a count.
     *
     * @param value candidate value
     * @return compact value
     */
    private static String formatCount(long value) {
        if (value >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
        }
        if (value >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (value >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        }
        return Long.toString(value);
    }

    /**
     * Returns a move label.
     *
     * @param move encoded chess move
     * @return move label
     */
    private static String moveLabel(short move) {
        return move == Move.NO_MOVE ? "" : Move.toString(move);
    }

    /**
     * One graph sample.
     *
     * @param evalCp White-relative centipawns
     * @param depth search depth
     * @param nodes searched nodes
     * @param nps nodes per second
     * @param timeMs search time in milliseconds
     * @param bestMove source best move
     */
    private record Sample(int evalCp, int depth, long nodes, long nps, long timeMs, short bestMove) {

        /**
         * Returns whether another point carries the same data.
         *
         * @param other other sample
         * @return true when equal for graphing
         */
    boolean samePoint(Sample other) {
            return evalCp == other.evalCp
                    && depth == other.depth
                    && nodes == other.nodes
                    && nps == other.nps
                    && timeMs == other.timeMs
                    && bestMove == other.bestMove;
        }
    }

    /**
     * Summary statistics.
     *
     * @param minEvalCp minimum eval
     * @param maxEvalCp maximum eval
     * @param maxDepth maximum depth
     * @param avgDepth average depth
     * @param maxNodes maximum nodes
     * @param maxNps maximum NPS
     * @param avgNps average NPS
     */
    private record Stats(
            int minEvalCp,
            int maxEvalCp,
            int maxDepth,
            double avgDepth,
            long maxNodes,
            long maxNps,
            long avgNps) {
    }
}
