package chess.tag;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.List;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;
import chess.tag.core.Text;

/**
 * Emits actual-checkmate attributes and conservative mate-pattern tags.
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
final class Checkmate {

    /**
     * Prevents instantiation.
     */
    private Checkmate() {
        // utility
    }

    /**
     * Adds checkmate tags for terminal mating positions.
     *
     * @param context shared tagging context
     * @param out mutable tag accumulator
     */
    static void addTags(Context context, List<String> out) {
        Position position = context.position;
        if (!position.inCheck() || !context.legalMoves().isEmpty()) {
            return;
        }
        boolean defenderWhite = position.isWhiteToMove();
        String defender = sideName(defenderWhite);
        String winner = sideName(!defenderWhite);
        byte[] board = context.board();
        List<Byte> checkers = checkerSquares(position, defenderWhite);

        Emitter.tag(CHECKMATE).field(WINNER, winner).emit(out);
        Emitter.tag(CHECKMATE).field(DEFENDER, defender).emit(out);
        Emitter.tag(CHECKMATE).field(DELIVERY, delivery(board, checkers)).emit(out);
        addPatternTags(out, board, position, defenderWhite, checkers);
    }

    /**
     * Adds conservative mate-pattern tags.
     *
     * @param out mutable tag accumulator
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     */
    private static void addPatternTags(List<String> out, byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() >= 2) {
            addPattern(out, DOUBLE_CHECK);
        }
        if (isBackRankMate(board, position, defenderWhite, checkers)) {
            addPattern(out, BACK_RANK_MATE);
        }
        if (isSmotheredMate(board, position, defenderWhite, checkers)) {
            addPattern(out, SMOTHERED_MATE);
        }
        if (isCornerMate(position, defenderWhite)) {
            addPattern(out, CORNER_MATE);
        }
        if (isSupportMate(board, position, defenderWhite, checkers)) {
            addPattern(out, SUPPORT_MATE);
        }
        if (isArabianMate(board, position, defenderWhite, checkers)) {
            addPattern(out, ARABIAN_MATE);
        }
        if (isEpauletteMate(board, position, defenderWhite, checkers)) {
            addPattern(out, EPAULETTE_MATE);
        }
        if (isAnastasiaMate(board, position, defenderWhite, checkers)) {
            addPattern(out, ANASTASIA_MATE);
        }
        if (isDavidAndGoliathMate(board, checkers)) {
            addPattern(out, DAVID_AND_GOLIATH_MATE);
        }
        if (isDamianoMate(board, position, defenderWhite, checkers)) {
            addPattern(out, DAMIANO_MATE);
        }
        if (isScholarsMate(board, position, defenderWhite, checkers)) {
            addPattern(out, SCHOLARS_MATE);
        }
        if (isSwallowsTailMate(board, position, defenderWhite, checkers)) {
            addPattern(out, SWALLOWS_TAIL_MATE);
        }
        if (isDovetailMate(board, position, defenderWhite, checkers)) {
            addPattern(out, DOVETAIL_MATE);
        }
        if (isHookMate(board, position, defenderWhite, checkers)) {
            addPattern(out, HOOK_MATE);
        }
        if (isOperaMate(board, position, defenderWhite, checkers)) {
            addPattern(out, OPERA_MATE);
        }
        if (isLawnmowerMate(board, position, defenderWhite, checkers)) {
            addPattern(out, LAWNMOWER_MATE);
        }
        if (isBlackburneMate(board, position, defenderWhite, checkers)) {
            addPattern(out, BLACKBURNE_MATE);
        }
        if (isGrecoMate(board, position, defenderWhite, checkers)) {
            addPattern(out, GRECO_MATE);
        }
        if (isKillBoxMate(board, position, defenderWhite, checkers)) {
            addPattern(out, KILL_BOX_MATE);
        }
        if (isRetiMate(board, position, defenderWhite, checkers)) {
            addPattern(out, RETI_MATE);
        }
        if (isAnderssenMate(board, position, defenderWhite, checkers)) {
            addPattern(out, ANDERSSEN_MATE);
        }
        if (isMayetMate(board, position, defenderWhite, checkers)) {
            addPattern(out, MAYET_MATE);
        }
    }

    /**
     * Adds one checkmate pattern tag.
     *
     * @param out mutable tag accumulator
     * @param pattern pattern label
     */
    private static void addPattern(List<String> out, String pattern) {
        Emitter.tag(CHECKMATE).field(PATTERN, pattern).emit(out);
    }

    /**
     * Returns the checker squares attacking the mated side's king.
     *
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @return checker squares
     */
    private static List<Byte> checkerSquares(Position position, boolean defenderWhite) {
        byte king = position.kingSquare(defenderWhite);
        if (king == Field.NO_SQUARE) {
            return List.of();
        }
        byte[] attackers = defenderWhite ? position.getAttackersByBlack(king) : position.getAttackersByWhite(king);
        List<Byte> checkers = new ArrayList<>(attackers.length);
        for (byte attacker : attackers) {
            checkers.add(attacker);
        }
        return checkers;
    }

    /**
     * Returns the delivery-piece label for a mate.
     *
     * @param board board snapshot
     * @param checkers checker squares
     * @return piece label or {@code multiple}
     */
    private static String delivery(byte[] board, List<Byte> checkers) {
        if (checkers.size() != 1) {
            return MULTIPLE;
        }
        byte piece = board[checkers.get(0)];
        return piece == Piece.EMPTY ? MULTIPLE : Text.pieceNameLower(piece);
    }

    /**
     * Conservatively detects a classic back-rank mate geometry.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when a rook or queen mates a boxed king on its home rank
     */
    private static boolean isBackRankMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1) {
            return false;
        }
        byte king = position.kingSquare(defenderWhite);
        if (king == Field.NO_SQUARE || Field.getY(king) != (defenderWhite ? 0 : 7)) {
            return false;
        }
        byte checker = checkers.get(0);
        byte checkerPiece = board[checker];
        if (!Piece.isRook(checkerPiece) && !Piece.isQueen(checkerPiece)) {
            return false;
        }
        if (Field.getX(checker) != Field.getX(king) && Field.getY(checker) != Field.getY(king)) {
            return false;
        }
        return friendlyEscapeRankBlockers(board, defenderWhite, Field.getX(king)) >= 2;
    }

    /**
     * Conservatively detects smothered mate.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when a knight mates a king boxed by its own pieces
     */
    private static boolean isSmotheredMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1 || !Piece.isKnight(board[checkers.get(0)])) {
            return false;
        }
        return adjacentFriendlyBlockers(board, position.kingSquare(defenderWhite), defenderWhite) >= 3;
    }

    /**
     * Detects corner mate from the mated king square.
     *
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @return true when the mated king is on a corner square
     */
    private static boolean isCornerMate(Position position, boolean defenderWhite) {
        byte king = position.kingSquare(defenderWhite);
        return king == Field.A1 || king == Field.H1 || king == Field.A8 || king == Field.H8;
    }

    /**
     * Detects whether the checking piece is protected by another attacking piece.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when a single checker is protected by its side
     */
    private static boolean isSupportMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1) {
            return false;
        }
        byte checker = checkers.get(0);
        if (board[checker] == Piece.EMPTY) {
            return false;
        }
        byte[] defenders = defenderWhite
                ? position.getAttackersByBlack(checker)
                : position.getAttackersByWhite(checker);
        for (byte defender : defenders) {
            if (defender != checker && board[defender] != Piece.EMPTY) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects the Arabian mate: a rook delivers mate to a cornered king while a
     * friendly knight defends the rook (covering the escape squares the king
     * would otherwise use). This is a named refinement of the corner/support
     * geometry and is emitted in addition to those patterns.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when a knight-defended rook mates a king in the corner
     */
    private static boolean isArabianMate(byte[] board, Position position, boolean defenderWhite, List<Byte> checkers) {
        if (checkers.size() != 1 || !isCornerMate(position, defenderWhite)) {
            return false;
        }
        byte checker = checkers.get(0);
        if (!Piece.isRook(board[checker])) {
            return false;
        }
        byte[] defenders = defenderWhite
                ? position.getAttackersByBlack(checker)
                : position.getAttackersByWhite(checker);
        for (byte defender : defenders) {
            if (defender != checker && Piece.isKnight(board[defender])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects the epaulette mate: the mated king stands on a board edge with its
     * own rooks occupying both horizontally adjacent squares (the "epaulettes"),
     * so a single frontal queen or rook check cannot be escaped sideways.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when both same-rank neighbors are friendly rooks blocking escape
     */
    private static boolean isEpauletteMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1) {
            return false;
        }
        byte checkerPiece = board[checkers.get(0)];
        if (!Piece.isRook(checkerPiece) && !Piece.isQueen(checkerPiece)) {
            return false;
        }
        byte king = position.kingSquare(defenderWhite);
        if (king == Field.NO_SQUARE) {
            return false;
        }
        int kingFile = Field.getX(king);
        int kingRank = Field.getY(king);
        return isFriendlyRook(board, kingFile - 1, kingRank, defenderWhite)
                && isFriendlyRook(board, kingFile + 1, kingRank, defenderWhite);
    }

    /**
     * Returns whether the given square holds a rook belonging to the mated side.
     *
     * @param board board snapshot
     * @param file target file
     * @param rank target rank
     * @param defenderWhite whether the mated side is White
     * @return true when an on-board square holds a friendly rook
     */
    private static boolean isFriendlyRook(byte[] board, int file, int rank, boolean defenderWhite) {
        if (!Field.isOnBoard(file, rank)) {
            return false;
        }
        byte piece = board[Field.toIndex(file, rank)];
        return Piece.isRook(piece) && Piece.isWhite(piece) == defenderWhite;
    }

    /**
     * Detects the Anastasia mate: a single rook checks the king along an edge
     * file (a/h) and a winning-side knight covers both inner diagonal flight
     * squares, while the inner same-rank flight is denied. Emitted alongside the
     * support-mate geometry it refines.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when an edge-file rook mate is covered by a knight
     */
    private static boolean isAnastasiaMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1 || !Piece.isRook(board[checkers.get(0)])) {
            return false;
        }
        byte king = position.kingSquare(defenderWhite);
        if (king == Field.NO_SQUARE) {
            return false;
        }
        int kingFile = Field.getX(king);
        int kingRank = Field.getY(king);
        if (kingFile != 0 && kingFile != 7) {
            return false;
        }
        if (king == Field.A1 || king == Field.H1 || king == Field.A8 || king == Field.H8) {
            return false;
        }
        byte checker = checkers.get(0);
        if (Field.getX(checker) != kingFile || Field.getY(checker) == kingRank) {
            return false;
        }
        boolean winnerWhite = !defenderWhite;
        int innerFile = kingFile == 0 ? 1 : 6;
        boolean coversUp = Field.isOnBoard(innerFile, kingRank + 1)
                && knightOfSideAttacks(board, innerFile, kingRank + 1, winnerWhite);
        boolean coversDown = Field.isOnBoard(innerFile, kingRank - 1)
                && knightOfSideAttacks(board, innerFile, kingRank - 1, winnerWhite);
        if (!coversUp || !coversDown) {
            return false;
        }
        if (Field.isOnBoard(innerFile, kingRank)) {
            byte sq = (byte) Field.toIndex(innerFile, kingRank);
            boolean occupied = board[sq] != Piece.EMPTY;
            byte[] att = winnerWhite ? position.getAttackersByWhite(sq) : position.getAttackersByBlack(sq);
            if (!occupied && att.length == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether a knight of the given side attacks the target square.
     *
     * @param board board snapshot
     * @param file target file
     * @param rank target rank
     * @param white whether the knight must be White
     * @return true when an on-board knight of that side attacks the target
     */
    private static boolean knightOfSideAttacks(byte[] board, int file, int rank, boolean white) {
        int[][] hops = { { 1, 2 }, { 2, 1 }, { -1, 2 }, { -2, 1 }, { 1, -2 }, { 2, -1 }, { -1, -2 }, { -2, -1 } };
        for (int[] h : hops) {
            int f = file + h[0];
            int r = rank + h[1];
            if (!Field.isOnBoard(f, r)) {
                continue;
            }
            byte piece = board[Field.toIndex(f, r)];
            if (Piece.isKnight(piece) && Piece.isWhite(piece) == white) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects the David-and-Goliath mate: the single checking piece is a pawn.
     *
     * @param board board snapshot
     * @param checkers checker squares
     * @return true when a lone pawn delivers the mate
     */
    private static boolean isDavidAndGoliathMate(byte[] board, List<Byte> checkers) {
        return checkers.size() == 1 && Piece.isPawn(board[checkers.get(0)]);
    }

    /**
     * Detects the Damiano mate: a queen mates adjacent to the king while being
     * supported by a friendly pawn.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when a pawn-supported queen mates beside the king
     */
    private static boolean isDamianoMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1 || !Piece.isQueen(board[checkers.get(0)])) {
            return false;
        }
        byte checker = checkers.get(0);
        byte king = position.kingSquare(defenderWhite);
        if (king == Field.NO_SQUARE) {
            return false;
        }
        int chebyshev = Math.max(Math.abs(Field.getX(checker) - Field.getX(king)),
                Math.abs(Field.getY(checker) - Field.getY(king)));
        if (chebyshev != 1) {
            return false;
        }
        boolean winnerWhite = !defenderWhite;
        byte[] sup = winnerWhite ? position.getAttackersByWhite(checker) : position.getAttackersByBlack(checker);
        for (byte s : sup) {
            if (Piece.isPawn(board[s])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects Scholar's mate: a lone queen mates from f7 (mating Black) or f2
     * (mating White) while the king is still on its original e-file back-rank
     * square, with the queen defended by a friendly bishop (not the king). The
     * bishop support and king-on-e square distinguish it from the king-supported
     * corner/support mate it co-exists with.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when a bishop-supported queen mates the e-file king on f7/f2
     */
    private static boolean isScholarsMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1) {
            return false;
        }
        byte queenSq = checkers.get(0);
        byte queenPiece = board[queenSq];
        if (!Piece.isQueen(queenPiece)) {
            return false;
        }
        boolean attackerWhite = !defenderWhite;
        if (Piece.isWhite(queenPiece) != attackerWhite) {
            return false;
        }
        byte king = position.kingSquare(defenderWhite);
        int backRank = defenderWhite ? 0 : 7;
        if (Field.getX(king) != 4 || Field.getY(king) != backRank) {
            return false;
        }
        int expectedQueenRank = defenderWhite ? 1 : 6;
        if (Field.getX(queenSq) != 5 || Field.getY(queenSq) != expectedQueenRank) {
            return false;
        }
        byte[] defenders = attackerWhite
                ? position.getAttackersByWhite(queenSq)
                : position.getAttackersByBlack(queenSq);
        boolean bishopDefends = false;
        boolean kingDefends = false;
        for (byte d : defenders) {
            byte dp = board[d];
            if (Piece.isBishop(dp)) {
                bishopDefends = true;
            }
            if (Piece.isKing(dp)) {
                kingDefends = true;
            }
        }
        return bishopDefends && !kingDefends;
    }

    /**
     * Detects the swallow's-tail (gueridon) mate: a single supported queen
     * checks orthogonally adjacent to the king while both rear-diagonal flight
     * squares are blocked by the king's own pieces and the square straight behind
     * the king is open (covered by the queen's line). Co-exists with
     * {@code support_mate}.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when the position is a swallow's-tail / gueridon mate
     */
    private static boolean isSwallowsTailMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1) {
            return false;
        }
        byte checkerSquare = checkers.get(0);
        if (!Piece.isQueen(board[checkerSquare])) {
            return false;
        }
        byte king = position.kingSquare(defenderWhite);
        if (king == Field.NO_SQUARE) {
            return false;
        }
        int kingFile = Field.getX(king);
        int kingRank = Field.getY(king);
        int dxToQueen = Field.getX(checkerSquare) - kingFile;
        int dyToQueen = Field.getY(checkerSquare) - kingRank;
        if (Math.abs(dxToQueen) > 1 || Math.abs(dyToQueen) > 1 || (dxToQueen == 0 && dyToQueen == 0)) {
            return false;
        }
        int awayFile = -dxToQueen;
        int awayRank = -dyToQueen;
        if (awayFile != 0 && awayRank != 0) {
            return false;
        }
        int perpFile = awayFile == 0 ? 1 : 0;
        int perpRank = awayFile == 0 ? 0 : 1;
        int tail1File = kingFile + awayFile + perpFile;
        int tail1Rank = kingRank + awayRank + perpRank;
        int tail2File = kingFile + awayFile - perpFile;
        int tail2Rank = kingRank + awayRank - perpRank;
        if (!Field.isOnBoard(tail1File, tail1Rank) || !Field.isOnBoard(tail2File, tail2Rank)) {
            return false;
        }
        if (!isFriendlyPiece(board[Field.toIndex(tail1File, tail1Rank)], defenderWhite)
                || !isFriendlyPiece(board[Field.toIndex(tail2File, tail2Rank)], defenderWhite)) {
            return false;
        }
        int behindFile = kingFile + awayFile;
        int behindRank = kingRank + awayRank;
        if (Field.isOnBoard(behindFile, behindRank)
                && isFriendlyPiece(board[Field.toIndex(behindFile, behindRank)], defenderWhite)) {
            return false;
        }
        byte[] support = defenderWhite
                ? position.getAttackersByBlack(checkerSquare)
                : position.getAttackersByWhite(checkerSquare);
        for (byte defender : support) {
            if (defender != checkerSquare && board[defender] != Piece.EMPTY) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects the dovetail (Cozio) mate: a lone queen checks diagonally adjacent
     * to the king while the two far orthogonal flight squares (the "feathers")
     * are both occupied by the king's own pieces. Co-exists with
     * {@code support_mate}.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when the position is a dovetail / Cozio mate
     */
    private static boolean isDovetailMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1) {
            return false;
        }
        byte checker = checkers.get(0);
        if (!Piece.isQueen(board[checker])) {
            return false;
        }
        byte king = position.kingSquare(defenderWhite);
        if (king == Field.NO_SQUARE) {
            return false;
        }
        int kingFile = Field.getX(king);
        int kingRank = Field.getY(king);
        int fileDelta = Field.getX(checker) - kingFile;
        int rankDelta = Field.getY(checker) - kingRank;
        if (Math.abs(fileDelta) != 1 || Math.abs(rankDelta) != 1) {
            return false;
        }
        return isFriendlyFlight(board, kingFile - fileDelta, kingRank, defenderWhite)
                && isFriendlyFlight(board, kingFile, kingRank - rankDelta, defenderWhite);
    }

    /**
     * Returns whether the given square holds a piece of the mated side.
     *
     * @param piece the piece byte to inspect
     * @param defenderWhite whether the mated side is White
     * @return true when the piece belongs to the mated side
     */
    private static boolean isFriendlyPiece(byte piece, boolean defenderWhite) {
        return piece != Piece.EMPTY && Piece.isWhite(piece) == defenderWhite;
    }

    /**
     * Returns whether the given file/rank is on the board and holds a piece of
     * the mated side (a flight square the king cannot use).
     *
     * @param board board snapshot
     * @param file target file
     * @param rank target rank
     * @param defenderWhite whether the mated side is White
     * @return true when an on-board square holds a friendly (mated-side) piece
     */
    private static boolean isFriendlyFlight(byte[] board, int file, int rank, boolean defenderWhite) {
        return Field.isOnBoard(file, rank) && isFriendlyPiece(board[Field.toIndex(file, rank)], defenderWhite);
    }

    /**
     * Detects the hook mate: a lone rook checks the king along its rank or file
     * (within two squares), a friendly knight defends that rook, a friendly pawn
     * defends that knight, and the mated king is hemmed in by one of its own
     * adjacent pawns. The rook+knight+pawn interlock plus the king's own blocker
     * distinguishes the hook from a plain rook / Arabian mate.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when the position is a hook mate
     */
    private static boolean isHookMate(byte[] board, Position position, boolean defenderWhite, List<Byte> checkers) {
        if (checkers.size() != 1) {
            return false;
        }
        byte checker = checkers.get(0);
        byte checkerPiece = board[checker];
        boolean attackerWhite = !defenderWhite;
        if (!Piece.isRook(checkerPiece) || Piece.isWhite(checkerPiece) != attackerWhite) {
            return false;
        }
        byte kingSquare = position.kingSquare(defenderWhite);
        int kingFile = Field.getX(kingSquare);
        int kingRank = Field.getY(kingSquare);
        int rookFile = Field.getX(checker);
        int rookRank = Field.getY(checker);
        boolean sameLine = rookFile == kingFile || rookRank == kingRank;
        if (!sameLine) {
            return false;
        }
        int lineDistance = rookFile == kingFile
                ? Math.abs(rookRank - kingRank)
                : Math.abs(rookFile - kingFile);
        if (lineDistance < 1 || lineDistance > 2) {
            return false;
        }
        for (byte sq = 0; sq < 64; sq++) {
            byte piece = board[sq];
            if (Piece.isQueen(piece) && Piece.isWhite(piece) == attackerWhite) {
                return false;
            }
        }
        byte hookKnight = hookMateKnightDefending(board, position, attackerWhite, checker);
        if (hookKnight == Field.NO_SQUARE) {
            return false;
        }
        if (!hookMatePawnDefends(board, position, attackerWhite, hookKnight)) {
            return false;
        }
        return hookMateKingHasOwnAdjacentPawn(board, defenderWhite, kingFile, kingRank);
    }

    /**
     * Returns the square of an attacking knight that defends the checking rook,
     * or {@link Field#NO_SQUARE} when none does.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param attackerWhite whether the mating side is White
     * @param rookSquare the checking rook square
     * @return the defending knight square, or {@link Field#NO_SQUARE}
     */
    private static byte hookMateKnightDefending(byte[] board, Position position, boolean attackerWhite,
            byte rookSquare) {
        byte[] defenders = attackerWhite
                ? position.getAttackersByWhite(rookSquare)
                : position.getAttackersByBlack(rookSquare);
        for (byte sq : defenders) {
            byte piece = board[sq];
            if (Piece.isKnight(piece) && Piece.isWhite(piece) == attackerWhite) {
                return sq;
            }
        }
        return Field.NO_SQUARE;
    }

    /**
     * Returns whether an attacking pawn defends the given knight square.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param attackerWhite whether the mating side is White
     * @param knightSquare the knight square to check support for
     * @return true when an attacking pawn defends the knight
     */
    private static boolean hookMatePawnDefends(byte[] board, Position position, boolean attackerWhite,
            byte knightSquare) {
        byte[] defenders = attackerWhite
                ? position.getAttackersByWhite(knightSquare)
                : position.getAttackersByBlack(knightSquare);
        for (byte sq : defenders) {
            byte piece = board[sq];
            if (Piece.isPawn(piece) && Piece.isWhite(piece) == attackerWhite) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the mated king has at least one of its own pawns on an
     * adjacent square.
     *
     * @param board board snapshot
     * @param defenderWhite whether the mated side is White
     * @param kingFile mated king file
     * @param kingRank mated king rank
     * @return true when a friendly pawn sits adjacent to the king
     */
    private static boolean hookMateKingHasOwnAdjacentPawn(byte[] board, boolean defenderWhite, int kingFile,
            int kingRank) {
        for (int df = -1; df <= 1; df++) {
            for (int dr = -1; dr <= 1; dr++) {
                if (df == 0 && dr == 0) {
                    continue;
                }
                int file = kingFile + df;
                int rank = kingRank + dr;
                if (!Field.isOnBoard(file, rank)) {
                    continue;
                }
                byte piece = board[Field.toIndex(file, rank)];
                if (Piece.isPawn(piece) && Piece.isWhite(piece) == defenderWhite) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Detects the Opera mate (Morphy's Opera Game, Rd8#): a single rook delivers
     * a lateral back-rank check while a friendly bishop on a long diagonal guards
     * an empty diagonal flight square in front of the king. Excludes the plain
     * pawn-boxed back-rank shape; co-exists with {@code support_mate}.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when a rook back-rank mate has a bishop-guarded diagonal flight
     */
    private static boolean isOperaMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1) {
            return false;
        }
        byte king = position.kingSquare(defenderWhite);
        if (king == Field.NO_SQUARE) {
            return false;
        }
        int backRank = defenderWhite ? 0 : 7;
        if (Field.getY(king) != backRank) {
            return false;
        }
        byte checker = checkers.get(0);
        if (!Piece.isRook(board[checker])) {
            return false;
        }
        if (Field.getY(checker) != backRank || Field.getX(checker) == Field.getX(king)) {
            return false;
        }
        if (operaMateEscapeRankBlockers(board, defenderWhite, Field.getX(king)) >= 2) {
            return false;
        }
        boolean winnerWhite = !defenderWhite;
        int kingFile = Field.getX(king);
        int forward = defenderWhite ? 1 : -1;
        for (int df = -1; df <= 1; df += 2) {
            int flightFile = kingFile + df;
            int flightRank = backRank + forward;
            if (!Field.isOnBoard(flightFile, flightRank)) {
                continue;
            }
            byte flightSquare = (byte) Field.toIndex(flightFile, flightRank);
            if (board[flightSquare] != Piece.EMPTY) {
                continue;
            }
            if (operaMateBishopGuards(board, flightFile, flightRank, winnerWhite)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Counts mated-side blockers on the three escape-rank squares in front of a
     * back-rank king (used to exclude the plain pawn-boxed back-rank shape).
     *
     * @param board board snapshot
     * @param defenderWhite whether the mated side is White
     * @param kingFile king file
     * @return number of friendly blockers on the escape rank
     */
    private static int operaMateEscapeRankBlockers(byte[] board, boolean defenderWhite, int kingFile) {
        int escapeRank = defenderWhite ? 1 : 6;
        int blockers = 0;
        for (int file = kingFile - 1; file <= kingFile + 1; file++) {
            if (!Field.isOnBoard(file, escapeRank)) {
                continue;
            }
            byte piece = board[Field.toIndex(file, escapeRank)];
            if (piece != Piece.EMPTY && Piece.isWhite(piece) == defenderWhite) {
                blockers++;
            }
        }
        return blockers;
    }

    /**
     * Returns whether a bishop (or queen on a diagonal) of the winning side
     * attacks the given square along an unobstructed diagonal.
     *
     * @param board board snapshot
     * @param file target file
     * @param rank target rank
     * @param winnerWhite whether the guarding side is White
     * @return true when a winning-side bishop or queen guards the square diagonally
     */
    private static boolean operaMateBishopGuards(byte[] board, int file, int rank, boolean winnerWhite) {
        int[][] dirs = { { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
        for (int[] d : dirs) {
            int f = file + d[0];
            int r = rank + d[1];
            while (Field.isOnBoard(f, r)) {
                byte piece = board[Field.toIndex(f, r)];
                if (piece != Piece.EMPTY) {
                    if ((Piece.isBishop(piece) || Piece.isQueen(piece)) && Piece.isWhite(piece) == winnerWhite) {
                        return true;
                    }
                    break;
                }
                f += d[0];
                r += d[1];
            }
        }
        return false;
    }

    /**
     * Detects the lawnmower (ladder/staircase) mate: a lone heavy checker pins
     * the king against a board edge along the edge line, while a second friendly
     * heavy piece seals the one adjacent parallel line. Excludes corners (which
     * overlap back-rank/corner/arabian/anastasia shapes).
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when the position is a lawnmower / ladder mate
     */
    private static boolean isLawnmowerMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1) {
            return false;
        }
        byte kingSquare = position.kingSquare(defenderWhite);
        if (kingSquare == Field.NO_SQUARE) {
            return false;
        }
        int kingFile = Field.getX(kingSquare);
        int kingRank = Field.getY(kingSquare);
        boolean fileEdge = kingFile == 0 || kingFile == 7;
        boolean rankEdge = kingRank == 0 || kingRank == 7;
        if (fileEdge == rankEdge) {
            return false;
        }
        byte checkerSquare = checkers.get(0);
        boolean attackerWhite = !defenderWhite;
        if (!isLawnmowerHeavy(board[checkerSquare], attackerWhite)) {
            return false;
        }
        int checkerFile = Field.getX(checkerSquare);
        int checkerRank = Field.getY(checkerSquare);
        if (fileEdge) {
            if (checkerFile != kingFile || !isLawnmowerClearBetween(board, checkerSquare, kingSquare)) {
                return false;
            }
            int adjacentFile = kingFile == 0 ? 1 : 6;
            return lawnmowerHeavyOnFile(board, attackerWhite, adjacentFile, checkerSquare);
        }
        if (checkerRank != kingRank || !isLawnmowerClearBetween(board, checkerSquare, kingSquare)) {
            return false;
        }
        int adjacentRank = kingRank == 0 ? 1 : 6;
        return lawnmowerHeavyOnRank(board, attackerWhite, adjacentRank, checkerSquare);
    }

    /**
     * Returns whether the piece is an attacking-side rook or queen.
     *
     * @param piece the piece byte to inspect
     * @param attackerWhite whether the mating side is White
     * @return true when the piece is an attacking-side rook or queen
     */
    private static boolean isLawnmowerHeavy(byte piece, boolean attackerWhite) {
        if (piece == Piece.EMPTY) {
            return false;
        }
        boolean colorMatches = Piece.isWhite(piece) == attackerWhite;
        return colorMatches && (Piece.isRook(piece) || Piece.isQueen(piece));
    }

    /**
     * Returns whether all squares strictly between two collinear squares are empty.
     *
     * @param board board snapshot
     * @param from start square
     * @param to end square
     * @return true when the path between is clear (and the squares are collinear)
     */
    private static boolean isLawnmowerClearBetween(byte[] board, byte from, byte to) {
        int fromFile = Field.getX(from);
        int fromRank = Field.getY(from);
        int toFile = Field.getX(to);
        int toRank = Field.getY(to);
        int stepFile = Integer.compare(toFile, fromFile);
        int stepRank = Integer.compare(toRank, fromRank);
        if (stepFile != 0 && stepRank != 0) {
            return false;
        }
        int file = fromFile + stepFile;
        int rank = fromRank + stepRank;
        while (file != toFile || rank != toRank) {
            if (board[Field.toIndex(file, rank)] != Piece.EMPTY) {
                return false;
            }
            file += stepFile;
            rank += stepRank;
        }
        return true;
    }

    /**
     * Returns whether an attacking heavy piece (other than the checker) stands on the file.
     *
     * @param board board snapshot
     * @param attackerWhite whether the mating side is White
     * @param file the file to scan
     * @param excludeSquare the checker square to skip
     * @return true when a second attacking heavy piece is on the file
     */
    private static boolean lawnmowerHeavyOnFile(byte[] board, boolean attackerWhite, int file, byte excludeSquare) {
        for (int rank = 0; rank < 8; rank++) {
            byte square = (byte) Field.toIndex(file, rank);
            if (square != excludeSquare && isLawnmowerHeavy(board[square], attackerWhite)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether an attacking heavy piece (other than the checker) stands on the rank.
     *
     * @param board board snapshot
     * @param attackerWhite whether the mating side is White
     * @param rank the rank to scan
     * @param excludeSquare the checker square to skip
     * @return true when a second attacking heavy piece is on the rank
     */
    private static boolean lawnmowerHeavyOnRank(byte[] board, boolean attackerWhite, int rank, byte excludeSquare) {
        for (int file = 0; file < 8; file++) {
            byte square = (byte) Field.toIndex(file, rank);
            if (square != excludeSquare && isLawnmowerHeavy(board[square], attackerWhite)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects the Blackburne mate: an attacking bishop checks a cornered king
     * along a diagonal while the second attacking bishop and a knight together
     * cover every flight square. The mating net is exactly two bishops and one
     * knight (no rook/queen/pawn assists). Co-exists with {@code support_mate}.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when a two-bishop-and-knight net mates with a bishop check
     */
    private static boolean isBlackburneMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1) {
            return false;
        }
        byte checker = checkers.get(0);
        byte checkerPiece = board[checker];
        boolean attackerWhite = !defenderWhite;
        if (!Piece.isBishop(checkerPiece) || Piece.isWhite(checkerPiece) != attackerWhite) {
            return false;
        }
        byte king = position.kingSquare(defenderWhite);
        if (king == Field.NO_SQUARE) {
            return false;
        }
        int kingFile = Field.getX(king);
        int kingRank = Field.getY(king);
        if (Math.abs(Field.getX(checker) - kingFile) != Math.abs(Field.getY(checker) - kingRank)) {
            return false;
        }
        byte secondBishop = blackburneMatingFrame(board, attackerWhite, checker);
        if (secondBishop == Field.NO_SQUARE) {
            return false;
        }
        return blackburneNetCovers(board, position, defenderWhite, attackerWhite, kingFile, kingRank, secondBishop);
    }

    /**
     * Validates the Blackburne material frame and returns the non-checking
     * bishop: exactly two attacking bishops and one knight, with no attacking
     * rook, queen, or pawn.
     *
     * @param board board snapshot
     * @param attackerWhite whether the mating side is White
     * @param checker the checking bishop square
     * @return the second attacking bishop square, or {@link Field#NO_SQUARE}
     */
    private static byte blackburneMatingFrame(byte[] board, boolean attackerWhite, byte checker) {
        int bishops = 0;
        int knights = 0;
        int heavyOrPawn = 0;
        byte secondBishop = Field.NO_SQUARE;
        for (byte square = 0; square < 64; square++) {
            byte piece = board[square];
            if (piece == Piece.EMPTY || Piece.isWhite(piece) != attackerWhite) {
                continue;
            }
            if (Piece.isBishop(piece)) {
                bishops++;
                if (square != checker) {
                    secondBishop = square;
                }
            } else if (Piece.isKnight(piece)) {
                knights++;
            } else if (Piece.isRook(piece) || Piece.isQueen(piece) || Piece.isPawn(piece)) {
                heavyOrPawn++;
            }
        }
        if (bishops != 2 || knights != 1 || heavyOrPawn != 0) {
            return Field.NO_SQUARE;
        }
        return secondBishop;
    }

    /**
     * Confirms the king's flight squares are sealed only by the two bishops and
     * the knight (king's-own pieces self-block), with both the second bishop and
     * the knight each load-bearing on at least one flight.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param attackerWhite whether the mating side is White
     * @param kingFile mated king file
     * @param kingRank mated king rank
     * @param secondBishop the non-checking bishop square
     * @return true when the minor-piece net seals the king with both bishop and knight load-bearing
     */
    private static boolean blackburneNetCovers(byte[] board, Position position, boolean defenderWhite,
            boolean attackerWhite, int kingFile, int kingRank, byte secondBishop) {
        boolean secondBishopCovers = false;
        boolean knightCovers = false;
        for (int fileStep = -1; fileStep <= 1; fileStep++) {
            for (int rankStep = -1; rankStep <= 1; rankStep++) {
                if (fileStep == 0 && rankStep == 0) {
                    continue;
                }
                int file = kingFile + fileStep;
                int rank = kingRank + rankStep;
                if (!Field.isOnBoard(file, rank)) {
                    continue;
                }
                byte square = (byte) Field.toIndex(file, rank);
                byte occupant = board[square];
                if (occupant != Piece.EMPTY && Piece.isWhite(occupant) == defenderWhite) {
                    continue;
                }
                byte[] attackers = attackerWhite
                        ? position.getAttackersByWhite(square)
                        : position.getAttackersByBlack(square);
                boolean coveredByMinor = false;
                for (byte attacker : attackers) {
                    byte attackerPiece = board[attacker];
                    if (Piece.isBishop(attackerPiece)) {
                        coveredByMinor = true;
                        if (attacker == secondBishop) {
                            secondBishopCovers = true;
                        }
                    } else if (Piece.isKnight(attackerPiece)) {
                        coveredByMinor = true;
                        knightCovers = true;
                    } else {
                        return false;
                    }
                }
                if (!coveredByMinor) {
                    return false;
                }
            }
        }
        return secondBishopCovers && knightCovers;
    }

    /**
     * Detects the Greco mate: an attacking bishop checks a king trapped in a
     * corner along the long diagonal while one of the king's own pawns hems it in
     * on an adjacent square, and an attacking rook or queen covers one of the
     * king's two empty non-corner flight squares. The mating force is exactly one
     * bishop plus at least one rook/queen (no attacking knight, no second
     * bishop), distinguishing it from the rook-delivered Opera mate and the
     * two-bishop-and-knight Blackburne mate. Co-exists with corner/support mate.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when a bishop mates a pawn-boxed cornered king with a heavy-piece-guarded flight
     */
    private static boolean isGrecoMate(byte[] board, Position position, boolean defenderWhite, List<Byte> checkers) {
        if (checkers.size() != 1) {
            return false;
        }
        byte checker = checkers.get(0);
        byte checkerPiece = board[checker];
        boolean attackerWhite = !defenderWhite;
        if (!Piece.isBishop(checkerPiece) || Piece.isWhite(checkerPiece) != attackerWhite) {
            return false;
        }
        byte king = position.kingSquare(defenderWhite);
        if (king != Field.A1 && king != Field.H1 && king != Field.A8 && king != Field.H8) {
            return false;
        }
        int kingFile = Field.getX(king);
        int kingRank = Field.getY(king);
        if (!grecoMateForceShape(board, attackerWhite)) {
            return false;
        }
        if (!grecoMateOwnPawnAdjacent(board, defenderWhite, kingFile, kingRank)) {
            return false;
        }
        int innerRank = kingRank == 7 ? 6 : 1;
        int innerFile = kingFile == 7 ? 6 : 1;
        int[][] flights = { { kingFile, innerRank }, { innerFile, kingRank } };
        for (int[] flight : flights) {
            if (!Field.isOnBoard(flight[0], flight[1])) {
                continue;
            }
            byte flightSquare = (byte) Field.toIndex(flight[0], flight[1]);
            if (board[flightSquare] != Piece.EMPTY) {
                continue;
            }
            if (grecoMateHeavyGuards(board, position, attackerWhite, flightSquare)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the attacking force is exactly one bishop and at least one
     * rook or queen, with no attacking knight and no second attacking bishop.
     *
     * @param board board snapshot
     * @param attackerWhite whether the mating side is White
     * @return true when the force matches the Greco one-bishop-plus-heavy shape
     */
    private static boolean grecoMateForceShape(byte[] board, boolean attackerWhite) {
        int bishops = 0;
        int knights = 0;
        int rooksQueens = 0;
        for (byte square = 0; square < 64; square++) {
            byte piece = board[square];
            if (piece == Piece.EMPTY || Piece.isWhite(piece) != attackerWhite) {
                continue;
            }
            if (Piece.isBishop(piece)) {
                bishops++;
            } else if (Piece.isKnight(piece)) {
                knights++;
            } else if (Piece.isRook(piece) || Piece.isQueen(piece)) {
                rooksQueens++;
            }
        }
        return bishops == 1 && knights == 0 && rooksQueens >= 1;
    }

    /**
     * Returns whether one of the mated king's own pawns sits on a square adjacent
     * to the king.
     *
     * @param board board snapshot
     * @param defenderWhite whether the mated side is White
     * @param kingFile mated king file
     * @param kingRank mated king rank
     * @return true when a friendly pawn hems the king in
     */
    private static boolean grecoMateOwnPawnAdjacent(byte[] board, boolean defenderWhite, int kingFile, int kingRank) {
        for (int fileStep = -1; fileStep <= 1; fileStep++) {
            for (int rankStep = -1; rankStep <= 1; rankStep++) {
                if (fileStep == 0 && rankStep == 0) {
                    continue;
                }
                int file = kingFile + fileStep;
                int rank = kingRank + rankStep;
                if (!Field.isOnBoard(file, rank)) {
                    continue;
                }
                byte piece = board[Field.toIndex(file, rank)];
                if (Piece.isPawn(piece) && Piece.isWhite(piece) == defenderWhite) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns whether an attacking rook or queen guards the given square.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param attackerWhite whether the mating side is White
     * @param square the flight square to test
     * @return true when an attacking rook or queen covers the square
     */
    private static boolean grecoMateHeavyGuards(byte[] board, Position position, boolean attackerWhite, byte square) {
        byte[] guards = attackerWhite
                ? position.getAttackersByWhite(square)
                : position.getAttackersByBlack(square);
        for (byte guard : guards) {
            byte piece = board[guard];
            if (Piece.isRook(piece) || Piece.isQueen(piece)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects the kill-box mate: a rook checks along the mated king's own file or
     * rank while a friendly queen a knight's-move from the king (off the rook's
     * line) seals the escape box, the king pinned against a board edge. Co-exists
     * with the generic support/corner mate.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when the position is a rook+queen kill-box mate
     */
    private static boolean isKillBoxMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1) {
            return false;
        }
        byte checker = checkers.get(0);
        byte checkerPiece = board[checker];
        boolean attackerWhite = !defenderWhite;
        if (!Piece.isRook(checkerPiece) || Piece.isWhite(checkerPiece) != attackerWhite) {
            return false;
        }
        byte king = position.kingSquare(defenderWhite);
        if (king == Field.NO_SQUARE) {
            return false;
        }
        int kingFile = Field.getX(king);
        int kingRank = Field.getY(king);
        int rookFile = Field.getX(checker);
        int rookRank = Field.getY(checker);
        boolean rookCutsLine = rookFile == kingFile || rookRank == kingRank;
        if (!rookCutsLine) {
            return false;
        }
        boolean onEdge = kingFile == 0 || kingFile == 7 || kingRank == 0 || kingRank == 7;
        if (!onEdge) {
            return false;
        }
        byte queen = killBoxKnightMoveQueen(board, attackerWhite, kingFile, kingRank);
        if (queen == Field.NO_SQUARE || queen == checker) {
            return false;
        }
        int queenFile = Field.getX(queen);
        int queenRank = Field.getY(queen);
        if ((rookFile == kingFile && queenFile == kingFile)
                || (rookRank == kingRank && queenRank == kingRank)) {
            return false;
        }
        return killBoxSealed(board, position, attackerWhite, kingFile, kingRank);
    }

    /**
     * Returns an attacking queen exactly a knight's-move from the king.
     *
     * @param board board snapshot
     * @param attackerWhite whether the mating side is White
     * @param kingFile mated king file
     * @param kingRank mated king rank
     * @return queen square or {@link Field#NO_SQUARE}
     */
    private static byte killBoxKnightMoveQueen(byte[] board, boolean attackerWhite, int kingFile, int kingRank) {
        int[][] knight = { { 1, 2 }, { 2, 1 }, { -1, 2 }, { -2, 1 }, { 1, -2 }, { 2, -1 }, { -1, -2 }, { -2, -1 } };
        for (int[] delta : knight) {
            int file = kingFile + delta[0];
            int rank = kingRank + delta[1];
            if (!Field.isOnBoard(file, rank)) {
                continue;
            }
            byte square = (byte) Field.toIndex(file, rank);
            byte piece = board[square];
            if (Piece.isQueen(piece) && Piece.isWhite(piece) == attackerWhite) {
                return square;
            }
        }
        return Field.NO_SQUARE;
    }

    /**
     * Returns whether every on-board square adjacent to the king is sealed:
     * occupied (by either side) or attacked by the mating side.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param attackerWhite whether the mating side is White
     * @param kingFile mated king file
     * @param kingRank mated king rank
     * @return true when no empty, unattacked flight square exists
     */
    private static boolean killBoxSealed(byte[] board, Position position, boolean attackerWhite,
            int kingFile, int kingRank) {
        for (int df = -1; df <= 1; df++) {
            for (int dr = -1; dr <= 1; dr++) {
                if (df == 0 && dr == 0) {
                    continue;
                }
                int file = kingFile + df;
                int rank = kingRank + dr;
                if (!Field.isOnBoard(file, rank)) {
                    continue;
                }
                byte square = (byte) Field.toIndex(file, rank);
                if (board[square] != Piece.EMPTY) {
                    continue;
                }
                byte[] attackers = attackerWhite
                        ? position.getAttackersByWhite(square)
                        : position.getAttackersByBlack(square);
                if (attackers.length == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Detects the Reti mate: a bishop delivers a diagonal check to a king hemmed
     * against an edge/corner by at least two of its own pieces, while a friendly
     * long-range piece (queen, rook, or second bishop) stands directly behind the
     * checking bishop on the same diagonal, x-raying through it. Co-exists with
     * the generic support-mate geometry.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when a backed bishop mates a king boxed by its own pieces
     */
    private static boolean isRetiMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1) {
            return false;
        }
        byte checkerSquare = checkers.get(0);
        byte checkerPiece = board[checkerSquare];
        if (!Piece.isBishop(checkerPiece)) {
            return false;
        }
        boolean attackerWhite = !defenderWhite;
        if (Piece.isWhite(checkerPiece) != attackerWhite) {
            return false;
        }
        byte king = position.kingSquare(defenderWhite);
        if (king == Field.NO_SQUARE) {
            return false;
        }
        int kingFile = Field.getX(king);
        int kingRank = Field.getY(king);
        int bishopFile = Field.getX(checkerSquare);
        int bishopRank = Field.getY(checkerSquare);
        if (Math.abs(bishopFile - kingFile) != Math.abs(bishopRank - kingRank)
                || bishopFile == kingFile) {
            return false;
        }
        int stepFile = bishopFile > kingFile ? 1 : -1;
        int stepRank = bishopRank > kingRank ? 1 : -1;
        if (!retiMateHasDiagonalBacker(board, bishopFile, bishopRank, stepFile, stepRank, attackerWhite)) {
            return false;
        }
        return retiMateKingBoxed(board, kingFile, kingRank, defenderWhite);
    }

    /**
     * Returns whether a queen, rook, or bishop of the attacking side is the first
     * occupied square behind the checking bishop along the same diagonal.
     *
     * @param board board snapshot
     * @param bishopFile checking-bishop file
     * @param bishopRank checking-bishop rank
     * @param stepFile file direction from king toward bishop (and beyond)
     * @param stepRank rank direction from king toward bishop (and beyond)
     * @param attackerWhite whether the mating side is White
     * @return true when the first piece past the bishop is a winning-side backer
     */
    private static boolean retiMateHasDiagonalBacker(byte[] board, int bishopFile, int bishopRank,
            int stepFile, int stepRank, boolean attackerWhite) {
        int file = bishopFile + stepFile;
        int rank = bishopRank + stepRank;
        while (Field.isOnBoard(file, rank)) {
            byte piece = board[Field.toIndex(file, rank)];
            if (piece != Piece.EMPTY) {
                boolean rightColor = Piece.isWhite(piece) == attackerWhite;
                return rightColor && (Piece.isQueen(piece) || Piece.isRook(piece) || Piece.isBishop(piece));
            }
            file += stepFile;
            rank += stepRank;
        }
        return false;
    }

    /**
     * Returns whether the mated king is hemmed against an edge or corner by at
     * least two of its own pieces (at most six on-board neighbor squares).
     *
     * @param board board snapshot
     * @param kingFile mated king file
     * @param kingRank mated king rank
     * @param defenderWhite whether the mated side is White
     * @return true when the king is edge-confined and self-blocked by two or more own pieces
     */
    private static boolean retiMateKingBoxed(byte[] board, int kingFile, int kingRank, boolean defenderWhite) {
        int ownBoxers = 0;
        int onBoardNeighbors = 0;
        for (int fileStep = -1; fileStep <= 1; fileStep++) {
            for (int rankStep = -1; rankStep <= 1; rankStep++) {
                if (fileStep == 0 && rankStep == 0) {
                    continue;
                }
                int file = kingFile + fileStep;
                int rank = kingRank + rankStep;
                if (!Field.isOnBoard(file, rank)) {
                    continue;
                }
                onBoardNeighbors++;
                byte neighbor = board[Field.toIndex(file, rank)];
                if (neighbor != Piece.EMPTY && Piece.isWhite(neighbor) == defenderWhite) {
                    ownBoxers++;
                }
            }
        }
        return ownBoxers >= 2 && onBoardNeighbors <= 6;
    }

    /**
     * Detects the Anderssen mate: an attacking rook or queen delivers mate
     * orthogonally adjacent to a king trapped in a board corner, with the
     * checking piece shielded (defended) by a friendly attacking-side pawn, and
     * with NO pawn of the mated side adjacent to the cornered king. The pawn
     * shield means the cornered king cannot capture the checker; requiring the
     * mated king to have no own adjacent pawn excludes the otherwise-overlapping
     * Damiano mate. Co-exists with the generic corner/support patterns.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when a pawn-shielded rook or queen mates a cornered king not hemmed by its own pawn
     */
    private static boolean isAnderssenMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1) {
            return false;
        }
        byte checker = checkers.get(0);
        byte checkerPiece = board[checker];
        boolean attackerWhite = !defenderWhite;
        if (Piece.isWhite(checkerPiece) != attackerWhite) {
            return false;
        }
        if (!Piece.isRook(checkerPiece) && !Piece.isQueen(checkerPiece)) {
            return false;
        }
        byte king = position.kingSquare(defenderWhite);
        if (king != Field.A1 && king != Field.H1 && king != Field.A8 && king != Field.H8) {
            return false;
        }
        int kingFile = Field.getX(king);
        int kingRank = Field.getY(king);
        int fileDelta = Math.abs(Field.getX(checker) - kingFile);
        int rankDelta = Math.abs(Field.getY(checker) - kingRank);
        if (fileDelta + rankDelta != 1) {
            return false;
        }
        if (anderssenMateKingHasOwnAdjacentPawn(board, defenderWhite, kingFile, kingRank)) {
            return false;
        }
        return anderssenMatePawnShieldsChecker(board, position, attackerWhite, checker);
    }

    /**
     * Returns whether the mated king has one of its own pawns on an adjacent
     * square (the Damiano-mate hallmark, used to exclude that overlapping pattern).
     *
     * @param board board snapshot
     * @param defenderWhite whether the mated side is White
     * @param kingFile mated king file
     * @param kingRank mated king rank
     * @return true when a friendly pawn sits adjacent to the cornered king
     */
    private static boolean anderssenMateKingHasOwnAdjacentPawn(byte[] board, boolean defenderWhite,
            int kingFile, int kingRank) {
        for (int fileStep = -1; fileStep <= 1; fileStep++) {
            for (int rankStep = -1; rankStep <= 1; rankStep++) {
                if (fileStep == 0 && rankStep == 0) {
                    continue;
                }
                int file = kingFile + fileStep;
                int rank = kingRank + rankStep;
                if (!Field.isOnBoard(file, rank)) {
                    continue;
                }
                byte piece = board[Field.toIndex(file, rank)];
                if (Piece.isPawn(piece) && Piece.isWhite(piece) == defenderWhite) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns whether an attacking-side pawn defends the checking piece.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param attackerWhite whether the mating side is White
     * @param checker the checking rook/queen square
     * @return true when an attacking pawn shields (defends) the checker
     */
    private static boolean anderssenMatePawnShieldsChecker(byte[] board, Position position, boolean attackerWhite,
            byte checker) {
        byte[] defenders = attackerWhite
                ? position.getAttackersByWhite(checker)
                : position.getAttackersByBlack(checker);
        for (byte defender : defenders) {
            if (defender != checker && Piece.isPawn(board[defender])
                    && Piece.isWhite(board[defender]) == attackerWhite) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects the Mayet mate: an attacking rook delivers mate orthogonally
     * adjacent to an edge-pinned king while a distant attacking bishop defends
     * that rook along a diagonal, and no attacking knight, pawn, or king defends
     * it. The distant-bishop signature distinguishes it from the Arabian mate
     * (knight-defended rook) and the hook mate. Co-exists with corner/support.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when a distant-bishop-defended rook mates an edge-pinned king
     */
    private static boolean isMayetMate(byte[] board, Position position, boolean defenderWhite, List<Byte> checkers) {
        if (checkers.size() != 1) {
            return false;
        }
        byte checker = checkers.get(0);
        byte checkerPiece = board[checker];
        boolean attackerWhite = !defenderWhite;
        if (!Piece.isRook(checkerPiece) || Piece.isWhite(checkerPiece) != attackerWhite) {
            return false;
        }
        byte king = position.kingSquare(defenderWhite);
        if (king == Field.NO_SQUARE) {
            return false;
        }
        int kingFile = Field.getX(king);
        int kingRank = Field.getY(king);
        boolean onEdge = kingFile == 0 || kingFile == 7 || kingRank == 0 || kingRank == 7;
        if (!onEdge) {
            return false;
        }
        int rookFile = Field.getX(checker);
        int rookRank = Field.getY(checker);
        boolean orthogonallyAdjacent = (rookFile == kingFile && Math.abs(rookRank - kingRank) == 1)
                || (rookRank == kingRank && Math.abs(rookFile - kingFile) == 1);
        if (!orthogonallyAdjacent) {
            return false;
        }
        return mayetMateBishopGuardsRook(board, position, attackerWhite, checker);
    }

    /**
     * Returns whether the checking rook is defended by a distant attacking bishop
     * (diagonal distance at least two) and by no attacking knight, pawn, or king.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param attackerWhite whether the mating side is White
     * @param rookSquare the checking rook square
     * @return true when a distant bishop is the sole minor/king defender of the rook
     */
    private static boolean mayetMateBishopGuardsRook(byte[] board, Position position, boolean attackerWhite,
            byte rookSquare) {
        byte[] defenders = attackerWhite
                ? position.getAttackersByWhite(rookSquare)
                : position.getAttackersByBlack(rookSquare);
        boolean distantBishopDefends = false;
        int rookFile = Field.getX(rookSquare);
        int rookRank = Field.getY(rookSquare);
        for (byte defender : defenders) {
            if (defender == rookSquare) {
                continue;
            }
            byte piece = board[defender];
            if (Piece.isWhite(piece) != attackerWhite) {
                continue;
            }
            if (Piece.isKnight(piece) || Piece.isPawn(piece) || Piece.isKing(piece)) {
                return false;
            }
            if (Piece.isBishop(piece)) {
                int diagonalDistance = Math.max(Math.abs(Field.getX(defender) - rookFile),
                        Math.abs(Field.getY(defender) - rookRank));
                if (diagonalDistance >= 2) {
                    distantBishopDefends = true;
                }
            }
        }
        return distantBishopDefends;
    }

    /**
     * Counts friendly blockers on the escape rank in front of a back-rank king.
     *
     * @param board board snapshot
     * @param defenderWhite whether the blocked king belongs to White
     * @param kingFile king file
     * @return number of friendly blockers on the three escape files
     */
    private static int friendlyEscapeRankBlockers(byte[] board, boolean defenderWhite, int kingFile) {
        int escapeRank = defenderWhite ? 1 : 6;
        int blockers = 0;
        for (int file = kingFile - 1; file <= kingFile + 1; file++) {
            if (!Field.isOnBoard(file, escapeRank)) {
                continue;
            }
            byte piece = board[Field.toIndex(file, escapeRank)];
            if (piece != Piece.EMPTY && Piece.isWhite(piece) == defenderWhite) {
                blockers++;
            }
        }
        return blockers;
    }

    /**
     * Counts mated-side pieces adjacent to the king.
     *
     * @param board board snapshot
     * @param king mated king square
     * @param defenderWhite whether White is mated
     * @return adjacent friendly blockers
     */
    private static int adjacentFriendlyBlockers(byte[] board, byte king, boolean defenderWhite) {
        if (king == Field.NO_SQUARE) {
            return 0;
        }
        int blockers = 0;
        int kingFile = Field.getX(king);
        int kingRank = Field.getY(king);
        for (int df = -1; df <= 1; df++) {
            for (int dr = -1; dr <= 1; dr++) {
                if (df == 0 && dr == 0) {
                    continue;
                }
                int file = kingFile + df;
                int rank = kingRank + dr;
                if (!Field.isOnBoard(file, rank)) {
                    continue;
                }
                byte piece = board[Field.toIndex(file, rank)];
                if (piece != Piece.EMPTY && Piece.isWhite(piece) == defenderWhite) {
                    blockers++;
                }
            }
        }
        return blockers;
    }

    /**
     * Returns the canonical label for a side.
     *
     * @param white whether the side is White
     * @return {@code white} or {@code black}
     */
    private static String sideName(boolean white) {
        return white ? WHITE : BLACK;
    }

}
