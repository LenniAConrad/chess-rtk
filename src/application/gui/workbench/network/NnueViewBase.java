package application.gui.workbench.network;

import application.gui.workbench.ui.HitRegions;
import application.gui.workbench.ui.InspectorDialog;
import application.gui.workbench.ui.ScrollableSupport;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import chess.nn.nnue.FeatureEncoder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.Scrollable;
import javax.swing.KeyStroke;

import static application.gui.workbench.network.NnueAtlas.*;
import static application.gui.workbench.network.NnueDrawing.*;

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

public abstract class NnueViewBase extends NetworkView implements Scrollable {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    protected static final long serialVersionUID = 1L;

    /**
     * Vertical pitch for accumulator neurons in the Trace graph.
     */
    protected static final int TRACE_SLOT_PITCH = 22;

    /**
     * Maximum accumulator slots drawn in Trace mode. Trace is an instructional
     * path view that shows only the strongest-magnitude slots so the wiring
     * reads clearly; the dense all-slot diagram remains available in All mode.
     */
    protected static final int TRACE_MAX_VISIBLE_SLOTS = 16;

    /**
     * Fixed feature lanes in Trace mode. A legal position has at most 30
     * non-king pieces, so these lanes can show every active HalfKP feature while
     * leaving inactive lanes visible as placeholders.
     */
    protected static final int TRACE_FEATURE_LANES = FeatureEncoder.MAX_ACTIVE_FEATURES;

    /**
     * Stockfish HalfKAv2_hm can activate one feature per occupied square.
     */
    protected static final int STOCKFISH_TRACE_FEATURE_LANES = 32;

    /**
     * Vertical pitch for HalfKP feature lanes in the Trace graph.
     */
    protected static final int TRACE_FEATURE_PITCH = 40;

    /**
     * Header height for the ChessNNVisualizer-style Trace graph.
     */
    protected static final int TRACE_HEADER_H = 48;

    /**
     * Height of the layer-stack summary ribbon in Trace mode.
     */
    protected static final int TRACE_RIBBON_H = 92;

    /**
     * Short label describing the loaded NNUE version.
     */
    protected String versionLabel = "synthetic Stockfish-shaped (FEN-seeded)";

    /**
     * Selected accumulator slot in the wired diagram (-1 when none).
     */
    protected int selectedSlot = -1;

    /**
     * Cached visible-slot indices (mapping slot order -> accumulator index).
     */
    protected int[] visibleSlots = new int[0];

    /**
     * Cached visible-feature indices (mapping row -> feature index).
     */
    protected int[] visibleFeatures = new int[0];

    /**
     * Selected feature index inside the abstract-view feature list. Used to
     * highlight K/P squares on the position panel. -1 when nothing selected.
     */
    protected int selectedFeature = -1;

    /**
     * Selected hidden-neuron tile in atlas mode (-1 when none).
     */
    protected int atlasSelected = -1;

    /**
     * Zoomed hidden-neuron tile (-1 when the atlas mosaic is shown). Clicking
     * any tile in mosaic mode zooms into that slot; clicking again returns
     * to the mosaic.
     */
    protected int atlasZoomedSlot = -1;

    /**
     * Piece plane shown in the atlas detail board.
     */
    protected int atlasSelectedPlane;

    /**
     * Atlas neuron-sort mode (one of "magnitude", "sparsity", "piece focus",
     * "slot index").
     */
    protected String atlasSort = "magnitude";

    /**
     * When non-null, the atlas is rendered as the signed difference between
     * the current network's atlas and this comparison atlas — that is,
     * {@code current[i] − compare[i]}. A separate colormap (warm/cool) makes
     * the diff visually distinct from the normal absolute atlas.
     */
    protected ActivationSnapshot atlasCompare;

    /**
     * Output-overlay flag. When on every neuron row in the atlas is tinted
     * by its current output-head contribution magnitude so the user can see
     * which slots matter most to the present eval at a glance.
     */
    protected boolean atlasOverlay;

    /**
     * Grid view: when true the atlas paint path renders every discovered
     * variant as a small thumbnail side-by-side instead of the single
     * primary atlas.
     */
    protected boolean atlasGrid;

    /**
     * Atlases for the grid view, kept in a separate list so the renderer
     * can iterate over them without rebuilding the structure on every paint.
     */
    protected List<NetworkPanel.NnueAtlasEntry> atlasGridEntries = new ArrayList<>();

    /**
     * Selected board square (0..63, LERF). Set when the user clicks a
     * square on the mini-board or in the inspector. When set, the atlas
     * dims every cell whose board square doesn't match so the user can
     * see how every slot weighs that specific square.
     */
    protected int selectedBoardSquare = -1;

    /**
     * Creates the NNUE view.
     */
    protected NnueViewBase() {
        super(720, 540);
        setFocusable(true);
        installAtlasKeyboardNavigation();
    }

