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
 * Workbench panel that visualises an LC0 BT4 transformer forward pass.
 *
 * <p>Abstract mode shows the WDL bars, the per-block activity strip, and the
 * token-energy heatmap overlaid on a small board. Detailed mode lets the user
 * pick a block (1..15) and a head (1..32), shows the chosen head's 64x64
 * attention map, and renders an on-board overlay that highlights the
 * attended squares when a board square is clicked.</p>
 *
 * <p>Shared scaffolding lives in {@link NetworkView}; this class only
 * owns the BT4-specific drawing. Atlas mode is a readable block/head attention
 * fingerprint; raw mode remains the dense matrix-heavy diagnostic view.</p>
 */
public final class Bt4View extends NetworkView {

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
     * Cached hit-box for the raw-atlas grid (rows = blocks, cols = heads).
     */
    private Rectangle rawAtlasBounds = new Rectangle();

    /**
     * Creates the BT4 view.
     */
    public Bt4View() {
        super(760, 540);
        selectedBlock = BLOCKS - 1;
        selectedHead = 0;
    }

    /**
     * Paints the BT4 attention atlas.
     *
     * @param g graphics context
     * @param body atlas bounds
     */
    @Override
    protected void paintAtlas(Graphics2D g, Rectangle body) {
        TensorViz.drawSectionHeader(g,
    new Rectangle(body.x, body.y, body.width, 40),
                "BT4 attention atlas",
                "block/head fingerprint · selected-head board footprint · selected head's 64×64 attention matrix");
        int top = body.y + 50;
        int h = body.height - 50;
        int gap = 12;
        if (body.width < 860) {
            int part = Math.max(160, (h - 2 * gap) / 3);
            Rectangle fingerprint = new Rectangle(body.x, top, body.width, part);
            Rectangle boards = new Rectangle(body.x, fingerprint.y + fingerprint.height + gap,
                    body.width, part);
            Rectangle heads = new Rectangle(body.x, boards.y + boards.height + gap,
                    body.width, Math.max(120, body.y + body.height - (boards.y + boards.height + gap)));
            paintBt4HeadFingerprint(g, fingerprint);
            paintBt4AtlasBoards(g, boards);
            paintAttentionMatrix(g, heads);
            return;
        }
        int leftW = Math.max(500, Math.min((int) (body.width * 0.58), body.width - 380));
        Rectangle fingerprint = new Rectangle(body.x, top, leftW, h);
        int rightX = fingerprint.x + fingerprint.width + gap;
        int rightW = body.x + body.width - rightX;
        int boardH = Math.max(240, Math.min((int) (h * 0.52), 330));
        Rectangle boards = new Rectangle(rightX, top, rightW, boardH);
        Rectangle heads = new Rectangle(rightX, boards.y + boards.height + gap,
                rightW, Math.max(140, body.y + body.height - (boards.y + boards.height + gap)));
        paintBt4HeadFingerprint(g, fingerprint);
        paintBt4AtlasBoards(g, boards);
        paintAttentionMatrix(g, heads);
    }

    /**
     * Resets detailed-mode selection state and cached hit-boxes whenever the
     * view mode changes.
     */
    @Override
    protected void onViewModeChanged() {
        selectedSquare = -1;
        headGridBounds = new Rectangle();
        boardBounds = new Rectangle();
        blockStripBounds = new Rectangle();
    }

