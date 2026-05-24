package application.gui.workbench.network;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Scrollable;

import static application.gui.workbench.network.NnueAtlas.*;
import static application.gui.workbench.network.NnueDrawing.*;
import static application.gui.workbench.network.NnueFeatureDecoder.*;
import static application.gui.workbench.network.NnueTraceGeometry.*;

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

public abstract class NnueAtlasView extends NnueViewBase {
    /** Serialization identifier for Swing component compatibility. */
    private static final long serialVersionUID = 1L;

    /**
     * Cached derived atlas render data for the current snapshot/sort/palette.
     */
    private transient AtlasPaintFrame atlasPaintFrame;

    /**
     * Clears dense atlas render caches when the displayed snapshot changes.
     */
    @Override
    protected void onSnapshotChanged() {
        super.onSnapshotChanged();
        atlasPaintFrame = null;
    }

    /**
     * Paints the Wikipedia-style weight atlas as a viewport-fitted, wrapped
     * bank of hidden-neuron rows. The full pixel plane stays visible without a
     * vertical scroll canvas whenever there is enough horizontal room to wrap.
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
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(13, Font.PLAIN));
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

        AtlasPaintFrame frame = atlasPaintFrame(atlas, atlasShape, output,
                hidden, planes, squares);
        List<String> modeFlags = new ArrayList<>();
        if (atlasCompare != null) {
            modeFlags.add("DIFF");
        }
        if (atlasOverlay) {
            modeFlags.add("output overlay");
        }
        if (selectedBoardSquare >= 0) {
            modeFlags.add("focus " + TensorViz.squareLabel(selectedBoardSquare));
        }
        String composedSubtitle = frame.subtitle;
        if (!modeFlags.isEmpty()) {
            String flags = String.join(" · ", modeFlags);
            composedSubtitle = (frame.subtitle == null || frame.subtitle.isEmpty())
                    ? flags
                    : flags + "  ·  " + frame.subtitle;
        }
        paintAtlasBrowser(g, body, frame, hidden, planes, squares, composedSubtitle);
    }

    /**
     * Returns cached atlas paint data, rebuilding it only when the snapshot,
     * sort mode, focused square, comparison atlas, or NN palette changes.
     *
     * @param rawAtlas raw atlas weights
     * @param rawShape raw atlas shape
     * @param output output weights
     * @param hidden hidden slot count
     * @param planes plane count
     * @param squares squares per plane
     * @return cached paint frame
     */
    private AtlasPaintFrame atlasPaintFrame(float[] rawAtlas, int[] rawShape, float[] output,
            int hidden, int planes, int squares) {
        ActivationSnapshot compareSnapshot = atlasCompare;
        float[] compareAtlas = compareSnapshot == null ? null : compareSnapshot.data("nnue.atlas.weights");
        int[] compareShape = compareSnapshot == null ? null : compareSnapshot.shape("nnue.atlas.weights");
        int paletteKey = atlasPaletteKey();
        AtlasPaintFrame cached = atlasPaintFrame;
        if (cached != null && cached.matches(snapshot, rawAtlas, output,
                compareSnapshot, compareAtlas, atlasSort, selectedBoardSquare,
                hidden, planes, squares, paletteKey)) {
            return cached;
        }

        float[] paintingData = rawAtlas;
        String subtitle = "every tile = one (slot, piece) king-averaged weight footprint · "
                + "click a tile to zoom · hover a tile for the per-square value";
        boolean compatibleCompare = compareAtlas != null && compareShape != null
                && compareShape.length >= 3
                && compareShape[0] == hidden
                && compareShape[1] == planes
                && compareShape[2] == squares
                && compareAtlas.length >= rawAtlas.length
                && rawShape != null
                && rawShape.length >= 3;
        if (compareSnapshot != null) {
            if (compatibleCompare) {
                paintingData = new float[rawAtlas.length];
                for (int i = 0; i < rawAtlas.length; i++) {
                    paintingData[i] = rawAtlas[i] - compareAtlas[i];
                }
                subtitle = "diff mode · current minus comparison · same colormap, but values are weight deltas";
            } else {
                subtitle = "diff mode requested but comparison atlas has incompatible shape · showing absolute atlas";
            }
        }

        Integer[] order = sortNeurons(rawAtlas, output, hidden, planes, squares);
        float[] perNeuronScale = computePerNeuronScale(paintingData, hidden, planes, squares);
        float[] overlayMagnitudes = computeOverlayMagnitudes(hidden);
        java.awt.image.BufferedImage wholePlaneImage = squares == 64
                ? atlasPlaneImage(paintingData, order, hidden, planes, squares, perNeuronScale)
                : null;
        atlasPaintFrame = new AtlasPaintFrame(snapshot, rawAtlas, output, compareSnapshot,
                compareAtlas, atlasSort, selectedBoardSquare, hidden, planes, squares,
                paletteKey, paintingData, subtitle, order, perNeuronScale,
                overlayMagnitudes, wholePlaneImage);
        return atlasPaintFrame;
    }

