package chess.tag;

import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;
import chess.engine.MateProver;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Grounded THREAT tags.
 *
 * <p>A THREAT is what the SIDE TO MOVE is threatening to do on their next move.
 * Every emitted tag cites a concrete LEGAL move (present in
 * {@link Position#legalMoves()}) and a verifiable consequence:
 * mate confirmed by {@code play().isCheckmate()} or
 * {@link MateProver#proveMate(Position)}, or check confirmed by
 * {@code play().inCheck()}. No speculative threats are emitted.
 *
 * <p>Canonical tag form (mirrors the existing promotion threat field order:
 * type, side, severity, ...):
 * {@code THREAT: type=<mate|material|king_attack> side=<color>
 * severity=<immediate|soon|latent> move="<SAN>" [target="..."]}
 */
public final class Threats {

    private Threats() {
    }

    /**
     * Appends all grounded threat tags for {@code position} to {@code tags}.
     */
    public static void addThreats(List<String> tags, Position position) {
        if (tags == null || position == null) {
            return;
        }
        Set<String> emitted = new LinkedHashSet<>();

        addPromotionThreats(emitted, position);
        boolean hasMate = addMateThreats(emitted, position);
        // type=material intentionally omitted: it cannot be grounded soundly
        // without an engine search (see notes). Emitting nothing is preferred
        // over a speculative material threat.
        // A mate threat strictly dominates the weaker king_attack threat, so
        // when a (verified) mate is already announced we do not also emit
        // king_attack tags for the same position.
        if (!hasMate) {
            addKingAttackThreats(emitted, position);
        }

        for (String tag : emitted) {
            if (!tags.contains(tag)) {
                tags.add(tag);
            }
        }
    }

    // ------------------------------------------------------------------
    // PROMOTE  (preserves the existing grounded render format:
    //   THREAT: type=promote side=<color> severity=immediate square=<sq>)
    // ------------------------------------------------------------------
    private static void addPromotionThreats(Set<String> tags, Position position) {
        boolean whiteToMove = position.isWhiteToMove();
        MoveList moves = position.legalMoves();
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.get(i);
            if (!Move.isPromotion(move)) {
                continue;
            }
            byte to = Move.getToIndex(move);
            // grounded: this is a legal promotion move available right now.
            tags.add("THREAT: type=promote side=" + describeColor(whiteToMove)
                    + " severity=immediate square=" + squareName(to));
        }
    }

    // ------------------------------------------------------------------
    // MATE  (mate-in-1 via play().isCheckmate(); longer via MateProver)
    // ------------------------------------------------------------------
    /**
     * @return true if a verified mate threat was emitted
     */
    private static boolean addMateThreats(Set<String> tags, Position position) {
        boolean whiteToMove = position.isWhiteToMove();
        MoveList moves = position.legalMoves();

        // Mate in 1: find a legal move m with copy().play(m).isCheckmate().
        short mateInOne = Move.NO_MOVE;
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.get(i);
            Position after = position.copy().play(move);
            if (after != null && after.isCheckmate()) {
                mateInOne = move;
                break;
            }
        }
        if (mateInOne != Move.NO_MOVE) {
            tags.add("THREAT: type=mate side=" + describeColor(whiteToMove)
                    + " severity=immediate move=\"" + san(position, mateInOne) + "\"");
            return true; // an immediate mate dominates any longer forced mate
        }

        // Longer forced mate proven by the engine within its default bound.
        MateProver.Proof proof = MateProver.proveMate(position);
        if (proof == null) {
            return false;
        }
        short best = proof.bestMove();
        int mateMoves = proof.mateMoves();
        if (best == Move.NO_MOVE || mateMoves <= 0) {
            return false; // not grounded
        }
        // Verify the proof's best move is actually legal here before citing it.
        if (!isLegal(moves, best)) {
            return false;
        }
        String severity = (mateMoves <= 1) ? "immediate" : "soon";
        tags.add("THREAT: type=mate side=" + describeColor(whiteToMove)
                + " severity=" + severity + " move=\"" + san(position, best) + "\""
                + " target=\"mate_in_" + mateMoves + "\"");
        return true;
    }

    // ------------------------------------------------------------------
    // KING_ATTACK  (a legal checking move, not mate, into a weak shelter)
    // ------------------------------------------------------------------
    private static void addKingAttackThreats(Set<String> tags, Position position) {
        boolean whiteToMove = position.isWhiteToMove();
        boolean enemyIsWhite = !whiteToMove;
        MoveList moves = position.legalMoves();
        byte[] board = position.getBoard();

        byte enemyKing = position.kingSquare(enemyIsWhite);
        if (enemyKing < 0 || enemyKing >= 64) {
            return;
        }

        // Pre-compute the concrete shelter weakness of the enemy king,
        // independent of which checking move we pick.
        String weakness = kingShelterWeakness(position, board, enemyKing, whiteToMove);
        if (weakness == null) {
            return; // cannot ground a weak-shelter fact -> emit nothing
        }

        for (int i = 0; i < moves.size(); i++) {
            short move = moves.get(i);
            Position after = position.copy().play(move);
            if (after == null) {
                continue;
            }
            // grounded: the move gives check and is NOT mate.
            if (after.inCheck() && !after.isCheckmate()) {
                tags.add("THREAT: type=king_attack side=" + describeColor(whiteToMove)
                        + " severity=soon move=\"" + san(position, move) + "\""
                        + " target=\"" + weakness + "\"");
            }
        }
    }

    /**
     * Returns a concrete, verifiable description of the enemy king's shelter
     * weakness, or {@code null} if no weakness can be grounded.
     *
     * Grounded facts used:
     *  - open file: no pawn of EITHER color on the king's file.
     *  - half-open file: no pawn of the king's OWN color on the king's file.
     *  - exposed ring: >= 2 of the king's 8 adjacent squares are attacked by
     *    the side to move (counted via countAttackersBy{White,Black}).
     */
    private static String kingShelterWeakness(Position position, byte[] board,
                                              byte kingIndex, boolean attackerIsWhite) {
        int kingX = Field.getX(kingIndex);
        int kingY = Field.getY(kingIndex);
        byte kingPiece = board[kingIndex];
        boolean kingIsWhite = Piece.isWhite(kingPiece);

        boolean ownPawnOnFile = false;
        boolean anyPawnOnFile = false;
        for (int y = 0; y < 8; y++) {
            int idx = Field.toIndex(kingX, y);
            if (idx < 0 || idx >= 64) {
                continue;
            }
            byte piece = board[idx];
            if (Piece.isPawn(piece)) {
                anyPawnOnFile = true;
                if (Piece.isWhite(piece) == kingIsWhite) {
                    ownPawnOnFile = true;
                }
            }
        }

        // Count attacked ring squares around the king.
        int attackedRing = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = kingX + dx;
                int ny = kingY + dy;
                if (nx < 0 || nx > 7 || ny < 0 || ny > 7) {
                    continue;
                }
                byte idx = (byte) Field.toIndex(nx, ny);
                int attackers = attackerIsWhite
                        ? position.countAttackersByWhite(idx)
                        : position.countAttackersByBlack(idx);
                if (attackers > 0) {
                    attackedRing++;
                }
            }
        }

        char fileChar = fileLetter(kingIndex);
        if (!anyPawnOnFile) {
            return "open_" + fileChar + "_file";
        }
        if (attackedRing >= 2) {
            return "exposed_king_ring_" + attackedRing;
        }
        if (!ownPawnOnFile) {
            return "half_open_" + fileChar + "_file";
        }
        return null;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------
    private static boolean isLegal(MoveList moves, short move) {
        for (int i = 0; i < moves.size(); i++) {
            if (moves.get(i) == move) {
                return true;
            }
        }
        return false;
    }

    private static String san(Position position, short move) {
        String s = SAN.toAlgebraic(position, move);
        if (s == null || s.isEmpty()) {
            return Move.toString(move);
        }
        return s;
    }

    /**
     * Human-readable square name (e.g. "a8"), derived from the column/row of
     * the index. {@code Field.getFile} is NOT used here because, in this
     * engine's internal board frame, it does not return the human file letter
     * for an arbitrary index (probed: index for SAN square a8 has getX=0 but
     * getFile='h'); the file letter is derived from getX instead.
     */
    private static String squareName(byte index) {
        return "" + fileLetter(index) + (Field.getY(index) + 1);
    }

    /**
     * File letter a..h derived from Field.getX (the column).
     */
    private static char fileLetter(byte index) {
        int x = Field.getX(index);
        if (x < 0 || x > 7) {
            return '?';
        }
        return (char) ('a' + x);
    }

    private static String describeColor(boolean white) {
        return white ? "white" : "black";
    }
}
