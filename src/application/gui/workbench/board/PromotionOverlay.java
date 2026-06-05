package application.gui.workbench.board;

import application.gui.workbench.ui.Theme;
import chess.core.Move;
import chess.core.Piece;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

/**
 * In-board promotion picker overlay.
 */
final class PromotionOverlay {
    /**
     * Inset of an option card inside its cell, in pixels.
     */
    private static final int CARD_INSET = 2;

    /**
     * Corner radius of an option card, in pixels.
     */
    private static final int CARD_RADIUS = 8;

    /**
     * Target square on the promotion rank.
     */
    private final byte target;

    /**
     * Candidate moves in canonical order.
     */
    private final short[] sorted;

    /**
     * Supplier for the current side to move.
     */
    private final BooleanSupplier whiteToMove;

    /**
     * Creates a promotion overlay.
     *
     * @param target target square on the promotion rank
     * @param candidates legal promotion candidates
     * @param whiteToMove supplier returning true when the side to move is white
     */
    PromotionOverlay(byte target, short[] candidates, BooleanSupplier whiteToMove) {
        this.target = target;
        this.sorted = sortPromotions(candidates);
        this.whiteToMove = whiteToMove;
    }

    /**
     * Returns the bounds for one option index.
     *
     * @param optionIndex promotion option index
     * @param board board drawing bounds
     * @param whiteDown true when white is rendered at the bottom
     * @return option bounds inside the board
     */
    Rectangle optionBounds(int optionIndex, Rectangle board, boolean whiteDown) {
        Rectangle squareRect = BoardGeometry.squareBounds(board, target, whiteDown);
        int cell = squareRect.width;
        int x = squareRect.x;
        int count = Math.max(1, sorted.length);
        int direction = squareRect.y < board.y + board.height / 2 ? 1 : -1;
        int firstY = squareRect.y;
        int lastY = squareRect.y + cell * direction * (count - 1);
        int minY = Math.min(firstY, lastY);
        int maxY = Math.max(firstY, lastY) + cell;
        int shift = 0;
        if (minY < board.y) {
            shift = board.y - minY;
        } else if (maxY > board.y + board.height) {
            shift = (board.y + board.height) - maxY;
        }
        int y = squareRect.y + cell * direction * optionIndex + shift;
        return new Rectangle(x, y, cell, cell);
    }

    /**
     * Resolves a click coordinate to the chosen move.
     *
     * @param clickX click x coordinate
     * @param clickY click y coordinate
     * @param board board drawing bounds
     * @param whiteDown true when white is rendered at the bottom
     * @return chosen move, or {@link Move#NO_MOVE} when the click missed the overlay
     */
    short hitTest(int clickX, int clickY, Rectangle board, boolean whiteDown) {
        for (int i = 0; i < sorted.length; i++) {
            if (optionBounds(i, board, whiteDown).contains(clickX, clickY)) {
                return sorted[i];
            }
        }
        return Move.NO_MOVE;
    }

    /**
     * Paints the promotion picker.
     *
     * @param g graphics context
     * @param board board drawing bounds
     * @param whiteDown true when white is rendered at the bottom
     * @param painter piece-painting callback
     */
    void paint(Graphics2D g, Rectangle board, boolean whiteDown, PiecePainter painter) {
        Color savedScrimColor = g.getColor();
        try {
            g.setColor(Theme.withAlpha(Theme.BG, 140));
            g.fillRect(board.x, board.y, board.width, board.height);
        } finally {
            g.setColor(savedScrimColor);
        }
        for (int i = 0; i < sorted.length; i++) {
            Rectangle bounds = optionBounds(i, board, whiteDown);
            Color savedColor = g.getColor();
            java.awt.Composite savedComposite = g.getComposite();
            try {
                drawOptionCard(g, bounds);
                short move = sorted[i];
                byte piece = pieceForPromotion(Move.getPromotion(move));
                painter.paint(g, bounds.width, bounds.x, bounds.y, piece);
            } finally {
                g.setColor(savedColor);
                g.setComposite(savedComposite);
            }
        }
    }

    /**
     * Fills and outlines one promotion-option card inside its cell bounds.
     *
     * @param g graphics context
     * @param bounds option cell bounds
     */
    private static void drawOptionCard(Graphics2D g, Rectangle bounds) {
        int x = bounds.x + CARD_INSET;
        int y = bounds.y + CARD_INSET;
        int w = bounds.width - 2 * CARD_INSET;
        int h = bounds.height - 2 * CARD_INSET;
        g.setColor(Theme.PANEL_SOLID);
        g.fillRoundRect(x, y, w, h, CARD_RADIUS, CARD_RADIUS);
        g.setColor(Theme.LINE);
        g.drawRoundRect(x, y, w, h, CARD_RADIUS, CARD_RADIUS);
    }

    /**
     * Sorts candidates as queen, rook, bishop, knight.
     *
     * @param in unsorted legal promotion candidates
     * @return sorted promotion candidates
     */
    private static short[] sortPromotions(short[] in) {
        short queen = Move.NO_MOVE;
        short rook = Move.NO_MOVE;
        short bishop = Move.NO_MOVE;
        short knight = Move.NO_MOVE;
        for (short move : in) {
            switch (Move.getPromotion(move)) {
                case 4 -> queen = move;
                case 3 -> rook = move;
                case 2 -> bishop = move;
                case 1 -> knight = move;
                default -> { /* ignored */ }
            }
        }
        short[] out = new short[4];
        int count = 0;
        if (queen != Move.NO_MOVE) {
            out[count++] = queen;
        }
        if (rook != Move.NO_MOVE) {
            out[count++] = rook;
        }
        if (bishop != Move.NO_MOVE) {
            out[count++] = bishop;
        }
        if (knight != Move.NO_MOVE) {
            out[count++] = knight;
        }
        return Arrays.copyOf(out, count);
    }

    /**
     * Maps the encoded promotion code to the actual side-to-move piece.
     *
     * @param code encoded promotion piece
     * @return signed piece code for the promoted piece
     */
    private byte pieceForPromotion(int code) {
        int sign = whiteToMove.getAsBoolean() ? 1 : -1;
        return (byte) (sign * switch (code) {
            case 4 -> Piece.WHITE_QUEEN;
            case 3 -> Piece.WHITE_ROOK;
            case 2 -> Piece.WHITE_BISHOP;
            case 1 -> Piece.WHITE_KNIGHT;
            default -> Piece.WHITE_QUEEN;
        });
    }

    /**
     * Piece painting callback used by the board panel.
     */
    @FunctionalInterface
    interface PiecePainter {
        /**
         * Paints one piece image.
         *
         * @param g graphics context
         * @param cell board cell size in pixels
         * @param x destination x coordinate
         * @param y destination y coordinate
         * @param piece signed piece code
         */
        void paint(Graphics2D g, int cell, int x, int y, byte piece);
    }
}