    /**
     * Paints the default interactive atlas browser.
     *
     * @param g graphics context
     * @param body body bounds
     * @param frame cached atlas render frame
     * @param hidden hidden slot count
     * @param planes plane count
     * @param squares squares per plane
     * @param subtitle header subtitle
     */
    private void paintAtlasBrowser(Graphics2D g, Rectangle body, AtlasPaintFrame frame,
            int hidden, int planes, int squares,
            String subtitle) {
        atlasSelectedPlane = Math.max(0, Math.min(atlasSelectedPlane, Math.max(0, planes - 1)));
        Integer[] order = frame.order;
        int selectedSlot = atlasSelected >= 0 && atlasSelected < hidden
                ? atlasSelected
                : order.length == 0 ? 0 : order[0];
        float[] paintingData = frame.paintingData;
        float[] rawAtlas = frame.rawAtlas;
        float[] output = frame.output;
        float[] perNeuronScale = frame.perNeuronScale;
        int headerH = 46;
        TensorViz.drawSectionHeader(g,
                new Rectangle(body.x, body.y, body.width, headerH),
                "slot atlas browser · " + hidden + " accumulator slots × " + planes + " piece planes",
                subtitle);
        Rectangle content = new Rectangle(body.x, body.y + headerH + 10,
                body.width, Math.max(1, body.height - headerH - 10));
        AtlasBrowserLayout layout = atlasBrowserLayout(content);
        paintAtlasWholePlaneOverview(g, layout.overview, order, paintingData, output,
                hidden, planes, squares, perNeuronScale, selectedSlot, frame.wholePlaneImage);
        paintAtlasSlotDetail(g, layout.detail, paintingData, selectedSlot, planes, squares,
                perNeuronScale[selectedSlot]);
        paintAtlasSlotExplanation(g, layout.explanation, paintingData, rawAtlas, output,
                selectedSlot, planes, squares);
    }


    /**
     * Computes the no-scroll atlas browser layout. On wide panes the whole
     * pixel-plane atlas fills the left side while the selected slot board and
     * analytical explanation sit on the right.
     *
     * @param content content bounds below the atlas header
     * @return browser layout rectangles
     */
    private static AtlasBrowserLayout atlasBrowserLayout(Rectangle content) {
        int gap = 12;
        if (content.width >= 900) {
            int rightW = Math.min(460, Math.max(340, content.width / 4));
            int leftW = Math.max(1, content.width - rightW - gap);
            Rectangle overview = new Rectangle(content.x, content.y, leftW, content.height);
            int detailH = Math.min(content.height,
                    Math.max(260, Math.min(content.height * 58 / 100, rightW + 110)));
            if (content.height - detailH - gap < 150) {
                detailH = Math.max(220, content.height - gap - 150);
            }
            Rectangle detail = new Rectangle(overview.x + overview.width + gap,
                    content.y, rightW, Math.max(1, detailH));
            Rectangle explanation = new Rectangle(detail.x,
                    detail.y + detail.height + gap, rightW,
                    Math.max(1, content.y + content.height - detail.y - detail.height - gap));
            return new AtlasBrowserLayout(overview, detail, explanation);
        }

        int detailH = Math.max(230, Math.min(330, content.height / 3));
        int explanationH = Math.max(150, Math.min(230, content.height / 4));
        int overviewH = Math.max(160, content.height - detailH - explanationH - gap * 2);
        Rectangle overview = new Rectangle(content.x, content.y, content.width,
                Math.min(content.height, overviewH));
        Rectangle detail = new Rectangle(content.x, overview.y + overview.height + gap,
                content.width, Math.max(1, Math.min(detailH,
                        content.y + content.height - overview.y - overview.height - gap)));
        Rectangle explanation = new Rectangle(content.x, detail.y + detail.height + gap,
                content.width, Math.max(1, content.y + content.height - detail.y - detail.height - gap));
        return new AtlasBrowserLayout(overview, detail, explanation);
    }


