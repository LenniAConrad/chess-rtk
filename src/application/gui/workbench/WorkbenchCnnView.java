package application.gui.workbench;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;

/**
 * Workbench panel that visualises an LC0 CNN forward pass.
 *
 * <p>Abstract mode shows the residual-trunk activity strip, the WDL bars, the
 * top policy logits, and the final policy-attention heatmap overlaid on a
 * mini board. Detailed mode shows per-block channel statistics, lets the user
 * click any block to inspect its channel grid, and shows the policy/value
 * heads as a small pipeline of cards.</p>
 */
final class WorkbenchCnnView extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Outer panel padding.
     */
    private static final int PAD = 12;

    /**
     * Latest snapshot or null.
     */
    private WorkbenchActivationSnapshot snapshot;

    /**
     * Current position FEN.
     */
    private String fen;

    /**
     * Detailed-mode flag.
     */
    private boolean detailed;

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
     * Hover + click region registry, rebuilt every paint pass.
     */
    private final WorkbenchHitRegions hitRegions = new WorkbenchHitRegions();

    /**
     * Shared inspector panel; null until wired by the host.
     */
    private WorkbenchInspectorPanel inspector;

    /**
     * Whether to pin colour scales across position changes.
     */
    @SuppressWarnings("unused")
    private boolean fixedScale;

    /**
     * Creates the CNN view.
     */
    WorkbenchCnnView() {
        setOpaque(true);
        setBackground(WorkbenchTheme.BG);
        setPreferredSize(new Dimension(720, 540));
        ToolTipManager.sharedInstance().registerComponent(this);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                handleClick(event.getX(), event.getY());
            }
        });
    }

    /**
     * Returns the hover-tooltip text for the hit region under the cursor.
     *
     * @param event mouse event
     * @return HTML tooltip text or null
     */
    @Override
    public String getToolTipText(MouseEvent event) {
        WorkbenchHitRegions.Region r = hitRegions.hitTest(event.getX(), event.getY());
        return r == null ? null : r.tooltipHtml();
    }

    /**
     * Wires the shared inspector panel.
     *
     * @param panel inspector (may be null)
     */
    void setInspector(WorkbenchInspectorPanel panel) {
        this.inspector = panel;
    }

    /**
     * Sets the fixed-scale flag.
     *
     * @param value true to pin heatmap scales
     */
    void setFixedScale(boolean value) {
        if (this.fixedScale != value) {
            this.fixedScale = value;
            repaint();
        }
    }

    /**
     * Sets the activation snapshot.
     *
     * @param newSnapshot snapshot (may be null)
     */
    void setSnapshot(WorkbenchActivationSnapshot newSnapshot) {
        this.snapshot = newSnapshot;
        rebuildLayers();
        repaint();
    }

    /**
     * Sets the current FEN for mini-board piece rendering.
     *
     * @param newFen position FEN
     */
    void setFen(String newFen) {
        this.fen = newFen;
        repaint();
    }

    /**
     * Sets the detailed-mode flag.
     *
     * @param value true for detailed
     */
    void setDetailed(boolean value) {
        if (detailed != value) {
            detailed = value;
            cellHitBoxes.clear();
            cellLayerIndices.clear();
            invalidate();
            revalidate();
            repaint();
        }
    }

    /**
     * Returns the detailed-mode flag.
     *
     * @return true when detailed
     */
    boolean isDetailed() {
        return detailed;
    }

    /**
     * Paints the panel.
     *
     * @param graphics graphics
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (isOpaque()) {
            graphics.setColor(getBackground());
            graphics.fillRect(0, 0, getWidth(), getHeight());
        }
        hitRegions.clear();
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            WorkbenchTensorViz.useHighQuality(g);
            Rectangle bounds = new Rectangle(0, 0, getWidth(), getHeight());
            if (snapshot == null || snapshot.isEmpty()) {
                paintEmpty(g, bounds);
                return;
            }
            paintHeader(g, bounds);
            Rectangle body = new Rectangle(PAD, 64, getWidth() - 2 * PAD, getHeight() - 64 - PAD);
            if (detailed) {
                paintDetailed(g, body);
            } else {
                paintAbstract(g, body);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Handles a click in detailed mode (block selection).
     *
     * @param x x
     * @param y y
     */
    private void handleClick(int x, int y) {
        if (detailed) {
            for (int i = 0; i < cellHitBoxes.size(); ++i) {
                if (cellHitBoxes.get(i).contains(x, y)) {
                    selectedLayer = cellLayerIndices.get(i);
                    repaint();
                    return;
                }
            }
        }
        WorkbenchHitRegions.Region r = hitRegions.hitTest(x, y);
        if (r == null) {
            return;
        }
        if (inspector != null) {
            inspector.inspect(r, snapshot);
        } else if (r.dataKey != null && snapshot != null) {
            WorkbenchInspectorDialog.shared(this).inspect(r, snapshot);
        }
    }

    /**
     * Paints the empty placeholder.
     *
     * @param g graphics
     * @param bounds bounds
     */
    private void paintEmpty(Graphics2D g, Rectangle bounds) {
        g.setColor(WorkbenchTheme.BG);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(13, Font.PLAIN));
        g.drawString("No CNN snapshot. Change the FEN to refresh.", PAD, 32);
    }

    /**
     * Paints the title header.
     *
     * @param g graphics
     * @param bounds bounds
     */
    private void paintHeader(Graphics2D g, Rectangle bounds) {
        float[] wdl = snapshot.data("cnn.value.wdl");
        float[] scalar = snapshot.data("cnn.value.scalar");
        float v = scalar == null ? 0.0f : scalar[0];
        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(15, Font.BOLD));
        g.drawString("LC0 CNN (ResNet) activations", PAD, 22);
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
        int blocks = countLayersWithPrefix("B");
        String summary = "112 input planes -> stem -> " + blocks + " residual blocks -> policy / value heads";
        g.drawString(summary, PAD, 40);
        if (wdl != null && wdl.length >= 3) {
            Rectangle barRect = new Rectangle(getWidth() - 240 - PAD, 18, 240, 24);
            WorkbenchTensorViz.drawMetricBars(g, barRect, new String[] { "W", "D", "L" }, wdl, 1.0f);
            hitRegions.addInspectable(barRect,
                    "Value head (W/D/L)",
                    "Predicted win / draw / loss probabilities for side to move",
                    String.format("W %.2f · D %.2f · L %.2f", wdl[0], wdl[1], wdl[2]),
                    "cnn.value.wdl", 0, 3, 0, "3");
        }
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
        g.drawString(String.format("value scalar %+.3f", v), getWidth() - 240 - PAD, 56);
    }

    /**
     * Paints the abstract overview.
     *
     * @param g graphics
     * @param body body rectangle
     */
    private void paintAbstract(Graphics2D g, Rectangle body) {
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
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
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
        WorkbenchTensorViz.drawElbowConnection(g, inputR, stemR, WorkbenchTensorViz.TRUNK, true);
        WorkbenchTensorViz.drawElbowConnection(g, stemR, trunkR, WorkbenchTensorViz.TRUNK, true);
        WorkbenchTensorViz.drawElbowConnection(g, trunkR, finalR, WorkbenchTensorViz.TRUNK, true);
        WorkbenchTensorViz.drawAbstractBlock(g, inputR, "input planes",
                input == null ? "112x8x8" : input.shape, activity(input, scale), WorkbenchTensorViz.POSITIVE);
        WorkbenchTensorViz.drawAbstractBlock(g, stemR, "stem conv 3×3",
                stem == null ? "-" : stem.shape, activity(stem, scale), WorkbenchTensorViz.TRUNK);
        int blocks = countLayersWithPrefix("B");
        // Render the trunk as a stack of three offset silhouette rectangles
        // BEHIND the main block so the user can read it as a repeating stack
        // of residual blocks rather than a single layer.
        drawTrunkStack(g, trunkR, blocks, trunkRms / scale);
        WorkbenchTensorViz.drawAbstractBlock(g, finalR, "final feature map",
                finalLayer == null ? "8x8" : finalLayer.shape, activity(finalLayer, scale), WorkbenchTensorViz.VALUE);
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
        WorkbenchTensorViz.drawElbowConnection(g, finalR, policyR, WorkbenchTensorViz.POLICY, true);
        WorkbenchTensorViz.drawElbowConnection(g, finalR, valueR, WorkbenchTensorViz.VALUE, true);
        WorkbenchTensorViz.drawAbstractBlock(g, policyR, "policy head → 1858",
                policy == null ? "logits" : policy.shape, activity(policy, scale), WorkbenchTensorViz.POLICY);
        WorkbenchTensorViz.drawAbstractBlock(g, valueR, "value head → WDL",
                value == null ? "W/D/L" : value.shape, activity(value, scale), WorkbenchTensorViz.VALUE);
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
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
            g.drawString("per-block activity (left = early block, right = late block)",
                    stripR.x, stripR.y - 4);
        }
    }

    /**
     * Renders a "stacked rectangles" silhouette behind the trunk block to
     * suggest the repeating residual stack. The foreground block is drawn
     * separately by the caller via {@link WorkbenchTensorViz#drawAbstractBlock}.
     *
     * @param g graphics
     * @param r front block rectangle
     * @param blocks number of residual blocks
     * @param activity 0..1 activity scalar (drives accent intensity)
     */
    private static void drawTrunkStack(Graphics2D g, Rectangle r, int blocks, float activity) {
        int layers = Math.min(4, Math.max(2, blocks / 4));
        Color edge = WorkbenchTensorViz.TRUNK;
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
        WorkbenchTensorViz.drawAbstractBlock(g, r, "residual trunk",
                blocks + " × residual block", activity, WorkbenchTensorViz.TRUNK);
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
            g.setColor(WorkbenchTensorViz.TRUNK);
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
        g.setColor(WorkbenchTheme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
    }

    /**
     * Paints the policy-head heatmap overlay on a small board.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintPolicyHeatmap(Graphics2D g, Rectangle r) {
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
                "policy attention", "final map mean over channels overlaid on the board");
        float[] heatmap = snapshot.data("cnn.final.activation");
        int size = Math.min(r.width, r.height - 60);
        Rectangle board = new Rectangle(r.x + (r.width - size) / 2, r.y + 50, size - 16, size - 16);
        WorkbenchTensorViz.drawMiniBoard(g, board);
        WorkbenchTensorViz.drawPositionPieces(g, board, fen);
        if (heatmap != null && heatmap.length >= 64) {
            WorkbenchTensorViz.drawSquareOverlay(g, board, heatmap, 0.0f, false);
            addBoardSquareTooltips(board, heatmap, "Final-map mean activation");
        }
        WorkbenchTensorViz.drawBoardCoordinates(g, board);

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
                    WorkbenchTensorViz.squareLabel(sq),
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
        WorkbenchTensorViz.drawMetricBars(g, r, labels, values, scale);
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
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
    private void paintDetailed(Graphics2D g, Rectangle body) {
        cellHitBoxes.clear();
        cellLayerIndices.clear();
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(body.x, body.y, body.width, 40),
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
            WorkbenchTensorViz.drawCard(g, card, info.name, info.shape, accent);
            float a = WorkbenchTensorViz.clamp(info.rms / scale, 0.0f, 1.0f);
            int barW = card.width - 16;
            g.setColor(WorkbenchTheme.LINE);
            g.fillRect(card.x + 8, card.y + card.height - 10, barW, 3);
            g.setColor(accent);
            g.fillRect(card.x + 8, card.y + card.height - 10, Math.round(barW * a), 3);
            if (selected) {
                g.setColor(WorkbenchTheme.ACCENT);
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
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 18),
                "position", "mean activity of the selected layer overlaid on the board");
        int size = Math.min(r.width - 8, r.height - 80);
        Rectangle board = new Rectangle(r.x + (r.width - size) / 2, r.y + 24, size, size);
        WorkbenchTensorViz.drawMiniBoard(g, board);
        WorkbenchTensorViz.drawPositionPieces(g, board, fen);
        int idx = selectedLayer < 0 || selectedLayer >= layers.size() ? defaultLayer() : selectedLayer;
        if (idx >= 0) {
            LayerInfo info = layers.get(idx);
            float[] perSquare = meanPerSquare(info);
            if (perSquare != null) {
                WorkbenchTensorViz.drawSquareOverlay(g, board, perSquare, 0.0f, false);
                addBoardSquareTooltips(board, perSquare,
                        info.name + " · mean activity (channel-averaged)");
            }
            WorkbenchTensorViz.drawBoardCoordinates(g, board);
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
            g.drawString("layer: " + info.name + "  ·  " + info.shape,
                    r.x + 6, board.y + board.height + 18);
            g.drawString(String.format("rms %.3f   mean %+.3f   range %+.2f..%+.2f",
                    info.rms, info.mean, info.min, info.max),
                    r.x + 6, board.y + board.height + 34);
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
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 18),
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
            WorkbenchTensorViz.drawMiniBoard(g, board);
            float[] slice = new float[64];
            System.arraycopy(info.values, c * 64, slice, 0, 64);
            WorkbenchTensorViz.drawSquareOverlay(g, board, slice, 0.0f, false);
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(10, Font.BOLD));
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
        for (int c = 0; c < show; ++c) {
            int row = c / cols;
            int col = c % cols;
            int x = r.x + col * (cellW + 6);
            int y = r.y + row * (cellH + 6);
            float[] slice = new float[64];
            System.arraycopy(info.values, c * 64, slice, 0, 64);
            Rectangle cell = new Rectangle(x, y, cellW, cellH);
            WorkbenchTensorViz.drawHeatmap(g, cell, slice, 8, 8, 0.0f, true);
            float[] stats = WorkbenchTensorViz.summarize(slice);
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
        g.setColor(WorkbenchTheme.LINE);
        g.drawLine(r.x, midY, r.x + r.width, midY);
        for (int i = 0; i < n; ++i) {
            int x = r.x + i;
            int sampleIdx = (int) Math.floor((double) i / n * values.length);
            float v = values[sampleIdx] / scale;
            int h = (int) Math.round(Math.abs(v) * (r.height / 2.0));
            g.setColor(v >= 0 ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE);
            if (v >= 0) {
                g.drawLine(x, midY, x, midY - h);
            } else {
                g.drawLine(x, midY, x, midY + h);
            }
        }
        g.setColor(WorkbenchTheme.LINE);
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
        return WorkbenchTensorViz.clamp(info.rms / scale, 0.0f, 1.0f);
    }

    /**
     * Returns the accent color for a layer label.
     *
     * @param name name
     * @return color
     */
    private static Color colorFor(String name) {
        if (name.startsWith("p")) {
            return WorkbenchTensorViz.POLICY;
        }
        if (name.startsWith("v") || name.equals("WDL")) {
            return WorkbenchTensorViz.VALUE;
        }
        if (name.equals("input")) {
            return WorkbenchTensorViz.POSITIVE;
        }
        return WorkbenchTensorViz.TRUNK;
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
        WorkbenchActivationSnapshot.Entry entry = snapshot.get(key);
        if (entry == null) {
            return;
        }
        LayerInfo info = new LayerInfo();
        info.name = label;
        info.shape = entry.shapeText();
        info.shapeDims = entry.shape();
        info.values = entry.data();
        float[] stats = WorkbenchTensorViz.summarize(info.values);
        info.mean = stats[0];
        info.rms = stats[2];
        info.min = stats[3];
        info.max = stats[4];
        layers.add(info);
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
}
