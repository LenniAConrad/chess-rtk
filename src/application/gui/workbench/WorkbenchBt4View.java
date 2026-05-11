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
     * Whether the selected-square overlay shows "attends to" (true) or
     * "attended by" (false). Toggles when the same square is clicked twice.
     */
    private boolean attendsToMode = true;

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
            if (r != null && r.dataKey != null && snapshot != null) {
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
            int cols = 8;
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
            if (sq == selectedSquare) {
                attendsToMode = !attendsToMode;
            } else {
                selectedSquare = sq;
                attendsToMode = true;
            }
            repaint();
            return;
        }
        WorkbenchHitRegions.Region r = hitRegions.hitTest(x, y);
        if (r != null && r.dataKey != null && snapshot != null) {
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
            WorkbenchTensorViz.drawSquareOverlay(g, board, energy, 0.0f, false);
            addBoardSquareTooltips(board, energy, "Mean attention received");
        }
        drawTopPolicyArrows(g, board);
        WorkbenchTensorViz.drawBoardCoordinates(g, board);
    }

    /**
     * Draws curved arrows on the board for the top-K policy moves at the
     * current position. Stroke thickness scales with the policy probability.
     *
     * @param g graphics
     * @param board mini-board rectangle
     */
    private void drawTopPolicyArrows(java.awt.Graphics2D g, Rectangle board) {
        if (fen == null || fen.isBlank()) {
            return;
        }
        float[] policy = snapshot.data("bt4.policy.logits");
        if (policy == null) {
            return;
        }
        float[] tx = snapshot.data("bt4.input.transform");
        int transform = tx == null ? 0 : Math.round(tx[0]);
        chess.core.Position position;
        try {
            position = new chess.core.Position(fen);
        } catch (RuntimeException ex) {
            return;
        }
        java.util.List<chess.nn.lc0.bt4.PolicyEncoder.ScoredMove> top =
                chess.nn.lc0.bt4.PolicyEncoder.topLegalMoves(position, policy, transform, 4);
        if (top.isEmpty()) {
            return;
        }
        java.awt.Stroke previousStroke = g.getStroke();
        for (int i = 0; i < top.size(); i++) {
            var sm = top.get(i);
            int from = chess.core.Move.getFromIndex(sm.move()) & 0xFF;
            int to = chess.core.Move.getToIndex(sm.move()) & 0xFF;
            drawMoveArrow(g, board, from, to, sm.probability(), i == 0);
        }
        g.setStroke(previousStroke);
    }

    /**
     * Draws a curved move arrow between two squares on the mini board.
     *
     * @param g graphics
     * @param board mini-board rectangle
     * @param fromSquare Field-encoded from square
     * @param toSquare Field-encoded to square
     * @param probability softmax probability over legal moves
     * @param isTop true to render the best move with a brighter color
     */
    private void drawMoveArrow(java.awt.Graphics2D g, Rectangle board,
            int fromSquare, int toSquare, float probability, boolean isTop) {
        double cellW = board.width / 8.0;
        double cellH = board.height / 8.0;
        int fFile = chess.core.Field.getX((byte) fromSquare);
        int fRank = chess.core.Field.getY((byte) fromSquare);
        int tFile = chess.core.Field.getX((byte) toSquare);
        int tRank = chess.core.Field.getY((byte) toSquare);
        double fx = board.x + (fFile + 0.5) * cellW;
        double fy = board.y + (7 - fRank + 0.5) * cellH;
        double tx = board.x + (tFile + 0.5) * cellW;
        double ty = board.y + (7 - tRank + 0.5) * cellH;
        double dx = tx - fx;
        double dy = ty - fy;
        double len = Math.sqrt(dx * dx + dy * dy);
        double offset = Math.min(28.0, len * 0.18);
        double cx = (fx + tx) / 2.0 + (-dy / Math.max(1.0, len)) * offset;
        double cy = (fy + ty) / 2.0 + (dx / Math.max(1.0, len)) * offset;
        java.awt.geom.QuadCurve2D.Double curve = new java.awt.geom.QuadCurve2D.Double(
                fx, fy, cx, cy, tx, ty);
        int alpha = Math.min(255, 110 + Math.round(145 * Math.min(1.0f, probability * 1.4f)));
        java.awt.Color base = isTop ? new java.awt.Color(70, 200, 80, alpha)
                : new java.awt.Color(245, 200, 80, alpha);
        g.setColor(base);
        float stroke = isTop ? 3.0f : 2.0f + Math.max(0.0f, probability * 2.5f);
        g.setStroke(new java.awt.BasicStroke(stroke,
                java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.draw(curve);
        double angle = Math.atan2(ty - cy, tx - cx);
        double a1 = angle + Math.PI * 0.85;
        double a2 = angle - Math.PI * 0.85;
        double ah = 7.0 + (isTop ? 3.0 : 1.0);
        int[] xs = { (int) tx, (int) (tx + ah * Math.cos(a1)), (int) (tx + ah * Math.cos(a2)) };
        int[] ys = { (int) ty, (int) (ty + ah * Math.sin(a1)), (int) (ty + ah * Math.sin(a2)) };
        g.fillPolygon(xs, ys, 3);
    }

    /**
     * Draws curved arcs from the selected (query) square to its top-K most
     * attended-to squares. Arc thickness and opacity scale with the attention
     * weight; the arcs sit on top of the colored square overlay.
     *
     * @param g graphics
     * @param board mini-board rectangle
     * @param querySquare 0..63 selected square
     * @param attention 64 attention values from query to each square
     */
    private void drawAttentionArcs(java.awt.Graphics2D g, Rectangle board,
            int querySquare, float[] attention) {
        if (attention == null || attention.length < 64) {
            return;
        }
        // Rank top destinations by attention weight (excluding self).
        Integer[] order = new Integer[64];
        for (int i = 0; i < 64; ++i) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Float.compare(attention[b], attention[a]));
        float maxW = 0.0f;
        for (float v : attention) {
            maxW = Math.max(maxW, v);
        }
        if (maxW <= 0.0f) {
            return;
        }
        double cellW = board.width / 8.0;
        double cellH = board.height / 8.0;
        int qFile = querySquare & 7;
        int qRank = querySquare >> 3;
        int qDrawRank = 7 - qRank;
        double qx = board.x + (qFile + 0.5) * cellW;
        double qy = board.y + (qDrawRank + 0.5) * cellH;
        int shown = 0;
        java.awt.Stroke previousStroke = g.getStroke();
        for (int idx = 0; idx < 64 && shown < 6; ++idx) {
            int sq = order[idx];
            if (sq == querySquare) {
                continue;
            }
            float w = attention[sq];
            if (w <= 1e-4f) {
                break;
            }
            float ratio = w / maxW;
            if (ratio < 0.18f) {
                break;
            }
            int sFile = sq & 7;
            int sRank = sq >> 3;
            int sDrawRank = 7 - sRank;
            double tx = board.x + (sFile + 0.5) * cellW;
            double ty = board.y + (sDrawRank + 0.5) * cellH;
            double mx = (qx + tx) / 2.0;
            double my = (qy + ty) / 2.0;
            // Curve control point: offset perpendicular to the line for an arc.
            double dx = tx - qx;
            double dy = ty - qy;
            double len = Math.sqrt(dx * dx + dy * dy);
            double offset = Math.min(40.0, len * 0.25);
            double cx = mx + (-dy / Math.max(1.0, len)) * offset;
            double cy = my + (dx / Math.max(1.0, len)) * offset;
            java.awt.geom.QuadCurve2D.Double curve = new java.awt.geom.QuadCurve2D.Double(
                    qx, qy, cx, cy, tx, ty);
            int alpha = (int) Math.min(255, 80 + 175 * ratio);
            Color c = new Color(WorkbenchTheme.ACCENT.getRed(),
                    WorkbenchTheme.ACCENT.getGreen(),
                    WorkbenchTheme.ACCENT.getBlue(), alpha);
            g.setColor(c);
            g.setStroke(new java.awt.BasicStroke(1.0f + ratio * 3.0f,
                    java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g.draw(curve);
            // Arrowhead at target end.
            double angle = Math.atan2(ty - cy, tx - cx);
            double a1 = angle + Math.PI * 0.85;
            double a2 = angle - Math.PI * 0.85;
            double ah = 6.0 + ratio * 4.0;
            int[] xs = { (int) tx,
                    (int) (tx + ah * Math.cos(a1)),
                    (int) (tx + ah * Math.cos(a2)) };
            int[] ys = { (int) ty,
                    (int) (ty + ah * Math.sin(a1)),
                    (int) (ty + ah * Math.sin(a2)) };
            g.fillPolygon(xs, ys, 3);
            ++shown;
        }
        g.setStroke(previousStroke);
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
        int gridW = Math.min(280, body.width / 3);
        int matrixW = Math.min(320, (body.width - gridW - 24));
        Rectangle gridR = new Rectangle(body.x, columnTop, gridW, Math.min(220, columnH));
        Rectangle matrixR = new Rectangle(body.x + gridW + 12, columnTop, matrixW,
                Math.min(matrixW + 16, columnH));
        Rectangle boardR = new Rectangle(matrixR.x + matrixW + 12, columnTop,
                body.width - (matrixR.x + matrixW + 12 - body.x), columnH);

        paintHeadGrid(g, gridR);
        headGridBounds = gridR;
        paintAttentionMatrix(g, matrixR);
        paintBoardOverlay(g, boardR);
        boardBounds = boardR;

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
     * Paints the head grid (one cell per head, selectable).
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintHeadGrid(Graphics2D g, Rectangle r) {
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y - 18, r.width, 18),
                "heads", "mean attention magnitude per head");
        int cols = 8;
        int rows = HEADS / cols;
        int cellW = r.width / cols;
        int cellH = r.height / rows;
        float[] heads = snapshot.data("bt4.block" + selectedBlock + ".attention.heads");
        // Use "peakiness" (max attention per head) instead of mean, because each
        // softmax row sums to 1, so mean across all rows is always 1/tokens and
        // would render every head identically. Max highlights focused heads.
        float[] magnitudes = new float[HEADS];
        for (int h = 0; h < HEADS; ++h) {
            float max = 0.0f;
            if (heads != null) {
                int off = h * 64 * 64;
                int end = Math.min(off + 64 * 64, heads.length);
                for (int i = off; i < end; ++i) {
                    if (heads[i] > max) {
                        max = heads[i];
                    }
                }
            }
            magnitudes[h] = max;
        }
        float maxMag = 0.0f;
        float minMag = Float.POSITIVE_INFINITY;
        for (float v : magnitudes) {
            maxMag = Math.max(maxMag, v);
            minMag = Math.min(minMag, v);
        }
        if (maxMag <= 0.0f) {
            maxMag = 1.0f;
            minMag = 0.0f;
        }
        float magRange = Math.max(1e-6f, maxMag - minMag);
        for (int h = 0; h < HEADS; ++h) {
            int col = h % cols;
            int row = h / cols;
            int x = r.x + col * cellW;
            int y = r.y + row * cellH;
            Color fill = WorkbenchTensorViz.lerp(WorkbenchTensorViz.NEUTRAL,
                    WorkbenchTensorViz.POLICY, (magnitudes[h] - minMag) / magRange);
            g.setColor(fill);
            g.fillRect(x + 1, y + 1, cellW - 2, cellH - 2);
            if (h == selectedHead) {
                g.setColor(WorkbenchTheme.ACCENT);
                g.drawRect(x, y, cellW - 1, cellH - 1);
                g.drawRect(x + 1, y + 1, cellW - 3, cellH - 3);
            } else {
                g.setColor(WorkbenchTheme.LINE);
                g.drawRect(x, y, cellW - 1, cellH - 1);
            }
            g.setColor(WorkbenchTheme.TEXT);
            g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
            g.drawString("h" + (h + 1), x + 4, y + 12);
            hitRegions.add(new Rectangle(x, y, cellW, cellH),
                    "Head " + (h + 1),
                    "Click to load this head's 64x64 attention matrix.",
                    String.format("peak attention %.4f", magnitudes[h]));
        }
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
        WorkbenchTensorViz.drawHeatmap(g, map, slice, 64, 64, 0.0f, false);
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
     * Paints the on-board overlay panel.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintBoardOverlay(Graphics2D g, Rectangle r) {
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y - 18, r.width, 18),
                attendsToMode ? "attends to" : "attended by",
                "click a square; click again to flip direction");
        int size = Math.min(r.width - 20, r.height - 60);
        Rectangle board = new Rectangle(r.x + (r.width - size) / 2, r.y + 8, size - 16, size - 16);
        WorkbenchTensorViz.drawMiniBoard(g, board);
        WorkbenchTensorViz.drawPositionPieces(g, board, fen);
        boardBounds = board;
        if (selectedSquare >= 0) {
            float[] heads = snapshot.data("bt4.block" + selectedBlock + ".attention.heads");
            if (heads != null) {
                int off = selectedHead * 64 * 64;
                float[] overlay = new float[64];
                if (attendsToMode) {
                    for (int to = 0; to < 64; ++to) {
                        overlay[to] = heads[off + selectedSquare * 64 + to];
                    }
                } else {
                    for (int from = 0; from < 64; ++from) {
                        overlay[from] = heads[off + from * 64 + selectedSquare];
                    }
                }
                WorkbenchTensorViz.drawSquareOverlay(g, board, overlay, 0.0f, false);
                drawAttentionArcs(g, board, selectedSquare, overlay);
                addBoardSquareTooltips(board, overlay,
                        attendsToMode
                                ? "attention from " + WorkbenchTensorViz.squareLabel(selectedSquare) + " to this square"
                                : "attention to " + WorkbenchTensorViz.squareLabel(selectedSquare) + " from this square");
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
                ? "no square selected"
                : "selected: " + WorkbenchTensorViz.squareLabel(selectedSquare);
        g.drawString(hint, r.x + 8, r.y + r.height - 4);
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
