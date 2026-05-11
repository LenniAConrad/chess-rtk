package application.gui.workbench;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;

/**
 * Workbench panel that visualises an LC0 BT4 transformer forward pass.
 *
 * <p>Abstract mode shows the WDL bars, the per-block activity strip, and the
 * token-energy heatmap overlaid on a small board. Detailed mode lets the user
 * pick a block (1..15) and a head (1..32), shows the chosen head's 64x64
 * attention map, and renders an on-board overlay that highlights the
 * attended squares when a board square is clicked.</p>
 */
final class WorkbenchBt4View extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Block count for the demo BT4 model.
     */
    private static final int BLOCKS = 15;

    /**
     * Head count per block.
     */
    private static final int HEADS = 32;

    /**
     * Outer padding.
     */
    private static final int PAD = 12;

    /**
     * Latest snapshot or null.
     */
    private WorkbenchActivationSnapshot snapshot;

    /**
     * Current position FEN, used to render pieces on mini boards.
     */
    private String fen;

    /**
     * Detailed-mode flag.
     */
    private boolean detailed;

    /**
     * Selected transformer block in detailed mode (0..BLOCKS-1).
     */
    private int selectedBlock;

    /**
     * Selected attention head in detailed mode (0..HEADS-1).
     */
    private int selectedHead;

    /**
     * Selected board square in detailed mode (-1 = none, 0..63).
     */
    private int selectedSquare = -1;

    /**
     * Cached hit-box for the head grid.
     */
    private Rectangle headGridBounds = new Rectangle();

    /**
     * Cached hit-box for the on-board panel.
     */
    private Rectangle boardBounds = new Rectangle();

    /**
     * Cached hit-box for the block strip.
     */
    private Rectangle blockStripBounds = new Rectangle();

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
    private boolean fixedScale;

    /**
     * Per-bucket pinned heatmap scales, used while {@link #fixedScale} is on
     * so attention/energy heatmaps stay comparable across position changes.
     */
    private final java.util.Map<String, Float> pinnedScales = new java.util.HashMap<>();

    /**
     * Creates the BT4 view.
     */
    WorkbenchBt4View() {
        setOpaque(true);
        setBackground(WorkbenchTheme.BG);
        setPreferredSize(new Dimension(760, 540));
        selectedBlock = BLOCKS - 1;
        selectedHead = 0;
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
     * Sets the fixed-scale flag. Resets pinned heatmap scales when the
     * toggle flips so the next snapshot starts a fresh baseline.
     *
     * @param value true to pin heatmap scales
     */
    void setFixedScale(boolean value) {
        if (this.fixedScale != value) {
            this.fixedScale = value;
            pinnedScales.clear();
            repaint();
        }
    }

    /**
     * Returns the colour-scale for a heatmap bucket. With fixed-scale off
     * the bucket is cleared and the dynamic max is returned; with fixed-scale
     * on the bucket's pinned scale grows monotonically across position
     * changes so attention magnitudes stay comparable.
     *
     * @param key bucket key
     * @param dynamicMax max-abs of the current snapshot data
     * @return scale (always &gt; 0)
     */
    private float scaleFor(String key, float dynamicMax) {
        float dm = dynamicMax <= 0.0f ? 1.0f : dynamicMax;
        if (!fixedScale) {
            pinnedScales.remove(key);
            return dm;
        }
        Float pinned = pinnedScales.get(key);
        float merged = pinned == null ? dm : Math.max(pinned, dm);
        pinnedScales.put(key, merged);
        return merged;
    }

    /**
     * Max-abs of an array, or 0 when null/empty.
     *
     * @param data values
     * @return max absolute value
     */
    private static float maxAbs(float[] data) {
        float m = 0.0f;
        if (data != null) {
            for (float v : data) {
                float a = Math.abs(v);
                if (a > m) {
                    m = a;
                }
            }
        }
        return m;
    }

    /**
     * Sets the activation snapshot.
     *
     * @param newSnapshot snapshot (may be null)
     */
    void setSnapshot(WorkbenchActivationSnapshot newSnapshot) {
        this.snapshot = newSnapshot;
        repaint();
    }

    /**
     * Sets the current FEN. Used for mini-board piece rendering.
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
            selectedSquare = -1;
            headGridBounds = new Rectangle();
            boardBounds = new Rectangle();
            blockStripBounds = new Rectangle();
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
     * Handles a mouse click.
     *
     * @param x x
     * @param y y
     */
    private void handleClick(int x, int y) {
        if (!detailed) {
            WorkbenchHitRegions.Region r = hitRegions.hitTest(x, y);
            if (r == null) {
                return;
            }
            if (inspector != null) {
                inspector.inspect(r, snapshot);
            } else if (r.dataKey != null && snapshot != null) {
                WorkbenchInspectorDialog.shared(this).inspect(r, snapshot);
            }
            return;
        }
        if (blockStripBounds.contains(x, y)) {
            int relX = x - blockStripBounds.x;
            int blk = (int) Math.floor(relX * (double) BLOCKS / Math.max(1, blockStripBounds.width));
            selectedBlock = Math.max(0, Math.min(BLOCKS - 1, blk));
            repaint();
            return;
        }
        if (headGridBounds.contains(x, y)) {
            int relX = x - headGridBounds.x;
            int relY = y - headGridBounds.y;
            int cols = 4;
            int rows = HEADS / cols;
            int cellW = headGridBounds.width / cols;
            int cellH = headGridBounds.height / rows;
            int col = Math.max(0, Math.min(cols - 1, relX / Math.max(1, cellW)));
            int row = Math.max(0, Math.min(rows - 1, relY / Math.max(1, cellH)));
            selectedHead = row * cols + col;
            repaint();
            return;
        }
        if (boardBounds.contains(x, y)) {
            int relX = x - boardBounds.x;
            int relY = y - boardBounds.y;
            int file = Math.max(0, Math.min(7, relX * 8 / Math.max(1, boardBounds.width)));
            int drawRank = Math.max(0, Math.min(7, relY * 8 / Math.max(1, boardBounds.height)));
            int rank = 7 - drawRank;
            int sq = rank * 8 + file;
            // Click same square again to deselect; otherwise select it.
            selectedSquare = (sq == selectedSquare) ? -1 : sq;
            repaint();
            return;
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
     * Paints the empty hint.
     *
     * @param g graphics
     * @param bounds bounds
     */
    private void paintEmpty(Graphics2D g, Rectangle bounds) {
        g.setColor(WorkbenchTheme.BG);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(13, Font.PLAIN));
        g.drawString("No BT4 snapshot. Change the FEN to refresh.", PAD, 32);
    }

    /**
     * Paints the title header.
     *
     * @param g graphics
     * @param bounds bounds
     */
    private void paintHeader(Graphics2D g, Rectangle bounds) {
        float[] wdl = snapshot.data("bt4.value.wdl");
        float[] scalar = snapshot.data("bt4.value.scalar");
        float v = scalar == null ? 0.0f : scalar[0];
        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(15, Font.BOLD));
        g.drawString("LC0 BT4 (transformer) activations", PAD, 22);
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
        g.drawString(BLOCKS + " blocks · " + HEADS + " heads · 64 tokens (squares) · 768-d embedding", PAD, 40);
        if (wdl != null && wdl.length >= 3) {
            Rectangle barRect = new Rectangle(getWidth() - 240 - PAD, 18, 240, 24);
            WorkbenchTensorViz.drawMetricBars(g, barRect, new String[] { "W", "D", "L" }, wdl, 1.0f);
            hitRegions.addInspectable(barRect,
                    "Value head (W/D/L)",
                    "Predicted win/draw/loss for side to move",
                    String.format("W %.2f · D %.2f · L %.2f", wdl[0], wdl[1], wdl[2]),
                    "bt4.value.wdl", 0, 3, 0, "3");
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
        int leftW = (int) (body.width * 0.58);
        Rectangle left = new Rectangle(body.x, body.y, leftW, body.height);
        Rectangle right = new Rectangle(body.x + leftW + 10, body.y, body.width - leftW - 10, body.height);
        paintAbstractPipeline(g, left);
        paintTokenEnergyBoard(g, right);
    }

    /**
     * Paints the abstract pipeline (block strip + summary cards).
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintAbstractPipeline(Graphics2D g, Rectangle r) {
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
                "abstract flow", "input -> token embedding -> stacked transformer blocks -> heads");
        int top = r.y + 56;
        int cardW = (r.width - 32) / 3;
        int cardH = 70;
        Rectangle inR = new Rectangle(r.x + 8, top, cardW, cardH);
        Rectangle emR = new Rectangle(inR.x + cardW + 8, top, cardW, cardH);
        Rectangle outR = new Rectangle(emR.x + cardW + 8, top, cardW, cardH);
        WorkbenchTensorViz.drawElbowConnection(g, inR, emR, WorkbenchTensorViz.TRUNK, true);
        WorkbenchTensorViz.drawElbowConnection(g, emR, outR, WorkbenchTensorViz.TRUNK, true);
        WorkbenchTensorViz.drawAbstractBlock(g, inR, "input planes", "112x8x8", 1.0f,
                WorkbenchTensorViz.POSITIVE);
        WorkbenchTensorViz.drawAbstractBlock(g, emR, "token embedding", "64x768", 0.7f,
                WorkbenchTensorViz.TRUNK);
        WorkbenchTensorViz.drawAbstractBlock(g, outR, "final tokens", "64x768", 0.6f,
                WorkbenchTensorViz.VALUE);
        hitRegions.add(inR, "Input planes",
                "112 board planes (own/enemy pieces, castling, side-to-move, repetition...)",
                "112 x 8 x 8");
        hitRegions.add(emR, "Token embedding",
                "Each of 64 squares becomes one transformer token of dimension 768.",
                "64 tokens · 768 dim");
        hitRegions.add(outR, "Final tokens",
                "Output of the last encoder block; consumed by policy + value heads.",
                "64 tokens · 768 dim");

        int stripY = top + cardH + 28;
        Rectangle strip = new Rectangle(r.x + 8, stripY, r.width - 16, 60);
        paintBlockStrip(g, strip);
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
        g.drawString("per-block attention focus (top) and ffn energy (bottom)", strip.x, strip.y - 4);

        int policyY = stripY + strip.height + 24;
        if (policyY + 60 <= r.y + r.height) {
            Rectangle policyR = new Rectangle(r.x + 8, policyY, r.width - 16, r.y + r.height - policyY - 4);
            paintTopPolicy(g, policyR);
        }
    }

    /**
     * Paints the per-block attention/ffn strip.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintBlockStrip(Graphics2D g, Rectangle r) {
        int gap = 3;
        int cellW = Math.max(8, (r.width - gap * (BLOCKS - 1)) / BLOCKS);
        int half = r.height / 2;
        g.setColor(WorkbenchTheme.PANEL_SOLID);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(WorkbenchTheme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
        float maxAttn = 0.0f;
        float maxFfn = 0.0f;
        float[] attnFocus = new float[BLOCKS];
        float[] ffnEnergy = new float[BLOCKS];
        for (int b = 0; b < BLOCKS; ++b) {
            attnFocus[b] = attentionFocus(b);
            float[] ffn = snapshot.data("bt4.block" + b + ".ffn");
            float[] stats = WorkbenchTensorViz.summarize(ffn);
            ffnEnergy[b] = stats[2];
            maxAttn = Math.max(maxAttn, attnFocus[b]);
            maxFfn = Math.max(maxFfn, ffnEnergy[b]);
        }
        if (maxAttn <= 0.0f) {
            maxAttn = 1.0f;
        }
        if (maxFfn <= 0.0f) {
            maxFfn = 1.0f;
        }
        for (int b = 0; b < BLOCKS; ++b) {
            int x = r.x + b * (cellW + gap);
            int hAttn = (int) Math.round(attnFocus[b] / maxAttn * (half - 2));
            int hFfn = (int) Math.round(ffnEnergy[b] / maxFfn * (half - 2));
            g.setColor(WorkbenchTensorViz.POLICY);
            g.fillRect(x, r.y + (half - hAttn), cellW, hAttn);
            g.setColor(WorkbenchTensorViz.VALUE);
            g.fillRect(x, r.y + half + 1, cellW, hFfn);
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(9, Font.PLAIN));
            String label = Integer.toString(b + 1);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(label, x + Math.max(0, (cellW - fm.stringWidth(label)) / 2), r.y + r.height - 2);
            hitRegions.addInspectable(new Rectangle(x, r.y, cellW + gap, r.height),
                    "Block " + (b + 1),
                    "Top half: mean attention magnitude · bottom half: ffn energy",
                    String.format("attn %.4f · ffn rms %.4f", attnFocus[b], ffnEnergy[b]),
                    "bt4.block" + b + ".attention.heads", 0, 0, 64, "32x64x64");
        }
    }

    /**
     * Paints the token-energy board overlay.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintTokenEnergyBoard(Graphics2D g, Rectangle r) {
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
                "token energy + top moves", "attention received · top-K policy as arrows");
        int size = Math.min(r.width, r.height - 60);
        Rectangle board = new Rectangle(r.x + (r.width - size) / 2, r.y + 50, size - 16, size - 16);
        WorkbenchTensorViz.drawMiniBoard(g, board);
        WorkbenchTensorViz.drawPositionPieces(g, board, fen);
        float[] energy = snapshot.data("bt4.token.energy");
        if (energy != null && energy.length >= 64) {
            float s = scaleFor("tokenEnergy", maxAbs(energy));
            WorkbenchTensorViz.drawSquareOverlay(g, board, energy, s, false);
            addBoardSquareTooltips(board, energy, "Mean attention received");
        }
        WorkbenchTensorViz.drawBoardCoordinates(g, board);
    }

    /**
     * Adds 64 per-square hover regions over an 8x8 board overlay.
     *
     * @param board pixel rectangle covering the 8x8 grid
     * @param values 64 values in rank-major order (rank 0 = first rank)
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
                    String.format("%+.4f", values[sq]));
        }
    }

    /**
     * Paints a small top-policy bars block.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintTopPolicy(Graphics2D g, Rectangle r) {
        float[] policy = snapshot.data("bt4.policy.logits");
        if (policy == null) {
            return;
        }
        java.util.List<chess.nn.lc0.bt4.PolicyEncoder.ScoredMove> top = decodeTopMoves(policy, 6);
        if (top.isEmpty()) {
            return;
        }
        int n = top.size();
        String[] labels = new String[n];
        float[] values = new float[n];
        float scale = 0.0f;
        for (int i = 0; i < n; ++i) {
            labels[i] = chess.core.Move.toString(top.get(i).move()) + "  " + String.format("%.1f%%", top.get(i).probability() * 100);
            values[i] = top.get(i).probability();
            scale = Math.max(scale, values[i]);
        }
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
        g.drawString("top moves (legal-softmax probability)", r.x, r.y - 4);
        WorkbenchTensorViz.drawMetricBars(g, r, labels, values, scale);
        int barH = r.height / Math.max(1, n);
        for (int i = 0; i < n; ++i) {
            Rectangle row = new Rectangle(r.x, r.y + i * barH, r.width, barH);
            var sm = top.get(i);
            hitRegions.addInspectable(row,
                    "Top move " + chess.core.Move.toString(sm.move()),
                    "LC0 policy index " + sm.policyIndex() + " · logit " + String.format("%+.3f", sm.logit()),
                    String.format("legal-softmax %.2f%% · rank %d", sm.probability() * 100, i + 1),
                    "bt4.policy.logits", 0, 0, 0, policy.length + "");
        }
    }

    /**
     * Decodes the top-K legal moves from the policy logits, using the
     * captured canonical transform.
     *
     * @param policy compressed policy logits
     * @param k maximum number of moves
     * @return list of scored moves, may be empty
     */
    private java.util.List<chess.nn.lc0.bt4.PolicyEncoder.ScoredMove> decodeTopMoves(float[] policy, int k) {
        if (fen == null || fen.isBlank()) {
            return java.util.Collections.emptyList();
        }
        chess.core.Position position;
        try {
            position = new chess.core.Position(fen);
        } catch (RuntimeException ex) {
            return java.util.Collections.emptyList();
        }
        float[] tx = snapshot.data("bt4.input.transform");
        int transform = tx == null ? 0 : Math.round(tx[0]);
        return chess.nn.lc0.bt4.PolicyEncoder.topLegalMoves(position, policy, transform, k);
    }

    /**
     * Paints the detailed per-head view.
     *
     * @param g graphics
     * @param body body rectangle
     */
    private void paintDetailed(Graphics2D g, Rectangle body) {
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(body.x, body.y, body.width, 40),
                "per-head attention",
                "block 1..15 · head 1..32 · click a head, then click any board square");
        int top = body.y + 50;

        Rectangle blockBar = new Rectangle(body.x, top, body.width, 22);
        paintBlockSelector(g, blockBar);
        blockStripBounds = blockBar;

        int columnTop = blockBar.y + blockBar.height + 12;
        int columnH = body.height - (columnTop - body.y) - 8;
        int gridW = Math.min(360, body.width / 4);
        int matrixW = Math.min(360, (body.width - gridW - 24) / 2);
        Rectangle gridR = new Rectangle(body.x, columnTop, gridW,
                Math.min(columnH, gridW * 2));
        Rectangle matrixR = new Rectangle(body.x + gridW + 12, columnTop, matrixW,
                Math.min(matrixW + 24, columnH));
        Rectangle boardR = new Rectangle(matrixR.x + matrixW + 12, columnTop,
                body.width - (matrixR.x + matrixW + 12 - body.x), columnH);

        paintHeadGrid(g, gridR);
        headGridBounds = gridR;
        paintAttentionMatrix(g, matrixR);
        // paintBoardOverlay sets boardBounds to the inner mini-board rectangle;
        // do not overwrite it here, otherwise click coordinates are mapped
        // against the surrounding column and decoded squares are off.
        paintBoardOverlay(g, boardR);

        Rectangle bottom = new Rectangle(body.x, gridR.y + gridR.height + 12,
                gridW + matrixW + 12, body.y + body.height - (gridR.y + gridR.height + 12) - 4);
        if (bottom.height > 24) {
            paintHeadReadout(g, bottom);
        }
    }

    /**
     * Paints the block selector strip.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintBlockSelector(Graphics2D g, Rectangle r) {
        g.setColor(WorkbenchTheme.PANEL_SOLID);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(WorkbenchTheme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
        int cellW = r.width / BLOCKS;
        g.setFont(WorkbenchTheme.font(11, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        for (int b = 0; b < BLOCKS; ++b) {
            int x = r.x + b * cellW;
            boolean sel = b == selectedBlock;
            g.setColor(sel ? WorkbenchTheme.ACCENT : WorkbenchTheme.ELEVATED_SOLID);
            g.fillRect(x + 1, r.y + 1, cellW - 2, r.height - 2);
            g.setColor(sel ? Color.WHITE : WorkbenchTheme.TEXT);
            String label = Integer.toString(b + 1);
            g.drawString(label, x + Math.max(0, (cellW - fm.stringWidth(label)) / 2),
                    r.y + r.height - 6);
            hitRegions.add(new Rectangle(x, r.y, cellW, r.height),
                    "Block " + (b + 1),
                    "Click to switch the head grid + attention matrix to this block.",
                    sel ? "currently selected" : "");
        }
    }

    /**
     * Paints the head grid. Each of the 32 cells now contains an 8×8 mini
     * heatmap of the head's per-token attention-received pattern, so the
     * user can compare all heads visually at once instead of relying on a
     * single scalar (peak attention) per head.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintHeadGrid(Graphics2D g, Rectangle r) {
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y - 18, r.width, 18),
                "heads", "per-head 8×8 attention-received heatmap · click one to focus");
        int cols = 4;
        int rows = HEADS / cols;
        int cellW = r.width / cols;
        int cellH = r.height / rows;
        float[] heads = snapshot.data("bt4.block" + selectedBlock + ".attention.heads");
        if (heads == null) {
            return;
        }
        float[][] perHeadEnergy = new float[HEADS][64];
        float globalMax = 0.0f;
        for (int h = 0; h < HEADS; ++h) {
            int off = h * 64 * 64;
            if (off + 64 * 64 > heads.length) {
                continue;
            }
            for (int from = 0; from < 64; ++from) {
                for (int to = 0; to < 64; ++to) {
                    perHeadEnergy[h][to] += heads[off + from * 64 + to];
                }
            }
            for (int s = 0; s < 64; ++s) {
                perHeadEnergy[h][s] /= 64.0f;
                if (perHeadEnergy[h][s] > globalMax) {
                    globalMax = perHeadEnergy[h][s];
                }
            }
        }
        if (globalMax <= 0.0f) {
            globalMax = 1.0f;
        }
        globalMax = scaleFor("headGrid:b" + selectedBlock, globalMax);
        for (int h = 0; h < HEADS; ++h) {
            int col = h % cols;
            int row = h / cols;
            int x = r.x + col * cellW;
            int y = r.y + row * cellH;
            int padTop = 12;
            int hmW = cellW - 4;
            int hmH = cellH - padTop - 2;
            Rectangle heat = new Rectangle(x + 2, y + padTop, hmW, hmH);
            WorkbenchTensorViz.drawHeatmap(g, heat, perHeadEnergy[h], 8, 8, globalMax, false);
            if (selectedSquare >= 0) {
                // Cross-highlight the selected board square in every head
                // thumbnail so the user can see at a glance which heads care
                // about that square without scanning all 32.
                drawHeadSquareMarker(g, heat, selectedSquare,
                        perHeadEnergy[h][selectedSquare], globalMax);
            }
            if (h == selectedHead) {
                g.setColor(WorkbenchTheme.ACCENT);
                g.drawRect(x, y, cellW - 1, cellH - 1);
                g.drawRect(x + 1, y + 1, cellW - 3, cellH - 3);
            } else {
                g.setColor(WorkbenchTheme.LINE);
                g.drawRect(x, y, cellW - 1, cellH - 1);
            }
            g.setColor(WorkbenchTheme.TEXT);
            g.setFont(WorkbenchTheme.font(10, Font.BOLD));
            g.drawString("h" + (h + 1), x + 4, y + 11);
            float peak = 0.0f;
            for (float v : perHeadEnergy[h]) {
                if (v > peak) {
                    peak = v;
                }
            }
            String desc = selectedSquare >= 0
                    ? "Click to load this head's full 64×64 attention matrix. " + WorkbenchTensorViz.squareLabel(selectedSquare)
                            + " attention-received = " + String.format("%.4f", perHeadEnergy[h][selectedSquare])
                    : "Click to load this head's full 64×64 attention matrix.";
            hitRegions.add(new Rectangle(x, y, cellW, cellH),
                    "Head " + (h + 1),
                    desc,
                    String.format("attention-received peak %.4f", peak));
        }
    }

    /**
     * Draws a small marker outline around the selected square inside a head
     * thumbnail. The marker is brighter for heads whose attention on that
     * square is large relative to the global scale, so the user can spot
     * "heads that care about this square" without reading numbers.
     *
     * @param g graphics
     * @param heat thumbnail rectangle
     * @param square selected board square 0..63
     * @param value attention value for that square in this head
     * @param scale global colour scale
     */
    private static void drawHeadSquareMarker(Graphics2D g, Rectangle heat, int square,
            float value, float scale) {
        int file = square & 7;
        int rank = square >> 3;
        int drawRank = 7 - rank;
        double cellW = heat.width / 8.0;
        double cellH = heat.height / 8.0;
        int x = (int) Math.round(heat.x + file * cellW);
        int y = (int) Math.round(heat.y + drawRank * cellH);
        int w = Math.max(2, (int) Math.round(cellW));
        int h = Math.max(2, (int) Math.round(cellH));
        float ratio = scale > 0.0f ? Math.min(1.0f, value / scale) : 0.0f;
        int alpha = 110 + Math.round(120 * ratio);
        Color edge = new Color(WorkbenchTheme.ACCENT.getRed(),
                WorkbenchTheme.ACCENT.getGreen(),
                WorkbenchTheme.ACCENT.getBlue(),
                Math.min(255, alpha));
        g.setColor(edge);
        g.drawRect(x, y, w - 1, h - 1);
    }

    /**
     * Paints the 64x64 attention matrix for the selected head.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintAttentionMatrix(Graphics2D g, Rectangle r) {
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y - 18, r.width, 18),
                "attention matrix", "rows = from-square, cols = to-square");
        float[] heads = snapshot.data("bt4.block" + selectedBlock + ".attention.heads");
        int size = Math.min(r.width - 28, r.height - 28);
        Rectangle map = new Rectangle(r.x + 24, r.y + 4, size, size);
        if (heads == null) {
            WorkbenchTensorViz.drawEmpty(g, map);
            return;
        }
        int off = selectedHead * 64 * 64;
        if (off + 64 * 64 > heads.length) {
            WorkbenchTensorViz.drawEmpty(g, map);
            return;
        }
        float[] slice = new float[64 * 64];
        System.arraycopy(heads, off, slice, 0, 64 * 64);
        float matrixScale = scaleFor(
                "attentionMatrix:b" + selectedBlock + "h" + selectedHead, maxAbs(slice));
        WorkbenchTensorViz.drawHeatmap(g, map, slice, 64, 64, matrixScale, false);
        hitRegions.addInspectable(map,
                "Block " + (selectedBlock + 1) + " head " + (selectedHead + 1) + " attention",
                "64x64 attention matrix · rows = from-square · cols = to-square",
                "click to inspect full 4096 values",
                "bt4.block" + selectedBlock + ".attention.heads",
                selectedHead * 64 * 64, 64 * 64, 64, "64x64");
        if (selectedSquare >= 0) {
            g.setColor(new Color(WorkbenchTensorViz.NEGATIVE.getRed(),
                    WorkbenchTensorViz.NEGATIVE.getGreen(), WorkbenchTensorViz.NEGATIVE.getBlue(), 80));
            int rowY = (int) Math.round(map.y + selectedSquare * (map.height / 64.0));
            int rowH = Math.max(1, (int) Math.round(map.height / 64.0));
            g.fillRect(map.x, rowY, map.width, rowH);
            int colX = (int) Math.round(map.x + selectedSquare * (map.width / 64.0));
            int colW = Math.max(1, (int) Math.round(map.width / 64.0));
            g.fillRect(colX, map.y, colW, map.height);
            // axis labels
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
            g.drawString(WorkbenchTensorViz.squareLabel(selectedSquare),
                    map.x - 22, rowY + rowH);
            g.drawString(WorkbenchTensorViz.squareLabel(selectedSquare),
                    colX, map.y - 4);
        }
    }

    /**
     * Paints the on-board overlay panel. Each square is split diagonally:
     * the upper-left triangle is shaded by attention <em>from</em> the
     * selected square to this one (selected → this), the lower-right
     * triangle by attention <em>to</em> the selected square from this one
     * (this → selected). Both directions are visible simultaneously so
     * the user does not have to toggle between modes.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintBoardOverlay(Graphics2D g, Rectangle r) {
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y - 18, r.width, 18),
                "attention (▲ selected → this · ▼ this → selected)",
                "upper-left triangle = outgoing from selected · lower-right = incoming to selected");
        int size = Math.min(r.width - 20, r.height - 60);
        Rectangle board = new Rectangle(r.x + (r.width - size) / 2, r.y + 8, size - 16, size - 16);
        WorkbenchTensorViz.drawMiniBoard(g, board);
        WorkbenchTensorViz.drawPositionPieces(g, board, fen);
        boardBounds = board;
        if (selectedSquare >= 0) {
            float[] heads = snapshot.data("bt4.block" + selectedBlock + ".attention.heads");
            if (heads != null) {
                int off = selectedHead * 64 * 64;
                float[] toOverlay = new float[64];
                float[] fromOverlay = new float[64];
                for (int sq = 0; sq < 64; ++sq) {
                    toOverlay[sq] = heads[off + selectedSquare * 64 + sq];
                    fromOverlay[sq] = heads[off + sq * 64 + selectedSquare];
                }
                float dynamicMax = Math.max(maxAbs(toOverlay), maxAbs(fromOverlay));
                float scale = scaleFor("boardOverlay:joint", dynamicMax);
                drawTriangleOverlay(g, board, toOverlay, fromOverlay, scale);
                addTriangleTooltips(board, toOverlay, fromOverlay);
                int sf = selectedSquare & 7;
                int sr = selectedSquare >> 3;
                int dr = 7 - sr;
                int cellW = board.width / 8;
                int cellH = board.height / 8;
                g.setColor(WorkbenchTheme.ACCENT);
                g.drawRect(board.x + sf * cellW, board.y + dr * cellH, cellW, cellH);
                g.drawRect(board.x + sf * cellW + 1, board.y + dr * cellH + 1, cellW - 2, cellH - 2);
            }
        }
        WorkbenchTensorViz.drawBoardCoordinates(g, board);
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
        String hint = selectedSquare < 0
                ? "no square selected — click a board square"
                : "selected: " + WorkbenchTensorViz.squareLabel(selectedSquare)
                        + " · click it again to clear";
        g.drawString(hint, r.x + 8, r.y + r.height - 4);
    }

    /**
     * Draws the two-triangle attention overlay. For each of the 64 squares
     * the cell is split along its main diagonal: the upper-left half is
     * filled by an orange "outgoing" colour scaled by the attention from
     * the selected square to this square, and the lower-right half by a
     * teal "incoming" colour scaled by the attention from this square to
     * the selected square. Same colour scale for both directions so the
     * triangles are directly comparable.
     *
     * @param g graphics
     * @param board pixel rectangle covering the 8x8 grid
     * @param toData per-square attention values for selected → this
     * @param fromData per-square attention values for this → selected
     * @param scale shared colour scale (max value mapped to full opacity)
     */
    private static void drawTriangleOverlay(java.awt.Graphics2D g, Rectangle board,
            float[] toData, float[] fromData, float scale) {
        if (toData == null || fromData == null || scale <= 0.0f) {
            return;
        }
        double cellW = board.width / 8.0;
        double cellH = board.height / 8.0;
        Color outgoingBase = new Color(235, 130, 55);
        Color incomingBase = new Color(60, 175, 200);
        Color diagonal = new Color(0, 0, 0, 45);
        for (int sq = 0; sq < 64; ++sq) {
            int file = sq & 7;
            int rank = sq >> 3;
            int drawRank = 7 - rank;
            double xd = board.x + file * cellW;
            double yd = board.y + drawRank * cellH;
            double xr = xd + cellW;
            double yb = yd + cellH;
            float vTo = Math.min(1.0f, Math.max(0.0f, toData[sq] / scale));
            float vFrom = Math.min(1.0f, Math.max(0.0f, fromData[sq] / scale));

            java.awt.geom.Path2D.Double upper = new java.awt.geom.Path2D.Double();
            upper.moveTo(xd, yd);
            upper.lineTo(xr, yd);
            upper.lineTo(xd, yb);
            upper.closePath();
            g.setColor(translucent(outgoingBase, vTo));
            g.fill(upper);

            java.awt.geom.Path2D.Double lower = new java.awt.geom.Path2D.Double();
            lower.moveTo(xr, yb);
            lower.lineTo(xr, yd);
            lower.lineTo(xd, yb);
            lower.closePath();
            g.setColor(translucent(incomingBase, vFrom));
            g.fill(lower);

            // Thin diagonal separator so the two halves stay readable even
            // when one is fully saturated and the other near zero.
            g.setColor(diagonal);
            g.drawLine((int) Math.round(xr), (int) Math.round(yd),
                    (int) Math.round(xd), (int) Math.round(yb));
        }
    }

    /**
     * Returns a translucent variant of {@code base} whose alpha scales with
     * {@code intensity} (0..1). A small floor keeps near-zero cells faintly
     * tinted so the colour-encoding is recognisable without being noisy.
     *
     * @param base base colour
     * @param intensity 0..1
     * @return translucent colour
     */
    private static Color translucent(Color base, float intensity) {
        float i = Math.max(0.0f, Math.min(1.0f, intensity));
        int alpha = Math.round(20 + 200 * i);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }

    /**
     * Adds per-square hover regions showing both attention directions.
     *
     * @param board mini-board rectangle
     * @param toData selected → this
     * @param fromData this → selected
     */
    private void addTriangleTooltips(Rectangle board, float[] toData, float[] fromData) {
        int cellW = board.width / 8;
        int cellH = board.height / 8;
        String selectedLabel = WorkbenchTensorViz.squareLabel(selectedSquare);
        for (int sq = 0; sq < 64; ++sq) {
            int file = sq & 7;
            int rank = sq >> 3;
            int drawRank = 7 - rank;
            Rectangle cell = new Rectangle(board.x + file * cellW, board.y + drawRank * cellH,
                    cellW, cellH);
            hitRegions.add(cell,
                    WorkbenchTensorViz.squareLabel(sq),
                    selectedLabel + " → this : " + String.format("%.4f", toData[sq])
                            + "   ·   this → " + selectedLabel + " : "
                            + String.format("%.4f", fromData[sq]),
                    "outgoing " + String.format("%.4f", toData[sq])
                            + " · incoming " + String.format("%.4f", fromData[sq]));
        }
    }

    /**
     * Paints the per-head/per-block readout.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintHeadReadout(Graphics2D g, Rectangle r) {
        float[] heads = snapshot.data("bt4.block" + selectedBlock + ".attention.heads");
        float headMag = 0.0f;
        float maxEntry = 0.0f;
        int argmaxFrom = 0;
        int argmaxTo = 0;
        if (heads != null) {
            int off = selectedHead * 64 * 64;
            for (int i = 0; i < 64 * 64; ++i) {
                float v = heads[off + i];
                headMag += v;
                if (v > maxEntry) {
                    maxEntry = v;
                    argmaxFrom = i >> 6;
                    argmaxTo = i & 63;
                }
            }
            headMag /= 64.0f * 64.0f;
        }
        g.setColor(WorkbenchTheme.PANEL_SOLID);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(WorkbenchTheme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(11, Font.BOLD));
        g.drawString(String.format("block %d   |   head %d/%d   |   mean attention %.4f",
                selectedBlock + 1, selectedHead + 1, HEADS, headMag), r.x + 8, r.y + 16);
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
        g.drawString(String.format("hottest pair: %s -> %s (%.3f)",
                WorkbenchTensorViz.squareLabel(argmaxFrom),
                WorkbenchTensorViz.squareLabel(argmaxTo),
                maxEntry), r.x + 8, r.y + 32);
    }

    /**
     * Returns a synthetic "attention focus" scalar for a block.
     *
     * @param block block index
     * @return mean attention magnitude
     */
    private float attentionFocus(int block) {
        float[] heads = snapshot.data("bt4.block" + block + ".attention.heads");
        if (heads == null) {
            return 0.0f;
        }
        double sum = 0.0;
        for (float v : heads) {
            sum += v;
        }
        return (float) (sum / Math.max(1, heads.length));
    }
}
