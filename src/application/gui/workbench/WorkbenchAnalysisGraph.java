package application.gui.workbench;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
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

import javax.swing.JComponent;
import javax.swing.SwingConstants;

import chess.core.Move;
import chess.uci.Evaluation;
import chess.uci.Output;

/**
 * Compact live-engine data visualization and report source.
 */
final class WorkbenchAnalysisGraph extends JComponent {

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
    private static final int PAD = 14;

    /**
     * Header height.
     */
    private static final int HEADER_H = 62;

    /**
     * Gap between chart bands.
     */
    private static final int BAND_GAP = 8;

    /**
     * Printable graph snapshot width.
     */
    private static final int PRINT_GRAPH_W = 900;

    /**
     * Printable graph snapshot height.
     */
    private static final int PRINT_GRAPH_H = 520;

    /**
     * Evaluation line color.
     */
    private static final Color EVAL_LINE = new Color(48, 111, 156);

    /**
     * Evaluation fill color.
     */
    private static final Color EVAL_FILL = new Color(48, 111, 156, 34);

    /**
     * Depth bar color.
     */
    private static final Color DEPTH_BAR = new Color(64, 156, 104, 142);

    /**
     * Node bar color.
     */
    private static final Color NODE_BAR = new Color(73, 123, 164, 92);

    /**
     * NPS trend color.
     */
    private static final Color NPS_LINE = new Color(190, 122, 42);

    /**
     * Grid line color.
     */
    private static final Color GRID = WorkbenchTheme.withAlpha(WorkbenchTheme.LINE, 150);

    /**
     * Zero line color.
     */
    private static final Color ZERO = WorkbenchTheme.withAlpha(WorkbenchTheme.ACCENT, 150);

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
    WorkbenchAnalysisGraph() {
        setOpaque(true);
        setBackground(WorkbenchTheme.ELEVATED_SOLID);
        setForeground(WorkbenchTheme.TEXT);
        setPreferredSize(new Dimension(420, 360));
        setMinimumSize(new Dimension(320, 260));
        setToolTipText("Live engine analysis data");
    }

    /**
     * Resets graph data for a new visible position.
     *
     * @param fen position FEN
     */
    void resetForPosition(String fen) {
        samples.clear();
        position = fen == null ? "" : fen;
        resetCarriedValues();
        repaint();
    }

    /**
     * Clears all graph samples.
     */
    void clearSamples() {
        samples.clear();
        resetCarriedValues();
        repaint();
    }

    /**
     * Adds one live engine sample.
     *
     * @param whiteToMove true when White was to move in the analyzed position
     * @param output engine output
     * @param bestMove current best move
     */
    void addSample(boolean whiteToMove, Output output, short bestMove) {
        if (output == null) {
            return;
        }
        Sample sample = sampleFrom(whiteToMove, output, bestMove);
        if (sample == null || isDuplicate(sample)) {
            return;
        }
        samples.add(sample);
        while (samples.size() > MAX_SAMPLES) {
            samples.remove(0);
        }
        repaint();
    }

    /**
     * Returns sample count.
     *
     * @return sample count
     */
    int sampleCount() {
        return samples.size();
    }

