package application.gui.workbench.network;

import application.gui.workbench.board.BoardStyle;
import application.gui.workbench.ui.HitRegions;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Shared board-tile painter for dense Network-tab views.
 *
 * <p>The CNN and BT4 atlas modes both render compact "board footprint" tiles.
 * Keeping the title, orientation, pieces, overlays, square rings, coordinates,
 * and hover regions in one helper prevents those small boards from drifting
 * apart as the NN visualizers evolve.</p>
 */
final class NetworkBoardSection {

    /**
     * Smallest board side that gets visible title text above the board.
     */
    private static final int LABELLED_BOARD_MIN_SIDE = 96;

    /**
     * Prevents instantiation.
     */
    private NetworkBoardSection() {
        // utility
    }

    /**
     * Paints one current-position board tile with an optional signed overlay.
     *
     * @param g graphics
     * @param hitRegions current paint-pass hit registry
     * @param board board rectangle
     * @param fen position FEN
     * @param title compact tile title
     * @param boardValues optional 64 LERF square values
     * @param scale overlay scale, or {@code <= 0} for auto
     * @param focusSquare optional highlighted LERF square
     * @param focusColor square-ring color
     * @param caption tooltip caption
     */
    static void paintOverlayBoard(Graphics2D g, HitRegions hitRegions,
            Rectangle board, String fen, String title, float[] boardValues,
            float scale, int focusSquare, Color focusColor, String caption) {
        paintOverlayBoard(g, hitRegions, board, fen, title, boardValues, scale,
                focusSquare, focusColor, caption, null, null);
    }

    /**
     * Paints one current-position board tile with optional overlay and inspector
     * metadata.
     *
     * @param g graphics
     * @param hitRegions current paint-pass hit registry
     * @param board board rectangle
     * @param fen position FEN
     * @param title compact tile title
     * @param boardValues optional 64 LERF square values
     * @param scale overlay scale, or {@code <= 0} for auto
     * @param focusSquare optional highlighted LERF square
     * @param focusColor square-ring color
     * @param caption tooltip caption
     * @param inspection optional whole-board inspector binding
     */
    static void paintOverlayBoard(Graphics2D g, HitRegions hitRegions,
            Rectangle board, String fen, String title, float[] boardValues,
            float scale, int focusSquare, Color focusColor, String caption,
            Inspection inspection) {
        paintOverlayBoard(g, hitRegions, board, fen, title, boardValues, scale,
                focusSquare, focusColor, caption, null, inspection);
    }

    /**
     * Paints one current-position board tile with optional value overlay,
     * custom board overlay, and inspector metadata.
     *
     * @param g graphics
     * @param hitRegions current paint-pass hit registry
     * @param board board rectangle
     * @param fen position FEN
     * @param title compact tile title
     * @param boardValues optional 64 LERF square values
     * @param scale overlay scale, or {@code <= 0} for auto
     * @param focusSquare optional highlighted LERF square
     * @param focusColor square-ring color
     * @param caption tooltip caption
     * @param overlay optional custom painter called before coordinates
     * @param inspection optional whole-board inspector binding
     */
    static void paintOverlayBoard(Graphics2D g, HitRegions hitRegions,
            Rectangle board, String fen, String title, float[] boardValues,
            float scale, int focusSquare, Color focusColor, String caption,
            BoardOverlay overlay, Inspection inspection) {
        paintOverlayBoard(g, hitRegions, board, fen, title, boardValues, scale,
                focusSquare, focusColor, caption, null, overlay, inspection);
    }

