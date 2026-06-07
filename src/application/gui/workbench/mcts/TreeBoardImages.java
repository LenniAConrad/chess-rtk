package application.gui.workbench.mcts;

import application.gui.workbench.board.BoardStyle;
import application.gui.workbench.network.TensorViz;
import application.gui.workbench.ui.Theme;
import chess.core.Move;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Renders position thumbnails for the search-tree view and its SVG export.
 *
 * <p>Boards always orient white at the bottom so the tree reads consistently
 * regardless of the side to move at each node.</p>
 */
final class TreeBoardImages {

    /**
     * Prevents instantiation.
     */
    private TreeBoardImages() {
    }

    /**
     * Renders a board thumbnail for a FEN at a pixel size.
     *
     * @param fen position FEN
     * @param side board pixel size
     * @return rendered image, or null when the FEN is missing
     */
    static BufferedImage board(String fen, int side) {
        return board(fen, side, Move.NO_MOVE);
    }

    /**
     * Renders a board thumbnail for a FEN at a pixel size, including the
     * incoming move highlight as part of the board layer before pieces are
     * drawn. Keeping the highlight in the same raster as the squares avoids a
     * second transformed overlay that can drift by a device pixel at fractional
     * tree zoom/pan values.
     *
     * @param fen position FEN
     * @param side board pixel size
     * @param lastMove incoming move to highlight, or {@link Move#NO_MOVE}
     * @return rendered image, or null when the FEN is missing
     */
    static BufferedImage board(String fen, int side, short lastMove) {
        if (fen == null || fen.isBlank() || side <= 0) {
            return null;
        }
        BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Rectangle r = new Rectangle(0, 0, side, side);
            BoardStyle.drawBoardSurface(g, r, false);
            drawLastMoveHighlight(g, r, lastMove);
            TensorViz.drawPositionPieces(g, r, fen, true);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * Draws the shared board last-move fill onto the board layer.
     *
     * @param g graphics context
     * @param board board bounds
     * @param move move to highlight
     */
    static void drawLastMoveHighlight(Graphics2D g, Rectangle board, short move) {
        if (move == Move.NO_MOVE) {
            return;
        }
        BoardStyle.drawFilledSquareHighlight(g,
                BoardStyle.fieldSquareBounds(board, Move.getFromIndex(move), true),
                Theme.LAST_MOVE_EDGE);
        BoardStyle.drawFilledSquareHighlight(g,
                BoardStyle.fieldSquareBounds(board, Move.getToIndex(move), true),
                Theme.LAST_MOVE_EDGE);
    }
}