    /**
     * Installs local arrow-key navigation for the whole pixel-plane atlas.
     * The binding is focus-scoped so the global board-navigation shortcuts
     * keep working unless the user has clicked into the network view.
     */
    private void installAtlasKeyboardNavigation() {
        bindAtlasNavigationKey(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up", 0, -1);
        bindAtlasNavigationKey(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down", 0, 1);
        bindAtlasNavigationKey(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "left", -1, 0);
        bindAtlasNavigationKey(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "right", 1, 0);
        bindAtlasNavigationKey(KeyStroke.getKeyStroke(KeyEvent.VK_KP_UP, 0), "keypadUp", 0, -1);
        bindAtlasNavigationKey(KeyStroke.getKeyStroke(KeyEvent.VK_KP_DOWN, 0), "keypadDown", 0, 1);
        bindAtlasNavigationKey(KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, 0), "keypadLeft", -1, 0);
        bindAtlasNavigationKey(KeyStroke.getKeyStroke(KeyEvent.VK_KP_RIGHT, 0), "keypadRight", 1, 0);
    }

    /**
     * Binds one keyboard stroke to atlas selection movement.
     *
     * @param stroke key stroke
     * @param name stable action suffix
     * @param dx horizontal visual-bank movement
     * @param dy vertical row movement
     */
    private void bindAtlasNavigationKey(KeyStroke stroke, String name, int dx, int dy) {
        if (stroke == null) {
            return;
        }
        String actionName = "nnueAtlasNavigate." + name;
        getInputMap(JComponent.WHEN_FOCUSED).put(stroke, actionName);
        getActionMap().put(actionName, new AbstractAction() {
            /**
             * Serialization identifier for Swing action compatibility.
             */
            private static final long serialVersionUID = 1L;

            /**
             * Runs the atlas navigation action.
             *
             * @param event action event
             */
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                if (isAtlas() && navigateAtlasSelection(dx, dy)) {
                    repaint();
                }
            }
        });
    }

