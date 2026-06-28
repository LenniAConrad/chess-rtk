package application.gui.workbench.network;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import static application.gui.workbench.network.NnueDrawing.*;
import static application.gui.workbench.network.NnueTraceGeometry.*;

/**
 * Workbench panel that visualises an NNUE half-KP forward pass.
 *
 * <p>Abstract mode shows the centipawn output, the top-N active features that
 * fired and their signed impact on the eval, and a short readout of which
 * accumulator slots moved the most. Detailed mode draws the full wired diagram
 * (features -&gt; accumulator -&gt; clipped -&gt; output) with weighted edges
 * coloured by sign and opacity scaled by magnitude.</p>
 *
 * <p>Shared scaffolding lives in {@link NetworkView}; this class adds
 * the NNUE-specific drawing plus the {@link Scrollable} plumbing used to keep
 * dense atlas and trace views inside the active viewport.</p>
 */

public abstract class NnueTraceView extends NnueOverviewView {
    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Paints the detailed wired-diagram view. The "clipped" column from the
     * original three-stage diagram is folded into the accumulator column —
     * the accumulator node already encodes the post-ReLU value, so a separate
     * clipped column was redundant and added straight-line clutter.
     *
     * @param g graphics
     * @param body body rectangle
     */
    @Override
    protected void paintDetailed(Graphics2D g, Rectangle body) {
        TensorViz.drawSectionHeader(g,
                new Rectangle(body.x, body.y, body.width, TRACE_HEADER_H),
                "detailed NNUE node graph",
                isStockfishSnapshot()
                        ? stockfishTraceShapeSummary()
                        : "30 active-feature lanes | raw accumulator -> clipped ReLU -> output contribution -> cp");
        Rectangle ribbon = new Rectangle(body.x, body.y + TRACE_HEADER_H + 8,
                body.width, TRACE_RIBBON_H);
        drawTracePipelineRibbon(g, ribbon);

        int boardSide = Math.min(280, Math.max(190, Math.min(300, body.width / 4)));
        int graphTop = ribbon.y + ribbon.height + 14;
        Rectangle boardArea = new Rectangle(body.x + body.width - boardSide - 8,
                graphTop, boardSide, boardSide);
        Rectangle wire = new Rectangle(body.x, graphTop,
                body.width - boardSide - 20, body.height - (graphTop - body.y));
        NnueTraceLayout layout = layout(wire);
        drawTraceBackdrop(g, wire, layout);
        drawClassicTrunkRails(g, layout);
        drawColumnLabels(g, layout);
        drawEdges(g, layout);
        drawFeatureColumn(g, layout);
        drawAccumulatorColumn(g, layout);
        drawClippedColumn(g, layout);
        drawContributionColumn(g, layout);
        drawOutputColumn(g, layout);
        NetworkBoardSection.paintOverlayBoard(g, hitRegions, boardArea, fen,
                "", null, 0.0f, -1, TensorViz.FOCUS,
                "Half-KP features are derived from this board",
                this::paintSelectedFeatureOverlay, null);
        hitRegions.add(boardArea, "Current position",
                fen == null ? "no FEN" : fen,
                "Half-KP features are derived from this board");
        drawSlotZoom(g, new Rectangle(boardArea.x, boardArea.y + boardArea.height + 12,
                boardArea.width, Math.min(240, body.y + body.height - boardArea.y - boardArea.height - 46)),
                layout);
        drawDetailedReadout(g, body, layout);
    }

    /**
     * Draws the compact layer-stack summary above the full Trace graph.
     *
     * @param g graphics
     * @param r ribbon rectangle
     */
    protected void drawTracePipelineRibbon(Graphics2D g, Rectangle r) {
        if (isStockfishSnapshot()) {
            drawStockfishTracePipelineRibbon(g, r);
            return;
        }
        float[] usIndices = snapshot.data("nnue.features.us.indices");
        float[] themIndices = snapshot.data("nnue.features.them.indices");
        float[] accUs = snapshot.data("nnue.accumulator.us");
        float[] accThem = snapshot.data("nnue.accumulator.them");
        float[] clippedUs = snapshot.data("nnue.clipped.us");
        float[] clippedThem = snapshot.data("nnue.clipped.them");
        float[] output = snapshot.data("nnue.output.centipawns");
        float[] activeSplit = {
                usIndices == null ? 0.0f : usIndices.length,
                themIndices == null ? 0.0f : -themIndices.length,
        };

        int stages = 6;
        int gap = 8;
        int stageW = Math.max(72, (r.width - gap * (stages - 1)) / stages);
        Rectangle[] cards = new Rectangle[stages];
        for (int i = 0; i < stages; i++) {
            cards[i] = new Rectangle(r.x + i * (stageW + gap), r.y,
                    i == stages - 1 ? r.x + r.width - (r.x + i * (stageW + gap)) : stageW,
                    r.height);
        }
        for (int i = 0; i < stages - 1; i++) {
            drawTraceConnector(g, cards[i], cards[i + 1]);
        }

        drawTraceStage(g, cards[0],
                "HalfKP inputs",
                safeLength(usIndices) + " us / " + safeLength(themIndices) + " them active",
                activeSplit, true, TensorViz.POSITIVE,
                null, "Sparse king-piece-square features currently active.");
        drawTraceStage(g, cards[1],
                "accumulator us",
                safeLength(accUs) + " raw neurons",
                accUs, true, TensorViz.TRUNK,
                "nnue.accumulator.us", "Raw side-to-move accumulator before clipped ReLU.");
        drawTraceStage(g, cards[2],
                "accumulator them",
                safeLength(accThem) + " raw neurons",
                accThem, true, TensorViz.TRUNK,
                "nnue.accumulator.them", "Raw opponent accumulator before clipped ReLU.");
        drawTraceStage(g, cards[3],
                "clipped us",
                safeLength(clippedUs) + " post-ReLU neurons",
                clippedUs, false, TensorViz.POLICY,
                "nnue.clipped.us", "Side-to-move activations after clipping.");
        drawTraceStage(g, cards[4],
                "clipped them",
                safeLength(clippedThem) + " post-ReLU neurons",
                clippedThem, false, TensorViz.VALUE,
                "nnue.clipped.them", "Opponent activations after clipping.");
        drawTraceStage(g, cards[5],
                "linear output",
                output == null || output.length == 0
                        ? "centipawn head"
                        : String.format("%+d cp", Math.round(output[0])),
                output, true, TensorViz.VALUE,
                "nnue.output.centipawns", "Final affine output converted to centipawns.");
    }