    /**
     * Handles a mouse click.
     *
     * @param x x
     * @param y y
     */
    @Override
    protected void onClick(int x, int y) {
        if (isRaw() || isAtlas()) {
            // In raw/atlas view, click any thumbnail to select that block+head.
            if (rawAtlasBounds.contains(x, y)) {
                int relX = x - rawAtlasBounds.x;
                int relY = y - rawAtlasBounds.y;
                int cellW = Math.max(1, rawAtlasBounds.width / HEADS);
                int cellH = Math.max(1, rawAtlasBounds.height / BLOCKS);
                selectedHead = Math.max(0, Math.min(HEADS - 1, relX / cellW));
                selectedBlock = Math.max(0, Math.min(BLOCKS - 1, relY / cellH));
                repaint();
                return;
            }
            HitRegions.Region region = hitRegions.hitTest(x, y);
            if (region != null && inspector != null) {
                inspector.inspect(region, snapshot);
            }
            return;
        }
        if (!isDetailed()) {
            HitRegions.Region r = hitRegions.hitTest(x, y);
            if (r == null) {
                return;
            }
            if (inspector != null) {
                inspector.inspect(r, snapshot);
            } else if (r.dataKey != null && snapshot != null) {
                InspectorDialog.shared(this).inspect(r, snapshot);
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
        HitRegions.Region r = hitRegions.hitTest(x, y);
        if (r == null) {
            return;
        }
        if (inspector != null) {
            inspector.inspect(r, snapshot);
        } else if (r.dataKey != null && snapshot != null) {
            InspectorDialog.shared(this).inspect(r, snapshot);
        }
    }

    /**
     * Paints the architecture-diagram schematic for the BT4 transformer.
     *
     * @param g graphics
     * @param body body rectangle
     */
    @Override
    protected void paintDiagram(Graphics2D g, Rectangle body) {
        int headerH = 40;
        TensorViz.drawSectionHeader(g,
    new Rectangle(body.x, body.y, body.width, headerH),
                "LC0 BT4 architecture",
                "input → embedding → " + BLOCKS + " transformer blocks ("
                        + HEADS + " heads each) → policy / value heads");
        String[] titles = {
                "input encoding",
                "embedding",
                BLOCKS + " transformer blocks",
                "policy head",
                "value head"
        };
        String[] subs = {
                "112 input planes (8×8)",
                "linear projection",
                HEADS + " self-attn heads × MLP",
                "logits over moves",
                "WDL"
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
            g.fillRoundRect(x, y, boxW, boxH, 14, 14);
            g.setColor(Theme.LINE);
            g.drawRoundRect(x, y, boxW, boxH, 14, 14);
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
                g.setColor(TensorViz.TRUNK);
                g.drawLine(ax1 + 2, ay, ax2 - 6, ay);
                int[] xs = { ax2 - 6, ax2 - 12, ax2 - 12 };
                int[] ys = { ay, ay - 5, ay + 5 };
                g.fillPolygon(xs, ys, 3);
            }
            hitRegions.add(new Rectangle(x, y, boxW, boxH),
                    "Layer · " + titles[i], subs[i], "");
        }
    }

    /**
     * Paints the empty placeholder.
     *
     * @param g graphics context
     * @param bounds placeholder bounds
     */
    @Override
    protected void paintEmpty(Graphics2D g, Rectangle bounds) {
        g.setColor(Theme.BG);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(13, Font.PLAIN));
        g.drawString("Loading BT4 snapshot...", PAD, 32);
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
        g.drawString("LC0 BT4 (transformer) activations", PAD, 22);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(11, Font.PLAIN));
        g.drawString(BLOCKS + " blocks · " + HEADS + " heads · 64 tokens (squares) · 768-d embedding", PAD, 40);
    }

    /**
     * Paints the abstract overview.
     *
     * @param g graphics
     * @param body body rectangle
     */
    @Override
    protected void paintAbstract(Graphics2D g, Rectangle body) {
        int leftW = (int) (body.width * 0.58);
        Rectangle left = new Rectangle(body.x, body.y, leftW, body.height);
        Rectangle right = new Rectangle(body.x + leftW + 10, body.y, body.width - leftW - 10, body.height);
        paintAbstractPipeline(g, left);
        paintTokenEnergyBoard(g, right);
    }

    /**
     * Paints the raw view: a dense atlas of every block × head attention
     * pattern. The screen is split into two stacked grids — the upper grid
     * shows each head's full 64×64 attention matrix (a "fingerprint" of
     * where attention flows), and the lower grid shows the same head's
     * 8×8 mean attention-received pattern projected onto the board.
     *
     * Each block gets one row (1..15); each head gets one column (1..32).
     * Per-block colour normalisation keeps earlier blocks readable even
     * when later blocks have much sharper peaks. A square-root gamma is
     * applied so low attention values stay visible without saturating
     * the high values.
     *
     * @param g graphics
     * @param body body rectangle
     */
    @Override
    protected void paintRaw(Graphics2D g, Rectangle body) {
        paintBt4AttentionGrid(g, body,
                "raw attention atlas — 480 heads at once",
                "top grid: 64×64 attention matrices · bottom grid: 8×8 mean attention-received on the board · click any cell to focus");
    }

    /**
     * Paints the complete block x head attention grid.
     *
     * @param g graphics
     * @param body body rectangle
     * @param title section title
     * @param subtitle section subtitle
     */
    private void paintBt4AttentionGrid(Graphics2D g, Rectangle body, String title, String subtitle) {
        int headerH = 38;
        TensorViz.drawSectionHeader(g,
    new Rectangle(body.x, body.y, body.width, headerH),
                title,
                subtitle);
        int rowLabelW = 28;
        int colLabelH = 16;
        int gridTop = body.y + headerH + 4 + colLabelH;
        int gridLeft = body.x + rowLabelW;
        int gridW = body.width - rowLabelW - 4;
        int gridH = body.height - headerH - 4 - colLabelH - 4;
        int cellW = Math.max(2, gridW / HEADS);
        // Each block row is split horizontally into two stacked sub-cells:
        // the top sub-cell holds the 64×64 matrix, the bottom holds the 8×8
        // board-shaped energy pattern.
        int cellH = Math.max(4, gridH / BLOCKS);
        int subPad = 1;
        int matrixH = (int) Math.round(cellH * 0.62);
        int boardH = cellH - matrixH - 2;

        // Compute per-block max for both matrix values and 8×8 energy so
        // each block row uses its full colour range, and apply a gamma
        // (sqrt) so low values stay visible.
        float[][][] matrix = new float[BLOCKS][HEADS][];
        float[][][] energy = new float[BLOCKS][HEADS][64];
        float[] matrixMax = new float[BLOCKS];
        float[] energyMax = new float[BLOCKS];
        for (int b = 0; b < BLOCKS; ++b) {
            float[] heads = snapshot.data("bt4.block" + b + ".attention.heads");
            if (heads == null) {
                matrixMax[b] = 1.0f;
                energyMax[b] = 1.0f;
                continue;
            }
            for (int h = 0; h < HEADS; ++h) {
                int off = h * 64 * 64;
                if (off + 64 * 64 > heads.length) {
                    continue;
                }
                float[] mat = new float[64 * 64];
                System.arraycopy(heads, off, mat, 0, 64 * 64);
                matrix[b][h] = mat;
                for (int from = 0; from < 64; ++from) {
                    for (int to = 0; to < 64; ++to) {
                        energy[b][h][to] += mat[from * 64 + to];
                    }
                }
                for (int s = 0; s < 64; ++s) {
                    energy[b][h][s] /= 64.0f;
                }
                for (float v : mat) {
                    if (v > matrixMax[b]) {
                        matrixMax[b] = v;
                    }
                }
                for (float v : energy[b][h]) {
                    if (v > energyMax[b]) {
                        energyMax[b] = v;
                    }
                }
            }
            if (matrixMax[b] <= 0.0f) {
                matrixMax[b] = 1.0f;
            }
            if (energyMax[b] <= 0.0f) {
                energyMax[b] = 1.0f;
            }
            matrixMax[b] = scaleFor("rawAtlas:matrix:b" + b, matrixMax[b]);
            energyMax[b] = scaleFor("rawAtlas:energy:b" + b, energyMax[b]);
        }

        // Column (head) labels — every 4th to avoid clutter.
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(9, Font.PLAIN));
        FontMetrics fm = g.getFontMetrics();
        for (int h = 0; h < HEADS; h += 2) {
            String lbl = "h" + (h + 1);
            int lx = gridLeft + h * cellW + (cellW - fm.stringWidth(lbl)) / 2;
            g.drawString(lbl, lx, body.y + headerH + 4 + colLabelH - 4);
        }

        for (int b = 0; b < BLOCKS; ++b) {
            int y = gridTop + b * cellH;
            String rowLabel = "B" + (b + 1);
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(10, Font.BOLD));
            g.drawString(rowLabel, body.x + 4, y + cellH / 2 + 4);
            for (int h = 0; h < HEADS; ++h) {
                int x = gridLeft + h * cellW;
                Rectangle matrixCell = new Rectangle(x + subPad, y + subPad,
                        Math.max(1, cellW - 2 * subPad),
                        Math.max(1, matrixH - 2 * subPad));
                Rectangle boardCell = new Rectangle(x + subPad, y + matrixH + 1,
                        Math.max(1, cellW - 2 * subPad),
                        Math.max(1, boardH - 2));
                if (matrix[b][h] != null) {
                    drawGammaHeatmap(g, matrixCell, matrix[b][h], 64, 64, matrixMax[b]);
                } else {
                    TensorViz.drawEmpty(g, matrixCell);
                }
                drawGammaHeatmap(g, boardCell, energy[b][h], 8, 8, energyMax[b]);
                boolean selected = b == selectedBlock && h == selectedHead;
                Color border = selected ? TensorViz.FOCUS : Theme.LINE;
                g.setColor(border);
                g.drawRect(x, y, cellW - 1, cellH - 1);
                if (selected) {
                    g.drawRect(x + 1, y + 1, cellW - 3, cellH - 3);
                }
                float peak = 0.0f;
                for (float v : energy[b][h]) {
                    if (v > peak) {
                        peak = v;
                    }
                }
                hitRegions.add(new Rectangle(x, y, cellW, cellH),
                        "B" + (b + 1) + " · h" + (h + 1),
                        "Click to select this block + head; switch off Raw view to inspect in detail.",
                        String.format("max attn %.4f · peak square %.4f", matrixMax[b], peak));
            }
        }
        rawAtlasBounds = new Rectangle(gridLeft, gridTop, HEADS * cellW, BLOCKS * cellH);
    }

    /**
     * Paints the readable BT4 atlas grid: one cell per block/head, where
     * brightness means "how concentrated is this head's received attention".
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintBt4HeadFingerprint(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 38),
                "block × head fingerprint",
                "bright cells are focused heads; pale cells are diffuse or low-impact for this position");
        int rowLabelW = 38;
        int colLabelH = 18;
        int gridX = r.x + rowLabelW;
        int gridY = r.y + 38 + colLabelH + 4;
        int gridW = r.width - rowLabelW - 4;
        int gridH = r.y + r.height - gridY - 4;
        int cellW = Math.max(4, gridW / HEADS);
        int cellH = Math.max(8, gridH / BLOCKS);
        float[][] scores = new float[BLOCKS][HEADS];
        float scale = 0.0f;
        int topBlock = 0;
        int topHead = 0;
        int quietHeads = 0;
        for (int b = 0; b < BLOCKS; b++) {
            for (int h = 0; h < HEADS; h++) {
                float[] energy = headReceivedEnergy(b, h);
                scores[b][h] = headFocusScore(energy);
                if (scores[b][h] > scale) {
                    topBlock = b;
                    topHead = h;
                }
                scale = Math.max(scale, scores[b][h]);
            }
        }
        if (scale <= 0.0f) {
            scale = 1.0f;
        }
        float rawScale = scale;
        float quietCutoff = rawScale * 0.16f;
        float importantCutoff = rawScale * 0.78f;
        for (int b = 0; b < BLOCKS; b++) {
            for (int h = 0; h < HEADS; h++) {
                if (scores[b][h] <= quietCutoff) {
                    quietHeads++;
                }
            }
        }
        scale = scaleFor("bt4Atlas:headFingerprint", scale);
        if (r.width >= 760) {
            TensorViz.drawInfoChip(g, new Rectangle(r.x + r.width - 322, r.y + 7, 154, 24),
                    "important", "B" + (topBlock + 1) + " h" + (topHead + 1),
                    TensorViz.POLICY);
            TensorViz.drawInfoChip(g, new Rectangle(r.x + r.width - 160, r.y + 7, 150, 24),
                    "not much", Math.round(100.0f * quietHeads / (BLOCKS * HEADS)) + "% quiet",
                    Theme.MUTED);
        }

        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(9, Font.PLAIN));
        FontMetrics fm = g.getFontMetrics();
        for (int h = 0; h < HEADS; h += 4) {
            String label = "h" + (h + 1);
            int lx = gridX + h * cellW + Math.max(0, (cellW - fm.stringWidth(label)) / 2);
            g.drawString(label, lx, gridY - 5);
        }

        for (int b = 0; b < BLOCKS; b++) {
            int y = gridY + b * cellH;
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(10, Font.BOLD));
            g.drawString("B" + (b + 1), r.x + 5, y + Math.max(10, cellH / 2 + 4));
            String key = "bt4.block" + b + ".attention.heads";
            for (int h = 0; h < HEADS; h++) {
                int x = gridX + h * cellW;
                float v = (float) Math.sqrt(Math.min(1.0f, Math.max(0.0f, scores[b][h] / scale)));
                g.setColor(TensorViz.lerp(Theme.PANEL_SOLID,
                        TensorViz.POLICY, 0.12f + 0.82f * v));
                g.fillRect(x, y, Math.max(1, cellW - 1), Math.max(1, cellH - 1));
                if (scores[b][h] >= importantCutoff && cellW >= 4 && cellH >= 8) {
                    g.setColor(Theme.withAlpha(Theme.TEXT, 120));
                    g.drawRect(x, y, cellW - 2, cellH - 2);
                }
                boolean selected = b == selectedBlock && h == selectedHead;
                if (selected) {
                    g.setColor(TensorViz.FOCUS);
                    g.drawRect(x, y, cellW - 1, cellH - 1);
                    g.drawRect(x + 1, y + 1, cellW - 3, cellH - 3);
                }
                Rectangle cell = new Rectangle(x, y, cellW, cellH);
                if (snapshot.has(key)) {
                    hitRegions.addInspectable(cell,
                            "Block " + (b + 1) + " · head " + (h + 1),
                            "Click to select this attention head for the board footprint.",
                            String.format("peak received %.4f", scores[b][h]),
                            key, h * 64 * 64, 64 * 64, 64, "64x64");
                } else {
                    hitRegions.add(cell,
                            "Block " + (b + 1) + " · head " + (h + 1),
                            "Click to select this attention head for the board footprint.",
                            String.format("peak received %.4f", scores[b][h]));
                }
            }
        }
        rawAtlasBounds = new Rectangle(gridX, gridY, HEADS * cellW, BLOCKS * cellH);
        g.setColor(Theme.LINE);
        g.drawRect(rawAtlasBounds.x, rawAtlasBounds.y,
                Math.min(gridW, rawAtlasBounds.width), Math.min(gridH, rawAtlasBounds.height));
    }

    /**
     * Paints board projections for the selected attention head and the model's
     * token-energy summary.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintBt4AtlasBoards(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 38),
                "board footprint", "selected head vs final token-energy summary");
        Rectangle content = new Rectangle(r.x + 4, r.y + 48, r.width - 8, r.height - 52);
        float[] selectedEnergy = headReceivedEnergy(selectedBlock, selectedHead);
        float[] tokenEnergy = snapshot.data("bt4.token.energy");
        int gap = 10;
        int boardSide = Math.min((content.width - gap) / 2, Math.max(64, content.height - 22));
        if (boardSide < 72) {
            boardSide = Math.min(content.width, Math.max(48, content.height - 22));
            drawBt4AtlasBoard(g, new Rectangle(content.x, content.y + 16, boardSide, boardSide),
                    "B" + (selectedBlock + 1) + " h" + (selectedHead + 1),
                    selectedEnergy, "Selected head · mean attention received");
            return;
        }
        Rectangle selectedBoard = new Rectangle(content.x, content.y + 16, boardSide, boardSide);
        Rectangle tokenBoard = new Rectangle(content.x + boardSide + gap, content.y + 16,
                boardSide, boardSide);
        drawBt4AtlasBoard(g, selectedBoard,
                "B" + (selectedBlock + 1) + " h" + (selectedHead + 1),
                selectedEnergy, "Selected head · mean attention received");
        drawBt4AtlasBoard(g, tokenBoard, "token energy", tokenEnergy,
                "Final token energy");
    }

    /**
     * Draws one board tile for the BT4 atlas.
     *
     * @param g graphics
     * @param board board rectangle
     * @param title title
     * @param values per-square values
     * @param caption tooltip caption
     */
    private void drawBt4AtlasBoard(Graphics2D g, Rectangle board, String title,
            float[] values, String caption) {
        int focusSquare = strongestSquare(values);
        String focus = focusSquare >= 0 ? " · focus " + TensorViz.squareLabel(focusSquare) : "";
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(10, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(Ui.elide(title + focus, fm, board.width + 10), board.x, board.y - 4);
        TensorViz.drawMiniBoard(g, board);
        TensorViz.drawPositionPieces(g, board, fen);
        if (values != null) {
            float scale = scaleFor("bt4Atlas:board:" + title, maxAbs(values));
            TensorViz.drawSquareOverlay(g, board, values, scale, false);
            TensorViz.drawBoardSquareRing(g, board, focusSquare, TensorViz.FOCUS);
            addBoardSquareTooltips(board, values, caption);
        }
        TensorViz.drawBoardCoordinates(g, board);
    }

    /**
     * Returns the mean attention received by each board square for one head.
     *
     * @param block block index
     * @param head head index
     * @return 64 board-square values
     */
    private float[] headReceivedEnergy(int block, int head) {
        float[] out = new float[64];
        if (block < 0 || block >= BLOCKS || head < 0 || head >= HEADS) {
            return out;
        }
        float[] heads = snapshot.data("bt4.block" + block + ".attention.heads");
        if (heads == null) {
            return out;
        }
        int off = head * 64 * 64;
        if (off + 64 * 64 > heads.length) {
            return out;
        }
        for (int from = 0; from < 64; from++) {
            for (int to = 0; to < 64; to++) {
                out[to] += heads[off + from * 64 + to];
            }
        }
        for (int sq = 0; sq < 64; sq++) {
            out[sq] /= 64.0f;
        }
        return out;
    }

    /**
     * Returns the focus score for a head-energy board.
     *
     * @param energy 64-square attention received values
     * @return focus score
     */
    private static float headFocusScore(float[] energy) {
        float peak = 0.0f;
        if (energy != null) {
            for (float v : energy) {
                peak = Math.max(peak, v);
            }
        }
        return peak;
    }

    /**
     * Returns the top attention heads by focus score.
     *
     * @param limit maximum count
     * @return sorted picks
     */
    private List<HeadPick> topAttentionHeads(int limit) {
        List<HeadPick> picks = new ArrayList<>();
        for (int b = 0; b < BLOCKS; b++) {
            for (int h = 0; h < HEADS; h++) {
                float[] energy = headReceivedEnergy(b, h);
                picks.add(new HeadPick(b, h, energy, headFocusScore(energy)));
            }
        }
        picks.sort((a, b) -> Float.compare(b.score, a.score));
        if (picks.size() > limit) {
            return new ArrayList<>(picks.subList(0, limit));
        }
        return picks;
    }

    /**
     * Draws a heatmap with a square-root gamma applied to each value so
     * low values stay visible. Same overall API as
     * {@link TensorViz#drawHeatmap} but tuned for the dense raw
     * atlas where most attention values are small.
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
        if (data == null || cols <= 0 || rows <= 0 || data.length < cols * rows
                || r.width <= 0 || r.height <= 0) {
            return;
        }
        // Blit one bitmap instead of cols x rows fills — the raw atlas packs
        // 480 attention heads, so the per-cell loop froze the whole tab.
        TensorViz.drawGammaHeatmap(g, r, data, cols, rows, scale, TensorViz.POLICY);
        g.setColor(Theme.withAlpha(Theme.TEXT, 36));
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
    }

    /**
     * Paints the abstract pipeline (block strip + summary cards).
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintAbstractPipeline(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
                "abstract flow", "input -> token embedding -> stacked transformer blocks -> heads");
        int top = r.y + 56;
        int cardW = (r.width - 32) / 3;
        int cardH = 70;
        Rectangle inR = new Rectangle(r.x + 8, top, cardW, cardH);
        Rectangle emR = new Rectangle(inR.x + cardW + 8, top, cardW, cardH);
        Rectangle outR = new Rectangle(emR.x + cardW + 8, top, cardW, cardH);
        TensorViz.drawElbowConnection(g, inR, emR, TensorViz.TRUNK, true);
        TensorViz.drawElbowConnection(g, emR, outR, TensorViz.TRUNK, true);
        TensorViz.drawAbstractBlock(g, inR, "input planes", "112x8x8", 1.0f,
                TensorViz.POSITIVE);
        TensorViz.drawAbstractBlock(g, emR, "token embedding", "64x768", 0.7f,
                TensorViz.TRUNK);
        TensorViz.drawAbstractBlock(g, outR, "final tokens", "64x768", 0.6f,
                TensorViz.VALUE);
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
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(10, Font.PLAIN));
        int bestAttn = strongestAttentionBlock();
        int bestFfn = strongestFfnBlock();
        g.drawString("important blocks: attention B" + (bestAttn + 1)
                + " · ffn B" + (bestFfn + 1)
                + "  (small bars are low-signal)",
                strip.x, strip.y - 4);

        int valueY = stripY + strip.height + 24;
        if (valueY + 96 <= r.y + r.height) {
            Rectangle valueR = new Rectangle(r.x + 8, valueY, r.width - 16, r.y + r.height - valueY - 4);
            TensorViz.drawWdlCard(g, valueR,
                    snapshot.data("bt4.value.wdl"), snapshot.data("bt4.value.scalar"));
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
        g.setColor(Theme.PANEL_SOLID);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(Theme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
        float maxAttn = 0.0f;
        float maxFfn = 0.0f;
        float[] attnFocus = new float[BLOCKS];
        float[] ffnEnergy = new float[BLOCKS];
        for (int b = 0; b < BLOCKS; ++b) {
            attnFocus[b] = attentionFocus(b);
            float[] ffn = snapshot.data("bt4.block" + b + ".ffn");
            float[] stats = TensorViz.summarize(ffn);
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
        int bestAttnBlock = 0;
        int bestFfnBlock = 0;
        for (int b = 1; b < BLOCKS; ++b) {
            if (attnFocus[b] > attnFocus[bestAttnBlock]) {
                bestAttnBlock = b;
            }
            if (ffnEnergy[b] > ffnEnergy[bestFfnBlock]) {
                bestFfnBlock = b;
            }
        }
        for (int b = 0; b < BLOCKS; ++b) {
            int x = r.x + b * (cellW + gap);
            int hAttn = Math.round(attnFocus[b] / maxAttn * (half - 2));
            int hFfn = Math.round(ffnEnergy[b] / maxFfn * (half - 2));
            g.setColor(TensorViz.POLICY);
            g.fillRect(x, r.y + (half - hAttn), cellW, hAttn);
            g.setColor(TensorViz.VALUE);
            g.fillRect(x, r.y + half + 1, cellW, hFfn);
            if (b == bestAttnBlock || b == bestFfnBlock) {
                g.setColor(b == bestAttnBlock ? TensorViz.POLICY : TensorViz.VALUE);
                g.drawRect(x, r.y + 1, Math.max(1, cellW - 1), r.height - 3);
                if (b == bestAttnBlock && b == bestFfnBlock) {
                    g.setColor(TensorViz.VALUE);
                    g.drawRect(x + 1, r.y + 2, Math.max(1, cellW - 3), r.height - 5);
                }
            }
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(9, Font.PLAIN));
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
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
                "token energy + top moves",
                "board tint = how much attention each square receives · bars = most likely moves");
        // Board fills the upper part; the top-move bars sit below it.
        int boardArea = Math.min(r.width, Math.max(160, (r.height - 50) * 3 / 5));
        Rectangle board = new Rectangle(r.x + Math.max(8, (r.width - boardArea) / 2),
                r.y + 50, boardArea - 16, boardArea - 16);
        TensorViz.drawMiniBoard(g, board);
        TensorViz.drawPositionPieces(g, board, fen);
        float[] energy = snapshot.data("bt4.token.energy");
        if (energy != null && energy.length >= 64) {
            float s = scaleFor("tokenEnergy", maxAbs(energy));
            TensorViz.drawSquareOverlay(g, board, energy, s, false);
            TensorViz.drawBoardSquareRing(g, board, strongestSquare(energy), TensorViz.FOCUS);
            addBoardSquareTooltips(board, energy, "Mean attention received");
        }
        TensorViz.drawBoardCoordinates(g, board);
        int barsTop = board.y + board.height + 30;
        if (barsTop + 40 <= r.y + r.height) {
            Rectangle barsR = new Rectangle(r.x + 8, barsTop,
                    r.width - 16, r.y + r.height - barsTop - 6);
            paintTopPolicy(g, barsR);
        }
    }

    /**
     * Paints the top-policy move bars beneath the token-energy board.
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
            labels[i] = chess.core.Move.toString(top.get(i).move()) + "  "
                    + String.format("%.1f%%", top.get(i).probability() * 100);
            values[i] = top.get(i).probability();
            scale = Math.max(scale, values[i]);
        }
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(10, Font.PLAIN));
        g.drawString("top moves (legal-softmax probability)", r.x, r.y - 4);
        TensorViz.drawMetricBars(g, r, labels, values, scale);
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
                    TensorViz.squareLabel(sq),
                    caption,
                    String.format("%+.4f", values[sq]));
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
    @Override
    protected void paintDetailed(Graphics2D g, Rectangle body) {
        TensorViz.drawSectionHeader(g, new Rectangle(body.x, body.y, body.width, 40),
                "per-head attention",
                "block 1..15 · head 1..32 · click a head, then click any board square");
        int top = body.y + 50;

        Rectangle blockBar = new Rectangle(body.x, top, body.width, 22);
        paintBlockSelector(g, blockBar);
        blockStripBounds = blockBar;

        int columnTop = blockBar.y + blockBar.height + 12;
        int columnH = body.height - (columnTop - body.y) - 8;
        int gridW = Math.min(560, Math.max(360, body.width / 3));
        int matrixW = Math.min(420, Math.max(320, (body.width - gridW - 24) / 3));
        Rectangle gridR = new Rectangle(body.x, columnTop, gridW,
                Math.min(columnH, gridW * 2));
        Rectangle matrixR = new Rectangle(body.x + gridW + 12, columnTop, matrixW,
                Math.min(matrixW + 32, columnH));
        Rectangle boardR = new Rectangle(matrixR.x + matrixW + 12, columnTop,
                body.width - (matrixR.x + matrixW + 12 - body.x), columnH);

        paintHeadGrid(g, gridR);
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
        g.setColor(Theme.PANEL_SOLID);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(Theme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
        int cellW = r.width / BLOCKS;
        g.setFont(Theme.font(11, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        for (int b = 0; b < BLOCKS; ++b) {
            int x = r.x + b * cellW;
            boolean sel = b == selectedBlock;
            g.setColor(sel ? TensorViz.FOCUS : Theme.ELEVATED_SOLID);
            g.fillRect(x + 1, r.y + 1, cellW - 2, r.height - 2);
            g.setColor(sel ? Theme.PRIMARY_BUTTON_TEXT : Theme.TEXT);
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
        int headerH = 38;
        Rectangle header = new Rectangle(r.x, r.y, r.width, headerH);
        TensorViz.drawSectionHeader(g, header, "heads of block " + (selectedBlock + 1),
                "8×8 attention-received per head · click one to focus");
        Rectangle grid = new Rectangle(r.x, r.y + headerH + 4, r.width, r.height - headerH - 4);
        headGridBounds = grid;
        int cols = 4;
        int rows = HEADS / cols;
        int cellW = grid.width / cols;
        int cellH = grid.height / rows;
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
            int x = grid.x + col * cellW;
            int y = grid.y + row * cellH;
            int padTop = 14;
            int hmW = cellW - 6;
            int hmH = cellH - padTop - 4;
            Rectangle heat = new Rectangle(x + 3, y + padTop, hmW, hmH);
            TensorViz.drawHeatmap(g, heat, perHeadEnergy[h], 8, 8, globalMax, false);
            if (selectedSquare >= 0) {
                drawHeadSquareMarker(g, heat, selectedSquare,
                        perHeadEnergy[h][selectedSquare], globalMax);
            }
            if (h == selectedHead) {
                g.setColor(TensorViz.FOCUS);
                g.drawRect(x, y, cellW - 1, cellH - 1);
                g.drawRect(x + 1, y + 1, cellW - 3, cellH - 3);
            } else {
                g.setColor(Theme.LINE);
                g.drawRect(x, y, cellW - 1, cellH - 1);
            }
            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(11, Font.BOLD));
            g.drawString("h" + (h + 1), x + 5, y + 12);
            float peak = 0.0f;
            for (float v : perHeadEnergy[h]) {
                if (v > peak) {
                    peak = v;
                }
            }
            String desc = selectedSquare >= 0
                    ? "Click to focus this head. " + TensorViz.squareLabel(selectedSquare)
                            + " attention-received = " + String.format("%.4f", perHeadEnergy[h][selectedSquare])
                    : "Click to focus this head's full 64×64 attention matrix.";
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
        Color edge = new Color(TensorViz.FOCUS.getRed(),
                TensorViz.FOCUS.getGreen(),
                TensorViz.FOCUS.getBlue(),
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
        int headerH = 38;
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, headerH),
                "head " + (selectedHead + 1) + " · full attention",
                "64×64 matrix · rows = from · cols = to");
        float[] heads = snapshot.data("bt4.block" + selectedBlock + ".attention.heads");
        int matrixTop = r.y + headerH + 4;
        int size = Math.min(r.width - 32, r.height - headerH - 16);
        Rectangle map = new Rectangle(r.x + 24, matrixTop, size, size);
        if (heads == null) {
            TensorViz.drawEmpty(g, map);
            return;
        }
        int off = selectedHead * 64 * 64;
        if (off + 64 * 64 > heads.length) {
            TensorViz.drawEmpty(g, map);
            return;
        }
        float[] slice = new float[64 * 64];
        System.arraycopy(heads, off, slice, 0, 64 * 64);
        float matrixScale = scaleFor(
                "attentionMatrix:b" + selectedBlock + "h" + selectedHead, maxAbs(slice));
        TensorViz.drawHeatmap(g, map, slice, 64, 64, matrixScale, false);
        hitRegions.addInspectable(map,
                "Block " + (selectedBlock + 1) + " head " + (selectedHead + 1) + " attention",
                "64x64 attention matrix · rows = from-square · cols = to-square",
                "click to inspect full 4096 values",
                "bt4.block" + selectedBlock + ".attention.heads",
                selectedHead * 64 * 64, 64 * 64, 64, "64x64");
        if (selectedSquare >= 0) {
            g.setColor(new Color(TensorViz.NEGATIVE.getRed(),
                    TensorViz.NEGATIVE.getGreen(), TensorViz.NEGATIVE.getBlue(), 80));
            int rowY = (int) Math.round(map.y + selectedSquare * (map.height / 64.0));
            int rowH = Math.max(1, (int) Math.round(map.height / 64.0));
            g.fillRect(map.x, rowY, map.width, rowH);
            int colX = (int) Math.round(map.x + selectedSquare * (map.width / 64.0));
            int colW = Math.max(1, (int) Math.round(map.width / 64.0));
            g.fillRect(colX, map.y, colW, map.height);
            // axis labels
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(10, Font.PLAIN));
            g.drawString(TensorViz.squareLabel(selectedSquare),
                    map.x - 22, rowY + rowH);
            g.drawString(TensorViz.squareLabel(selectedSquare),
                    colX, map.y - 4);
        }
    }

    /**
     * Paints the on-board overlay panel. Each square is split diagonally
     * from top-left to bottom-right: the upper-right half is shaded by
     * attention <em>from</em> the selected square to this one (selected →
     * this, "outgoing"), the lower-left half by attention <em>to</em> the
     * selected square from this one (this → selected, "incoming"). Both
     * directions stay visible simultaneously and share a single colour
     * scale so magnitudes are directly comparable.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintBoardOverlay(Graphics2D g, Rectangle r) {
        int headerH = 38;
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, headerH),
                "attention on the board",
                "▟ lower-left = incoming (this → selected)  ·  ▜ upper-right = outgoing (selected → this)");
        int top = r.y + headerH + 6;
        int bottomReserve = 22;
        int availW = r.width - 16;
        int availH = r.height - headerH - 6 - bottomReserve;
        int size = Math.min(availW, availH);
        Rectangle board = new Rectangle(r.x + (r.width - size) / 2, top, size, size);
        TensorViz.drawMiniBoard(g, board);
        TensorViz.drawPositionPieces(g, board, fen);
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
                g.setColor(TensorViz.FOCUS);
                g.drawRect(board.x + sf * cellW, board.y + dr * cellH, cellW, cellH);
                g.drawRect(board.x + sf * cellW + 1, board.y + dr * cellH + 1, cellW - 2, cellH - 2);
            }
        }
        TensorViz.drawBoardCoordinates(g, board);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(10, Font.PLAIN));
        String hint = selectedSquare < 0
                ? "no square selected — click a board square"
                : "selected: " + TensorViz.squareLabel(selectedSquare)
                        + " · click it again to clear";
        g.drawString(hint, r.x + 8, r.y + r.height - 4);
    }

    /**
     * Draws the two-triangle attention overlay. For each of the 64 squares
     * the cell is split along the main (top-left → bottom-right) diagonal:
     * the upper-right triangle is filled with a warm amber tone scaled by
     * outgoing attention (selected → this square), and the lower-left
     * triangle with a cool indigo tone scaled by incoming attention (this
     * square → selected). Both halves share one colour scale so magnitudes
     * are directly comparable.
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
        Color outgoingBase = TensorViz.POSITIVE;
        Color incomingBase = TensorViz.NEGATIVE;
        Color diagonal = Theme.withAlpha(Theme.TEXT, 58);
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

            // Upper-right triangle = outgoing (selected → this).
            java.awt.geom.Path2D.Double upperRight = new java.awt.geom.Path2D.Double();
            upperRight.moveTo(xd, yd);
            upperRight.lineTo(xr, yd);
            upperRight.lineTo(xr, yb);
            upperRight.closePath();
            g.setColor(translucent(outgoingBase, vTo));
            g.fill(upperRight);

            // Lower-left triangle = incoming (this → selected).
            java.awt.geom.Path2D.Double lowerLeft = new java.awt.geom.Path2D.Double();
            lowerLeft.moveTo(xd, yd);
            lowerLeft.lineTo(xd, yb);
            lowerLeft.lineTo(xr, yb);
            lowerLeft.closePath();
            g.setColor(translucent(incomingBase, vFrom));
            g.fill(lowerLeft);

            // Thin diagonal separator along the top-left → bottom-right line
            // so the two halves stay readable even when one is fully
            // saturated and the other near zero.
            g.setColor(diagonal);
            g.drawLine((int) Math.round(xd), (int) Math.round(yd),
                    (int) Math.round(xr), (int) Math.round(yb));
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
        String selectedLabel = TensorViz.squareLabel(selectedSquare);
        for (int sq = 0; sq < 64; ++sq) {
            int file = sq & 7;
            int rank = sq >> 3;
            int drawRank = 7 - rank;
            Rectangle cell = new Rectangle(board.x + file * cellW, board.y + drawRank * cellH,
                    cellW, cellH);
            hitRegions.add(cell,
                    TensorViz.squareLabel(sq),
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
        g.setColor(Theme.PANEL_SOLID);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(Theme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(11, Font.BOLD));
        g.drawString(String.format("block %d   |   head %d/%d   |   mean attention %.4f",
                selectedBlock + 1, selectedHead + 1, HEADS, headMag), r.x + 8, r.y + 16);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(11, Font.PLAIN));
        float selectedFocus = headFocusScore(headReceivedEnergy(selectedBlock, selectedHead));
        List<HeadPick> top = topAttentionHeads(1);
        float topFocus = top.isEmpty() ? Math.max(1e-6f, selectedFocus) : Math.max(1e-6f, top.get(0).score);
        String text = String.format("importance: %s (%.0f%% of top head) · hottest pair: %s -> %s (%.3f)",
                attentionImportance(selectedFocus / topFocus),
                Math.min(100.0f, 100.0f * selectedFocus / topFocus),
                TensorViz.squareLabel(argmaxFrom),
                TensorViz.squareLabel(argmaxTo),
                maxEntry);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(Ui.elide(text, fm, r.width - 16), r.x + 8, r.y + 32);
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

    /**
     * Returns the block with the strongest average attention.
     *
     * @return block index
     */
    private int strongestAttentionBlock() {
        int best = 0;
        float bestValue = Float.NEGATIVE_INFINITY;
        for (int b = 0; b < BLOCKS; b++) {
            float value = attentionFocus(b);
            if (value > bestValue) {
                bestValue = value;
                best = b;
            }
        }
        return best;
    }

    /**
     * Returns the block with the strongest FFN RMS.
     *
     * @return block index
     */
    private int strongestFfnBlock() {
        int best = 0;
        float bestValue = Float.NEGATIVE_INFINITY;
        for (int b = 0; b < BLOCKS; b++) {
            float[] ffn = snapshot.data("bt4.block" + b + ".ffn");
            float value = TensorViz.summarize(ffn)[2];
            if (value > bestValue) {
                bestValue = value;
                best = b;
            }
        }
        return best;
    }

    /**
     * Returns the strongest square in a 64-value board array.
     *
     * @param values square values
     * @return square index or -1
     */
    private static int strongestSquare(float[] values) {
        if (values == null || values.length < 64) {
            return -1;
        }
        int best = 0;
        float bestValue = values[0];
        for (int sq = 1; sq < 64; sq++) {
            if (values[sq] > bestValue) {
                bestValue = values[sq];
                best = sq;
            }
        }
        return best;
    }

    /**
     * Labels a selected head relative to the most focused head in the snapshot.
     *
     * @param ratio selected/top
     * @return label
     */
    private static String attentionImportance(float ratio) {
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
     * One high-focus attention head selected for the BT4 atlas.
     */
    private static final class HeadPick {

        /**
         * Block index.
         */
        private final int block;

        /**
         * Head index.
         */
        private final int head;

        /**
         * Board-shaped mean attention-received pattern.
         */
        private final float[] energy;

        /**
         * Focus score.
         */
        private final float score;

        /**
         * Creates a selected attention-head summary.
         *
         * @param block block index
         * @param head head index
         * @param energy attention energy map
         * @param score focus score
         */
        HeadPick(int block, int head, float[] energy, float score) {
            this.block = block;
            this.head = head;
            this.energy = energy;
            this.score = score;
        }
    }
}
