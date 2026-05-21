package application.gui.workbench.network;

import application.gui.workbench.ui.HitRegions;
import application.gui.workbench.ui.InspectorDialog;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Workbench panel that visualises an LC0 CNN forward pass.
 *
 * <p>Abstract mode shows the residual-trunk activity strip, the WDL bars, the
 * top policy logits, and the final policy-attention heatmap overlaid on a
 * mini board. Detailed mode shows per-block channel statistics, lets the user
 * click any block to inspect its channel grid, and shows the policy/value
 * heads as a small pipeline of cards.</p>
 *
 * <p>All the shared scaffolding — snapshot/fen/mode plumbing, the paint
 * skeleton, the fixed-scale colour helpers and the hit-region registry — lives
 * in {@link NetworkView}; this class only owns the CNN-specific
 * drawing. Atlas mode is a curated layer/channel fingerprint, while raw mode
 * remains the dense every-channel mosaic.</p>
 */
public final class CnnView extends NetworkView {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Cached layer summaries built during snapshot updates.
     */
    private final List<LayerInfo> layers = new ArrayList<>();

    /**
     * Selected layer index in detailed mode, -1 when none.
     */
    private int selectedLayer = -1;

    /**
     * Cached cell rectangles for selectedLayer hit-testing.
     */
    private final List<Rectangle> cellHitBoxes = new ArrayList<>();

    /**
     * Cached layer-index mapping for cellHitBoxes.
     */
    private final List<Integer> cellLayerIndices = new ArrayList<>();

    /**
     * Spatial-layer index zoomed into in the raw "All" view, or -1 for the
     * full all-layers mosaic.
     */
    private int rawZoomLayer = -1;

    /**
     * Creates the CNN view.
     */
    public CnnView() {
        super(720, 540);
    }

    /**
     * Rebuilds the cached layer list whenever a new snapshot arrives.
     */
    @Override
    protected void onSnapshotChanged() {
        rebuildLayers();
    }

    /**
     * Clears the detailed-mode hit-box caches when the view mode changes.
     */
    @Override
    protected void onViewModeChanged() {
        cellHitBoxes.clear();
        cellLayerIndices.clear();
        rawZoomLayer = -1;
    }

    /**
     * Handles a click in detailed mode (block selection).
     *
     * @param x x
     * @param y y
     */
    @Override
    protected void onClick(int x, int y) {
        if (isDetailed()) {
            for (int i = 0; i < cellHitBoxes.size(); ++i) {
                if (cellHitBoxes.get(i).contains(x, y)) {
                    selectedLayer = cellLayerIndices.get(i);
                    repaint();
                    return;
                }
            }
        }
        HitRegions.Region r = hitRegions.hitTest(x, y);
        if (r == null) {
            return;
        }
        // Raw "All" view: click a layer row to zoom in, click again to zoom out.
        if (r.title != null && r.title.startsWith("rawzoom:")) {
            String which = r.title.substring("rawzoom:".length());
            if ("back".equals(which)) {
                rawZoomLayer = -1;
            } else {
                try {
                    rawZoomLayer = Integer.parseInt(which);
                } catch (NumberFormatException ignored) {
                    rawZoomLayer = -1;
                }
            }
            repaint();
            return;
        }
        if (inspector != null) {
            inspector.inspect(r, snapshot);
        } else if (r.dataKey != null && snapshot != null) {
            InspectorDialog.shared(this).inspect(r, snapshot);
        }
    }