    /**
     * Paints a true whole-atlas pixel-plane overview: every selected-order slot,
     * every piece plane, and every square cell are present in one dense image.
     *
     * @param g graphics context
     * @param r drawing bounds
     * @param order slot render order
     * @param atlas atlas values
     * @param output output weights
     * @param hidden hidden slot count
     * @param planes plane count
     * @param squares squares per plane
     * @param perNeuronScale scale per hidden slot
     * @param selectedSlot selected hidden slot
     * @param wholePlaneImage cached dense atlas raster
     */
    protected void paintAtlasWholePlaneOverview(Graphics2D g, Rectangle r, Integer[] order,
            float[] atlas, float[] output, int hidden, int planes, int squares,
            float[] perNeuronScale, int selectedSlot, java.awt.image.BufferedImage wholePlaneImage) {
        TensorViz.drawCard(g, r,
                "whole pixel-plane atlas",
                hidden + " slots × " + planes + " planes · white-bottom board squares",
                TensorViz.FOCUS);
        Rectangle inner = new Rectangle(r.x + 10, r.y + 38,
                Math.max(1, r.width - 20), Math.max(1, r.height - 48));
        if (squares != 64 || hidden <= 0 || planes <= 0 || inner.width <= 12 || inner.height <= 16) {
            TensorViz.drawEmpty(g, inner);
            return;
        }
        int labelH = 14;
        int columnGap = atlasWholeColumnGap();
        int rowAreaH = Math.max(1, inner.height - labelH);
        int columns = atlasWholeColumnCount(inner.width, rowAreaH, hidden, planes);
        int rowsPerColumn = atlasWholeRowsPerColumn(inner.width, rowAreaH, hidden, planes);
        int slotPitch = atlasWholeSlotPitch(hidden, rowsPerColumn, rowAreaH);
        int columnW = Math.max(1, (inner.width - columnGap * Math.max(0, columns - 1))
                / Math.max(1, columns));
        if (columnW <= 0 || rowsPerColumn <= 0) {
            return;
        }
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(9, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        java.awt.image.BufferedImage image = wholePlaneImage == null
                ? atlasPlaneImage(atlas, order, hidden, planes, squares, perNeuronScale)
                : wholePlaneImage;
        hitRegions.add(inner,
                "whole atlas overview",
                "Wrapped pixel-plane banks for every accumulator slot.",
                hidden + " slots × " + planes + " planes");
        Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int selectedRow = orderIndex(order, selectedSlot);
        for (int column = 0; column < columns; column++) {
            int startRow = column * rowsPerColumn;
            int rowCount = Math.min(rowsPerColumn, hidden - startRow);
            if (rowCount <= 0) {
                continue;
            }
            int columnX = inner.x + column * (columnW + columnGap);
            Rectangle labelRect = new Rectangle(columnX, inner.y, columnW, labelH);
            Rectangle imageRect = new Rectangle(columnX, inner.y + labelH, columnW,
                    Math.max(1, Math.min(rowAreaH, rowCount * slotPitch)));
            paintAtlasPlaneLabels(g, labelRect, planes, fm);
            java.awt.image.BufferedImage bank = image.getSubimage(0, startRow * 8,
                    image.getWidth(), rowCount * 8);
            g.drawImage(bank, imageRect.x, imageRect.y, imageRect.width, imageRect.height, null);
            g.setColor(Theme.LINE);
            g.drawRect(imageRect.x, imageRect.y, imageRect.width - 1, imageRect.height - 1);
            if (selectedRow >= startRow && selectedRow < startRow + rowCount) {
                int y = imageRect.y + (selectedRow - startRow) * slotPitch;
                g.setColor(TensorViz.FOCUS);
                g.setStroke(new BasicStroke(2.0f));
                g.drawRect(imageRect.x, y, imageRect.width - 1, Math.max(1, slotPitch) - 1);
                g.setStroke(new BasicStroke(1.0f));
            }
            if (atlasSelectedPlane >= 0 && atlasSelectedPlane < planes) {
                int x0 = (int) Math.floor(imageRect.x + atlasSelectedPlane * imageRect.width / (double) planes);
                int x1 = (int) Math.ceil(imageRect.x + (atlasSelectedPlane + 1) * imageRect.width / (double) planes);
                g.setColor(Theme.withAlpha(TensorViz.FOCUS, 150));
                g.drawRect(x0, imageRect.y, Math.max(1, x1 - x0) - 1, imageRect.height - 1);
            }
            for (int row = 0; row < rowCount && startRow + row < order.length; row++) {
                int slot = order[startRow + row];
                hitRegions.add(new Rectangle(imageRect.x, imageRect.y + row * slotPitch,
                        imageRect.width, Math.max(1, slotPitch)),
                        "Slot " + slot + " · whole atlas",
                        "Select this accumulator slot from the whole pixel-plane overview.",
                        atlasSlotDetail(output, slot, atlas, planes, squares));
            }
        }
        if (interpolation != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        }
    }

    /**
     * Paints piece-plane labels above one wrapped whole-atlas bank.
     *
     * @param g graphics context
     * @param r label bounds
     * @param planes plane count
     * @param fm font metrics
     */
    private void paintAtlasPlaneLabels(Graphics2D g, Rectangle r, int planes, FontMetrics fm) {
        if (r.width / Math.max(1, planes) >= 10) {
            for (int p = 0; p < planes; p++) {
                int x0 = (int) Math.round(r.x + p * r.width / (double) planes);
                int x1 = (int) Math.round(r.x + (p + 1) * r.width / (double) planes);
                String label = atlasPlaneLabel(p, planes);
                int tw = fm.stringWidth(label);
                g.drawString(label, x0 + Math.max(1, (x1 - x0 - tw) / 2), r.y + 10);
            }
        }
    }



    /**
     * Paints the sorted slot thumbnail gallery.
     *
     * @param g graphics context
     * @param r drawing bounds
     * @param order slot render order
     * @param atlas atlas values
     * @param output output weights
     * @param hidden hidden slot count
     * @param planes plane count
     * @param squares squares per plane
     * @param perNeuronScale scale per hidden slot
     * @param overlayMag overlay magnitudes
     * @param selectedSlot selected hidden slot
     */
    protected void paintAtlasSlotGallery(Graphics2D g, Rectangle r, Integer[] order,
            float[] atlas, float[] output, int hidden, int planes, int squares,
            float[] perNeuronScale, float[] overlayMag, int selectedSlot) {
        TensorViz.drawCard(g, r,
                "slot gallery",
                selectedBoardSquare >= 0
                        ? "ranked by " + TensorViz.squareLabel(selectedBoardSquare)
                        : "sorted by " + atlasSort,
                TensorViz.FOCUS);
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
            g.setColor(selected ? Theme.SELECTION_SOLID : Theme.ELEVATED_SOLID);
            g.fillRoundRect(card.x, card.y, card.width, card.height,
                    Theme.RADIUS, Theme.RADIUS);
            g.setColor(selected ? TensorViz.FOCUS : Theme.LINE);
            g.drawRoundRect(card.x, card.y, card.width - 1, card.height - 1,
                    Theme.RADIUS, Theme.RADIUS);
            if (atlasOverlay && overlayMag != null && slot < overlayMag.length && overlayMax > 0.0f) {
                float t = Math.min(1.0f, Math.abs(overlayMag[slot]) / overlayMax);
                g.setColor(Theme.withAlpha(
                        overlayMag[slot] >= 0.0f ? TensorViz.POSITIVE : TensorViz.NEGATIVE,
                        Math.round(160.0f * t)));
                g.fillRoundRect(card.x + 4, card.y + 4, 4, card.height - 8, 4, 4);
            }
            Rectangle thumb = new Rectangle(card.x + 8, card.y + 21,
                    Math.min(44, card.width - 16), Math.min(44, card.height - 30));
            paintAtlasCompositeTile(g, thumb, atlas, slot, planes, squares, perNeuronScale[slot]);
            if (selectedBoardSquare >= 0) {
                overlaySelectedSquare(g, thumb, selectedBoardSquare);
            }
            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(11, Font.BOLD));
            g.drawString("#" + slot, card.x + 8, card.y + 15);
            g.setColor(valueAt(output, slot) >= 0.0f ? TensorViz.POSITIVE : TensorViz.NEGATIVE);
            g.setFont(Theme.font(10, Font.BOLD));
            String value = atlasSlotBadge(output, slot, atlas, planes, squares);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(Ui.elide(value, fm, Math.max(20, card.width - thumb.width - 18)),
                    thumb.x + thumb.width + 8, card.y + 38);
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(9, Font.PLAIN));
            String label = atlasSlotArchetype(atlas, slot, planes, squares);
            fm = g.getFontMetrics();
            g.drawString(Ui.elide(label, fm, Math.max(20, card.width - thumb.width - 18)),
                    thumb.x + thumb.width + 8, card.y + 54);
            hitRegions.add(card,
                    "Slot " + slot + " · gallery",
                    "Select this accumulator slot in the atlas browser.",
                    atlasSlotDetail(output, slot, atlas, planes, squares));
        }
        if (show < order.length) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(10, Font.ITALIC));
            g.drawString("showing top " + show + " of " + hidden + " slots",
                    inner.x + 4, r.y + r.height - 8);
        }
    }

    /**
     * Paints the focused slot board and plane selector.
     *
     * @param g graphics context
     * @param r drawing bounds
     * @param atlas atlas values
     * @param slot hidden slot
     * @param planes plane count
     * @param squares squares per plane
     * @param scale heatmap scale
     */
    protected void paintAtlasSlotDetail(Graphics2D g, Rectangle r, float[] atlas,
            int slot, int planes, int squares, float scale) {
        TensorViz.drawCard(g, r,
                "slot #" + slot + " atlas",
                atlasPlaneName(atlasSelectedPlane, planes) + " weights",
                TensorViz.FOCUS);
        Rectangle inner = new Rectangle(r.x + 12, r.y + 38,
                Math.max(1, r.width - 24), Math.max(1, r.height - 50));
        int chipGap = 5;
        int chipH = 24;
        int chipW = Math.max(28, Math.min(58, (inner.width - chipGap * Math.max(0, planes - 1)) / Math.max(1, planes)));
        for (int p = 0; p < planes; p++) {
            Rectangle chip = new Rectangle(inner.x + p * (chipW + chipGap), inner.y, chipW, chipH);
            boolean selected = p == atlasSelectedPlane;
            g.setColor(selected ? Theme.SELECTION_SOLID : Theme.ELEVATED_SOLID);
            g.fillRoundRect(chip.x, chip.y, chip.width, chip.height, Theme.RADIUS, Theme.RADIUS);
            g.setColor(selected ? TensorViz.FOCUS : Theme.LINE);
            g.drawRoundRect(chip.x, chip.y, chip.width - 1, chip.height - 1,
                    Theme.RADIUS, Theme.RADIUS);
            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(11, Font.BOLD));
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
        TensorViz.drawMiniBoard(g, board);
        paintAtlasTileDense(g, board, atlas, offset, Math.max(1e-6f, scale), false);
        TensorViz.drawPositionPieces(g, board, fen);
        drawWhiteBottomLabel(g, board, inner.y + inner.height);
        if (selectedBoardSquare >= 0) {
            TensorViz.drawBoardSquareRing(g, board, selectedBoardSquare, TensorViz.FOCUS);
        }
        TensorViz.drawBoardCoordinates(g, board);
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
            TensorViz.drawInfoChip(g, zoom, "open", "all planes", TensorViz.FOCUS);
            hitRegions.add(zoom,
                    "Zoom slot " + slot,
                    "Open the full all-piece-plane view for this slot.",
                    "slot #" + slot);
            if (selectedBoardSquare >= 0) {
                g.setColor(Theme.MUTED);
                g.setFont(Theme.font(11, Font.PLAIN));
                g.drawString(TensorViz.squareLabel(selectedBoardSquare) + "  "
                        + selectedSquareValue(atlas, offset, selectedBoardSquare),
                        zoom.x + zoom.width + 12, footerY + 16);
            }
        }
    }

    /**
     * Paints the right-side explanation pane for a slot.
     *
     * @param g graphics context
     * @param r drawing bounds
     * @param paintingData atlas values to paint
     * @param rawAtlas raw atlas values
     * @param output output weights
     * @param slot hidden slot
     * @param planes plane count
     * @param squares squares per plane
     */
    protected void paintAtlasSlotExplanation(Graphics2D g, Rectangle r, float[] paintingData,
            float[] rawAtlas, float[] output, int slot, int planes, int squares) {
        TensorViz.drawCard(g, r,
                "slot explanation",
                atlasSlotArchetype(rawAtlas, slot, planes, squares),
                TensorViz.FOCUS);
        Rectangle inner = new Rectangle(r.x + 12, r.y + 42,
                Math.max(1, r.width - 24), Math.max(1, r.height - 54));
        int y = inner.y;
        float[] contribution = totalContribution();
        float contrib = valueAt(contribution, slot);
        float out = valueAt(output, slot);
        y = drawAtlasMetricLine(g, inner, y, "current contribution", String.format("%+.3f", contrib),
                contrib >= 0.0f ? TensorViz.POSITIVE : TensorViz.NEGATIVE);
        y = drawAtlasMetricLine(g, inner, y, "output weight", String.format("%+.3f", out),
                out >= 0.0f ? TensorViz.POSITIVE : TensorViz.NEGATIVE);
        int topPlane = strongestAtlasPlane(rawAtlas, slot, planes, squares);
        y = drawAtlasMetricLine(g, inner, y, "dominant plane",
                topPlane >= 0 ? atlasPlaneName(topPlane, planes) : "-", TensorViz.FOCUS);
        int offset = (slot * planes + atlasSelectedPlane) * squares;
        int posSq = strongestSquare(paintingData, offset, squares, true);
        int negSq = strongestSquare(paintingData, offset, squares, false);
        y = drawAtlasMetricLine(g, inner, y, "strongest + square",
                squareValueLabel(paintingData, offset, posSq), TensorViz.POSITIVE);
        y = drawAtlasMetricLine(g, inner, y, "strongest - square",
                squareValueLabel(paintingData, offset, negSq), TensorViz.NEGATIVE);
        y += 8;
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(11, Font.BOLD));
        g.drawString("top active features feeding this slot", inner.x, y + 12);
        y += 20;
        drawAtlasTopFeatureInputs(g, new Rectangle(inner.x, y, inner.width,
                Math.max(1, inner.y + inner.height - y)), slot);
    }


    /**
     * Draws strongest active Half-KP rows feeding one atlas slot.
     *
     * @param g graphics context
     * @param r drawing bounds
     * @param slot hidden slot
     */
    protected void drawAtlasTopFeatureInputs(Graphics2D g, Rectangle r, int slot) {
        float[] indices = snapshot.data("nnue.features.us.indices");
        float[] weights = snapshot.data("nnue.features.us.weights");
        float[] impact = snapshot.data("nnue.features.us.impact");
        int[] shape = snapshot.shape("nnue.features.us.weights");
        int stride = shape != null && shape.length >= 2 ? shape[1] : 0;
        if (indices == null || weights == null || stride <= slot) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(11, Font.PLAIN));
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
            g.setColor(i % 2 == 0 ? Theme.PANEL_SOLID : Theme.ELEVATED_SOLID);
            g.fillRect(line.x, line.y, line.width, line.height);
            int glyph = Math.min(22, line.height - 2);
            HalfKpFeature feature = decodeHalfKpFeature(featureIdx, sideToMoveWhite());
            TensorViz.drawHalfKpGlyph(g,
    new Rectangle(line.x + 2, line.y + (line.height - glyph) / 2, glyph, glyph),
                    feature.kingSquare, feature.pieceCode, feature.pieceSquare);
            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(10, Font.PLAIN));
            FontMetrics fm = g.getFontMetrics();
            String label = decodeUsHalfKP(featureIdx);
            int valueW = 86;
            g.drawString(Ui.elide(label, fm, Math.max(20, line.width - valueW - glyph - 12)),
                    line.x + glyph + 8, line.y + 16);
            g.setColor(weight >= 0.0f ? TensorViz.POSITIVE : TensorViz.NEGATIVE);
            g.setFont(Theme.font(10, Font.BOLD));
            g.drawString(String.format("%+.3f", weight), line.x + line.width - valueW, line.y + 12);
            g.setColor(featureImpact >= 0.0f ? TensorViz.POSITIVE : TensorViz.NEGATIVE);
            g.drawString(String.format("%+.1fcp", featureImpact), line.x + line.width - valueW, line.y + 24);
            hitRegions.add(line,
                    "Feature #" + featureIdx + "  " + label,
                    "Active feature input to atlas slot #" + slot,
                    String.format("slot weight %+.3f · impact %+.1f cp", weight, featureImpact));
        }
    }

    /**
     * Moves the selected slot through the whole pixel-plane atlas using the
     * current wrapped-bank geometry. Up/down move by one visual row; left/right
     * jump to the adjacent wrapped bank at the same row.
     *
     * @param dx horizontal visual-bank movement
     * @param dy vertical row movement
     * @return true when the selection changed
     */
    @Override
    protected boolean navigateAtlasSelection(int dx, int dy) {
        if (!isAtlas() || snapshot == null || atlasGrid || atlasZoomedSlot >= 0) {
            return false;
        }
        float[] atlas = snapshot.data("nnue.atlas.weights");
        int[] shape = snapshot.shape("nnue.atlas.weights");
        float[] output = snapshot.data("nnue.atlas.output");
        if (atlas == null || shape == null || shape.length < 3) {
            return false;
        }
        int hidden = shape[0];
        int planes = shape[1];
        int squares = shape[2];
        Integer[] order = sortNeurons(atlas, output, hidden, planes, squares);
        if (order.length == 0) {
            return false;
        }
        int currentSlot = atlasSelected >= 0 && atlasSelected < hidden ? atlasSelected : order[0];
        int currentIndex = orderIndex(order, currentSlot);
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        int rowsPerColumn = atlasNavigationRowsPerColumn(hidden, planes);
        int nextIndex = Math.max(0, Math.min(order.length - 1,
                currentIndex + dy + dx * rowsPerColumn));
        int nextSlot = order[nextIndex];
        if (nextSlot == atlasSelected) {
            return false;
        }
        atlasSelected = nextSlot;
        return true;
    }

    /**
     * Returns the visible rows-per-bank value used by the current whole-atlas
     * overview layout.
     *
     * @param hidden hidden slot count
     * @param planes piece-plane count
     * @return rows per wrapped bank
     */
    private int atlasNavigationRowsPerColumn(int hidden, int planes) {
        Rectangle body = new Rectangle(PAD, BODY_TOP,
                Math.max(1, getWidth() - 2 * PAD), Math.max(1, getHeight() - BODY_TOP - PAD));
        int headerH = 46;
        Rectangle content = new Rectangle(body.x, body.y + headerH + 10,
                body.width, Math.max(1, body.height - headerH - 10));
        AtlasBrowserLayout layout = atlasBrowserLayout(content);
        Rectangle inner = new Rectangle(layout.overview.x + 10, layout.overview.y + 38,
                Math.max(1, layout.overview.width - 20), Math.max(1, layout.overview.height - 48));
        int rowAreaH = Math.max(1, inner.height - 14);
        return atlasWholeRowsPerColumn(inner.width, rowAreaH, hidden, planes);
    }











    /**
     * Computes per-neuron overlay magnitude — the current activation × output
     * weight if both are available, falling back to |output| only when not.
     *
     * @param hidden hidden size
     * @return per-slot signed magnitude or null when nothing useful is known
     */
    protected float[] computeOverlayMagnitudes(int hidden) {
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
    protected Integer[] sortNeurons(float[] atlas, float[] output, int hidden, int planes, int squares) {
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
     * Paints the multi-variant grid view: one small atlas thumbnail per
     * discovered NNUE network, laid out left-to-right.
     *
     * @param g graphics
     * @param body body rectangle
     * @param hidden expected hidden size of the primary atlas
     * @param planes expected piece-plane count
     * @param squares expected per-plane square count
     */
    protected void paintAtlasGrid(Graphics2D g, Rectangle body, int hidden, int planes, int squares) {
        int headerH = 38;
        TensorViz.drawSectionHeader(g,
    new Rectangle(body.x, body.y, body.width, headerH),
                "variant grid · " + atlasGridEntries.size() + " atlases",
                "every column = one .nnue file's atlas at thumbnail size · hover a tile for value");
        int variants = atlasGridEntries.size();
        int gap = 24;
        int colW = (body.width - gap * (variants + 1)) / variants;
        int colTop = body.y + headerH + 18;
        int colH = body.height - headerH - 36;
        for (int v = 0; v < variants; v++) {
            NetworkPanel.NnueAtlasEntry entry = atlasGridEntries.get(v);
            int colX = body.x + gap + v * (colW + gap);
            // Variant label
            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(11, Font.BOLD));
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
    protected void paintSingleAtlasThumb(Graphics2D g, Rectangle r, ActivationSnapshot snap) {
        float[] atlas = snap.data("nnue.atlas.weights");
        int[] atlasShape = snap.shape("nnue.atlas.weights");
        if (atlas == null || atlasShape == null || atlasShape.length < 3) {
            g.setColor(Theme.MUTED);
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
        g.setColor(Theme.LINE);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
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
        TensorViz.drawSectionHeader(g,
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
        FontMetrics fmTitle = g.getFontMetrics(Theme.font(13, Font.BOLD));
        for (int i = 0; i < titles.length; i++) {
            int x = startX + i * (boxW + gap);
            // Box
            g.setColor(Theme.PANEL_SOLID);
            g.fillRoundRect(x, y, boxW, boxH, 4, 4);
            g.setColor(Theme.LINE);
            g.drawRoundRect(x, y, boxW, boxH, 4, 4);
            // Title
            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(13, Font.BOLD));
            int tw = fmTitle.stringWidth(titles[i]);
            g.drawString(titles[i], x + (boxW - tw) / 2, y + 28);
            // Subtitle
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(11, Font.PLAIN));
            FontMetrics fmSub = g.getFontMetrics();
            int sw = fmSub.stringWidth(subtitles[i]);
            g.drawString(subtitles[i], x + (boxW - sw) / 2, y + 56);
            // Arrow to next
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
                    "Layer · " + titles[i],
                    subtitles[i],
                    "switch into Atlas/Detailed for richer detail");
        }
        // Caption with current eval if we have one.
        float[] cp = snapshot == null ? null : snapshot.data("nnue.output.centipawns");
        if (cp != null) {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(12, Font.ITALIC));
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
    protected void paintAtlasZoom(Graphics2D g, Rectangle body, float[] atlas,
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

        TensorViz.drawSectionHeader(g,
    new Rectangle(body.x, body.y, body.width, headerH),
                "slot #" + slot + " · zoom",
                "all 11 piece planes at full resolution · click anywhere to return to the atlas");
        hitRegions.add(new Rectangle(body.x, body.y, body.width, headerH),
                "atlas:back", "Return to atlas mosaic", "click to go back");

        int gap = 14;
        Rectangle area = new Rectangle(body.x + 6, body.y + headerH + 12,
                body.width - 12, body.height - headerH - 18);

        // NnueTraceLayout: 6 cols × 2 rows of piece tiles (11 planes + 1 king-map),
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
            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(13, Font.BOLD));
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
            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(13, Font.BOLD));
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
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(11, Font.ITALIC));
        g.drawString("slot " + slot + " of " + hidden + " · click any tile or the header to return",
                area.x + 6, body.y + body.height - 6);
    }

    /**
     * Immutable layout rectangles for the atlas browser.
     */
    private static final class AtlasBrowserLayout {

        /**
         * Whole pixel-plane atlas bounds.
         */
        private final Rectangle overview;

        /**
         * Selected slot board bounds.
         */
        private final Rectangle detail;

        /**
         * Analytical explanation bounds.
         */
        private final Rectangle explanation;

        /**
         * Creates an atlas browser layout.
         *
         * @param overview whole pixel-plane atlas bounds
         * @param detail selected slot board bounds
         * @param explanation analytical explanation bounds
         */
        AtlasBrowserLayout(Rectangle overview, Rectangle detail, Rectangle explanation) {
            this.overview = overview;
            this.detail = detail;
            this.explanation = explanation;
        }
    }

    /**
     * Immutable cache record for dense atlas paint data.
     */
    private static final class AtlasPaintFrame {

        /**
         * Snapshot that produced this frame.
         */
        private final ActivationSnapshot snapshot;

        /**
         * Raw atlas data array used for sorting and explanations.
         */
        private final float[] rawAtlas;

        /**
         * Output weights paired with the atlas slots.
         */
        private final float[] output;

        /**
         * Optional comparison snapshot for diff mode.
         */
        private final ActivationSnapshot compareSnapshot;

        /**
         * Optional comparison atlas data array.
         */
        private final float[] compareAtlas;

        /**
         * Sort mode that produced {@link #order}.
         */
        private final String sortMode;

        /**
         * Focused board square used for square-rank sorting, or -1.
         */
        private final int selectedSquare;

        /**
         * Hidden accumulator-slot count.
         */
        private final int hidden;

        /**
         * Piece-plane count.
         */
        private final int planes;

        /**
         * Board-square count per plane.
         */
        private final int squares;

        /**
         * Theme palette key used for the cached raster.
         */
        private final int paletteKey;

        /**
         * Atlas data actually painted, either raw values or current-minus-compare.
         */
        private final float[] paintingData;

        /**
         * Header subtitle describing the current atlas data.
         */
        private final String subtitle;

        /**
         * Slot render order.
         */
        private final Integer[] order;

        /**
         * Per-slot heatmap scale.
         */
        private final float[] perNeuronScale;

        /**
         * Optional output-overlay magnitudes.
         */
        private final float[] overlayMagnitudes;

        /**
         * Cached whole pixel-plane atlas raster.
         */
        private final java.awt.image.BufferedImage wholePlaneImage;

        /**
         * Creates an immutable atlas paint frame.
         *
         * @param snapshot source snapshot
         * @param rawAtlas raw atlas data
         * @param output output weights
         * @param compareSnapshot comparison snapshot
         * @param compareAtlas comparison atlas data
         * @param sortMode sort mode
         * @param selectedSquare selected board square
         * @param hidden hidden slot count
         * @param planes piece-plane count
         * @param squares square count
         * @param paletteKey palette key
         * @param paintingData data to paint
         * @param subtitle header subtitle
         * @param order slot render order
         * @param perNeuronScale per-slot scale
         * @param overlayMagnitudes overlay magnitudes
         * @param wholePlaneImage whole-atlas raster
         */
        AtlasPaintFrame(ActivationSnapshot snapshot, float[] rawAtlas, float[] output,
                ActivationSnapshot compareSnapshot, float[] compareAtlas, String sortMode,
                int selectedSquare, int hidden, int planes, int squares, int paletteKey,
                float[] paintingData, String subtitle, Integer[] order,
                float[] perNeuronScale, float[] overlayMagnitudes,
                java.awt.image.BufferedImage wholePlaneImage) {
            this.snapshot = snapshot;
            this.rawAtlas = rawAtlas;
            this.output = output;
            this.compareSnapshot = compareSnapshot;
            this.compareAtlas = compareAtlas;
            this.sortMode = sortMode == null ? "" : sortMode;
            this.selectedSquare = selectedSquare;
            this.hidden = hidden;
            this.planes = planes;
            this.squares = squares;
            this.paletteKey = paletteKey;
            this.paintingData = paintingData;
            this.subtitle = subtitle;
            this.order = order;
            this.perNeuronScale = perNeuronScale;
            this.overlayMagnitudes = overlayMagnitudes;
            this.wholePlaneImage = wholePlaneImage;
        }

        /**
         * Returns whether this frame can be reused for the requested paint.
         *
         * @param requestedSnapshot source snapshot
         * @param requestedAtlas raw atlas data
         * @param requestedOutput output weights
         * @param requestedCompareSnapshot comparison snapshot
         * @param requestedCompareAtlas comparison atlas data
         * @param requestedSortMode sort mode
         * @param requestedSquare selected square
         * @param requestedHidden hidden slot count
         * @param requestedPlanes piece-plane count
         * @param requestedSquares square count
         * @param requestedPaletteKey palette key
         * @return true when reusable
         */
        boolean matches(ActivationSnapshot requestedSnapshot, float[] requestedAtlas,
                float[] requestedOutput, ActivationSnapshot requestedCompareSnapshot,
                float[] requestedCompareAtlas, String requestedSortMode,
                int requestedSquare, int requestedHidden, int requestedPlanes,
                int requestedSquares, int requestedPaletteKey) {
            String normalizedSort = requestedSortMode == null ? "" : requestedSortMode;
            return snapshot == requestedSnapshot
                    && rawAtlas == requestedAtlas
                    && output == requestedOutput
                    && compareSnapshot == requestedCompareSnapshot
                    && compareAtlas == requestedCompareAtlas
                    && sortMode.equals(normalizedSort)
                    && selectedSquare == requestedSquare
                    && hidden == requestedHidden
                    && planes == requestedPlanes
                    && squares == requestedSquares
                    && paletteKey == requestedPaletteKey;
        }
    }





}
