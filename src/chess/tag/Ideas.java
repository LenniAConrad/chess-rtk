package chess.tag;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;
import chess.eval.See;

/**
 * Grounded IDEA tags. For the side to move (or, for king_safety, the endangered
 * side) it states what that side is concretely trying to do.
 *
 * Hard rule: every emitted IDEA is justified by EITHER (a) a specific legal move
 * present in position.legalMoves(), OR (b) a concrete, re-derivable board fact.
 * No speculative ideas, no external engine. When a type cannot be grounded for
 * the given position, nothing is emitted for it.
 *
 * Canonical form (FAMILY: key=value):
 *   IDEA: side=<color> type=<type> [move=<SAN>] detail="<short grounded reason>"
 *
 * Drop-in: call addIdeas(tags, position) from the Generator tags() pipeline
 * (e.g. Ideas.addIdeas(tags, position); after addTacticalTags). It needs only
 * the Position, no Analysis.
 */
public final class Ideas {

    private static final String FAMILY = "IDEA: ";
    private static final String SIDE = "side=";
    private static final String TYPE = " type=";
    private static final String MOVE = " move=";
    private static final String DETAIL = " detail=\"";
    private static final String QUOTE = "\"";
    private static final String WHITE = "white";
    private static final String BLACK = "black";

    /**
     * Minimum signed-rank advance (relative to a side's own king rank, toward the
     * enemy) for a piece to count as strictly in the opponent's half. Ranks are
     * 0..7 via Field.getY (0 == rank 1, 7 == rank 8), so the two central ranks are
     * 3 and 4 and an advance of >= 4 means past the middle.
     */
    private static final int SPACE_ADVANCE_THRESHOLD = 4;

    private Ideas() {
        // utility
    }