    /**
     * Paints the empty placeholder.
     *
     * @param g graphics
     * @param bounds bounds
     */
    @Override
    protected void paintEmpty(Graphics2D g, Rectangle bounds) {
        g.setColor(Theme.BG);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(13, Font.PLAIN));
        g.drawString("CNN snapshot is loading. The view refreshes when inference finishes.", PAD, 32);
    }

    /**
     * Paints the CNN activation atlas.
     *
     * @param g graphics context
     * @param body atlas bounds
     */
    @Override
    protected void paintAtlas(Graphics2D g, Rectangle body) {
        cellHitBoxes.clear();
        cellLayerIndices.clear();
        TensorViz.drawSectionHeader(g,
    new Rectangle(body.x, body.y, body.width, 40),
                "CNN activation atlas",
                "layer/channel fingerprint · strongest spatial filters · board-level policy/value footprint");
        if (layers.isEmpty()) {
            return;
        }
        int top = body.y + 50;
        int h = body.height - 50;
        int gap = 12;
        if (body.width < 820) {
            int part = Math.max(160, (h - 2 * gap) / 3);
            Rectangle fingerprint = new Rectangle(body.x, top, body.width, part);
            Rectangle channels = new Rectangle(body.x, fingerprint.y + fingerprint.height + gap,
                    body.width, part);
            Rectangle boards = new Rectangle(body.x, channels.y + channels.height + gap,
                    body.width, Math.max(120, body.y + body.height - (channels.y + channels.height + gap)));
            paintCnnLayerFingerprint(g, fingerprint);
            paintCnnTopChannels(g, channels);
            paintCnnSpatialAtlas(g, boards);
            return;
        }
        int leftW = Math.max(430, Math.min((int) (body.width * 0.56), body.width - 360));
        Rectangle fingerprint = new Rectangle(body.x, top, leftW, h);
        int rightX = fingerprint.x + fingerprint.width + gap;
        int rightW = body.x + body.width - rightX;
        int topH = Math.max(220, Math.min((int) (h * 0.52), 320));
        Rectangle channels = new Rectangle(rightX, top, rightW, topH);
        Rectangle boards = new Rectangle(rightX, channels.y + channels.height + gap,
                rightW, Math.max(140, body.y + body.height - (channels.y + channels.height + gap)));
        paintCnnLayerFingerprint(g, fingerprint);
        paintCnnTopChannels(g, channels);
        paintCnnSpatialAtlas(g, boards);
    }

    /**
     * Paints the title header.
     *
     * @param g graphics
     * @param bounds bounds
     */
    @Override
    protected void paintHeader(Graphics2D g, Rectangle bounds) {
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(15, Font.BOLD));
        g.drawString("LC0 CNN (ResNet) activations", PAD, 22);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(11, Font.PLAIN));
        int blocks = countLayersWithPrefix("B");
        String summary = "112 input planes -> stem -> " + blocks + " residual blocks -> policy / value heads";
        g.drawString(summary, PAD, 40);
    }

    /**
     * Paints the raw view: every channel of every spatial 8×8 layer
     * rendered as a tiny 8×8 heatmap, laid out in a single dense atlas.
     * Each row is one layer (input planes, stem, residual blocks 1..N,
     * final feature map) and each column is one channel. Per-layer colour
     * normalisation keeps every row readable regardless of the layer's
     * absolute activity, and a sqrt gamma keeps low values visible.
     * Clicking any cell selects that layer in the detailed view.
     *
     * @param g graphics
     * @param body body rectangle
     */
    @Override
    protected void paintRaw(Graphics2D g, Rectangle body) {
        int headerH = 38;
        if (layers.isEmpty()) {
            TensorViz.drawSectionHeader(g,
    new Rectangle(body.x, body.y, body.width, headerH),
                    "raw channel atlas", "no CNN snapshot");
            return;
        }
        // Only spatial 8x8 layers fit the atlas; collect those.
        java.util.List<LayerInfo> spatial = new java.util.ArrayList<>();
        for (LayerInfo info : layers) {
            if (info.shapeDims.length == 3
                    && info.shapeDims[1] == 8 && info.shapeDims[2] == 8) {
                spatial.add(info);
            }
        }
        if (spatial.isEmpty()) {
            TensorViz.drawSectionHeader(g,
    new Rectangle(body.x, body.y, body.width, headerH),
                    "raw channel atlas", "no spatial layers in this snapshot");
            return;
        }
        if (rawZoomLayer >= 0 && rawZoomLayer < spatial.size()) {
            paintRawZoom(g, body, spatial.get(rawZoomLayer));
            return;
        }
        TensorViz.drawSectionHeader(g,
    new Rectangle(body.x, body.y, body.width, headerH),
                "raw channel atlas — every layer × every channel",
                "rows = layer · cols = channel · click a layer row to zoom into its channels");
        int maxChannels = 0;
        for (LayerInfo info : spatial) {
            if (info.shapeDims[0] > maxChannels) {
                maxChannels = info.shapeDims[0];
            }
        }
        int rowLabelW = 96;
        int colLabelH = 16;
        int gridLeft = body.x + rowLabelW;
        int gridTop = body.y + headerH + 4 + colLabelH;
        int gridW = body.width - rowLabelW - 8;
        int gridH = body.height - headerH - 4 - colLabelH - 4;
        int cellW = Math.max(2, gridW / maxChannels);
        int cellH = Math.max(8, gridH / spatial.size());

        // Per-layer scale via scaleFor so fixed-scale wiring applies.
        float[] perLayerScale = new float[spatial.size()];
        for (int li = 0; li < spatial.size(); ++li) {
            LayerInfo info = spatial.get(li);
            float maxAbs = 0.0f;
            for (float v : info.values) {
                float a = Math.abs(v);
                if (a > maxAbs) {
                    maxAbs = a;
                }
            }
            if (maxAbs <= 0.0f) {
                maxAbs = 1.0f;
            }
            perLayerScale[li] = scaleFor("rawCnnAtlas:" + info.name, maxAbs);
        }

        // Column header (channel index, sparse to avoid clutter).
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(9, Font.PLAIN));
        FontMetrics fm = g.getFontMetrics();
        int step = Math.max(1, maxChannels / 16);
        for (int c = 0; c < maxChannels; c += step) {
            String lbl = Integer.toString(c);
            int lx = gridLeft + c * cellW + (cellW - fm.stringWidth(lbl)) / 2;
            g.drawString(lbl, lx, body.y + headerH + 4 + colLabelH - 4);
        }

        for (int li = 0; li < spatial.size(); ++li) {
            LayerInfo info = spatial.get(li);
            int channels = info.shapeDims[0];
            int y = gridTop + li * cellH;
            // Row label.
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(10, Font.BOLD));
            g.drawString(info.name, body.x + 4, y + cellH / 2 + 4);
            g.setFont(Theme.font(9, Font.PLAIN));
            g.drawString(info.shape, body.x + 4, y + cellH / 2 + 16);

            for (int c = 0; c < channels; ++c) {
                int x = gridLeft + c * cellW;
                Rectangle cell = new Rectangle(x + 1, y + 1,
                        Math.max(1, cellW - 2), Math.max(1, cellH - 2));
                float[] slice = new float[64];
                int off = c * 64;
                if (off + 64 <= info.values.length) {
                    System.arraycopy(info.values, off, slice, 0, 64);
                }
                drawGammaHeatmap(g, cell, slice, 8, 8, perLayerScale[li]);
            }
            // One zoom hit region per layer row.
            hitRegions.add(new Rectangle(body.x, y, body.width, cellH),
                    "rawzoom:" + li,
                    "Click to zoom into this layer's " + channels + " channels",
                    info.shape);
        }
    }

    /**
     * Paints one spatial layer's channels zoomed to fill the body, reached by
     * clicking a layer row in the raw atlas. Clicking again zooms back out.
     *
     * @param g graphics
     * @param body body rectangle
     * @param info zoomed spatial layer
     */
    private void paintRawZoom(Graphics2D g, Rectangle body, LayerInfo info) {
        int headerH = 38;
        int channels = info.shapeDims[0];
        TensorViz.drawSectionHeader(g,
    new Rectangle(body.x, body.y, body.width, headerH),
                "layer " + info.name + " — " + channels + " channels",
                "8×8 activation per channel · click anywhere to zoom back out");
        float maxAbs = 0.0f;
        for (float v : info.values) {
            maxAbs = Math.max(maxAbs, Math.abs(v));
        }
        float scale = scaleFor("rawCnnAtlas:" + info.name, maxAbs <= 0.0f ? 1.0f : maxAbs);
        int gridTop = body.y + headerH + 6;
        int gridW = body.width;
        int gridH = body.height - headerH - 10;
        // Choose a column count so cells stay roughly square and all fit.
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(
                (double) channels * gridW / Math.max(1, gridH))));
        int rows = (channels + cols - 1) / cols;
        int cell = Math.max(8, Math.min(gridW / cols, gridH / Math.max(1, rows)));
        g.setFont(Theme.font(9, Font.PLAIN));
        for (int c = 0; c < channels; ++c) {
            int col = c % cols;
            int row = c / cols;
            int x = body.x + col * cell;
            int y = gridTop + row * cell;
            Rectangle r = new Rectangle(x + 1, y + 1, cell - 2, cell - 2);
            float[] slice = new float[64];
            int off = c * 64;
            if (off + 64 <= info.values.length) {
                System.arraycopy(info.values, off, slice, 0, 64);
            }
            drawGammaHeatmap(g, r, slice, 8, 8, scale);
            if (cell >= 26) {
                g.setColor(Theme.MUTED);
                g.drawString(Integer.toString(c), x + 2, y + 10);
            }
        }
        hitRegions.add(new Rectangle(body.x, body.y, body.width, body.height),
                "rawzoom:back", "Click to return to the all-layers atlas", info.shape);
    }

    /**
     * Sequential heatmap with a sqrt gamma so low values stay visible.
     * Mirrors the BT4 raw atlas style — warm amber on a low-alpha base.
     *
     * @param g graphics
     * @param r destination rectangle
     * @param data flat row-major values
     * @param cols column count
     * @param rows row count
     * @param scale absolute max for the colour ramp
     */
    private static void drawGammaHeatmap(Graphics2D g, Rectangle r, float[] data,
            int cols, int rows, float scale) {
        // Render into a cols x rows bitmap and blit it once. The raw atlas
        // packs thousands of these, so a per-data-cell fillRect loop turned
        // every repaint into millions of draw calls and froze the tab.
        TensorViz.drawGammaHeatmap(g, r, data, cols, rows, scale);
    }

    /**
     * Paints a compact layer x channel activation fingerprint. Each row is a
     * spatial layer and each cell is one channel's RMS activity.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintCnnLayerFingerprint(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 38),
                "layer × channel fingerprint",
                "bright cells are the channels carrying the most signal now; pale cells are low-signal background");
        List<LayerInfo> spatial = spatialLayers();
        if (spatial.isEmpty()) {
            return;
        }
        int rowLabelW = 64;
        int colLabelH = 16;
        int gridX = r.x + rowLabelW;
        int gridY = r.y + 38 + colLabelH + 4;
        int gridW = r.width - rowLabelW - 4;
        int gridH = r.y + r.height - gridY - 4;
        int maxChannels = 1;
        for (LayerInfo info : spatial) {
            maxChannels = Math.max(maxChannels, info.shapeDims[0]);
        }
        int cellW = Math.max(2, gridW / maxChannels);
        int rowH = Math.max(10, gridH / spatial.size());
        float[][] scores = new float[spatial.size()][maxChannels];
        float scale = 0.0f;
        int topLayer = 0;
        int topChannel = 0;
        int scoredCells = 0;
        int quietCells = 0;
        for (int i = 0; i < spatial.size(); i++) {
            LayerInfo info = spatial.get(i);
            int channels = info.shapeDims[0];
            for (int c = 0; c < channels; c++) {
                scores[i][c] = channelRms(info, c);
                if (scores[i][c] > scale) {
                    topLayer = i;
                    topChannel = c;
                }
                scale = Math.max(scale, scores[i][c]);
                scoredCells++;
            }
        }
        if (scale <= 0.0f) {
            scale = 1.0f;
        }
        float rawScale = scale;
        float quietCutoff = rawScale * 0.16f;
        float importantCutoff = rawScale * 0.78f;
        for (int i = 0; i < spatial.size(); i++) {
            int channels = spatial.get(i).shapeDims[0];
            for (int c = 0; c < channels; c++) {
                if (scores[i][c] <= quietCutoff) {
                    quietCells++;
                }
            }
        }
        scale = scaleFor("cnnAtlas:channelFingerprint", scale);
        if (r.width >= 760 && scoredCells > 0) {
            String top = spatial.get(topLayer).name + " c" + topChannel;
            String quiet = Math.round(100.0f * quietCells / scoredCells) + "% quiet";
            TensorViz.drawInfoChip(g, new Rectangle(r.x + r.width - 322, r.y + 7, 154, 24),
                    "important", top, colorFor(spatial.get(topLayer).name));
            TensorViz.drawInfoChip(g, new Rectangle(r.x + r.width - 160, r.y + 7, 150, 24),
                    "not much", quiet, Theme.MUTED);
        }

        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(9, Font.PLAIN));
        FontMetrics fm = g.getFontMetrics();
        int step = Math.max(1, maxChannels / 8);
        for (int c = 0; c < maxChannels; c += step) {
            String label = Integer.toString(c);
            int lx = gridX + c * cellW + Math.max(0, (cellW - fm.stringWidth(label)) / 2);
            g.drawString(label, lx, gridY - 5);
        }

        for (int i = 0; i < spatial.size(); i++) {
            LayerInfo info = spatial.get(i);
            int y = gridY + i * rowH;
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(10, Font.BOLD));
            g.drawString(info.name, r.x + 4, y + Math.max(10, rowH / 2 + 4));
            int channels = info.shapeDims[0];
            Color accent = colorFor(info.name);
            for (int c = 0; c < channels; c++) {
                float v = (float) Math.sqrt(Math.min(1.0f, Math.max(0.0f, scores[i][c] / scale)));
                g.setColor(TensorViz.lerp(Theme.PANEL_SOLID, accent, 0.18f + 0.78f * v));
                int x = gridX + c * cellW;
                int w = Math.max(1, cellW - 1);
                int h = Math.max(1, rowH - 1);
                g.fillRect(x, y, w, h);
                if (scores[i][c] >= importantCutoff && cellW >= 4 && rowH >= 8) {
                    g.setColor(Theme.withAlpha(Theme.TEXT, 120));
                    g.drawRect(x, y, w - 1, h - 1);
                }
            }
            String key = snapshotKey(info);
            Rectangle rowBounds = new Rectangle(r.x, y, r.width, rowH);
            String stats = String.format("rms %.3f · mean %+.3f · range %+.2f..%+.2f",
                    info.rms, info.mean, info.min, info.max);
            if (key != null && snapshot.has(key)) {
                int stride = info.shapeDims.length >= 2 ? info.shapeDims[info.shapeDims.length - 1] : 0;
                hitRegions.addInspectable(rowBounds, info.name + " · " + info.shape,
                        "Layer/channel atlas row", stats, key, 0, 0, stride, info.shape);
            } else {
                hitRegions.add(rowBounds, info.name + " · " + info.shape,
                        "Layer/channel atlas row", stats);
            }
        }
        g.setColor(Theme.LINE);
        g.drawRect(gridX, gridY, Math.min(gridW, maxChannels * cellW),
                Math.min(gridH, spatial.size() * rowH));
    }

    /**
     * Paints the strongest spatial channels as small board-shaped filters.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintCnnTopChannels(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 38),
                "strongest filters", "ranked channels; primary filters explain most of this position's spatial signal");
        List<ChannelPick> picks = strongestChannels(8);
        if (picks.isEmpty()) {
            return;
        }
        float bestScore = Math.max(1e-6f, picks.get(0).score);
        Rectangle content = new Rectangle(r.x + 2, r.y + 44, r.width - 4, r.height - 46);
        int show = Math.min(8, picks.size());
        int cols = Math.min(4, Math.max(1, content.width / 104));
        int rows = (show + cols - 1) / cols;
        int gap = 8;
        int cellW = Math.max(60, (content.width - gap * (cols - 1)) / cols);
        int cellH = Math.max(48, (content.height - gap * (rows - 1)) / rows);
        for (int i = 0; i < show; i++) {
            ChannelPick pick = picks.get(i);
            int row = i / cols;
            int col = i % cols;
            Rectangle card = new Rectangle(content.x + col * (cellW + gap),
                    content.y + row * (cellH + gap), cellW, cellH);
            TensorViz.drawCard(g, card,
                    pick.layer.name + " c" + pick.channel,
                    importanceLabel(pick.score / bestScore) + " · rms " + String.format("%.3f", pick.score),
                    colorFor(pick.layer.name));
            int side = Math.max(24, Math.min(card.width - 14, card.height - 34));
            Rectangle heat = new Rectangle(card.x + 7, card.y + card.height - side - 7, side, side);
            float[] slice = channelSlice(pick.layer, pick.channel);
            TensorViz.drawHeatmap(g, heat, slice, 8, 8, scaleFor(
                    "cnnAtlas:top:" + pick.layer.name + ":" + pick.channel, maxAbs(slice)), true);
            String key = snapshotKey(pick.layer);
            if (key != null && snapshot.has(key)) {
                hitRegions.addInspectable(card,
                        pick.layer.name + " channel " + pick.channel,
                        "Strong spatial filter in the current position",
                        String.format("%s importance · rms %.3f",
                                importanceLabel(pick.score / bestScore), pick.score),
                        key, pick.channel * 64, 64, 8, "8x8");
            }
        }
    }

    /**
     * Paints board-sized summaries of the final feature map and policy planes.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintCnnSpatialAtlas(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 38),
                "board footprint", "final-map and policy-plane activation projected onto the board");
        Rectangle content = new Rectangle(r.x + 4, r.y + 46, r.width - 8, r.height - 50);
        LayerInfo finalLayer = findLayer("final");
        LayerInfo policyPlanes = findLayer("pPlane");
        float[] finalMap = snapshot.data("cnn.final.activation");
        if (finalMap == null) {
            finalMap = meanPerSquare(finalLayer);
        }
        float[] policyMap = meanPerSquare(policyPlanes);
        int gap = 10;
        int boardSide = Math.min((content.width - gap) / 2, Math.max(60, content.height - 22));
        if (boardSide < 72) {
            boardSide = Math.min(content.width, Math.max(48, content.height - 22));
            drawCnnAtlasBoard(g, new Rectangle(content.x, content.y + 14, boardSide, boardSide),
                    "final", finalMap, "Final-map mean activation");
            return;
        }
        Rectangle finalBoard = new Rectangle(content.x, content.y + 16, boardSide, boardSide);
        Rectangle policyBoard = new Rectangle(content.x + boardSide + gap, content.y + 16, boardSide, boardSide);
        drawCnnAtlasBoard(g, finalBoard, "final map", finalMap, "Final-map mean activation");
        drawCnnAtlasBoard(g, policyBoard, "policy planes", policyMap, "Policy-plane mean activation");
    }

    /**
     * Draws one board tile for the CNN atlas.
     *
     * @param g graphics
     * @param board board rectangle
     * @param title title
     * @param values per-square values
     * @param caption tooltip caption
     */
    private void drawCnnAtlasBoard(Graphics2D g, Rectangle board, String title,
            float[] values, String caption) {
        int focusSquare = strongestSquare(values, true);
        String focus = focusSquare >= 0 ? " · focus " + TensorViz.squareLabel(focusSquare) : "";
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(10, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(Ui.elide(title + focus, fm, board.width + 10), board.x, board.y - 4);
        TensorViz.drawMiniBoard(g, board);
        TensorViz.drawPositionPieces(g, board, fen);
        if (values != null) {
            float scale = scaleFor("cnnAtlas:board:" + title, maxAbs(values));
            TensorViz.drawSquareOverlay(g, board, values, scale, false);
            TensorViz.drawBoardSquareRing(g, board, focusSquare, Theme.ACCENT);
            addBoardSquareTooltips(board, values, caption);
        }
        TensorViz.drawBoardCoordinates(g, board);
    }

    /**
     * Returns the current spatial layers.
     *
     * @return spatial layers
     */
    private List<LayerInfo> spatialLayers() {
        List<LayerInfo> spatial = new ArrayList<>();
        for (LayerInfo info : layers) {
            if (info.shapeDims.length == 3 && info.shapeDims[1] == 8 && info.shapeDims[2] == 8) {
                spatial.add(info);
            }
        }
        return spatial;
    }

    /**
     * Returns the strongest channels across spatial layers.
     *
     * @param limit maximum count
     * @return sorted picks
     */
    private List<ChannelPick> strongestChannels(int limit) {
        List<ChannelPick> picks = new ArrayList<>();
        for (LayerInfo info : spatialLayers()) {
            if ("input".equals(info.name)) {
                continue;
            }
            int channels = info.shapeDims[0];
            for (int c = 0; c < channels; c++) {
                picks.add(new ChannelPick(info, c, channelRms(info, c)));
            }
        }
        picks.sort((a, b) -> Float.compare(b.score, a.score));
        if (picks.size() > limit) {
            return new ArrayList<>(picks.subList(0, limit));
        }
        return picks;
    }

    /**
     * Returns one channel as a 64-cell slice.
     *
     * @param info layer
     * @param channel channel index
     * @return 8x8 slice
     */
    private static float[] channelSlice(LayerInfo info, int channel) {
        float[] out = new float[64];
        if (info == null || info.values == null || channel < 0 || channel >= info.shapeDims[0]) {
            return out;
        }
        int off = channel * 64;
        if (off + 64 <= info.values.length) {
            System.arraycopy(info.values, off, out, 0, 64);
        }
        return out;
    }

    /**
     * Returns the RMS of one channel.
     *
     * @param info layer
     * @param channel channel index
     * @return RMS
     */
    private static float channelRms(LayerInfo info, int channel) {
        float[] slice = channelSlice(info, channel);
        double sumSq = 0.0;
        for (float v : slice) {
            sumSq += (double) v * v;
        }
        return (float) Math.sqrt(sumSq / Math.max(1, slice.length));
    }

    /**
     * Paints the abstract overview.
     *
     * @param g graphics
     * @param body body rectangle
     */
    @Override
    protected void paintAbstract(Graphics2D g, Rectangle body) {
        cellHitBoxes.clear();
        cellLayerIndices.clear();
        int leftW = (int) (body.width * 0.62);
        Rectangle left = new Rectangle(body.x, body.y, leftW, body.height);
        Rectangle right = new Rectangle(body.x + leftW + 10, body.y, body.width - leftW - 10, body.height);
        paintAbstractPipeline(g, left);
        paintPolicyHeatmap(g, right);
    }

    /**
     * Paints the abstract trunk-pipeline view. Lays out the four trunk
     * stages (input → stem → residual trunk → final feature map) on the top
     * row, with the residual trunk box rendered as a stacked-block silhouette
     * to suggest the repeating internal conv-conv-skip-add-ReLU structure,
     * and branches the two heads off the final map on the row below.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintAbstractPipeline(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
                "abstract flow",
                "input planes → stem conv → residual trunk (conv-conv-skip-ReLU × N) → final map → policy / value heads");
        LayerInfo input = findLayer("input");
        LayerInfo stem = findLayer("stem");
        LayerInfo finalLayer = findLayer("final");
        LayerInfo policy = findLayer("pLogit");
        LayerInfo value = findLayer("WDL");
        float scale = 0.0f;
        float trunkRms = 0.0f;
        int trunkSeen = 0;
        for (LayerInfo info : layers) {
            scale = Math.max(scale, info.rms);
            if (info.name.startsWith("B")) {
                trunkRms += info.rms;
                ++trunkSeen;
            }
        }
        if (trunkSeen > 0) {
            trunkRms /= trunkSeen;
        }
        if (scale <= 0.0f) {
            scale = 1.0f;
        }
        int top = r.y + 56;
        int blockH = 80;
        int gap = 16;
        int cellW = (r.width - 5 * gap) / 4;
        Rectangle inputR = new Rectangle(r.x + gap, top, cellW, blockH);
        Rectangle stemR = new Rectangle(inputR.x + cellW + gap, top, cellW, blockH);
        Rectangle trunkR = new Rectangle(stemR.x + cellW + gap, top, cellW, blockH);
        Rectangle finalR = new Rectangle(trunkR.x + cellW + gap, top, cellW, blockH);
        TensorViz.drawElbowConnection(g, inputR, stemR, TensorViz.TRUNK, true);
        TensorViz.drawElbowConnection(g, stemR, trunkR, TensorViz.TRUNK, true);
        TensorViz.drawElbowConnection(g, trunkR, finalR, TensorViz.TRUNK, true);
        TensorViz.drawAbstractBlock(g, inputR, "input planes",
                input == null ? "112x8x8" : input.shape, activity(input, scale), TensorViz.POSITIVE);
        TensorViz.drawAbstractBlock(g, stemR, "stem conv 3×3",
                stem == null ? "-" : stem.shape, activity(stem, scale), TensorViz.TRUNK);
        int blocks = countLayersWithPrefix("B");
        // Render the trunk as a stack of three offset silhouette rectangles
        // BEHIND the main block so the user can read it as a repeating stack
        // of residual blocks rather than a single layer.
        drawTrunkStack(g, trunkR, blocks, trunkRms / scale);
        TensorViz.drawAbstractBlock(g, finalR, "final feature map",
                finalLayer == null ? "8x8" : finalLayer.shape, activity(finalLayer, scale), TensorViz.VALUE);
        registerBlockTooltip(inputR, "Input planes",
                "112 board planes (6 own pieces · 6 enemy pieces · castling · side-to-move · repetition...)",
                input, "cnn.input");
        registerBlockTooltip(stemR, "Stem conv (3×3)",
                "First convolution; mixes input planes into the residual-trunk feature space.",
                stem, "cnn.stem.relu");
        hitRegions.add(trunkR, "Residual trunk",
                "Stack of " + blocks + " residual blocks. Each block is conv→conv→add(skip)→ReLU.",
                String.format("%d blocks · avg rms %.3f", blocks, trunkRms));
        registerBlockTooltip(finalR, "Final feature map",
                "Last conv activation map (channels × 8 × 8) feeding both heads.",
                finalLayer, "cnn.final.relu");

        // Both heads sit directly under the final feature map, side by side.
        // Halving cellW keeps them readable while the elbow connections drop
        // down from finalR's bottom edge — matching the network topology
        // where both heads consume the same final map.
        int headTop = top + blockH + 40;
        int headW = (finalR.width - 8) / 2;
        int headH = blockH - 16;
        Rectangle policyR = new Rectangle(finalR.x, headTop, headW, headH);
        Rectangle valueR = new Rectangle(finalR.x + headW + 8, headTop, headW, headH);
        TensorViz.drawElbowConnection(g, finalR, policyR, TensorViz.POLICY, true);
        TensorViz.drawElbowConnection(g, finalR, valueR, TensorViz.VALUE, true);
        TensorViz.drawAbstractBlock(g, policyR, "policy head → 1858",
                policy == null ? "logits" : policy.shape, activity(policy, scale), TensorViz.POLICY);
        TensorViz.drawAbstractBlock(g, valueR, "value head → WDL",
                value == null ? "W/D/L" : value.shape, activity(value, scale), TensorViz.VALUE);
        registerBlockTooltip(policyR, "Policy head",
                "1×1 conv → flatten → fully-connected → 1858 LC0 move logits.",
                policy, "cnn.policy.logits");
        registerBlockTooltip(valueR, "Value head",
                "1×1 conv → flatten → fully-connected → 3-way W/D/L softmax.",
                value, "cnn.value.wdl");

        int stripY = policyR.y + policyR.height + 28;
        if (stripY + 26 <= r.y + r.height) {
            Rectangle stripR = new Rectangle(r.x + gap, stripY, r.width - 2 * gap, 26);
            paintTrunkStrip(g, stripR);
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(10, Font.PLAIN));
            g.drawString("per-block activity (left = early block, right = late block)",
                    stripR.x, stripR.y - 4);
        }

        // Fill the lower half of the column with a proper value-head card
        // instead of leaving it blank.
        int valueTop = stripY + 26 + 22;
        if (valueTop + 96 <= r.y + r.height) {
            Rectangle valueCardR = new Rectangle(r.x + gap, valueTop,
                    r.width - 2 * gap, r.y + r.height - valueTop);
            paintValueCard(g, valueCardR);
        }
    }

    /**
     * Paints the value-head card via the shared WDL renderer, so the CNN and
     * BT4 overviews present their value output identically.
     *
     * @param g graphics
     * @param r card rectangle
     */
    private void paintValueCard(Graphics2D g, Rectangle r) {
        TensorViz.drawWdlCard(g, r,
                snapshot.data("cnn.value.wdl"), snapshot.data("cnn.value.scalar"));
    }

    /**
     * Renders a "stacked rectangles" silhouette behind the trunk block to
     * suggest the repeating residual stack. The foreground block is drawn
     * separately by the caller via {@link TensorViz#drawAbstractBlock}.
     *
     * @param g graphics
     * @param r front block rectangle
     * @param blocks number of residual blocks
     * @param activity 0..1 activity scalar (drives accent intensity)
     */
    private static void drawTrunkStack(Graphics2D g, Rectangle r, int blocks, float activity) {
        int layers = Math.min(4, Math.max(2, blocks / 4));
        Color edge = TensorViz.TRUNK;
        Color fill = new Color(edge.getRed(), edge.getGreen(), edge.getBlue(),
                30 + Math.round(20 * Math.min(1.0f, Math.max(0.0f, activity))));
        for (int i = layers; i >= 1; --i) {
            int dx = i * 4;
            int dy = i * 4;
            g.setColor(fill);
            g.fillRect(r.x + dx, r.y - dy, r.width, r.height);
            g.setColor(edge);
            g.drawRect(r.x + dx, r.y - dy, r.width - 1, r.height - 1);
        }
        TensorViz.drawAbstractBlock(g, r, "residual trunk",
                blocks + " × residual block", activity, TensorViz.TRUNK);
    }

    /**
     * Paints the per-block RMS strip.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintTrunkStrip(Graphics2D g, Rectangle r) {
        List<LayerInfo> blocks = new ArrayList<>();
        for (LayerInfo info : layers) {
            if (info.name.startsWith("B")) {
                blocks.add(info);
            }
        }
        if (blocks.isEmpty()) {
            return;
        }
        float scale = 0.0f;
        for (LayerInfo info : blocks) {
            scale = Math.max(scale, info.rms);
        }
        if (scale <= 0.0f) {
            scale = 1.0f;
        }
        int gap = 3;
        int cellW = Math.max(8, (r.width - gap * (blocks.size() - 1)) / blocks.size());
        for (int i = 0; i < blocks.size(); ++i) {
            int x = r.x + i * (cellW + gap);
            int h = Math.max(2, Math.round(r.height * (blocks.get(i).rms / scale)));
            g.setColor(TensorViz.TRUNK);
            g.fillRect(x, r.y + r.height - h, cellW, h);
            LayerInfo blk = blocks.get(i);
            String key = "cnn.block" + i + ".relu";
            int stride = blk.shapeDims.length >= 2 ? blk.shapeDims[blk.shapeDims.length - 1] : 0;
            hitRegions.addInspectable(new Rectangle(x, r.y, cellW + gap, r.height),
                    "Residual block " + (i + 1),
                    "Post-residual activation map (channels x 8 x 8).",
                    String.format("rms %.3f · mean %+.3f · min %+.2f · max %+.2f",
                            blk.rms, blk.mean, blk.min, blk.max),
                    key, 0, 0, stride, blk.shape);
        }
        g.setColor(Theme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
    }

    /**
     * Paints the policy-head heatmap overlay on a small board.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintPolicyHeatmap(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
                "policy attention", "final map mean over channels overlaid on the board");
        float[] heatmap = snapshot.data("cnn.final.activation");
        int size = Math.min(r.width, r.height - 60);
        Rectangle board = new Rectangle(r.x + (r.width - size) / 2, r.y + 50, size - 16, size - 16);
        TensorViz.drawMiniBoard(g, board);
        TensorViz.drawPositionPieces(g, board, fen);
        if (heatmap != null && heatmap.length >= 64) {
            float s = scaleFor("policyAttention", maxAbs(heatmap));
            TensorViz.drawSquareOverlay(g, board, heatmap, s, false);
            TensorViz.drawBoardSquareRing(g, board, strongestSquare(heatmap, true), Theme.ACCENT);
            addBoardSquareTooltips(board, heatmap, "Final-map mean activation");
        }
        TensorViz.drawBoardCoordinates(g, board);

        float[] policy = snapshot.data("cnn.policy.logits");
        if (policy != null) {
            Rectangle topRect = new Rectangle(r.x + 4, board.y + board.height + 22, r.width - 8, r.height - (board.y + board.height + 22 - r.y) - 8);
            paintTopPolicy(g, topRect, policy);
        }
    }

    /**
     * Adds 64 per-square hover regions over an 8x8 board overlay.
     *
     * @param board pixel rectangle covering the 8x8 grid
     * @param values 64 cell values in rank-major (rank 0 = a1)
     * @param caption tooltip caption
     */
    private void addBoardSquareTooltips(Rectangle board, float[] values, String caption) {
        if (values == null || values.length < 64) {
            return;
        }
        int cellW = board.width / 8;
        int cellH = board.height / 8;
        for (int sq = 0; sq < 64; ++sq) {
            int file = sq & 7;
            int rank = sq >> 3;
            int drawRank = 7 - rank;
            Rectangle cell = new Rectangle(board.x + file * cellW, board.y + drawRank * cellH, cellW, cellH);
            hitRegions.add(cell,
                    TensorViz.squareLabel(sq),
                    caption,
                    String.format("%+.3f", values[sq]));
        }
    }

    /**
     * Paints a top-N policy logits bar list.
     *
     * @param g graphics
     * @param r rectangle
     * @param policy logits
     */
    private void paintTopPolicy(Graphics2D g, Rectangle r, float[] policy) {
        List<CnnScoredMove> legalMoves = decodeTopCnnMoves(policy, 6);
        if (!legalMoves.isEmpty()) {
            int n = legalMoves.size();
            String[] labels = new String[n];
            float[] values = new float[n];
            float scale = 0.0f;
            for (int i = 0; i < n; ++i) {
                CnnScoredMove move = legalMoves.get(i);
                labels[i] = chess.core.Move.toString(move.move) + "  "
                        + String.format("%.1f%%", move.probability * 100.0f);
                values[i] = move.probability;
                scale = Math.max(scale, values[i]);
            }
            TensorViz.drawMetricBars(g, r, labels, values, scale);
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(10, Font.PLAIN));
            g.drawString("top legal moves (policy probability)", r.x, r.y - 4);
            int barH = r.height / Math.max(1, n);
            for (int i = 0; i < n; ++i) {
                Rectangle row = new Rectangle(r.x, r.y + i * barH, r.width, barH);
                CnnScoredMove move = legalMoves.get(i);
                hitRegions.addInspectable(row,
                        "Top move " + chess.core.Move.toString(move.move),
                        "CNN policy index " + move.policyIndex + " · logit "
                                + String.format("%+.3f", move.logit),
                        String.format("legal-softmax %.2f%% · rank %d", move.probability * 100.0f, i + 1),
                        "cnn.policy.logits", 0, 0, 0, policy.length + "");
            }
            return;
        }

        int n = Math.min(6, policy.length);
        Integer[] order = new Integer[policy.length];
        for (int i = 0; i < policy.length; ++i) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Float.compare(policy[b], policy[a]));
        String[] labels = new String[n];
        float[] values = new float[n];
        float scale = 0.0f;
        for (int i = 0; i < n; ++i) {
            labels[i] = "#" + order[i];
            values[i] = policy[order[i]];
            scale = Math.max(scale, Math.abs(values[i]));
        }
        TensorViz.drawMetricBars(g, r, labels, values, scale);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(10, Font.PLAIN));
        g.drawString("top policy logits", r.x, r.y - 4);
        int barH = r.height / Math.max(1, n);
        for (int i = 0; i < n; ++i) {
            Rectangle row = new Rectangle(r.x, r.y + i * barH, r.width, barH);
            hitRegions.addInspectable(row,
                    "Top policy move #" + order[i],
                    "LC0 move index in the 1858 move list",
                    String.format("logit %+.3f · rank %d", values[i], i + 1),
                    "cnn.policy.logits", 0, 0, 0, policy.length + "");
        }
    }

    /**
     * Decodes the top legal CNN policy moves from raw 73-plane logits.
     *
     * @param policy raw CNN policy logits
     * @param limit maximum count
     * @return sorted scored legal moves
     */
    private List<CnnScoredMove> decodeTopCnnMoves(float[] policy, int limit) {
        if (fen == null || fen.isBlank() || policy == null) {
            return java.util.Collections.emptyList();
        }
        chess.core.Position position;
        try {
            position = new chess.core.Position(fen);
        } catch (RuntimeException ex) {
            return java.util.Collections.emptyList();
        }
        chess.core.MoveList moves = position.legalMoves();
        List<CnnScoredMove> out = new ArrayList<>();
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.get(i);
            int policyIndex = chess.nn.lc0.cnn.PolicyEncoder.rawPolicyIndex(position, move);
            if (policyIndex < 0 || policyIndex >= policy.length) {
                continue;
            }
            float logit = policy[policyIndex];
            out.add(new CnnScoredMove(move, policyIndex, logit, 0.0f));
            max = Math.max(max, logit);
        }
        if (out.isEmpty()) {
            return out;
        }
        double sum = 0.0;
        for (CnnScoredMove move : out) {
            sum += Math.exp(move.logit - max);
        }
        float inv = sum <= 0.0 ? 0.0f : (float) (1.0 / sum);
        for (int i = 0; i < out.size(); i++) {
            CnnScoredMove move = out.get(i);
            float probability = (float) Math.exp(move.logit - max) * inv;
            out.set(i, new CnnScoredMove(move.move, move.policyIndex, move.logit, probability));
        }
        out.sort((a, b) -> Float.compare(b.probability, a.probability));
        if (out.size() > limit) {
            return new ArrayList<>(out.subList(0, limit));
        }
        return out;
    }

    /**
     * Paints the detailed view. Layout: compact row of layer cards across
     * the top, then a left-side chessboard showing the current position
     * (overlaid with the selected layer's mean activity per square) plus a
     * right-side channel grid for the selected layer. The board mirrors the
     * NNUE / BT4 views so all three architectures share the same
     * board-on-the-left layout language.
     *
     * @param g graphics
     * @param body body rectangle
     */
    @Override
    protected void paintDetailed(Graphics2D g, Rectangle body) {
        cellHitBoxes.clear();
        cellLayerIndices.clear();
        TensorViz.drawSectionHeader(g, new Rectangle(body.x, body.y, body.width, 40),
                "layer cards (top) · board + channel grid (below)",
                "click a card to focus a layer; the board overlay shows its 8×8 mean activity");
        int top = body.y + 50;
        int cardW = Math.max(72, Math.min(110, (body.width - 8) / Math.max(1, layers.size())));
        int cardH = 60;
        int gap = 6;
        int cols = Math.max(1, (body.width - 8) / (cardW + gap));
        int rows = (layers.size() + cols - 1) / cols;
        int gridH = rows * cardH + (rows - 1) * gap;
        float scale = 0.0f;
        for (LayerInfo info : layers) {
            scale = Math.max(scale, info.rms);
        }
        if (scale <= 0.0f) {
            scale = 1.0f;
        }
        for (int i = 0; i < layers.size(); ++i) {
            int row = i / cols;
            int col = i % cols;
            int x = body.x + 4 + col * (cardW + gap);
            int y = top + row * (cardH + gap);
            Rectangle card = new Rectangle(x, y, cardW, cardH);
            LayerInfo info = layers.get(i);
            boolean selected = i == selectedLayer;
            Color accent = colorFor(info.name);
            TensorViz.drawCard(g, card, info.name, info.shape, accent);
            float a = TensorViz.clamp(info.rms / scale, 0.0f, 1.0f);
            int barW = card.width - 16;
            g.setColor(Theme.LINE);
            g.fillRect(card.x + 8, card.y + card.height - 10, barW, 3);
            g.setColor(accent);
            g.fillRect(card.x + 8, card.y + card.height - 10, Math.round(barW * a), 3);
            if (selected) {
                g.setColor(Theme.ACCENT);
                g.drawRect(card.x - 2, card.y - 2, card.width + 3, card.height + 3);
            }
            cellHitBoxes.add(card);
            cellLayerIndices.add(i);
            hitRegions.add(card,
                    info.name + "  " + info.shape,
                    "Click the card to focus this layer in the inspector below.",
                    String.format("rms %.3f · mean %+.3f · range %+.2f..%+.2f",
                            info.rms, info.mean, info.min, info.max));
        }
        int inspectorTop = top + gridH + 14;
        int inspectorH = Math.max(220, body.y + body.height - inspectorTop - 4);
        int boardW = Math.min(360, Math.max(220, inspectorH));
        Rectangle boardArea = new Rectangle(body.x, inspectorTop, boardW, inspectorH);
        Rectangle channelArea = new Rectangle(body.x + boardW + 12, inspectorTop,
                body.width - boardW - 12, inspectorH);
        paintDetailedBoard(g, boardArea);
        paintDetailedChannels(g, channelArea);
    }

    /**
     * Paints the interactive chessboard alongside the channel grid. Shows
     * the current position; if the selected layer has an 8×8 spatial layout
     * the per-square mean activity is overlaid as a heatmap so the user can
     * see what part of the board the layer is reacting to.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintDetailedBoard(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 18),
                "position", "mean activity of the selected layer overlaid on the board");
        int size = Math.min(r.width - 8, r.height - 80);
        Rectangle board = new Rectangle(r.x + (r.width - size) / 2, r.y + 24, size, size);
        TensorViz.drawMiniBoard(g, board);
        TensorViz.drawPositionPieces(g, board, fen);
        int idx = selectedLayer < 0 || selectedLayer >= layers.size() ? defaultLayer() : selectedLayer;
        if (idx >= 0) {
            LayerInfo info = layers.get(idx);
            float[] perSquare = meanPerSquare(info);
            if (perSquare != null) {
                float s = scaleFor("detailedBoard:" + info.name, maxAbs(perSquare));
                TensorViz.drawSquareOverlay(g, board, perSquare, s, false);
                TensorViz.drawBoardSquareRing(g, board, strongestSquare(perSquare, true), Theme.ACCENT);
                addBoardSquareTooltips(board, perSquare,
                        info.name + " · mean activity (channel-averaged)");
            }
            TensorViz.drawBoardCoordinates(g, board);
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(11, Font.PLAIN));
            g.drawString("layer: " + info.name + "  ·  " + info.shape,
                    r.x + 6, board.y + board.height + 18);
            g.drawString(String.format("rms %.3f   mean %+.3f   range %+.2f..%+.2f",
                    info.rms, info.mean, info.min, info.max),
                    r.x + 6, board.y + board.height + 34);
            int focusSquare = strongestSquare(perSquare, true);
            if (focusSquare >= 0) {
                g.drawString("strongest board square: " + TensorViz.squareLabel(focusSquare),
                        r.x + 6, board.y + board.height + 50);
            }
        }
    }

    /**
     * Averages a layer's activation across channels into one value per board
     * square. Returns {@code null} for layers that are not channels × 8 × 8.
     *
     * @param info layer info
     * @return per-square mean (length 64) or null
     */
    private static float[] meanPerSquare(LayerInfo info) {
        if (info == null || info.values == null || info.shapeDims.length != 3
                || info.shapeDims[1] != 8 || info.shapeDims[2] != 8) {
            return null;
        }
        int channels = info.shapeDims[0];
        float[] out = new float[64];
        for (int c = 0; c < channels; ++c) {
            int off = c * 64;
            for (int s = 0; s < 64; ++s) {
                out[s] += info.values[off + s];
            }
        }
        float inv = channels > 0 ? 1.0f / channels : 1.0f;
        for (int s = 0; s < 64; ++s) {
            out[s] *= inv;
        }
        return out;
    }

    /**
     * Paints the selected layer's channel grid (or input planes for the input
     * layer, or a flat bar strip for non-spatial layers).
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintDetailedChannels(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 18),
                "channels", "per-channel 8×8 heatmaps for the focused layer");
        int idx = selectedLayer < 0 || selectedLayer >= layers.size() ? defaultLayer() : selectedLayer;
        if (idx < 0) {
            return;
        }
        LayerInfo info = layers.get(idx);
        Rectangle content = new Rectangle(r.x, r.y + 24, r.width, r.height - 24);
        if ("input".equals(info.name) && info.shapeDims.length == 3
                && info.shapeDims[1] == 8 && info.shapeDims[2] == 8) {
            paintInputPlanes(g, content, info);
        } else if (info.shapeDims.length == 3 && info.shapeDims[1] == 8 && info.shapeDims[2] == 8) {
            paintChannelGrid(g, content, info);
        } else if (info.values != null && info.values.length > 0) {
            paintBarStrip(g, content, info.values);
        }
    }

    /**
     * Renders the first 14 LC0 input planes (own pieces, enemy pieces, two
     * repetition planes) as labelled mini-boards. Makes the "what does the
     * network see?" question concrete.
     *
     * @param g graphics
     * @param r rectangle
     * @param info input layer info
     */
    private void paintInputPlanes(Graphics2D g, Rectangle r, LayerInfo info) {
        String[] labels = {
                "own P", "own N", "own B", "own R", "own Q", "own K",
                "enemy P", "enemy N", "enemy B", "enemy R", "enemy Q", "enemy K",
                "rep 1", "rep 2"
        };
        int channels = Math.min(labels.length, info.shapeDims[0]);
        int cols = Math.min(7, channels);
        int rows = (channels + cols - 1) / cols;
        int labelH = 14;
        int cellW = (r.width - (cols - 1) * 8) / cols;
        int cellH = (r.height - (rows - 1) * 10 - rows * labelH) / rows;
        int side = Math.min(cellW, cellH);
        for (int c = 0; c < channels; ++c) {
            int row = c / cols;
            int col = c % cols;
            int x = r.x + col * (cellW + 8);
            int y = r.y + row * (side + 10 + labelH);
            Rectangle board = new Rectangle(x, y + labelH, side, side);
            TensorViz.drawMiniBoard(g, board);
            float[] slice = new float[64];
            System.arraycopy(info.values, c * 64, slice, 0, 64);
            TensorViz.drawSquareOverlay(g, board, slice, 0.0f, false);
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(10, Font.BOLD));
            g.drawString(labels[c], x, y + labelH - 2);
            hitRegions.addInspectable(board,
                    "Input plane: " + labels[c],
                    "1 if the indicated piece is on the square, else 0",
                    String.format("plane %d", c),
                    "cnn.input", c * 64, 64, 8, "8x8");
        }
    }

    /**
     * Paints a small grid of channel heatmaps.
     *
     * @param g graphics
     * @param r rectangle
     * @param info layer
     */
    private void paintChannelGrid(Graphics2D g, Rectangle r, LayerInfo info) {
        int channels = info.shapeDims[0];
        int show = Math.min(channels, 32);
        int cols = Math.min(show, Math.max(4, r.width / 60));
        int rows = (show + cols - 1) / cols;
        int cellW = (r.width - (cols - 1) * 6) / cols;
        int cellH = Math.min(cellW, (r.height - (rows - 1) * 6) / rows);
        float channelGridScale = scaleFor("channelGrid:" + info.name, maxAbs(info.values));
        for (int c = 0; c < show; ++c) {
            int row = c / cols;
            int col = c % cols;
            int x = r.x + col * (cellW + 6);
            int y = r.y + row * (cellH + 6);
            float[] slice = new float[64];
            System.arraycopy(info.values, c * 64, slice, 0, 64);
            Rectangle cell = new Rectangle(x, y, cellW, cellH);
            TensorViz.drawHeatmap(g, cell, slice, 8, 8, channelGridScale, true);
            float[] stats = TensorViz.summarize(slice);
            int snapKey = indexOfLayer(info);
            String key = snapKey >= 0 ? snapshotKey(info) : null;
            if (key != null) {
                hitRegions.addInspectable(cell,
                        info.name + " channel " + c,
                        "8x8 activation map for this channel",
                        String.format("rms %.3f · min %+.2f · max %+.2f", stats[2], stats[3], stats[4]),
                        key, c * 64, 64, 8, "8x8");
            } else {
                hitRegions.add(cell,
                        info.name + " channel " + c,
                        "8x8 activation map for this channel",
                        String.format("rms %.3f · min %+.2f · max %+.2f", stats[2], stats[3], stats[4]));
            }
        }
    }

    /**
     * Returns the snapshot key for a layer info, or null when unknown.
     *
     * @param info layer info
     * @return key or null
     */
    private String snapshotKey(LayerInfo info) {
        if (info == null) {
            return null;
        }
        switch (info.name) {
            case "input": return "cnn.input";
            case "stem": return "cnn.stem.relu";
            case "final": return "cnn.final.relu";
            case "pStem": return "cnn.policy.hidden";
            case "pPlane": return "cnn.policy.planes";
            case "pLogit": return "cnn.policy.logits";
            case "vConv": return "cnn.value.conv";
            case "fc1": return "cnn.value.fc1";
            case "vLogit": return "cnn.value.logits";
            case "WDL": return "cnn.value.wdl";
            default:
                if (info.name.startsWith("B")) {
                    int b = Integer.parseInt(info.name.substring(1)) - 1;
                    return "cnn.block" + b + ".relu";
                }
                return null;
        }
    }

    /**
     * Returns the layer index for a layer info or -1.
     *
     * @param info layer info
     * @return index or -1
     */
    private int indexOfLayer(LayerInfo info) {
        return layers.indexOf(info);
    }

    /**
     * Paints a bar strip for a flat vector layer.
     *
     * @param g graphics
     * @param r rectangle
     * @param values values
     */
    private void paintBarStrip(Graphics2D g, Rectangle r, float[] values) {
        int n = Math.min(values.length, r.width);
        float scale = 0.0f;
        for (float v : values) {
            scale = Math.max(scale, Math.abs(v));
        }
        if (scale <= 0.0f) {
            scale = 1.0f;
        }
        int midY = r.y + r.height / 2;
        g.setColor(Theme.LINE);
        g.drawLine(r.x, midY, r.x + r.width, midY);
        for (int i = 0; i < n; ++i) {
            int x = r.x + i;
            int sampleIdx = (int) Math.floor((double) i / n * values.length);
            float v = values[sampleIdx] / scale;
            int h = (int) Math.round(Math.abs(v) * (r.height / 2.0));
            g.setColor(v >= 0 ? TensorViz.POSITIVE : TensorViz.NEGATIVE);
            if (v >= 0) {
                g.drawLine(x, midY, x, midY - h);
            } else {
                g.drawLine(x, midY, x, midY + h);
            }
        }
        g.setColor(Theme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
    }

    /**
     * Returns a default inspector layer (final or last block).
     *
     * @return index or -1 if there are no layers
     */
    private int defaultLayer() {
        int idx = indexOf("final");
        if (idx >= 0) {
            return idx;
        }
        return layers.isEmpty() ? -1 : layers.size() - 1;
    }

    /**
     * Returns the index of the named layer or -1.
     *
     * @param name name
     * @return index
     */
    private int indexOf(String name) {
        for (int i = 0; i < layers.size(); ++i) {
            if (layers.get(i).name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the named layer info or null.
     *
     * @param name name
     * @return layer info or null
     */
    private LayerInfo findLayer(String name) {
        int idx = indexOf(name);
        return idx >= 0 ? layers.get(idx) : null;
    }

    /**
     * Adds a hover+inspect region for an abstract pipeline block card.
     *
     * @param r card rectangle
     * @param title hover title
     * @param desc one-line description
     * @param info layer info (may be null)
     * @param key snapshot key (may be missing)
     */
    private void registerBlockTooltip(Rectangle r, String title, String desc,
            LayerInfo info, String key) {
        String value = info == null
                ? "no data"
                : String.format("shape %s · rms %.3f · mean %+.3f", info.shape, info.rms, info.mean);
        if (info == null || !snapshot.has(key)) {
            hitRegions.add(r, title, desc, value);
        } else {
            int stride = info.shapeDims.length >= 2 ? info.shapeDims[info.shapeDims.length - 1] : 0;
            hitRegions.addInspectable(r, title, desc, value, key, 0, 0, stride, info.shape);
        }
    }

    /**
     * Returns the rms/scale activity for an info, or 0 when missing.
     *
     * @param info layer info or null
     * @param scale scale
     * @return activity
     */
    private static float activity(LayerInfo info, float scale) {
        if (info == null) {
            return 0.0f;
        }
        return TensorViz.clamp(info.rms / scale, 0.0f, 1.0f);
    }

    /**
     * Returns the accent color for a layer label.
     *
     * @param name name
     * @return color
     */
    private static Color colorFor(String name) {
        if (name.startsWith("p")) {
            return TensorViz.POLICY;
        }
        if (name.startsWith("v") || name.equals("WDL")) {
            return TensorViz.VALUE;
        }
        if (name.equals("input")) {
            return TensorViz.POSITIVE;
        }
        return TensorViz.TRUNK;
    }

    /**
     * Rebuilds the cached layer list from the current snapshot.
     */
    private void rebuildLayers() {
        layers.clear();
        if (snapshot == null) {
            return;
        }
        addLayer("input", "cnn.input");
        addLayer("stem", "cnn.stem.relu");
        for (int i = 0; i < 64; ++i) {
            String key = "cnn.block" + i + ".relu";
            if (snapshot.has(key)) {
                addLayer("B" + (i + 1), key);
            }
        }
        addLayer("final", "cnn.final.relu");
        addLayer("pStem", "cnn.policy.hidden");
        addLayer("pPlane", "cnn.policy.planes");
        addLayer("pLogit", "cnn.policy.logits");
        addLayer("vConv", "cnn.value.conv");
        addLayer("fc1", "cnn.value.fc1");
        addLayer("vLogit", "cnn.value.logits");
        addLayer("WDL", "cnn.value.wdl");
        if (selectedLayer >= layers.size()) {
            selectedLayer = -1;
        }
    }

    /**
     * Adds a layer info from a key if present.
     *
     * @param label display label
     * @param key snapshot key
     */
    private void addLayer(String label, String key) {
        ActivationSnapshot.Entry entry = snapshot.get(key);
        if (entry == null) {
            return;
        }
        LayerInfo info = new LayerInfo();
        info.name = label;
        info.shape = entry.shapeText();
        info.shapeDims = entry.shape();
        info.values = entry.data();
        float[] stats = TensorViz.summarize(info.values);
        info.mean = stats[0];
        info.rms = stats[2];
        info.min = stats[3];
        info.max = stats[4];
        layers.add(info);
    }

    /**
     * Paints the architecture-diagram schematic for the CNN.
     *
     * @param g graphics
     * @param body body rectangle
     */
    @Override
    protected void paintDiagram(Graphics2D g, Rectangle body) {
        int headerH = 40;
        TensorViz.drawSectionHeader(g,
    new Rectangle(body.x, body.y, body.width, headerH),
                "LC0 CNN architecture",
                "input planes → stem → residual blocks → policy / value heads");
        int blocks = countLayersWithPrefix("B");
        String[] titles = {
                "input encoding",
                "stem",
                blocks + " residual blocks",
                "policy head",
                "value head"
        };
        String[] subs = {
                "112 input planes (8×8)",
                "3×3 conv · ReLU",
                "3×3 conv ×2 + skip",
                "policy logits (4672)",
                "WDL + scalar eval"
        };
        int boxW = 200;
        int boxH = 90;
        int gap = 30;
        int totalW = boxW * titles.length + gap * (titles.length - 1);
        int startX = body.x + (body.width - totalW) / 2;
        int y = body.y + headerH + 60;
        FontMetrics fmTitle = g.getFontMetrics(Theme.font(13, Font.BOLD));
        for (int i = 0; i < titles.length; i++) {
            int x = startX + i * (boxW + gap);
            g.setColor(Theme.PANEL_SOLID);
            g.fillRoundRect(x, y, boxW, boxH, 4, 4);
            g.setColor(Theme.LINE);
            g.drawRoundRect(x, y, boxW, boxH, 4, 4);
            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(13, Font.BOLD));
            int tw = fmTitle.stringWidth(titles[i]);
            g.drawString(titles[i], x + (boxW - tw) / 2, y + 28);
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(11, Font.PLAIN));
            int sw = g.getFontMetrics().stringWidth(subs[i]);
            g.drawString(subs[i], x + (boxW - sw) / 2, y + 56);
            if (i < titles.length - 1) {
                int ax1 = x + boxW;
                int ax2 = x + boxW + gap;
                int ay = y + boxH / 2;
                g.setColor(Theme.ACCENT);
                g.drawLine(ax1 + 2, ay, ax2 - 6, ay);
                int[] xs = { ax2 - 6, ax2 - 12, ax2 - 12 };
                int[] ys = { ay, ay - 5, ay + 5 };
                g.fillPolygon(xs, ys, 3);
            }
            hitRegions.add(new Rectangle(x, y, boxW, boxH),
                    "Layer · " + titles[i], subs[i], "");
        }
        float[] scalar = snapshot == null ? null : snapshot.data("cnn.value.scalar");
        if (scalar != null) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(12, Font.ITALIC));
            String caption = String.format("value scalar %+.3f", scalar[0]);
            int cw = g.getFontMetrics().stringWidth(caption);
            g.drawString(caption, body.x + (body.width - cw) / 2, y + boxH + 28);
        }
    }

    /**
     * Counts layers whose name starts with prefix.
     *
     * @param prefix label prefix
     * @return count
     */
    private int countLayersWithPrefix(String prefix) {
        int n = 0;
        for (LayerInfo info : layers) {
            if (info.name.startsWith(prefix)) {
                ++n;
            }
        }
        return n;
    }

    /**
     * Returns a compact importance label for a value normalised to the current
     * top item.
     *
     * @param ratio value/top
     * @return label
     */
    private static String importanceLabel(float ratio) {
        if (ratio >= 0.82f) {
            return "primary";
        }
        if (ratio >= 0.55f) {
            return "high";
        }
        if (ratio >= 0.28f) {
            return "medium";
        }
        return "low";
    }

    /**
     * Returns the strongest square in a 64-value board array.
     *
     * @param values square values
     * @param absolute true to rank by absolute magnitude
     * @return square index or -1
     */
    private static int strongestSquare(float[] values, boolean absolute) {
        if (values == null || values.length < 64) {
            return -1;
        }
        int best = 0;
        float bestValue = absolute ? Math.abs(values[0]) : values[0];
        for (int sq = 1; sq < 64; sq++) {
            float v = absolute ? Math.abs(values[sq]) : values[sq];
            if (v > bestValue) {
                bestValue = v;
                best = sq;
            }
        }
        return best;
    }

    /**
     * One legal CNN policy move with its softmax probability.
     */
    private static final class CnnScoredMove {

        /**
         * Encoded move.
         */
        private final short move;

        /**
         * Raw CNN policy index.
         */
        private final int policyIndex;

        /**
         * Raw logit.
         */
        private final float logit;

        /**
         * Legal-move softmax probability.
         */
        private final float probability;

        /**
         * Creates a scored CNN policy move row.
         *
         * @param move move
         * @param policyIndex policy index
         * @param logit raw logit
         * @param probability legal-move probability
         */
        CnnScoredMove(short move, int policyIndex, float logit, float probability) {
            this.move = move;
            this.policyIndex = policyIndex;
            this.logit = logit;
            this.probability = probability;
        }
    }

    /**
     * Per-layer summary used by the view.
     */
    private static final class LayerInfo {

        /**
         * Display name.
         */
        private String name = "";

        /**
         * Shape label.
         */
        private String shape = "";

        /**
         * Shape dimensions.
         */
        private int[] shapeDims = new int[0];

        /**
         * Flat values.
         */
        private float[] values = new float[0];

        /**
         * Mean.
         */
        private float mean;

        /**
         * RMS.
         */
        private float rms;

        /**
         * Minimum.
         */
        private float min;

        /**
         * Maximum.
         */
        private float max;
    }

    /**
     * One high-activity channel selected for the CNN atlas.
     */
    private static final class ChannelPick {

        /**
         * Owning layer.
         */
        private final LayerInfo layer;

        /**
         * Channel index inside the owning layer.
         */
        private final int channel;

        /**
         * Channel RMS score.
         */
        private final float score;

        /**
         * Creates a selected CNN channel summary.
         *
         * @param layer owning layer
         * @param channel channel index
         * @param score channel score
         */
        ChannelPick(LayerInfo layer, int channel, float score) {
            this.layer = layer;
            this.channel = channel;
            this.score = score;
        }
    }
}