    /**
     * Draws the Stockfish NNUE layer-stack ribbon.
     *
     * @param g graphics
     * @param r ribbon rectangle
     */
    protected void drawStockfishTracePipelineRibbon(Graphics2D g, Rectangle r) {
        float[] usIndices = snapshot.data("nnue.features.us.indices");
        float[] transformed = snapshot.data("nnue.stockfish.transformed");
        float[] transformedUs = snapshot.data("nnue.stockfish.transformed.us");
        float[] fc0 = snapshot.data("nnue.stockfish.fc0.raw");
        float[] fc1 = snapshot.data("nnue.stockfish.fc1.clipped");
        float[] fc2 = snapshot.data("nnue.stockfish.fc2.contribution");
        float[] output = snapshot.data("nnue.output.centipawns");
        float[] outputParts = snapshot.data("nnue.stockfish.output.parts");

        int stages = 6;
        int gap = 8;
        int stageW = Math.max(72, (r.width - gap * (stages - 1)) / stages);
        Rectangle[] cards = new Rectangle[stages];
        for (int i = 0; i < stages; i++) {
            cards[i] = new Rectangle(r.x + i * (stageW + gap), r.y,
                    i == stages - 1 ? r.x + r.width - (r.x + i * (stageW + gap)) : stageW,
                    r.height);
        }
        for (int i = 0; i < stages - 1; i++) {
            drawTraceConnector(g, cards[i], cards[i + 1]);
        }

        drawTraceStage(g, cards[0],
                "HalfKAv2_hm inputs",
                safeLength(usIndices) + " side-to-move active",
                usIndices, false, TensorViz.POSITIVE,
                "nnue.features.us.indices", "Stockfish HalfKAv2_hm sparse features.");
        drawTraceStage(g, cards[1],
                "transformer",
                stockfishTransformerRibbonDetail(transformed, transformedUs),
                transformed == null ? transformedUs : transformed, false, TensorViz.TRUNK,
                transformed == null ? "nnue.stockfish.transformed.us" : "nnue.stockfish.transformed",
                "Feature-transformer output: side-to-move half followed by opponent half.");
        drawTraceStage(g, cards[2],
                "FC0",
                Math.max(0, safeLength(fc0) - 1) + " hidden + fwd",
                fc0, true, TensorViz.TRUNK,
                "nnue.stockfish.fc0.raw", "First Stockfish dense affine layer.");
        drawTraceStage(g, cards[3],
                "FC1",
                safeLength(fc1) + " clipped hidden",
                fc1, false, TensorViz.POLICY,
                "nnue.stockfish.fc1.clipped", "Second Stockfish dense hidden layer after clipping.");
        drawTraceStage(g, cards[4],
                "FC2 contribution",
                safeLength(fc2) + " output terms",
                fc2, true, TensorViz.VALUE,
                "nnue.stockfish.fc2.contribution", "Final affine output contribution per FC1 lane.");
        drawTraceStage(g, cards[5],
                "linear output",
                output == null || output.length == 0
                        ? "centipawn head"
                        : String.format("%+d cp", Math.round(output[0])),
                outputParts == null ? output : outputParts, true, TensorViz.VALUE,
                outputParts == null ? "nnue.output.centipawns" : "nnue.stockfish.output.parts",
                "Stockfish output parts: PSQT, FC2 bias, FC2 terms, and FC0 forward branch.");
    }

    /**
     * Returns a compact transformer-stage dimension label.
     *
     * @param transformed full transformed vector
     * @param transformedUs side-to-move half
     * @return display label
     */
    protected String stockfishTransformerRibbonDetail(float[] transformed, float[] transformedUs) {
        int total = safeLength(transformed);
        int stm = safeLength(transformedUs);
        if (total > 0 && stm > 0 && total != stm) {
            return total + " lanes (" + stm + " stm)";
        }
        if (total > 0) {
            return total + " lanes";
        }
        return stm + " stm lanes";
    }

    /**
     * Returns the first available tensor by key.
     *
     * @param preferred preferred tensor key
     * @param fallback fallback tensor key
     * @return tensor data, or null
     */
    protected float[] preferredData(String preferred, String fallback) {
        float[] out = snapshot == null ? null : snapshot.data(preferred);
        return out == null && snapshot != null ? snapshot.data(fallback) : out;
    }

    /**
     * Returns the first available tensor shape by key.
     *
     * @param preferred preferred tensor key
     * @param fallback fallback tensor key
     * @return tensor shape
     */
    protected int[] preferredShape(String preferred, String fallback) {
        int[] out = snapshot == null ? new int[0] : snapshot.shape(preferred);
        if (out.length == 0 && snapshot != null) {
            return snapshot.shape(fallback);
        }
        return out;
    }

    /**
     * Draws a single stage card in the Trace layer-stack ribbon.
     *
     * @param g graphics
     * @param r card rectangle
     * @param title stage title
     * @param subtitle stage subtitle
     * @param values optional values to show as a compact strip
     * @param signed true for signed/diverging colour scale
     * @param accent accent colour
     * @param dataKey snapshot key, or null for hover-only regions
     * @param detail tooltip detail
     */
    protected void drawTraceStage(Graphics2D g, Rectangle r, String title, String subtitle,
            float[] values, boolean signed, Color accent, String dataKey, String detail) {
        TensorViz.drawCard(g, r, title, subtitle, accent);
        Rectangle strip = new Rectangle(r.x + 10, r.y + r.height - 26,
                Math.max(8, r.width - 20), 12);
        if (values == null || values.length == 0) {
            TensorViz.drawEmpty(g, strip);
        } else {
            TensorViz.drawHeatmap(g, strip, values, values.length, 1,
                    Math.max(1e-4f, maxAbs(values)), signed);
        }
        g.setFont(Theme.font(9, Font.PLAIN));
        g.setColor(Theme.MUTED);
        String stats = traceStats(values, signed);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(Ui.elide(stats, fm, Math.max(12, r.width - 20)),
                r.x + 10, r.y + r.height - 32);
        if (dataKey == null) {
            hitRegions.add(strip, title, detail, stats);
        } else {
            hitRegions.addInspectable(strip, title, detail, stats,
                    dataKey, 0, values == null ? 0 : values.length, 0,
                    "1x" + (values == null ? 0 : values.length));
        }
    }

    /**
     * Draws a small arrow between stage cards.
     *
     * @param g graphics
     * @param from source card
     * @param to destination card
     */
    protected void drawTraceConnector(Graphics2D g, Rectangle from, Rectangle to) {
        int y = from.y + from.height / 2;
        int x1 = from.x + from.width + 1;
        int x2 = to.x - 2;
        if (x2 <= x1) {
            return;
        }
        g.setColor(Theme.withAlpha(TensorViz.FOCUS, 140));
        g.drawLine(x1, y, x2, y);
        g.drawLine(x2, y, x2 - 4, y - 3);
        g.drawLine(x2, y, x2 - 4, y + 3);
    }

    /**
     * Computes column geometry for the Trace diagram
     * (features → accumulator → clipped → contribution → output).
     *
     * @param r body rectangle
     * @return layout
     */
    protected NnueTraceLayout layout(Rectangle r) {
        NnueTraceLayout out = new NnueTraceLayout();
        int leftCx = r.x + 72;
        int rightCx = r.x + Math.max(leftCx + 4 * 56, r.width - 102);
        int gap = Math.max(56, (rightCx - leftCx) / 4);
        out.featureCx = leftCx;
        out.accumCx = leftCx + gap;
        out.clippedCx = leftCx + gap * 2;
        out.contribCx = leftCx + gap * 3;
        out.outputCx = leftCx + gap * 4;
        out.slotRadius = 8;
        int graphTop = r.y + 40;
        int graphBottom = r.y + Math.max(72, r.height - 28);
        int usable = Math.max(1, graphBottom - graphTop);
        out.graphTop = graphTop;
        out.graphBottom = graphBottom;
        out.usableHeight = usable;
        int slots = Math.max(1, visibleSlots.length);
        int features = Math.max(1, traceFeatureLanes());
        out.slotPitch = adaptivePitch(usable, slots, TRACE_SLOT_PITCH);
        out.featurePitch = adaptivePitch(usable, features, TRACE_FEATURE_PITCH);
        int slotSpan = (slots - 1) * out.slotPitch;
        int featureSpan = (features - 1) * out.featurePitch;
        out.startY = graphTop + Math.max(0, (usable - slotSpan) / 2);
        out.featureStartY = graphTop + Math.max(0, (usable - featureSpan) / 2);
        out.bottomY = out.startY + (slots - 1) * out.slotPitch;
        out.featureBottomY = out.featureStartY + (features - 1) * out.featurePitch;
        out.labelY = Math.max(r.y + 18, Math.min(out.startY, out.featureStartY) - 18);
        return out;
    }

