package application.gui.workbench.board;

import application.gui.workbench.ui.Theme;
import chess.core.Piece;
import chess.core.Position;
import chess.images.assets.Shapes;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
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
 * For one side it renders the opponent pieces that side has captured — one icon
 * per piece type (in the captured pieces' own / the opponent's colour, ascending
 * by value) with an {@code xN} multiplier when two or more of a type were taken —
 * followed by a prominent {@code +N} pawn-unit advantage when that side is ahead
 * on material. Captured counts are derived from the standard starting
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
    private static final int STRIP_HEIGHT = 28;

    /**
     * Rendered piece-icon size in pixels.
     */
    private static final int ICON = 22;

    /**
     * Point size for the per-type capture count ("xN").
     */
    private static final float COUNT_FONT_SIZE = 11f;

    /**
     * Point size for the material-advantage number ("+N").
     */
    private static final float ADV_FONT_SIZE = 15f;

    /**
     * Gap in pixels between a capture icon and its "xN" multiplier.
     */
    private static final int COUNT_GAP = 2;

    /**
     * Gap in pixels between distinct captured-piece-type groups.
     */
    private static final double GROUP_GAP = ICON * 0.42;

    /**
     * Leading gap in pixels before the "+N" advantage number.
     */
    private static final double ADV_GAP = ICON * 0.5;

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

    /**
     * {@inheritDoc}
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Rectangle area = board == null ? null : board.boardSquareBounds();
        if (area == null || area.width <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double x = area.x;
        double y = (getHeight() - ICON) / 2.0;
        double rightLimit = area.x + area.width;
        // Captured icons are the opponent's colour: White's strip shows the black
        // pieces White has taken, and vice versa.
        byte opponentSign = forWhite ? (byte) -1 : (byte) 1;
        Font countFont = Theme.font(COUNT_FONT_SIZE, Font.BOLD);
        FontMetrics countMetrics = g2.getFontMetrics(countFont);
        int countBaseline = (getHeight() + countMetrics.getAscent()) / 2 - 1;
        // Reserve room for the advantage up front so the headline material lead is
        // never the first thing clamped off the right edge by the capture icons.
        Font advFont = Theme.font(ADV_FONT_SIZE, Font.BOLD);
        FontMetrics advMetrics = g2.getFontMetrics(advFont);
        String adv = advantage > 0 ? "+" + advantage : "";
        double advReserve = advantage > 0 ? ADV_GAP + advMetrics.stringWidth(adv) : 0.0;
        // One icon per captured type, with an "xN" multiplier when 2+ were taken,
        // so a rout collapses to a compact, countable group instead of a smear.
        for (int i = 0; i < ORDER.length; i++) {
            if (captured[i] <= 0) {
                continue;
            }
            if (x + ICON > rightLimit - advReserve) {
                break;
            }
            byte piece = (byte) (opponentSign * ORDER[i]);
            Shapes.drawPiece(board.pieceSet(), piece, g2, x, y, ICON, ICON);
            x += ICON;
            if (captured[i] >= 2) {
                String count = "x" + captured[i];
                g2.setFont(countFont);
                g2.setColor(Theme.MUTED);
                x += COUNT_GAP;
                g2.drawString(count, (int) Math.round(x), countBaseline);
                x += countMetrics.stringWidth(count);
            }
            x += GROUP_GAP;
        }
        if (advantage > 0) {
            // Prominent plain bold number in the brightest token, not a pill, so
            // the material lead reads at a glance without a contrast dependency.
            // Clamp to the right edge so the lead is always fully visible.
            g2.setFont(advFont);
            g2.setColor(Theme.TEXT);
            int textY = (getHeight() + advMetrics.getAscent()) / 2 - 1;
            double advX = Math.min(x + ADV_GAP, rightLimit - advMetrics.stringWidth(adv));
            g2.drawString(adv, (int) Math.round(advX), textY);
        }
        g2.dispose();
    }
}
