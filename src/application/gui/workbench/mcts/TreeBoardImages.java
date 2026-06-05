package application.gui.workbench.mcts;

import application.gui.workbench.board.BoardStyle;
import application.gui.workbench.network.TensorViz;
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
        if (fen == null || fen.isBlank() || side <= 0) {
            return null;
        }
        BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Rectangle r = new Rectangle(0, 0, side, side);
            BoardStyle.drawBoardSurface(g, r, true);
            TensorViz.drawPositionPieces(g, r, fen, true);
        } finally {
            g.dispose();
        }
        return image;
    }
}