    /**
     * Paints subtle stage bands behind the Trace graph. The bands turn the dense
     * mesh into five readable pipeline stages without changing the data.
     *
     * @param g graphics
     * @param wire trace body rectangle
     * @param layout layout model
     */
    protected void drawTraceBackdrop(Graphics2D g, Rectangle wire, NnueTraceLayout layout) {
        int top = wire.y + 4;
        int bottom = Math.min(wire.y + wire.height - 24, layout.graphBottom + 12);
        if (bottom <= top) {
            return;
        }
        int[] centers = traceColumnCenters(layout);
        int bandW = traceBandWidth(layout);
        for (int i = 0; i < centers.length; i++) {
            int x = centers[i] - bandW / 2;
            Color tint = traceStageTint(i);
            g.setColor(tint);
            g.fillRoundRect(x, top, bandW, bottom - top, Theme.RADIUS, Theme.RADIUS);
            g.setColor(Theme.withAlpha(Theme.LINE, 105));
            g.drawRoundRect(x, top, bandW, bottom - top, Theme.RADIUS, Theme.RADIUS);
        }

        int flowY = Math.max(wire.y + 34, layout.labelY + 17);
        if (flowY < layout.graphTop - 2 && centers.length > 1) {
            int startX = centers[0] + Math.max(14, bandW / 2 - 6);
            int endX = centers[centers.length - 1] - Math.max(14, bandW / 2 - 6);
            g.setColor(Theme.withAlpha(TensorViz.FOCUS, 120));
            g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(startX, flowY, endX, flowY);
            g.drawLine(endX, flowY, endX - 5, flowY - 3);
            g.drawLine(endX, flowY, endX - 5, flowY + 3);
            g.setStroke(new BasicStroke(1.0f));
        }
    }

    /**
     * Draws faint per-slot rails through the classic NNUE trunk. The coloured
     * edges still carry the data, but the rails keep the right-side lane
     * structure visible and make accumulator -> clipped -> contribution read as
     * one continuous hidden path.
     *
     * @param g graphics
     * @param layout trace layout
     */
    protected void drawClassicTrunkRails(Graphics2D g, NnueTraceLayout layout) {
        if (isStockfishSnapshot() || visibleSlots.length == 0) {
            return;
        }
        int x1 = layout.accumCx;
        int x2 = layout.contribCx;
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < visibleSlots.length; i++) {
            int y = layout.startY + i * layout.slotPitch;
            boolean selected = i == selectedSlot;
            g.setColor(Theme.withAlpha(
                    selected ? TensorViz.FOCUS : TensorViz.TRUNK,
                    selected ? 130 : 52));
            g.drawLine(x1, y, x2, y);
        }
        g.setStroke(new BasicStroke(1.0f));

