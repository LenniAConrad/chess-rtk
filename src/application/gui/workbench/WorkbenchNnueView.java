package application.gui.workbench;

import java.awt.BasicStroke;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Scrollable;
import javax.swing.SwingConstants;

import chess.nn.nnue.FeatureEncoder;

/**
 * Workbench panel that visualises an NNUE half-KP forward pass.
 *
 * <p>Abstract mode shows the centipawn output, the top-N active features that
 * fired and their signed impact on the eval, and a short readout of which
 * accumulator slots moved the most. Detailed mode draws the full wired diagram
 * (features -&gt; accumulator -&gt; clipped -&gt; output) with weighted edges
 * coloured by sign and opacity scaled by magnitude.</p>
 *
 * <p>Shared scaffolding lives in {@link WorkbenchNetworkView}; this class adds
 * the NNUE-specific drawing plus the {@link Scrollable} plumbing the atlas mode
 * needs to expose every accumulator slot in a tall scrolling mosaic.</p>
 */
final class WorkbenchNnueView extends WorkbenchNetworkView implements Scrollable {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Vertical pitch for accumulator neurons in the Trace graph.
     */
    private static final int TRACE_SLOT_PITCH = 22;

    /**
     * Maximum accumulator slots drawn in Trace mode. Trace is an instructional
     * path view; dense all-slot data remains available in All mode.
     */
    private static final int TRACE_MAX_VISIBLE_SLOTS = 32;

    /**
     * Fixed feature lanes in Trace mode. A legal position has at most 30
     * non-king pieces, so these lanes can show every active HalfKP feature while
     * leaving inactive lanes visible as placeholders.
     */
    private static final int TRACE_FEATURE_LANES = FeatureEncoder.MAX_ACTIVE_FEATURES;

    /**
     * Stockfish HalfKAv2_hm can activate one feature per occupied square.
     */
    private static final int STOCKFISH_TRACE_FEATURE_LANES = 32;

    /**
     * Vertical pitch for HalfKP feature lanes in the Trace graph.
     */
    private static final int TRACE_FEATURE_PITCH = 40;

    /**
     * Header height for the ChessNNVisualizer-style Trace graph.
     */
    private static final int TRACE_HEADER_H = 48;

    /**
     * Height of the layer-stack summary ribbon in Trace mode.
     */
    private static final int TRACE_RIBBON_H = 92;

    /**
     * Uniform Trace edge stroke. Edge sign uses colour and edge magnitude uses
     * opacity; stroke width is deliberately fixed so dense paths compare cleanly.
     */
    private static final float TRACE_EDGE_WIDTH = 1.05f;

    /**
     * Lowest alpha used for visible Trace connections.
     */
    private static final int TRACE_EDGE_ALPHA_MIN = 20;

    /**
     * Highest alpha used for non-focused Trace connections.
     */
    private static final int TRACE_EDGE_ALPHA_MAX = 168;

    /**
     * Extra alpha for the currently selected lane's connections.
     */
    private static final int TRACE_EDGE_FOCUS_BOOST = 58;

    /**
     * Short label describing the loaded NNUE version.
     */
    private String versionLabel = "synthetic Stockfish-shaped (FEN-seeded)";

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
     * Selected feature index inside the abstract-view feature list. Used to
     * highlight K/P squares on the position panel. -1 when nothing selected.
     */
    private int selectedFeature = -1;

    /**
     * Selected hidden-neuron tile in atlas mode (-1 when none).
     */
    private int atlasSelected = -1;

    /**
     * Zoomed hidden-neuron tile (-1 when the atlas mosaic is shown). Clicking
     * any tile in mosaic mode zooms into that slot; clicking again returns
     * to the mosaic.
     */
    private int atlasZoomedSlot = -1;

    /**
     * Piece plane shown in the atlas detail board.
     */
    private int atlasSelectedPlane;

    /**
     * Atlas neuron-sort mode (one of "magnitude", "sparsity", "piece focus",
     * "slot index").
     */
    private String atlasSort = "magnitude";

    /**
     * When non-null, the atlas is rendered as the signed difference between
     * the current network's atlas and this comparison atlas — that is,
     * {@code current[i] − compare[i]}. A separate colormap (warm/cool) makes
     * the diff visually distinct from the normal absolute atlas.
     */
    private WorkbenchActivationSnapshot atlasCompare;

    /**
     * Output-overlay flag. When on every neuron row in the atlas is tinted
     * by its current output-head contribution magnitude so the user can see
     * which slots matter most to the present eval at a glance.
     */
    private boolean atlasOverlay;

    /**
     * Grid view: when true the atlas paint path renders every discovered
     * variant as a small thumbnail side-by-side instead of the single
     * primary atlas.
     */
    private boolean atlasGrid;

    /**
     * Atlases for the grid view, kept in a separate list so the renderer
     * can iterate over them without rebuilding the structure on every paint.
     */
    private List<WorkbenchNetworkPanel.NnueAtlasEntry> atlasGridEntries = new ArrayList<>();

    /**
     * Selected board square (0..63, LERF). Set when the user clicks a
     * square on the mini-board or in the inspector. When set, the atlas
     * dims every cell whose board square doesn't match so the user can
     * see how every slot weighs that specific square.
     */
    private int selectedBoardSquare = -1;

    /**
     * Creates the NNUE view.
     */
    WorkbenchNnueView() {
        super(720, 540);
    }

    /**
     * Rebuilds the cached visible-slot / visible-feature selections whenever a
     * new snapshot arrives.
     */
    @Override
    protected void onSnapshotChanged() {
        rebuildVisibleSelection();
    }

    /**
     * Resets mode-scoped selection state (wired-diagram slot, atlas tile and
     * atlas zoom) whenever the view mode changes.
     */
    @Override
    protected void onViewModeChanged() {
        selectedSlot = -1;
        atlasSelected = -1;
        atlasZoomedSlot = -1;
        atlasSelectedPlane = 0;
        selectedFeature = -1;
        if (isAbstract() && inspector != null) {
            inspector.clear();
        }
    }

    /**
     * Returns the hover-tooltip text for the hit region under the cursor. In
     * atlas mode we augment the base tooltip with the specific board square
     * the cursor is over, plus the per-square weight value, so the user
     * can read individual cells without zooming in.
     *
     * @param event mouse event
     * @return HTML tooltip text or null
     */
    @Override
    public String getToolTipText(MouseEvent event) {
        WorkbenchHitRegions.Region r = hitRegions.hitTest(event.getX(), event.getY());
        if (r == null) {
            return null;
        }
        // Per-square enrichment only happens when the region points at the
        // atlas weights tensor and has a known 8×8 shape.
        if (r.dataKey != null
                && ("nnue.atlas.weights".equals(r.dataKey) || "nnue.atlas.king".equals(r.dataKey))
                && "8x8".equals(r.shapeText)
                && r.bounds.width >= 8 && r.bounds.height >= 8
                && snapshot != null) {
            int fileLocal = (int) (((event.getX() - r.bounds.x) / (double) r.bounds.width) * 8);
            int drawRank = (int) (((event.getY() - r.bounds.y) / (double) r.bounds.height) * 8);
            if (fileLocal < 0) fileLocal = 0;
            if (fileLocal > 7) fileLocal = 7;
            if (drawRank < 0) drawRank = 0;
            if (drawRank > 7) drawRank = 7;
            int rank = 7 - drawRank;
            int sq = rank * 8 + fileLocal;
            float[] data = snapshot.data(r.dataKey);
            if (data != null && r.dataOffset + sq < data.length) {
                float v = data[r.dataOffset + sq];
                String sqLabel = WorkbenchTensorViz.squareLabel(sq);
                String extra = String.format(
                        "<br><b>%s</b>: <span style='color:%s;'>%+.3f</span>",
                        sqLabel, v >= 0 ? "#ffd33c" : "#5acde6", v);
                String base = r.tooltipHtml();
                int closing = base.lastIndexOf("</html>");
                if (closing >= 0) {
                    return base.substring(0, closing) + extra + "</html>";
                }
                return base + extra;
            }
        }
        if (isAbstract()) {
            return overviewTooltip(r);
        }
        return r.tooltipHtml();
    }

    /**
     * Returns a hover tooltip for overview mode without the raw-inspection hint.
     */
    private static String overviewTooltip(WorkbenchHitRegions.Region r) {
        StringBuilder sb = new StringBuilder("<html>");
        sb.append("<b>").append(htmlEscape(r.title)).append("</b>");
        if (r.value != null && !r.value.isEmpty()) {
            sb.append("<br><span style='color:#9aa0a6;'>")
                    .append(htmlEscape(r.value)).append("</span>");
        }
        if (r.description != null && !r.description.isEmpty()) {
            sb.append("<br>").append(htmlEscape(r.description));
        }
        sb.append("</html>");
        return sb.toString();
    }

    /**
     * Escapes text for a small tooltip fragment.
     */
    private static String htmlEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Sets the atlas neuron-sort mode.
     *
     * @param mode sort label
     */
    void setAtlasSort(String mode) {
        if (mode == null || mode.equals(atlasSort)) {
            return;
        }
        atlasSort = mode;
        repaint();
    }

    /**
     * Sets the comparison atlas for diff mode. Pass null to disable diff
     * mode and render the absolute atlas.
     *
     * @param compare comparison atlas snapshot or null
     */
    void setAtlasCompareData(WorkbenchActivationSnapshot compare) {
        this.atlasCompare = compare;
        repaint();
    }

    /**
     * Sets the output-overlay flag.
     *
     * @param value true to overlay output-head contribution
     */
    void setAtlasOverlay(boolean value) {
        if (atlasOverlay != value) {
            atlasOverlay = value;
            repaint();
        }
    }

    /**
     * Sets the grid-view flag and the list of atlas snapshots to render.
     *
     * @param value true for grid view
     * @param entries variant atlas list (may be empty)
     */
    void setAtlasGrid(boolean value,
            List<WorkbenchNetworkPanel.NnueAtlasEntry> entries) {
        this.atlasGrid = value;
        this.atlasGridEntries = entries == null ? new ArrayList<>() : entries;
        invalidate();
        revalidate();
        repaint();
    }

    /**
     * Sets the selected board square for bidirectional linking. Pass -1 to
     * clear the selection.
     *
     * @param square 0..63 LERF index or -1
     */
    void setSelectedBoardSquare(int square) {
        this.selectedBoardSquare = (square >= 0 && square < 64) ? square : -1;
        repaint();
    }

    // ---------- Scrollable plumbing ----------

