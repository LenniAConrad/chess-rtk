package application.gui.workbench.network;

import application.gui.workbench.board.BoardStyle;
import application.gui.workbench.ui.HitRegions;
import application.gui.workbench.ui.InspectorDialog;
import application.gui.workbench.ui.Theme;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Workbench panel for the OTIS policy/WDL architecture.
 */
public final class OtisView extends NetworkView {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the OTIS view.
     */
    public OtisView() {
        super(720, 540);
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
        g.drawString("Loading OTIS snapshot...", PAD, 32);
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
        g.drawString("OTIS (Policy + WDL)", PAD, 22);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(11, Font.PLAIN));
        int channels = shapeOr("otis.trunk", 0, chess.nn.otis.Model.DEFAULT_TRUNK_CHANNELS);
        int policy = shapeOr("otis.policy.logits", 0, chess.nn.otis.Model.DEFAULT_POLICY_SIZE);
        g.drawString("simple_18 board planes -> tactical incidence sheaf -> "
                + channels + "x8x8 trunk -> " + policy + " policy / WDL heads -> "
                + chess.nn.otis.Model.formatParameterCount(chess.nn.otis.Model.DEFAULT_PARAMETER_COUNT)
                + " params", PAD, 40);
    }

    /**
     * Paints overview mode.
     *
     * @param g graphics
     * @param body body rectangle
     */
    @Override
    protected void paintAbstract(Graphics2D g, Rectangle body) {
        int gap = 12;
        if (body.width < 780) {
            int part = Math.max(150, (body.height - 2 * gap) / 3);
            paintPolicyBoard(g, new Rectangle(body.x, body.y, body.width, part));
            TensorViz.drawWdlCard(g, new Rectangle(body.x, body.y + part + gap,
                    body.width, part),
                    data("otis.value.wdl"), data("otis.value.scalar"));
            paintTopPolicy(g, new Rectangle(body.x, body.y + 2 * (part + gap),
                    body.width, Math.max(120, body.height - 2 * (part + gap))));
            return;
        }
        int leftW = Math.max(320, Math.min((int) (body.width * 0.48), body.width - 330));
        Rectangle board = new Rectangle(body.x, body.y, leftW, body.height);
        int rightX = board.x + board.width + gap;
        int rightW = body.x + body.width - rightX;
        Rectangle wdl = new Rectangle(rightX, body.y, rightW,
                Math.max(140, Math.min(190, body.height / 3)));
        Rectangle policy = new Rectangle(rightX, wdl.y + wdl.height + gap,
                rightW, body.y + body.height - (wdl.y + wdl.height + gap));
        paintPolicyBoard(g, board);
        TensorViz.drawWdlCard(g, wdl, data("otis.value.wdl"), data("otis.value.scalar"));
        paintTopPolicy(g, policy);
    }

    /**
     * Paints trace mode.
     *
     * @param g graphics
     * @param body body rectangle
     */
    @Override
    protected void paintDetailed(Graphics2D g, Rectangle body) {
        TensorViz.drawSectionHeader(g, new Rectangle(body.x, body.y, body.width, 40),
                "OTIS Trace", "typed relation energy · sheaf Laplacian · policy and WDL heads");
        int top = body.y + 52;
        int gap = 12;
        int leftW = Math.max(330, Math.min((int) (body.width * 0.54), body.width - 300));
        Rectangle bars = new Rectangle(body.x, top, leftW, Math.max(120, body.height / 3));
        Rectangle trunk = new Rectangle(body.x, bars.y + bars.height + gap, leftW,
                Math.max(160, body.y + body.height - (bars.y + bars.height + gap)));
        Rectangle board = new Rectangle(body.x + leftW + gap, top,
                body.x + body.width - (body.x + leftW + gap), Math.max(250, body.height / 2));
        Rectangle wdl = new Rectangle(board.x, board.y + board.height + gap,
                board.width, body.y + body.height - (board.y + board.height + gap));
        paintRelationSummary(g, bars);
        paintSpatialMosaic(g, trunk, "otis.trunk", "trunk channels", TensorViz.FOCUS, 16);
        paintSheafBoard(g, board);
        TensorViz.drawWdlCard(g, wdl, data("otis.value.wdl"), data("otis.value.scalar"));
    }

    /**
     * Paints raw mode.
     *
     * @param g graphics
     * @param body body rectangle
     */
    @Override
    protected void paintRaw(Graphics2D g, Rectangle body) {
        TensorViz.drawSectionHeader(g, new Rectangle(body.x, body.y, body.width, 40),
                "raw OTIS tensors", "spatial boards and dense projection heads");
        int top = body.y + 52;
        int gap = 12;
        if (body.width < 880) {
            int fifth = Math.max(122, (body.height - 52 - 4 * gap) / 5);
            paintSpatialMosaic(g, new Rectangle(body.x, top, body.width, fifth),
                    "otis.input", "simple_18 input planes", TensorViz.TRUNK, chess.nn.otis.Model.INPUT_PLANES);
            paintSpatialMosaic(g, new Rectangle(body.x, top + fifth + gap, body.width, fifth),
                    "otis.sheaf.target.pressure", "12 sheaf relation target maps",
                    TensorViz.POLICY, chess.nn.otis.Model.RELATION_COUNT);
            paintSpatialMosaic(g, new Rectangle(body.x, top + 2 * (fifth + gap), body.width, fifth),
                    "otis.trunk", "trunk channel atlas", TensorViz.FOCUS, 24);
            paintDenseMatrix(g, new Rectangle(body.x, top + 3 * (fifth + gap), body.width, fifth),
                    "otis.weights.policy_head", "policy head matrix", TensorViz.POLICY);
            paintDenseMatrix(g, new Rectangle(body.x, top + 4 * (fifth + gap), body.width,
                    Math.max(120, body.y + body.height - (top + 4 * (fifth + gap)))),
                    "otis.weights.readout_hidden", "readout hidden matrix", TensorViz.FOCUS);
            return;
        }
        int leftW = Math.max(380, Math.min((int) (body.width * 0.43), body.width - 520));
        int rightX = body.x + leftW + gap;
        int rightW = body.x + body.width - rightX;
        int availableH = body.y + body.height - top;
        int spatialH = Math.max(118, (availableH - 2 * gap) / 3);
        paintSpatialMosaic(g, new Rectangle(body.x, top, leftW, spatialH),
                "otis.input", "simple_18 input planes", TensorViz.TRUNK, chess.nn.otis.Model.INPUT_PLANES);
        paintSpatialMosaic(g, new Rectangle(body.x, top + spatialH + gap, leftW, spatialH),
                "otis.sheaf.target.pressure", "12 sheaf relation target maps",
                TensorViz.POLICY, chess.nn.otis.Model.RELATION_COUNT);
        paintSpatialMosaic(g, new Rectangle(body.x, top + 2 * (spatialH + gap), leftW,
                Math.max(120, body.y + body.height - (top + 2 * (spatialH + gap)))),
                "otis.trunk", "trunk channel atlas", TensorViz.FOCUS, 24);
        int policyH = Math.max(260, (int) (availableH * 0.62));
        paintDenseMatrix(g, new Rectangle(rightX, top, rightW, policyH),
                "otis.weights.policy_head", "policy head matrix", TensorViz.POLICY);
        paintDenseMatrix(g, new Rectangle(rightX, top + policyH + gap, rightW,
                Math.max(160, body.y + body.height - (top + policyH + gap))),
                "otis.weights.readout_hidden", "readout hidden matrix", TensorViz.FOCUS);
    }

    /**
     * Paints atlas mode.
     *
     * @param g graphics
     * @param body body rectangle
     */
    @Override
    protected void paintAtlas(Graphics2D g, Rectangle body) {
        TensorViz.drawSectionHeader(g, new Rectangle(body.x, body.y, body.width, 40),
                "OTIS weight atlas", "dense v2 tensors loaded from the .bin");
        int top = body.y + 58;
        int gap = 12;
        int availableH = body.y + body.height - top;
        if (body.width < 880) {
            String[] keys = {
                    "otis.weights.policy_head",
                    "otis.weights.readout_hidden",
                    "otis.weights.raw_proj",
                    "otis.weights.rho_src",
                    "otis.weights.wdl_head"
            };
            String[] titles = {
                    "policy head matrix",
                    "readout hidden matrix",
                    "raw input projection",
                    "source restriction maps",
                    "WDL head matrix"
            };
            Color[] colors = {
                    TensorViz.POLICY,
                    TensorViz.FOCUS,
                    TensorViz.TRUNK,
                    TensorViz.TRUNK,
                    TensorViz.VALUE
            };
            int cellH = Math.max(136, (availableH - gap * (keys.length - 1)) / keys.length);
            for (int i = 0; i < keys.length; i++) {
                Rectangle panel = new Rectangle(body.x, top + i * (cellH + gap), body.width, cellH);
                paintDenseMatrix(g, panel, keys[i], titles[i], colors[i]);
            }
            return;
        }
        int leftW = Math.max(470, Math.min((int) (body.width * 0.58), body.width - 360));
        Rectangle policy = new Rectangle(body.x, top, leftW, availableH);
        int rightX = policy.x + policy.width + gap;
        int rightW = body.x + body.width - rightX;
        int topH = Math.max(180, (availableH - 2 * gap) / 3);
        Rectangle readout = new Rectangle(rightX, top, rightW, topH);
        Rectangle rho = new Rectangle(rightX, readout.y + readout.height + gap, rightW, topH);
        int bottomY = rho.y + rho.height + gap;
        int bottomH = Math.max(132, body.y + body.height - bottomY);
        int bottomW = (rightW - gap) / 2;
        Rectangle raw = new Rectangle(rightX, bottomY, bottomW, bottomH);
        Rectangle wdl = new Rectangle(raw.x + raw.width + gap, bottomY,
                rightW - raw.width - gap, bottomH);
        paintDenseMatrix(g, policy, "otis.weights.policy_head",
                "policy head matrix", TensorViz.POLICY);
        paintDenseMatrix(g, readout, "otis.weights.readout_hidden",
                "readout hidden matrix", TensorViz.FOCUS);
        paintDenseMatrix(g, rho, "otis.weights.rho_src",
                "source restriction maps", TensorViz.TRUNK);
        paintDenseMatrix(g, raw, "otis.weights.raw_proj",
                "raw input projection", TensorViz.TRUNK);
        paintDenseMatrix(g, wdl, "otis.weights.wdl_head",
                "WDL head matrix", TensorViz.VALUE);
    }

    /**
     * Paints architecture diagram mode.
     *
     * @param g graphics
     * @param body body rectangle
     */
    @Override
    protected void paintDiagram(Graphics2D g, Rectangle body) {
        TensorViz.drawSectionHeader(g, new Rectangle(body.x, body.y, body.width, 40),
                "OTIS architecture", "current-position encoder -> sheaf Laplacian -> policy / WDL");
        String[] titles = {
                "input",
                "tactical incidence",
                "sheaf Laplacian",
                "policy head",
                "WDL head"
        };
        String[] subs = {
                "18 simple_18 planes",
                "12 typed relations",
                "2 blocks, "
                        + chess.nn.otis.Model.formatParameterCount(chess.nn.otis.Model.DEFAULT_PARAMETER_COUNT)
                        + " params",
                "1858 move logits",
                "3 WDL logits"
        };
        int boxW = Math.max(140, Math.min(190, (body.width - 90) / titles.length));
        int boxH = 84;
        int gap = Math.max(18, (body.width - boxW * titles.length) / Math.max(1, titles.length - 1));
        int totalW = boxW * titles.length + gap * (titles.length - 1);
        int x = body.x + Math.max(0, (body.width - totalW) / 2);
        int y = body.y + 96;
        Rectangle previous = null;
        for (int i = 0; i < titles.length; i++) {
            Rectangle card = new Rectangle(x + i * (boxW + gap), y, boxW, boxH);
            TensorViz.drawCard(g, card, titles[i], subs[i],
                    i == 3 ? TensorViz.POLICY : i == 4 ? TensorViz.VALUE : TensorViz.FOCUS);
            if (previous != null) {
                TensorViz.drawElbowConnection(g, previous, card,
                        i == 4 ? TensorViz.VALUE : i == 3 ? TensorViz.POLICY : TensorViz.FOCUS, true);
            }
            hitRegions.add(card, titles[i], subs[i], "OTIS");
            previous = card;
        }
    }

    /**
     * Handles mouse clicks.
     *
     * @param x x
     * @param y y
     */
    @Override
    protected void onClick(int x, int y) {
        HitRegions.Region region = hitRegions.hitTest(x, y);
        if (region == null) {
            return;
        }
        if (inspector != null) {
            inspector.inspect(region, snapshot);
        } else if (region.dataKey != null && snapshot != null) {
            InspectorDialog.shared(this).inspect(region, snapshot);
        }
    }

    /**
     * Paints the policy salience board.
     *
     * @param g graphics
     * @param r bounds
     */
    private void paintPolicyBoard(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
                "sheaf focus", "OTIS post-Laplacian square signal over the current position");
        float[] salience = data("otis.sheaf.node");
        if (salience == null) {
            salience = data("otis.square.salience");
        }
        int size = Math.max(96, Math.min(r.width - 28, r.height - 86));
        Rectangle board = new Rectangle(r.x + (r.width - size) / 2, r.y + 56, size, size);
        boolean whiteDown = TensorViz.whiteDownForSideToMove(fen);
        TensorViz.drawMiniBoard(g, board);
        TensorViz.drawPositionPieces(g, board, fen, whiteDown);
        hitRegions.addInspectable(board, "Current position",
                "OTIS sheaf signal board", "", "otis.sheaf.node", 0, 0, 8, "8x8");
        if (salience != null) {
            float scale = scaleFor("otis:sheaf-node", maxAbs(salience));
            TensorViz.drawSquareOverlay(g, board, salience, scale, whiteDown);
            int focusSquare = strongestSquare(salience);
            TensorViz.drawBoardSquareRing(g, board, focusSquare, TensorViz.FOCUS, whiteDown);
            addBoardTooltips(board, salience, "OTIS sheaf square signal", whiteDown);
        }
        TensorViz.drawBoardCoordinates(g, board, whiteDown);
    }

    /**
     * Paints the signed sheaf-Laplacian board.
     *
     * @param g graphics
     * @param r bounds
     */
    private void paintSheafBoard(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
                "sheaf Laplacian", "signed delta^T delta heat-step pressure");
        float[] laplacian = data("otis.sheaf.laplacian");
        int size = Math.max(96, Math.min(r.width - 28, r.height - 86));
        Rectangle board = new Rectangle(r.x + (r.width - size) / 2, r.y + 56, size, size);
        boolean whiteDown = TensorViz.whiteDownForSideToMove(fen);
        TensorViz.drawMiniBoard(g, board);
        TensorViz.drawPositionPieces(g, board, fen, whiteDown);
        hitRegions.addInspectable(board, "Sheaf Laplacian",
                "signed OTIS tactical sheaf Laplacian", "", "otis.sheaf.laplacian", 0, 0, 8, "8x8");
        if (laplacian != null) {
            float scale = scaleFor("otis:sheaf-laplacian", maxAbs(laplacian));
            TensorViz.drawSquareOverlay(g, board, laplacian, scale, whiteDown);
            TensorViz.drawBoardSquareRing(g, board, strongestSquare(laplacian), TensorViz.VALUE, whiteDown);
            addBoardTooltips(board, laplacian, "OTIS sheaf Laplacian", whiteDown);
        }
        TensorViz.drawBoardCoordinates(g, board, whiteDown);
    }

    /**
     * Paints legal-move policy bars.
     *
     * @param g graphics
     * @param r bounds
     */
    private void paintTopPolicy(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 40),
                "policy head", "top legal moves from compressed OTIS logits");
        float[] policy = data("otis.policy.logits");
        if (policy == null) {
            return;
        }
        java.util.List<chess.nn.lc0.bt4.PolicyEncoder.ScoredMove> top = topLegalMoves(policy, 6);
        if (top.isEmpty()) {
            paintTopPolicyIndices(g, new Rectangle(r.x + 8, r.y + 56, r.width - 16, r.height - 64), policy);
            return;
        }
        int n = top.size();
        String[] labels = new String[n];
        float[] values = new float[n];
        float scale = 0.0f;
        for (int i = 0; i < n; i++) {
            var scored = top.get(i);
            labels[i] = chess.core.Move.toString(scored.move()) + "  "
                    + String.format("%.1f%%", scored.probability() * 100);
            values[i] = scored.probability();
            scale = Math.max(scale, values[i]);
        }
        Rectangle bars = new Rectangle(r.x + 8, r.y + 56, r.width - 16, r.height - 64);
        TensorViz.drawMetricBars(g, bars, labels, values, scale);
        int rowH = bars.height / Math.max(1, n);
        for (int i = 0; i < n; i++) {
            var scored = top.get(i);
            Rectangle row = new Rectangle(bars.x, bars.y + i * rowH, bars.width, rowH);
            hitRegions.addInspectable(row,
                    "Top move " + chess.core.Move.toString(scored.move()),
                    "OTIS policy index " + scored.policyIndex()
                            + " · logit " + String.format("%+.3f", scored.logit()),
                    String.format("legal-softmax %.2f%% · rank %d", scored.probability() * 100, i + 1),
                    "otis.policy.logits", 0, 0, 0, policy.length + "");
        }
    }

    /**
     * Paints top raw policy indices when a FEN cannot be decoded.
     *
     * @param g graphics
     * @param r bounds
     * @param policy policy logits
     */
    private void paintTopPolicyIndices(Graphics2D g, Rectangle r, float[] policy) {
        int n = Math.min(6, policy.length);
        Integer[] order = new Integer[policy.length];
        for (int i = 0; i < policy.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Float.compare(policy[b], policy[a]));
        String[] labels = new String[n];
        float[] values = new float[n];
        float scale = 0.0f;
        for (int i = 0; i < n; i++) {
            labels[i] = "#" + order[i];
            values[i] = policy[order[i]];
            scale = Math.max(scale, Math.abs(values[i]));
        }
        TensorViz.drawMetricBars(g, r, labels, values, scale);
    }

    /**
     * Paints trunk-channel summary bars.
     *
     * @param g graphics
     * @param r bounds
     */
    private void paintTrunkSummary(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 34),
                "trunk summary", "mean activation by channel");
        float[] summary = data("otis.trunk.summary");
        if (summary == null) {
            return;
        }
        int n = Math.min(16, summary.length);
        String[] labels = new String[n];
        float[] values = new float[n];
        for (int i = 0; i < n; i++) {
            labels[i] = Integer.toString(i);
            values[i] = summary[i];
        }
        Rectangle bars = new Rectangle(r.x + 8, r.y + 48, r.width - 16, r.height - 58);
        TensorViz.drawMetricBars(g, bars, labels, values, maxAbs(values));
        hitRegions.addInspectable(bars, "OTIS trunk summary",
                "mean activation by trunk channel", "", "otis.trunk.summary", 0, 0, 0,
                summary.length + "");
    }

    /**
     * Paints typed sheaf relation energy.
     *
     * @param g graphics
     * @param r bounds
     */
    private void paintRelationSummary(Graphics2D g, Rectangle r) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 34),
                "sheaf relation energy", "12 typed tactical incidence channels");
        float[] energy = data("otis.sheaf.relation.energy");
        if (energy == null) {
            paintTrunkSummary(g, r);
            return;
        }
        int n = Math.min(chess.nn.otis.Model.RELATION_COUNT, energy.length);
        String[] labels = new String[n];
        float[] values = new float[n];
        for (int i = 0; i < n; i++) {
            labels[i] = relationLabel(i);
            values[i] = energy[i];
        }
        Rectangle bars = new Rectangle(r.x + 8, r.y + 48, r.width - 16, r.height - 58);
        TensorViz.drawMetricBars(g, bars, labels, values, maxAbs(values));
        hitRegions.addInspectable(bars, "OTIS sheaf relation energy",
                "mean squared coboundary residual per relation", "", "otis.sheaf.relation.energy", 0, 0, 0,
                energy.length + "");
    }

    /**
     * Paints a spatial tensor mosaic.
     *
     * @param g graphics
     * @param r bounds
     * @param key snapshot key
     * @param title title
     * @param tint heatmap tint
     * @param maxChannels maximum channels to paint
     */
    private void paintSpatialMosaic(Graphics2D g, Rectangle r, String key, String title,
            Color tint, int maxChannels) {
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 34), title, key);
        float[] values = data(key);
        int[] shape = snapshot == null ? new int[0] : snapshot.shape(key);
        if (values == null || shape.length < 3 || shape[1] != 8 || shape[2] != 8) {
            return;
        }
        int channels = Math.min(Math.min(shape[0], maxChannels), values.length / 64);
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(channels)));
        int rows = (channels + cols - 1) / cols;
        Rectangle grid = new Rectangle(r.x + 8, r.y + 48, r.width - 16, r.height - 56);
        int cellW = Math.max(18, grid.width / cols);
        int cellH = Math.max(18, grid.height / rows);
        float scale = scaleFor("otis:mosaic:" + key, maxAbs(values));
        for (int c = 0; c < channels; c++) {
            int col = c % cols;
            int row = c / cols;
            Rectangle cell = new Rectangle(grid.x + col * cellW, grid.y + row * cellH,
                    Math.max(1, cellW - 3), Math.max(1, cellH - 3));
            float[] slice = new float[64];
            System.arraycopy(values, c * 64, slice, 0, 64);
            TensorViz.drawGammaHeatmap(g, cell, slice, 8, 8, scale, tint);
            g.setColor(Theme.LINE);
            g.drawRect(cell.x, cell.y, cell.width - 1, cell.height - 1);
            hitRegions.addInspectable(cell, title + " " + c,
                    "8x8 OTIS spatial tensor", "", key, c * 64, 64, 8, "8x8");
        }
    }

    /**
     * Paints one dense model tensor as a matrix.
     *
     * @param g graphics
     * @param r bounds
     * @param key snapshot key
     * @param title title
     * @param tint accent tint
     */
    private void paintDenseMatrix(Graphics2D g, Rectangle r, String key, String title, Color tint) {
        float[] values = data(key);
        int[] shape = snapshot == null ? new int[0] : snapshot.shape(key);
        int rows = matrixRows(shape, values);
        int cols = matrixCols(shape, values);
        String subtitle = key;
        if (values != null && rows > 0 && cols > 0) {
            subtitle = shapeText(shape) + " · " + chess.nn.otis.Model.formatParameterCount(values.length)
                    + " floats";
        }
        TensorViz.drawSectionHeader(g, new Rectangle(r.x, r.y, r.width, 34), title, subtitle);
        Rectangle map = new Rectangle(r.x + 10, r.y + 48,
                Math.max(1, r.width - 20), Math.max(1, r.height - 62));
        if (values == null || rows <= 0 || cols <= 0) {
            TensorViz.drawEmpty(g, map);
            return;
        }
        TensorViz.drawSignedGammaHeatmap(g, map, values, cols, rows, maxAbs(values));
        g.setColor(Theme.LINE);
        g.drawRect(map.x, map.y, map.width - 1, map.height - 1);
        hitRegions.addInspectable(map, title,
                "dense OTIS v2 tensor", subtitle, key, 0, 0, cols, shapeText(shape));
    }

    /**
     * Returns matrix rows for a snapshot tensor.
     */
    private static int matrixRows(int[] shape, float[] values) {
        if (values == null || values.length == 0) {
            return 0;
        }
        if (shape == null || shape.length == 0) {
            return 1;
        }
        if (shape.length == 1) {
            return 1;
        }
        if (shape.length == 2) {
            return shape[0];
        }
        return shape[0] * shape[1];
    }

    /**
     * Returns matrix columns for a snapshot tensor.
     */
    private static int matrixCols(int[] shape, float[] values) {
        if (values == null || values.length == 0) {
            return 0;
        }
        if (shape == null || shape.length == 0) {
            return values.length;
        }
        if (shape.length == 1) {
            return shape[0];
        }
        return shape[shape.length - 1];
    }

    /**
     * Formats a tensor shape.
     */
    private static String shapeText(int[] shape) {
        if (shape == null || shape.length == 0) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shape.length; i++) {
            if (i > 0) {
                sb.append('x');
            }
            sb.append(shape[i]);
        }
        return sb.toString();
    }

    /**
     * Returns top legal policy moves.
     *
     * @param policy policy logits
     * @param k count
     * @return scored moves
     */
    private java.util.List<chess.nn.lc0.bt4.PolicyEncoder.ScoredMove> topLegalMoves(float[] policy, int k) {
        if (fen == null || fen.isBlank()) {
            return java.util.Collections.emptyList();
        }
        try {
            return chess.nn.lc0.bt4.PolicyEncoder.topLegalMoves(new chess.core.Position(fen), policy, 0, k);
        } catch (RuntimeException ex) {
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Adds per-square tooltips over a board.
     *
     * @param board board bounds
     * @param values values
     * @param caption caption
     * @param whiteDown orientation
     */
    private void addBoardTooltips(Rectangle board, float[] values, String caption, boolean whiteDown) {
        if (values == null || values.length < 64) {
            return;
        }
        for (int sq = 0; sq < 64; sq++) {
            Rectangle cell = BoardStyle.lerfSquareBounds(board, sq, whiteDown);
            hitRegions.add(cell, TensorViz.squareLabel(sq), caption,
                    String.format("%+.4f", values[sq]));
        }
    }

    /**
     * Returns the strongest square by absolute value.
     *
     * @param values 64 values
     * @return square or -1
     */
    private static int strongestSquare(float[] values) {
        if (values == null || values.length < 64) {
            return -1;
        }
        int best = 0;
        float score = -1.0f;
        for (int i = 0; i < 64; i++) {
            float abs = Math.abs(values[i]);
            if (abs > score) {
                score = abs;
                best = i;
            }
        }
        return best;
    }

    /**
     * Returns a compact label for one OTIS sheaf relation.
     *
     * @param index relation index
     * @return short label
     */
    private static String relationLabel(int index) {
        return switch (index) {
            case 0 -> "us->them";
            case 1 -> "them->us";
            case 2 -> "us defend";
            case 3 -> "them defend";
            case 4 -> "us king-zone";
            case 5 -> "them king-zone";
            case 6 -> "bishop rays";
            case 7 -> "rook rays";
            case 8 -> "queen rays";
            case 9 -> "knights";
            case 10 -> "pawns";
            case 11 -> "pins";
            default -> "#" + index;
        };
    }

    /**
     * Reads snapshot data.
     *
     * @param key key
     * @return data or null
     */
    private float[] data(String key) {
        return snapshot == null ? null : snapshot.data(key);
    }

    /**
     * Returns one shape dimension, or a fallback.
     *
     * @param key tensor key
     * @param index dimension index
     * @param fallback fallback value
     * @return dimension value
     */
    private int shapeOr(String key, int index, int fallback) {
        if (snapshot == null) {
            return fallback;
        }
        int[] shape = snapshot.shape(key);
        return index >= 0 && index < shape.length ? shape[index] : fallback;
    }
}