    /**
     * Appends all grounded IDEA tags for {@code position} to {@code tags}.
     */
    public static void addIdeas(List<String> tags, Position position) {
        if (tags == null || position == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        addWinMaterial(tags, position, seen);
        addPromote(tags, position, seen);
        addKingSafety(tags, position, seen);
        addSpace(tags, position, seen);
    }

    /**
     * type=win_material: the highest-SEE LEGAL capture by the side to move with
     * See.see > 0 (strict). Grounding is the exact idiom Generator.candidateNote
     * uses for "wins material": the move is in legalMoves(), isCapture(move), and
     * See.see(position, move) > 0. Emits one tag (the best such capture).
     */
    private static void addWinMaterial(List<String> tags, Position position, Set<String> seen) {
        MoveList moves = position.legalMoves();
        short best = Move.NO_MOVE;
        int bestSee = 0;
        for (int i = 0; i < moves.size(); i++) {
            short m = moves.get(i);
            if (!position.isCapture(m)) {
                continue;
            }
            int see = See.see(position, m);
            if (see > 0 && see > bestSee) {
                bestSee = see;
                best = m;
            }
        }
        if (best != Move.NO_MOVE) {
            emit(tags, seen, sideToMove(position), "win_material", best, position, "wins material");
        }
    }

    /**
     * type=promote: any LEGAL promotion move (Move.isPromotion, orientation-free),
     * OR a LEGAL advance of a passed pawn (Position.isPassedPawn on the move's
     * from square). One tag per distinct legal move.
     */
    private static void addPromote(List<String> tags, Position position, Set<String> seen) {
        MoveList moves = position.legalMoves();
        boolean white = position.isWhiteToMove();
        byte[] board = position.getBoard();
        for (int i = 0; i < moves.size(); i++) {
            short m = moves.get(i);
            if (Move.isPromotion(m)) {
                emit(tags, seen, sideToMove(position), "promote", m, position, "promote pawn");
                continue;
            }
            byte from = Move.getFromIndex(m);
            if (from == Field.NO_SQUARE) {
                continue;
            }
            byte piece = board[from];
            if (piece == Piece.EMPTY || !Piece.isPawn(piece) || Piece.isWhite(piece) != white) {
                continue;
            }
        }
    }

    /**
     * type=king_safety: side = endangered side. Conservative grounding requiring
     * BOTH (1) no friendly pawn on the king's file (half-open/open file at the
     * king), AND (2) >= 1 enemy attacker on a king-ring square (king + on-board
     * neighbours), counted via countAttackersBy{White,Black}. No move attached.
     * detail names the concrete open file. One tag per side; both sides can be
     * endangered at once (e.g. both kings stuck on the same open file).
     */
    private static void addKingSafety(List<String> tags, Position position, Set<String> seen) {
        kingSafetyFor(tags, position, seen, true);
        kingSafetyFor(tags, position, seen, false);
    }

    private static void kingSafetyFor(List<String> tags, Position position, Set<String> seen, boolean kingWhite) {
        byte king = position.kingSquare(kingWhite);
        if (king == Field.NO_SQUARE) {
            return;
        }
        int kf = Field.getX(king);
        byte[] board = position.getBoard();
        for (int sq = 0; sq < board.length; sq++) {
            byte p = board[sq];
            if (p != Piece.EMPTY && Piece.isPawn(p) && Piece.isWhite(p) == kingWhite
                    && Field.getX((byte) sq) == kf) {
                return; // friendly pawn shields the king's file
            }
        }
        if (ringAttackers(position, king, kingWhite) < 1) {
            return;
        }
        String side = kingWhite ? WHITE : BLACK;
        String detail = "king on open " + ((char) ('a' + Field.getX(king))) + "-file under attack";
        if (seen.add("king_safety|" + side)) {
            tags.add(FAMILY + SIDE + side + TYPE + "king_safety" + DETAIL + detail + QUOTE);
        }
    }

    /**
     * Counts enemy attackers across the king-ring (the king square and each
     * on-board neighbour), summing per-square enemy attacker counts. A concrete,
     * deterministically re-derivable number.
     */
    private static int ringAttackers(Position position, byte king, boolean kingWhite) {
        int kx = Field.getX(king);
        int ky = Field.getY(king);
        int total = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = kx + dx;
                int ny = ky + dy;
                if (!Field.isOnBoard(nx, ny)) {
                    continue;
                }
                byte sq = (byte) Field.toIndex(nx, ny);
                total += kingWhite ? position.countAttackersByBlack(sq) : position.countAttackersByWhite(sq);
            }
        }
        return total;
    }

    /**
     * type=space: the side to move has strictly more non-king/non-pawn pieces deep
     * in the opponent's half than the opponent has in this side's half. The half
     * boundary is derived from the two kings' ranks (no absolute rank constant),
     * so it is orientation-robust. Emitted only when strictly positive for the
     * side to move.
     */
    private static void addSpace(List<String> tags, Position position, Set<String> seen) {
        boolean white = position.isWhiteToMove();
        int mine = piecesInEnemyHalf(position, white);
        int theirs = piecesInEnemyHalf(position, !white);
        if (mine <= theirs) {
            return;
        }
        String side = white ? WHITE : BLACK;
        String detail = "space advantage (" + mine + " vs " + theirs + " pieces past the middle)";
        if (seen.add("space|" + side)) {
            tags.add(FAMILY + SIDE + side + TYPE + "space" + DETAIL + detail + QUOTE);
        }
    }

    private static int piecesInEnemyHalf(Position position, boolean colorWhite) {
        byte[] board = position.getBoard();
        byte ownKing = position.kingSquare(colorWhite);
        byte enemyKing = position.kingSquare(!colorWhite);
        if (ownKing == Field.NO_SQUARE || enemyKing == Field.NO_SQUARE) {
            return 0;
        }
        int ownRank = Field.getY(ownKing);
        int dir = Integer.signum(Field.getY(enemyKing) - ownRank);
        if (dir == 0) {
            return 0;
        }
        int count = 0;
        for (int sq = 0; sq < board.length; sq++) {
            byte p = board[sq];
            if (p == Piece.EMPTY || Piece.isKing(p) || Piece.isPawn(p) || Piece.isWhite(p) != colorWhite) {
                continue;
            }
            if ((Field.getY((byte) sq) - ownRank) * dir >= SPACE_ADVANCE_THRESHOLD) {
                count++;
            }
        }
        return count;
    }

    /**
     * Emits a move-bearing IDEA tag, de-duplicated by (type,move). The move is
     * rendered in SAN via SAN.toAlgebraic, falling back to UCI if SAN throws (the
     * same defensive idiom Generator.addCandidate uses).
     */
    private static void emit(List<String> tags, Set<String> seen, String side, String type, short move,
            Position position, String detail) {
        if (!seen.add(type + "|" + Integer.toHexString(move & 0xFFFF))) {
            return;
        }
        String san;
        try {
            san = SAN.toAlgebraic(position, move);
        } catch (RuntimeException ex) {
            san = Move.toString(move);
        }
        tags.add(FAMILY + SIDE + side + TYPE + type + MOVE + san + DETAIL + detail + QUOTE);
    }

    private static String sideToMove(Position position) {
        return position.isWhiteToMove() ? WHITE : BLACK;
    }
}