    /**
     * Returns the preferred viewport size for scroll panes.
     *
     * @return preferred viewport size
     */
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    /**
     * Returns the unit scroll increment.
     *
     * @param visibleRect visible viewport rectangle
     * @param orientation scroll orientation
     * @param direction scroll direction
     * @return unit increment in pixels
     */
    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 24;
    }

    /**
     * Returns the block scroll increment.
     *
     * @param visibleRect visible viewport rectangle
     * @param orientation scroll orientation
     * @param direction scroll direction
     * @return block increment in pixels
     */
    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(48, (orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width) - 48);
    }

    /**
     * Returns whether the view tracks viewport width.
     *
     * @return true when width tracks the viewport
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    /**
     * Returns whether the view tracks viewport height.
     *
     * @return true when height tracks the viewport
     */
    @Override
    public boolean getScrollableTracksViewportHeight() {
        return !isAtlas() || !atlasGrid;
    }

    /**
     * Returns the preferred component size for trace and atlas modes.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        // Atlas reports a tall preferred size so the JScrollPane can expose
        // every neuron at a readable pitch instead of compressing them.
        if (isAtlas() && snapshot != null && atlasGrid && atlasZoomedSlot < 0) {
            int hidden = atlasHiddenSize();
            int rowH = pickAtlasRowHeight(hidden);
            int variants = atlasGrid ? Math.max(1, atlasGridEntries.size()) : 1;
            int width = Math.max(720, 200 + 11 * pickAtlasTileWidth(hidden) * variants
                    + 36 * (variants - 1));
            int top = 78; // header band
            int bottom = 16;
            int totalH = top + hidden * rowH + bottom;
            // Leave plenty of room for a wide tooltip popup.
            return new Dimension(width, totalH);
        }
        if (isAtlas() && snapshot != null) {
            return new Dimension(1120, 720);
        }
        if (isDetailed() && snapshot != null) {
            return new Dimension(1120, 720);
        }
        return new Dimension(720, 540);
    }

    /**
     * Returns the loaded network's hidden size, or 0 when unavailable.
     *
     * @return hidden size
     */
    private int atlasHiddenSize() {
        if (snapshot == null) {
            return 0;
        }
        int[] shape = snapshot.shape("nnue.atlas.weights");
        return shape != null && shape.length >= 1 ? shape[0] : 0;
    }

    /**
     * Picks a per-row pixel height for the scrolling atlas. Smaller for
     * very big networks so the user can still scroll the whole thing in
     * a reasonable time.
     *
     * @param hidden hidden-layer dimension
     * @return row height in pixels
     */
    private static int pickAtlasRowHeight(int hidden) {
        if (hidden >= 512) {
            return 24;
        }
        if (hidden >= 256) {
            return 30;
        }
        return 40;
    }

    /**
     * Picks a per-tile pixel width matching the row height.
     *
     * @param hidden hidden-layer dimension
     * @return tile width
     */
    private static int pickAtlasTileWidth(int hidden) {
        return pickAtlasRowHeight(hidden);
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
     * Handles a mouse click. Clicking an already-selected slot or feature
     * toggles it off so the user can return to the overview. Clicking empty
     * space outside any hit region also clears the current selection.
     *
     * @param x x
     * @param y y
     */
    @Override
    protected void onClick(int x, int y) {
        if (isDetailed() && visibleSlots.length > 0) {
            Rectangle body = new Rectangle(PAD, BODY_TOP, getWidth() - 2 * PAD, getHeight() - BODY_TOP - PAD);
            int boardSide = Math.min(280, Math.max(190, Math.min(300, body.width / 4)));
            int graphTop = body.y + TRACE_HEADER_H + TRACE_RIBBON_H + 22;
            Rectangle wire = new Rectangle(body.x, graphTop,
                    body.width - boardSide - 20, body.height - (graphTop - body.y));
            Layout layout = layout(wire);
            // Pick the single nearest node. The old hit test accepted any node
            // within twice its radius, so densely-packed slot rows had
            // overlapping hit zones with no gap — a click could never land on
            // blank space, so the zoom selection felt stuck.
            int nearest = -1;
            long nearestDist = Long.MAX_VALUE;
            int[] cxs = { layout.accumCx, layout.clippedCx, layout.contribCx };
            for (int i = 0; i < visibleSlots.length; ++i) {
                int cy = layout.startY + i * layout.slotPitch;
                for (int cx : cxs) {
                    long dx = x - cx;
                    long dy = y - cy;
                    long dist = dx * dx + dy * dy;
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = i;
                    }
                }
            }
            // A threshold below half the row pitch keeps a dead zone between
            // rows, so a click on blank wire space deselects instead of sticking.
            int hitRadius = Math.max(layout.slotRadius + 2, layout.slotPitch / 2 - 1);
            if (nearest >= 0 && nearestDist <= (long) hitRadius * hitRadius) {
                selectedSlot = (selectedSlot == nearest) ? -1 : nearest;
                repaint();
                return;
            }
            if (wire.contains(x, y) && selectedSlot >= 0) {
                selectedSlot = -1;
                repaint();
                return;
            }
        }
        WorkbenchHitRegions.Region r = hitRegions.hitTest(x, y);
        if (r == null) {
            if (selectedSlot >= 0 || selectedFeature >= 0 || atlasSelected >= 0 || selectedBoardSquare >= 0) {
                selectedSlot = -1;
                selectedFeature = -1;
                atlasSelected = -1;
                selectedBoardSquare = -1;
                repaint();
            }
            return;
        }
        // Mini-board click → set the bidirectional-link square.
        if (r.title != null && r.title.startsWith("Current position")) {
            int fileLocal = (int) (((x - r.bounds.x) / (double) r.bounds.width) * 8);
            int drawRank = (int) (((y - r.bounds.y) / (double) r.bounds.height) * 8);
            if (fileLocal >= 0 && fileLocal < 8 && drawRank >= 0 && drawRank < 8) {
                int sq = (7 - drawRank) * 8 + fileLocal;
                selectedBoardSquare = (selectedBoardSquare == sq) ? -1 : sq;
                repaint();
                return;
            }
        }
        if (r.title != null && r.title.startsWith("Slot ")) {
            int slot = parseSlotNumber(r.title);
            if (slot >= 0) {
                if (isAtlas()) {
                    atlasSelected = slot;
                    atlasZoomedSlot = -1;
                } else {
                    atlasSelected = (atlasSelected == slot) ? -1 : slot;
                }
                repaint();
                if (isAtlas()) {
                    return;
                }
            }
        }
        if (isAtlas() && r.title != null && "atlas:back".equals(r.title)) {
            atlasZoomedSlot = -1;
            atlasSelected = -1;
            repaint();
            return;
        }
        if (isAtlas() && r.title != null && r.title.startsWith("Zoom slot ")) {
            int slot = parseFirstInteger(r.title, "Zoom slot ".length());
            if (slot >= 0) {
                atlasSelected = slot;
                atlasZoomedSlot = slot;
                repaint();
                return;
            }
        }
        if (isAtlas() && r.title != null && r.title.startsWith("Plane ")) {
            int plane = parseFirstInteger(r.title, "Plane ".length());
            if (plane >= 0) {
                atlasSelectedPlane = plane;
                repaint();
                return;
            }
        }
        if (r.title != null && r.title.startsWith("Feature #")) {
            int idx = parseFeatureIndex(r.title);
            selectedFeature = (selectedFeature == idx) ? -1 : idx;
            repaint();
            if (isAbstract()) {
                return;
            }
        }
        if (isAbstract()) {
            return;
        }
        if (inspector != null) {
            inspector.inspect(r, snapshot);
        } else if (r.hasData()) {
            WorkbenchInspectorDialog.shared(this).inspect(r, snapshot);
        }
    }

    /**
     * Parses the slot index from an atlas tile region title of the form
     * "Slot 12 · own pawn".
     *
     * @param title region title
     * @return slot index or -1 when not parseable
     */
    private static int parseSlotNumber(String title) {
        int start = "Slot ".length();
        return parseFirstInteger(title, start);
    }

    /**
     * Parses the first integer beginning at a title offset.
     *
     * @param title source title
     * @param start start offset
     * @return integer or -1 when not parseable
     */
    private static int parseFirstInteger(String title, int start) {
        int end = start;
        while (end < title.length() && Character.isDigit(title.charAt(end))) {
            end++;
        }
        if (end == start) {
            return -1;
        }
        try {
            return Integer.parseInt(title.substring(start, end));
        } catch (NumberFormatException ex) {
            return -1;
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
    @Override
    protected void paintEmpty(Graphics2D g, Rectangle bounds) {
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
    @Override
    protected void paintHeader(Graphics2D g, Rectangle bounds) {
        float[] aff = snapshot.data("nnue.output.affine");
        float[] cp = snapshot.data("nnue.output.centipawns");
        float[] indicesUs = snapshot.data("nnue.features.us.indices");
        float[] indicesThem = snapshot.data("nnue.features.them.indices");
        float[] clippedUs = snapshot.data("nnue.clipped.us");
        float[] transformed = snapshot.data("nnue.stockfish.transformed");
        float[] transformedUs = snapshot.data("nnue.stockfish.transformed.us");
        float[] fc0 = snapshot.data("nnue.stockfish.fc0.raw");
        float[] fc1Input = snapshot.data("nnue.stockfish.fc1.input");
        float[] fc1 = snapshot.data("nnue.stockfish.fc1.clipped");
        float[] psqt = snapshot.data("nnue.stockfish.psqt.cp");
        float affine = aff == null ? 0.0f : aff[0];
        float centipawns = cp == null ? 0.0f : cp[0];
        int activeUs = indicesUs == null ? 0 : indicesUs.length;
        int activeThem = indicesThem == null ? 0 : indicesThem.length;
        int slots = isStockfishSnapshot()
                ? (transformedUs == null ? 0 : transformedUs.length)
                : clippedUs == null ? 0 : clippedUs.length;
        Rectangle shell = new Rectangle(PAD, 8, Math.max(1, bounds.width - 2 * PAD), 48);
        g.setColor(WorkbenchTheme.PANEL_SOLID);
        g.fillRoundRect(shell.x, shell.y, shell.width, shell.height,
                WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
        g.setColor(WorkbenchTheme.LINE);
        g.drawRoundRect(shell.x, shell.y, shell.width - 1, shell.height - 1,
                WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
        g.setColor(WorkbenchTheme.ACCENT);
        g.fillRoundRect(shell.x, shell.y, 5, shell.height,
                WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);

        int titleW = Math.min(220, Math.max(150, shell.width / 4));
        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(15, Font.BOLD));
        g.drawString("NNUE inspector", shell.x + 14, shell.y + 19);
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
        FontMetrics versionMetrics = g.getFontMetrics();
        if (versionLabel != null && !versionLabel.isEmpty()) {
            g.drawString(WorkbenchUi.elide(versionLabel, versionMetrics, titleW - 18),
                    shell.x + 14, shell.y + 37);
        }

        int barW = Math.min(220, Math.max(150, shell.width / 5));
        Rectangle barRect = new Rectangle(shell.x + shell.width - barW - 12, shell.y + 15, barW, 18);
        int chipsX = shell.x + titleW + 12;
        int chipsRight = barRect.x - 12;
        int chipArea = Math.max(0, chipsRight - chipsX);
        String[] labels = isStockfishSnapshot()
                ? new String[] { "HalfKA", "PSQT", "stack" }
                : new String[] { "features", "affine", "slots" };
        String[] values = isStockfishSnapshot()
                ? new String[] {
                        activeUs + " / " + traceFeatureLanes(),
                        psqt == null ? "--" : String.format("%+.0f", psqt[0]),
                        stockfishStackSummary(transformed, transformedUs, fc0, fc1Input, fc1)
                }
                : new String[] {
                        activeUs + " / " + activeThem,
                        String.format("%+.2f", affine),
                        slots == 0 ? "--" : String.valueOf(slots)
                };
        Color[] accents = {
                WorkbenchTheme.STATUS_INFO_BORDER,
                affine >= 0.0f ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE,
                WorkbenchTheme.ACCENT
        };
        int chipCount = chipArea < 86 ? 0 : Math.min(labels.length, Math.max(1, (chipArea + 6) / 92));
        int chipW = chipCount == 0 ? 0 : Math.max(76, (chipArea - 6 * (chipCount - 1)) / chipCount);
        for (int i = 0; i < chipCount; i++) {
            Rectangle chip = new Rectangle(chipsX + i * (chipW + 6), shell.y + 10, chipW, 28);
            paintHeaderChip(g, chip, labels[i], values[i], accents[i]);
        }

        hitRegions.addInspectable(shell,
                "NNUE header",
                versionLabel == null ? "Loaded network info" : versionLabel,
                String.format("us %d active · them %d active", activeUs, activeThem),
                "nnue.features.us.indices", 0, 0, 0, activeUs + " active");
        WorkbenchTensorViz.drawHorizontalBar(g, barRect, centipawns, Math.max(80.0f, Math.abs(centipawns) * 1.4f),
                String.format("%+d cp", Math.round(centipawns)));
        hitRegions.addInspectable(barRect,
                "Final centipawn eval",
                "Network output after scaling. Positive favors side to move.",
                String.format("%+d cp · affine %+.2f", Math.round(centipawns), affine),
                "nnue.output.centipawns", 0, 0, 0, "1");
    }

    /**
     * Paints one compact metric chip in the NNUE header.
     *
     * @param g graphics
     * @param r chip bounds
     * @param label label
     * @param value value
     * @param accent accent strip colour
     */
    private static void paintHeaderChip(Graphics2D g, Rectangle r,
            String label, String value, Color accent) {
        g.setColor(WorkbenchTheme.ELEVATED_SOLID);
        g.fillRoundRect(r.x, r.y, r.width, r.height,
                WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
        g.setColor(WorkbenchTheme.LINE);
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1,
                WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
        g.setColor(accent == null ? WorkbenchTheme.ACCENT : accent);
        g.fillRoundRect(r.x + 3, r.y + 5, 3, r.height - 10, 3, 3);
        g.setFont(WorkbenchTheme.font(9, Font.BOLD));
        FontMetrics labelMetrics = g.getFontMetrics();
        g.setColor(WorkbenchTheme.MUTED);
        g.drawString(WorkbenchUi.elide(label, labelMetrics, r.width - 18), r.x + 11, r.y + 11);
        g.setFont(WorkbenchTheme.font(12, Font.BOLD));
        FontMetrics valueMetrics = g.getFontMetrics();
        g.setColor(WorkbenchTheme.TEXT);
        g.drawString(WorkbenchUi.elide(value, valueMetrics, r.width - 18), r.x + 11, r.y + 24);
    }

    /**
     * Builds the Stockfish stack dimension summary from the actual captured
     * tensor shapes.
     *
     * @param transformed full transformed vector
     * @param transformedUs side-to-move transformed half
     * @param fc0 FC0 raw output including fwd row
     * @param fc1Input FC1 input after squared/clipped split
     * @param fc1 FC1 clipped output
     * @return compact stack summary
     */
    private static String stockfishStackSummary(float[] transformed, float[] transformedUs,
            float[] fc0, float[] fc1Input, float[] fc1) {
        int input = safeLength(transformed);
        if (input == 0) {
            input = safeLength(transformedUs);
        }
        int fc0Hidden = Math.max(0, safeLength(fc0) - 1);
        int fc1In = safeLength(fc1Input);
        int fc1Out = safeLength(fc1);
        if (fc1In > 0) {
            return input + "->" + fc0Hidden + "+fwd->" + fc1In + "->" + fc1Out;
        }
        return input + "->" + fc0Hidden + "+fwd->" + fc1Out;
    }

    /**
     * Paints the Wikipedia-style weight atlas as a scrollable, one-row-per-
     * neuron strip. The component overrides {@link #getPreferredSize} so the
     * JScrollPane has scroll content; the renderer assumes it has the full
     * vertical canvas available rather than trying to shrink-fit.
     *
     * @param g graphics
     * @param body body rectangle
     */
    @Override
    protected void paintAtlas(Graphics2D g, Rectangle body) {
        float[] atlas = snapshot.data("nnue.atlas.weights");
        int[] atlasShape = snapshot.shape("nnue.atlas.weights");
        float[] output = snapshot.data("nnue.atlas.output");
        if (atlas == null || atlasShape == null || atlasShape.length < 3) {
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(13, Font.PLAIN));
            g.drawString("Atlas data unavailable.",
                    body.x + 12, body.y + 32);
            return;
        }
        int hidden = atlasShape[0];
        int planes = atlasShape[1];
        int squares = atlasShape[2];
        if (atlasZoomedSlot >= 0 && atlasZoomedSlot < hidden) {
            paintAtlasZoom(g, body, atlas, planes, squares, hidden);
            return;
        }

        // Grid-of-variants mode short-circuits the single-atlas renderer.
        if (atlasGrid && !atlasGridEntries.isEmpty()) {
            paintAtlasGrid(g, body, hidden, planes, squares);
            return;
        }

        // Diff mode: compare current against another variant by subtracting.
        // We render the resulting signed delta with the same colormap (the
        // diff itself carries the sign).
        float[] paintingData = atlas;
        String headerSubtitle = "every tile = one (slot, piece) king-averaged weight footprint · "
                + "click a tile to zoom · hover a tile for the per-square value";
        if (atlasCompare != null) {
            float[] compareAtlas = atlasCompare.data("nnue.atlas.weights");
            int[] compareShape = atlasCompare.shape("nnue.atlas.weights");
            if (compareAtlas != null && compareShape != null
                    && compareShape.length >= 3
                    && compareShape[0] == hidden
                    && compareShape[1] == planes
                    && compareShape[2] == squares) {
                paintingData = new float[atlas.length];
                for (int i = 0; i < atlas.length; i++) {
                    paintingData[i] = atlas[i] - compareAtlas[i];
                }
                headerSubtitle = "diff mode · current minus comparison · same colormap, but values are weight deltas";
            } else {
                headerSubtitle = "diff mode requested but comparison atlas has incompatible shape · showing absolute atlas";
            }
        }

        java.util.List<String> modeFlags = new java.util.ArrayList<>();
        if (atlasCompare != null) {
            modeFlags.add("DIFF");
        }
        if (atlasOverlay) {
            modeFlags.add("output overlay");
        }
        if (selectedBoardSquare >= 0) {
            modeFlags.add("focus " + WorkbenchTensorViz.squareLabel(selectedBoardSquare));
        }
        String composedSubtitle = headerSubtitle;
        if (!modeFlags.isEmpty()) {
            String flags = String.join(" · ", modeFlags);
            composedSubtitle = (composedSubtitle == null || composedSubtitle.isEmpty())
                    ? flags
                    : flags + "  ·  " + composedSubtitle;
        }
        paintAtlasBrowser(g, body, paintingData, atlas, output, hidden, planes, squares, composedSubtitle);
    }

    /**
     * Paints the default interactive atlas browser.
     */
    private void paintAtlasBrowser(Graphics2D g, Rectangle body, float[] paintingData,
            float[] rawAtlas, float[] output, int hidden, int planes, int squares,
            String subtitle) {
        atlasSelectedPlane = Math.max(0, Math.min(atlasSelectedPlane, Math.max(0, planes - 1)));
        Integer[] order = sortNeurons(rawAtlas, output, hidden, planes, squares);
        int selectedSlot = atlasSelected >= 0 && atlasSelected < hidden
                ? atlasSelected
                : order.length == 0 ? 0 : order[0];
        float[] perNeuronScale = computePerNeuronScale(paintingData, hidden, planes, squares);
        float[] overlayMag = computeOverlayMagnitudes(hidden);
        int headerH = 46;
        WorkbenchTensorViz.drawSectionHeader(g,
                new Rectangle(body.x, body.y, body.width, headerH),
                "slot atlas browser · " + hidden + " accumulator slots × " + planes + " piece planes",
                subtitle);
        Rectangle content = new Rectangle(body.x, body.y + headerH + 10,
                body.width, Math.max(1, body.height - headerH - 10));
        if (body.width < 900) {
            int galleryH = Math.min(210, Math.max(120, content.height / 3));
            int explainH = Math.min(220, Math.max(150, content.height / 3));
            Rectangle gallery = new Rectangle(content.x, content.y, content.width, galleryH);
            Rectangle detail = new Rectangle(content.x, gallery.y + gallery.height + 10,
                    content.width, Math.max(1, content.height - gallery.height - explainH - 20));
            Rectangle explain = new Rectangle(content.x, detail.y + detail.height + 10,
                    content.width, Math.max(1, explainH));
            paintAtlasSlotGallery(g, gallery, order, paintingData, output,
                    hidden, planes, squares, perNeuronScale, overlayMag, selectedSlot);
            paintAtlasSlotDetail(g, detail, paintingData, selectedSlot, planes, squares,
                    perNeuronScale[selectedSlot]);
            paintAtlasSlotExplanation(g, explain, paintingData, rawAtlas, output,
                    selectedSlot, planes, squares);
            return;
        }
        int galleryW = Math.min(420, Math.max(300, content.width / 3));
        int explainW = Math.min(340, Math.max(270, content.width / 4));
        int detailW = Math.max(260, content.width - galleryW - explainW - 20);
        Rectangle gallery = new Rectangle(content.x, content.y, galleryW, content.height);
        Rectangle detail = new Rectangle(gallery.x + gallery.width + 10, content.y,
                detailW, content.height);
        Rectangle explain = new Rectangle(detail.x + detail.width + 10, content.y,
                Math.max(1, content.x + content.width - detail.x - detail.width - 10), content.height);
        paintAtlasSlotGallery(g, gallery, order, paintingData, output,
                hidden, planes, squares, perNeuronScale, overlayMag, selectedSlot);
        paintAtlasSlotDetail(g, detail, paintingData, selectedSlot, planes, squares,
                perNeuronScale[selectedSlot]);
        paintAtlasSlotExplanation(g, explain, paintingData, rawAtlas, output,
                selectedSlot, planes, squares);
    }

    /**
     * Paints the sorted slot thumbnail gallery.
     */
    private void paintAtlasSlotGallery(Graphics2D g, Rectangle r, Integer[] order,
            float[] atlas, float[] output, int hidden, int planes, int squares,
            float[] perNeuronScale, float[] overlayMag, int selectedSlot) {
        WorkbenchTensorViz.drawCard(g, r,
                "slot gallery",
                selectedBoardSquare >= 0
                        ? "ranked by " + WorkbenchTensorViz.squareLabel(selectedBoardSquare)
                        : "sorted by " + atlasSort,
                WorkbenchTheme.ACCENT);
        Rectangle inner = new Rectangle(r.x + 10, r.y + 38,
                Math.max(1, r.width - 20), Math.max(1, r.height - 48));
        int cardW = Math.max(82, Math.min(118, inner.width / Math.max(1, inner.width / 96)));
        int cardH = 78;
        int gap = 8;
        int cols = Math.max(1, (inner.width + gap) / (cardW + gap));
        int rows = Math.max(1, (inner.height + gap) / (cardH + gap));
        int show = Math.min(order.length, rows * cols);
        float overlayMax = maxAbs(overlayMag);
        for (int i = 0; i < show; i++) {
            int slot = order[i];
            int col = i % cols;
            int row = i / cols;
            Rectangle card = new Rectangle(inner.x + col * (cardW + gap),
                    inner.y + row * (cardH + gap), cardW, cardH);
            boolean selected = slot == selectedSlot;
            g.setColor(selected ? WorkbenchTheme.SELECTION_SOLID : WorkbenchTheme.ELEVATED_SOLID);
            g.fillRoundRect(card.x, card.y, card.width, card.height,
                    WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
            g.setColor(selected ? WorkbenchTheme.ACCENT : WorkbenchTheme.LINE);
            g.drawRoundRect(card.x, card.y, card.width - 1, card.height - 1,
                    WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
            if (atlasOverlay && overlayMag != null && slot < overlayMag.length && overlayMax > 0.0f) {
                float t = Math.min(1.0f, Math.abs(overlayMag[slot]) / overlayMax);
                g.setColor(WorkbenchTheme.withAlpha(
                        overlayMag[slot] >= 0.0f ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE,
                        Math.round(160.0f * t)));
                g.fillRoundRect(card.x + 4, card.y + 4, 4, card.height - 8, 4, 4);
            }
            Rectangle thumb = new Rectangle(card.x + 8, card.y + 21,
                    Math.min(44, card.width - 16), Math.min(44, card.height - 30));
            paintAtlasCompositeTile(g, thumb, atlas, slot, planes, squares, perNeuronScale[slot]);
            if (selectedBoardSquare >= 0) {
                overlaySelectedSquare(g, thumb, selectedBoardSquare);
            }
            g.setColor(WorkbenchTheme.TEXT);
            g.setFont(WorkbenchTheme.font(11, Font.BOLD));
            g.drawString("#" + slot, card.x + 8, card.y + 15);
            g.setColor(valueAt(output, slot) >= 0.0f ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE);
            g.setFont(WorkbenchTheme.font(10, Font.BOLD));
            String value = atlasSlotBadge(output, slot, atlas, planes, squares);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(WorkbenchUi.elide(value, fm, Math.max(20, card.width - thumb.width - 18)),
                    thumb.x + thumb.width + 8, card.y + 38);
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(9, Font.PLAIN));
            String label = atlasSlotArchetype(atlas, slot, planes, squares);
            fm = g.getFontMetrics();
            g.drawString(WorkbenchUi.elide(label, fm, Math.max(20, card.width - thumb.width - 18)),
                    thumb.x + thumb.width + 8, card.y + 54);
            hitRegions.add(card,
                    "Slot " + slot + " · gallery",
                    "Select this accumulator slot in the atlas browser.",
                    atlasSlotDetail(output, slot, atlas, planes, squares));
        }
        if (show < order.length) {
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(10, Font.ITALIC));
            g.drawString("showing top " + show + " of " + hidden + " slots",
                    inner.x + 4, r.y + r.height - 8);
        }
    }

    /**
     * Paints the focused slot board and plane selector.
     */
    private void paintAtlasSlotDetail(Graphics2D g, Rectangle r, float[] atlas,
            int slot, int planes, int squares, float scale) {
        WorkbenchTensorViz.drawCard(g, r,
                "slot #" + slot + " atlas",
                atlasPlaneName(atlasSelectedPlane, planes) + " weights",
                WorkbenchTheme.ACCENT);
        Rectangle inner = new Rectangle(r.x + 12, r.y + 38,
                Math.max(1, r.width - 24), Math.max(1, r.height - 50));
        int chipGap = 5;
        int chipH = 24;
        int chipW = Math.max(28, Math.min(58, (inner.width - chipGap * Math.max(0, planes - 1)) / Math.max(1, planes)));
        for (int p = 0; p < planes; p++) {
            Rectangle chip = new Rectangle(inner.x + p * (chipW + chipGap), inner.y, chipW, chipH);
            boolean selected = p == atlasSelectedPlane;
            g.setColor(selected ? WorkbenchTheme.SELECTION_SOLID : WorkbenchTheme.ELEVATED_SOLID);
            g.fillRoundRect(chip.x, chip.y, chip.width, chip.height, WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
            g.setColor(selected ? WorkbenchTheme.ACCENT : WorkbenchTheme.LINE);
            g.drawRoundRect(chip.x, chip.y, chip.width - 1, chip.height - 1,
                    WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
            g.setColor(WorkbenchTheme.TEXT);
            g.setFont(WorkbenchTheme.font(11, Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            String label = atlasPlaneLabel(p, planes);
            g.drawString(label, chip.x + Math.max(4, (chip.width - fm.stringWidth(label)) / 2), chip.y + 16);
            hitRegions.add(chip,
                    "Plane " + p + " · " + atlasPlaneName(p, planes),
                    "Show this piece plane for the selected slot.",
                    atlasPlaneName(p, planes));
        }
        int boardTop = inner.y + chipH + 14;
        int side = Math.max(80, Math.min(inner.width,
                Math.max(80, inner.y + inner.height - boardTop - 58)));
        Rectangle board = new Rectangle(inner.x + Math.max(0, (inner.width - side) / 2),
                boardTop, side, side);
        int offset = (slot * planes + atlasSelectedPlane) * squares;
        WorkbenchTensorViz.drawMiniBoard(g, board);
        paintAtlasTileDense(g, board, atlas, offset, Math.max(1e-6f, scale), false);
        Graphics2D pieceGraphics = (Graphics2D) g.create();
        try {
            pieceGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f));
            WorkbenchTensorViz.drawPositionPieces(pieceGraphics, board, fen);
        } finally {
            pieceGraphics.dispose();
        }
        if (selectedBoardSquare >= 0) {
            WorkbenchTensorViz.drawBoardSquareRing(g, board, selectedBoardSquare, WorkbenchTheme.ACCENT);
        }
        hitRegions.addInspectable(board,
                "Slot " + slot + " · " + atlasPlaneName(atlasSelectedPlane, planes),
                "Selected slot/piece-plane weight board.",
                selectedBoardSquare >= 0
                        ? selectedSquareValue(atlas, offset, selectedBoardSquare)
                        : String.format("max |w| %.3f", scale),
                "nnue.atlas.weights",
                offset, squares, 0, "8x8");

        int footerY = board.y + board.height + 14;
        if (footerY < inner.y + inner.height - 22) {
            Rectangle zoom = new Rectangle(inner.x, footerY,
                    Math.min(150, inner.width), 24);
            WorkbenchTensorViz.drawInfoChip(g, zoom, "open", "all planes", WorkbenchTheme.ACCENT);
            hitRegions.add(zoom,
                    "Zoom slot " + slot,
                    "Open the full all-piece-plane view for this slot.",
                    "slot #" + slot);
            if (selectedBoardSquare >= 0) {
                g.setColor(WorkbenchTheme.MUTED);
                g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
                g.drawString(WorkbenchTensorViz.squareLabel(selectedBoardSquare) + "  "
                        + selectedSquareValue(atlas, offset, selectedBoardSquare),
                        zoom.x + zoom.width + 12, footerY + 16);
            }
        }
    }

    /**
     * Paints the right-side explanation pane for a slot.
     */
    private void paintAtlasSlotExplanation(Graphics2D g, Rectangle r, float[] paintingData,
            float[] rawAtlas, float[] output, int slot, int planes, int squares) {
        WorkbenchTensorViz.drawCard(g, r,
                "slot explanation",
                atlasSlotArchetype(rawAtlas, slot, planes, squares),
                WorkbenchTheme.ACCENT);
        Rectangle inner = new Rectangle(r.x + 12, r.y + 42,
                Math.max(1, r.width - 24), Math.max(1, r.height - 54));
        int y = inner.y;
        float[] contribution = totalContribution();
        float contrib = valueAt(contribution, slot);
        float out = valueAt(output, slot);
        y = drawAtlasMetricLine(g, inner, y, "current contribution", String.format("%+.3f", contrib),
                contrib >= 0.0f ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE);
        y = drawAtlasMetricLine(g, inner, y, "output weight", String.format("%+.3f", out),
                out >= 0.0f ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE);
        int topPlane = strongestAtlasPlane(rawAtlas, slot, planes, squares);
        y = drawAtlasMetricLine(g, inner, y, "dominant plane",
                topPlane >= 0 ? atlasPlaneName(topPlane, planes) : "-", WorkbenchTheme.ACCENT);
        int offset = (slot * planes + atlasSelectedPlane) * squares;
        int posSq = strongestSquare(paintingData, offset, squares, true);
        int negSq = strongestSquare(paintingData, offset, squares, false);
        y = drawAtlasMetricLine(g, inner, y, "strongest + square",
                squareValueLabel(paintingData, offset, posSq), WorkbenchTensorViz.POSITIVE);
        y = drawAtlasMetricLine(g, inner, y, "strongest - square",
                squareValueLabel(paintingData, offset, negSq), WorkbenchTensorViz.NEGATIVE);
        y += 8;
        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(11, Font.BOLD));
        g.drawString("top active features feeding this slot", inner.x, y + 12);
        y += 20;
        drawAtlasTopFeatureInputs(g, new Rectangle(inner.x, y, inner.width,
                Math.max(1, inner.y + inner.height - y)), slot);
    }

    /**
     * Draws one metric row in the atlas explanation pane.
     */
    private static int drawAtlasMetricLine(Graphics2D g, Rectangle bounds, int y,
            String label, String value, Color accent) {
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(10, Font.BOLD));
        g.drawString(label, bounds.x, y + 11);
        g.setColor(accent == null ? WorkbenchTheme.TEXT : accent);
        g.setFont(WorkbenchTheme.font(12, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(WorkbenchUi.elide(value, fm, Math.max(20, bounds.width - 120)),
                bounds.x + 120, y + 12);
        return y + 22;
    }

    /**
     * Draws strongest active Half-KP rows feeding one atlas slot.
     */
    private void drawAtlasTopFeatureInputs(Graphics2D g, Rectangle r, int slot) {
        float[] indices = snapshot.data("nnue.features.us.indices");
        float[] weights = snapshot.data("nnue.features.us.weights");
        float[] impact = snapshot.data("nnue.features.us.impact");
        int[] shape = snapshot.shape("nnue.features.us.weights");
        int stride = shape != null && shape.length >= 2 ? shape[1] : 0;
        if (indices == null || weights == null || stride <= slot) {
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
            g.drawString("No active feature rows available.", r.x, r.y + 16);
            return;
        }
        Integer[] rows = new Integer[indices.length];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = i;
        }
        java.util.Arrays.sort(rows, (a, b) -> Float.compare(
                Math.abs(valueAt(weights, b * stride + slot)),
                Math.abs(valueAt(weights, a * stride + slot))));
        int rowH = 28;
        int count = Math.min(rows.length, Math.max(0, r.height / rowH));
        for (int i = 0; i < count; i++) {
            int row = rows[i];
            int featureIdx = Math.round(indices[row]);
            float weight = valueAt(weights, row * stride + slot);
            float featureImpact = valueAt(impact, row);
            Rectangle line = new Rectangle(r.x, r.y + i * rowH, r.width, rowH - 3);
            g.setColor(i % 2 == 0 ? WorkbenchTheme.PANEL_SOLID : WorkbenchTheme.ELEVATED_SOLID);
            g.fillRect(line.x, line.y, line.width, line.height);
            int glyph = Math.min(22, line.height - 2);
            HalfKpFeature feature = decodeHalfKpFeature(featureIdx, sideToMoveWhite());
            WorkbenchTensorViz.drawHalfKpGlyph(g,
                    new Rectangle(line.x + 2, line.y + (line.height - glyph) / 2, glyph, glyph),
                    feature.kingSquare, feature.pieceCode, feature.pieceSquare);
            g.setColor(WorkbenchTheme.TEXT);
            g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
            FontMetrics fm = g.getFontMetrics();
            String label = decodeUsHalfKP(featureIdx);
            int valueW = 86;
            g.drawString(WorkbenchUi.elide(label, fm, Math.max(20, line.width - valueW - glyph - 12)),
                    line.x + glyph + 8, line.y + 16);
            g.setColor(weight >= 0.0f ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE);
            g.setFont(WorkbenchTheme.font(10, Font.BOLD));
            g.drawString(String.format("%+.3f", weight), line.x + line.width - valueW, line.y + 12);
            g.setColor(featureImpact >= 0.0f ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE);
            g.drawString(String.format("%+.1fcp", featureImpact), line.x + line.width - valueW, line.y + 24);
            hitRegions.add(line,
                    "Feature #" + featureIdx + "  " + label,
                    "Active feature input to atlas slot #" + slot,
                    String.format("slot weight %+.3f · impact %+.1f cp", weight, featureImpact));
        }
    }

    /**
     * Paints a composite slot thumbnail using the strongest signed plane value
     * on each square.
     */
    private static void paintAtlasCompositeTile(Graphics2D g, Rectangle r, float[] atlas,
            int slot, int planes, int squares, float scale) {
        if (squares != 64) {
            WorkbenchTensorViz.drawEmpty(g, r);
            return;
        }
        float[] composite = new float[64];
        for (int sq = 0; sq < 64; sq++) {
            float best = 0.0f;
            for (int p = 0; p < planes; p++) {
                float value = valueAt(atlas, (slot * planes + p) * squares + sq);
                if (Math.abs(value) > Math.abs(best)) {
                    best = value;
                }
            }
            composite[sq] = best;
        }
        WorkbenchTensorViz.drawHeatmap(g, r, composite, 8, 8, Math.max(1e-6f, scale), true);
    }

    /**
     * Returns a short slot badge for the gallery.
     */
    private static String atlasSlotBadge(float[] output, int slot, float[] atlas, int planes, int squares) {
        if (output != null && slot < output.length) {
            return String.format("%+.2f", output[slot]);
        }
        return String.format("%.2f", atlasSlotMagnitude(atlas, slot, planes, squares));
    }

    /**
     * Returns a detail tooltip string for one atlas slot.
     */
    private static String atlasSlotDetail(float[] output, int slot, float[] atlas, int planes, int squares) {
        return String.format("output %+.3f · magnitude %.3f",
                valueAt(output, slot), atlasSlotMagnitude(atlas, slot, planes, squares));
    }

    /**
     * Heuristic label for a slot based on plane concentration and sparsity.
     */
    private static String atlasSlotArchetype(float[] atlas, int slot, int planes, int squares) {
        int plane = strongestAtlasPlane(atlas, slot, planes, squares);
        float sparsity = atlasSlotSparsity(atlas, slot, planes, squares);
        String density = sparsity > 0.78f ? "sparse" : sparsity < 0.45f ? "broad" : "focused";
        return density + " · " + (plane >= 0 ? atlasPlaneName(plane, planes) : "mixed");
    }

    /**
     * Returns the slot's mean absolute atlas weight.
     */
    private static float atlasSlotMagnitude(float[] atlas, int slot, int planes, int squares) {
        int base = slot * planes * squares;
        int span = planes * squares;
        float sum = 0.0f;
        for (int i = 0; i < span; i++) {
            sum += Math.abs(valueAt(atlas, base + i));
        }
        return span == 0 ? 0.0f : sum / span;
    }

    /**
     * Returns approximate sparsity for one slot.
     */
    private static float atlasSlotSparsity(float[] atlas, int slot, int planes, int squares) {
        int base = slot * planes * squares;
        int span = planes * squares;
        float max = 1e-6f;
        for (int i = 0; i < span; i++) {
            max = Math.max(max, Math.abs(valueAt(atlas, base + i)));
        }
        float threshold = max * 0.2f;
        int near = 0;
        for (int i = 0; i < span; i++) {
            if (Math.abs(valueAt(atlas, base + i)) < threshold) {
                near++;
            }
        }
        return span == 0 ? 0.0f : near / (float) span;
    }

    /**
     * Returns the plane with most absolute mass for one slot.
     */
    private static int strongestAtlasPlane(float[] atlas, int slot, int planes, int squares) {
        int best = -1;
        float bestMass = -1.0f;
        for (int p = 0; p < planes; p++) {
            float sum = 0.0f;
            int base = (slot * planes + p) * squares;
            for (int sq = 0; sq < squares; sq++) {
                sum += Math.abs(valueAt(atlas, base + sq));
            }
            if (sum > bestMass) {
                bestMass = sum;
                best = p;
            }
        }
        return best;
    }

    /**
     * Returns the strongest positive or negative square in one plane.
     */
    private static int strongestSquare(float[] data, int offset, int squares, boolean positive) {
        int best = -1;
        float bestValue = positive ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        for (int sq = 0; sq < squares; sq++) {
            float value = valueAt(data, offset + sq);
            if (positive ? value > bestValue : value < bestValue) {
                bestValue = value;
                best = sq;
            }
        }
        return best;
    }

    /**
     * Formats one square/value pair.
     */
    private static String squareValueLabel(float[] data, int offset, int square) {
        if (square < 0) {
            return "-";
        }
        return WorkbenchTensorViz.squareLabel(square) + " " + String.format("%+.3f", valueAt(data, offset + square));
    }

    /**
     * Formats the selected-square value.
     */
    private static String selectedSquareValue(float[] data, int offset, int square) {
        return square < 0 ? "-" : String.format("%s %+.3f",
                WorkbenchTensorViz.squareLabel(square), valueAt(data, offset + square));
    }

    /**
     * Computes per-neuron max-abs scale for the atlas.
     *
     * @param data atlas data (or diff data)
     * @param hidden hidden size
     * @param planes piece planes
     * @param squares squares per plane
     * @return per-neuron scale array
     */
    private static float[] computePerNeuronScale(float[] data, int hidden, int planes, int squares) {
        float[] out = new float[hidden];
        int span = planes * squares;
        for (int h = 0; h < hidden; h++) {
            float m = 0.0f;
            int b = h * span;
            int end = b + span;
            for (int i = b; i < end; i++) {
                float a = Math.abs(data[i]);
                if (a > m) {
                    m = a;
                }
            }
            out[h] = Math.max(1e-6f, m);
        }
        return out;
    }

    /**
     * Computes per-neuron overlay magnitude — the current activation × output
     * weight if both are available, falling back to |output| only when not.
     *
     * @param hidden hidden size
     * @return per-slot signed magnitude or null when nothing useful is known
     */
    private float[] computeOverlayMagnitudes(int hidden) {
        if (snapshot == null) {
            return null;
        }
        float[] contrib = totalContribution();
        if (contrib != null && contrib.length == hidden) {
            return contrib;
        }
        float[] output = snapshot.data("nnue.atlas.output");
        if (output == null || output.length != hidden) {
            return null;
        }
        // Without per-position contribution we can still tint by static
        // output weight magnitude; the sign degrades to "+".
        return output;
    }

    /**
     * Ranks neurons by the currently-selected sort metric and returns the
     * permutation (length hidden).
     *
     * @param atlas atlas weights
     * @param output per-neuron output magnitudes
     * @param hidden hidden size
     * @param planes piece planes
     * @param squares squares per plane
     * @return permutation in descending preference
     */
    private Integer[] sortNeurons(float[] atlas, float[] output, int hidden, int planes, int squares) {
        Integer[] order = new Integer[hidden];
        for (int i = 0; i < hidden; i++) {
            order[i] = i;
        }
        if (selectedBoardSquare >= 0 && selectedBoardSquare < squares) {
            java.util.Arrays.sort(order, (a, b) -> Float.compare(
                    atlasSquareFocus(atlas, b, planes, squares, selectedBoardSquare),
                    atlasSquareFocus(atlas, a, planes, squares, selectedBoardSquare)));
            return order;
        }
        switch (atlasSort) {
            case "slot index" -> { /* keep natural order */ }
            case "sparsity" -> {
                // Sparsity = fraction of near-zero cells. Higher = more focused.
                float[] sparsity = new float[hidden];
                int span = planes * squares;
                for (int h = 0; h < hidden; h++) {
                    float m = 1e-6f;
                    int b = h * span;
                    for (int i = 0; i < span; i++) {
                        float a = Math.abs(atlas[b + i]);
                        if (a > m) {
                            m = a;
                        }
                    }
                    int near = 0;
                    float thr = 0.2f * m;
                    for (int i = 0; i < span; i++) {
                        if (Math.abs(atlas[b + i]) < thr) {
                            near++;
                        }
                    }
                    sparsity[h] = near / (float) span;
                }
                java.util.Arrays.sort(order,
                        (a, b) -> Float.compare(sparsity[b], sparsity[a]));
            }
            case "piece focus" -> {
                // Rank by how concentrated the neuron's mass is on a single
                // piece plane (max plane / total).
                float[] focus = new float[hidden];
                for (int h = 0; h < hidden; h++) {
                    float total = 0.0f;
                    float maxPlane = 0.0f;
                    for (int p = 0; p < planes; p++) {
                        float s = 0.0f;
                        int b = (h * planes + p) * squares;
                        for (int i = 0; i < squares; i++) {
                            s += Math.abs(atlas[b + i]);
                        }
                        total += s;
                        if (s > maxPlane) {
                            maxPlane = s;
                        }
                    }
                    focus[h] = total > 0.0f ? maxPlane / total : 0.0f;
                }
                java.util.Arrays.sort(order,
                        (a, b) -> Float.compare(focus[b], focus[a]));
            }
            default -> {
                // magnitude (output-weight magnitude or atlas magnitude)
                if (output != null && output.length == hidden) {
                    java.util.Arrays.sort(order,
                            (a, b) -> Float.compare(Math.abs(output[b]), Math.abs(output[a])));
                }
            }
        }
        return order;
    }

    /**
     * Returns total absolute sensitivity to one board square across planes.
     */
    private static float atlasSquareFocus(float[] atlas, int slot, int planes, int squares, int square) {
        float sum = 0.0f;
        for (int p = 0; p < planes; p++) {
            sum += Math.abs(valueAt(atlas, (slot * planes + p) * squares + square));
        }
        return sum;
    }

    /**
     * Paints the multi-variant grid view: one small atlas thumbnail per
     * discovered NNUE network, laid out left-to-right.
     *
     * @param g graphics
     * @param body body rectangle
     * @param hidden expected hidden size of the primary atlas
     * @param planes expected piece-plane count
     * @param squares expected per-plane square count
     */
    private void paintAtlasGrid(Graphics2D g, Rectangle body, int hidden, int planes, int squares) {
        int headerH = 38;
        WorkbenchTensorViz.drawSectionHeader(g,
                new Rectangle(body.x, body.y, body.width, headerH),
                "variant grid · " + atlasGridEntries.size() + " atlases",
                "every column = one .nnue file's atlas at thumbnail size · hover a tile for value");
        int variants = atlasGridEntries.size();
        int gap = 24;
        int colW = (body.width - gap * (variants + 1)) / variants;
        int colTop = body.y + headerH + 18;
        int colH = body.height - headerH - 36;
        for (int v = 0; v < variants; v++) {
            WorkbenchNetworkPanel.NnueAtlasEntry entry = atlasGridEntries.get(v);
            int colX = body.x + gap + v * (colW + gap);
            // Variant label
            g.setColor(WorkbenchTheme.TEXT);
            g.setFont(WorkbenchTheme.font(11, Font.BOLD));
            g.drawString(entry.label, colX, colTop - 4);
            Rectangle col = new Rectangle(colX, colTop, colW, colH);
            paintSingleAtlasThumb(g, col, entry.atlas);
        }
    }

    /**
     * Paints one variant's atlas into the supplied rectangle as a thumbnail.
     *
     * @param g graphics
     * @param r area
     * @param snap atlas snapshot
     */
    private void paintSingleAtlasThumb(Graphics2D g, Rectangle r, WorkbenchActivationSnapshot snap) {
        float[] atlas = snap.data("nnue.atlas.weights");
        int[] atlasShape = snap.shape("nnue.atlas.weights");
        if (atlas == null || atlasShape == null || atlasShape.length < 3) {
            g.setColor(WorkbenchTheme.MUTED);
            g.drawString("(no atlas)", r.x, r.y + 14);
            return;
        }
        int hidden = atlasShape[0];
        int planes = atlasShape[1];
        int squares = atlasShape[2];
        int planeW = Math.max(2, r.width / planes);
        int rowH = Math.max(2, r.height / hidden);
        float[] perNeuron = computePerNeuronScale(atlas, hidden, planes, squares);
        for (int h = 0; h < hidden; h++) {
            int y = r.y + h * rowH;
            if (y + rowH > r.y + r.height) {
                break;
            }
            float scale = perNeuron[h];
            for (int p = 0; p < planes; p++) {
                Rectangle tile = new Rectangle(r.x + p * planeW, y, planeW, rowH);
                int offset = (h * planes + p) * squares;
                paintAtlasTileDense(g, tile, atlas, offset, scale, false);
            }
        }
        // Frame
        g.setColor(WorkbenchTheme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
    }

    /**
     * Lightly outlines one board square inside an atlas tile so the user can
     * see which cell maps to the currently-selected board square.
     *
     * @param g graphics
     * @param tile tile rectangle
     * @param square 0..63 LERF index
     */
    private static void overlaySelectedSquare(Graphics2D g, Rectangle tile, int square) {
        if (tile.width < 8 || tile.height < 8) {
            return;
        }
        int file = square & 7;
        int rank = square >> 3;
        int drawRank = 7 - rank;
        double cw = tile.width / 8.0;
        double ch = tile.height / 8.0;
        int cx = (int) Math.floor(tile.x + file * cw);
        int cy = (int) Math.floor(tile.y + drawRank * ch);
        int cw2 = (int) Math.ceil(cw + 1);
        int ch2 = (int) Math.ceil(ch + 1);
        g.setColor(WorkbenchTheme.ACCENT);
        g.drawRect(cx, cy, cw2 - 1, ch2 - 1);
    }

    /**
     * Paints the architecture-diagram view: a schematic of the loaded
     * network's stages with the actual dimensions filled in, plus a hint
     * to switch into atlas/detailed for richer detail.
     *
     * @param g graphics
     * @param body body rectangle
     */
    @Override
    protected void paintDiagram(Graphics2D g, Rectangle body) {
        int headerH = 40;
        WorkbenchTensorViz.drawSectionHeader(g,
                new Rectangle(body.x, body.y, body.width, headerH),
                "architecture diagram",
                "schematic of the loaded NNUE · click a block to focus that layer in another view");
        int boxW = 200;
        int boxH = 90;
        int gap = 36;
        int hidden = atlasHiddenSize();
        int planes = 0;
        int squares = 0;
        int[] shape = snapshot == null ? null : snapshot.shape("nnue.atlas.weights");
        if (shape != null && shape.length >= 3) {
            planes = shape[1];
            squares = shape[2];
        }
        // Five boxes: input encoding → feature transformer → ReLU → output stack → eval
        String[] titles = {
                "input features",
                "feature transformer",
                "ReLU + scale",
                "output stack",
                "centipawn eval"
        };
        int activeUs = snapshot == null ? 0 : (snapshot.data("nnue.features.us.indices") == null
                ? 0 : snapshot.data("nnue.features.us.indices").length);
        int activeThem = snapshot == null ? 0 : (snapshot.data("nnue.features.them.indices") == null
                ? 0 : snapshot.data("nnue.features.them.indices").length);
        String[] subtitles = {
                planes == 0 ? "halfKP / halfKAv2_hm" : "halfKAv2_hm · " + planes + " planes × " + squares + " squares",
                "[features → " + hidden + " hidden]",
                "post-ReLU clipped activations",
                hidden + " → output (cp)",
                snapshot == null ? "" : String.format("active us=%d · them=%d", activeUs, activeThem)
        };
        int totalW = boxW * titles.length + gap * (titles.length - 1);
        int startX = body.x + (body.width - totalW) / 2;
        int y = body.y + headerH + 60;
        FontMetrics fmTitle = g.getFontMetrics(WorkbenchTheme.font(13, Font.BOLD));
        for (int i = 0; i < titles.length; i++) {
            int x = startX + i * (boxW + gap);
            // Box
            g.setColor(WorkbenchTheme.PANEL_SOLID);
            g.fillRoundRect(x, y, boxW, boxH, 14, 14);
            g.setColor(WorkbenchTheme.LINE);
            g.drawRoundRect(x, y, boxW, boxH, 14, 14);
            // Title
            g.setColor(WorkbenchTheme.TEXT);
            g.setFont(WorkbenchTheme.font(13, Font.BOLD));
            int tw = fmTitle.stringWidth(titles[i]);
            g.drawString(titles[i], x + (boxW - tw) / 2, y + 28);
            // Subtitle
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
            FontMetrics fmSub = g.getFontMetrics();
            int sw = fmSub.stringWidth(subtitles[i]);
            g.drawString(subtitles[i], x + (boxW - sw) / 2, y + 56);
            // Arrow to next
            if (i < titles.length - 1) {
                int ax1 = x + boxW;
                int ax2 = x + boxW + gap;
                int ay = y + boxH / 2;
                g.setColor(WorkbenchTheme.ACCENT);
                g.drawLine(ax1 + 2, ay, ax2 - 6, ay);
                int[] xs = { ax2 - 6, ax2 - 12, ax2 - 12 };
                int[] ys = { ay, ay - 5, ay + 5 };
                g.fillPolygon(xs, ys, 3);
            }
            hitRegions.add(new Rectangle(x, y, boxW, boxH),
                    "Layer · " + titles[i],
                    subtitles[i],
                    "switch into Atlas/Detailed for richer detail");
        }
        // Caption with current eval if we have one.
        float[] cp = snapshot == null ? null : snapshot.data("nnue.output.centipawns");
        if (cp != null) {
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(12, Font.ITALIC));
            String caption = String.format("eval %+d cp", Math.round(cp[0]));
            int cw = g.getFontMetrics().stringWidth(caption);
            g.drawString(caption, body.x + (body.width - cw) / 2, y + boxH + 28);
        }
    }

    /**
     * Paints the single-slot zoom view. Shows the focused neuron's 11 piece-
     * type tiles at large size plus the king-square preference heatmap on
     * the side. A clickable header strip returns to the mosaic.
     *
     * @param g graphics
     * @param body body rectangle
     * @param atlas flat atlas weights (shape [hidden, planes, squares])
     * @param planes piece-plane count
     * @param squares board-square count (64)
     * @param hidden total accumulator-slot count
     */
    private void paintAtlasZoom(Graphics2D g, Rectangle body, float[] atlas,
            int planes, int squares, int hidden) {
        int slot = atlasZoomedSlot;
        float[] king = snapshot.data("nnue.atlas.king");
        int headerH = 38;

        // Per-neuron scale.
        float scale = 1e-6f;
        int base = slot * planes * squares;
        int end = base + planes * squares;
        for (int i = base; i < end; i++) {
            float a = Math.abs(atlas[i]);
            if (a > scale) {
                scale = a;
            }
        }

        WorkbenchTensorViz.drawSectionHeader(g,
                new Rectangle(body.x, body.y, body.width, headerH),
                "slot #" + slot + " · zoom",
                "all 11 piece planes at full resolution · click anywhere to return to the atlas");
        hitRegions.add(new Rectangle(body.x, body.y, body.width, headerH),
                "atlas:back", "Return to atlas mosaic", "click to go back");

        int gap = 14;
        Rectangle area = new Rectangle(body.x + 6, body.y + headerH + 12,
                body.width - 12, body.height - headerH - 18);

        // Layout: 6 cols × 2 rows of piece tiles (11 planes + 1 king-map),
        // each tile a large 8x8 board heatmap with a label.
        int cols = 6;
        int rows = 2;
        int tileW = (area.width - gap * (cols + 1)) / cols;
        int tileH = (area.height - gap * (rows + 1) - 24) / rows;
        int side = Math.min(tileW, tileH);

        for (int p = 0; p < planes; p++) {
            int col = p % cols;
            int row = p / cols;
            int x = area.x + gap + col * (side + gap);
            int y = area.y + gap + row * (side + gap + 24);
            Rectangle tile = new Rectangle(x, y + 18, side, side);
            int offset = (slot * planes + p) * squares;
            // Labels
            g.setColor(WorkbenchTheme.TEXT);
            g.setFont(WorkbenchTheme.font(13, Font.BOLD));
            g.drawString(atlasPlaneName(p, planes), x, y + 14);
            paintAtlasTileDense(g, tile, atlas, offset, scale, false);
            hitRegions.addInspectable(tile,
                    "Slot " + slot + " · " + atlasPlaneName(p, planes),
                    "Click anywhere to return to the atlas mosaic",
                    String.format("max |w| %.3f", scale),
                    "nnue.atlas.weights",
                    offset, squares, 0, "8x8");
        }

        // King-square heatmap occupies the spare slot (planes index = 11).
        if (king != null && king.length >= (slot + 1) * squares) {
            int p = planes;
            int col = p % cols;
            int row = p / cols;
            int x = area.x + gap + col * (side + gap);
            int y = area.y + gap + row * (side + gap + 24);
            Rectangle tile = new Rectangle(x, y + 18, side, side);
            g.setColor(WorkbenchTheme.TEXT);
            g.setFont(WorkbenchTheme.font(13, Font.BOLD));
            g.drawString("king preference", x, y + 14);
            float kingScale = 1e-6f;
            int kBase = slot * squares;
            for (int i = 0; i < squares; i++) {
                float a = Math.abs(king[kBase + i]);
                if (a > kingScale) {
                    kingScale = a;
                }
            }
            paintAtlasTileDense(g, tile, king, kBase, kingScale, false);
            hitRegions.addInspectable(tile,
                    "Slot " + slot + " · king preference",
                    "Average weight per king-square (over pieces and squares)",
                    String.format("max |w| %.3f", kingScale),
                    "nnue.atlas.king",
                    kBase, squares, 0, "8x8");
        }

        // Hint at the bottom.
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(11, Font.ITALIC));
        g.drawString("slot " + slot + " of " + hidden + " · click any tile or the header to return",
                area.x + 6, body.y + body.height - 6);
    }

    /**
     * Returns the short column label for an atlas piece plane.
     *
     * @param plane plane index (0..planes-1)
     * @param totalPlanes total number of piece planes
     * @return short label
     */
    private static String atlasPlaneLabel(int plane, int totalPlanes) {
        char[] own = { 'P', 'N', 'B', 'R', 'Q' };
        char[] enemy = { 'p', 'n', 'b', 'r', 'q' };
        if (plane < 5) {
            return String.valueOf(own[plane]);
        }
        if (plane < 10) {
            return String.valueOf(enemy[plane - 5]);
        }
        return totalPlanes > 10 ? "K" : "";
    }

    /**
     * Returns the long human-readable name for an atlas piece plane.
     *
     * @param plane plane index
     * @param totalPlanes total piece planes
     * @return long name
     */
    private static String atlasPlaneName(int plane, int totalPlanes) {
        String[] own = { "own pawn", "own knight", "own bishop", "own rook", "own queen" };
        String[] enemy = { "enemy pawn", "enemy knight", "enemy bishop", "enemy rook", "enemy queen" };
        if (plane < 5) {
            return own[plane];
        }
        if (plane < 10) {
            return enemy[plane - 5];
        }
        return totalPlanes > 10 ? "kings" : ("plane " + plane);
    }

    /**
     * Paints a single dense atlas tile straight onto the canvas with no
     * border, reading 64 weights from a flat atlas array starting at the
     * given offset.
     *
     * @param g graphics
     * @param r tile rectangle
     * @param atlas flat atlas data
     * @param offset starting offset of this tile's 64 weights
     * @param scale max-abs scale
     * @param selected highlight ring when selected
     */
    private static void paintAtlasTileDense(Graphics2D g, Rectangle r, float[] atlas,
            int offset, float scale, boolean selected) {
        double cw = r.width / 8.0;
        double ch = r.height / 8.0;
        for (int sq = 0; sq < 64; sq++) {
            int file = sq & 7;
            int rank = sq >> 3;
            int drawRank = 7 - rank;
            int cellX = (int) Math.floor(r.x + file * cw);
            int cellY = (int) Math.floor(r.y + drawRank * ch);
            int cellW = (int) Math.ceil(cw + 1);
            int cellH = (int) Math.ceil(ch + 1);
            float v = atlas[offset + sq] / scale;
            if (v > 1.0f) {
                v = 1.0f;
            } else if (v < -1.0f) {
                v = -1.0f;
            }
            g.setColor(atlasRamp(v));
            g.fillRect(cellX, cellY, cellW, cellH);
        }
        if (selected) {
            g.setColor(WorkbenchTheme.ACCENT);
            g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            g.drawRect(r.x - 1, r.y - 1, r.width + 1, r.height + 1);
        }
    }

    /**
     * Atlas colormap: signed weights in [-1, 1] mapped to the shared
     * green/red diverging ramp used elsewhere (raise = green, lower = red),
     * on the workbench's light heat-zero background.
     *
     * @param v normalised value in [-1, 1]
     * @return colour
     */
    private static java.awt.Color atlasRamp(float v) {
        return WorkbenchTensorViz.signedRamp(v);
    }

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
        WorkbenchTensorViz.drawSectionHeader(g,
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
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(12, Font.PLAIN));
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
    private void drawRawGroupLabel(Graphics2D g, Rectangle body, int y, int rowH,
            String title, String detail) {
        g.setColor(WorkbenchTheme.LINE);
        g.drawLine(body.x, y + rowH - 1, body.x + body.width, y + rowH - 1);
        g.setFont(WorkbenchTheme.font(10, Font.BOLD));
        g.setColor(WorkbenchTheme.TEXT);
        g.drawString(title, body.x + 4, y + rowH / 2 + 4);
        g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
        g.setColor(WorkbenchTheme.MUTED);
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
     */
    private void drawRawFeatureRow(Graphics2D g, Rectangle body, int y, int rowH,
            int gridLeft, int gridW, float[] indices, float[] impact,
            float[] weights, int hidden, int sourceIndex, float scale,
            String dataKey, String sideLabel, boolean whitePerspective) {
        int featureIdx = Math.round(indices[sourceIndex]);
        float v = impact == null || sourceIndex >= impact.length ? 0.0f : impact[sourceIndex];
        int glyphSize = Math.max(7, Math.min(rowH - 2, 26));
        Rectangle glyph = new Rectangle(body.x + 4, y + (rowH - glyphSize) / 2,
                glyphSize, glyphSize);
        HalfKpFeature feature = decodeHalfKpFeature(featureIdx, whitePerspective);
        WorkbenchTensorViz.drawHalfKpGlyph(g, glyph,
                feature.kingSquare, feature.pieceCode, feature.pieceSquare);
        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
        String label = "#" + featureIdx + "  " + decodeHalfKP(featureIdx, whitePerspective);
        g.drawString(label, glyph.x + glyphSize + 6, y + rowH / 2 + 4);
        g.setColor(v >= 0 ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE);
        g.setFont(WorkbenchTheme.font(10, Font.BOLD));
        g.drawString(String.format("%+5.1f cp", v),
                gridLeft - 56, y + rowH / 2 + 4);

        float[] row = new float[hidden];
        int rowStart = sourceIndex * hidden;
        for (int s = 0; s < hidden && rowStart + s < weights.length; ++s) {
            row[s] = weights[rowStart + s];
        }
        Rectangle cell = new Rectangle(gridLeft, y + 1, gridW, Math.max(1, rowH - 2));
        WorkbenchTensorViz.drawHeatmap(g, cell, row, hidden, 1, scale, true);
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
    private int drawRawVectorRow(Graphics2D g, Rectangle body, int gridTop,
            int rowH, int gridLeft, int gridW, int row, String label,
            float[] values, int hidden, float scale, boolean signed,
            String dataKey, String detail) {
        if (values == null) {
            return row;
        }
        int y = gridTop + row * rowH;
        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(10, Font.BOLD));
        g.drawString(label, body.x + 4, y + rowH / 2 + 4);
        Rectangle cell = new Rectangle(gridLeft, y + 1, gridW, Math.max(1, rowH - 2));
        WorkbenchTensorViz.drawHeatmap(g, cell, values,
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
    private void paintOverviewSummary(Graphics2D g, Rectangle r) {
        WorkbenchTensorViz.drawCard(g, r, null, null, WorkbenchTheme.ACCENT);
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
                cp >= 0.0f ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE);
        x += chipW + chipGap;
        paintSummaryChip(g, new Rectangle(x, r.y + 9, chipW, chipH),
                "top raiser",
                raiser.valid() ? driverLabel(raiser) : "-",
                raiser.valid() ? String.format("%+.1f cp", raiser.impact()) : "none",
                WorkbenchTensorViz.POSITIVE);
        x += chipW + chipGap;
        paintSummaryChip(g, new Rectangle(x, r.y + 9, chipW, chipH),
                "top lowerer",
                lowerer.valid() ? driverLabel(lowerer) : "-",
                lowerer.valid() ? String.format("%+.1f cp", lowerer.impact()) : "none",
                WorkbenchTensorViz.NEGATIVE);
        if (chipCount > 3) {
            x += chipW + chipGap;
            String label = isStockfishSnapshot() ? "HalfKA" : "HalfKP";
            paintSummaryChip(g, new Rectangle(x, r.y + 9, chipW, chipH),
                    label,
                    activeUs + " / " + activeThem,
                    isStockfishSnapshot() ? stockfishStackShort() : "us / them active",
                    WorkbenchTheme.STATUS_INFO_BORDER);
        }
        hitRegions.addInspectable(r,
                "Overview summary",
                "Final score and strongest active feature drivers.",
                String.format("%+d cp · us %d · them %d", Math.round(cp), activeUs, activeThem),
                "nnue.output.centipawns", 0, 1, 0, "1");
    }

    /**
     * Paints one score summary chip.
     */
    private static void paintSummaryChip(Graphics2D g, Rectangle r, String label,
            String value, String detail, Color accent) {
        g.setColor(WorkbenchTheme.ELEVATED_SOLID);
        g.fillRoundRect(r.x, r.y, r.width, r.height, WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
        g.setColor(WorkbenchTheme.LINE);
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1,
                WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
        g.setColor(accent == null ? WorkbenchTheme.ACCENT : accent);
        g.fillRoundRect(r.x + 4, r.y + 7, 4, r.height - 14, 4, 4);
        g.setFont(WorkbenchTheme.font(10, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(WorkbenchTheme.MUTED);
        g.drawString(WorkbenchUi.elide(label, fm, Math.max(20, r.width - 18)),
                r.x + 15, r.y + 15);
        g.setFont(WorkbenchTheme.font(18, Font.BOLD));
        fm = g.getFontMetrics();
        g.setColor(WorkbenchTheme.TEXT);
        g.drawString(WorkbenchUi.elide(value, fm, Math.max(20, r.width - 18)),
                r.x + 15, r.y + 36);
        if (r.height >= 58) {
            g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
            fm = g.getFontMetrics();
            g.setColor(WorkbenchTheme.MUTED);
            g.drawString(WorkbenchUi.elide(detail, fm, Math.max(20, r.width - 18)),
                    r.x + 15, r.y + r.height - 10);
        }
    }

    /**
     * Paints the central signal stack: accumulator comparison and dense trunk.
     */
    private void paintOverviewSignal(Graphics2D g, Rectangle r) {
        int gap = 10;
        int accumulatorH = Math.max(118, Math.min((int) (r.height * 0.56), r.height - 116));
        Rectangle accumulator = new Rectangle(r.x, r.y, r.width, Math.max(1, accumulatorH));
        Rectangle trunk = new Rectangle(r.x, accumulator.y + accumulator.height + gap,
                r.width, Math.max(1, r.y + r.height - accumulator.y - accumulator.height - gap));
        paintAccumulatorOverview(g, accumulator);
        paintTrunkOverview(g, trunk);
    }

    /**
     * Paints a curated internal inspector for the selected or strongest feature.
     */
    private void paintFeatureInspector(Graphics2D g, Rectangle r) {
        FeatureDriver driver = selectedFeature >= 0
                ? driverForFeature(selectedFeature)
                : strongestDriverByAbs();
        Color accent = !driver.valid() ? WorkbenchTheme.ACCENT
                : driver.impact() >= 0.0f ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE;
        WorkbenchTensorViz.drawCard(g, r,
                driver.valid() ? "selected feature" : "feature inspector",
                driver.valid() ? "Half-KP active feature" : "no active feature",
                accent);
        Rectangle content = new Rectangle(r.x + 12, r.y + 42,
                Math.max(1, r.width - 24), Math.max(1, r.height - 54));
        if (!driver.valid()) {
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
            g.drawString("No active Half-KP feature in this snapshot.", content.x, content.y + 16);
            return;
        }

        HalfKpFeature feature = decodeHalfKpFeature(driver.featureIndex(), sideToMoveWhite());
        int glyph = Math.min(86, Math.max(48, Math.min(content.width / 3, content.height - 18)));
        Rectangle glyphRect = new Rectangle(content.x, content.y, glyph, glyph);
        WorkbenchTensorViz.drawHalfKpGlyph(g, glyphRect,
                feature.kingSquare, feature.pieceCode, feature.pieceSquare);
        int textX = glyphRect.x + glyphRect.width + 12;
        int textW = Math.max(1, content.x + content.width - textX);
        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(18, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(WorkbenchUi.elide(driverLabel(driver), fm, textW), textX, content.y + 19);
        g.setColor(accent);
        g.setFont(WorkbenchTheme.font(14, Font.BOLD));
        fm = g.getFontMetrics();
        g.drawString(WorkbenchUi.elide(String.format("impact %+.2f cp", driver.impact()), fm, textW),
                textX, content.y + 40);
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
        fm = g.getFontMetrics();
        String rank = driver.rank() > 0 ? "rank #" + driver.rank() + " by |impact|" : "active feature";
        g.drawString(WorkbenchUi.elide("#" + driver.featureIndex() + " · " + rank, fm, textW),
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
                g.setColor(WorkbenchTheme.TEXT);
                g.setFont(WorkbenchTheme.font(11, Font.BOLD));
                g.drawString("feature -> L1 weight row", content.x, heatY - 4);
                Rectangle heat = new Rectangle(content.x, heatY + 4, content.width,
                        Math.max(18, Math.min(42, content.y + content.height - heatY - 8)));
                WorkbenchTensorViz.drawHeatmap(g, heat, row, row.length, 1,
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
     */
    private String stockfishStackShort() {
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
     */
    private FeatureDriver strongestDriver(boolean positive) {
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
     */
    private FeatureDriver strongestDriverByAbs() {
        float[] indices = snapshot.data("nnue.features.us.indices");
        float[] impact = snapshot.data("nnue.features.us.impact");
        if (indices == null || impact == null) {
            return FeatureDriver.invalid();
        }
        return featureDriverAt(strongestAbsIndex(impact), indices, impact);
    }

    /**
     * Returns the active feature driver for a sparse feature index.
     */
    private FeatureDriver driverForFeature(int featureIndex) {
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
     */
    private FeatureDriver featureDriverAt(int row, float[] indices, float[] impact) {
        if (row < 0 || indices == null || impact == null
                || row >= indices.length || row >= impact.length) {
            return FeatureDriver.invalid();
        }
        return new FeatureDriver(row, Math.round(indices[row]), impact[row],
                featureImpactRank(row, impact), true);
    }

    /**
     * Returns the one-based absolute-impact rank for an active feature row.
     */
    private static int featureImpactRank(int row, float[] impact) {
        if (impact == null || row < 0 || row >= impact.length) {
            return 0;
        }
        float abs = Math.abs(impact[row]);
        int rank = 1;
        for (float value : impact) {
            if (Math.abs(value) > abs) {
                rank++;
            }
        }
        return rank;
    }

    /**
     * Returns a human-readable label for a driver.
     */
    private String driverLabel(FeatureDriver driver) {
        return driver.valid() ? decodeUsHalfKP(driver.featureIndex()) : "-";
    }

    /**
     * Paints the current position card used by the NNUE overview column.
     *
     * @param g graphics
     * @param r destination rectangle
     */
    private void paintPositionOverview(Graphics2D g, Rectangle r) {
        WorkbenchTensorViz.drawCard(g, r,
                "board",
                "each NNUE input = a (king square, piece) pair; click a feature to light its squares",
                WorkbenchTheme.ACCENT);
        int availableW = Math.max(24, r.width - 16);
        int availableH = Math.max(24, r.height - 44);
        int side = Math.max(24, Math.min(availableW, availableH));
        Rectangle innerBoard = new Rectangle(r.x + (r.width - side) / 2,
                r.y + 36 + Math.max(0, (availableH - side) / 2),
                side, side);
        WorkbenchTensorViz.drawMiniBoard(g, innerBoard);
        WorkbenchTensorViz.drawPositionPieces(g, innerBoard, fen);
        paintOverviewFeatureOverlay(g, innerBoard);
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
    private void paintSelectedFeatureOverlay(Graphics2D g, Rectangle board) {
        if (selectedFeature < 0) {
            return;
        }
        HalfKpFeature feature = decodeHalfKpFeature(selectedFeature, sideToMoveWhite());
        if (!feature.valid) {
            return;
        }
        double cell = board.width / 8.0;
        highlightSquare(g, board, feature.kingSquare, cell, cell,
                WorkbenchTheme.withAlpha(WorkbenchTheme.ACCENT, 110));
        if (feature.pieceSquare != feature.kingSquare) {
            boolean enemy = feature.pieceCode >= 5;
            highlightSquare(g, board, feature.pieceSquare, cell, cell,
                    enemy ? WorkbenchTheme.withAlpha(WorkbenchTensorViz.NEGATIVE, 110)
                            : WorkbenchTheme.withAlpha(WorkbenchTensorViz.POSITIVE, 110));
        }
    }

    /**
     * Paints selected or top-driver feature anchors on the overview board.
     */
    private void paintOverviewFeatureOverlay(Graphics2D g, Rectangle board) {
        if (selectedBoardSquare >= 0) {
            WorkbenchTensorViz.drawBoardSquareRing(g, board, selectedBoardSquare,
                    WorkbenchTheme.ACCENT);
        }
        if (selectedFeature >= 0) {
            paintSelectedFeatureOverlay(g, board);
            return;
        }
        paintDriverFeatureOverlay(g, board, strongestDriver(true),
                WorkbenchTheme.withAlpha(WorkbenchTensorViz.POSITIVE, 110));
        paintDriverFeatureOverlay(g, board, strongestDriver(false),
                WorkbenchTheme.withAlpha(WorkbenchTensorViz.NEGATIVE, 105));
    }

    /**
     * Paints one driver's king and piece anchors on the board.
     */
    private void paintDriverFeatureOverlay(Graphics2D g, Rectangle board,
            FeatureDriver driver, Color tint) {
        if (!driver.valid()) {
            return;
        }
        HalfKpFeature feature = decodeHalfKpFeature(driver.featureIndex(), sideToMoveWhite());
        if (!feature.valid) {
            return;
        }
        double cell = board.width / 8.0;
        highlightSquare(g, board, feature.kingSquare, cell, cell,
                WorkbenchTheme.withAlpha(WorkbenchTheme.ACCENT, 80));
        highlightSquare(g, board, feature.pieceSquare, cell, cell, tint);
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
     * Paints the per-piece contributor columns. Each active half-KP feature
     * is one piece-and-king-square pairing; the column on the left lists the
     * features pulling the eval up for us, the column on the right lists the
     * features pulling it down (i.e. helping them). Rows are sized to fit as
     * many features as space allows.
     *
     * @param g graphics
     * @param r area rectangle
     */
    private void paintTopContributors(Graphics2D g, Rectangle r) {
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 36),
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
                        ? WorkbenchTensorViz.POSITIVE
                        : WorkbenchTensorViz.NEGATIVE;
                WorkbenchTensorViz.drawInfoChip(g,
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
        g.setColor(positive ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE);
        g.setFont(WorkbenchTheme.font(11, Font.BOLD));
        g.drawString(positive ? "raise" : "lower",
                r.x + 2, r.y + 14);
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
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
        g.setFont(WorkbenchTheme.font(compact ? 10 : 11, Font.PLAIN));
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < rows; ++i) {
            int idx = selected.get(i);
            int featureIdx = Math.round(indices[idx]);
            float v = impact[idx];
            int y = r.y + 22 + i * rowH;
            Rectangle row = new Rectangle(r.x, y, r.width, rowH - 2);
            boolean isSelected = featureIdx == selectedFeature;
            g.setColor(isSelected ? WorkbenchTheme.SELECTION_SOLID
                    : (i % 2 == 0 ? WorkbenchTheme.PANEL_SOLID : WorkbenchTheme.ELEVATED_SOLID));
            g.fillRect(row.x, row.y, row.width, row.height);
            g.setColor(isSelected ? WorkbenchTheme.ACCENT : WorkbenchTheme.LINE);
            g.drawRect(row.x, row.y, row.width - 1, row.height - 1);
            int glyphSize = Math.min(row.height - 2, compact ? 18 : 28);
            Rectangle glyph = new Rectangle(row.x + 2, row.y + (row.height - glyphSize) / 2,
                    glyphSize, glyphSize);
            HalfKpFeature feature = decodeHalfKpFeature(featureIdx, sideToMoveWhite());
            WorkbenchTensorViz.drawHalfKpGlyph(g, glyph,
                    feature.kingSquare, feature.pieceCode, feature.pieceSquare);
            String decoded = decodeUsHalfKP(featureIdx);
            String label = compact ? decoded : ("#" + featureIdx + "  " + decoded);
            g.setColor(WorkbenchTheme.TEXT);
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
                WorkbenchTensorViz.drawHorizontalBar(g, bar, v, maxAbs, null);
            }
            g.setColor(v >= 0 ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE);
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
    private void paintAccumulatorOverview(Graphics2D g, Rectangle r) {
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
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
        float[] stats = WorkbenchTensorViz.summarize(us);
        float[] them2 = WorkbenchTensorViz.summarize(them);
        float dynamicMax = Math.max(Math.max(Math.abs(stats[3]), Math.abs(stats[4])),
                Math.max(Math.abs(them2[3]), Math.abs(them2[4])));
        float scale = scaleFor("accumulator", dynamicMax);
        WorkbenchTensorViz.drawHeatmap(g, leftMap, us, colsPerSide, rows, scale, false);
        WorkbenchTensorViz.drawHeatmap(g, rightMap, them, colsPerSide, rows, scale, false);
        float[] contribution = totalContribution();
        int topSlot = strongestAbsIndex(contribution);
        if (topSlot >= 0 && topSlot < us.length) {
            Color accent = contribution[topSlot] >= 0.0f
                    ? WorkbenchTensorViz.POSITIVE
                    : WorkbenchTensorViz.NEGATIVE;
            drawGridCellRing(g, leftMap, topSlot, colsPerSide, rows, accent);
            if (topSlot < them.length) {
                drawGridCellRing(g, rightMap, topSlot, colsPerSide, rows, accent);
            }
            if (r.width >= 560) {
                WorkbenchTensorViz.drawInfoChip(g,
                        new Rectangle(r.x + r.width - 216, r.y + 8, 206, 24),
                        "top net slot",
                        "#" + topSlot + "  " + String.format("%+.2f", contribution[topSlot]),
                        accent);
            }
        }
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(10, Font.BOLD));
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
    private void paintTrunkOverview(Graphics2D g, Rectangle r) {
        if (isStockfishSnapshot()) {
            paintStockfishTrunkOverview(g, r);
            return;
        }
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
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
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
            g.drawString("No trunk tensors in this snapshot.", r.x + 10, r.y + 70);
            return;
        }

        int topSlot = strongestAbsIndex(contribution);
        if (topSlot >= 0 && r.width >= 420) {
            float value = valueAt(contribution, topSlot);
            WorkbenchTensorViz.drawInfoChip(g,
                    new Rectangle(r.x + r.width - 206, r.y + 8, 196, 24),
                    "top net",
                    "#" + topSlot + "  " + String.format("%+.3f", value),
                    value >= 0.0f ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE);
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
    private void paintStockfishTrunkOverview(Graphics2D g, Rectangle r) {
        WorkbenchTensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
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
            WorkbenchTensorViz.drawInfoChip(g,
                    new Rectangle(r.x + r.width - 206, r.y + 8, 196, 24),
                    "top FC2",
                    "#" + topTerm + "  " + String.format("%+.3f", value),
                    value >= 0.0f ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE);
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
    private int drawOverviewVectorRow(Graphics2D g, Rectangle r, String label,
            float[] values, boolean signed, String dataKey, String detail,
            String scaleKey, int ringIndex) {
        int labelW = Math.min(112, Math.max(74, r.width / 4));
        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(10, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(WorkbenchUi.elide(label, fm, Math.max(12, labelW - 8)),
                r.x + 2, r.y + r.height / 2 + 4);
        Rectangle heat = new Rectangle(r.x + labelW, r.y + 2,
                Math.max(1, r.width - labelW), Math.max(1, r.height - 4));
        if (values == null || values.length == 0) {
            WorkbenchTensorViz.drawEmpty(g, heat);
            return r.y + r.height;
        }
        float scale = scaleFor(scaleKey, maxAbs(values));
        WorkbenchTensorViz.drawHeatmap(g, heat, values, values.length, 1,
                Math.max(1e-4f, scale), signed);
        if (ringIndex >= 0 && ringIndex < values.length) {
            Color accent = valueAt(values, ringIndex) >= 0.0f
                    ? WorkbenchTensorViz.POSITIVE
                    : WorkbenchTensorViz.NEGATIVE;
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
     * Returns {@code a - b}, allowing either side to be absent.
     *
     * @param a first vector
     * @param b second vector
     * @return difference vector, or null when both inputs are absent
     */
    private static float[] subtractVectors(float[] a, float[] b) {
        int len = Math.max(safeLength(a), safeLength(b));
        if (len == 0) {
            return null;
        }
        float[] out = new float[len];
        for (int i = 0; i < len; i++) {
            out[i] = valueAt(a, i) - valueAt(b, i);
        }
        return out;
    }

    /**
     * Returns {@code a + b}, allowing either side to be absent.
     *
     * @param a first vector
     * @param b second vector
     * @return sum vector, or null when both inputs are absent
     */
    private static float[] addVectors(float[] a, float[] b) {
        int len = Math.max(safeLength(a), safeLength(b));
        if (len == 0) {
            return null;
        }
        float[] out = new float[len];
        for (int i = 0; i < len; i++) {
            out[i] = valueAt(a, i) + valueAt(b, i);
        }
        return out;
    }

    /**
     * Returns a copy of a vector without its final fwd/bias slot.
     *
     * @param values source values
     * @return copy without last value, or null when absent
     */
    private static float[] withoutLast(float[] values) {
        if (values == null || values.length == 0) {
            return null;
        }
        float[] out = new float[Math.max(0, values.length - 1)];
        System.arraycopy(values, 0, out, 0, out.length);
        return out;
    }

    /**
     * Paints a compact positive-vs-negative contribution ledger.
     *
     * @param g graphics
     * @param r ledger bounds
     */
    private void paintContributionLedger(Graphics2D g, Rectangle r) {
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
        g.setColor(WorkbenchTheme.PANEL_SOLID);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(WorkbenchTheme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);

        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(11, Font.BOLD));
        g.drawString("output contribution", r.x + 8, r.y + 15);
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
        g.drawString(active + " active slots", r.x + 128, r.y + 15);
        if (r.width >= 420) {
            boolean raiseDominates = positive >= negative;
            float dominant = raiseDominates ? positive : negative;
            float share = (positive + negative) <= 1e-5f ? 0.0f : dominant / (positive + negative);
            WorkbenchTensorViz.drawInfoChip(g,
                    new Rectangle(r.x + r.width - 180, r.y + 3, 170, 18),
                    "dominant",
                    (raiseDominates ? "raise " : "lower ") + Math.round(share * 100.0f) + "%",
                    raiseDominates ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE);
        }

        int center = r.x + r.width / 2;
        int barY = r.y + 25;
        int barH = 12;
        int halfW = Math.max(1, r.width / 2 - 18);
        g.setColor(WorkbenchTheme.ELEVATED_SOLID);
        g.fillRoundRect(center - halfW, barY, halfW * 2, barH, barH, barH);
        int negW = Math.round(halfW * (negative / scale));
        int posW = Math.round(halfW * (positive / scale));
        g.setColor(WorkbenchTensorViz.NEGATIVE);
        g.fillRoundRect(center - negW, barY, negW, barH, barH, barH);
        g.setColor(WorkbenchTensorViz.POSITIVE);
        g.fillRoundRect(center, barY, posW, barH, barH, barH);
        g.setColor(WorkbenchTheme.LINE);
        g.drawLine(center, barY - 2, center, barY + barH + 2);

        g.setFont(WorkbenchTheme.font(10, Font.BOLD));
        String negLabel = String.format("-%.1f", negative);
        String posLabel = String.format("+%.1f", positive);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(WorkbenchTensorViz.NEGATIVE);
        g.drawString(negLabel, center - halfW, r.y + r.height - 5);
        g.setColor(WorkbenchTensorViz.POSITIVE);
        g.drawString(posLabel, center + halfW - fm.stringWidth(posLabel), r.y + r.height - 5);
        hitRegions.addInline(r,
                "Output contribution ledger",
                "Net us+them per-slot contributions before final centipawn scaling.",
                String.format("raise %.2f · lower %.2f · %d active", positive, negative, active),
                contribution, "1x" + contribution.length);
    }

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
        WorkbenchTensorViz.drawSectionHeader(g,
                new Rectangle(body.x, body.y, body.width, TRACE_HEADER_H),
                "detailed NNUE node graph",
                isStockfishSnapshot()
                        ? "Stockfish HalfKA -> transformer -> FC0 -> FC1 -> FC2 head"
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
        Layout layout = layout(wire);
        drawTraceBackdrop(g, wire, layout);
        drawClassicTrunkRails(g, layout);
        drawColumnLabels(g, layout);
        drawEdges(g, layout);
        drawFeatureColumn(g, layout);
        drawAccumulatorColumn(g, layout);
        drawClippedColumn(g, layout);
        drawContributionColumn(g, layout);
        drawOutputColumn(g, layout);
        WorkbenchTensorViz.drawMiniBoard(g, boardArea);
        WorkbenchTensorViz.drawPositionPieces(g, boardArea, fen);
        paintSelectedFeatureOverlay(g, boardArea);
        hitRegions.add(boardArea, "Current position",
                fen == null ? "no FEN" : fen,
                "Half-KP features are derived from this board");
        g.setColor(WorkbenchTheme.MUTED);
        g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
        g.drawString("position", boardArea.x, boardArea.y - 4);
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
    private void drawTracePipelineRibbon(Graphics2D g, Rectangle r) {
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
                activeSplit, true, WorkbenchTensorViz.POSITIVE,
                null, "Sparse king-piece-square features currently active.");
        drawTraceStage(g, cards[1],
                "accumulator us",
                safeLength(accUs) + " raw neurons",
                accUs, true, WorkbenchTensorViz.TRUNK,
                "nnue.accumulator.us", "Raw side-to-move accumulator before clipped ReLU.");
        drawTraceStage(g, cards[2],
                "accumulator them",
                safeLength(accThem) + " raw neurons",
                accThem, true, WorkbenchTensorViz.TRUNK,
                "nnue.accumulator.them", "Raw opponent accumulator before clipped ReLU.");
        drawTraceStage(g, cards[3],
                "clipped us",
                safeLength(clippedUs) + " post-ReLU neurons",
                clippedUs, false, WorkbenchTensorViz.POLICY,
                "nnue.clipped.us", "Side-to-move activations after clipping.");
        drawTraceStage(g, cards[4],
                "clipped them",
                safeLength(clippedThem) + " post-ReLU neurons",
                clippedThem, false, WorkbenchTensorViz.VALUE,
                "nnue.clipped.them", "Opponent activations after clipping.");
        drawTraceStage(g, cards[5],
                "linear output",
                output == null || output.length == 0
                        ? "centipawn head"
                        : String.format("%+d cp", Math.round(output[0])),
                output, true, WorkbenchTheme.ACCENT,
                "nnue.output.centipawns", "Final affine output converted to centipawns.");
    }

    /**
     * Draws the Stockfish NNUE layer-stack ribbon.
     *
     * @param g graphics
     * @param r ribbon rectangle
     */
    private void drawStockfishTracePipelineRibbon(Graphics2D g, Rectangle r) {
        float[] usIndices = snapshot.data("nnue.features.us.indices");
        float[] transformed = snapshot.data("nnue.stockfish.transformed.us");
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
                "HalfKA inputs",
                safeLength(usIndices) + " side-to-move active",
                usIndices, false, WorkbenchTensorViz.POSITIVE,
                "nnue.features.us.indices", "Stockfish HalfKAv2_hm sparse features.");
        drawTraceStage(g, cards[1],
                "transformer",
                safeLength(transformed) + " stm lanes",
                transformed, false, WorkbenchTensorViz.TRUNK,
                "nnue.stockfish.transformed.us", "Feature-transformer output for side-to-move half.");
        drawTraceStage(g, cards[2],
                "FC0",
                Math.max(0, safeLength(fc0) - 1) + " hidden + fwd",
                fc0, true, WorkbenchTensorViz.TRUNK,
                "nnue.stockfish.fc0.raw", "First Stockfish dense affine layer.");
        drawTraceStage(g, cards[3],
                "FC1",
                safeLength(fc1) + " clipped hidden",
                fc1, false, WorkbenchTensorViz.POLICY,
                "nnue.stockfish.fc1.clipped", "Second Stockfish dense hidden layer after clipping.");
        drawTraceStage(g, cards[4],
                "FC2 contribution",
                safeLength(fc2) + " output terms",
                fc2, true, WorkbenchTensorViz.VALUE,
                "nnue.stockfish.fc2.contribution", "Final affine output contribution per FC1 lane.");
        drawTraceStage(g, cards[5],
                "linear output",
                output == null || output.length == 0
                        ? "centipawn head"
                        : String.format("%+d cp", Math.round(output[0])),
                outputParts == null ? output : outputParts, true, WorkbenchTheme.ACCENT,
                outputParts == null ? "nnue.output.centipawns" : "nnue.stockfish.output.parts",
                "Stockfish output parts: PSQT, FC2 bias, FC2 terms, and FC0 forward branch.");
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
    private void drawTraceStage(Graphics2D g, Rectangle r, String title, String subtitle,
            float[] values, boolean signed, Color accent, String dataKey, String detail) {
        WorkbenchTensorViz.drawCard(g, r, title, subtitle, accent);
        Rectangle strip = new Rectangle(r.x + 10, r.y + r.height - 26,
                Math.max(8, r.width - 20), 12);
        if (values == null || values.length == 0) {
            WorkbenchTensorViz.drawEmpty(g, strip);
        } else {
            WorkbenchTensorViz.drawHeatmap(g, strip, values, values.length, 1,
                    Math.max(1e-4f, maxAbs(values)), signed);
        }
        g.setFont(WorkbenchTheme.font(9, Font.PLAIN));
        g.setColor(WorkbenchTheme.MUTED);
        String stats = traceStats(values, signed);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(WorkbenchUi.elide(stats, fm, Math.max(12, r.width - 20)),
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
    private void drawTraceConnector(Graphics2D g, Rectangle from, Rectangle to) {
        int y = from.y + from.height / 2;
        int x1 = from.x + from.width + 1;
        int x2 = to.x - 2;
        if (x2 <= x1) {
            return;
        }
        g.setColor(WorkbenchTheme.withAlpha(WorkbenchTheme.ACCENT, 140));
        g.drawLine(x1, y, x2, y);
        g.drawLine(x2, y, x2 - 4, y - 3);
        g.drawLine(x2, y, x2 - 4, y + 3);
    }

    /**
     * Returns compact stats text for a Trace ribbon strip.
     *
     * @param values values
     * @param signed true when the strip is signed
     * @return stats label
     */
    private static String traceStats(float[] values, boolean signed) {
        if (values == null || values.length == 0) {
            return "no data";
        }
        float[] s = WorkbenchTensorViz.summarize(values);
        if (signed) {
            return String.format("mean %+.3f | rms %.3f | max %.3f",
                    s[0], s[2], maxAbs(values));
        }
        return String.format("mean %.3f | max %.3f", s[0], s[4]);
    }

    /**
     * Safe array length helper.
     *
     * @param values values
     * @return length, or 0 for null
     */
    private static int safeLength(float[] values) {
        return values == null ? 0 : values.length;
    }

    /**
     * Computes column geometry for the Trace diagram
     * (features → accumulator → clipped → contribution → output).
     *
     * @param r body rectangle
     * @return layout
     */
    private Layout layout(Rectangle r) {
        Layout out = new Layout();
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
     * Picks a pitch that prefers the nominal spacing but compresses enough to
     * keep a column inside the current pane.
     *
     * @param usable usable vertical pixels
     * @param count node count
     * @param nominal preferred pitch
     * @return adaptive pitch
     */
    private static int adaptivePitch(int usable, int count, int nominal) {
        if (count <= 1) {
            return 0;
        }
        int fit = Math.max(6, usable / (count - 1));
        return Math.min(nominal, fit);
    }

    /**
     * Paints subtle stage bands behind the Trace graph. The bands turn the dense
     * mesh into five readable pipeline stages without changing the data.
     *
     * @param g graphics
     * @param wire trace body rectangle
     * @param layout layout
     */
    private void drawTraceBackdrop(Graphics2D g, Rectangle wire, Layout layout) {
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
            g.fillRoundRect(x, top, bandW, bottom - top, WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
            g.setColor(WorkbenchTheme.withAlpha(WorkbenchTheme.LINE, 105));
            g.drawRoundRect(x, top, bandW, bottom - top, WorkbenchTheme.RADIUS, WorkbenchTheme.RADIUS);
        }

        int flowY = Math.max(wire.y + 34, layout.labelY + 17);
        if (flowY < layout.graphTop - 2 && centers.length > 1) {
            int startX = centers[0] + Math.max(14, bandW / 2 - 6);
            int endX = centers[centers.length - 1] - Math.max(14, bandW / 2 - 6);
            g.setColor(WorkbenchTheme.withAlpha(WorkbenchTheme.ACCENT, 120));
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
    private void drawClassicTrunkRails(Graphics2D g, Layout layout) {
        if (isStockfishSnapshot() || visibleSlots.length == 0) {
            return;
        }
        int x1 = layout.accumCx;
        int x2 = layout.contribCx;
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < visibleSlots.length; i++) {
            int y = layout.startY + i * layout.slotPitch;
            boolean selected = i == selectedSlot;
            g.setColor(WorkbenchTheme.withAlpha(
                    selected ? WorkbenchTheme.ACCENT : WorkbenchTheme.NN_TRUNK,
                    selected ? 130 : 52));
            g.drawLine(x1, y, x2, y);
        }
        g.setStroke(new BasicStroke(1.0f));

        int labelY = layout.graphTop - 4;
        if (labelY > layout.labelY + 14) {
            g.setFont(WorkbenchTheme.font(9, Font.BOLD));
            g.setColor(WorkbenchTheme.withAlpha(WorkbenchTheme.NN_TRUNK, 180));
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
     * Returns Trace column centers in display order.
     *
     * @param layout layout
     * @return center x coordinates
     */
    private static int[] traceColumnCenters(Layout layout) {
        return new int[] { layout.featureCx, layout.accumCx, layout.clippedCx,
                layout.contribCx, layout.outputCx };
    }

    /**
     * Computes a responsive stage-band width from the current column spacing.
     *
     * @param layout layout
     * @return band width in pixels
     */
    private static int traceBandWidth(Layout layout) {
        int gap = Math.min(layout.accumCx - layout.featureCx,
                Math.min(layout.clippedCx - layout.accumCx,
                        Math.min(layout.contribCx - layout.clippedCx,
                                layout.outputCx - layout.contribCx)));
        return Math.max(38, Math.min(132, gap - 18));
    }

    /**
     * Stage tint for the Trace backdrop.
     *
     * @param index stage index
     * @return translucent tint
     */
    private static Color traceStageTint(int index) {
        switch (index) {
            case 0:
                return WorkbenchTheme.withAlpha(WorkbenchTensorViz.POSITIVE, 18);
            case 1:
                return WorkbenchTheme.withAlpha(WorkbenchTheme.ACCENT, 20);
            case 2:
                return WorkbenchTheme.withAlpha(WorkbenchTheme.NN_TRUNK, 20);
            case 3:
                return WorkbenchTheme.withAlpha(WorkbenchTheme.NN_POLICY, 16);
            default:
                return WorkbenchTheme.withAlpha(WorkbenchTheme.NN_VALUE, 16);
        }
    }

    /**
     * Draws column labels.
     *
     * @param g graphics
     * @param layout layout
     */
    private void drawColumnLabels(Graphics2D g, Layout layout) {
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
    private String stockfishFeatureStageDetail() {
        int active = safeLength(snapshot.data("nnue.features.us.indices"));
        return active + "/" + traceFeatureLanes() + " active";
    }

    /**
     * Detail label for the Stockfish transformer stage.
     *
     * @return label text
     */
    private String stockfishTransformerStageDetail() {
        int stm = safeLength(snapshot.data("nnue.stockfish.transformed.us"));
        int shown = Math.min(visibleSlots.length, stm);
        if (stm > 0 && shown > 0 && shown < stm) {
            return "top " + shown + "/" + stm + " stm";
        }
        return stm + " stm lanes";
    }

    /**
     * Detail label for the Stockfish FC0 stage.
     *
     * @return label text
     */
    private String stockfishFc0StageDetail() {
        int rows = Math.max(0, safeLength(snapshot.data("nnue.stockfish.fc0.raw")) - 1);
        return rows + " hidden + fwd";
    }

    /**
     * Detail label for the Stockfish FC1 stage.
     *
     * @return label text
     */
    private String stockfishFc1StageDetail() {
        return safeLength(snapshot.data("nnue.stockfish.fc1.clipped")) + " clipped";
    }

    /**
     * Detail label for the Stockfish FC2 stage.
     *
     * @return label text
     */
    private String stockfishFc2StageDetail() {
        String suffix = snapshot.data("nnue.stockfish.fc0.fwd.cp") == null ? " terms" : " terms + fwd";
        return safeLength(snapshot.data("nnue.stockfish.fc2.contribution")) + suffix;
    }

    /**
     * Detail label for the classic sparse-input stage.
     *
     * @return label text
     */
    private String classicFeatureStageDetail() {
        int active = safeLength(snapshot.data("nnue.features.us.indices"));
        return active + "/" + traceFeatureLanes() + " active";
    }

    /**
     * Detail label for the classic accumulator stage.
     *
     * @return label text
     */
    private String classicAccumulatorStageDetail() {
        return visibleStageDetail(snapshot.data("nnue.accumulator.us"), "slots");
    }

    /**
     * Detail label for the classic clipped-ReLU stage.
     *
     * @return label text
     */
    private String classicClippedStageDetail() {
        return visibleStageDetail(snapshot.data("nnue.clipped.us"), "slots");
    }

    /**
     * Detail label for the classic contribution stage.
     *
     * @return label text
     */
    private String classicContributionStageDetail() {
        return visibleStageDetail(totalContribution(), "terms");
    }

    /**
     * Returns a "shown/total" label for a Trace column.
     *
     * @param values backing values
     * @param unit unit label
     * @return label text
     */
    private String visibleStageDetail(float[] values, String unit) {
        int total = safeLength(values);
        int shown = Math.min(visibleSlots.length, total);
        if (total > 0 && shown > 0 && shown < total) {
            return "top " + shown + "/" + total;
        }
        return total + " " + unit;
    }

    /**
     * Draws a numbered stage label above a Trace column.
     *
     * @param g graphics
     * @param cx center x
     * @param y title baseline
     * @param width maximum label width
     * @param number stage number
     * @param title title
     * @param detail detail line
     */
    private static void drawStageHeader(Graphics2D g, int cx, int y, int width,
            String number, String title, String detail) {
        String heading = number + " " + title;
        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(10, Font.BOLD));
        drawCenteredFittedLabel(g, heading, cx, y, width);
        if (width >= 64) {
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(9, Font.PLAIN));
            drawCenteredFittedLabel(g, detail, cx, y + 12, width);
        }
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
                    g.setColor(WorkbenchTheme.MUTED);
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
            WorkbenchTensorViz.drawNode(g, layout.featureCx, y, layout.slotRadius, v, false);
            g.setColor(WorkbenchTheme.TEXT);
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
     * Draws a muted placeholder for an inactive feature lane.
     *
     * @param g graphics
     * @param cx center x
     * @param y center y
     * @param radius node radius
     */
    private static void drawInactiveFeatureLane(Graphics2D g, int cx, int y, int radius) {
        g.setColor(WorkbenchTheme.withAlpha(WorkbenchTheme.LINE, 90));
        g.fillOval(cx - radius, y - radius, radius * 2, radius * 2);
        g.setColor(WorkbenchTheme.withAlpha(WorkbenchTheme.MUTED, 130));
        g.drawOval(cx - radius, y - radius, radius * 2, radius * 2);
    }

    /**
     * Draws the raw accumulator column. The visible slot set is still ranked by
     * final output contribution; this column shows the trunk value before the
     * clipped ReLU gate.
     *
     * @param g graphics
     * @param layout layout
     */
    private void drawAccumulatorColumn(Graphics2D g, Layout layout) {
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
            WorkbenchTensorViz.drawNode(g, layout.accumCx, y, layout.slotRadius, v, i == selectedSlot);
            if (pre < 0.0f || themPre < 0.0f) {
                int rr = layout.slotRadius + 3;
                g.setColor(WorkbenchTensorViz.NEGATIVE);
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
     * @param layout layout
     */
    private void drawClippedColumn(Graphics2D g, Layout layout) {
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
            WorkbenchTensorViz.drawNode(g, layout.clippedCx, y, layout.slotRadius, v, i == selectedSlot);
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
     * @param layout layout
     */
    private void drawContributionColumn(Graphics2D g, Layout layout) {
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
            WorkbenchTensorViz.drawNode(g, layout.contribCx, y, layout.slotRadius, value / scale, i == selectedSlot);
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
     * @param layout layout
     */
    private void drawStockfishTransformerColumn(Graphics2D g, Layout layout) {
        float[] transformed = snapshot.data("nnue.stockfish.transformed.us");
        if (transformed == null) {
            return;
        }
        float scale = visibleAbsScale(transformed, visibleSlots);
        for (int i = 0; i < visibleSlots.length; ++i) {
            int idx = visibleSlots[i];
            float value = valueAt(transformed, idx);
            int y = layout.startY + i * layout.slotPitch;
            WorkbenchTensorViz.drawNode(g, layout.accumCx, y, layout.slotRadius, value / scale, i == selectedSlot);
            int rad = layout.slotRadius + 2;
            hitRegions.add(new Rectangle(layout.accumCx - rad, y - rad, rad * 2, rad * 2),
                    "Transformer lane " + idx,
                    "Stockfish feature-transformer output, side-to-move half",
                    String.format("value %.1f", value));
        }
    }

    /**
     * Draws Stockfish FC0 raw outputs.
     *
     * @param g graphics
     * @param layout layout
     */
    private void drawStockfishFc0Column(Graphics2D g, Layout layout) {
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
            WorkbenchTensorViz.drawNode(g, layout.clippedCx, y, layout.slotRadius, fc0[i] / scale, false);
            if (fwd) {
                int rr = layout.slotRadius + 3;
                g.setColor(WorkbenchTheme.withAlpha(WorkbenchTheme.ACCENT, 190));
                g.drawOval(layout.clippedCx - rr, y - rr, rr * 2, rr * 2);
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
     * @param layout layout
     */
    private void drawStockfishFc1Column(Graphics2D g, Layout layout) {
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
            WorkbenchTensorViz.drawNode(g, layout.contribCx, y, layout.slotRadius, fc1[i] / scale, false);
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
    private void drawSlotZoom(Graphics2D g, Rectangle r, Layout layout) {
        if (r.height < 96 || r.width < 140) {
            return;
        }
        if (isStockfishSnapshot()) {
            drawStockfishSlotZoom(g, r);
            return;
        }
        WorkbenchTensorViz.drawCard(g, r,
                selectedSlot >= 0 ? "slot zoom" : "slot zoom",
                selectedSlot >= 0 ? "incoming feature weights for the selected neuron"
                        : "click any accumulator neuron to zoom",
                WorkbenchTheme.ACCENT);
        if (selectedSlot < 0 || selectedSlot >= visibleSlots.length) {
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
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

        g.setFont(WorkbenchTheme.font(11, Font.BOLD));
        g.setColor(WorkbenchTheme.TEXT);
        g.drawString(String.format("slot %d", slot), r.x + 10, r.y + 58);
        g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
        g.setColor(WorkbenchTheme.MUTED);
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
        WorkbenchTensorViz.drawHeatmap(g, heat, column, 1, rows, scale, true);
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
    private void drawStockfishSlotZoom(Graphics2D g, Rectangle r) {
        WorkbenchTensorViz.drawCard(g, r,
                selectedSlot >= 0 ? "lane zoom" : "lane zoom",
                selectedSlot >= 0 ? "incoming HalfKA weights and FC0 forward branch"
                        : "click any transformer lane to zoom",
                WorkbenchTheme.ACCENT);
        if (selectedSlot < 0 || selectedSlot >= visibleSlots.length) {
            g.setColor(WorkbenchTheme.MUTED);
            g.setFont(WorkbenchTheme.font(11, Font.PLAIN));
            g.drawString("No transformer lane selected.", r.x + 10, r.y + 64);
            return;
        }

        int lane = visibleSlots[selectedSlot];
        float[] transformed = snapshot.data("nnue.stockfish.transformed.us");
        float[] fwdWeights = snapshot.data("nnue.stockfish.fc0.weights.fwd.us");
        float[] fwdCp = snapshot.data("nnue.stockfish.fc0.fwd.cp");
        float value = valueAt(transformed, lane);
        float fwdWeight = valueAt(fwdWeights, lane);

        g.setFont(WorkbenchTheme.font(11, Font.BOLD));
        g.setColor(WorkbenchTheme.TEXT);
        g.drawString(String.format("lane %d", lane), r.x + 10, r.y + 58);
        g.setFont(WorkbenchTheme.font(10, Font.PLAIN));
        g.setColor(WorkbenchTheme.MUTED);
        String detail = String.format("transformer %+6.1f   fwd w %+6.2f   fwd branch %+6.2f cp",
                value, fwdWeight, valueAt(fwdCp, 0));
        g.drawString(WorkbenchUi.elide(detail, g.getFontMetrics(), Math.max(12, r.width - 20)),
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
        WorkbenchTensorViz.drawHeatmap(g, heat, column, 1, rows, scale, true);
        hitRegions.addInline(heat,
                "Transformer lane " + lane + " incoming HalfKA weights",
                "Zoomed feature->transformer column for the selected Stockfish lane",
                String.format("%d active feature rows · range ±%.3f", rows, Math.max(scale, 1e-4f)),
                column, rows + "x1");
    }

    /**
     * Draws all weighted edges for active feature lanes and all visible
     * accumulator slots, emphasising the selected slot's edges.
     *
     * @param g graphics
     * @param layout layout
     */
    private void drawEdges(Graphics2D g, Layout layout) {
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
     * @param layout layout
     */
    private void drawStockfishEdges(Graphics2D g, Layout layout) {
        float[] featureWeights = snapshot.data("nnue.features.us.weights");
        int[] featureWeightShape = snapshot.shape("nnue.features.us.weights");
        float[] fc0Weights = snapshot.data("nnue.stockfish.fc0.weights.us");
        int[] fc0Shape = snapshot.shape("nnue.stockfish.fc0.weights.us");
        float[] fc0FwdWeights = snapshot.data("nnue.stockfish.fc0.weights.fwd.us");
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
        for (int si = 0; si < visibleSlots.length; si++) {
            int slotIdx = visibleSlots[si];
            int sourceY = layout.startY + si * layout.slotPitch;
            for (int row = 0; row < fc0Rows; row++) {
                int targetY = columnY(layout, row, fc0DisplayRows);
                int offset = row * fc0Stride + slotIdx;
                float strength = fc0Weights != null && fc0Stride > 0
                        && slotIdx < fc0Stride && offset < fc0Weights.length
                        ? fc0Weights[offset] / fc0Scale : 0.0f;
                drawTraceEdge(g, layout.accumCx + layout.slotRadius, sourceY,
                        layout.clippedCx - layout.slotRadius, targetY, strength, si == selectedSlot);
            }
            if (fc0DisplayRows > fc0Rows) {
                int fwdY = columnY(layout, fc0Rows, fc0DisplayRows);
                float strength = fc0FwdWeights != null && slotIdx < fc0FwdWeights.length
                        ? fc0FwdWeights[slotIdx] / fc0Scale : 0.0f;
                drawTraceEdge(g, layout.accumCx + layout.slotRadius, sourceY,
                        layout.clippedCx - layout.slotRadius, fwdY, strength, si == selectedSlot);
            }
        }

        int fc1Rows = fc1 == null ? 0 : fc1.length;
        int fc1Stride = fc1Shape != null && fc1Shape.length >= 2 ? fc1Shape[1] : 0;
        float fc1Scale = matrixScale(fc1Weights);
        for (int row = 0; row < fc0Rows; row++) {
            int sourceY = columnY(layout, row, fc0DisplayRows);
            for (int next = 0; next < fc1Rows; next++) {
                int targetY = columnY(layout, next, fc1Rows);
                int offset = next * fc1Stride + row;
                float strength = fc1Weights != null && fc1Stride > 0
                        && row < fc1Stride && offset < fc1Weights.length
                        ? fc1Weights[offset] / fc1Scale : 0.0f;
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
            drawTraceEdge(g, layout.clippedCx + layout.slotRadius, fwdY,
                    layout.outputCx - (layout.slotRadius + 6), outCy, strength, false);
        }
    }

    /**
     * Draws a Trace edge using one fixed stroke style for every layer. Colour
     * carries sign and opacity carries magnitude.
     *
     * @param g graphics
     * @param x1 source x
     * @param y1 source y
     * @param x2 target x
     * @param y2 target y
     * @param strength signed magnitude in [-1, 1]
     * @param emphasised true to highlight a selected slot's incoming edges
     */
    private static void drawTraceEdge(Graphics2D g, int x1, int y1, int x2, int y2,
            float strength, boolean emphasised) {
        float s = Math.max(-1.0f, Math.min(1.0f, strength));
        float mag = (float) Math.sqrt(Math.abs(s));
        Color base = Math.abs(s) < 0.004f ? WorkbenchTheme.MUTED
                : s >= 0.0f ? WorkbenchTensorViz.POSITIVE : WorkbenchTensorViz.NEGATIVE;
        int alpha = TRACE_EDGE_ALPHA_MIN
                + Math.round((TRACE_EDGE_ALPHA_MAX - TRACE_EDGE_ALPHA_MIN) * mag);
        if (emphasised) {
            alpha = Math.min(238, alpha + TRACE_EDGE_FOCUS_BOOST);
        }
        g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
        g.setStroke(new BasicStroke(TRACE_EDGE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(x1, y1, x2, y2);
        g.setStroke(new BasicStroke(1.0f));
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
            String text = isStockfishSnapshot()
                    ? "Stockfish trace: HalfKA -> transformer -> FC0 hidden/forward -> FC1 -> FC2/output | green raises, red lowers, opacity = weight size"
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
        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(11, Font.BOLD));
        g.drawString(text, body.x + 8, body.y + body.height - 8);
    }

    /**
     * Draws the Stockfish trace footer for the selected transformer lane.
     *
     * @param g graphics
     * @param body detail body rectangle
     */
    private void drawStockfishDetailedReadout(Graphics2D g, Rectangle body) {
        int lane = visibleSlots[selectedSlot];
        float[] transformed = snapshot.data("nnue.stockfish.transformed.us");
        float[] fwdWeights = snapshot.data("nnue.stockfish.fc0.weights.fwd.us");
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
        g.setColor(WorkbenchTheme.TEXT);
        g.setFont(WorkbenchTheme.font(11, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(WorkbenchUi.elide(text, fm, Math.max(24, body.width - 16)),
                body.x + 8, body.y + body.height - 8);
    }

    /**
     * Draws a centered label, trimming it to fit a narrow Trace stage band.
     *
     * @param g graphics
     * @param text label
     * @param cx center x
     * @param y baseline y
     * @param maxWidth maximum text width
     */
    private static void drawCenteredFittedLabel(Graphics2D g, String text, int cx, int y, int maxWidth) {
        FontMetrics fm = g.getFontMetrics();
        String fitted = fitText(fm, text, Math.max(0, maxWidth));
        if (fitted.isEmpty()) {
            return;
        }
        int w = fm.stringWidth(fitted);
        g.drawString(fitted, cx - w / 2, y);
    }

    /**
     * Trims text with an ASCII ellipsis when it is wider than the available
     * label width.
     *
     * @param fm font metrics
     * @param text text
     * @param maxWidth maximum width
     * @return fitted text
     */
    private static String fitText(FontMetrics fm, String text, int maxWidth) {
        if (maxWidth <= 0 || text == null || text.isEmpty()) {
            return "";
        }
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = fm.stringWidth(ellipsis);
        if (maxWidth <= ellipsisWidth) {
            return "";
        }
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return end <= 0 ? "" : text.substring(0, end) + ellipsis;
    }

    /**
     * Returns the index with the largest absolute value.
     *
     * @param values values
     * @return index or -1
     */
    private static int strongestAbsIndex(float[] values) {
        if (values == null || values.length == 0) {
            return -1;
        }
        int best = 0;
        float bestAbs = Math.abs(values[0]);
        for (int i = 1; i < values.length; i++) {
            float value = Math.abs(values[i]);
            if (value > bestAbs) {
                bestAbs = value;
                best = i;
            }
        }
        return best;
    }

    /**
     * Outlines one row-major cell in a heatmap grid.
     *
     * @param g graphics
     * @param grid grid rectangle
     * @param index row-major cell index
     * @param cols column count
     * @param rows row count
     * @param color outline colour
     */
    private static void drawGridCellRing(Graphics2D g, Rectangle grid, int index,
            int cols, int rows, Color color) {
        if (index < 0 || cols <= 0 || rows <= 0 || index >= cols * rows) {
            return;
        }
        int col = index % cols;
        int row = index / cols;
        double cellW = grid.width / (double) cols;
        double cellH = grid.height / (double) rows;
        int x = (int) Math.floor(grid.x + col * cellW);
        int y = (int) Math.floor(grid.y + row * cellH);
        int w = Math.max(2, (int) Math.ceil(cellW));
        int h = Math.max(2, (int) Math.ceil(cellH));
        g.setColor(WorkbenchTheme.withAlpha(Color.WHITE, 220));
        g.drawRect(x, y, w - 1, h - 1);
        g.setColor(color);
        g.drawRect(x + 1, y + 1, Math.max(1, w - 3), Math.max(1, h - 3));
    }

    /**
     * Recomputes the visible-slot / visible-feature index lists from the
     * snapshot.
     */
    private void rebuildVisibleSelection() {
        int selectedSlotId = selectedSlot >= 0 && selectedSlot < visibleSlots.length
                ? visibleSlots[selectedSlot]
                : -1;
        selectedSlot = -1;
        if (snapshot == null) {
            visibleSlots = new int[0];
            visibleFeatures = new int[0];
            return;
        }
        float[] slotRankValues = isStockfishSnapshot()
                ? snapshot.data("nnue.stockfish.transformed.us")
                : totalContribution();
        if (slotRankValues == null) {
            visibleSlots = new int[0];
        } else {
            Integer[] ranked = new Integer[slotRankValues.length];
            for (int i = 0; i < slotRankValues.length; ++i) {
                ranked[i] = i;
            }
            java.util.Arrays.sort(ranked,
                    (a, b) -> Float.compare(Math.abs(slotRankValues[b]), Math.abs(slotRankValues[a])));
            int n = Math.min(TRACE_MAX_VISIBLE_SLOTS, ranked.length);
            visibleSlots = new int[n];
            for (int i = 0; i < n; ++i) {
                visibleSlots[i] = ranked[i];
            }
        }
        float[] featureIndices = snapshot.data("nnue.features.us.indices");
        float[] impact = snapshot.data("nnue.features.us.impact");
        int featureRows = featureIndices != null
                ? featureIndices.length
                : impact == null ? 0 : impact.length;
        if (featureRows == 0) {
            visibleFeatures = new int[0];
        } else {
            int n = Math.min(traceFeatureLanes(), featureRows);
            visibleFeatures = new int[n];
            for (int i = 0; i < n; ++i) {
                visibleFeatures[i] = i;
            }
        }
        if (selectedSlotId >= 0) {
            for (int i = 0; i < visibleSlots.length; ++i) {
                if (visibleSlots[i] == selectedSlotId) {
                    selectedSlot = i;
                    break;
                }
            }
        }
    }

    /**
     * Returns side-to-move-perspective feature text for a currently active
     * {@code us} feature.
     *
     * @param featureIndex sparse feature index
     * @return summary like "Kc4 / Nf6"
     */
    private String decodeUsHalfKP(int featureIndex) {
        return decodeHalfKP(featureIndex, sideToMoveWhite());
    }

    /**
     * Decodes a half-KP feature index into a human-friendly board-square
     * summary. The feature encoder stores squares in perspective-oriented
     * coordinates, so black-perspective labels are mirrored back to the board
     * before display.
     *
     * @param featureIndex sparse feature index
     * @param whitePerspective true when the feature was encoded from White's
     *     perspective
     * @return summary like "Kc4 / Nf6"
     */
    private static String decodeHalfKP(int featureIndex, boolean whitePerspective) {
        HalfKpFeature feature = decodeHalfKpFeature(featureIndex, whitePerspective);
        if (!feature.valid) {
            return "invalid";
        }
        char[] codes = { 'P', 'N', 'B', 'R', 'Q', 'p', 'n', 'b', 'r', 'q' };
        char pieceChar = feature.pieceCode < codes.length ? codes[feature.pieceCode] : '?';
        return "K" + WorkbenchTensorViz.squareLabel(feature.kingSquare)
                + " / " + pieceChar + WorkbenchTensorViz.squareLabel(feature.pieceSquare);
    }

    /**
     * Decodes feature components and maps oriented squares back to board
     * coordinates for display.
     *
     * @param featureIndex sparse feature index
     * @param whitePerspective true for White perspective, false for Black
     * @return decoded feature parts
     */
    private static HalfKpFeature decodeHalfKpFeature(int featureIndex, boolean whitePerspective) {
        if (featureIndex < 0 || featureIndex >= FeatureEncoder.FEATURE_COUNT) {
            return new HalfKpFeature(0, 0, 0, false);
        }
        int pieceSquare = featureIndex % FeatureEncoder.SQUARES;
        int packed = featureIndex / FeatureEncoder.SQUARES;
        int pieceCode = packed % FeatureEncoder.PIECE_PLANES;
        int kingSquare = packed / FeatureEncoder.PIECE_PLANES;
        return new HalfKpFeature(
                displaySquare(kingSquare, whitePerspective),
                pieceCode,
                displaySquare(pieceSquare, whitePerspective),
                true);
    }

    /**
     * Converts a perspective-oriented square back to board coordinates.
     *
     * @param square oriented square
     * @param whitePerspective perspective flag
     * @return display square
     */
    private static int displaySquare(int square, boolean whitePerspective) {
        return whitePerspective ? square : (square ^ 56);
    }

    /**
     * Returns whether the current FEN has White to move.
     *
     * @return true for White to move or unknown FENs
     */
    private boolean sideToMoveWhite() {
        if (fen == null || fen.isBlank()) {
            return true;
        }
        String[] parts = fen.trim().split("\\s+");
        return parts.length < 2 || !"b".equals(parts[1]);
    }

    /**
     * Returns a vector containing each slot's full output-head contribution.
     *
     * @return total contribution, or null when unavailable
     */
    private float[] totalContribution() {
        if (snapshot == null) {
            return null;
        }
        float[] total = snapshot.data("nnue.output.contribution.total");
        if (total != null) {
            return total;
        }
        float[] us = snapshot.data("nnue.output.contribution.us");
        float[] them = snapshot.data("nnue.output.contribution.them");
        if (us == null) {
            return them;
        }
        if (them == null) {
            return us;
        }
        int len = Math.min(us.length, them.length);
        float[] combined = new float[len];
        for (int i = 0; i < len; i++) {
            combined[i] = us[i] + them[i];
        }
        return combined;
    }

    /**
     * Returns true when the snapshot contains Stockfish layer-stack activations.
     *
     * @return true for Stockfish-shaped snapshots
     */
    private boolean isStockfishSnapshot() {
        return snapshot != null && snapshot.data("nnue.stockfish.fc0.raw") != null;
    }

    /**
     * Returns the fixed feature-lane count for the current NNUE architecture.
     *
     * @return feature-lane count
     */
    private int traceFeatureLanes() {
        return isStockfishSnapshot() ? STOCKFISH_TRACE_FEATURE_LANES : TRACE_FEATURE_LANES;
    }

    /**
     * Returns a centered y coordinate for a column with its own row count.
     *
     * @param layout layout
     * @param row row index
     * @param count row count
     * @return center y
     */
    private static int columnY(Layout layout, int row, int count) {
        if (count <= 1) {
            return (layout.graphTop + layout.graphBottom) / 2;
        }
        int pitch = adaptivePitch(layout.usableHeight, count, TRACE_SLOT_PITCH);
        int span = (count - 1) * pitch;
        int start = layout.graphTop + Math.max(0, (layout.usableHeight - span) / 2);
        return start + row * pitch;
    }

    /**
     * Computes a non-zero absolute scale for a dense matrix/vector.
     *
     * @param values values
     * @return scale
     */
    private static float matrixScale(float[] values) {
        float scale = maxAbs(values);
        return scale <= 0.0f ? 1.0f : scale;
    }

    /**
     * Computes a robust visible-slot scale for signed us-minus-them lane values.
     *
     * @param us side-to-move values
     * @param them opponent values
     * @param slots visible slots
     * @return non-zero scale
     */
    private static float visiblePairScale(float[] us, float[] them, int[] slots) {
        float scale = 0.0f;
        if (slots != null) {
            for (int idx : slots) {
                scale = Math.max(scale, Math.abs(valueAt(us, idx) - valueAt(them, idx)));
            }
        }
        if (scale <= 0.0f) {
            scale = Math.max(maxAbs(us), maxAbs(them));
        }
        return scale <= 0.0f ? 1.0f : scale;
    }

    /**
     * Computes a robust visible-slot scale for one signed vector.
     *
     * @param values values
     * @param slots visible slots
     * @return non-zero scale
     */
    private static float visibleAbsScale(float[] values, int[] slots) {
        float scale = 0.0f;
        if (slots != null) {
            for (int idx : slots) {
                scale = Math.max(scale, Math.abs(valueAt(values, idx)));
            }
        }
        if (scale <= 0.0f) {
            scale = maxAbs(values);
        }
        return scale <= 0.0f ? 1.0f : scale;
    }

    /**
     * Reads an array value, returning zero when the array is absent or too
     * short for a selected slot.
     *
     * @param values values
     * @param index index
     * @return value or 0
     */
    private static float valueAt(float[] values, int index) {
        return values == null || index < 0 || index >= values.length ? 0.0f : values[index];
    }

    /**
     * Decoded HalfKP feature components.
     */
    private static final class HalfKpFeature {

        private final int kingSquare;
        private final int pieceCode;
        private final int pieceSquare;
        private final boolean valid;

        HalfKpFeature(int kingSquare, int pieceCode, int pieceSquare, boolean valid) {
            this.kingSquare = kingSquare;
            this.pieceCode = pieceCode;
            this.pieceSquare = pieceSquare;
            this.valid = valid;
        }
    }

    /**
     * One active Half-KP feature ranked by signed centipawn impact.
     */
    private record FeatureDriver(int row, int featureIndex, float impact, int rank, boolean valid) {

        /**
         * Empty feature-driver sentinel.
         *
         * @return invalid driver
         */
        private static FeatureDriver invalid() {
            return new FeatureDriver(-1, -1, 0.0f, 0, false);
        }
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
         * Center x of the clipped-ReLU column.
         */
        private int clippedCx;

        /**
         * Center x of the output-contribution column.
         */
        private int contribCx;

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
         * Top of the graph content area.
         */
        private int graphTop;

        /**
         * Bottom of the graph content area.
         */
        private int graphBottom;

        /**
         * Usable graph height.
         */
        private int usableHeight;

        /**
         * Top y of the first feature node.
         */
        private int featureStartY;

        /**
         * Y pitch between slots.
         */
        private int slotPitch;

        /**
         * Y pitch between feature nodes.
         */
        private int featurePitch;

        /**
         * Bottom y of the last slot.
         */
        private int bottomY;

        /**
         * Bottom y of the last feature node.
         */
        private int featureBottomY;

        /**
         * Column-label baseline.
         */
        private int labelY;
    }
}
