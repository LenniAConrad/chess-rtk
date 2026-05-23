/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.network;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import chess.nn.nnue.FeatureEncoder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

import static application.gui.workbench.network.NnueAtlas.*;
import static application.gui.workbench.network.NnueDrawing.*;
import static application.gui.workbench.network.NnueFeatureDecoder.*;
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
 * the NNUE-specific drawing plus the {@link Scrollable} plumbing the atlas mode
 * needs to expose every accumulator slot in a tall scrolling mosaic.</p>
 */

public abstract class NnueOverviewView extends NnueAtlasView {
    /** Serialization identifier for Swing component compatibility. */
    private static final long serialVersionUID = 1L;

    /**
     * Paints the raw view: a dense matrix of every active feature against
     * every accumulator slot. Each row is one active half-KP feature (with
     * its mini glyph and decoded label on the left); each cell shades that
     * feature's L1 weight contribution to one accumulator slot. The two
     * bottom rows summarise the result — the accumulator (post-ReLU) and
     * the per-slot contribution to the centipawn output — so the user can
     * see the whole "feature → accumulator → eval" computation in one
     * data-dense frame.
     *
     * @param g graphics
     * @param body body rectangle
     */
    @Override
    protected void paintRaw(Graphics2D g, Rectangle body) {
        int headerH = 38;
        TensorViz.drawSectionHeader(g,
    new Rectangle(body.x, body.y, body.width, headerH),
                "all NNUE activations",
                "every active half-KP row plus every accumulator, clipped, output-weight and contribution slot");
        float[] usIndices = snapshot.data("nnue.features.us.indices");
        float[] themIndices = snapshot.data("nnue.features.them.indices");
        float[] usImpact = snapshot.data("nnue.features.us.impact");
        float[] themImpact = snapshot.data("nnue.features.them.impact");
        float[] usWeights = snapshot.data("nnue.features.us.weights");
        float[] themWeights = snapshot.data("nnue.features.them.weights");
        int[] weightShape = snapshot.shape("nnue.features.us.weights");
        float[] accumulatorUs = snapshot.data("nnue.accumulator.us");
        float[] accumulatorThem = snapshot.data("nnue.accumulator.them");
        float[] clippedUs = snapshot.data("nnue.clipped.us");
        float[] clippedThem = snapshot.data("nnue.clipped.them");
        float[] outputWeightsUs = snapshot.data("nnue.output.weights.us");
        float[] outputWeightsThem = snapshot.data("nnue.output.weights.them");
        float[] contributionUs = snapshot.data("nnue.output.contribution.us");
        float[] contributionThem = snapshot.data("nnue.output.contribution.them");
        if (usIndices == null || themIndices == null
                || usWeights == null || themWeights == null || weightShape == null
                || weightShape.length < 2) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(12, Font.PLAIN));
            g.drawString("Raw NNUE data unavailable for this snapshot.",
                    body.x + 12, body.y + headerH + 30);
            return;
        }
        int hidden = weightShape[1];
        int usCount = usIndices.length;
        int themCount = themIndices.length;
        boolean whiteToMove = sideToMoveWhite();
        int groupRows = 2;
        int summaryRows = 8;
        int totalRows = usCount + themCount + groupRows + summaryRows;
        int rowLabelW = 230;
        int gridTop = body.y + headerH + 6;
        int gridH = body.height - headerH - 12;
        int rowH = Math.max(9, gridH / Math.max(1, totalRows));
        int gridLeft = body.x + rowLabelW;
        int gridW = body.width - rowLabelW - 4;

        float featScale = 0.0f;
        for (int i = 0; i < usCount; ++i) {
            int rowStart = i * hidden;
            for (int s = 0; s < hidden && rowStart + s < usWeights.length; ++s) {
                float a = Math.abs(usWeights[rowStart + s]);
                if (a > featScale) {
                    featScale = a;
                }
            }
        }
        for (int i = 0; i < themCount; ++i) {
            int rowStart = i * hidden;
            for (int s = 0; s < hidden && rowStart + s < themWeights.length; ++s) {
                float a = Math.abs(themWeights[rowStart + s]);
                if (a > featScale) {
                    featScale = a;
                }
            }
        }
        if (featScale <= 0.0f) {
            featScale = 1.0f;
        }
        featScale = scaleFor("rawNnue:featureWeights", featScale);
        float preReluScale = scaleFor("rawNnue:preRelu",
                Math.max(maxAbs(accumulatorUs), maxAbs(accumulatorThem)));
        float clippedScale = scaleFor("rawNnue:clipped",
                Math.max(maxAbs(clippedUs), maxAbs(clippedThem)));
        float outputWeightScale = scaleFor("rawNnue:outputWeights",
                Math.max(maxAbs(outputWeightsUs), maxAbs(outputWeightsThem)));
        float contribScale = scaleFor("rawNnue:contribution",
                Math.max(maxAbs(contributionUs), maxAbs(contributionThem)));

        int row = 0;
        drawRawGroupLabel(g, body, gridTop + row * rowH, rowH,
                "side to move features", usCount + " active sparse inputs");
        row++;
        for (int i = 0; i < usCount; ++i) {
            drawRawFeatureRow(g, body, gridTop + row * rowH, rowH, gridLeft, gridW,
                    usIndices, usImpact, usWeights, hidden, i, featScale,
                    "nnue.features.us.weights", "us feature", whiteToMove);
            row++;
        }
        drawRawGroupLabel(g, body, gridTop + row * rowH, rowH,
                "opponent features", themCount + " active sparse inputs");
        row++;
        for (int i = 0; i < themCount; ++i) {
            drawRawFeatureRow(g, body, gridTop + row * rowH, rowH, gridLeft, gridW,
                    themIndices, themImpact, themWeights, hidden, i, featScale,
                    "nnue.features.them.weights", "them feature", !whiteToMove);
            row++;
        }

        row = drawRawVectorRow(g, body, gridTop, rowH, gridLeft, gridW, row,
                "pre-ReLU accumulator (us)", accumulatorUs, hidden, preReluScale, false,
                "nnue.accumulator.us", "Dense accumulator before clipping, side to move");
        row = drawRawVectorRow(g, body, gridTop, rowH, gridLeft, gridW, row,
                "pre-ReLU accumulator (them)", accumulatorThem, hidden, preReluScale, false,
                "nnue.accumulator.them", "Dense accumulator before clipping, opponent side");
        row = drawRawVectorRow(g, body, gridTop, rowH, gridLeft, gridW, row,
                "post-ReLU clipped (us)", clippedUs, hidden, clippedScale, false,
                "nnue.clipped.us", "Clipped activation used by the output head, side to move");
        row = drawRawVectorRow(g, body, gridTop, rowH, gridLeft, gridW, row,
                "post-ReLU clipped (them)", clippedThem, hidden, clippedScale, false,
                "nnue.clipped.them", "Clipped activation used by the output head, opponent side");
        row = drawRawVectorRow(g, body, gridTop, rowH, gridLeft, gridW, row,
                "output weights (us)", outputWeightsUs, hidden, outputWeightScale, true,
                "nnue.output.weights.us", "Output-head weights applied to side-to-move slots");
        row = drawRawVectorRow(g, body, gridTop, rowH, gridLeft, gridW, row,
                "output weights (them)", outputWeightsThem, hidden, outputWeightScale, true,
                "nnue.output.weights.them", "Output-head weights applied to opponent slots");
        row = drawRawVectorRow(g, body, gridTop, rowH, gridLeft, gridW, row,
                "output contribution (us)", contributionUs, hidden, contribScale, true,
                "nnue.output.contribution.us", "Clipped activation × output weight, side to move");
        drawRawVectorRow(g, body, gridTop, rowH, gridLeft, gridW, row,
                "output contribution (them)", contributionThem, hidden, contribScale, true,
                "nnue.output.contribution.them", "Clipped activation × output weight, opponent side");
    }

    /**
     * Draws a group separator inside the dense NNUE activation table.
     *
     * @param g graphics
     * @param body body bounds
     * @param y row y coordinate
     * @param rowH row height
     * @param title group title
     * @param detail secondary text
     */
    protected void drawRawGroupLabel(Graphics2D g, Rectangle body, int y, int rowH,
            String title, String detail) {
        g.setColor(Theme.LINE);
        g.drawLine(body.x, y + rowH - 1, body.x + body.width, y + rowH - 1);
        g.setFont(Theme.font(10, Font.BOLD));
        g.setColor(Theme.TEXT);
        g.drawString(title, body.x + 4, y + rowH / 2 + 4);
        g.setFont(Theme.font(10, Font.PLAIN));
        g.setColor(Theme.MUTED);
        g.drawString(detail, body.x + 140, y + rowH / 2 + 4);
    }

    /**
     * Draws one active sparse-feature row in the dense NNUE activation table.
     *
     * @param g graphics
     * @param body body bounds
     * @param y row y coordinate
     * @param rowH row height
     * @param gridLeft heatmap x coordinate
     * @param gridW heatmap width
     * @param indices feature indices
     * @param impact approximate feature impacts
     * @param weights feature-to-accumulator weights
     * @param hidden hidden accumulator width
     * @param sourceIndex feature row index
     * @param scale heatmap scale
     * @param dataKey snapshot data key
     * @param sideLabel side label for tooltips
     * @param whitePerspective true when feature indices use White perspective
     */
    protected void drawRawFeatureRow(Graphics2D g, Rectangle body, int y, int rowH,
            int gridLeft, int gridW, float[] indices, float[] impact,
            float[] weights, int hidden, int sourceIndex, float scale,
            String dataKey, String sideLabel, boolean whitePerspective) {
        int featureIdx = Math.round(indices[sourceIndex]);
        float v = impact == null || sourceIndex >= impact.length ? 0.0f : impact[sourceIndex];
        int glyphSize = Math.max(7, Math.min(rowH - 2, 26));
        Rectangle glyph = new Rectangle(body.x + 4, y + (rowH - glyphSize) / 2,
                glyphSize, glyphSize);
        HalfKpFeature feature = decodeHalfKpFeature(featureIdx, whitePerspective);
        TensorViz.drawHalfKpGlyph(g, glyph,
                feature.kingSquare, feature.pieceCode, feature.pieceSquare);
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(10, Font.PLAIN));
        String label = "#" + featureIdx + "  " + decodeHalfKP(featureIdx, whitePerspective);
        g.drawString(label, glyph.x + glyphSize + 6, y + rowH / 2 + 4);
        g.setColor(v >= 0 ? TensorViz.POSITIVE : TensorViz.NEGATIVE);
        g.setFont(Theme.font(10, Font.BOLD));
        g.drawString(String.format("%+5.1f cp", v),
                gridLeft - 56, y + rowH / 2 + 4);

        float[] row = new float[hidden];
        int rowStart = sourceIndex * hidden;
        for (int s = 0; s < hidden && rowStart + s < weights.length; ++s) {
            row[s] = weights[rowStart + s];
        }
        Rectangle cell = new Rectangle(gridLeft, y + 1, gridW, Math.max(1, rowH - 2));
        TensorViz.drawHeatmap(g, cell, row, hidden, 1, scale, true);
        hitRegions.addInspectable(cell,
                "Feature #" + featureIdx + " · " + decodeHalfKP(featureIdx, whitePerspective),
                sideLabel + " row of the sparse feature → accumulator matrix",
                String.format("impact %+.2f cp · weights range ±%.3f", v, scale),
                dataKey, rowStart, hidden, 0,
                "1x" + hidden);
    }

    /**
     * Draws one dense vector row in the NNUE activation table.
     *
     * @param g graphics
     * @param body body bounds
     * @param gridTop top y coordinate for the table
     * @param rowH row height
     * @param gridLeft heatmap x coordinate
     * @param gridW heatmap width
     * @param row row index
     * @param label row label
     * @param values dense vector
     * @param hidden expected hidden width
     * @param scale heatmap scale
     * @param signed true for signed heatmap
     * @param dataKey snapshot data key
     * @param detail tooltip detail
     * @return next row index
     */
    protected int drawRawVectorRow(Graphics2D g, Rectangle body, int gridTop,
            int rowH, int gridLeft, int gridW, int row, String label,
            float[] values, int hidden, float scale, boolean signed,
            String dataKey, String detail) {
        if (values == null) {
            return row;
        }
        int y = gridTop + row * rowH;
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(10, Font.BOLD));
        g.drawString(label, body.x + 4, y + rowH / 2 + 4);
        Rectangle cell = new Rectangle(gridLeft, y + 1, gridW, Math.max(1, rowH - 2));
        TensorViz.drawHeatmap(g, cell, values,
                Math.min(values.length, hidden), 1, Math.max(scale, 1e-4f), signed);
        hitRegions.addInspectable(cell, label, detail,
                String.format("all %d slots · range %s%.3f",
                        values.length, signed ? "±" : "0..", scale),
                dataKey, 0, values.length, 0, "1x" + values.length);
        return row + 1;
    }


    /**
     * Paints the abstract overview ("why is the score what it is?").
     *
     * <p>The overview is intentionally curated: final score first, board and
     * active feature anchors next, then the accumulator/trunk summary and the
     * signed feature-driver ledger. Dense tensors remain in Trace/All/Atlas.</p>
     *
     * @param g graphics
     * @param body body rectangle
     */
    @Override
    protected void paintAbstract(Graphics2D g, Rectangle body) {
        int gap = 12;
        int summaryH = body.width < 760 ? 78 : 84;
        Rectangle summary = new Rectangle(body.x, body.y, body.width, Math.min(summaryH, body.height));
        paintOverviewSummary(g, summary);
        Rectangle rest = new Rectangle(body.x, summary.y + summary.height + gap,
                body.width, Math.max(1, body.y + body.height - summary.y - summary.height - gap));
        if (rest.height <= 1) {
            return;
        }
        if (body.width < 820) {
            int boardH = Math.max(170, Math.min(320, rest.height / 3));
            int signalH = Math.max(190, Math.min(320, rest.height / 3));
            int inspectorH = Math.max(130, Math.min(220, rest.height / 5));
            int y = rest.y;
            Rectangle boardRect = new Rectangle(rest.x, y, rest.width, Math.min(boardH, rest.height));
            y += boardRect.height + gap;
            Rectangle signal = new Rectangle(rest.x, y, rest.width,
                    Math.min(signalH, Math.max(1, rest.y + rest.height - y)));
            y += signal.height + gap;
            Rectangle featureInspector = new Rectangle(rest.x, y, rest.width,
                    Math.min(inspectorH, Math.max(1, rest.y + rest.height - y)));
            y += featureInspector.height + gap;
            Rectangle contributors = new Rectangle(rest.x, y, rest.width,
                    Math.max(1, rest.y + rest.height - y));
            paintPositionOverview(g, boardRect);
            paintOverviewSignal(g, signal);
            paintFeatureInspector(g, featureInspector);
            paintTopContributors(g, contributors);
            return;
        }

        int driversH = Math.min(Math.max(250, rest.height * 38 / 100),
                Math.max(250, rest.height - 260));
        driversH = Math.min(driversH, Math.max(1, rest.height - 220));
        int topH = Math.max(1, rest.height - driversH - gap);
        Rectangle top = new Rectangle(rest.x, rest.y, rest.width, topH);
        Rectangle contributors = new Rectangle(rest.x, top.y + top.height + gap,
                rest.width, Math.max(1, driversH));

        int inspectorW = Math.min(360, Math.max(270, top.width / 5));
        int boardW = Math.min(Math.max(330, top.width / 3), Math.max(330, top.height + 80));
        int signalW = Math.max(240, top.width - boardW - inspectorW - gap * 2);
        if (signalW < 300) {
            inspectorW = Math.min(inspectorW, Math.max(230, top.width / 4));
            boardW = Math.min(boardW, Math.max(260, top.width - inspectorW - 300 - gap * 2));
            signalW = Math.max(1, top.width - boardW - inspectorW - gap * 2);
        }
        Rectangle boardRect = new Rectangle(top.x, top.y, boardW, top.height);
        Rectangle signal = new Rectangle(boardRect.x + boardRect.width + gap, top.y,
                signalW, top.height);
        Rectangle featureInspector = new Rectangle(signal.x + signal.width + gap, top.y,
                Math.max(1, top.x + top.width - signal.x - signal.width - gap), top.height);

        paintPositionOverview(g, boardRect);
        paintOverviewSignal(g, signal);
        paintFeatureInspector(g, featureInspector);
        paintTopContributors(g, contributors);
    }

    /**
     * Paints the top score-and-driver summary band.
     *
     * @param g graphics
     * @param r summary rectangle
     */
    protected void paintOverviewSummary(Graphics2D g, Rectangle r) {
        TensorViz.drawCard(g, r, null, null, TensorViz.FOCUS);
        float cp = valueAt(snapshot.data("nnue.output.centipawns"), 0);
        FeatureDriver raiser = strongestDriver(true);
        FeatureDriver lowerer = strongestDriver(false);
        float[] us = snapshot.data("nnue.features.us.indices");
        float[] them = snapshot.data("nnue.features.them.indices");
        int activeUs = safeLength(us);
        int activeThem = safeLength(them);
        int chipGap = 8;
        int chipCount = r.width < 780 ? 3 : 4;
        int chipW = Math.max(1, (r.width - 20 - chipGap * (chipCount - 1)) / chipCount);
        int chipH = Math.max(46, r.height - 18);
        int x = r.x + 10;
        Rectangle eval = new Rectangle(x, r.y + 9, chipW, chipH);
        paintSummaryChip(g, eval, "eval", String.format("%+d cp", Math.round(cp)),
                sideToMoveWhite() ? "white to move" : "black to move",
                cp >= 0.0f ? TensorViz.POSITIVE : TensorViz.NEGATIVE);
        x += chipW + chipGap;
        paintSummaryChip(g, new Rectangle(x, r.y + 9, chipW, chipH),
                "top raiser",
                raiser.valid() ? driverLabel(raiser) : "-",
                raiser.valid() ? String.format("%+.1f cp", raiser.impact()) : "none",
                TensorViz.POSITIVE);
        x += chipW + chipGap;
        paintSummaryChip(g, new Rectangle(x, r.y + 9, chipW, chipH),
                "top lowerer",
                lowerer.valid() ? driverLabel(lowerer) : "-",
                lowerer.valid() ? String.format("%+.1f cp", lowerer.impact()) : "none",
                TensorViz.NEGATIVE);
        if (chipCount > 3) {
            x += chipW + chipGap;
            String label = isStockfishSnapshot() ? "HalfKA" : "HalfKP";
            paintSummaryChip(g, new Rectangle(x, r.y + 9, chipW, chipH),
                    label,
                    activeUs + " / " + activeThem,
                    isStockfishSnapshot() ? stockfishStackShort() : "us / them active",
                    TensorViz.FOCUS);
        }
        hitRegions.addInspectable(r,
                "Overview summary",
                "Final score and strongest active feature drivers.",
                String.format("%+d cp · us %d · them %d", Math.round(cp), activeUs, activeThem),
                "nnue.output.centipawns", 0, 1, 0, "1");
    }


    /**
     * Paints the central signal stack: accumulator comparison and dense trunk.
     *
     * @param g graphics context
     * @param r drawing bounds
     */
    protected void paintOverviewSignal(Graphics2D g, Rectangle r) {
        int gap = 10;
        boolean stockfish = isStockfishSnapshot();
        int minTrunkH = stockfish ? 196 : 156;
        int targetAccumulatorH = Math.round(r.height * (stockfish ? 0.46f : 0.56f));
        int accumulatorH = Math.max(118, targetAccumulatorH);
        if (r.height - accumulatorH - gap < minTrunkH) {
            accumulatorH = Math.max(100, r.height - minTrunkH - gap);
        }
        if (accumulatorH < 100) {
            accumulatorH = Math.max(1, r.height / 2);
        }
        Rectangle accumulator = new Rectangle(r.x, r.y, r.width, Math.max(1, accumulatorH));
        Rectangle trunk = new Rectangle(r.x, accumulator.y + accumulator.height + gap,
                r.width, Math.max(1, r.y + r.height - accumulator.y - accumulator.height - gap));
        paintAccumulatorOverview(g, accumulator);
        paintTrunkOverview(g, trunk);
    }

    /**
     * Paints a curated internal inspector for the selected or strongest feature.
     *
     * @param g graphics context
     * @param r drawing bounds
     */
    protected void paintFeatureInspector(Graphics2D g, Rectangle r) {
        FeatureDriver driver = selectedFeature >= 0
                ? driverForFeature(selectedFeature)
                : strongestDriverByAbs();
        Color accent = !driver.valid() ? TensorViz.FOCUS
                : driver.impact() >= 0.0f ? TensorViz.POSITIVE : TensorViz.NEGATIVE;
        TensorViz.drawCard(g, r,
                driver.valid() ? "selected feature" : "feature inspector",
                driver.valid() ? "Half-KP active feature" : "no active feature",
                accent);
        Rectangle content = new Rectangle(r.x + 12, r.y + 42,
                Math.max(1, r.width - 24), Math.max(1, r.height - 54));
        if (!driver.valid()) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(11, Font.PLAIN));
            g.drawString("No active Half-KP feature in this snapshot.", content.x, content.y + 16);
            return;
        }

        HalfKpFeature feature = decodeHalfKpFeature(driver.featureIndex(), sideToMoveWhite());
        int glyph = Math.min(86, Math.max(48, Math.min(content.width / 3, content.height - 18)));
        Rectangle glyphRect = new Rectangle(content.x, content.y, glyph, glyph);
        TensorViz.drawHalfKpGlyph(g, glyphRect,
                feature.kingSquare, feature.pieceCode, feature.pieceSquare);
        int textX = glyphRect.x + glyphRect.width + 12;
        int textW = Math.max(1, content.x + content.width - textX);
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(18, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(Ui.elide(driverLabel(driver), fm, textW), textX, content.y + 19);
        g.setColor(accent);
        g.setFont(Theme.font(14, Font.BOLD));
        fm = g.getFontMetrics();
        g.drawString(Ui.elide(String.format("impact %+.2f cp", driver.impact()), fm, textW),
                textX, content.y + 40);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(11, Font.PLAIN));
        fm = g.getFontMetrics();
        String rank = driver.rank() > 0 ? "rank #" + driver.rank() + " by |impact|" : "active feature";
        g.drawString(Ui.elide("#" + driver.featureIndex() + " · " + rank, fm, textW),
                textX, content.y + 60);

        float[] weights = snapshot.data("nnue.features.us.weights");
        int[] shape = snapshot.shape("nnue.features.us.weights");
        int stride = shape != null && shape.length >= 2 ? shape[1] : 0;
        int heatY = content.y + Math.max(glyph + 12, 82);
        if (weights != null && stride > 0 && driver.row() >= 0) {
            int offset = driver.row() * stride;
            int len = Math.min(stride, Math.max(0, weights.length - offset));
            if (len > 0 && heatY < content.y + content.height - 16) {
                float[] row = new float[len];
                System.arraycopy(weights, offset, row, 0, len);
                g.setColor(Theme.TEXT);
                g.setFont(Theme.font(11, Font.BOLD));
                g.drawString("feature -> L1 weight row", content.x, heatY - 4);
                Rectangle heat = new Rectangle(content.x, heatY + 4, content.width,
                        Math.max(18, Math.min(42, content.y + content.height - heatY - 8)));
                TensorViz.drawHeatmap(g, heat, row, row.length, 1,
                        Math.max(1e-4f, maxAbs(row)), true);
                hitRegions.addInline(heat,
                        "Feature #" + driver.featureIndex() + "  " + driverLabel(driver),
                        "Half-KP feature weight row.",
                        String.format("impact %+.2f cp", driver.impact()),
                        row, "1x" + row.length);
            }
        }
    }

    /**
     * Returns a compact network stack label for the summary band.
     *
     * @return compact stack label
     */
    protected String stockfishStackShort() {
        float[] transformed = snapshot.data("nnue.stockfish.transformed");
        float[] transformedUs = snapshot.data("nnue.stockfish.transformed.us");
        float[] fc0 = snapshot.data("nnue.stockfish.fc0.raw");
        float[] fc1 = snapshot.data("nnue.stockfish.fc1.clipped");
        int input = safeLength(transformed);
        if (input == 0) {
            input = safeLength(transformedUs);
        }
        return input + " -> " + Math.max(0, safeLength(fc0) - 1) + " -> " + safeLength(fc1);
    }

    /**
     * Returns the strongest driver matching one sign.
     *
     * @param positive true for positive impact, false for negative
     * @return strongest matching driver
     */
    protected FeatureDriver strongestDriver(boolean positive) {
        float[] indices = snapshot.data("nnue.features.us.indices");
        float[] impact = snapshot.data("nnue.features.us.impact");
        if (indices == null || impact == null) {
            return FeatureDriver.invalid();
        }
        int best = -1;
        float bestAbs = -1.0f;
        for (int i = 0; i < Math.min(indices.length, impact.length); i++) {
            if (positive ? impact[i] <= 0.0f : impact[i] >= 0.0f) {
                continue;
            }
            float abs = Math.abs(impact[i]);
            if (abs > bestAbs) {
                bestAbs = abs;
                best = i;
            }
        }
    return featureDriverAt(best, indices, impact);
    }

    /**
     * Returns the strongest active feature by absolute impact.
     *
     * @return strongest absolute-impact driver
     */
    protected FeatureDriver strongestDriverByAbs() {
        float[] indices = snapshot.data("nnue.features.us.indices");
        float[] impact = snapshot.data("nnue.features.us.impact");
        if (indices == null || impact == null) {
            return FeatureDriver.invalid();
        }
    return featureDriverAt(strongestAbsIndex(impact), indices, impact);
    }

    /**
     * Returns the active feature driver for a sparse feature index.
     *
     * @param featureIndex sparse feature index
     * @return feature driver, or invalid sentinel
     */
    protected FeatureDriver driverForFeature(int featureIndex) {
        float[] indices = snapshot.data("nnue.features.us.indices");
        float[] impact = snapshot.data("nnue.features.us.impact");
        if (indices == null || impact == null) {
            return FeatureDriver.invalid();
        }
        for (int i = 0; i < Math.min(indices.length, impact.length); i++) {
            if (Math.round(indices[i]) == featureIndex) {
    return featureDriverAt(i, indices, impact);
            }
        }
        return FeatureDriver.invalid();
    }

    /**
     * Builds a feature driver record for one active row.
     *
     * @param row active feature row
     * @param indices active feature indices
     * @param impact active feature impacts
     * @return feature driver, or invalid sentinel
     */
    protected FeatureDriver featureDriverAt(int row, float[] indices, float[] impact) {
        if (row < 0 || indices == null || impact == null
                || row >= indices.length || row >= impact.length) {
            return FeatureDriver.invalid();
        }
    return new FeatureDriver(row, Math.round(indices[row]), impact[row],
                featureImpactRank(row, impact), true);
    }


    /**
     * Returns a human-readable label for a driver.
     *
     * @param driver feature driver
     * @return display label
     */
    protected String driverLabel(FeatureDriver driver) {
        return driver.valid() ? decodeUsHalfKP(driver.featureIndex()) : "-";
    }

    /**
     * Paints the current position card used by the NNUE overview column.
     *
     * @param g graphics
     * @param r destination rectangle
     */
    protected void paintPositionOverview(Graphics2D g, Rectangle r) {
        TensorViz.drawCard(g, r,
                "board",
                "each NNUE input = a (king square, piece) pair; click a feature to light its squares",
                TensorViz.FOCUS);
        int availableW = Math.max(24, r.width - 16);
        int availableH = Math.max(24, r.height - 44);
        int side = Math.max(24, Math.min(availableW, availableH));
        Rectangle innerBoard = new Rectangle(r.x + (r.width - side) / 2,
                r.y + 36 + Math.max(0, (availableH - side) / 2),
                side, side);
        TensorViz.drawMiniBoard(g, innerBoard);
        TensorViz.drawPositionPieces(g, innerBoard, fen);
        paintOverviewFeatureOverlay(g, innerBoard);
        TensorViz.drawBoardCoordinates(g, innerBoard);
        drawWhiteBottomLabel(g, innerBoard, r.y + r.height);
        hitRegions.add(innerBoard, "Current position",
                fen == null ? "no FEN" : fen,
                "NNUE half-KP features are derived from this position");
    }

    /**
     * Highlights the king and piece squares of the currently selected feature
     * on the mini position board to connect the feature list to the board.
     *
     * @param g graphics
     * @param board mini-board rectangle
     */
    protected void paintSelectedFeatureOverlay(Graphics2D g, Rectangle board) {
        if (selectedFeature < 0) {
            return;
        }
        HalfKpFeature feature = decodeHalfKpFeature(selectedFeature, sideToMoveWhite());
        if (!feature.valid) {
            return;
        }
        highlightSquare(g, board, feature.kingSquare, Theme.withAlpha(TensorViz.FOCUS, 110));
        if (feature.pieceSquare != feature.kingSquare) {
            boolean enemy = feature.pieceCode >= 5;
            highlightSquare(g, board, feature.pieceSquare,
                    enemy ? Theme.withAlpha(TensorViz.NEGATIVE, 110)
                            : Theme.withAlpha(TensorViz.POSITIVE, 110));
        }
    }

    /**
     * Paints selected or top-driver feature anchors on the overview board.
     *
     * @param g graphics context
     * @param board board bounds
     */
    protected void paintOverviewFeatureOverlay(Graphics2D g, Rectangle board) {
        if (selectedBoardSquare >= 0) {
            TensorViz.drawBoardSquareRing(g, board, selectedBoardSquare,
                    TensorViz.FOCUS);
        }
        if (selectedFeature >= 0) {
            paintSelectedFeatureOverlay(g, board);
            return;
        }
        paintDriverFeatureOverlay(g, board, strongestDriver(true),
                Theme.withAlpha(TensorViz.POSITIVE, 110));
        paintDriverFeatureOverlay(g, board, strongestDriver(false),
                Theme.withAlpha(TensorViz.NEGATIVE, 105));
    }

    /**
     * Paints one driver's king and piece anchors on the board.
     *
     * @param g graphics context
     * @param board board bounds
     * @param driver feature driver
     * @param tint piece-square tint
     */
    protected void paintDriverFeatureOverlay(Graphics2D g, Rectangle board,
            FeatureDriver driver, Color tint) {
        if (!driver.valid()) {
            return;
        }
        HalfKpFeature feature = decodeHalfKpFeature(driver.featureIndex(), sideToMoveWhite());
        if (!feature.valid) {
            return;
        }
        highlightSquare(g, board, feature.kingSquare, Theme.withAlpha(TensorViz.FOCUS, 80));
        highlightSquare(g, board, feature.pieceSquare, tint);
    }



    /**
     * Paints the per-piece contributor columns. Each active half-KP feature
     * is one piece-and-king-square pairing; the column on the left lists the
     * features pulling the eval up for us, the column on the right lists the
     * features pulling it down (i.e. helping them). Rows are sized to fit as
     * many features as space allows.
     *
     * @param g graphics
     * @param r area rectangle
     */
    protected void paintTopContributors(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 36),
                "top feature drivers",
                "largest half-KP features pushing the evaluation up or down");
        float[] indices = snapshot.data("nnue.features.us.indices");
        float[] impact = snapshot.data("nnue.features.us.impact");
        if (indices == null || impact == null || indices.length == 0) {
            return;
        }
        if (r.width >= 680) {
            int strongest = strongestAbsIndex(impact);
            if (strongest >= 0 && strongest < indices.length) {
                int featureIdx = Math.round(indices[strongest]);
                Color accent = impact[strongest] >= 0.0f
                        ? TensorViz.POSITIVE
                        : TensorViz.NEGATIVE;
                TensorViz.drawInfoChip(g,
    new Rectangle(r.x + r.width - 270, r.y + 6, 260, 24),
                        "strongest",
                        decodeUsHalfKP(featureIdx) + "  " + String.format("%+.1f cp", impact[strongest]),
                        accent);
            }
        }
        int colGap = 12;
        int colW = (r.width - colGap) / 2;
        Rectangle posCol = new Rectangle(r.x, r.y + 40, colW, r.height - 40);
        Rectangle negCol = new Rectangle(r.x + colW + colGap, r.y + 40, colW, r.height - 40);
        paintContributorColumn(g, posCol, indices, impact, true);
        paintContributorColumn(g, negCol, indices, impact, false);
    }

    /**
     * Paints one signed contributor column. Sizes rows to fit every feature
     * of the matching sign in the available height rather than capping at a
     * fixed top-N, so the user can see the whole active-feature set at once.
     *
     * @param g graphics
     * @param r column rectangle
     * @param indices feature indices array
     * @param impact feature impacts array
     * @param positive true for positive column, false for negative
     */
    protected void paintContributorColumn(Graphics2D g, Rectangle r,
            float[] indices, float[] impact, boolean positive) {
        java.util.List<Integer> selected = new java.util.ArrayList<>();
        for (int i = 0; i < impact.length; i++) {
            if (positive ? impact[i] > 0 : impact[i] < 0) {
                selected.add(i);
            }
        }
        selected.sort((a, b) -> {
            float va = Math.abs(impact[a]);
            float vb = Math.abs(impact[b]);
            return Float.compare(vb, va);
        });
        int rowsByHeight = Math.max(0, (r.height - 22) / 20);
        int rows = Math.min(selected.size(), rowsByHeight);
        int rowH = rows == 0 ? 0
                : Math.max(18, Math.min(34, (r.height - 22) / Math.max(1, rows)));
        float maxAbs = 0.0f;
        for (float v : impact) {
            maxAbs = Math.max(maxAbs, Math.abs(v));
        }
        if (maxAbs <= 0.0f) {
            maxAbs = 1.0f;
        }
        g.setColor(positive ? TensorViz.POSITIVE : TensorViz.NEGATIVE);
        g.setFont(Theme.font(11, Font.BOLD));
        g.drawString(positive ? "raise" : "lower",
                r.x + 2, r.y + 14);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(10, Font.PLAIN));
        int totalSign = selected.size();
        String count = rows == totalSign ? totalSign + " features" : "top " + rows + " of " + totalSign;
        g.drawString(count, r.x + 54, r.y + 14);
        if (rows == 0) {
            String empty = totalSign == 0
                    ? "(no " + (positive ? "positive" : "negative") + " features active)"
                    : "(" + totalSign + " features, not enough vertical room)";
            g.drawString(empty, r.x + 4, r.y + 32);
            return;
        }
        int[] weightShape = snapshot.shape("nnue.features.us.weights");
        int hiddenStride = weightShape != null && weightShape.length >= 2 ? weightShape[1] : 0;
        boolean compact = rowH < 26;
        g.setFont(Theme.font(compact ? 10 : 11, Font.PLAIN));
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < rows; ++i) {
            int idx = selected.get(i);
            int featureIdx = Math.round(indices[idx]);
            float v = impact[idx];
            int y = r.y + 22 + i * rowH;
            Rectangle row = new Rectangle(r.x, y, r.width, rowH - 2);
            boolean isSelected = featureIdx == selectedFeature;
            g.setColor(isSelected ? Theme.SELECTION_SOLID
                    : (i % 2 == 0 ? Theme.PANEL_SOLID : Theme.ELEVATED_SOLID));
            g.fillRect(row.x, row.y, row.width, row.height);
            g.setColor(isSelected ? TensorViz.FOCUS : Theme.LINE);
            g.drawRect(row.x, row.y, row.width - 1, row.height - 1);
            int glyphSize = Math.min(row.height - 2, compact ? 18 : 28);
            Rectangle glyph = new Rectangle(row.x + 2, row.y + (row.height - glyphSize) / 2,
                    glyphSize, glyphSize);
            HalfKpFeature feature = decodeHalfKpFeature(featureIdx, sideToMoveWhite());
            TensorViz.drawHalfKpGlyph(g, glyph,
                    feature.kingSquare, feature.pieceCode, feature.pieceSquare);
            String decoded = decodeUsHalfKP(featureIdx);
            String label = compact ? decoded : ("#" + featureIdx + "  " + decoded);
            g.setColor(Theme.TEXT);
            int textX = row.x + glyphSize + 6;
            int textY = row.y + row.height / 2 + (compact ? 3 : 4);
            g.drawString(label, textX, textY);
            String tail = String.format("%+5.1f cp", v);
            int tailW = fm.stringWidth(tail);
            int barX = textX + Math.max(compact ? 80 : 140, fm.stringWidth(label) + 8);
            int barW = row.x + row.width - barX - tailW - 8;
            if (barW > 16) {
                int barH = compact ? 8 : 12;
                Rectangle bar = new Rectangle(barX, row.y + (row.height - barH) / 2, barW, barH);
                TensorViz.drawHorizontalBar(g, bar, v, maxAbs, null);
            }
            g.setColor(v >= 0 ? TensorViz.POSITIVE : TensorViz.NEGATIVE);
            g.drawString(tail, row.x + row.width - tailW - 4, textY);
            if (hiddenStride > 0) {
                hitRegions.addInspectable(row,
                        "Feature #" + featureIdx + "  " + decoded,
                        "Half-KP active feature · row of feature->L1 weight matrix",
                        String.format("impact %+.2f cp", v),
                        "nnue.features.us.weights",
                        idx * hiddenStride, hiddenStride, 0,
                        "1x" + hiddenStride);
            } else {
                hitRegions.add(row,
                        "Feature #" + featureIdx + "  " + decoded,
                        "Half-KP active feature",
                        String.format("impact %+.2f cp", v));
            }
        }
    }

    /**
     * Paints the accumulator slot overview heatmap. The colour scale is the
     * combined max-abs of the us+them clipped activations so both sides stay
     * directly comparable, instead of being hard-pinned to 1.0 (which left
     * typical positions rendering almost invisibly). When the fixed-scale
     * toggle is on the scale is pinned across position changes via
     * {@link #scaleFor}.
     *
     * @param g graphics
     * @param r rectangle
     */
    protected void paintAccumulatorOverview(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
                "accumulator — first hidden layer",
                "one cell per neuron · 'us' = side-to-move view, 'them' = opponent view");
        float[] us = snapshot.data("nnue.clipped.us");
        float[] them = snapshot.data("nnue.clipped.them");
        if (us == null || them == null) {
            return;
        }
        int rows = 16;
        int colsPerSide = us.length / rows;
        int gap = 8;
        int sideW = (r.width - gap - 16) / 2;
        int ledgerH = r.height >= 190 ? 48 : 0;
        int heatH = Math.max(36, r.height - 56 - ledgerH - (ledgerH == 0 ? 0 : 8));
        Rectangle leftMap = new Rectangle(r.x + 8, r.y + 48, sideW, heatH);
        Rectangle rightMap = new Rectangle(r.x + sideW + 8 + gap, r.y + 48, sideW, heatH);
        float[] stats = TensorViz.summarize(us);
        float[] them2 = TensorViz.summarize(them);
        float dynamicMax = Math.max(Math.max(Math.abs(stats[3]), Math.abs(stats[4])),
                Math.max(Math.abs(them2[3]), Math.abs(them2[4])));
        float scale = scaleFor("accumulator", dynamicMax);
        TensorViz.drawHeatmap(g, leftMap, us, colsPerSide, rows, scale, false);
        TensorViz.drawHeatmap(g, rightMap, them, colsPerSide, rows, scale, false);
        float[] contribution = totalContribution();
        int topSlot = strongestAbsIndex(contribution);
        if (topSlot >= 0 && topSlot < us.length) {
            Color accent = contribution[topSlot] >= 0.0f
                    ? TensorViz.POSITIVE
                    : TensorViz.NEGATIVE;
            drawGridCellRing(g, leftMap, topSlot, colsPerSide, rows, accent);
            if (topSlot < them.length) {
                drawGridCellRing(g, rightMap, topSlot, colsPerSide, rows, accent);
            }
            if (r.width >= 560) {
                TensorViz.drawInfoChip(g,
    new Rectangle(r.x + r.width - 216, r.y + 8, 206, 24),
                        "top net slot",
                        "#" + topSlot + "  " + String.format("%+.2f", contribution[topSlot]),
                        accent);
            }
        }
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(10, Font.BOLD));
        g.drawString("us", leftMap.x, leftMap.y - 4);
        g.drawString("them", rightMap.x, rightMap.y - 4);
        hitRegions.addInspectable(leftMap,
                "Accumulator (us, clipped)",
                "Per-slot clipped activation for side-to-move; product of all active features.",
                String.format("rms %.2f · min %+.2f · max %+.2f", stats[2], stats[3], stats[4]),
                "nnue.clipped.us", 0, 0, colsPerSide, rows + "x" + colsPerSide);
        hitRegions.addInspectable(rightMap,
                "Accumulator (them, clipped)",
                "Per-slot clipped activation for the opponent side.",
                String.format("rms %.2f · min %+.2f · max %+.2f", them2[2], them2[3], them2[4]),
                "nnue.clipped.them", 0, 0, colsPerSide, rows + "x" + colsPerSide);
        if (ledgerH > 0) {
            paintContributionLedger(g, new Rectangle(r.x + 8,
                    r.y + r.height - ledgerH, r.width - 16, ledgerH));
        }
    }

    /**
     * Paints the post-accumulator path in the overview. This mirrors the left
     * column's feature rows with the right column's dense trunk rows, so the
     * summary view shows what happens after the accumulator without switching
     * to the dense All mode.
     *
     * @param g graphics
     * @param r destination rectangle
     */
    protected void paintTrunkOverview(Graphics2D g, Rectangle r) {
        if (isStockfishSnapshot()) {
            paintStockfishTrunkOverview(g, r);
            return;
        }
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
                "trunk — accumulator to score",
                "raw (us minus them) -> clipped ReLU -> x output weight -> centipawn contribution");
        float[] rawNet = subtractVectors(snapshot.data("nnue.accumulator.us"),
                snapshot.data("nnue.accumulator.them"));
        float[] clippedNet = subtractVectors(snapshot.data("nnue.clipped.us"),
                snapshot.data("nnue.clipped.them"));
        float[] outputWeightNet = addVectors(snapshot.data("nnue.output.weights.us"),
                snapshot.data("nnue.output.weights.them"));
        float[] contribution = totalContribution();
        String contributionKey = snapshot.data("nnue.output.contribution.total") == null
                ? null : "nnue.output.contribution.total";
        if (rawNet == null && clippedNet == null && outputWeightNet == null && contribution == null) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(11, Font.PLAIN));
            g.drawString("No trunk tensors in this snapshot.", r.x + 10, r.y + 70);
            return;
        }

        int topSlot = strongestAbsIndex(contribution);
        if (topSlot >= 0 && r.width >= 420) {
            float value = valueAt(contribution, topSlot);
            TensorViz.drawInfoChip(g,
    new Rectangle(r.x + r.width - 206, r.y + 8, 196, 24),
                    "top net",
                    "#" + topSlot + "  " + String.format("%+.3f", value),
                    value >= 0.0f ? TensorViz.POSITIVE : TensorViz.NEGATIVE);
        }

        Rectangle content = new Rectangle(r.x + 8, r.y + 48,
                Math.max(1, r.width - 16), Math.max(1, r.height - 56));
        int rows = 4;
        int rowGap = 5;
        int rowH = Math.max(18, Math.min(30, (content.height - rowGap * (rows - 1)) / rows));
        int y = content.y;
        y = drawOverviewVectorRow(g, new Rectangle(content.x, y, content.width, rowH),
                "raw net", rawNet, true, null,
                "Side-to-move raw accumulator minus opponent raw accumulator.",
                "overview.raw.net", topSlot);
        y += rowGap;
        y = drawOverviewVectorRow(g, new Rectangle(content.x, y, content.width, rowH),
                "clipped", clippedNet, true, null,
                "Post-ReLU side-to-move activation minus opponent activation.",
                "overview.clipped.net", topSlot);
        y += rowGap;
        y = drawOverviewVectorRow(g, new Rectangle(content.x, y, content.width, rowH),
                "out weight", outputWeightNet, true, null,
                "Output-head weight used by the final affine layer.",
                "overview.output.weights", topSlot);
        y += rowGap;
        drawOverviewVectorRow(g, new Rectangle(content.x, y, content.width, rowH),
                "contrib", contribution, true, contributionKey,
                "Per-slot contribution after activation and output weight.",
                "overview.contribution", topSlot);
    }

    /**
     * Paints the Stockfish-shaped trunk summary used for upstream .nnue files.
     *
     * @param g graphics
     * @param r destination rectangle
     */
    protected void paintStockfishTrunkOverview(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
                "Stockfish trunk",
                "feature transformer -> FC0 -> FC1 -> FC2 contribution");
        float[] transformedUs = snapshot.data("nnue.stockfish.transformed.us");
        float[] transformedThem = snapshot.data("nnue.stockfish.transformed.them");
        float[] fc0 = withoutLast(snapshot.data("nnue.stockfish.fc0.raw"));
        float[] fc1 = snapshot.data("nnue.stockfish.fc1.clipped");
        float[] fc2 = snapshot.data("nnue.stockfish.fc2.contribution");
        int topTerm = strongestAbsIndex(fc2);
        if (topTerm >= 0 && r.width >= 420) {
            float value = valueAt(fc2, topTerm);
            TensorViz.drawInfoChip(g,
    new Rectangle(r.x + r.width - 206, r.y + 8, 196, 24),
                    "top FC2",
                    "#" + topTerm + "  " + String.format("%+.3f", value),
                    value >= 0.0f ? TensorViz.POSITIVE : TensorViz.NEGATIVE);
        }

        Rectangle content = new Rectangle(r.x + 8, r.y + 48,
                Math.max(1, r.width - 16), Math.max(1, r.height - 56));
        int rows = 5;
        int rowGap = 5;
        int rowH = Math.max(16, Math.min(28, (content.height - rowGap * (rows - 1)) / rows));
        int y = content.y;
        y = drawOverviewVectorRow(g, new Rectangle(content.x, y, content.width, rowH),
                "xform us", transformedUs, false, "nnue.stockfish.transformed.us",
                "Feature-transformer side-to-move lanes.",
                "overview.stockfish.xform.us", -1);
        y += rowGap;
        y = drawOverviewVectorRow(g, new Rectangle(content.x, y, content.width, rowH),
                "xform them", transformedThem, false, "nnue.stockfish.transformed.them",
                "Feature-transformer opponent lanes.",
                "overview.stockfish.xform.them", -1);
        y += rowGap;
        y = drawOverviewVectorRow(g, new Rectangle(content.x, y, content.width, rowH),
                "FC0 raw", fc0, true, null,
                "First dense layer before the squared/clipped split.",
                "overview.stockfish.fc0", -1);
        y += rowGap;
        y = drawOverviewVectorRow(g, new Rectangle(content.x, y, content.width, rowH),
                "FC1", fc1, false, "nnue.stockfish.fc1.clipped",
                "Second dense hidden layer after clipping.",
                "overview.stockfish.fc1", topTerm);
        y += rowGap;
        drawOverviewVectorRow(g, new Rectangle(content.x, y, content.width, rowH),
                "FC2 contrib", fc2, true, "nnue.stockfish.fc2.contribution",
                "Final dense contribution terms before adding PSQT.",
                "overview.stockfish.fc2", topTerm);
    }

    /**
     * Draws one compact vector heatmap row in the overview trunk panel.
     *
     * @param g graphics
     * @param r row rectangle
     * @param label row label
     * @param values vector values
     * @param signed true for diverging signed colour
     * @param dataKey optional snapshot data key for inspection
     * @param detail tooltip detail
     * @param scaleKey scale bucket key
     * @param ringIndex optional cell to outline
     * @return next y coordinate
     */
    protected int drawOverviewVectorRow(Graphics2D g, Rectangle r, String label,
            float[] values, boolean signed, String dataKey, String detail,
            String scaleKey, int ringIndex) {
        int labelW = Math.min(112, Math.max(74, r.width / 4));
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(10, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(Ui.elide(label, fm, Math.max(12, labelW - 8)),
                r.x + 2, r.y + r.height / 2 + 4);
        Rectangle heat = new Rectangle(r.x + labelW, r.y + 2,
                Math.max(1, r.width - labelW), Math.max(1, r.height - 4));
        if (values == null || values.length == 0) {
            TensorViz.drawEmpty(g, heat);
            return r.y + r.height;
        }
        float scale = scaleFor(scaleKey, maxAbs(values));
        TensorViz.drawHeatmap(g, heat, values, values.length, 1,
                Math.max(1e-4f, scale), signed);
        if (ringIndex >= 0 && ringIndex < values.length) {
            Color accent = valueAt(values, ringIndex) >= 0.0f
                    ? TensorViz.POSITIVE
                    : TensorViz.NEGATIVE;
            drawGridCellRing(g, heat, ringIndex, values.length, 1, accent);
        }
        String valueText = String.format("len %d · range %s%.3f",
                values.length, signed ? "+/-" : "0..", Math.max(1e-4f, scale));
        if (dataKey == null) {
            hitRegions.addInline(heat, label, detail, valueText, values, "1x" + values.length);
        } else {
            hitRegions.addInspectable(heat, label, detail, valueText,
                    dataKey, 0, values.length, 0, "1x" + values.length);
        }
        return r.y + r.height;
    }




    /**
     * Paints a compact positive-vs-negative contribution ledger.
     *
     * @param g graphics
     * @param r ledger bounds
     */
    protected void paintContributionLedger(Graphics2D g, Rectangle r) {
        float[] contribution = totalContribution();
        if (contribution == null || contribution.length == 0) {
            return;
        }
        float positive = 0.0f;
        float negative = 0.0f;
        int active = 0;
        for (float value : contribution) {
            if (Math.abs(value) > 1e-5f) {
                active++;
            }
            if (value >= 0.0f) {
                positive += value;
            } else {
                negative += -value;
            }
        }
        float scale = Math.max(1e-5f, Math.max(positive, negative));
        g.setColor(Theme.PANEL_SOLID);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(Theme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);

        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(11, Font.BOLD));
        g.drawString("output contribution", r.x + 8, r.y + 15);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(10, Font.PLAIN));
        g.drawString(active + " active slots", r.x + 128, r.y + 15);
        if (r.width >= 420) {
            boolean raiseDominates = positive >= negative;
            float dominant = raiseDominates ? positive : negative;
            float share = (positive + negative) <= 1e-5f ? 0.0f : dominant / (positive + negative);
            TensorViz.drawInfoChip(g,
    new Rectangle(r.x + r.width - 180, r.y + 3, 170, 18),
                    "dominant",
                    (raiseDominates ? "raise " : "lower ") + Math.round(share * 100.0f) + "%",
                    raiseDominates ? TensorViz.POSITIVE : TensorViz.NEGATIVE);
        }

        int center = r.x + r.width / 2;
        int barY = r.y + 25;
        int barH = 12;
        int halfW = Math.max(1, r.width / 2 - 18);
        g.setColor(Theme.ELEVATED_SOLID);
        g.fillRoundRect(center - halfW, barY, halfW * 2, barH, barH, barH);
        int negW = Math.round(halfW * (negative / scale));
        int posW = Math.round(halfW * (positive / scale));
        g.setColor(TensorViz.NEGATIVE);
        g.fillRoundRect(center - negW, barY, negW, barH, barH, barH);
        g.setColor(TensorViz.POSITIVE);
        g.fillRoundRect(center, barY, posW, barH, barH, barH);
        g.setColor(Theme.LINE);
        g.drawLine(center, barY - 2, center, barY + barH + 2);

        g.setFont(Theme.font(10, Font.BOLD));
        String negLabel = String.format("-%.1f", negative);
        String posLabel = String.format("+%.1f", positive);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(TensorViz.NEGATIVE);
        g.drawString(negLabel, center - halfW, r.y + r.height - 5);
        g.setColor(TensorViz.POSITIVE);
        g.drawString(posLabel, center + halfW - fm.stringWidth(posLabel), r.y + r.height - 5);
        hitRegions.addInline(r,
                "Output contribution ledger",
                "Net us+them per-slot contributions before final centipawn scaling.",
                String.format("raise %.2f · lower %.2f · %d active", positive, negative, active),
                contribution, "1x" + contribution.length);
    }

}