    /**
     * Returns the latest eval label.
     *
     * @return latest eval label or blank
     */
    String latestEvalLabel() {
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
    boolean hasSamples() {
        return !samples.isEmpty();
    }

    /**
     * Builds a CSV export for downstream data analysis.
     *
     * @return CSV text
     */
    String csvText() {
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
    String reportText() {
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
    boolean printReport() throws PrinterException {
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
     * @param width width
     * @param height height
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
     * @param graphics graphics
     * @param page page format
     * @param pageIndex page index
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
     * @param width width
     * @param height height
     */
    private void paintBackground(Graphics2D g, int width, int height) {
        g.setColor(getBackground());
        g.fillRect(0, 0, width, height);
    }

    /**
     * Paints header metrics.
     *
     * @param g graphics
     * @param width width
     */
    private void paintHeader(Graphics2D g, int width) {
        g.setFont(WorkbenchTheme.font(12, Font.BOLD));
        g.setColor(WorkbenchTheme.TEXT);
        g.drawString("Analysis Data", PAD, 20);

        g.setFont(WorkbenchTheme.mono(11));
        g.setColor(WorkbenchTheme.MUTED);
        String detail = samples.isEmpty() ? compactPosition() : latestSummary();
        g.drawString(WorkbenchUi.elide(detail, g.getFontMetrics(), Math.max(0, width - PAD * 2)), PAD, 38);
        if (!samples.isEmpty()) {
            g.drawString(WorkbenchUi.elide(statsSummary(), g.getFontMetrics(), Math.max(0, width - PAD * 2)),
                    PAD, 54);
        }
    }

    /**
     * Paints empty state.
     *
     * @param g graphics
     * @param width width
     * @param height height
     */
    private void paintEmpty(Graphics2D g, int width, int height) {
        int x = PAD;
        int y = HEADER_H + PAD;
        int w = Math.max(0, width - PAD * 2);
        int h = Math.max(0, height - y - PAD);
        paintGrid(g, x, y, w, h, 3);
        g.setFont(WorkbenchTheme.font(12, Font.PLAIN));
        g.setColor(WorkbenchTheme.MUTED);
        String text = "No analysis data";
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, x + Math.max(0, (w - metrics.stringWidth(text)) / 2),
                y + Math.max(metrics.getAscent(), (h + metrics.getAscent()) / 2));
    }

    /**
     * Paints all chart bands.
     *
     * @param g graphics
     * @param width width
     * @param height height
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
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     */
    private void paintEvalBand(Graphics2D g, int x, int y, int w, int h) {
        paintGrid(g, x, y, w, h, 4);
        paintBandLabel(g, "Evaluation", x, y);
        int range = evalRange();
        int zeroY = evalY(y, h, 0, range);
        g.setColor(ZERO);
        g.drawLine(x, zeroY, x + w, zeroY);
        paintAxisLabel(g, formatEvalCp(range), x + w - 8, y + 14, SwingConstants.RIGHT);
        paintAxisLabel(g, "0.00", x + w - 8, zeroY - 3, SwingConstants.RIGHT);
        paintAxisLabel(g, formatEvalCp(-range), x + w - 8, y + h - 4, SwingConstants.RIGHT);

        if (samples.size() < 2) {
            paintPoint(g, x + w, evalY(y, h, latest().evalCp(), range), EVAL_LINE);
            paintValueLabel(g, formatEval(latest()), x + w - 8, y + 28);
            return;
        }
        Path2D.Double line = new Path2D.Double();
        Path2D.Double fill = new Path2D.Double();
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            double px = sampleX(x, w, i);
            double py = evalY(y, h, sample.evalCp(), range);
            if (i == 0) {
                line.moveTo(px, py);
                fill.moveTo(px, zeroY);
                fill.lineTo(px, py);
            } else {
                line.lineTo(px, py);
                fill.lineTo(px, py);
            }
        }
        fill.lineTo(sampleX(x, w, samples.size() - 1), zeroY);
        fill.closePath();

        g.setColor(EVAL_FILL);
        g.fill(fill);
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(EVAL_LINE);
        g.draw(line);
        paintPoint(g, x + w, evalY(y, h, latest().evalCp(), range), EVAL_LINE);
        paintValueLabel(g, formatEval(latest()), x + w - 8, y + 28);
    }

    /**
     * Paints the search-depth band.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     */
    private void paintDepthBand(Graphics2D g, int x, int y, int w, int h) {
        paintGrid(g, x, y, w, h, 2);
        paintBandLabel(g, "Depth", x, y);
        int maxDepth = Math.max(1, samples.stream().mapToInt(Sample::depth).max().orElse(1));
        double barW = Math.max(2.0, w / (double) Math.max(1, samples.size()));

        g.setColor(DEPTH_BAR);
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            int barH = (int) Math.round((sample.depth() / (double) maxDepth) * Math.max(1, h - 8));
            int bx = (int) Math.round(sampleX(x, w, i) - barW / 2.0);
            g.fillRoundRect(bx, y + h - barH, Math.max(1, (int) Math.round(barW - 1)), barH, 3, 3);
        }
        paintValueLabel(g, latest().depth() + " / " + maxDepth, x + w - 8, y + 14);
    }

    /**
     * Paints the node and speed band.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     */
    private void paintSpeedBand(Graphics2D g, int x, int y, int w, int h) {
        paintGrid(g, x, y, w, h, 2);
        paintBandLabel(g, "Nodes / NPS", x, y);
        long maxNodes = Math.max(1L, samples.stream().mapToLong(Sample::nodes).max().orElse(1L));
        long maxNps = Math.max(1L, samples.stream().mapToLong(Sample::nps).max().orElse(1L));
        double barW = Math.max(2.0, w / (double) Math.max(1, samples.size()));

        g.setColor(NODE_BAR);
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            int barH = (int) Math.round((sample.nodes() / (double) maxNodes) * Math.max(1, h - 8));
            int bx = (int) Math.round(sampleX(x, w, i) - barW / 2.0);
            g.fillRoundRect(bx, y + h - barH, Math.max(1, (int) Math.round(barW - 1)), barH, 3, 3);
        }

        if (samples.size() >= 2) {
            Path2D.Double nps = new Path2D.Double();
            for (int i = 0; i < samples.size(); i++) {
                double px = sampleX(x, w, i);
                double py = y + h - (samples.get(i).nps() / (double) maxNps) * Math.max(1, h - 8);
                if (i == 0) {
                    nps.moveTo(px, py);
                } else {
                    nps.lineTo(px, py);
                }
            }
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(NPS_LINE);
            g.draw(nps);
            int pointY = y + h - (int) Math.round((latest().nps() / (double) maxNps) * Math.max(1, h - 8));
            paintPoint(g, x + w, Math.max(y + 4, Math.min(y + h - 4, pointY)), NPS_LINE);
        }
        paintValueLabel(g, formatCount(latest().nodes()) + " / " + formatCount(latest().nps()) + " nps",
                x + w - 8, y + 14);
    }

