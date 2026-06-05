package application.gui.workbench.mcts;

import application.gui.workbench.board.BoardStyle;
import application.gui.workbench.network.TensorViz;
import application.gui.workbench.ui.NotationPainter;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import chess.core.Move;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;

/**
 * Pan/zoom canvas that renders a laid-out MCTS search tree.
 *
 * <p>Each node shows a mini board for its position; parent/child edges are drawn
 * as solid links, transpositions as dashed links, and the principal variation is
 * accented. To stay smooth on very large trees (hundreds of thousands of nodes),
 * the view caches a key lookup and a per-layer spatial index when the model
 * changes, culls to the visible layer/x-span on every frame instead of scanning
 * the whole tree, reuses a single edge path, and renders each position's board
 * once into a FEN-keyed high-resolution tile that is bilinear-downscaled to the
 * on-screen size (so thumbnails stay crisp at any zoom).</p>
 */
public final class TreeGraphView extends JComponent {

    /**
     * Serialization identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Minimum zoom factor (deep zoom-out for very wide trees).
     */
    private static final double MIN_ZOOM = 0.01;

    /**
     * Maximum zoom factor (deep zoom-in onto a single node's board).
     */
    private static final double MAX_ZOOM = 8.0;

    /**
     * Board pixel size below which nodes collapse to compact boxes.
     */
    private static final int BOARD_PIXEL_THRESHOLD = 26;

    /**
     * Fixed high-resolution size each position's board is rendered at and cached;
     * the on-screen thumbnail is a bilinear downscale of this tile, so one cache
     * entry per position serves every zoom/board-size without re-rendering.
     */
    private static final int BOARD_TILE = 192;

    /**
     * Maximum cached board images.
     */
    private static final int CACHE_LIMIT = 384;

    /**
     * Pixel movement treated as a drag rather than a click.
     */
    private static final int DRAG_SLOP = 4;

    /**
     * World-space padding added around the viewport when culling.
     */
    private static final int CULL_PAD = 48;

    /**
     * Horizontal padding inside a node caption.
     */
    private static final int CAPTION_PAD_X = 8;

    /**
     * Width of the value-color stripe on a node caption.
     */
    private static final int CAPTION_ACCENT_WIDTH = 3;

    /**
     * Current layout model.
     */
    private transient TreeLayout.Model model =
            new TreeLayout.Model(java.util.List.of(), java.util.List.of(), 0, 0, null, 0, 0);

    /**
     * Key-to-node lookup for the current model (rebuilt only on model change).
     */
    private transient Map<String, TreeLayout.Node> byKey = Map.of();

    /**
     * Per-layer node arrays, each sorted by x, for spatial cull and hit-testing
     * (rebuilt only on model change). Layers are keyed by depth so the visible
     * band is found without scanning the whole tree.
     */
    private transient TreeMap<Integer, TreeLayout.Node[]> rowsByLayer = new TreeMap<>();

    /**
     * Reused edge path, to avoid allocating one per edge per frame.
     */
    private final transient Path2D.Double edgePath = new Path2D.Double();

    /**
     * Current zoom factor.
     */
    private double zoom = 1.0;

    /**
     * Horizontal pan offset, device pixels.
     */
    private double panX;

    /**
     * Vertical pan offset, device pixels.
     */
    private double panY;

    /**
     * True until the first model is fitted to the viewport.
     */
    private boolean needsInitialFit = true;

    /**
     * Press location for drag detection.
     */
    private Point pressPoint;

    /**
     * True once the pointer has moved beyond the drag slop.
     */
    private boolean dragging;