    /**
     * Moves atlas selection in response to arrow-key navigation.
     *
     * @param dx horizontal visual-bank movement
     * @param dy vertical row movement
     * @return true when selection changed
     */
    protected boolean navigateAtlasSelection(int dx, int dy) {
        return false;
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
        HitRegions.Region r = hitRegions.hitTest(event.getX(), event.getY());
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
                String sqLabel = TensorViz.squareLabel(sq);
                String extra = String.format(
                        "<br><b>%s</b>: <span style='color:%s;'>%+.3f</span>",
                        sqLabel,
                        Theme.css(v >= 0 ? TensorViz.POSITIVE : TensorViz.NEGATIVE),
                        v);
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
     * Sets the atlas neuron-sort mode.
     *
     * @param mode sort label
     */
    public void setAtlasSort(String mode) {
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
    public void setAtlasCompareData(ActivationSnapshot compare) {
        this.atlasCompare = compare;
        repaint();
    }

    /**
     * Sets the output-overlay flag.
     *
     * @param value true to overlay output-head contribution
     */
    public void setAtlasOverlay(boolean value) {
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
    public void setAtlasGrid(boolean value,
            List<NetworkPanel.NnueAtlasEntry> entries) {
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
    public void setSelectedBoardSquare(int square) {
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
        return ScrollableSupport.preferredViewportSize(this);
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
        return ScrollableSupport.DEFAULT_UNIT_INCREMENT;
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
        return ScrollableSupport.blockIncrement(visibleRect, orientation, 48);
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
        return true;
    }

    /**
     * Returns the preferred component size for trace and atlas modes.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        if (isAtlas() && snapshot != null) {
            return new Dimension(1120, 760);
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
    protected int atlasHiddenSize() {
        if (snapshot == null) {
            return 0;
        }
        int[] shape = snapshot.shape("nnue.atlas.weights");
        return shape != null && shape.length >= 1 ? shape[0] : 0;
    }

    /**
     * Sets a label describing the loaded NNUE network version (or "synthetic").
     *
     * @param label short version label
     */
    public void setVersionLabel(String label) {
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
            NnueTraceLayout layout = layout(wire);
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
                    long dx = (long) x - cx;
                    long dy = (long) y - cy;
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
        HitRegions.Region r = hitRegions.hitTest(x, y);
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
            int sq = TensorViz.boardSquareAt(r.bounds, x, y, sideToMoveWhite());
            if (sq < 0) {
                return;
            }
            selectedBoardSquare = (selectedBoardSquare == sq) ? -1 : sq;
            repaint();
            return;
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
            InspectorDialog.shared(this).inspect(r, snapshot);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String emptyStateTitle() {
        return "No NNUE snapshot yet";
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
        g.setColor(Theme.PANEL_SOLID);
        g.fillRoundRect(shell.x, shell.y, shell.width, shell.height,
                Theme.RADIUS, Theme.RADIUS);
        g.setColor(Theme.LINE);
        g.drawRoundRect(shell.x, shell.y, shell.width - 1, shell.height - 1,
                Theme.RADIUS, Theme.RADIUS);
        g.setColor(TensorViz.FOCUS);
        g.fillRoundRect(shell.x, shell.y, 5, shell.height,
                Theme.RADIUS, Theme.RADIUS);

        int titleW = Math.min(220, Math.max(150, shell.width / 4));
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(15, Font.BOLD));
        g.drawString("NNUE inspector", shell.x + 14, shell.y + 19);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(11, Font.PLAIN));
        FontMetrics versionMetrics = g.getFontMetrics();
        if (versionLabel != null && !versionLabel.isEmpty()) {
            g.drawString(Ui.elide(versionLabel, versionMetrics, titleW - 18),
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
                        stockfishStackSummary(transformed, transformedUs, fc0, fc1)
                }
                : new String[] {
                        activeUs + " / " + activeThem,
                        String.format("%+.2f", affine),
                        slots == 0 ? "--" : String.valueOf(slots)
                };
        Color[] accents = {
                TensorViz.FOCUS,
                affine >= 0.0f ? TensorViz.POSITIVE : TensorViz.NEGATIVE,
                TensorViz.TRUNK
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
        TensorViz.drawHorizontalBar(g, barRect, centipawns, Math.max(80.0f, Math.abs(centipawns) * 1.4f),
                String.format("%+d cp", Math.round(centipawns)));
        hitRegions.addInspectable(barRect,
                "Final centipawn eval",
                "Network output after scaling. Positive favors side to move.",
                String.format("%+d cp · affine %+.2f", Math.round(centipawns), affine),
                "nnue.output.centipawns", 0, 0, 0, "1");
    }

    /**
     * Computes the detailed trace layout for the supplied wire graph bounds.
     *
     * @param r graph bounds
     * @return trace layout
     */
    protected abstract NnueTraceLayout layout(Rectangle r);

    /**
     * Rebuilds visible slot and feature arrays after filter changes.
     */
    protected void rebuildVisibleSelection() {
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
            // Force-include the pinned slot so a user-selected lane stays in
            // the visible set even when a new snapshot pushes it out of the
            // top-N ranking. Without this, switching to a new MCTS leaf
            // re-ranks slots and the previous selection silently drops.
            boolean forceSelected = selectedSlotId >= 0 && selectedSlotId < slotRankValues.length;
            visibleSlots = new int[n];
            int written = 0;
            if (forceSelected) {
                visibleSlots[written++] = selectedSlotId;
            }
            for (int i = 0; i < ranked.length && written < n; ++i) {
                int slot = ranked[i];
                if (forceSelected && slot == selectedSlotId) {
                    continue;
                }
                visibleSlots[written++] = slot;
            }
            if (written < n) {
                visibleSlots = java.util.Arrays.copyOf(visibleSlots, written);
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
    protected String decodeUsHalfKP(int featureIndex) {
    return decodeHalfKP(featureIndex, sideToMoveWhite());
    }

    /**
     * Returns a feature label correct for the loaded network. CRTK nets use HalfKP
     * (king-square + piece + square), so the index decodes to "Kc4 / Nf6". Upstream
     * (Stockfish) nets use HalfKAv2_hm, an entirely different sparse encoding whose
     * indices must NOT be cracked open with the HalfKP formula — doing so prints a
     * bogus "king" square that has nothing to do with the board. For those, show
     * the raw feature index instead.
     *
     * @param featureIndex sparse feature index
     * @return a decoded HalfKP label, or a raw HalfKAv2 index for upstream nets
     */
    protected String featureLabel(int featureIndex) {
        return isStockfishSnapshot() ? "HalfKAv2 #" + featureIndex : decodeUsHalfKP(featureIndex);
    }

    /**
     * Returns whether HalfKP king/piece glyphs and board overlays are meaningful
     * for the loaded snapshot. They are not for upstream (Stockfish HalfKAv2_hm)
     * nets, whose feature indices are not HalfKP.
     *
     * @return true when HalfKP feature visualizations are valid
     */
    protected boolean halfKpFeaturesDecodable() {
        return !isStockfishSnapshot();
    }

    /**
     * Retained for reflective regression coverage of the HalfKP decoder.
     *
     * @param featureIndex sparse feature index
     * @param whitePerspective true when encoded from White's perspective
     * @return summary like "Kc4 / Nf6"
     */
    protected static String decodeHalfKP(int featureIndex, boolean whitePerspective) {
        return NnueFeatureDecoder.decodeHalfKP(featureIndex, whitePerspective);
    }

    /**
     * Returns whether the current FEN has White to move.
     *
     * @return true for White to move or unknown FENs
     */
    protected boolean sideToMoveWhite() {
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
    protected float[] totalContribution() {
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
    protected boolean isStockfishSnapshot() {
        return snapshot != null && snapshot.data("nnue.stockfish.fc0.raw") != null;
    }

    /**
     * Returns the fixed feature-lane count for the current NNUE architecture.
     *
     * @return feature-lane count
     */
    protected int traceFeatureLanes() {
    return isStockfishSnapshot() ? STOCKFISH_TRACE_FEATURE_LANES : TRACE_FEATURE_LANES;
    }
}