    /**
     * Paints subtle grid lines.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     * @param rows row count
     */
    private static void paintGrid(Graphics2D g, int x, int y, int w, int h, int rows) {
        g.setPaint(new GradientPaint(x, y, WorkbenchTheme.withAlpha(WorkbenchTheme.PANEL_SOLID, 220),
                x, (float) y + h, WorkbenchTheme.withAlpha(WorkbenchTheme.ELEVATED_SOLID, 220)));
        g.fillRoundRect(x, y, w, h, 7, 7);
        g.setColor(GRID);
        for (int i = 0; i <= rows; i++) {
            int yy = y + Math.round(i * h / (float) rows);
            g.drawLine(x, yy, x + w, yy);
        }
        g.setColor(WorkbenchTheme.LINE);
        g.drawRoundRect(x, y, Math.max(0, w - 1), Math.max(0, h - 1), 7, 7);
    }

    /**
     * Paints a small band label.
     *
     * @param g graphics
     * @param label label
     * @param x x
     * @param y y
     */
    private static void paintBandLabel(Graphics2D g, String label, int x, int y) {
        g.setFont(WorkbenchTheme.font(10, Font.BOLD));
        g.setColor(WorkbenchTheme.MUTED);
        g.drawString(label, x + 8, y + 14);
    }

    /**
     * Paints a right-aligned value label.
     *
     * @param g graphics
     * @param label label
     * @param x right x
     * @param y baseline y
     */
    private static void paintValueLabel(Graphics2D g, String label, int x, int y) {
        paintAxisLabel(g, label, x, y, SwingConstants.RIGHT);
    }

    /**
     * Paints a small axis label.
     *
     * @param g graphics
     * @param label label
     * @param x x
     * @param y baseline y
     * @param alignment Swing alignment
     */
    private static void paintAxisLabel(Graphics2D g, String label, int x, int y, int alignment) {
        g.setFont(WorkbenchTheme.mono(10));
        g.setColor(WorkbenchTheme.MUTED);
        int drawX = alignment == SwingConstants.RIGHT ? x - g.getFontMetrics().stringWidth(label) : x;
        g.drawString(label, drawX, y);
    }

    /**
     * Paints latest point.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param color color
     */
    private static void paintPoint(Graphics2D g, int x, int y, Color color) {
        g.setColor(WorkbenchTheme.withAlpha(color, 50));
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
     * Returns x coordinate for a sample index.
     *
     * @param x chart x
     * @param w chart width
     * @param index sample index
     * @return x coordinate
     */
    private double sampleX(int x, int w, int index) {
        if (samples.size() <= 1) {
            return (double) x + w;
        }
        return x + (index / (double) (samples.size() - 1)) * w;
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
     * @param bestMove best move
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
     * Stores values used to smooth sparse engine output.
     *
     * @param evalCp eval in centipawns
     * @param depth depth
     * @param nodes nodes
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
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString("CRTK Workbench Analysis Report", 0, 14);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 9));
        String[] lines = reportText().split("\\R");
        int y = 34;
        FontMetrics metrics = g.getFontMetrics();
        for (String line : lines) {
            String visible = WorkbenchUi.elide(line, metrics, Math.max(0, width));
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
     * @param sample sample
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
     * @param value value
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
     * @param move move
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
     * @param bestMove best move
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