    /**
     * LRU board-image cache keyed by FEN (rendered at {@link #BOARD_TILE}).
     */
    private final transient Map<String, BufferedImage> boardCache =
            new LinkedHashMap<>(64, 0.75f, true) {
                private static final long serialVersionUID = 1L;

                /**
                 * Removes the eldest cached board image when the cache is full.
                 *
                 * @param eldest eldest cache entry
                 * @return true when the eldest entry should be removed
                 */
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
                    return size() > CACHE_LIMIT;
                }
            };

    /**
     * Accent for the live search path (distinct from the blue PV).
     */
    private static final Color SEARCH_COLOR = new Color(0xFF, 0xB4, 0x54);

    /**
     * Emerald ring marking the clicked/selected node (distinct from the blue PV
     * and the amber search path).
     */
    private static final Color SELECT_COLOR = new Color(0x3D, 0xD6, 0x7E);

    /**
     * Teal accent marking a user's tracked target move line.
     */
    private static final Color TARGET_COLOR = new Color(0x2D, 0xD4, 0xBF);

    /**
     * Keys on the path to the leaf the search is currently evaluating.
     */
    private transient java.util.Set<String> searchPath = java.util.Set.of();

    /**
     * Keys on the user's tracked target line that currently exist in the tree.
     */
    private transient java.util.Set<String> targetPath = java.util.Set.of();

    /**
     * True while a target line is being tracked: nodes/edges off the tracked
     * branch are dimmed toward the background so the branch stands out.
     */
    private transient boolean targetFocus;

    /**
     * Whether to draw depth-level separator lines and labels (like an image
     * editor's layer guides) so it is clear which ply each row is.
     */
    private transient boolean showLayers;

    /**
     * Composite that fades off-branch nodes/edges toward the background while a
     * target line is tracked (darker in dark mode, washed out in light mode).
     */
    private static final java.awt.AlphaComposite DIM_COMPOSITE =
            java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.20f);

    /**
     * Selection callback invoked with the clicked node.
     */
    private transient Consumer<TreeLayout.Node> selectionListener;

    /**
     * Creates the view.
     */
    public TreeGraphView() {
        setOpaque(true);
        setBackground(Theme.BG);
        setFocusable(true);
        MouseAdapter mouse = new MouseAdapter() {
            /**
             * Starts a possible pan or selection gesture.
             *
             * @param event mouse press event
             */
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                pressPoint = event.getPoint();
                dragging = false;
            }

            /**
             * Pans the tree after the drag threshold is crossed.
             *
             * @param event mouse drag event
             */
            @Override
            public void mouseDragged(MouseEvent event) {
                if (pressPoint == null) {
                    return;
                }
                if (!dragging && pressPoint.distance(event.getPoint()) > DRAG_SLOP) {
                    dragging = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
                if (dragging) {
                    panX += event.getX() - pressPoint.x;
                    panY += event.getY() - pressPoint.y;
                    pressPoint = event.getPoint();
                    repaint();
                }
            }

            /**
             * Finishes a pan gesture or selects the clicked node.
             *
             * @param event mouse release event
             */
            @Override
            public void mouseReleased(MouseEvent event) {
                setCursor(Cursor.getDefaultCursor());
                if (!dragging && pressPoint != null) {
                    selectAt(event.getPoint());
                }
                pressPoint = null;
                dragging = false;
            }

            /**
             * Zooms the graph around the wheel location.
             *
             * @param event wheel event
             */
            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                zoomAt(event.getPoint(), event.getWheelRotation() < 0 ? 1.12 : 1.0 / 1.12);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    /**
     * Sets the selection callback.
     *
     * @param listener callback invoked when a node is clicked
     */
    public void setSelectionListener(Consumer<TreeLayout.Node> listener) {
        selectionListener = listener;
    }

    /**
     * Sets the keys on the live search path (root to the leaf currently being
     * evaluated). Painted as an amber overlay on top of the tree.
     *
     * @param keys node keys on the search path
     */
    public void setSearchPath(java.util.Set<String> keys) {
        searchPath = keys == null ? java.util.Set.of() : keys;
        repaint();
    }

    /**
     * Sets the keys of the user's tracked target line that currently exist in
     * the tree. Painted as a teal overlay so you can watch the search reach into
     * the line you are looking for.
     *
     * @param keys node keys on the target line
     */
    public void setTargetPath(java.util.Set<String> keys, boolean focus) {
        targetPath = keys == null ? java.util.Set.of() : keys;
        targetFocus = focus;
        repaint();
    }

    /**
     * Toggles the depth-level guide lines and labels.
     *
     * @param show true to draw level guides
     */
    public void setShowLayers(boolean show) {
        showLayers = show;
        repaint();
    }

    /**
     * Replaces the layout model and repaints.
     *
     * @param next next model
     */
    public void setModel(TreeLayout.Model next) {
        model = next == null
                ? new TreeLayout.Model(java.util.List.of(), java.util.List.of(), 0, 0, null, 0, 0)
                : next;
        rebuildIndex();
        if (needsInitialFit && !model.isEmpty()) {
            resetView();
            needsInitialFit = false;
        }
        repaint();
    }

    /**
     * Returns the current model (for export).
     *
     * @return current model
     */
    public TreeLayout.Model model() {
        return model;
    }

    /**
     * Clears the view to its empty state.
     */
    public void clear() {
        model = new TreeLayout.Model(java.util.List.of(), java.util.List.of(), 0, 0, null, 0, 0);
        rebuildIndex();
        needsInitialFit = true;
        repaint();
    }

    /**
     * Rebuilds the key lookup and per-layer spatial index for the current model.
     * Done once per model change so the per-frame paint and hit-test paths never
     * scan the whole tree.
     */
    private void rebuildIndex() {
        List<TreeLayout.Node> nodes = model.nodes();
        Map<String, TreeLayout.Node> keys = new HashMap<>(Math.max(16, nodes.size() * 2));
        Map<Integer, List<TreeLayout.Node>> byLayer = new HashMap<>();
        for (TreeLayout.Node node : nodes) {
            keys.put(node.key(), node);
            byLayer.computeIfAbsent(node.layer(), ignored -> new ArrayList<>()).add(node);
        }
        TreeMap<Integer, TreeLayout.Node[]> rows = new TreeMap<>();
        for (Map.Entry<Integer, List<TreeLayout.Node>> entry : byLayer.entrySet()) {
            List<TreeLayout.Node> row = entry.getValue();
            row.sort((a, b) -> Integer.compare(a.x(), b.x()));
            rows.put(entry.getKey(), row.toArray(new TreeLayout.Node[0]));
        }
        byKey = keys;
        rowsByLayer = rows;
    }

    /**
     * Fits the whole tree into the current viewport, centered both ways.
     */
    public void fit() {
        if (model.isEmpty() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        double sx = getWidth() / (double) Math.max(1, model.width());
        double sy = getHeight() / (double) Math.max(1, model.height());
        zoom = clampZoom(Math.min(sx, sy));
        panX = (getWidth() - model.width() * zoom) / 2.0;
        // Center vertically when the whole tree fits; only fall back to a top
        // margin when it is genuinely taller than the viewport (clamped zoom).
        double freeY = getHeight() - model.height() * zoom;
        panY = Math.max(MARGIN_TOP, freeY / 2.0);
        repaint();
    }

    /**
     * Top padding used when the tree is taller than the viewport.
     */
    private static final double MARGIN_TOP = 14.0;

    /**
     * Resets to a readable, root-anchored view. Boards stay legible (the zoom is
     * floored) and the root sits near the top-center, so a bushy tree is explored
     * by panning and zooming rather than collapsed to an unreadable strip. The
     * {@link #fit()} action remains available to zoom the whole tree into view.
     */
    public void resetView() {
        if (model.isEmpty() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        double fitZoom = Math.min(
                getWidth() / (double) Math.max(1, model.width()),
                getHeight() / (double) Math.max(1, model.height()));
        zoom = clampZoom(Math.min(1.0, Math.max(0.5, fitZoom)));
        double rootCenterX = model.width() / 2.0;
        for (TreeLayout.Node node : model.nodes()) {
            if (node.key().equals(model.rootKey())) {
                rootCenterX = node.centerX();
                break;
            }
        }
        panX = getWidth() / 2.0 - rootCenterX * zoom;
        panY = MARGIN_TOP;
        repaint();
    }

    /**
     * Multiplies the zoom around the viewport center.
     *
     * @param factor zoom multiplier
     */
    public void zoomBy(double factor) {
        zoomAt(new Point(getWidth() / 2, getHeight() / 2), factor);
    }

    /**
     * Pans so a node (by key) is centered in the viewport at the current zoom.
     * Used by the navigation inspector to jump to a chosen node.
     *
     * @param key node key
     */
    public void focusNode(String key) {
        if (key == null) {
            return;
        }
        TreeLayout.Node node = byKey.get(key);
        if (node != null) {
            panX = getWidth() / 2.0 - node.centerX() * zoom;
            panY = getHeight() / 2.0 - node.centerY() * zoom;
            repaint();
        }
    }

    /**
     * Multiplies the zoom around an anchor point, keeping the model point under
     * the anchor fixed.
     *
     * @param anchor anchor in device pixels
     * @param factor zoom multiplier
     */
    private void zoomAt(Point anchor, double factor) {
        double next = clampZoom(zoom * factor);
        double ratio = next / zoom;
        panX = anchor.x - (anchor.x - panX) * ratio;
        panY = anchor.y - (anchor.y - panY) * ratio;
        zoom = next;
        repaint();
    }

    /**
     * Clamps a zoom factor to the supported range.
     *
     * @param value raw zoom
     * @return clamped zoom
     */
    private static double clampZoom(double value) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
    }

    /**
     * Selects the node under a device point, if any.
     *
     * @param device device point
     */
    private void selectAt(Point device) {
        TreeLayout.Node node = nodeAt(device);
        if (node != null && selectionListener != null) {
            selectionListener.accept(node);
        }
    }

    /**
     * Returns the node whose box contains a device point, or null. Uses the
     * per-layer index: the point's world-y selects at most one layer band, then
     * a binary search over that layer's x-sorted nodes locates the hit in
     * O(log n) instead of scanning every node.
     *
     * @param device device point
     * @return node or null
     */
    private TreeLayout.Node nodeAt(Point device) {
        double mx = (device.x - panX) / zoom;
        double my = (device.y - panY) / zoom;
        for (TreeLayout.Node[] row : rowsByLayer.values()) {
            if (row.length == 0) {
                continue;
            }
            TreeLayout.Node band = row[0];
            if (my < band.y() || my > band.y() + band.h()) {
                continue;
            }
            int lo = 0;
            int hi = row.length - 1;
            int candidate = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (row[mid].x() <= mx) {
                    candidate = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            if (candidate >= 0) {
                TreeLayout.Node node = row[candidate];
                if (mx <= node.x() + node.w() && my >= node.y() && my <= node.y() + node.h()) {
                    return node;
                }
            }
            return null;
        }
        return null;
    }

    /**
     * Returns the default graph view size.
     *
     * @return preferred component size
     */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(640, 460);
    }

    /**
     * Paints the tree graph.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setColor(Theme.BG);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (model.isEmpty()) {
                Ui.paintEmptyState(g, new Rectangle(0, 0, getWidth(), getHeight()),
                        "No search tree yet",
                        "Start PUCT to watch the tree grow, node by node.");
                return;
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (showLayers) {
                drawLayerLines(g);
            }
            Graphics2D world = (Graphics2D) g.create();
            try {
                world.translate(panX, panY);
                world.scale(zoom, zoom);
                world.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                Rectangle viewWorld = worldViewport();
                drawEdges(world, viewWorld);
                drawTargetOverlay(world, viewWorld);
                drawSearchOverlay(world, viewWorld);
                drawNodes(world, viewWorld);
            } finally {
                world.dispose();
            }
            drawLegend(g);
            if (showLayers) {
                drawLayerLabels(g);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Returns the world-space rectangle currently visible, padded for culling.
     *
     * @return visible world rectangle
     */
    private Rectangle worldViewport() {
        return new Rectangle(
                (int) ((-panX) / zoom) - CULL_PAD,
                (int) ((-panY) / zoom) - CULL_PAD,
                (int) (getWidth() / zoom) + 2 * CULL_PAD,
                (int) (getHeight() / zoom) + 2 * CULL_PAD);
    }

    /**
     * Draws the parent/child edges that touch the visible region.
     *
     * @param g world-space graphics
     * @param view visible world rectangle
     */
    private void drawEdges(Graphics2D g, Rectangle view) {
        Graphics2D edgeGraphics = (Graphics2D) g.create();
        try {
            for (TreeLayout.Edge edge : model.edges()) {
                TreeLayout.Node from = byKey.get(edge.fromKey());
                TreeLayout.Node to = byKey.get(edge.toKey());
                if (from == null || to == null || !edgeVisible(from, to, view)) {
                    continue;
                }
                edgeGraphics.setComposite(targetFocus
                        && !(targetPath.contains(edge.fromKey()) && targetPath.contains(edge.toKey()))
                        ? DIM_COMPOSITE : java.awt.AlphaComposite.SrcOver);
                int x1 = from.centerX();
                int y1 = from.y() + from.h();
                int x2 = to.centerX();
                int y2 = to.y();
                boolean pv = from.onPrincipalVariation() && to.onPrincipalVariation() && !edge.transposition();
                if (edge.transposition()) {
                    edgeGraphics.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            1f, new float[] { 5f, 5f }, 0f));
                    edgeGraphics.setColor(Theme.withAlpha(TensorViz.POLICY, 190));
                } else if (pv) {
                    edgeGraphics.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    edgeGraphics.setColor(Theme.withAlpha(Theme.ACCENT, 235));
                } else {
                    edgeGraphics.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    edgeGraphics.setColor(Theme.withAlpha(Theme.LINE, 220));
                }
                int midY = (y1 + y2) / 2;
                edgePath.reset();
                edgePath.moveTo(x1, y1);
                edgePath.curveTo(x1, midY, x2, midY, x2, y2);
                edgeGraphics.draw(edgePath);
            }
        } finally {
            edgeGraphics.dispose();
        }
    }

    /**
     * Returns whether an edge's bounding box intersects the visible region.
     *
     * @param from parent node
     * @param to child node
     * @param view visible world rectangle
     * @return true when potentially visible
     */
    private static boolean edgeVisible(TreeLayout.Node from, TreeLayout.Node to, Rectangle view) {
        int minX = Math.min(from.x(), to.x());
        int minY = Math.min(from.y(), to.y());
        int maxX = Math.max(from.x() + from.w(), to.x() + to.w());
        int maxY = Math.max(from.y() + from.h(), to.y() + to.h());
        return maxX >= view.x && minX <= view.x + view.width
                && maxY >= view.y && minY <= view.y + view.height;
    }

    /**
     * Overlays the live search path (root to the leaf currently being
     * evaluated) in amber on top of the base edges, so it reads as a second
     * "line" alongside the blue principal variation and moves as the search
     * picks new leaves.
     *
     * @param g world-space graphics
     * @param view visible world rectangle
     */
    private void drawSearchOverlay(Graphics2D g, Rectangle view) {
        drawPathOverlay(g, view, searchPath, SEARCH_COLOR);
    }

    /**
     * Overlays the user's tracked target line in teal, so you can watch the
     * search reach into the line you are looking for.
     *
     * @param g world-space graphics
     * @param view visible world rectangle
     */
    private void drawTargetOverlay(Graphics2D g, Rectangle view) {
        drawPathOverlay(g, view, targetPath, TARGET_COLOR);
    }

    /**
     * Draws a colored overlay along the edges whose endpoints are both in a key
     * set, reusing the shared edge path.
     *
     * @param g world-space graphics
     * @param view visible world rectangle
     * @param keys node keys defining the path
     * @param color overlay color
     */
    private void drawPathOverlay(Graphics2D g, Rectangle view, java.util.Set<String> keys, Color color) {
        if (keys.isEmpty()) {
            return;
        }
        Graphics2D overlay = (Graphics2D) g.create();
        try {
            overlay.setComposite(java.awt.AlphaComposite.SrcOver);
            overlay.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            overlay.setColor(Theme.withAlpha(color, 245));
            for (TreeLayout.Edge edge : model.edges()) {
                if (edge.transposition() || !keys.contains(edge.fromKey()) || !keys.contains(edge.toKey())) {
                    continue;
                }
                TreeLayout.Node from = byKey.get(edge.fromKey());
                TreeLayout.Node to = byKey.get(edge.toKey());
                if (from == null || to == null || !edgeVisible(from, to, view)) {
                    continue;
                }
                int x1 = from.centerX();
                int y1 = from.y() + from.h();
                int x2 = to.centerX();
                int y2 = to.y();
                int midY = (y1 + y2) / 2;
                edgePath.reset();
                edgePath.moveTo(x1, y1);
                edgePath.curveTo(x1, midY, x2, midY, x2, y2);
                overlay.draw(edgePath);
            }
        } finally {
            overlay.dispose();
        }
    }

    /**
     * Draws the visible nodes, culling to the visible layer bands and x-spans via
     * the per-layer index so off-screen nodes are never touched.
     *
     * @param g world-space graphics
     * @param view visible world rectangle
     */
    private void drawNodes(Graphics2D g, Rectangle view) {
        double boardPx = 0;
        for (TreeLayout.Node node : model.nodes()) {
            if (node.w() > 0) {
                boardPx = node.w() * zoom;
                break;
            }
        }
        boolean compact = boardPx < BOARD_PIXEL_THRESHOLD;
        int viewRight = view.x + view.width;
        int viewBottom = view.y + view.height;
        for (TreeLayout.Node[] row : rowsByLayer.values()) {
            if (row.length == 0) {
                continue;
            }
            TreeLayout.Node band = row[0];
            if (band.y() + band.h() < view.y || band.y() > viewBottom) {
                continue;
            }
            int start = firstVisibleColumn(row, view.x);
            for (int i = start; i < row.length; i++) {
                TreeLayout.Node node = row[i];
                if (node.x() > viewRight) {
                    break;
                }
                if (node.x() + node.w() < view.x) {
                    continue;
                }
                Graphics2D nodeGraphics = (Graphics2D) g.create();
                try {
                    nodeGraphics.setComposite(targetFocus && !targetPath.contains(node.key())
                            ? DIM_COMPOSITE : java.awt.AlphaComposite.SrcOver);
                    if (node.blob()) {
                        drawBlobNode(nodeGraphics, node, compact);
                    } else if (compact) {
                        drawCompactNode(nodeGraphics, node);
                    } else {
                        drawBoardNode(nodeGraphics, node);
                    }
                } finally {
                    nodeGraphics.dispose();
                }
            }
        }
    }

    /**
     * Binary-searches an x-sorted layer row for the first node that can be
     * visible at the given left edge.
     *
     * @param row x-sorted nodes in a layer
     * @param viewLeft visible world left edge
     * @return index of the first possibly-visible node
     */
    private static int firstVisibleColumn(TreeLayout.Node[] row, int viewLeft) {
        int lo = 0;
        int hi = row.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (row[mid].x() + row[mid].w() < viewLeft) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /**
     * Draws a batched-leaf blob node as a stacked card with a count.
     *
     * @param g world-space graphics
     * @param node blob node
     * @param compact true when zoomed far out
     */
    private void drawBlobNode(Graphics2D g, TreeLayout.Node node, boolean compact) {
        Rectangle box = new Rectangle(node.x(), node.y(), node.w(), node.h());
        Graphics2D blob = (Graphics2D) g.create();
        try {
            blob.setClip(box);
            // A short stack of offset cards reads as "many positions".
            int layers = Math.min(3, Math.max(2, node.members().size()));
            for (int i = layers - 1; i >= 0; i--) {
                int off = i * Math.max(2, node.w() / 22);
                blob.setColor(Theme.withAlpha(Theme.ELEVATED_SOLID, i == 0 ? 255 : 150));
                blob.fillRoundRect(box.x + off, box.y + off, box.width - off, box.height - off, 8, 8);
            }
            int total = 0;
            for (MctsSearch.NodeInfo m : node.members()) {
                total += m.visits();
            }
            int textX = box.x + CAPTION_PAD_X;
            int textW = Math.max(0, box.width - 2 * CAPTION_PAD_X);
            blob.setColor(Theme.TEXT);
            blob.setFont(Theme.font(compact ? Math.max(9, node.w() / 5) : 13, Font.BOLD));
            FontMetrics countMetrics = blob.getFontMetrics();
            String count = node.members().size() + " leaves";
            blob.drawString(Ui.elide(count, countMetrics, textW), textX, box.y + box.height / 2 - 2);
            if (!compact) {
                blob.setFont(Theme.font(10, Font.PLAIN));
                blob.setColor(Theme.MUTED);
                FontMetrics totalMetrics = blob.getFontMetrics();
                blob.drawString(Ui.elide("ΣN=" + compactCount(total), totalMetrics, textW),
                        textX, box.y + box.height / 2 + 14);
                blob.setColor(Theme.withAlpha(Theme.ACCENT, 200));
                blob.setFont(Theme.font(9, Font.BOLD));
                FontMetrics linkMetrics = blob.getFontMetrics();
                blob.drawString(Ui.elide("click to list", linkMetrics, textW),
                        textX, box.y + box.height - 7);
            }
        } finally {
            blob.dispose();
        }
        drawNodeBorder(g, node);
    }

    /**
     * Draws a full mini-board node.
     *
     * @param g world-space graphics
     * @param node node to draw
     */
    private void drawBoardNode(Graphics2D g, TreeLayout.Node node) {
        int side = node.w();
        Rectangle full = nodeBounds(node);
        Rectangle board = new Rectangle(node.x(), node.y(), side, side);
        Graphics2D nodeGraphics = (Graphics2D) g.create();
        try {
            nodeGraphics.setClip(full);
            if (side * zoom > BOARD_TILE) {
                // Zoomed in past the cached tile resolution: draw the board straight
                // into the world graphics so the squares and vector pieces stay
                // razor-sharp however far in you zoom.
                BoardStyle.drawBoardSurface(nodeGraphics, board, true);
                TensorViz.drawPositionPieces(nodeGraphics, board, node.info().fen(), true);
            } else {
                BufferedImage image = boardImage(node.info().fen());
                if (image != null) {
                    // Bilinear downscale of the hi-res tile to the node size keeps the
                    // thumbnail crisp at any board size / zoom.
                    nodeGraphics.drawImage(image, board.x, board.y, side, side, null);
                } else {
                    BoardStyle.drawBoardSurface(nodeGraphics, board, true);
                }
            }
            short move = node.info().move();
            if (move != Move.NO_MOVE) {
                Shape oldClip = nodeGraphics.getClip();
                nodeGraphics.setClip(board);
                BoardStyle.drawInsetSquareHighlight(nodeGraphics,
                        BoardStyle.fieldSquareBounds(board, Move.getFromIndex(move), true),
                        Theme.withAlpha(Theme.ACCENT, 150));
                BoardStyle.drawInsetSquareHighlight(nodeGraphics,
                        BoardStyle.fieldSquareBounds(board, Move.getToIndex(move), true),
                        Theme.withAlpha(TensorViz.POSITIVE, 170));
                nodeGraphics.setClip(oldClip);
            }
            drawCaption(nodeGraphics, node, new Rectangle(node.x(), node.y() + side, side, node.h() - side));
        } finally {
            nodeGraphics.dispose();
        }
        // Draw the selection/PV/search/target rings on the UNCLIPPED graphics so
        // they sit around the board rather than being clipped inside the squares.
        drawNodeBorder(g, node);
    }

    /**
     * Draws a compact box node for zoomed-out views.
     *
     * @param g world-space graphics
     * @param node node to draw
     */
    private void drawCompactNode(Graphics2D g, TreeLayout.Node node) {
        Rectangle box = new Rectangle(node.x(), node.y(), node.w(), node.h());
        Graphics2D compactGraphics = (Graphics2D) g.create();
        try {
            compactGraphics.setClip(box);
            compactGraphics.setColor(Theme.withAlpha(qColor(node.info().q()), 210));
            compactGraphics.fillRoundRect(box.x, box.y, box.width, box.height, 8, 8);
            compactGraphics.setFont(Theme.font(Math.max(9, node.w() / 5), Font.BOLD));
            compactGraphics.setColor(Theme.TEXT);
            String label = node.root() ? "root" : node.info().san();
            NotationPainter.draw(compactGraphics, label, box.x + 4,
                    box.y + box.height / 2 + 4, box.width - 8, Theme.TEXT);
        } finally {
            compactGraphics.dispose();
        }
        drawNodeBorder(g, node);
    }

    /**
     * Draws a clipped two-line node caption.
     *
     * @param g graphics context
     * @param node node being drawn
     * @param caption caption rectangle
     */
    private void drawCaption(Graphics2D g, TreeLayout.Node node, Rectangle caption) {
        if (caption.width <= 0 || caption.height <= 0) {
            return;
        }
        g.setColor(Theme.withAlpha(Theme.PANEL_SOLID, 235));
        g.fillRect(caption.x, caption.y, caption.width, caption.height);
        g.setColor(accentFor(node));
        g.fillRect(caption.x, caption.y, Math.min(CAPTION_ACCENT_WIDTH, caption.width), caption.height);

        Graphics2D text = (Graphics2D) g.create();
        try {
            text.setClip(caption);
            int textX = caption.x + CAPTION_PAD_X;
            int textW = Math.max(0, caption.width - CAPTION_PAD_X * 2);
            String label = node.root() ? "root" : node.info().san();
            text.setFont(Theme.font(11, Font.BOLD));
            text.setColor(Theme.TEXT);
            FontMetrics labelMetrics = text.getFontMetrics();
            int labelBaseline = caption.y + Math.max(labelMetrics.getAscent() + 2,
                    Math.min(caption.height - 8, 14));
            NotationPainter.draw(text, label, textX, labelBaseline, textW, Theme.TEXT);

            text.setFont(Theme.font(9, Font.PLAIN));
            text.setColor(Theme.MUTED);
            FontMetrics statsMetrics = text.getFontMetrics();
            int statsBaseline = caption.y + caption.height - Math.max(5, statsMetrics.getDescent() + 1);
            if (statsBaseline > labelBaseline + 3) {
                String stats = statsText(node.info(), statsMetrics, textW);
                text.drawString(stats, textX, statsBaseline);
            }
        } finally {
            text.dispose();
        }
    }

    /**
     * Draws the selection / PV / search / target rings for a node.
     *
     * <p>Each ring hugs the board from just outside, nested outward, so it never
     * cuts across the chess squares or caption text. Compact nodes use their whole
     * box. A plain node draws no frame at all: a line on the board edge reads as a
     * gray border laid over the chessboard.</p>
     *
     * @param g world-space graphics
     * @param node node
     */
    private void drawNodeBorder(Graphics2D g, TreeLayout.Node node) {
        Rectangle full = nodeBounds(node);
        boolean boardNode = node.h() > node.w();
        Rectangle highlight = boardNode ? new Rectangle(node.x(), node.y(), node.w(), node.w()) : full;
        Graphics2D ring = g;
        if (boardNode) {
            ring = (Graphics2D) g.create();
            ring.setClip(new Rectangle(highlight.x - 8, highlight.y - 8,
                    highlight.width + 16, highlight.height + 8));
        }
        try {
            if (targetPath.contains(node.key())) {
                // Teal ring marks the user's tracked target line through this node.
                drawOuterRing(ring, highlight, 5, 2.4f, Theme.withAlpha(TARGET_COLOR, 235), 5);
            }
            if (searchPath.contains(node.key())) {
                // Amber ring marks the live search path through this node.
                drawOuterRing(ring, highlight, 3, 2.2f, Theme.withAlpha(SEARCH_COLOR, 245), 4);
            }
            if (node.selected()) {
                // Emerald ring marks the clicked/selected node.
                drawOuterRing(ring, highlight, 1, 2.6f, SELECT_COLOR, 3);
            } else if (node.onPrincipalVariation()) {
                drawOuterRing(ring, highlight, 1, 1.8f, Theme.withAlpha(Theme.ACCENT, 210), 3);
            }
        } finally {
            if (ring != g) {
                ring.dispose();
            }
        }
    }

    /**
     * Returns a node's full allocated bounds.
     *
     * @param node node
     * @return bounds
     */
    private static Rectangle nodeBounds(TreeLayout.Node node) {
        return new Rectangle(node.x(), node.y(), node.w(), node.h());
    }

    /**
     * Draws a rounded ring around (outside) the node's allocated rectangle.
     *
     * @param g graphics context
     * @param bounds node bounds
     * @param gap distance outside the bounds
     * @param strokeWidth stroke width
     * @param color ring color
     * @param arc corner arc
     */
    private static void drawOuterRing(Graphics2D g, Rectangle bounds, int gap,
            float strokeWidth, Color color, int arc) {
        g.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(color);
        g.drawRoundRect(bounds.x - gap, bounds.y - gap,
                bounds.width + gap * 2 - 1, bounds.height + gap * 2 - 1, arc, arc);
    }

    /**
     * Returns a stats line that fits a caption lane.
     *
     * @param info node info
     * @param metrics active font metrics
     * @param maxWidth caption text width
     * @return fitted stats text
     */
    private static String statsText(MctsSearch.NodeInfo info, FontMetrics metrics, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        String full = String.format(Locale.ROOT, "N=%,d  Q%+.2f", info.visits(), info.q());
        if (metrics.stringWidth(full) <= maxWidth) {
            return full;
        }
        String compact = String.format(Locale.ROOT, "N %s  Q %+.2f", compactCount(info.visits()), info.q());
        return Ui.elide(compact, metrics, maxWidth);
    }

    /**
     * Formats a count for a narrow caption.
     *
     * @param value count
     * @return compact count text
     */
    private static String compactCount(long value) {
        long abs = Math.abs(value);
        if (abs >= 1_000_000_000L) {
            return compactDecimal(value / 1_000_000_000.0, "B");
        }
        if (abs >= 1_000_000L) {
            return compactDecimal(value / 1_000_000.0, "M");
        }
        if (abs >= 1_000L) {
            return compactDecimal(value / 1_000.0, "k");
        }
        return Long.toString(value);
    }

    /**
     * Formats one compact decimal and removes a redundant ".0".
     *
     * @param value scaled value
     * @param suffix count suffix
     * @return formatted text
     */
    private static String compactDecimal(double value, String suffix) {
        String text = String.format(Locale.ROOT, "%.1f", value);
        if (text.endsWith(".0")) {
            text = text.substring(0, text.length() - 2);
        }
        return text + suffix;
    }

    /**
     * Returns the accent color for a node caption strip.
     *
     * @param node node
     * @return accent color
     */
    private static Color accentFor(TreeLayout.Node node) {
        if ("draw".equals(node.info().terminalState()) || "proven".equals(node.info().terminalState())) {
            return TensorViz.POLICY;
        }
        return qColor(node.info().q());
    }

    /**
     * Maps a node value to a red/green accent.
     *
     * @param q mean value in [-1, 1]
     * @return accent color
     */
    private static Color qColor(double q) {
        return q >= 0 ? TensorViz.POSITIVE : TensorViz.NEGATIVE;
    }

    /**
     * Returns a cached high-resolution board image for a FEN. The tile is keyed
     * by FEN alone (not by on-screen size), so changing the board-size spinner or
     * zoom never thrashes the cache and each position is rendered at most once.
     *
     * @param fen position FEN
     * @return rendered board tile, or null when the FEN is missing
     */
    private BufferedImage boardImage(String fen) {
        if (fen == null || fen.isBlank()) {
            return null;
        }
        BufferedImage cached = boardCache.get(fen);
        if (cached != null) {
            return cached;
        }
        BufferedImage image = TreeBoardImages.board(fen, BOARD_TILE);
        if (image != null) {
            boardCache.put(fen, image);
        }
        return image;
    }

    /**
     * Draws faint full-width separator lines between depth levels (screen-space,
     * under the tree).
     *
     * @param g device-space graphics
     */
    private void drawLayerLines(Graphics2D g) {
        if (rowsByLayer.isEmpty()) {
            return;
        }
        g.setStroke(new BasicStroke(1f));
        g.setColor(Theme.withAlpha(Theme.MUTED, 105));
        double prevBottom = Double.NaN;
        for (Map.Entry<Integer, TreeLayout.Node[]> entry : rowsByLayer.entrySet()) {
            TreeLayout.Node[] row = entry.getValue();
            if (row.length == 0) {
                continue;
            }
            double topWorld = row[0].y();
            double lineWorld = Double.isNaN(prevBottom) ? topWorld - 16 : (prevBottom + topWorld) / 2.0;
            int y = (int) Math.round(lineWorld * zoom + panY);
            if (y >= -2 && y <= getHeight() + 2) {
                g.drawLine(0, y, getWidth(), y);
            }
            prevBottom = row[0].y() + row[0].h();
        }
    }

    /**
     * Draws a depth label chip at the left for each visible level (screen-space,
     * over the tree). Overlapping labels are skipped when zoomed far out.
     *
     * @param g device-space graphics
     */
    private void drawLayerLabels(Graphics2D g) {
        if (rowsByLayer.isEmpty()) {
            return;
        }
        g.setFont(Theme.font(10, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        int lastLabelY = -10000;
        for (Map.Entry<Integer, TreeLayout.Node[]> entry : rowsByLayer.entrySet()) {
            TreeLayout.Node[] row = entry.getValue();
            if (row.length == 0) {
                continue;
            }
            int layer = entry.getKey();
            double centerWorld = row[0].y() + row[0].h() / 2.0;
            int y = (int) Math.round(centerWorld * zoom + panY);
            if (y < -10 || y > getHeight() + 10 || y - lastLabelY < 18) {
                continue;
            }
            lastLabelY = y;
            String label = layer == 0 ? "root" : "ply " + layer;
            int baseline = y + (fm.getAscent() - fm.getDescent()) / 2;
            // Plain text (no box) with a 1px backdrop shadow so it stays legible
            // if a board happens to sit behind it.
            g.setColor(Theme.withAlpha(Theme.BG, 220));
            g.drawString(label, 9, baseline + 1);
            g.setColor(Theme.withAlpha(Theme.TEXT, 205));
            g.drawString(label, 8, baseline);
        }
    }

    /**
     * Draws the screen-space legend overlay.
     *
     * @param g device-space graphics
     */
    private void drawLegend(Graphics2D g) {
        String text = String.format("%,d positions · %,d transpositions · %.0f%%",
                model.uniquePositions(), model.transpositionEdges(), zoom * 100.0);
        g.setFont(Theme.font(10, Font.BOLD));
        int w = g.getFontMetrics().stringWidth(text) + 18;
        int x = getWidth() - w - 12;
        int y = getHeight() - 28;
        g.setColor(Theme.withAlpha(Theme.PANEL_SOLID, 230));
        g.fillRoundRect(x, y, w, 20, 10, 10);
        g.setColor(Theme.MUTED);
        g.drawString(text, x + 9, y + 14);
    }

    /**
     * Returns a tooltip for the node under the pointer.
     *
     * @param event mouse event
     * @return tooltip or null
     */
    @Override
    public String getToolTipText(MouseEvent event) {
        TreeLayout.Node node = nodeAt(event.getPoint());
        if (node == null) {
            return null;
        }
        if (node.blob()) {
            int total = 0;
            for (MctsSearch.NodeInfo m : node.members()) {
                total += m.visits();
            }
            return "<html><b>" + node.members().size() + " batched leaf positions</b>"
                    + "<br>combined visits " + String.format("%,d", total)
                    + "<br><i>click to list and navigate them</i></html>";
        }
        MctsSearch.NodeInfo info = node.info();
        String line = info.lineSan() == null || info.lineSan().isBlank() ? "root" : info.lineSan();
        return "<html><b>" + escape(line) + "</b>"
                + "<br>visits " + String.format("%,d", info.visits())
                + " · Q " + String.format("%+.3f", info.q())
                + " · prior " + String.format("%.1f%%", info.prior() * 100.0)
                + "<br>depth " + node.layer() + " · " + escape(info.terminalState())
                + "<br><i>" + escape(info.fen()) + "</i></html>";
    }

    /**
     * Escapes HTML metacharacters.
     *
     * @param text raw text
     * @return escaped text
     */
    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
