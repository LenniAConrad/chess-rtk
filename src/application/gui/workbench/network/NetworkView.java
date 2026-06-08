package application.gui.workbench.network;

import application.gui.workbench.ui.HitRegions;
import application.gui.workbench.ui.InspectorPanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;

/**
 * Shared base for the workbench network visualisers (NNUE / CNN / BT4 / OTIS).
 *
 * <p>Before this class existed each view re-implemented the same scaffolding:
 * the {@code paintComponent} skeleton, the empty/header/body split, the
 * mode-flag plumbing, the fixed-scale colour-pinning helpers, the hit-region
 * registry and the tooltip/mouse wiring. Those are all collected here so the
 * subclasses only own the parts that are genuinely architecture-specific — the
 * actual drawing of each {@link ViewMode}.</p>
 *
 * <p>State that crosses the inference boundary ({@link #snapshot}) is only ever
 * a {@linkplain ActivationSnapshot#isSealed() sealed} snapshot, so the
 * EDT can paint it while a background worker builds the next one without any
 * shared mutable state.</p>
 */
public abstract class NetworkView extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Outer padding inside the panel, shared by every view.
     */
    protected static final int PAD = 12;

    /**
     * Y offset where the body region starts, leaving room for the header band.
     */
    protected static final int BODY_TOP = 64;

    /**
     * Latest sealed activation snapshot, or null before the first inference.
     */
    protected ActivationSnapshot snapshot;

    /**
     * Current position FEN, used for mini-board piece rendering.
     */
    protected String fen;

    /**
     * Active rendering mode. Exactly one mode is in effect at any time.
     */
    private ViewMode mode = ViewMode.ABSTRACT;

    /**
     * Whether to pin colour scales across position changes so heatmaps stay
     * comparable when the user flips between positions.
     */
    protected boolean fixedScale;

    /**
     * Shared inspector panel; null until the host wires one in.
     */
    protected InspectorPanel inspector;

    /**
     * Hover + click region registry, rebuilt every paint pass.
     */
    protected final HitRegions hitRegions = new HitRegions();

    /**
     * Per-bucket pinned heatmap scales, consulted by {@link #scaleFor} while
     * {@link #fixedScale} is on so colour ranges stay comparable across
     * position changes. Cleared whenever fixed-scale is toggled.
     */
    private final Map<String, Float> pinnedScales = new HashMap<>();

    /**
     * Creates a network view with the given default preferred size.
     *
     * @param preferredWidth default preferred width in pixels
     * @param preferredHeight default preferred height in pixels
     */
    protected NetworkView(int preferredWidth, int preferredHeight) {
        setOpaque(true);
        setBackground(Theme.BG);
        setPreferredSize(new Dimension(preferredWidth, preferredHeight));
        ToolTipManager.sharedInstance().registerComponent(this);
        addMouseListener(new MouseAdapter() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                onClick(event.getX(), event.getY());
            }
        });
    }

    /**
     * Wires the shared inspector panel.
     *
     * @param panel inspector (may be null to detach)
     */
    public final void setInspector(InspectorPanel panel) {
        this.inspector = panel;
    }

    /**
     * Sets the activation snapshot. The snapshot must be sealed; the view
     * never mutates it and may keep the reference for the lifetime of the
     * displayed position.
     *
     * @param newSnapshot sealed snapshot, or null to clear
     */
    public final void setSnapshot(ActivationSnapshot newSnapshot) {
        Dimension previousPreferred = getPreferredSize();
        this.snapshot = newSnapshot;
        onSnapshotChanged();
        Dimension nextPreferred = getPreferredSize();
        if (!nextPreferred.equals(previousPreferred)) {
            invalidate();
            revalidate();
        }
        repaint();
    }

    /**
     * Hook invoked after {@link #setSnapshot} stores a new snapshot but before
     * the repaint. Subclasses override this to rebuild cached, derived
     * structures (layer summaries, visible-slot selections, ...).
     */
    protected void onSnapshotChanged() {
        // default: nothing derived
    }

    /**
     * Sets the current FEN for mini-board piece rendering.
     *
     * @param newFen position FEN
     */
    public final void setFen(String newFen) {
        this.fen = newFen;
        repaint();
    }

    /**
     * Sets the fixed-scale flag. Resets pinned heatmap scales when the toggle
     * flips so the next snapshot starts from a fresh baseline.
     *
     * @param value true to pin heatmap scales
     */
    public final void setFixedScale(boolean value) {
        if (this.fixedScale != value) {
            this.fixedScale = value;
            pinnedScales.clear();
            repaint();
        }
    }

    /**
     * Sets the active rendering mode. Triggers a relayout + repaint and the
     * {@link #onViewModeChanged} hook when the mode actually changes.
     *
     * @param newMode new rendering mode (ignored when null)
     */
    public final void setViewMode(ViewMode newMode) {
        if (newMode != null && this.mode != newMode) {
            this.mode = newMode;
            onViewModeChanged();
            invalidate();
            revalidate();
            repaint();
        }
    }

    /**
     * Returns the active rendering mode.
     *
     * @return current mode
     */
    protected final ViewMode viewMode() {
        return mode;
    }

    /**
     * Hook invoked after {@link #setViewMode} changes the mode. Subclasses
     * override this to reset mode-scoped selection state (selected slot,
     * cached hit-boxes, ...).
     */
    protected void onViewModeChanged() {
        // default: no mode-scoped state to reset
    }

    /**
     * Returns whether the abstract mode is active.
     *
     * @return true in abstract mode
     */
    protected final boolean isAbstract() {
        return mode == ViewMode.ABSTRACT;
    }

    /**
     * Returns whether the detailed mode is active.
     *
     * @return true in detailed mode
     */
    protected final boolean isDetailed() {
        return mode == ViewMode.DETAILED;
    }

    /**
     * Returns whether the raw mode is active.
     *
     * @return true in raw mode
     */
    protected final boolean isRaw() {
        return mode == ViewMode.RAW;
    }

    /**
     * Returns whether the atlas mode is active.
     *
     * @return true in atlas mode
     */
    protected final boolean isAtlas() {
        return mode == ViewMode.ATLAS;
    }

    /**
     * Returns whether the diagram mode is active.
     *
     * @return true in diagram mode
     */
    protected final boolean isDiagram() {
        return mode == ViewMode.DIAGRAM;
    }

    /**
     * Returns the colour-scale for a heatmap bucket. With fixed-scale off the
     * bucket is cleared and the dynamic max is returned; with fixed-scale on
     * the bucket's pinned scale grows monotonically across position changes so
     * heatmaps stay comparable.
     *
     * @param key bucket key
     * @param dynamicMax max-abs of the current snapshot data
     * @return scale to use (always &gt; 0)
     */
    protected final float scaleFor(String key, float dynamicMax) {
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
     * Returns the max absolute value in a float array, or 0 when null/empty.
     *
     * @param data values
     * @return max absolute value
     */
    protected static float maxAbs(float[] data) {
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
     * Returns the hover-tooltip text for the hit region under the cursor.
     * Subclasses may override to enrich the tooltip but should fall back to
     * this implementation for the base behaviour.
     *
     * @param event mouse event
     * @return HTML tooltip text or null
     */
    @Override
    public String getToolTipText(MouseEvent event) {
        HitRegions.Region r = hitRegions.hitTest(event.getX(), event.getY());
        return r == null ? null : r.tooltipHtml();
    }

    /**
     * Paints the panel: clears the background, rebuilds the hit-region
     * registry, then either paints the empty placeholder or the header plus
     * the body for the {@linkplain #viewMode() active mode}.
     *
     * @param graphics graphics context
     */
    @Override
    protected final void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (isOpaque()) {
            graphics.setColor(getBackground());
            graphics.fillRect(0, 0, getWidth(), getHeight());
        }
        hitRegions.clear();
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            TensorViz.useHighQuality(g);
            Rectangle bounds = new Rectangle(0, 0, getWidth(), getHeight());
            if (snapshot == null || snapshot.isEmpty()) {
                paintEmpty(g, bounds);
                return;
            }
            paintHeader(g, bounds);
            Rectangle body = new Rectangle(PAD, BODY_TOP,
                    getWidth() - 2 * PAD, getHeight() - BODY_TOP - PAD);
            switch (mode) {
                case DIAGRAM -> paintDiagram(g, body);
                case ATLAS -> paintAtlas(g, body);
                case RAW -> paintRaw(g, body);
                case DETAILED -> paintDetailed(g, body);
                case ABSTRACT -> paintAbstract(g, body);
                default -> paintAbstract(g, body);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints the placeholder shown when there is no snapshot yet: a centered
     * loading/empty state, consistent across every network view.
     *
     * @param g graphics
     * @param bounds full component bounds
     */
    protected void paintEmpty(Graphics2D g, Rectangle bounds) {
        g.setColor(Theme.BG);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        Ui.paintEmptyState(g, bounds, emptyStateTitle(), emptyStateHint());
    }

    /**
     * Returns the centered empty-state title; subclasses name their network
     * kind (e.g. "No NNUE snapshot yet").
     *
     * @return empty-state title
     */
    protected String emptyStateTitle() {
        return "No evaluator snapshot yet";
    }

    /**
     * Returns the one-line hint shown beneath the empty-state title.
     *
     * @return empty-state hint
     */
    protected String emptyStateHint() {
        return "Run PUCT or load an evaluator to populate this view.";
    }

    /**
     * Paints the title/header band above the body.
     *
     * @param g graphics
     * @param bounds full component bounds
     */
    protected abstract void paintHeader(Graphics2D g, Rectangle bounds);

    /**
     * Paints the abstract (summary) mode.
     *
     * @param g graphics
     * @param body body rectangle below the header
     */
    protected abstract void paintAbstract(Graphics2D g, Rectangle body);

    /**
     * Paints the detailed (drill-down) mode.
     *
     * @param g graphics
     * @param body body rectangle below the header
     */
    protected abstract void paintDetailed(Graphics2D g, Rectangle body);

    /**
     * Paints the raw (dense mosaic) mode.
     *
     * @param g graphics
     * @param body body rectangle below the header
     */
    protected abstract void paintRaw(Graphics2D g, Rectangle body);

    /**
     * Paints the atlas (learned-weight) mode. Views without a bespoke atlas
     * route this onto {@link #paintRaw}.
     *
     * @param g graphics
     * @param body body rectangle below the header
     */
    protected abstract void paintAtlas(Graphics2D g, Rectangle body);

    /**
     * Paints the diagram (architecture schematic) mode.
     *
     * @param g graphics
     * @param body body rectangle below the header
     */
    protected abstract void paintDiagram(Graphics2D g, Rectangle body);

    /**
     * Handles a mouse press at the given component coordinates.
     *
     * @param x x coordinate
     * @param y y coordinate
     */
    protected abstract void onClick(int x, int y);
}
