package application.gui.workbench.board;

import application.gui.workbench.ui.Theme;
import chess.core.Piece;
import chess.core.Position;
import chess.images.assets.Shapes;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.Arrays;
import javax.swing.JComponent;

/**
 * Thin Lichess-style material strip shown directly above and below the board.
 *
 * <p>
 * For one side it renders the opponent pieces that side has captured (drawn in
 * the opponent's colour, the captured pieces' own colour, grouped by type in
 * ascending value) followed by a {@code +N} pawn-unit advantage when that side
 * is ahead on material. Captured counts are derived from the standard starting
 * material, so they are exact for normal games and a close approximation when a
 * game starts from a custom position or involves promotions; the {@code +N}
 * advantage is always exact because it is summed from the pieces on the board.
 * </p>
 *
 * <p>
 * Icons are aligned to the board square (via {@link BoardPanel#boardSquareBounds()})
 * so the strip sits flush with the board even though the board is letterboxed
 * beside the evaluation bar.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class MaterialStrip extends JComponent {

    private static final long serialVersionUID = 1L;

    /**
     * Piece types in the order Lichess lists captures (ascending value).
     */
    private static final byte[] ORDER = { Piece.PAWN, Piece.KNIGHT, Piece.BISHOP, Piece.ROOK, Piece.QUEEN };

    /**
     * Standard starting count for each piece code (index by piece code 1..6).
     */
    private static final int[] START_COUNT = { 0, 8, 2, 2, 2, 1, 0 };

    /**
     * Pawn-unit value for each piece code (index by piece code 1..6).
     */
    private static final int[] PAWN_VALUE = { 0, 1, 3, 3, 5, 9, 0 };

    /**
     * Strip height in pixels.
     */
    private static final int STRIP_HEIGHT = 24;

    /**
     * Rendered piece-icon size in pixels.
     */
    private static final int ICON = 17;

    /**
     * Board whose square geometry the strip aligns to.
     */
    private final transient BoardPanel board;

    /**
     * Whether this strip shows White's captures (and White's advantage).
     */
    private boolean forWhite;

    /**
     * Count of captured opponent pieces, parallel to {@link #ORDER}.
     */
    private final int[] captured = new int[ORDER.length];

    /**
     * Pawn-unit material advantage for this side, or zero when not ahead.
     */
    private int advantage;

    /**
     * Creates a material strip bound to a board.
     *
     * @param board board whose square geometry the strip follows
     */
    public MaterialStrip(BoardPanel board) {
        this.board = board;
        setOpaque(false);
        setPreferredSize(new Dimension(10, STRIP_HEIGHT));
    }

    /**
     * Recomputes the strip from a position.
     *
     * @param position current position, or null to clear
     * @param showWhite whether this strip represents the White side
     */
    public void update(Position position, boolean showWhite) {
        this.forWhite = showWhite;
        Arrays.fill(captured, 0);
        advantage = 0;
        if (position != null) {
            compute(position.getBoard());
        }
        repaint();
    }

    /**
     * Counts captured opponent pieces and the material advantage from a board.
     *
     * @param boardArray 64-square piece-code array
     */
    private void compute(byte[] boardArray) {
        int[] whiteRemaining = new int[7];
        int[] blackRemaining = new int[7];
        for (byte piece : boardArray) {
            if (piece >= Piece.PAWN && piece <= Piece.KING) {
                whiteRemaining[piece]++;
            } else if (piece <= -Piece.PAWN && piece >= -Piece.KING) {
                blackRemaining[-piece]++;
            }
        }
        int[] opponentRemaining = forWhite ? blackRemaining : whiteRemaining;
        for (int i = 0; i < ORDER.length; i++) {
            int code = ORDER[i];
            captured[i] = Math.max(0, START_COUNT[code] - opponentRemaining[code]);
        }
        int whitePoints = points(whiteRemaining);
        int blackPoints = points(blackRemaining);
        advantage = Math.max(0, forWhite ? whitePoints - blackPoints : blackPoints - whitePoints);
    }

    /**
     * Sums the pawn-unit value of remaining pieces.
     *
     * @param remaining count by piece code 1..6
     * @return pawn-unit total
     */
    private static int points(int[] remaining) {
        int total = 0;
        for (int code = Piece.PAWN; code <= Piece.QUEEN; code++) {
            total += remaining[code] * PAWN_VALUE[code];
        }
        return total;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Rectangle area = board == null ? null : board.boardSquareBounds();
        if (area == null || area.width <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double x = area.x;
        double y = (getHeight() - ICON) / 2.0;
        // Captured pieces are the opponent's colour: White's strip shows the black
        // pieces White has taken, and vice versa.
        byte opponentSign = forWhite ? (byte) -1 : (byte) 1;
        for (int i = 0; i < ORDER.length; i++) {
            byte piece = (byte) (opponentSign * ORDER[i]);
            for (int n = 0; n < captured[i]; n++) {
                Shapes.drawPiece(board.pieceSet(), piece, g2, x, y, ICON, ICON);
                x += ICON * 0.58;
            }
            if (captured[i] > 0) {
                x += ICON * 0.34;
            }
        }
        if (advantage > 0) {
            g2.setFont(Theme.font(12f, Font.BOLD));
            g2.setColor(Theme.MUTED);
            int textY = (getHeight() + g2.getFontMetrics().getAscent()) / 2 - 2;
            g2.drawString("+" + advantage, (int) Math.round(x + 4), textY);
        }
        g2.dispose();
    }
}
