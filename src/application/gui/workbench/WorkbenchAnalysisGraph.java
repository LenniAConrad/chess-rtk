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
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import chess.core.Move;
import chess.uci.Evaluation;
import chess.uci.Output;

/**
 * Compact analysis data visualization for live engine output.
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
    private static final int HEADER_H = 44;

    /**
     * Gap between chart bands.
     */
    private static final int BAND_GAP = 14;

    /**
     * Evaluation line color.
     */
    private static final Color EVAL_LINE = new Color(48, 111, 156);

    /**
     * Evaluation fill color.
     */
    private static final Color EVAL_FILL = new Color(48, 111, 156, 42);

    /**
     * Depth bar color.
     */
    private static final Color DEPTH_BAR = new Color(47, 143, 78, 132);

    /**
     * NPS trend color.
     */
    private static final Color NPS_LINE = new Color(168, 111, 45);

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
     * Creates a graph.
     */
    WorkbenchAnalysisGraph() {
        setOpaque(true);
        setBackground(WorkbenchTheme.ELEVATED_SOLID);
        setForeground(WorkbenchTheme.TEXT);
        setPreferredSize(new Dimension(360, 260));
        setMinimumSize(new Dimension(280, 200));
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
        repaint();
    }

    /**
     * Clears all graph samples.
     */
    void clearSamples() {
        samples.clear();
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
        samples.add(sampleFrom(whiteToMove, output, bestMove));
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
     * Paints graph surface.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintBackground(g);
            paintHeader(g);
            if (samples.isEmpty()) {
                paintEmpty(g);
            } else {
                paintCharts(g);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints solid background.
     *
     * @param g graphics
     */
    private void paintBackground(Graphics2D g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    /**
     * Paints header metrics.
     *
     * @param g graphics
     */
    private void paintHeader(Graphics2D g) {
        g.setFont(WorkbenchTheme.font(12, Font.BOLD));
        g.setColor(WorkbenchTheme.TEXT);
        g.drawString("Analysis Data", PAD, 20);

        g.setFont(WorkbenchTheme.mono(11));
        g.setColor(WorkbenchTheme.MUTED);
        String detail = samples.isEmpty() ? compactPosition() : latestSummary();
        g.drawString(WorkbenchUi.elide(detail, g.getFontMetrics(), Math.max(0, getWidth() - PAD * 2)), PAD, 38);
    }

    /**
     * Paints empty state.
     *
     * @param g graphics
     */
    private void paintEmpty(Graphics2D g) {
        int x = PAD;
        int y = HEADER_H + PAD;
        int w = Math.max(0, getWidth() - PAD * 2);
        int h = Math.max(0, getHeight() - y - PAD);
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
     */
    private void paintCharts(Graphics2D g) {
        int x = PAD;
        int y = HEADER_H + PAD;
        int w = Math.max(0, getWidth() - PAD * 2);
        int h = Math.max(0, getHeight() - y - PAD);
        int evalH = Math.max(80, (int) (h * 0.62));
        int metricY = y + evalH + BAND_GAP;
        int metricH = Math.max(44, h - evalH - BAND_GAP);

        paintEvalBand(g, x, y, w, evalH);
        paintMetricBand(g, x, metricY, w, metricH);
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
        int range = evalRange();
        int zeroY = evalY(y, h, 0, range);
        g.setColor(ZERO);
        g.drawLine(x, zeroY, x + w, zeroY);

        if (samples.size() < 2) {
            paintPoint(g, x + w, evalY(y, h, latest().evalCp(), range), EVAL_LINE);
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
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(EVAL_LINE);
        g.draw(line);
        paintPoint(g, x + w, evalY(y, h, latest().evalCp(), range), EVAL_LINE);
        paintBandLabel(g, "Eval", x, y);
    }

    /**
     * Paints depth and speed band.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     */
    private void paintMetricBand(Graphics2D g, int x, int y, int w, int h) {
        paintGrid(g, x, y, w, h, 3);
        int maxDepth = Math.max(1, samples.stream().mapToInt(Sample::depth).max().orElse(1));
        long maxNps = Math.max(1L, samples.stream().mapToLong(Sample::nps).max().orElse(1L));
        double barW = Math.max(2.0, w / (double) Math.max(1, samples.size()));

        g.setColor(DEPTH_BAR);
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            int barH = (int) Math.round((sample.depth() / (double) maxDepth) * Math.max(1, h - 8));
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
        }
        paintBandLabel(g, "Depth / NPS", x, y);
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
                + "   nps " + formatCount(latest.nps())
                + (latest.bestMove() == Move.NO_MOVE ? "" : "   " + Move.toString(latest.bestMove()));
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
     * @return graph sample
     */
    private static Sample sampleFrom(boolean whiteToMove, Output output, short bestMove) {
        int evalCp = whiteCentipawns(whiteToMove, output.getEvaluation());
        return new Sample(evalCp, output.getDepth(), output.getNodesPerSecond(), bestMove);
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
        int cp = sample.evalCp();
        return (cp > 0 ? "+" : "") + String.format(java.util.Locale.ROOT, "%.2f", cp / 100.0);
    }

    /**
     * Formats a count.
     *
     * @param value value
     * @return compact value
     */
    private static String formatCount(long value) {
        if (value >= 1_000_000_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
        }
        if (value >= 1_000_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (value >= 1_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000.0);
        }
        return Long.toString(value);
    }

    /**
     * One graph sample.
     *
     * @param evalCp White-relative centipawns
     * @param depth search depth
     * @param nps nodes per second
     * @param bestMove best move
     */
    private record Sample(int evalCp, int depth, long nps, short bestMove) {
    }
}