        int labelY = layout.graphTop - 4;
        if (labelY > layout.labelY + 14) {
            g.setFont(Theme.font(9, Font.BOLD));
            g.setColor(Theme.withAlpha(TensorViz.TRUNK, 180));
            drawCenteredFittedLabel(g, "trunk lanes", (x1 + x2) / 2, labelY,
                    Math.max(32, x2 - x1 - 12));
        }
        hitRegions.add(new Rectangle(Math.min(x1, x2), layout.graphTop,
                Math.abs(x2 - x1), Math.max(1, layout.graphBottom - layout.graphTop)),
                "NNUE trunk lanes",
                "Raw accumulator, clipped activation and output contribution for the same selected slots.",
                visibleSlots.length + " visible lanes");
    }

    /**
     * Draws column labels.
     *
     * @param g graphics
     * @param layout layout model
     */
    protected void drawColumnLabels(Graphics2D g, NnueTraceLayout layout) {
        int y = layout.labelY;
        int w = traceBandWidth(layout) - 8;
        if (isStockfishSnapshot()) {
            drawStageHeader(g, layout.featureCx, y, w, "1", "HalfKA", stockfishFeatureStageDetail());
            drawStageHeader(g, layout.accumCx, y, w, "2", "transformer", stockfishTransformerStageDetail());
            drawStageHeader(g, layout.clippedCx, y, w, "3", "FC0", stockfishFc0StageDetail());
            drawStageHeader(g, layout.contribCx, y, w, "4", "FC1", stockfishFc1StageDetail());
            drawStageHeader(g, layout.outputCx, y, w, "5", "FC2", stockfishFc2StageDetail());
        } else {
            drawStageHeader(g, layout.featureCx, y, w, "1", "features", classicFeatureStageDetail());
            drawStageHeader(g, layout.accumCx, y, w, "2", "accumulator", classicAccumulatorStageDetail());
            drawStageHeader(g, layout.clippedCx, y, w, "3", "clipped", classicClippedStageDetail());
            drawStageHeader(g, layout.contribCx, y, w, "4", "contrib", classicContributionStageDetail());
            drawStageHeader(g, layout.outputCx, y, w, "5", "output", "centipawns");
        }
    }

    /**
     * Detail label for the Stockfish sparse-input stage.
     *
     * @return label text
     */
    protected String stockfishFeatureStageDetail() {
        int active = safeLength(snapshot.data("nnue.features.us.indices"));
        return active + "/" + traceFeatureLanes() + " active";
    }

    /**
     * Detail label for the Stockfish transformer stage.
     *
     * @return label text
     */
    protected String stockfishTransformerStageDetail() {
        int stm = safeLength(snapshot.data("nnue.stockfish.transformed.us"));
        int total = safeLength(snapshot.data("nnue.stockfish.transformed"));
        int shown = Math.min(visibleSlots.length, stm);
        if (stm > 0 && shown > 0 && shown < stm) {
            return "top " + shown + "/" + stm + " stm · " + total + " total";
        }
        if (total > 0 && stm > 0 && total != stm) {
            return stm + " stm / " + total + " total";
        }
        return stm + " stm lanes";
    }

    /**
     * Detail label for the Stockfish FC0 stage.
     *
     * @return label text
     */
    protected String stockfishFc0StageDetail() {
        int rows = Math.max(0, safeLength(snapshot.data("nnue.stockfish.fc0.raw")) - 1);
        return rows + " hidden + fwd skip";
    }

    /**
     * Detail label for the Stockfish FC1 stage.
     *
     * @return label text
     */
    protected String stockfishFc1StageDetail() {
        return safeLength(snapshot.data("nnue.stockfish.fc1.clipped")) + " clipped";
    }

    /**
     * Detail label for the Stockfish FC2 stage.
     *
     * @return label text
     */
    protected String stockfishFc2StageDetail() {
        String suffix = snapshot.data("nnue.stockfish.fc0.fwd.cp") == null ? " terms" : " terms + fwd";
        return safeLength(snapshot.data("nnue.stockfish.fc2.contribution")) + suffix;
    }

    /**
     * Detail label for the classic sparse-input stage.
     *
     * @return label text
     */
    protected String classicFeatureStageDetail() {
        return stockfishFeatureStageDetail();
    }

    /**
     * Detail label for the classic accumulator stage.
     *
     * @return label text
     */
    protected String classicAccumulatorStageDetail() {
        return visibleStageDetail(snapshot.data("nnue.accumulator.us"), "slots");
    }

    /**
     * Detail label for the classic clipped-ReLU stage.
     *
     * @return label text
     */
    protected String classicClippedStageDetail() {
    return visibleStageDetail(snapshot.data("nnue.clipped.us"), "slots");
    }

    /**
     * Detail label for the classic contribution stage.
     *
     * @return label text
     */
    protected String classicContributionStageDetail() {
    return visibleStageDetail(totalContribution(), "terms");
    }

    /**
     * Returns a "shown/total" label for a Trace column.
     *
     * @param values backing values
     * @param unit unit label
     * @return label text
     */
    protected String visibleStageDetail(float[] values, String unit) {
        int total = safeLength(values);
        int shown = Math.min(visibleSlots.length, total);
        if (total > 0 && shown > 0 && shown < total) {
            return "top " + shown + "/" + total;
        }
        return total + " " + unit;
    }

    /**
     * Draws the feature node column.
     *
     * @param g graphics
     * @param layout layout model
     */
    protected void drawFeatureColumn(Graphics2D g, NnueTraceLayout layout) {
        float[] indices = snapshot.data("nnue.features.us.indices");
        float[] impact = snapshot.data("nnue.features.us.impact");
        float maxAbs = 0.0f;
        if (impact != null) {
            for (float v : impact) {
                maxAbs = Math.max(maxAbs, Math.abs(v));
            }
        }
        if (maxAbs <= 0.0f) {
            maxAbs = 1.0f;
        }
        g.setFont(Theme.font(10, Font.PLAIN));
        int[] weightShape = snapshot.shape("nnue.features.us.weights");
        int hiddenStride = weightShape != null && weightShape.length >= 2 ? weightShape[1] : 0;
        int lanes = traceFeatureLanes();
        int activeCount = indices == null ? 0 : Math.min(visibleFeatures.length, lanes);
        for (int i = 0; i < lanes; ++i) {
            int y = layout.featureStartY + i * layout.featurePitch;
            if (i >= activeCount || visibleFeatures[i] >= indices.length) {
                drawInactiveFeatureLane(g, layout.featureCx, y, layout.slotRadius);
                String label = i == activeCount ? "empty" : "";
                FontMetrics fm = g.getFontMetrics();
                int lw = fm.stringWidth(label);
                if (!label.isEmpty()) {
                    g.setColor(Theme.MUTED);
                    g.drawString(label, layout.featureCx - lw - 14, y + 4);
                }
                hitRegions.add(new Rectangle(layout.featureCx - Math.max(lw, layout.slotRadius) - 18,
                        y - layout.slotRadius - 2,
                        Math.max(lw, layout.slotRadius) + layout.slotRadius * 2 + 22,
                        layout.slotRadius * 2 + 4),
                        "Inactive feature lane",
                        "No side-to-move non-king piece currently occupies this lane",
                        "maximum active feature lanes: " + lanes);
                continue;
            }
            int featureRow = visibleFeatures[i];
            int featureIdx = Math.round(indices[featureRow]);
            float rawImpact = valueAt(impact, featureRow);
            float v = rawImpact / maxAbs;
            TensorViz.drawNode(g, layout.featureCx, y, layout.slotRadius, v, false);
            g.setColor(Theme.TEXT);
            String label = "#" + featureIdx;
            FontMetrics fm = g.getFontMetrics();
            int lw = fm.stringWidth(label);
            g.drawString(label, layout.featureCx - lw - 14, y + 4);
            Rectangle hit = new Rectangle(layout.featureCx - lw - 18, y - layout.slotRadius - 2,
                    lw + layout.slotRadius * 2 + 22, layout.slotRadius * 2 + 4);
            String dec = isStockfishSnapshot() ? "Stockfish HalfKAv2_hm" : decodeUsHalfKP(featureIdx);
            if (hiddenStride > 0) {
                hitRegions.addInspectable(hit,
                        "Feature #" + featureIdx + "  " + dec,
                        "Active half-KP feature · click to see its row of feature->L1 weights",
                        String.format("impact %+.2f cp", rawImpact),
                        "nnue.features.us.weights",
                        featureRow * hiddenStride, hiddenStride, 0,
                        "1x" + hiddenStride);
            } else {
                hitRegions.add(hit,
                        "Feature #" + featureIdx + "  " + dec,
                        "Active half-KP feature",
                        String.format("impact %+.2f cp", rawImpact));
            }
        }
    }

    /**
     * Draws the raw accumulator column. The visible slot set is still ranked by
     * final output contribution; this column shows the trunk value before the
     * clipped ReLU gate.
     *
     * @param g graphics
     * @param layout layout model
     */
    protected void drawAccumulatorColumn(Graphics2D g, NnueTraceLayout layout) {
        if (isStockfishSnapshot()) {
            drawStockfishTransformerColumn(g, layout);
            return;
        }
        float[] acc = snapshot.data("nnue.accumulator.us");
        float[] accThem = snapshot.data("nnue.accumulator.them");
        float[] cl = snapshot.data("nnue.clipped.us");
        float[] clThem = snapshot.data("nnue.clipped.them");
        float[] total = totalContribution();
        if (acc == null) {
            return;
        }
        float scale = visiblePairScale(acc, accThem, visibleSlots);
        for (int i = 0; i < visibleSlots.length; ++i) {
            int idx = visibleSlots[i];
            float pre = valueAt(acc, idx);
            float post = valueAt(cl, idx);
            float themPre = valueAt(accThem, idx);
            float themPost = valueAt(clThem, idx);
            float net = valueAt(total, idx);
            float v = (pre - themPre) / scale;
            int y = layout.startY + i * layout.slotPitch;
            TensorViz.drawNode(g, layout.accumCx, y, layout.slotRadius, v, i == selectedSlot);
            if (pre < 0.0f || themPre < 0.0f) {
                int rr = layout.slotRadius + 3;
                g.setColor(TensorViz.NEGATIVE);
                g.drawOval(layout.accumCx - rr, y - rr, rr * 2, rr * 2);
            }
            int rad = layout.slotRadius + 2;
            hitRegions.add(new Rectangle(layout.accumCx - rad, y - rad, rad * 2, rad * 2),
                    "Accumulator slot " + idx,
                    "raw us " + String.format("%+.3f→%.3f", pre, post)
                            + " · them " + String.format("%+.3f→%.3f", themPre, themPost),
                    String.format("net contribution %+.3f", net));
        }
    }

    /**
     * Draws the clipped-ReLU trunk column.
     *
     * @param g graphics
     * @param layout layout model
     */
    protected void drawClippedColumn(Graphics2D g, NnueTraceLayout layout) {
        if (isStockfishSnapshot()) {
            drawStockfishFc0Column(g, layout);
            return;
        }
        float[] cl = snapshot.data("nnue.clipped.us");
        float[] clThem = snapshot.data("nnue.clipped.them");
        if (cl == null && clThem == null) {
            return;
        }
        float scale = visiblePairScale(cl, clThem, visibleSlots);
        for (int i = 0; i < visibleSlots.length; ++i) {
            int idx = visibleSlots[i];
            float post = valueAt(cl, idx);
            float themPost = valueAt(clThem, idx);
            float v = (post - themPost) / scale;
            int y = layout.startY + i * layout.slotPitch;
            TensorViz.drawNode(g, layout.clippedCx, y, layout.slotRadius, v, i == selectedSlot);
            int rad = layout.slotRadius + 2;
            hitRegions.add(new Rectangle(layout.clippedCx - rad, y - rad, rad * 2, rad * 2),
                    "Clipped slot " + idx,
                    "post-ReLU us " + String.format("%.3f", post)
                            + " · them " + String.format("%.3f", themPost),
                    String.format("visible net %.3f", post - themPost));
        }
    }

    /**
     * Draws each visible slot's weighted output-head contribution.
     *
     * @param g graphics
     * @param layout layout model
     */
    protected void drawContributionColumn(Graphics2D g, NnueTraceLayout layout) {
        if (isStockfishSnapshot()) {
            drawStockfishFc1Column(g, layout);
            return;
        }
        float[] contrib = totalContribution();
        if (contrib == null) {
            return;
        }
        float scale = visibleAbsScale(contrib, visibleSlots);
        for (int i = 0; i < visibleSlots.length; ++i) {
            int idx = visibleSlots[i];
            float value = valueAt(contrib, idx);
            int y = layout.startY + i * layout.slotPitch;
            TensorViz.drawNode(g, layout.contribCx, y, layout.slotRadius, value / scale, i == selectedSlot);
            int rad = layout.slotRadius + 2;
            hitRegions.add(new Rectangle(layout.contribCx - rad, y - rad, rad * 2, rad * 2),
                    "Output contribution slot " + idx,
                    "clipped activation multiplied by output-head weight",
                    String.format("net contribution %+.3f", value));
        }
    }

    /**
     * Draws Stockfish feature-transformer lanes.
     *
     * @param g graphics
     * @param layout layout model
     */
    protected void drawStockfishTransformerColumn(Graphics2D g, NnueTraceLayout layout) {
        float[] transformed = snapshot.data("nnue.stockfish.transformed.us");
        float[] transformedFull = snapshot.data("nnue.stockfish.transformed");
        if (transformed == null) {
            return;
        }
        float scale = visibleAbsScale(transformed, visibleSlots);
        for (int i = 0; i < visibleSlots.length; ++i) {
            int idx = visibleSlots[i];
            float value = valueAt(transformed, idx);
            int y = layout.startY + i * layout.slotPitch;
            TensorViz.drawNode(g, layout.accumCx, y, layout.slotRadius, value / scale, i == selectedSlot);
            int rad = layout.slotRadius + 2;
            hitRegions.add(new Rectangle(layout.accumCx - rad, y - rad, rad * 2, rad * 2),
                    "Transformer lane " + idx,
                    "Stockfish feature-transformer output, side-to-move half of "
                            + safeLength(transformedFull) + " total lanes",
                    String.format("value %.1f", value));
        }
    }

    /**
     * Draws Stockfish FC0 raw outputs.
     *
     * @param g graphics
     * @param layout layout model
     */
    protected void drawStockfishFc0Column(Graphics2D g, NnueTraceLayout layout) {
        float[] fc0 = snapshot.data("nnue.stockfish.fc0.raw");
        if (fc0 == null || fc0.length == 0) {
            return;
        }
        int hiddenRows = Math.max(0, fc0.length - 1);
        int rows = fc0.length;
        float scale = maxAbs(fc0);
        if (scale <= 0.0f) {
            scale = 1.0f;
        }
        for (int i = 0; i < rows; ++i) {
            int y = columnY(layout, i, rows);
            boolean fwd = i == hiddenRows;
            TensorViz.drawNode(g, layout.clippedCx, y, layout.slotRadius, fc0[i] / scale, false);
            if (fwd) {
                int rr = layout.slotRadius + 3;
                g.setColor(Theme.withAlpha(TensorViz.FOCUS, 190));
                g.drawOval(layout.clippedCx - rr, y - rr, rr * 2, rr * 2);
                g.setColor(TensorViz.FOCUS);
                g.setFont(Theme.font(9, Font.BOLD));
                g.drawString("fwd skip", layout.clippedCx + rr + 3, y + 3);
            }
            int rad = layout.slotRadius + 2;
            String title = fwd ? "FC0 forward row" : "FC0 neuron " + i;
            String detail = fwd
                    ? "Stockfish forward branch added directly to positional output"
                    : "First Stockfish dense affine layer before clipped activations";
            float[] fwdCp = snapshot.data("nnue.stockfish.fc0.fwd.cp");
            String stats = fwd
                    ? String.format("raw %+.1f -> %+.2f cp", fc0[i], valueAt(fwdCp, 0))
                    : String.format("raw %+.1f", fc0[i]);
            hitRegions.add(new Rectangle(layout.clippedCx - rad, y - rad, rad * 2, rad * 2),
                    title, detail, stats);
        }
    }

    /**
     * Draws Stockfish FC1 clipped outputs.
     *
     * @param g graphics
     * @param layout layout model
     */
    protected void drawStockfishFc1Column(Graphics2D g, NnueTraceLayout layout) {
        float[] fc1 = snapshot.data("nnue.stockfish.fc1.clipped");
        if (fc1 == null) {
            return;
        }
        float scale = maxAbs(fc1);
        if (scale <= 0.0f) {
            scale = 1.0f;
        }
        for (int i = 0; i < fc1.length; ++i) {
            int y = columnY(layout, i, fc1.length);
            TensorViz.drawNode(g, layout.contribCx, y, layout.slotRadius, fc1[i] / scale, false);
            int rad = layout.slotRadius + 2;
            hitRegions.add(new Rectangle(layout.contribCx - rad, y - rad, rad * 2, rad * 2),
                    "FC1 neuron " + i,
                    "Second Stockfish dense hidden layer after clipped ReLU",
                    String.format("clipped %.1f", fc1[i]));
        }
    }

    /**
     * Draws the output column (single node, bar to the right).
     *
     * @param g graphics
     * @param layout layout model
     */
    protected void drawOutputColumn(Graphics2D g, NnueTraceLayout layout) {
        float[] cp = snapshot.data("nnue.output.centipawns");
        float value = cp == null ? 0.0f : cp[0];
        int cy = (layout.startY + layout.bottomY) / 2;
        float scale = Math.max(80.0f, Math.abs(value) * 1.4f);
        int rad = layout.slotRadius + 6;
        TensorViz.drawNode(g, layout.outputCx, cy, rad, value / scale, false);
        Rectangle bar = new Rectangle(layout.outputCx + 18, cy - 8, 80, 16);
        TensorViz.drawHorizontalBar(g, bar, value, scale, null);
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(11, Font.BOLD));
        g.drawString(String.format("%+d cp", Math.round(value)), layout.outputCx + 18, cy + 24);
        String detail = isStockfishSnapshot()
                ? "Final Stockfish eval after PSQT, FC2 dense head, and FC0 forward branch"
                : "Final NNUE eval after affine + scaling";
        hitRegions.addInspectable(new Rectangle(layout.outputCx - rad, cy - rad, rad * 2 + 100, rad * 2 + 28),
                "Centipawn output",
                detail,
                String.format("%+d cp", Math.round(value)),
                "nnue.output.centipawns", 0, 0, 0, "1");
    }

    /**
     * Draws the selected accumulator slot zoom card.
     *
     * @param g graphics
     * @param r card rectangle
     * @param layout trace layout
     */
    protected void drawSlotZoom(Graphics2D g, Rectangle r, NnueTraceLayout layout) {
        if (r.height < 96 || r.width < 140) {
            return;
        }
        if (isStockfishSnapshot()) {
            drawStockfishSlotZoom(g, r);
            return;
        }
        TensorViz.drawCard(g, r,
                "slot zoom",
                selectedSlot >= 0 ? "incoming feature weights for the selected neuron"
                        : "click any accumulator neuron to zoom",
                TensorViz.FOCUS);
        if (selectedSlot < 0 || selectedSlot >= visibleSlots.length) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(11, Font.PLAIN));
            g.drawString("No neuron selected.", r.x + 10, r.y + 64);
            return;
        }
        int slot = visibleSlots[selectedSlot];
        float[] acc = snapshot.data("nnue.accumulator.us");
        float[] accThem = snapshot.data("nnue.accumulator.them");
        float[] cl = snapshot.data("nnue.clipped.us");
        float[] clThem = snapshot.data("nnue.clipped.them");
        float[] ow = snapshot.data("nnue.output.weights.us");
        float[] owThem = snapshot.data("nnue.output.weights.them");
        float[] contribUs = snapshot.data("nnue.output.contribution.us");
        float[] contribThem = snapshot.data("nnue.output.contribution.them");
        float[] contrib = totalContribution();
        float pre = valueAt(acc, slot);
        float post = valueAt(cl, slot);
        float themPre = valueAt(accThem, slot);
        float themPost = valueAt(clThem, slot);
        float weight = valueAt(ow, slot);
        float themWeight = valueAt(owThem, slot);
        float outUs = valueAt(contribUs, slot);
        float outThem = valueAt(contribThem, slot);
        float out = valueAt(contrib, slot);

        g.setFont(Theme.font(11, Font.BOLD));
        g.setColor(Theme.TEXT);
        g.drawString(String.format("slot %d", slot), r.x + 10, r.y + 58);
        g.setFont(Theme.font(10, Font.PLAIN));
        g.setColor(Theme.MUTED);
        g.drawString(String.format("us %+6.3f -> %.3f   w %+6.3f   k %+6.3f",
                pre, post, weight, outUs), r.x + 10, r.y + 76);
        g.drawString(String.format("them %+6.3f -> %.3f   w %+6.3f   k %+6.3f   net %+6.3f",
                themPre, themPost, themWeight, outThem, out), r.x + 10, r.y + 92);

        float[] featureWeights = snapshot.data("nnue.features.us.weights");
        int[] shape = snapshot.shape("nnue.features.us.weights");
        if (featureWeights == null || shape.length < 2 || visibleFeatures.length == 0) {
            return;
        }
        int stride = shape[1];
        int rows = visibleFeatures.length;
        float[] column = new float[rows];
        float scale = 0.0f;
        for (int i = 0; i < rows; i++) {
            int featureRow = visibleFeatures[i];
            int offset = featureRow * stride + slot;
            float v = offset < featureWeights.length ? featureWeights[offset] : 0.0f;
            column[i] = v;
            scale = Math.max(scale, Math.abs(v));
        }
        Rectangle heat = new Rectangle(r.x + 10, r.y + 106,
                Math.max(12, r.width - 20), Math.max(20, r.height - 116));
        TensorViz.drawHeatmap(g, heat, column, 1, rows, scale, true);
        hitRegions.addInline(heat,
                "Slot " + slot + " incoming feature weights",
                "Zoomed feature->accumulator column for the selected neuron",
                String.format("%d active feature rows · range ±%.3f", rows, Math.max(scale, 1e-4f)),
                column, rows + "x1");
    }

    /**
     * Draws the selected Stockfish transformer-lane zoom card.
     *
     * @param g graphics
     * @param r card rectangle
     */
    protected void drawStockfishSlotZoom(Graphics2D g, Rectangle r) {
        TensorViz.drawCard(g, r,
                "lane zoom",
                selectedSlot >= 0 ? "incoming HalfKAv2_hm weights and FC0 forward branch"
                        : "click any transformer lane to zoom",
                TensorViz.FOCUS);
        if (selectedSlot < 0 || selectedSlot >= visibleSlots.length) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(11, Font.PLAIN));
            g.drawString("No transformer lane selected.", r.x + 10, r.y + 64);
            return;
        }

        int lane = visibleSlots[selectedSlot];
        float[] transformed = snapshot.data("nnue.stockfish.transformed.us");
        float[] fwdWeights = preferredData(
                "nnue.stockfish.fc0.weights.fwd",
                "nnue.stockfish.fc0.weights.fwd.us");
        float[] fwdCp = snapshot.data("nnue.stockfish.fc0.fwd.cp");
        float value = valueAt(transformed, lane);
        float fwdWeight = valueAt(fwdWeights, lane);

        g.setFont(Theme.font(11, Font.BOLD));
        g.setColor(Theme.TEXT);
        g.drawString(String.format("lane %d", lane), r.x + 10, r.y + 58);
        g.setFont(Theme.font(10, Font.PLAIN));
        g.setColor(Theme.MUTED);
        String detail = String.format("transformer %+6.1f   fwd w %+6.2f   fwd branch %+6.2f cp",
                value, fwdWeight, valueAt(fwdCp, 0));
        g.drawString(Ui.elide(detail, g.getFontMetrics(), Math.max(12, r.width - 20)),
                r.x + 10, r.y + 76);

        float[] featureWeights = snapshot.data("nnue.features.us.weights");
        int[] shape = snapshot.shape("nnue.features.us.weights");
        if (featureWeights == null || shape.length < 2 || visibleFeatures.length == 0) {
            return;
        }
        int stride = shape[1];
        int rows = visibleFeatures.length;
        float[] column = new float[rows];
        float scale = 0.0f;
        for (int i = 0; i < rows; i++) {
            int featureRow = visibleFeatures[i];
            int offset = featureRow * stride + lane;
            float v = offset < featureWeights.length ? featureWeights[offset] : 0.0f;
            column[i] = v;
            scale = Math.max(scale, Math.abs(v));
        }
        Rectangle heat = new Rectangle(r.x + 10, r.y + 92,
                Math.max(12, r.width - 20), Math.max(20, r.height - 102));
        TensorViz.drawHeatmap(g, heat, column, 1, rows, scale, true);
        hitRegions.addInline(heat,
                "Transformer lane " + lane + " incoming HalfKAv2_hm weights",
                "Zoomed feature->transformer column for the selected Stockfish lane",
                String.format("%d active feature rows · range ±%.3f", rows, Math.max(scale, 1e-4f)),
                column, rows + "x1");
    }

    /**
     * Draws all weighted edges for active feature lanes and all visible
     * accumulator slots, emphasising the selected slot's edges.
     *
     * @param g graphics
     * @param layout layout model
     */
    protected void drawEdges(Graphics2D g, NnueTraceLayout layout) {
        if (isStockfishSnapshot()) {
            drawStockfishEdges(g, layout);
            return;
        }
        float[] acc = snapshot.data("nnue.accumulator.us");
        float[] accThem = snapshot.data("nnue.accumulator.them");
        float[] cl = snapshot.data("nnue.clipped.us");
        float[] clThem = snapshot.data("nnue.clipped.them");
        float[] contrib = totalContribution();
        float[] indices = snapshot.data("nnue.features.us.indices");
        float[] featureWeights = snapshot.data("nnue.features.us.weights");
        int[] featureWeightShape = snapshot.shape("nnue.features.us.weights");
        if (contrib == null || indices == null) {
            return;
        }
        int hidden = cl == null ? contrib.length : cl.length;
        int activeFeatures = indices.length;
        int weightsPerFeature = (featureWeights != null && featureWeightShape != null
                && featureWeightShape.length >= 2)
                ? featureWeightShape[1]
                : hidden;

        int featureCount = visibleFeatures.length;

        // Per-feature-to-slot edge scale.
        float featWeightScale = 0.0f;
        if (featureWeights != null) {
            for (int fi : visibleFeatures) {
                for (int si = 0; si < visibleSlots.length; si++) {
                    int slotIdx = visibleSlots[si];
                    int w = fi * weightsPerFeature + slotIdx;
                    if (w < featureWeights.length) {
                        featWeightScale = Math.max(featWeightScale, Math.abs(featureWeights[w]));
                    }
                }
            }
        }
        if (featWeightScale <= 0.0f) {
            featWeightScale = 1.0f;
        }
        float contribScale = visibleAbsScale(contrib, visibleSlots);
        float clippedScale = visiblePairScale(cl, clThem, visibleSlots);

        boolean focused = selectedSlot >= 0 && selectedSlot < visibleSlots.length;

        // Features -> accumulator: every active feature lane connects to every
        // visible accumulator slot. Empty lanes are placeholders and have no
        // edge because no feature is present to add a weight row.
        for (int fi = 0; fi < featureCount; ++fi) {
            int featureIdx = visibleFeatures[fi];
            int featureY = layout.featureStartY + fi * layout.featurePitch;
            for (int si = 0; si < visibleSlots.length; si++) {
                int slotY = layout.startY + si * layout.slotPitch;
                int slotIdx = visibleSlots[si];
                float strength;
                if (featureWeights != null && featureIdx < activeFeatures
                        && slotIdx < weightsPerFeature) {
                    int w = featureIdx * weightsPerFeature + slotIdx;
                    strength = w < featureWeights.length
                            ? featureWeights[w] / featWeightScale
                            : 0.0f;
                } else {
                    strength = 0.0f;
                }
                drawTraceEdge(g, layout.featureCx + layout.slotRadius, featureY,
                        layout.accumCx - layout.slotRadius, slotY, strength, focused && si == selectedSlot);
            }
        }

        // Trunk lanes: raw accumulator -> clipped ReLU -> weighted contribution
        // -> final output. These one-to-one lanes make the right side mirror the
        // stable feature lanes on the left.
        int outCy = (layout.startY + layout.bottomY) / 2;
        for (int si = 0; si < visibleSlots.length; si++) {
            int slotY = layout.startY + si * layout.slotPitch;
            int idx = visibleSlots[si];
            float w2 = contrib[idx] / contribScale;
            float postNet = valueAt(cl, idx) - valueAt(clThem, idx);
            float gate = postNet / clippedScale;
            if (Math.abs(gate) < 0.02f && (valueAt(acc, idx) < 0.0f || valueAt(accThem, idx) < 0.0f)) {
                gate = -0.35f;
            }
            drawTraceEdge(g, layout.accumCx + layout.slotRadius, slotY,
                    layout.clippedCx - layout.slotRadius, slotY, gate, si == selectedSlot);
            drawTraceEdge(g, layout.clippedCx + layout.slotRadius, slotY,
                    layout.contribCx - layout.slotRadius, slotY, w2, si == selectedSlot);
            drawTraceEdge(g, layout.contribCx + layout.slotRadius, slotY,
                    layout.outputCx - (layout.slotRadius + 6), outCy, w2, si == selectedSlot);
        }
    }

    /**
     * Draws Stockfish NNUE layer-stack edges.
     *
     * @param g graphics
     * @param layout layout model
     */
    protected void drawStockfishEdges(Graphics2D g, NnueTraceLayout layout) {
        float[] featureWeights = snapshot.data("nnue.features.us.weights");
        int[] featureWeightShape = snapshot.shape("nnue.features.us.weights");
        float[] fc0Weights = preferredData(
                "nnue.stockfish.fc0.weights",
                "nnue.stockfish.fc0.weights.us");
        int[] fc0Shape = preferredShape(
                "nnue.stockfish.fc0.weights",
                "nnue.stockfish.fc0.weights.us");
        float[] fc0FwdWeights = preferredData(
                "nnue.stockfish.fc0.weights.fwd",
                "nnue.stockfish.fc0.weights.fwd.us");
        float[] fc1Weights = snapshot.data("nnue.stockfish.fc1.weights.combined");
        int[] fc1Shape = snapshot.shape("nnue.stockfish.fc1.weights.combined");
        float[] fc2Contribution = snapshot.data("nnue.stockfish.fc2.contribution");
        float[] fc0FwdContribution = snapshot.data("nnue.stockfish.fc0.fwd.cp");
        float[] fc0 = snapshot.data("nnue.stockfish.fc0.raw");
        float[] fc1 = snapshot.data("nnue.stockfish.fc1.clipped");

        int featureRows = visibleFeatures.length;
        int featureStride = featureWeightShape != null && featureWeightShape.length >= 2
                ? featureWeightShape[1] : 0;
        float featureScale = matrixScale(featureWeights);
        for (int fi = 0; fi < featureRows; ++fi) {
            int featureRow = visibleFeatures[fi];
            int featureY = layout.featureStartY + fi * layout.featurePitch;
            for (int si = 0; si < visibleSlots.length; si++) {
                int slotIdx = visibleSlots[si];
                int slotY = layout.startY + si * layout.slotPitch;
                float strength = 0.0f;
                int offset = featureRow * featureStride + slotIdx;
                if (featureWeights != null && featureStride > 0
                        && slotIdx < featureStride && offset < featureWeights.length) {
                    strength = featureWeights[offset] / featureScale;
                }
                drawTraceEdge(g, layout.featureCx + layout.slotRadius, featureY,
                        layout.accumCx - layout.slotRadius, slotY, strength, si == selectedSlot);
            }
        }

        int fc0Rows = fc0 == null ? 0 : Math.max(0, fc0.length - 1);
        int fc0DisplayRows = fc0 == null ? 0 : fc0.length;
        int fc0Stride = fc0Shape != null && fc0Shape.length >= 2 ? fc0Shape[1] : 0;
        float fc0Scale = Math.max(matrixScale(fc0Weights), matrixScale(fc0FwdWeights));
        // Accumulator activation supplies the per-position factor; multiplied
        // by the static weight, the rendered edge magnitude now reflects the
        // actual flow through this layer rather than just the learned wiring.
        float[] accumulatorUs = snapshot.data("nnue.accumulator.us");
        float accumulatorScale = Math.max(1e-6f, maxAbs(accumulatorUs));
        for (int si = 0; si < visibleSlots.length; si++) {
            int slotIdx = visibleSlots[si];
            int sourceY = layout.startY + si * layout.slotPitch;
            float sourceActivation = accumulatorUs != null && slotIdx < accumulatorUs.length
                    ? accumulatorUs[slotIdx] / accumulatorScale : 0.0f;
            for (int row = 0; row < fc0Rows; row++) {
                int targetY = columnY(layout, row, fc0DisplayRows);
                int offset = row * fc0Stride + slotIdx;
                float weight = fc0Weights != null && fc0Stride > 0
                        && slotIdx < fc0Stride && offset < fc0Weights.length
                        ? fc0Weights[offset] / fc0Scale : 0.0f;
                drawTraceEdge(g, layout.accumCx + layout.slotRadius, sourceY,
                        layout.clippedCx - layout.slotRadius, targetY,
                        weight * sourceActivation, si == selectedSlot);
            }
            if (fc0DisplayRows > fc0Rows) {
                int fwdY = columnY(layout, fc0Rows, fc0DisplayRows);
                float weight = fc0FwdWeights != null && slotIdx < fc0FwdWeights.length
                        ? fc0FwdWeights[slotIdx] / fc0Scale : 0.0f;
                drawTraceEdge(g, layout.accumCx + layout.slotRadius, sourceY,
                        layout.clippedCx - layout.slotRadius, fwdY,
                        weight * sourceActivation, si == selectedSlot);
            }
        }

        int fc1Rows = fc1 == null ? 0 : fc1.length;
        int fc1Stride = fc1Shape != null && fc1Shape.length >= 2 ? fc1Shape[1] : 0;
        // Per-position edge strength: weight × source FC0 activation, both
        // normalised. Without the activation factor the mesh between FC0
        // and FC1 was the same for every position (just the static weight
        // matrix), which made the trace look frozen when the user changed
        // the board.
        float fc1WeightScale = Math.max(1e-6f, matrixScale(fc1Weights));
        float fc0ActivationScale = Math.max(1e-6f, maxAbs(fc0));
        for (int row = 0; row < fc0Rows; row++) {
            int sourceY = columnY(layout, row, fc0DisplayRows);
            float sourceActivation = fc0 != null && row < fc0.length
                    ? fc0[row] / fc0ActivationScale : 0.0f;
            for (int next = 0; next < fc1Rows; next++) {
                int targetY = columnY(layout, next, fc1Rows);
                int offset = next * fc1Stride + row;
                float weight = fc1Weights != null && fc1Stride > 0
                        && row < fc1Stride && offset < fc1Weights.length
                        ? fc1Weights[offset] / fc1WeightScale : 0.0f;
                float strength = weight * sourceActivation;
                drawTraceEdge(g, layout.clippedCx + layout.slotRadius, sourceY,
                        layout.contribCx - layout.slotRadius, targetY, strength, false);
            }
        }

        float contribScale = Math.max(maxAbs(fc2Contribution), Math.abs(valueAt(fc0FwdContribution, 0)));
        if (contribScale <= 0.0f) {
            contribScale = 1.0f;
        }
        int outCy = (layout.startY + layout.bottomY) / 2;
        for (int i = 0; i < fc1Rows; i++) {
            int sourceY = columnY(layout, i, fc1Rows);
            float strength = valueAt(fc2Contribution, i) / contribScale;
            drawTraceEdge(g, layout.contribCx + layout.slotRadius, sourceY,
                    layout.outputCx - (layout.slotRadius + 6), outCy, strength, false);
        }
        if (fc0DisplayRows > fc0Rows) {
            int fwdY = columnY(layout, fc0Rows, fc0DisplayRows);
            float strength = valueAt(fc0FwdContribution, 0) / contribScale;
            int sourceX = layout.clippedCx + layout.slotRadius;
            int targetX = layout.outputCx - (layout.slotRadius + 6);
            Rectangle hit = drawTraceSkipEdge(g, sourceX, fwdY, targetX, outCy, strength);
            hitRegions.add(hit,
                    "FC0 forward skip edge",
                    "Stockfish NNUE carries this FC0 forward branch directly into the output, bypassing FC1.",
                    String.format("branch contribution %+.2f cp", valueAt(fc0FwdContribution, 0)));
        }
    }

    /**
     * Draws the detail-mode readout footer.
     *
     * @param g graphics
     * @param body body rectangle
     * @param layout layout model
     */
    protected void drawDetailedReadout(Graphics2D g, Rectangle body, NnueTraceLayout layout) {
        if (selectedSlot < 0 || selectedSlot >= visibleSlots.length) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(11, Font.PLAIN));
            String text = isStockfishSnapshot()
                    ? "Stockfish trace: " + stockfishTraceShapeSummary()
                            + " | green raises, red lowers, opacity = weight size"
                    : "Full trace: features -> raw accumulator -> clipped ReLU -> contribution -> cp | green raises, red lowers, opacity = weight size";
            g.drawString(text,
                    body.x + 8, body.y + body.height - 8);
            return;
        }
        if (isStockfishSnapshot()) {
            drawStockfishDetailedReadout(g, body);
            return;
        }
        int idx = visibleSlots[selectedSlot];
        float[] acc = snapshot.data("nnue.accumulator.us");
        float[] accThem = snapshot.data("nnue.accumulator.them");
        float[] cl = snapshot.data("nnue.clipped.us");
        float[] clThem = snapshot.data("nnue.clipped.them");
        float[] ow = snapshot.data("nnue.output.weights.us");
        float[] owThem = snapshot.data("nnue.output.weights.them");
        float[] contribUs = snapshot.data("nnue.output.contribution.us");
        float[] contribThem = snapshot.data("nnue.output.contribution.them");
        float[] contrib = totalContribution();
        float a = valueAt(acc, idx);
        float c = valueAt(cl, idx);
        float ta = valueAt(accThem, idx);
        float tc = valueAt(clThem, idx);
        float w = valueAt(ow, idx);
        float tw = valueAt(owThem, idx);
        float ku = valueAt(contribUs, idx);
        float kt = valueAt(contribThem, idx);
        float k = valueAt(contrib, idx);
        String text = String.format(
                "slot %3d | us %+.3f->%.3f x %+.3f = %+.3f | them %+.3f->%.3f x %+.3f = %+.3f | net %+.3f",
                idx, a, c, w, ku, ta, tc, tw, kt, k);
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(11, Font.BOLD));
        g.drawString(text, body.x + 8, body.y + body.height - 8);
    }

    /**
     * Returns the user-facing Stockfish trace shape.
     *
     * @return compact shape summary
     */
    protected String stockfishTraceShapeSummary() {
        int total = safeLength(snapshot == null ? null : snapshot.data("nnue.stockfish.transformed"));
        int stm = safeLength(snapshot == null ? null : snapshot.data("nnue.stockfish.transformed.us"));
        int fc0 = Math.max(0, safeLength(snapshot == null ? null : snapshot.data("nnue.stockfish.fc0.raw")) - 1);
        int fc1 = safeLength(snapshot == null ? null : snapshot.data("nnue.stockfish.fc1.clipped"));
        if (total > 0 && stm > 0 && fc0 > 0 && fc1 > 0) {
            return "HalfKAv2_hm -> " + total + " transformer lanes (" + stm
                    + " stm + " + (total - stm) + " opponent) -> FC0 " + fc0
                    + " hidden + fwd -> FC1 " + fc1 + " clipped -> FC2 weighted sum -> cp";
        }
        return "HalfKAv2_hm -> transformer (stm + opponent) -> FC0 hidden + forward skip -> FC1 clipped -> FC2 weighted sum -> cp";
    }

    /**
     * Draws the Stockfish trace footer for the selected transformer lane.
     *
     * @param g graphics
     * @param body detail body rectangle
     */
    protected void drawStockfishDetailedReadout(Graphics2D g, Rectangle body) {
        int lane = visibleSlots[selectedSlot];
        float[] transformed = snapshot.data("nnue.stockfish.transformed.us");
        float[] fwdWeights = preferredData(
                "nnue.stockfish.fc0.weights.fwd",
                "nnue.stockfish.fc0.weights.fwd.us");
        float[] fwdCp = snapshot.data("nnue.stockfish.fc0.fwd.cp");
        float[] output = snapshot.data("nnue.output.centipawns");
        float[] fc2 = snapshot.data("nnue.stockfish.fc2.contribution");
        String text = String.format(
                "lane %3d | transformer %+6.1f | FC0 fwd w %+6.2f | fwd branch %+6.2f cp | FC2 terms %d | output %+d cp",
                lane,
                valueAt(transformed, lane),
                valueAt(fwdWeights, lane),
                valueAt(fwdCp, 0),
                safeLength(fc2),
                Math.round(valueAt(output, 0)));
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(11, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(Ui.elide(text, fm, Math.max(24, body.width - 16)),
                body.x + 8, body.y + body.height - 8);
    }

    /**
     * Recomputes the visible-slot / visible-feature index lists from the
     * snapshot.
     */
}
