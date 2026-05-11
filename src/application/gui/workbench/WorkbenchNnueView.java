package application.gui.workbench;

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
 * Workbench panel that visualises an NNUE half-KP forward pass.
 *
 * <p>Abstract mode shows the centipawn output, the top-N active features that
 * fired and their signed impact on the eval, and a short readout of which
 * accumulator slots moved the most. Detailed mode draws the full wired diagram
 * (features -&gt; accumulator -&gt; clipped -&gt; output) with weighted edges
 * coloured by sign and thickness scaled by magnitude.</p>
 */
final class WorkbenchNnueView extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Maximum number of accumulator slots drawn in the wired diagram.
     */
    private static final int MAX_VISIBLE_SLOTS = 32;

    /**
     * Maximum number of active features drawn in the wired diagram.
     */
    private static final int MAX_VISIBLE_FEATURES = 14;

    /**
     * Outer padding inside the panel.
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
     * Short label describing the loaded NNUE version.
     */
    private String versionLabel = "synthetic (FEN-seeded)";

    /**
     * Detailed-mode flag.
     */
    private boolean detailed;

    /**
     * Selected accumulator slot in the wired diagram (-1 when none).
     */
    private int selectedSlot = -1;

    /**
     * Cached visible-slot indices (mapping slot order -> accumulator index).
     */
    private int[] visibleSlots = new int[0];

    /**
     * Cached visible-feature indices (mapping row -> feature index).
     */
    private int[] visibleFeatures = new int[0];

    /**
     * Hover + click region registry, rebuilt every paint pass.
     */
    private final WorkbenchHitRegions hitRegions = new WorkbenchHitRegions();

    /**
     * Embedded inspector panel (right-side), populated on click. Null until
     * the host wires one in.
     */
    private WorkbenchInspectorPanel inspector;

    /**
     * When true, the view pins its heatmap colour scales across position
     * changes so different positions stay visually comparable.
     */
    private boolean fixedScale;

    /**
     * Selected feature index inside the abstract-view feature list. Used to
     * highlight K/P squares on the position panel. -1 when nothing selected.
     */
    private int selectedFeature = -1;

    /**
     * Creates the NNUE view.
     */
    WorkbenchNnueView() {
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
     * @param panel inspector (may be null to detach)
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
        rebuildVisibleSelection();
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
     * Sets a label describing the loaded NNUE network version (or "synthetic").
     *
     * @param label short version label
     */
    void setVersionLabel(String label) {
        this.versionLabel = label == null || label.isBlank() ? "" : label;
        repaint();
    }

    /**
     * Sets the detailed-mode flag.
     *
     * @param value true for detailed mode
     */
    void setDetailed(boolean value) {
        if (detailed != value) {
            detailed = value;
            selectedSlot = -1;
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
        if (detailed && visibleSlots.length > 0) {
            Rectangle body = new Rectangle(PAD, 64, getWidth() - 2 * PAD, getHeight() - 64 - PAD);
            int boardSide = Math.min(180, Math.max(120, body.height / 4));
            Rectangle wire = new Rectangle(body.x, body.y + 48,
                    body.width - boardSide - 20, body.height - 48);
            Layout layout = layout(wire);
            for (int i = 0; i < visibleSlots.length; ++i) {
                int cx = layout.accumCx;
                int cy = layout.startY + i * layout.slotPitch;
                int dx = x - cx;
                int dy = y - cy;
                if (dx * dx + dy * dy <= layout.slotRadius * layout.slotRadius * 4) {
                    selectedSlot = i;
                    repaint();
                    return;
                }
            }
        }
        WorkbenchHitRegions.Region r = hitRegions.hitTest(x, y);
        if (r == null) {
            return;
        }
        if (r.title != null && r.title.startsWith("Feature #")) {
            selectedFeature = parseFeatureIndex(r.title);
            repaint();
        }
        if (inspector != null) {
            inspector.inspect(r, snapshot);
        } else if (r.dataKey != null && snapshot != null) {
            WorkbenchInspectorDialog.shared(this).inspect(r, snapshot);
        }
    }

    /**
     * Extracts the integer feature index embedded in a hit-region title of
     * the form "Feature #12345  ...".
     *
     * @param title region title
     * @return feature index or -1 when not parseable
     */
    private static int parseFeatureIndex(String title) {
        int hash = title.indexOf('#');
        if (hash < 0) {
            return -1;
        }
        int end = hash + 1;
        while (end < title.length() && Character.isDigit(title.charAt(end))) {
            end++;
        }
        if (end == hash + 1) {
            return -1;
        }
        try {
            return Integer.parseInt(title.substring(hash + 1, end));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /**
     * Paints the empty-state hint.
     *
     * @param g graphics
     * @param bounds full bounds
     */
    private void paintEmpty(Graphics2D g, Rectangle bounds) {
        g.setColor(WorkbenchTheme.BG);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(13, Font.PLAIN));
        g.drawString("No NNUE snapshot. Change the FEN to refresh.", PAD, 32);
    }

    /**
     * Paints the top header strip (title + key stats).
     *
     * @param g graphics
     * @param bounds full bounds
     */
    private void paintHeader(Graphics2D g, Rectangle bounds) {
        float[] aff = snapshot.data("nnue.output.affine");
        float[] cp = snapshot.data("nnue.output.centipawns");
        float[] indicesUs = snapshot.data("nnue.features.us.indices");
        float[] indicesThem = snapshot.data("nnue.features.them.indices");
        float affine = aff == null ? 0.0f : aff[0];
        float centipawns = cp == null ? 0.0f : cp[0];
        int activeUs = indicesUs == null ? 0 : indicesUs.length;
        int activeThem = indicesThem == null ? 0 : indicesThem.length;
        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(15, Font.BOLD));
        g.drawString("NNUE (half-KP) accumulator path", PAD, 22);
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
        g.drawString(String.format(
                "active features: us %d · them %d   |   affine %+.2f   |   eval %+d cp",
                activeUs, activeThem, affine, Math.round(centipawns)), PAD, 40);
        if (versionLabel != null && !versionLabel.isEmpty()) {
            g.setFont(WorkbenchTheme.font(10, Font.ITALIC));
            g.drawString(versionLabel, PAD, 54);
        }
        Rectangle barRect = new Rectangle(getWidth() - 220 - PAD, 18, 220, 22);
        WorkbenchTensorViz.drawHorizontalBar(g, barRect, centipawns, Math.max(80.0f, Math.abs(centipawns) * 1.4f),
                String.format("%+d cp", Math.round(centipawns)));
        hitRegions.addInspectable(barRect,
                "Final centipawn eval",
                "Network output after scaling. Positive favors side to move.",
                String.format("%+d cp · affine %+.2f", Math.round(centipawns), affine),
                "nnue.output.centipawns", 0, 0, 0, "1");
        hitRegions.addInspectable(new Rectangle(PAD, 6, getWidth() - 240 - PAD, 50),
                "NNUE header",
                versionLabel == null ? "Loaded network info" : versionLabel,
                String.format("us %d active · them %d active", activeUs, activeThem),
                "nnue.features.us.indices", 0, 0, 0, activeUs + " active");
    }

    /**
     * Paints the abstract overview ("what does the network see?").
     *
     * <p>Layout: a large position panel on the left (with the selected
     * feature's king/piece squares highlighted), an accumulator strip on the
     * right, and a two-column "Top positive / Top negative" contributor list
     * underneath. The split-by-sign view answers the research question of
     * which features push the eval up vs down at a glance.</p>
     *
     * @param g graphics
     * @param body body rectangle
     */
    private void paintAbstract(Graphics2D g, Rectangle body) {
        int boardSide = Math.min(260, Math.max(180, body.height / 2));
        Rectangle boardRect = new Rectangle(body.x, body.y, boardSide, boardSide);
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(boardRect.x, boardRect.y, boardRect.width, 18),
                "position", null);
        Rectangle innerBoard = new Rectangle(boardRect.x + 6, boardRect.y + 24,
                boardRect.width - 12, boardRect.height - 30);
        WorkbenchTensorViz.drawMiniBoard(g, innerBoard);
        WorkbenchTensorViz.drawPositionPieces(g, innerBoard, fen);
        paintSelectedFeatureOverlay(g, innerBoard);
        hitRegions.add(innerBoard, "Current position",
                fen == null ? "no FEN" : fen,
                "NNUE half-KP features are derived from this position");

        Rectangle right = new Rectangle(body.x + boardSide + 12, body.y,
                body.width - boardSide - 12, boardSide);
        paintAccumulatorOverview(g, right);

        Rectangle below = new Rectangle(body.x, body.y + boardSide + 12, body.width,
                body.height - boardSide - 12);
        paintTopContributors(g, below);
    }

    /**
     * Highlights the king and piece squares of the currently selected feature
     * on the mini position board to connect the feature list to the board.
     *
     * @param g graphics
     * @param board mini-board rectangle
     */
    private void paintSelectedFeatureOverlay(java.awt.Graphics2D g, Rectangle board) {
        if (selectedFeature < 0) {
            return;
        }
        int piecePart = selectedFeature % 641;
        int kingSquare = (selectedFeature / 641) % 64;
        int pieceSquare = piecePart % 64;
        int pieceCode = piecePart / 64;
        double cellW = board.width / 8.0;
        double cellH = board.height / 8.0;
        highlightSquare(g, board, kingSquare, cellW, cellH, new java.awt.Color(255, 205, 70, 110));
        if (pieceSquare != kingSquare) {
            boolean enemy = pieceCode >= 5;
            highlightSquare(g, board, pieceSquare, cellW, cellH,
                    enemy ? new java.awt.Color(220, 90, 90, 110) : new java.awt.Color(80, 160, 220, 110));
        }
    }

    /**
     * Fills one board square with a translucent halo.
     *
     * @param g graphics
     * @param board mini-board rectangle
     * @param square 0..63 LERF index
     * @param cellW cell width
     * @param cellH cell height
     * @param tint translucent fill colour
     */
    private static void highlightSquare(java.awt.Graphics2D g, Rectangle board, int square,
            double cellW, double cellH, java.awt.Color tint) {
        int file = square & 7;
        int rank = square >> 3;
        int drawRank = 7 - rank;
        int x = (int) Math.floor(board.x + file * cellW);
        int y = (int) Math.floor(board.y + drawRank * cellH);
        int w = (int) Math.ceil(cellW + 1);
        int h = (int) Math.ceil(cellH + 1);
        g.setColor(tint);
        g.fillRect(x, y, w, h);
    }

    /**
     * Paints the top-positive / top-negative contributor columns.
     *
     * @param g graphics
     * @param r area rectangle
     */
    private void paintTopContributors(Graphics2D g, Rectangle r) {
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 36),
                "top contributors", "split by sign · click a row to highlight on the board and inspect");
        float[] indices = snapshot.data("nnue.features.us.indices");
        float[] impact = snapshot.data("nnue.features.us.impact");
        if (indices == null || impact == null || indices.length == 0) {
            return;
        }
        int colGap = 12;
        int colW = (r.width - colGap) / 2;
        Rectangle posCol = new Rectangle(r.x, r.y + 40, colW, r.height - 40);
        Rectangle negCol = new Rectangle(r.x + colW + colGap, r.y + 40, colW, r.height - 40);
        paintContributorColumn(g, posCol, indices, impact, true);
        paintContributorColumn(g, negCol, indices, impact, false);
    }

    /**
     * Paints one signed contributor column (positive or negative).
     *
     * @param g graphics
     * @param r column rectangle
     * @param indices feature indices array
     * @param impact feature impacts array
     * @param positive true for positive column, false for negative
     */
    private void paintContributorColumn(Graphics2D g, Rectangle r,
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
        int rows = Math.min(6, selected.size());
        int rowH = Math.max(36, (r.height - 26) / Math.max(1, rows));
        float maxAbs = 0.0f;
        for (float v : impact) {
            maxAbs = Math.max(maxAbs, Math.abs(v));
        }
        if (maxAbs <= 0.0f) {
            maxAbs = 1.0f;
        }
        g.setColor(positive ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE);
        g.setFont(WorkbenchTheme.font(11, Font.BOLD));
        g.drawString(positive ? "▲ top positive (us)" : "▼ top negative (us)", r.x + 2, r.y + 14);
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
        if (rows == 0) {
            g.drawString("(no " + (positive ? "positive" : "negative") + " features active)",
                    r.x + 4, r.y + 32);
            return;
        }
        int[] weightShape = snapshot.shape("nnue.features.us.weights");
        int hiddenStride = weightShape != null && weightShape.length >= 2 ? weightShape[1] : 0;
        g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < rows; ++i) {
            int idx = selected.get(i);
            int featureIdx = Math.round(indices[idx]);
            float v = impact[idx];
            int y = r.y + 22 + i * rowH;
            Rectangle row = new Rectangle(r.x, y, r.width, rowH - 4);
            boolean isSelected = featureIdx == selectedFeature;
            g.setColor(isSelected ? WorkbenchTheme.SELECTION_SOLID
                    : (i % 2 == 0 ? WorkbenchTheme.PANEL_SOLID : WorkbenchTheme.ELEVATED_SOLID));
            g.fillRect(row.x, row.y, row.width, row.height);
            g.setColor(isSelected ? WorkbenchTheme.ACCENT : WorkbenchTheme.LINE);
            g.drawRect(row.x, row.y, row.width - 1, row.height - 1);
            int glyphSize = Math.min(row.height - 4, 30);
            Rectangle glyph = new Rectangle(row.x + 3, row.y + (row.height - glyphSize) / 2,
                    glyphSize, glyphSize);
            int piecePart = featureIdx % 641;
            int kingSquare = (featureIdx / 641) % 64;
            int pieceSquare = piecePart % 64;
            int pieceCode = piecePart / 64;
            WorkbenchTensorViz.drawHalfKpGlyph(g, glyph, kingSquare, pieceCode, pieceSquare);
            String label = "#" + featureIdx + "  " + decodeHalfKP(featureIdx);
            g.setColor(WorkbenchTheme.TEXT);
            int textX = row.x + glyphSize + 8;
            g.drawString(label, textX, row.y + row.height / 2 + 4);
            String tail = String.format("%+5.1f cp", v);
            int tailW = fm.stringWidth(tail);
            int barX = textX + Math.max(140, fm.stringWidth(label) + 12);
            int barW = row.x + row.width - barX - tailW - 12;
            if (barW > 20) {
                Rectangle bar = new Rectangle(barX, row.y + (row.height - 12) / 2, barW, 12);
                WorkbenchTensorViz.drawHorizontalBar(g, bar, v, maxAbs, null);
            }
            g.setColor(v >= 0 ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE);
            g.drawString(tail, row.x + row.width - tailW - 6, row.y + row.height / 2 + 4);
            if (hiddenStride > 0) {
                hitRegions.addInspectable(row,
                        "Feature #" + featureIdx + "  " + decodeHalfKP(featureIdx),
                        "Half-KP active feature · row of feature->L1 weight matrix",
                        String.format("impact %+.2f cp", v),
                        "nnue.features.us.weights",
                        idx * hiddenStride, hiddenStride, 0,
                        "1x" + hiddenStride);
            } else {
                hitRegions.add(row,
                        "Feature #" + featureIdx + "  " + decodeHalfKP(featureIdx),
                        "Half-KP active feature",
                        String.format("impact %+.2f cp", v));
            }
        }
    }

    /**
     * Paints the accumulator slot overview heatmap.
     *
     * @param g graphics
     * @param r rectangle
     */
    private void paintAccumulatorOverview(Graphics2D g, Rectangle r) {
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
                "accumulator slots (us | them)",
                "clipped activation per slot | side by side");
        float[] us = snapshot.data("nnue.clipped.us");
        float[] them = snapshot.data("nnue.clipped.them");
        if (us == null || them == null) {
            return;
        }
        int rows = 16;
        int colsPerSide = us.length / rows;
        int gap = 8;
        int sideW = (r.width - gap - 16) / 2;
        int heatH = r.height - 56;
        Rectangle leftMap = new Rectangle(r.x + 8, r.y + 48, sideW, heatH);
        Rectangle rightMap = new Rectangle(r.x + sideW + 8 + gap, r.y + 48, sideW, heatH);
        WorkbenchTensorViz.drawHeatmap(g, leftMap, us, colsPerSide, rows, 1.0f, false);
        WorkbenchTensorViz.drawHeatmap(g, rightMap, them, colsPerSide, rows, 1.0f, false);
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(10, Font.BOLD));
        g.drawString("us", leftMap.x, leftMap.y - 4);
        g.drawString("them", rightMap.x, rightMap.y - 4);
        float[] stats = WorkbenchTensorViz.summarize(us);
        float[] them2 = WorkbenchTensorViz.summarize(them);
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
    }

    /**
     * Paints the detailed wired-diagram view.
     *
     * @param g graphics
     * @param body body rectangle
     */
    private void paintDetailed(Graphics2D g, Rectangle body) {
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(body.x, body.y, body.width, 40),
                "wired diagram (us side)",
                "active features -> accumulator -> clipped -> output  (click a node to inspect edges)");
        int boardSide = Math.min(180, Math.max(120, body.height / 4));
        Rectangle boardArea = new Rectangle(body.x + body.width - boardSide - 8,
                body.y + body.height - boardSide - 32, boardSide, boardSide);
        Rectangle wire = new Rectangle(body.x, body.y + 48,
                body.width - boardSide - 20, body.height - 48);
        Layout layout = layout(wire);
        drawColumnLabels(g, layout);
        drawFeatureColumn(g, layout);
        drawAccumulatorColumn(g, layout);
        drawClippedColumn(g, layout);
        drawOutputColumn(g, layout);
        drawEdges(g, layout);
        WorkbenchTensorViz.drawMiniBoard(g, boardArea);
        WorkbenchTensorViz.drawPositionPieces(g, boardArea, fen);
        hitRegions.add(boardArea, "Current position",
                fen == null ? "no FEN" : fen,
                "Half-KP features are derived from this board");
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
        g.drawString("position", boardArea.x, boardArea.y - 4);
        drawDetailedReadout(g, body, layout);
    }

    /**
     * Computes column geometry.
     *
     * @param r body rectangle
     * @return layout
     */
    private Layout layout(Rectangle r) {
        Layout out = new Layout();
        int colW = 96;
        int gap = (r.width - 4 * colW - 20) / 3;
        gap = Math.max(40, gap);
        int leftCx = r.x + colW / 2 + 30;
        out.featureCx = leftCx;
        out.accumCx = leftCx + colW + gap;
        out.clippedCx = out.accumCx + colW + gap;
        out.outputCx = out.clippedCx + colW + gap;
        out.slotRadius = 7;
        out.startY = r.y + 40;
        int avail = r.height - 90;
        int slots = Math.min(MAX_VISIBLE_SLOTS, visibleSlots.length == 0 ? MAX_VISIBLE_SLOTS : visibleSlots.length);
        out.slotPitch = Math.max(14, avail / Math.max(1, slots));
        out.bottomY = out.startY + (slots - 1) * out.slotPitch;
        return out;
    }

    /**
     * Draws column labels.
     *
     * @param g graphics
     * @param layout layout
     */
    private void drawColumnLabels(Graphics2D g, Layout layout) {
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(11, Font.BOLD));
        int y = layout.startY - 18;
        drawCenteredLabel(g, "features", layout.featureCx, y);
        drawCenteredLabel(g, "accumulator", layout.accumCx, y);
        drawCenteredLabel(g, "clipped", layout.clippedCx, y);
        drawCenteredLabel(g, "output", layout.outputCx, y);
    }

    /**
     * Draws the feature node column.
     *
     * @param g graphics
     * @param layout layout
     */
    private void drawFeatureColumn(Graphics2D g, Layout layout) {
        float[] indices = snapshot.data("nnue.features.us.indices");
        float[] impact = snapshot.data("nnue.features.us.impact");
        if (indices == null) {
            return;
        }
        int count = Math.min(MAX_VISIBLE_FEATURES, visibleFeatures.length);
        int pitch = layout.slotPitch * Math.max(1, MAX_VISIBLE_SLOTS / Math.max(1, count));
        float maxAbs = 0.0f;
        if (impact != null) {
            for (float v : impact) {
                maxAbs = Math.max(maxAbs, Math.abs(v));
            }
        }
        if (maxAbs <= 0.0f) {
            maxAbs = 1.0f;
        }
        g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
        int[] weightShape = snapshot.shape("nnue.features.us.weights");
        int hiddenStride = weightShape != null && weightShape.length >= 2 ? weightShape[1] : 0;
        for (int i = 0; i < count; ++i) {
            int featureIdx = Math.round(indices[visibleFeatures[i]]);
            float rawImpact = impact == null ? 0.0f : impact[visibleFeatures[i]];
            float v = rawImpact / maxAbs;
            int y = layout.startY + i * pitch;
            WorkbenchTensorViz.drawNode(g, layout.featureCx, y, layout.slotRadius, v, false);
            g.setColor(WorkbenchTheme.TEXT);
            String label = "#" + featureIdx;
            FontMetrics fm = g.getFontMetrics();
            int lw = fm.stringWidth(label);
            g.drawString(label, layout.featureCx - lw - 14, y + 4);
            Rectangle hit = new Rectangle(layout.featureCx - lw - 18, y - layout.slotRadius - 2,
                    lw + layout.slotRadius * 2 + 22, layout.slotRadius * 2 + 4);
            String dec = decodeHalfKP(featureIdx);
            if (hiddenStride > 0) {
                hitRegions.addInspectable(hit,
                        "Feature #" + featureIdx + "  " + dec,
                        "Active half-KP feature · click to see its row of feature->L1 weights",
                        String.format("impact %+.2f cp", rawImpact),
                        "nnue.features.us.weights",
                        visibleFeatures[i] * hiddenStride, hiddenStride, 0,
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
     * Draws the accumulator (L1) column.
     *
     * @param g graphics
     * @param layout layout
     */
    private void drawAccumulatorColumn(Graphics2D g, Layout layout) {
        float[] acc = snapshot.data("nnue.accumulator.us");
        if (acc == null) {
            return;
        }
        float scale = 0.0f;
        for (int idx : visibleSlots) {
            scale = Math.max(scale, Math.abs(acc[idx]));
        }
        if (scale <= 0.0f) {
            scale = 1.0f;
        }
        for (int i = 0; i < visibleSlots.length; ++i) {
            int idx = visibleSlots[i];
            float raw = acc[idx];
            float v = raw / scale;
            int y = layout.startY + i * layout.slotPitch;
            WorkbenchTensorViz.drawNode(g, layout.accumCx, y, layout.slotRadius, v, i == selectedSlot);
            int rad = layout.slotRadius + 2;
            hitRegions.add(new Rectangle(layout.accumCx - rad, y - rad, rad * 2, rad * 2),
                    "Accumulator slot " + idx,
                    "Pre-ReLU accumulator value (sum of active feature weights + bias)",
                    String.format("%+.3f", raw));
        }
    }

    /**
     * Draws the clipped (post-relu) column.
     *
     * @param g graphics
     * @param layout layout
     */
    private void drawClippedColumn(Graphics2D g, Layout layout) {
        float[] cl = snapshot.data("nnue.clipped.us");
        if (cl == null) {
            return;
        }
        for (int i = 0; i < visibleSlots.length; ++i) {
            int idx = visibleSlots[i];
            int y = layout.startY + i * layout.slotPitch;
            WorkbenchTensorViz.drawNode(g, layout.clippedCx, y, layout.slotRadius, cl[idx], i == selectedSlot);
            int rad = layout.slotRadius + 2;
            hitRegions.add(new Rectangle(layout.clippedCx - rad, y - rad, rad * 2, rad * 2),
                    "Clipped slot " + idx,
                    "Post-ReLU activation (negative values clipped to 0)",
                    String.format("%.3f", cl[idx]));
        }
    }

    /**
     * Draws the output column (single node, bar to the right).
     *
     * @param g graphics
     * @param layout layout
     */
    private void drawOutputColumn(Graphics2D g, Layout layout) {
        float[] cp = snapshot.data("nnue.output.centipawns");
        float value = cp == null ? 0.0f : cp[0];
        int cy = (layout.startY + layout.bottomY) / 2;
        float scale = Math.max(80.0f, Math.abs(value) * 1.4f);
        int rad = layout.slotRadius + 6;
        WorkbenchTensorViz.drawNode(g, layout.outputCx, cy, rad, value / scale, false);
        Rectangle bar = new Rectangle(layout.outputCx + 18, cy - 8, 80, 16);
        WorkbenchTensorViz.drawHorizontalBar(g, bar, value, scale, null);
        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(11, Font.BOLD));
        g.drawString(String.format("%+d cp", Math.round(value)), layout.outputCx + 18, cy + 24);
        hitRegions.addInspectable(new Rectangle(layout.outputCx - rad, cy - rad, rad * 2 + 100, rad * 2 + 28),
                "Centipawn output",
                "Final NNUE eval after affine + scaling",
                String.format("%+d cp", Math.round(value)),
                "nnue.output.centipawns", 0, 0, 0, "1");
    }

    /**
     * Draws all weighted edges, emphasising the selected slot's edges.
     *
     * @param g graphics
     * @param layout layout
     */
    private void drawEdges(Graphics2D g, Layout layout) {
        float[] cl = snapshot.data("nnue.clipped.us");
        float[] contrib = snapshot.data("nnue.output.contribution.us");
        float[] indices = snapshot.data("nnue.features.us.indices");
        float[] featureWeights = snapshot.data("nnue.features.us.weights");
        int[] featureWeightShape = snapshot.shape("nnue.features.us.weights");
        if (cl == null || contrib == null || indices == null) {
            return;
        }
        int hidden = cl.length;
        int activeFeatures = indices.length;
        int weightsPerFeature = (featureWeights != null && featureWeightShape != null
                && featureWeightShape.length >= 2)
                ? featureWeightShape[1]
                : hidden;

        // Edges are rendered "focused mode": only the selected slot's incoming
        // and outgoing edges are drawn at full strength. When no slot is
        // selected, draw the top-K strongest contribution edges to keep the
        // diagram readable instead of showing every feature->slot pair.
        boolean focused = selectedSlot >= 0 && selectedSlot < visibleSlots.length;
        int featureCount = Math.min(MAX_VISIBLE_FEATURES, visibleFeatures.length);
        int featurePitch = layout.slotPitch * Math.max(1, MAX_VISIBLE_SLOTS / Math.max(1, featureCount));

        // Decide which slots to draw edges for.
        int topK = 3;
        int[] slotsToShow;
        if (focused) {
            slotsToShow = new int[] { selectedSlot };
        } else {
            Integer[] ranked = new Integer[visibleSlots.length];
            for (int i = 0; i < ranked.length; i++) {
                ranked[i] = i;
            }
            java.util.Arrays.sort(ranked,
                    (a, b) -> Float.compare(Math.abs(contrib[visibleSlots[b]]),
                            Math.abs(contrib[visibleSlots[a]])));
            int take = Math.min(topK, ranked.length);
            slotsToShow = new int[take];
            for (int i = 0; i < take; i++) {
                slotsToShow[i] = ranked[i];
            }
        }

        // Per-feature-to-slot edge scale.
        float featWeightScale = 0.0f;
        if (featureWeights != null) {
            for (int fi : visibleFeatures) {
                for (int si : slotsToShow) {
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
        float contribScale = 0.0f;
        for (int idx : visibleSlots) {
            contribScale = Math.max(contribScale, Math.abs(contrib[idx]));
        }
        if (contribScale <= 0.0f) {
            contribScale = 1.0f;
        }

        // Features -> accumulator: only for the slots in slotsToShow.
        for (int fi = 0; fi < featureCount; ++fi) {
            int featureIdx = visibleFeatures[fi];
            int featureY = layout.startY + fi * featurePitch;
            for (int si : slotsToShow) {
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
                WorkbenchTensorViz.drawWeightedEdge(g, layout.featureCx + layout.slotRadius, featureY,
                        layout.accumCx - layout.slotRadius, slotY, strength, si == selectedSlot);
            }
        }

        // Accumulator -> clipped: identity-style edge per slot to show
        // activation flow. All slots are drawn but de-emphasised; the
        // focused slot stands out.
        for (int si = 0; si < visibleSlots.length; ++si) {
            int slotY = layout.startY + si * layout.slotPitch;
            int idx = visibleSlots[si];
            float w1 = cl[idx];
            if (!focused || si == selectedSlot) {
                WorkbenchTensorViz.drawWeightedEdge(g, layout.accumCx + layout.slotRadius, slotY,
                        layout.clippedCx - layout.slotRadius, slotY, w1, si == selectedSlot);
            }
        }

        // Clipped -> output: only top-K contributors (or the focused slot).
        int outCy = (layout.startY + layout.bottomY) / 2;
        for (int si : slotsToShow) {
            int slotY = layout.startY + si * layout.slotPitch;
            int idx = visibleSlots[si];
            float w2 = contrib[idx] / contribScale;
            WorkbenchTensorViz.drawWeightedEdge(g, layout.clippedCx + layout.slotRadius, slotY,
                    layout.outputCx - (layout.slotRadius + 6), outCy, w2, si == selectedSlot);
        }
    }

    /**
     * Draws the detail-mode readout footer.
     *
     * @param g graphics
     * @param body body rectangle
     * @param layout layout
     */
    private void drawDetailedReadout(Graphics2D g, Rectangle body, Layout layout) {
        if (selectedSlot < 0 || selectedSlot >= visibleSlots.length) {
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
            g.drawString("click an accumulator node to focus its edges",
                    body.x + 8, body.y + body.height - 8);
            return;
        }
        int idx = visibleSlots[selectedSlot];
        float[] acc = snapshot.data("nnue.accumulator.us");
        float[] cl = snapshot.data("nnue.clipped.us");
        float[] ow = snapshot.data("nnue.output.weights.us");
        float[] contrib = snapshot.data("nnue.output.contribution.us");
        float a = acc == null ? 0.0f : acc[idx];
        float c = cl == null ? 0.0f : cl[idx];
        float w = ow == null ? 0.0f : ow[idx];
        float k = contrib == null ? 0.0f : contrib[idx];
        String text = String.format(
                "slot %3d   |   accumulator %+.3f -> clipped %.3f   x   weight %+.3f   =   contribution %+.3f",
                idx, a, c, w, k);
        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(11, Font.BOLD));
        g.drawString(text, body.x + 8, body.y + body.height - 8);
    }

    /**
     * Draws a centered label.
     *
     * @param g graphics
     * @param text label
     * @param cx center x
     * @param y baseline y
     */
    private void drawCenteredLabel(Graphics2D g, String text, int cx, int y) {
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text);
        g.drawString(text, cx - w / 2, y);
    }

    /**
     * Recomputes the visible-slot / visible-feature index lists from the
     * snapshot.
     */
    private void rebuildVisibleSelection() {
        selectedSlot = -1;
        if (snapshot == null) {
            visibleSlots = new int[0];
            visibleFeatures = new int[0];
            return;
        }
        float[] contrib = snapshot.data("nnue.output.contribution.us");
        if (contrib == null) {
            visibleSlots = new int[0];
        } else {
            Integer[] ranked = new Integer[contrib.length];
            for (int i = 0; i < contrib.length; ++i) {
                ranked[i] = i;
            }
            java.util.Arrays.sort(ranked,
                    (a, b) -> Float.compare(Math.abs(contrib[b]), Math.abs(contrib[a])));
            int n = Math.min(MAX_VISIBLE_SLOTS, ranked.length);
            visibleSlots = new int[n];
            for (int i = 0; i < n; ++i) {
                visibleSlots[i] = ranked[i];
            }
        }
        float[] impact = snapshot.data("nnue.features.us.impact");
        if (impact == null) {
            visibleFeatures = new int[0];
        } else {
            Integer[] ranked = new Integer[impact.length];
            for (int i = 0; i < impact.length; ++i) {
                ranked[i] = i;
            }
            java.util.Arrays.sort(ranked,
                    (a, b) -> Float.compare(Math.abs(impact[b]), Math.abs(impact[a])));
            int n = Math.min(MAX_VISIBLE_FEATURES, ranked.length);
            visibleFeatures = new int[n];
            for (int i = 0; i < n; ++i) {
                visibleFeatures[i] = ranked[i];
            }
        }
    }

    /**
     * Decodes a half-KP feature index into a human-friendly summary.
     *
     * @param featureIndex 0..41023
     * @return summary like "Kc4 / Nf6"
     */
    private static String decodeHalfKP(int featureIndex) {
        int piecePart = featureIndex % 641;
        int kingSquare = (featureIndex / 641) % 64;
        int pieceSquare = piecePart % 64;
        int pieceCode = piecePart / 64;
        char[] codes = { 'P', 'N', 'B', 'R', 'Q', 'p', 'n', 'b', 'r', 'q' };
        char pieceChar = pieceCode < codes.length ? codes[pieceCode] : '?';
        return "K" + WorkbenchTensorViz.squareLabel(kingSquare)
                + " / " + pieceChar + WorkbenchTensorViz.squareLabel(pieceSquare);
    }

    /**
     * Geometry for the wired-diagram columns.
     */
    private static final class Layout {

        /**
         * Center x of the feature column.
         */
        private int featureCx;

        /**
         * Center x of the accumulator column.
         */
        private int accumCx;

        /**
         * Center x of the clipped column.
         */
        private int clippedCx;

        /**
         * Center x of the output column.
         */
        private int outputCx;

        /**
         * Slot radius.
         */
        private int slotRadius;

        /**
         * Top y of the first slot.
         */
        private int startY;

        /**
         * Y pitch between slots.
         */
        private int slotPitch;

        /**
         * Bottom y of the last slot.
         */
        private int bottomY;
    }
}