    /**
     * Paints one current-position board tile with optional value underlay,
     * value overlay, custom board overlay, and inspector metadata.
     *
     * @param g graphics
     * @param hitRegions current paint-pass hit registry
     * @param board board rectangle
     * @param fen position FEN
     * @param title compact tile title
     * @param boardValues optional 64 LERF square values
     * @param scale overlay scale, or {@code <= 0} for auto
     * @param focusSquare optional highlighted LERF square
     * @param focusColor square-ring color
     * @param caption tooltip caption
     * @param underlay optional custom painter called before pieces
     * @param overlay optional custom painter called before coordinates
     * @param inspection optional whole-board inspector binding
     */
    static void paintOverlayBoard(Graphics2D g, HitRegions hitRegions,
            Rectangle board, String fen, String title, float[] boardValues,
            float scale, int focusSquare, Color focusColor, String caption,
            BoardOverlay underlay, BoardOverlay overlay, Inspection inspection) {
        if (board.width <= 0 || board.height <= 0) {
            return;
        }
        boolean whiteDown = TensorViz.whiteDownForSideToMove(fen);
        String detail = focusSquare >= 0
                ? title + " - focus " + TensorViz.squareLabel(focusSquare)
                : title;
        paintTitle(g, board, detail);
        TensorViz.drawMiniBoard(g, board);
        if (underlay != null) {
            underlay.paint(g, board, whiteDown);
        }
        TensorViz.drawPositionPieces(g, board, fen, whiteDown);
        addInspection(hitRegions, board, inspection);
        if (boardValues != null && boardValues.length >= 64) {
            TensorViz.drawSquareOverlay(g, board, boardValues, scale, whiteDown);
            TensorViz.drawBoardSquareRing(g, board, focusSquare,
                    focusColor == null ? TensorViz.FOCUS : focusColor, whiteDown);
            addSquareTooltips(hitRegions, board, boardValues, caption, whiteDown);
        } else if (hitRegions != null && inspection == null) {
            hitRegions.add(board, title, caption, fen == null || fen.isBlank() ? "No FEN" : fen);
        }
        if (overlay != null) {
            overlay.paint(g, board, whiteDown);
        }
        TensorViz.drawBoardCoordinates(g, board, whiteDown);
    }

    /**
     * Adds a whole-board inspection region before per-square hover regions.
     *
     * @param hitRegions current hit registry
     * @param board board rectangle
     * @param inspection optional inspector binding
     */
    private static void addInspection(HitRegions hitRegions, Rectangle board,
            Inspection inspection) {
        if (hitRegions == null || inspection == null) {
            return;
        }
        hitRegions.addInspectable(board, inspection.title(), inspection.description(),
                inspection.value(), inspection.dataKey(), inspection.dataOffset(),
                inspection.dataLength(), inspection.dataStride(), inspection.shapeText());
    }

    /**
     * Paints a visible tile label only when the board is large enough for it.
     *
     * @param g graphics
     * @param board board rectangle
     * @param title title text
     */
    private static void paintTitle(Graphics2D g, Rectangle board, String title) {
        if (title == null || title.isBlank()
                || Math.min(board.width, board.height) < LABELLED_BOARD_MIN_SIDE) {
            return;
        }
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(10, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(Ui.elide(title, fm, board.width + 10), board.x, board.y - 4);
    }

    /**
     * Adds one hover region per board square.
     *
     * @param hitRegions current hit registry
     * @param board board rectangle
     * @param values 64 LERF square values
     * @param caption tooltip caption
     * @param whiteDown board orientation
     */
    private static void addSquareTooltips(HitRegions hitRegions, Rectangle board,
            float[] values, String caption, boolean whiteDown) {
        if (hitRegions == null || values == null || values.length < 64) {
            return;
        }
        for (int sq = 0; sq < 64; sq++) {
            Rectangle cell = BoardStyle.lerfSquareBounds(board, sq, whiteDown);
            hitRegions.add(cell, TensorViz.squareLabel(sq), caption,
                    String.format("%+.4f", values[sq]));
        }
    }

    /**
     * Optional raw-tensor binding for a board tile.
     *
     * @param title inspector title
     * @param description inspector description
     * @param value compact value text
     * @param dataKey activation snapshot key
     * @param dataOffset offset into snapshot tensor
     * @param dataLength length to inspect
     * @param dataStride row stride for matrix display
     * @param shapeText shape label
     */
    record Inspection(String title, String description, String value,
            String dataKey, int dataOffset, int dataLength, int dataStride,
            String shapeText) {
    }

    /**
     * Optional overlay hook for board-specific annotations that must be drawn
     * after pieces and before coordinates.
     */
    @FunctionalInterface
    interface BoardOverlay {

        /**
         * Paints a custom board annotation.
         *
         * @param g graphics
         * @param board board rectangle
         * @param whiteDown whether White is rendered at the bottom
         */
        void paint(Graphics2D g, Rectangle board, boolean whiteDown);
    }
}
