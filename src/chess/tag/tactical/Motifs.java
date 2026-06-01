package chess.tag.tactical;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.tag.core.Text;

/**
 * Derives higher-level tactical motif tags from the current position.
 * <p>
 * The output includes pins, skewers, overloaded defenders, and hanging pieces.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Motifs {

    /**
     * No-direction sentinel used when a sliding piece has no valid vectors.
     */
    private static final int[][] NO_DIRS = new int[0][];

    /**
     * Orthogonal directions for rook-like movement.
     */
    private static final int[][] ORTHO_DIRS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    /**
     * Diagonal directions for bishop-like movement.
     */
    private static final int[][] DIAG_DIRS = { { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };

    /**
     * Prevents instantiation of this utility class.
     */
    private Motifs() {
        // utility
    }

    /**
     * Returns tactical motif tags for the given position.
     *
     * @param position the position to inspect
     * @return an immutable list of tactical tags
     * @throws NullPointerException if {@code position} is {@code null}
     */
    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, POSITION);

        byte[] board = position.getBoard();
        List<String> tags = new ArrayList<>();

        addPins(tags, position, board, true);
        addPins(tags, position, board, false);
        addSkewers(tags, board);
        addOverloadedDefenders(tags, position, board, true);
        addOverloadedDefenders(tags, position, board, false);
        addHangingPieces(tags, position, board);
        addLosesMaterial(tags, position, board);
        addBatteries(tags, board);
        addXRays(tags, board);
        addBackRankWeakness(position, tags);
        addF7Weakness(position, tags);
        for (String decoyTag : decoys(position).split("\n")) {
            if (!decoyTag.isBlank() && !tags.contains(decoyTag)) {
                tags.add(decoyTag);
            }
        }
        addRooksOnSeventh(tags, board, true);
        addRooksOnSeventh(tags, board, false);

        return List.copyOf(tags);
    }

    /**
     * Adds a "rooks on the 7th" motif tag when one side has at least one rook on
     * the enemy's second-from-back rank (White rooks on rank 7, Black rooks on
     * rank 2). Two such rooks is the strong doubled form ("pigs on the 7th").
     * Pure board geometry; emits nothing when no rook qualifies.
     *
     * @param tags the mutable tag accumulator
     * @param board the board array
     * @param white whether to scan the White rooks (rank 7) or Black rooks (rank 2)
     */
    private static void addRooksOnSeventh(List<String> tags, byte[] board, boolean white) {
        int targetRank = white ? 6 : 1;
        List<String> squares = new ArrayList<>();
        for (byte sq = 0; sq < board.length; sq++) {
            byte piece = board[sq];
            if (piece == Piece.EMPTY || !Piece.isRook(piece) || Piece.isWhite(piece) != white) {
                continue;
            }
            if (Field.getY(sq) == targetRank) {
                squares.add(Text.squareNameLower(sq));
            }
        }
        if (squares.isEmpty()) {
            return;
        }
        String side = white ? WHITE : BLACK;
        String noun = squares.size() >= 2 ? "rooks " : "rook ";
        tags.add(formatTactical(ROOKS_ON_SEVENTH_PREFIX + side + SPACE_TEXT + noun
                + String.join(", ", squares)));
    }

    /**
     * Adds pin tags for one side.
     *
     * @param tags the mutable tag accumulator
     * @param position the analyzed position
     * @param board the board array
     * @param pinnedIsWhite whether the pinned side is White
     */
    private static void addPins(List<String> tags, Position position, byte[] board, boolean pinnedIsWhite) {
        for (int index = 0; index < board.length; index++) {
            String tag = pinTag(position, board, pinnedIsWhite, (byte) index);
            if (tag != null) {
                tags.add(tag);
            }
        }
    }

    /**
     * Builds a pin tag when the inspected piece is pinned to its king.
     *
     * @param position the analyzed position
     * @param board the board array
     * @param pinnedIsWhite whether the pinned side is White
     * @param square the candidate pinned square
     * @return the formatted pin tag, or {@code null} when no pin exists
     */
    private static String pinTag(Position position, byte[] board, boolean pinnedIsWhite, byte square) {
        byte piece = board[square];
        if (!isPinnedCandidate(piece, pinnedIsWhite)) {
            return null;
        }
        Position.PinInfo info = position.findPinToOwnKing(square);
        if (info == null) {
            return null;
        }
        return formatTactical(PIN_PREFIX + Text.colorNameLower(info.pinnerPiece) + SPACE_TEXT
                + Text.pieceNameLower(info.pinnerPiece) + SPACE_TEXT + Text.squareNameLower(info.pinnerSquare)
                + PINS + Text.colorNameLower(piece) + SPACE_TEXT + Text.pieceNameLower(piece) + SPACE_TEXT
                + Text.squareNameLower(info.pinnedSquare) + TO_KING);
    }

    /**
     * Checks whether a piece can be a pinned candidate.
     *
     * @param piece the piece to inspect
     * @param pinnedIsWhite whether the pinned side is White
     * @return {@code true} when the piece can be pinned
     */
    private static boolean isPinnedCandidate(byte piece, boolean pinnedIsWhite) {
        return piece != Piece.EMPTY && Piece.isWhite(piece) == pinnedIsWhite && !Piece.isKing(piece);
    }

    /**
     * Adds skewer tags for all sliding attackers.
     *
     * @param tags the mutable tag accumulator
     * @param board the board array
     */
    private static void addSkewers(List<String> tags, byte[] board) {
        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (isSlidingAttacker(piece)) {
                addSkewersForPiece(tags, board, piece, (byte) index);
            }
        }
    }

    /**
     * Adds skewer checks for one sliding attacker.
     *
     * @param tags the mutable tag accumulator
     * @param board the board array
     * @param piece the attacker piece
     * @param from the attacker square
     */
    private static void addSkewersForPiece(List<String> tags, byte[] board, byte piece, byte from) {
        if (Piece.isQueen(piece)) {
            checkSkewers(tags, board, piece, from, ORTHO_DIRS);
            checkSkewers(tags, board, piece, from, DIAG_DIRS);
            return;
        }
        checkSkewers(tags, board, piece, from, slidingDirections(piece));
    }

    /**
     * Checks whether a piece is a sliding attacker.
     *
     * @param piece the piece to inspect
     * @return {@code true} when the piece is a rook, bishop, or queen
     */
    private static boolean isSlidingAttacker(byte piece) {
        return piece != Piece.EMPTY && (Piece.isRook(piece) || Piece.isBishop(piece) || Piece.isQueen(piece));
    }

    /**
     * Returns the sliding directions appropriate for a piece.
     *
     * @param piece the piece to inspect
     * @return the set of sliding directions for the piece
     */
    private static int[][] slidingDirections(byte piece) {
        if (Piece.isBishop(piece)) {
            return DIAG_DIRS;
        }
        if (Piece.isRook(piece)) {
            return ORTHO_DIRS;
        }
        return NO_DIRS;
    }

    /**
     * Checks all directions for a skewer from a given attacker.
     *
     * @param tags the mutable tag accumulator
     * @param board the board array
     * @param piece the attacker piece
     * @param from the attacker square
     * @param dirs the directions to inspect
     */
    private static void checkSkewers(List<String> tags, byte[] board, byte piece, byte from, int[][] dirs) {
        boolean white = Piece.isWhite(piece);
        for (int[] dir : dirs) {
            String tag = skewerTagInDirection(board, piece, from, white, dir);
            if (tag != null) {
                tags.add(tag);
            }
        }
    }

    /**
     * Builds a skewer tag when a valid skewer is found.
     *
     * @param board the board array
     * @param piece the attacking piece
     * @param from the attacker square
     * @param white whether the attacker is White
     * @param dir the direction being inspected
     * @return the formatted skewer tag, or {@code null} when no skewer exists
     */
    private static String skewerTagInDirection(byte[] board, byte piece, byte from, boolean white, int[] dir) {
        LineTarget first = firstOccupied(board, from, dir, 0);
        if (!isEnemyTarget(first, white)) {
            return null;
        }
        LineTarget second = firstOccupied(board, from, dir, 1);
        if (!isMajorSkewerFront(first.piece)
                || !isEnemyTarget(second, white) || !isValuableSkewerTarget(second.piece)
                || !isHigherValueTarget(first.piece, second.piece)) {
            return null;
        }
        return formatTactical(SKEWER_PREFIX + Text.colorNameLower(piece) + SPACE_TEXT + Text.pieceNameLower(piece)
                + SPACE_TEXT + Text.squareNameLower(from) + SKEWERS + Text.colorNameLower(first.piece) + SPACE_TEXT
                + Text.pieceNameLower(first.piece) + SPACE_TEXT + Text.squareNameLower((byte) first.index)
                + WITH + Text.colorNameLower(second.piece) + SPACE_TEXT + Text.pieceNameLower(second.piece)
                + SPACE_TEXT
                + Text.squareNameLower((byte) second.index) + BEHIND);
    }

    /**
     * Adds overloaded-defender tags for one side.
     *
     * @param tags the mutable tag accumulator
     * @param position the analyzed position
     * @param board the board array
     * @param white whether to inspect White or Black pieces
     */
    private static void addOverloadedDefenders(List<String> tags, Position position, byte[] board, boolean white) {
        Map<Byte, List<Byte>> overloaded = new HashMap<>();
        for (int index = 0; index < board.length; index++) {
            Byte defender = soleDefenderIfOverloaded(position, board, white, (byte) index);
            if (defender != null) {
                overloaded.computeIfAbsent(defender, k -> new ArrayList<>()).add((byte) index);
            }
        }
        for (Map.Entry<Byte, List<Byte>> entry : overloaded.entrySet()) {
            List<Byte> defended = entry.getValue();
            if (defended.size() < 2) {
                continue;
            }
            byte defenderSquare = entry.getKey();
            byte defenderPiece = board[defenderSquare];
            tags.add(formatTactical(OVERLOADED_DEFENDER_PREFIX + Text.colorNameLower(defenderPiece) + SPACE_TEXT
                    + Text.pieceNameLower(defenderPiece) + SPACE_TEXT + Text.squareNameLower(defenderSquare)
                    + DEFENDS + joinSquares(defended)));
        }
    }

    /**
     * Returns the sole defender square when a piece is defended by exactly one friendly piece.
     *
     * @param position the analyzed position
     * @param board the board array
     * @param white whether the inspected piece belongs to White
     * @param square the defended square
     * @return the sole defender square, or {@code null} if not overloaded
     */
    private static Byte soleDefenderIfOverloaded(Position position, byte[] board, boolean white, byte square) {
        byte piece = board[square];
        if (piece == Piece.EMPTY || Piece.isWhite(piece) != white || Piece.isKing(piece)) {
            return null;
        }
        if (countAttackers(position, !white, square) == 0) {
            return null;
        }
        byte[] defenders = getAttackers(position, white, square);
        return defenders.length == 1 ? defenders[0] : null;
    }

    /**
     * Joins defended squares into human-readable text.
     *
     * @param defended the defended squares to join
     * @return the joined square list
     */
    private static String joinSquares(List<Byte> defended) {
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < defended.size(); i++) {
            if (i > 0) {
                detail.append(i == defended.size() - 1 ? AND : COMMA_SPACE);
            }
            detail.append(Text.squareNameLower(defended.get(i)));
        }
        return detail.toString();
    }

    /**
     * Adds hanging-piece tags for all pieces on the board.
     *
     * @param tags the mutable tag accumulator
     * @param position the analyzed position
     * @param board the board array
     */
    private static void addHangingPieces(List<String> tags, Position position, byte[] board) {
        for (int index = 0; index < board.length; index++) {
            if (isHangingPiece(position, board, (byte) index)) {
                byte piece = board[index];
                tags.add(formatTactical(HANGING_PREFIX + Text.colorNameLower(piece) + SPACE_TEXT
                        + Text.pieceNameLower(piece) + SPACE_TEXT + Text.squareNameLower((byte) index)));
            }
        }
    }

    /**
     * Checks whether a piece is hanging.
     *
     * @param position the analyzed position
     * @param board the board array
     * @param square the square to inspect
     * @return {@code true} when the piece is attacked but not defended
     */
    private static boolean isHangingPiece(Position position, byte[] board, byte square) {
        byte piece = board[square];
        if (piece == Piece.EMPTY || Piece.isKing(piece) || Piece.isPawn(piece)) {
            return false;
        }
        boolean white = Piece.isWhite(piece);
        return countAttackers(position, !white, square) > 0
                && countAttackers(position, white, square) == 0;
    }

    /**
     * Counts attackers from one side using the core attack-query API.
     *
     * @param position the analyzed position
     * @param white whether to count White attackers or Black attackers
     * @param square the attacked square
     * @return the number of attackers from the requested side
     */
    private static int countAttackers(Position position, boolean white, byte square) {
        return white ? position.countAttackersByWhite(square) : position.countAttackersByBlack(square);
    }

    /**
     * Returns the attacker squares for one side using the core attack-query API.
     *
     * @param position the analyzed position
     * @param white whether to return White attackers or Black attackers
     * @param square the attacked square
     * @return the attacker squares from the requested side
     */
    private static byte[] getAttackers(Position position, boolean white, byte square) {
        return white ? position.getAttackersByWhite(square) : position.getAttackersByBlack(square);
    }

    /**
     * Returns the first occupied square in a direction, optionally skipping the first one.
     *
     * @param board the board array
     * @param from the starting square
     * @param dir the direction vector
     * @param skip how many occupied squares to skip before returning one
     * @return the located square, or {@code null} when the ray exits the board
     */
    private static LineTarget firstOccupied(byte[] board, byte from, int[] dir, int skip) {
        int x = Field.getX(from);
        int y = Field.getY(from);
        int seen = 0;
        while (true) {
            x += dir[0];
            y += dir[1];
            if (!Field.isOnBoard(x, y)) {
                return null;
            }
            int idx = Field.toIndex(x, y);
            if (board[idx] != Piece.EMPTY) {
                if (seen == skip) {
                    return new LineTarget(idx, board[idx]);
                }
                seen++;
            }
        }
    }

    /**
     * Checks whether a line target belongs to the enemy side.
     *
     * @param target the target to inspect
     * @param white whether the attacker is White
     * @return {@code true} when the target is an enemy piece
     */
    private static boolean isEnemyTarget(LineTarget target, boolean white) {
        return target != null && Piece.isWhite(target.piece) != white;
    }

    /**
     * Compares the material values of two pieces.
     *
     * @param firstPiece the first piece
     * @param secondPiece the second piece
     * @return {@code true} when the first piece is more valuable than the second
     */
    private static boolean isHigherValueTarget(byte firstPiece, byte secondPiece) {
        return tacticalValue(firstPiece) > tacticalValue(secondPiece);
    }

    /**
     * Returns a comparison value for line-tactic targets.
     *
     * @param piece the piece to inspect
     * @return material-like tactical value, with kings ranked highest
     */
    private static int tacticalValue(byte piece) {
        return Piece.isKing(piece) ? 10000 : Piece.getMaterialValue(piece);
    }

    /**
     * Checks whether a target behind a front piece is meaningful enough for a skewer.
     *
     * @param piece the target piece
     * @return true for kings and non-pawn material
     */
    private static boolean isValuableSkewerTarget(byte piece) {
        return Piece.isKing(piece) || Piece.isQueen(piece) || Piece.isRook(piece)
                || Piece.isBishop(piece) || Piece.isKnight(piece);
    }

    /**
     * Checks whether the front piece is important enough to be called a skewer.
     *
     * @param piece the front target
     * @return true for king, queen, and rook targets
     */
    private static boolean isMajorSkewerFront(byte piece) {
        return Piece.isKing(piece) || Piece.isQueen(piece) || Piece.isRook(piece);
    }

    /**
     * Wraps a tactical description in the canonical FACT payload format.
     *
     * @param text the tactical description text
     * @return the serialized tactical tag
     */
    private static String formatTactical(String text) {
        return FACT_PREFIX + TACTICAL + EQUAL_SIGN + QUOTE
                + text.replace(String.valueOf(QUOTE), String.valueOf(BACKSLASH) + QUOTE)
                + QUOTE;
    }

    /**
     * Holds one target discovered during ray scans.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class LineTarget {

        /**
         * The board index.
         */
        private final int index;

        /**
         * The piece on the square.
         */
        private final byte piece;

        /**
         * Creates a line target snapshot.
         *
         * @param index the board index
         * @param piece the piece on the square
         */
        private LineTarget(int index, byte piece) {
            this.index = index;
            this.piece = piece;
        }
    }

/**
 * Scans the legal captures available to the side to move, runs Static Exchange
 * Evaluation on each, and emits one loses_material tag per target square that can
 * be won (highest-SEE legal capturer wins the slot). Only legalMoves() captures
 * are considered, which is the soundness fix for See.see() ignoring pins.
 *
 * @param tags     mutable tag accumulator (same list the other add* helpers use)
 * @param position the position to inspect
 * @param board    position.getBoard() snapshot
 */
private static void addLosesMaterial(java.util.List<String> tags, Position position, byte[] board) {
    chess.core.MoveList legal = position.legalMoves();
    if (legal == null) {
        return;
    }
    final int moveCount = legal.size();

    short[] bestMove = new short[64];
    int[] bestSee = new int[64];
    boolean[] hit = new boolean[64];
    for (int i = 0; i < 64; i++) {
        bestSee[i] = Integer.MIN_VALUE;
    }

    for (int i = 0; i < moveCount; i++) {
        short move = legal.get(i);
        if (!position.isCapture(move)) {
            continue;
        }
        int to = position.actualToSquare(move) & 0xFF; // en-passant-aware target
        if (to < 0 || to >= 64) {
            continue;
        }
        int see = chess.eval.See.see(position, move);
        if (see <= 0) {
            continue; // only flag captures that strictly WIN material
        }
        if (see > bestSee[to]) {
            bestSee[to] = see;
            bestMove[to] = move;
            hit[to] = true;
        }
    }

    for (int to = 0; to < 64; to++) {
        if (hit[to]) {
            tags.add(losesMaterialTag(position, board, to, bestMove[to], bestSee[to]));
        }
    }
}

/**
 * Builds the canonical loses_material motif tag.
 * <p>
 * Form matches the project's other motif tags (see Generator.addForkTag /
 * addSkewerTag, which use TACTIC_MOTIF_PREFIX = "TACTIC: motif=", SIDE_FIELD =
 * " side=", CAND_MOVE_FIELD = " move="):
 * {@code TACTIC: motif=loses_material side=<loser> move=<uci> piece=<name> square=<sq> see=<cp>}.
 * </p>
 * side  = color of the piece being WON (the loser) = the side NOT to move.
 * move  = UCI of the winning legal capture.
 * piece = target piece name (lower-case); "pawn" for en passant (to-square empty).
 * square= target square name (lower-case).
 * see   = positive SEE centipawns.
 *
 * @param position    the analyzed position
 * @param board       the board snapshot
 * @param targetSquare the square of the piece being won
 * @param winningMove the highest-SEE legal capture onto that square
 * @param see         the positive SEE in centipawns
 * @return the formatted motif tag
 */
private static String losesMaterialTag(Position position, byte[] board, int targetSquare,
                                       short winningMove, int see) {
    boolean loserIsWhite = !position.isWhiteToMove();
    String side = loserIsWhite ? WHITE : BLACK;

    byte targetPiece = board[targetSquare];
    // For en passant the to-square is EMPTY; the victim is always a pawn.
    String pieceName = (targetPiece == Piece.EMPTY) ? "pawn" : Text.pieceNameLower(targetPiece);
    String squareName = Text.squareNameLower((byte) targetSquare);
    String uci = chess.core.Move.toString(winningMove);

    return TACTIC_MOTIF_PREFIX + "loses_material"
            + SIDE_FIELD + side
            + CAND_MOVE_FIELD + uci
            + " piece=" + pieceName
            + " square=" + squareName
            + " see=" + see;
}

    /**
     * Detects batteries: two friendly sliders aligned on a clear file/rank (rook/queen) or
     * diagonal (bishop/queen), bearing through empty squares onto the enemy king or a heavy
     * enemy piece (queen/rook). Purely static and geometric; each unordered slider pair is
     * reported at most once (the first king/heavy target found along the pair's line).
     *
     * @param tags  the mutable tag accumulator
     * @param board position.getBoard() snapshot
     */
    private static void addBatteries(List<String> tags, byte[] board) {
        java.util.Set<Integer> emittedPairs = new java.util.HashSet<>();
        addBatteriesForColor(tags, board, true, emittedPairs);
        addBatteriesForColor(tags, board, false, emittedPairs);
    }

    /**
     * Scans one side's rook/queen (orthogonal) and bishop/queen (diagonal) sliders as the
     * rear of a candidate battery.
     *
     * @param tags         the mutable tag accumulator
     * @param board        the board array
     * @param white        whether to scan the White sliders or the Black sliders
     * @param emittedPairs squares-pair guard so each battery is reported once
     */
    private static void addBatteriesForColor(List<String> tags, byte[] board, boolean white,
                                             java.util.Set<Integer> emittedPairs) {
        for (int sq = 0; sq < board.length; sq++) {
            byte rear = board[sq];
            if (rear == Piece.EMPTY || Piece.isWhite(rear) != white) {
                continue;
            }
            if (Piece.isRook(rear) || Piece.isQueen(rear)) {
                for (int[] dir : ORTHO_DIRS) {
                    addBatteryRay(tags, board, white, (byte) sq, dir[0], dir[1], false, emittedPairs);
                }
            }
            if (Piece.isBishop(rear) || Piece.isQueen(rear)) {
                for (int[] dir : DIAG_DIRS) {
                    addBatteryRay(tags, board, white, (byte) sq, dir[0], dir[1], true, emittedPairs);
                }
            }
        }
    }

    /**
     * From the rear slider on {@code rearSq}, walks (dx,dy): the first occupied square must be
     * a friendly slider of the matching kind (the front of the battery), reached through
     * empties. Beyond it, again through empties, the first piece must be an enemy king or
     * heavy piece (queen/rook) for the battery to count. The unordered slider pair is guarded
     * so the two opposing scan directions report it exactly once; when a queen+bishop or
     * queen+rook pair bears on enemies BOTH ways, the first direction scanned wins (a single,
     * always-true tag rather than two).
     *
     * @param tags         the mutable tag accumulator
     * @param board        the board array
     * @param white        whether the battery belongs to White
     * @param rearSq       the rear slider square
     * @param dx           the file step (-1, 0, or 1)
     * @param dy           the rank step (-1, 0, or 1)
     * @param diagonal     whether this is a diagonal (bishop/queen) line
     * @param emittedPairs squares-pair guard
     */
    private static void addBatteryRay(List<String> tags, byte[] board, boolean white, byte rearSq,
                                      int dx, int dy, boolean diagonal,
                                      java.util.Set<Integer> emittedPairs) {
        int fx = Field.getX(rearSq) + dx;
        int fy = Field.getY(rearSq) + dy;
        byte frontSq = Field.NO_SQUARE;
        while (Field.isOnBoard(fx, fy)) {
            byte occ = board[Field.toIndex(fx, fy)];
            if (occ != Piece.EMPTY) {
                frontSq = (byte) Field.toIndex(fx, fy);
                break;
            }
            fx += dx;
            fy += dy;
        }
        if (frontSq == Field.NO_SQUARE) {
            return;
        }
        byte front = board[frontSq];
        if (Piece.isWhite(front) != white) {
            return;
        }
        boolean frontMatches = diagonal
                ? (Piece.isBishop(front) || Piece.isQueen(front))
                : (Piece.isRook(front) || Piece.isQueen(front));
        if (!frontMatches) {
            return;
        }
        int rearIdx = rearSq & 0xFF;
        int frontIdx = frontSq & 0xFF;
        int pairKey = Math.min(rearIdx, frontIdx) * 64 + Math.max(rearIdx, frontIdx);
        if (emittedPairs.contains(pairKey)) {
            return;
        }
        int tx = Field.getX(frontSq) + dx;
        int ty = Field.getY(frontSq) + dy;
        byte targetSq = Field.NO_SQUARE;
        while (Field.isOnBoard(tx, ty)) {
            byte occ = board[Field.toIndex(tx, ty)];
            if (occ != Piece.EMPTY) {
                if (Piece.isWhite(occ) != white) {
                    targetSq = (byte) Field.toIndex(tx, ty);
                }
                break;
            }
            tx += dx;
            ty += dy;
        }
        if (targetSq == Field.NO_SQUARE) {
            return;
        }
        byte target = board[targetSq];
        if (!(Piece.isKing(target) || Piece.isQueen(target) || Piece.isRook(target))) {
            return;
        }
        emittedPairs.add(pairKey);
        tags.add(batteryTag(white, rearSq, frontSq, targetSq, diagonal,
                board[rearSq], board[frontSq], target));
    }

    /**
     * Builds the canonical battery motif tag.
     * <p>
     * Form: {@code TACTIC: motif=battery side=<color> pieces=<p1>@<sq1>,<p2>@<sq2>
     * line=<file|rank|diagonal> target=<piece>@<sq> detail="..."}. The side string uses the
     * project's WHITE/BLACK literals (same convention as {@code losesMaterialTag} and
     * {@code addRooksOnSeventh}); squares/files/ranks come from {@link Text#squareNameLower(byte)}.
     * </p>
     *
     * @param white      whether the battery belongs to White
     * @param rearSq     the rear slider square
     * @param frontSq    the front slider square
     * @param targetSq   the enemy target square
     * @param diagonal   whether the line is diagonal
     * @param rearPiece  the rear slider piece byte
     * @param frontPiece the front slider piece byte
     * @param target     the target piece byte
     * @return the formatted battery tag
     */
    private static String batteryTag(boolean white, byte rearSq, byte frontSq, byte targetSq,
                                     boolean diagonal, byte rearPiece, byte frontPiece, byte target) {
        boolean sameFile = Field.getX(rearSq) == Field.getX(frontSq);
        String lineKind = diagonal ? "diagonal" : (sameFile ? "file" : "rank");
        String detail;
        if (diagonal) {
            detail = "battery on the " + Text.squareNameLower(rearSq) + "-"
                    + Text.squareNameLower(frontSq) + " diagonal";
        } else if (sameFile) {
            detail = "doubled on the " + Text.squareNameLower(rearSq).charAt(0) + "-file";
        } else {
            detail = "doubled on the " + ordinalRank(Text.squareNameLower(rearSq).charAt(1) - '0') + " rank";
        }
        return TACTIC_MOTIF_PREFIX + "battery"
                + SIDE_FIELD + (white ? WHITE : BLACK)
                + " pieces=" + Text.pieceNameLower(rearPiece) + "@" + Text.squareNameLower(rearSq)
                + "," + Text.pieceNameLower(frontPiece) + "@" + Text.squareNameLower(frontSq)
                + " line=" + lineKind
                + " target=" + Text.pieceNameLower(target) + "@" + Text.squareNameLower(targetSq)
                + " detail=\"" + detail + "\"";
    }

    /**
     * Renders an English ordinal for a rank number (1..8).
     *
     * @param n the rank number
     * @return the ordinal string, e.g. "7th"
     */
    private static String ordinalRank(int n) {
        if (n == 1) {
            return "1st";
        }
        if (n == 2) {
            return "2nd";
        }
        if (n == 3) {
            return "3rd";
        }
        return n + "th";
    }

/**
 * Adds x_ray motif tags for every friendly slider whose ray passes through
 * EXACTLY ONE enemy piece (the front) and then reaches a second enemy piece of
 * value &gt;= the front piece (the behind target), with only empty squares
 * between the slider and the behind target.
 * <p>
 * Pure static alignment (no move, no search) -- the geometric cousin of
 * {@link #addSkewers}. It reuses the same ray primitive ({@link #firstOccupied})
 * the skewer detector uses, so the "exactly one blocker" property is structural:
 * the front is the first occupied square (skip 0) and the behind is the second
 * occupied square (skip 1); every square strictly between them is empty by
 * construction, and any third piece deeper on the ray is invisible. Conservative
 * gates, mirroring the house skewer detector:
 * <ul>
 *   <li>slider must be the correct sliding type for the line (rook/queen on
 *       orthogonals, bishop/queen on diagonals);</li>
 *   <li>the front piece must be ENEMY -- a friendly first blocker abandons the
 *       direction (own piece in front == no x-ray) -- and must NOT be a pawn (a
 *       pawn shield is not a meaningful x-ray motif; matches the skewer
 *       detector, whose regression tests assert pawn fronts/behinds never
 *       surface);</li>
 *   <li>the behind piece must be ENEMY and must NOT be a pawn (the project
 *       suppresses {@code behind=pawn@} for line tactics);</li>
 *   <li>the behind target's value must be &gt;= the front piece's value
 *       ({@link #tacticalValue}, kings ranked highest, matching the skewer
 *       detector).</li>
 * </ul>
 * Geometry is expressed purely as X/Y deltas, which is frame-invariant in this
 * engine's internal (vertically-inverted, side-to-move-relative) board, so it
 * never relies on absolute ranks.
 *
 * @param tags  the mutable tag accumulator (same list the other add* helpers use)
 * @param board the board snapshot from {@code position.getBoard()}
 */
private static void addXRays(List<String> tags, byte[] board) {
    for (int index = 0; index < board.length; index++) {
        byte piece = board[index];
        if (piece == Piece.EMPTY) {
            continue;
        }
        boolean isRook = Piece.isRook(piece);
        boolean isBishop = Piece.isBishop(piece);
        boolean isQueen = Piece.isQueen(piece);
        if (!isRook && !isBishop && !isQueen) {
            continue;
        }
        addXRaysForPiece(tags, board, piece, (byte) index, isRook, isBishop, isQueen);
    }
}

/**
 * Scans the relevant rays for one sliding piece and appends any x_ray tag found.
 *
 * @param tags     the mutable tag accumulator
 * @param board    the board snapshot
 * @param piece    the sliding attacker piece byte
 * @param from     the attacker square
 * @param isRook   whether the attacker is a rook
 * @param isBishop whether the attacker is a bishop
 * @param isQueen  whether the attacker is a queen
 */
private static void addXRaysForPiece(List<String> tags, byte[] board, byte piece, byte from,
        boolean isRook, boolean isBishop, boolean isQueen) {
    if (isQueen || isRook) {
        for (int[] dir : ORTHO_DIRS) {
            String tag = xRayTagInDirection(board, piece, from, dir);
            if (tag != null) {
                tags.add(tag);
            }
        }
    }
    if (isQueen || isBishop) {
        for (int[] dir : DIAG_DIRS) {
            String tag = xRayTagInDirection(board, piece, from, dir);
            if (tag != null) {
                tags.add(tag);
            }
        }
    }
}

/**
 * Builds an x_ray tag for a single direction, or {@code null} when no x-ray
 * alignment exists along it. Reuses {@link #firstOccupied},
 * {@link #isEnemyTarget}, and {@link #tacticalValue} from this class.
 *
 * @param board the board snapshot
 * @param piece the sliding attacker piece byte
 * @param from  the attacker square
 * @param dir   the direction vector to scan
 * @return the formatted x_ray tag, or {@code null} when this direction has none
 */
private static String xRayTagInDirection(byte[] board, byte piece, byte from, int[] dir) {
    boolean white = Piece.isWhite(piece);
    LineTarget front = firstOccupied(board, from, dir, 0);
    if (!isEnemyTarget(front, white) || !isMeaningfulXRayPiece(front.piece)) {
        return null; // empty ray, friendly first blocker (own piece in front), or a pawn shield
    }
    LineTarget behind = firstOccupied(board, from, dir, 1);
    if (!isEnemyTarget(behind, white) || !isMeaningfulXRayPiece(behind.piece)) {
        return null; // nothing behind, friendly piece behind, or a pawn behind (suppressed)
    }
    if (tacticalValue(behind.piece) < tacticalValue(front.piece)) {
        return null; // behind target must be at least as valuable as the front piece it hides behind
    }
    String side = white ? WHITE : BLACK;
    return "TACTIC: motif=x_ray side=" + side
            + " piece=" + Text.pieceNameLower(piece)
            + " square=" + Text.squareNameLower(from)
            + " front=" + Text.pieceNameLower(front.piece) + "@" + Text.squareNameLower((byte) front.index)
            + " behind=" + Text.pieceNameLower(behind.piece) + "@" + Text.squareNameLower((byte) behind.index)
            + " detail=\"" + Text.pieceNameLower(piece) + " on " + Text.squareNameLower(from)
            + " x-rays " + Text.pieceNameLower(front.piece) + " to "
            + Text.pieceNameLower(behind.piece) + " on " + Text.squareNameLower((byte) behind.index) + "\"";
}

/**
 * Checks whether a piece is meaningful as an x-ray front shield or behind
 * target. Pawns are excluded to match the house skewer detector's conservatism
 * (the project suppresses pawn fronts/behinds for line tactics).
 *
 * @param piece the piece to inspect
 * @return {@code true} for any non-pawn piece
 */
private static boolean isMeaningfulXRayPiece(byte piece) {
    return !Piece.isPawn(piece);
}

    /**
     * Back-rank weakness: an enemy king boxed on its own back rank by its own
     * pawns (no luft) and not currently in check -- a latent mating weakness.
     * Emits one tag per boxed king; side = the side that could exploit it.
     *
     * Grounded, conservative conditions (all must hold):
     *   (1) the king sits on its OWN back rank (white -> rank 1, black -> rank 8);
     *   (2) it is NOT currently in check (this is latent, not a mate);
     *   (3) every existing square in front of the king on the next rank
     *       (king-file -1,0,+1, clamped to the board) is occupied by a friendly
     *       pawn -- i.e. there is no luft escape forward;
     *   (4) the back rank is penetrable: there is at least one EMPTY square on the
     *       king's back rank with a clear rank-path to the king, so an enemy heavy
     *       piece could land there with check;
     *   (5) the exploiting side actually owns a rook or queen (the only pieces that
     *       deliver back-rank mate).
     * Conditions (4)+(5) keep the opening start position (back rank full of the
     * king's own pieces) quiet while real boxed-king positions fire.
     *
     * Frame note (verified empirically against ./out): the public accessors used
     * here are in absolute human coordinates and do NOT flip with side-to-move.
     * Field.getY(sq)==0 is rank 1 / white's back rank, ==7 is rank 8 / black's
     * back rank; Field.getX is the file (0=a..7=h); Piece.isWhite(raw) gives the
     * colour; index == Field.toIndex(x,y).
     */
    private static void addBackRankWeakness(Position position, List<String> out) {
        byte[] board = position.getBoard();
        for (int c = 0; c < 2; c++) {
            boolean kingIsWhite = (c == 0);
            byte kingSq = position.kingSquare(kingIsWhite);
            if (kingSq == Field.NO_SQUARE) {
                continue;
            }
            int kx = Field.getX(kingSq);
            int ky = Field.getY(kingSq);
            int backRank = kingIsWhite ? 0 : 7;   // y=0 -> rank 1, y=7 -> rank 8
            int frontRank = kingIsWhite ? 1 : 6;
            // (1) king on its own back rank
            if (ky != backRank) {
                continue;
            }
            // (2) not currently in check (latent, not a mate)
            if (position.inCheck(kingIsWhite)) {
                continue;
            }
            // (3) no luft: all existing forward squares are the king's own pawns
            boolean boxed = true;
            int frontSquares = 0;
            StringBuilder pawnList = new StringBuilder();
            for (int dx = -1; dx <= 1; dx++) {
                int fx = kx + dx;
                if (fx < 0 || fx > 7) {
                    continue;
                }
                int idx = Field.toIndex(fx, frontRank);
                byte v = board[idx];
                boolean ownPawn = v != Piece.EMPTY
                        && Piece.isPawn(v)
                        && (Piece.isWhite(v) == kingIsWhite);
                if (!ownPawn) {
                    boxed = false;
                    break;
                }
                frontSquares++;
                if (pawnList.length() > 0) {
                    pawnList.append(',');
                }
                pawnList.append(Text.squareNameLower((byte) idx));
            }
            if (!boxed || frontSquares == 0) {
                continue;
            }
            // (4) back rank penetrable: an empty back-rank square with a clear
            //     rank-path to the king (where an enemy rook/queen could mate)
            boolean openLanding = false;
            byte landingSq = Field.NO_SQUARE;
            for (int x = 0; x < 8; x++) {
                if (x == kx) {
                    continue;
                }
                int idx = Field.toIndex(x, backRank);
                if (board[idx] != Piece.EMPTY) {
                    continue;
                }
                int lo = Math.min(x, kx) + 1;
                int hi = Math.max(x, kx) - 1;
                boolean clear = true;
                for (int m = lo; m <= hi; m++) {
                    if (board[Field.toIndex(m, backRank)] != Piece.EMPTY) {
                        clear = false;
                        break;
                    }
                }
                if (clear) {
                    openLanding = true;
                    landingSq = (byte) idx;
                    break;
                }
            }
            if (!openLanding) {
                continue;
            }
            // (5) exploiting side owns a rook or queen (the mating pieces)
            boolean enemyHeavy = false;
            for (byte sq = 0; sq < 64; sq++) {
                byte v = board[sq];
                if (v == Piece.EMPTY || Piece.isWhite(v) == kingIsWhite) {
                    continue;
                }
                if (Piece.isRook(v) || Piece.isQueen(v)) {
                    enemyHeavy = true;
                    break;
                }
            }
            if (!enemyHeavy) {
                continue;
            }
            // Emit: side = the side that could exploit it (enemy of the boxed king).
            // Text.colorNameLower takes a signed piece value: +6 = white king, -6 = black king.
            String side = Text.colorNameLower((byte) (kingIsWhite ? -6 : 6));
            String kingColor = Text.colorNameLower((byte) (kingIsWhite ? 6 : -6));
            out.add("TACTIC: motif=back_rank_weakness side=" + side
                    + " square=" + Text.squareNameLower(kingSq)
                    + " detail=\"" + kingColor + " king boxed on back rank by own pawns "
                    + pawnList + " (no luft); landing " + Text.squareNameLower(landingSq) + "\"");
        }
    }

// motif=f7_weakness detector for chess.tag.tactical.Motifs.
// Purely static: grounded by absolute board geometry + Position.countAttackers*.
// No search, no speculation. VERIFIED at runtime against ./out (see notes).
//
// FRAME: confirmed ABSOLUTE in this tagger context. The existing
// addRooksOnSeventh in Motifs.java tests Field.getY(sq) == 6 for White's 7th
// rank directly, and Field defines toIndex(file,rank)=(7-rank)*8+file,
// getX=index%8, getY=7-index/8. A reflection probe confirmed:
//   toIndex(5,1)=53 -> "f2",  toIndex(5,6)=13 -> "f7",  getX(13)=getX(53)=5.
// So f2 = index 53 (rank 2), f7 = index 13 (rank 7); human file 'f' is getX==5.
//
// Soft square is f7 when the DEFENDING king is Black, f2 when White. We emit only
// when (a) the defending king is NOT in check (latent weakness, not an active
// threat), (b) the defending king is king-adjacent to the soft square (the
// classic e8/f8/g8/e7/g7 hug for f7, e1/f1/g1/e2/g2 for f2), and (c) the
// attacking side hits the square STRICTLY more times than the defender guards it
// (the adjacent king itself counts as a defender -> conservative).

private static void addF7Weakness(Position position, List<String> out) {
    addF7WeaknessForKing(position, out, false); // Black king -> f7
    addF7WeaknessForKing(position, out, true);  // White king -> f2
}

/**
 * @param defenderIsWhite true to inspect the White king's f2 soft spot,
 *                        false to inspect the Black king's f7 soft spot.
 */
private static void addF7WeaknessForKing(Position position, List<String> out, boolean defenderIsWhite) {
    byte kingSq = position.kingSquare(defenderIsWhite);
    if (kingSq == Field.NO_SQUARE) {
        return;
    }
    // Latent weakness only: skip if the defending king is currently in check.
    int kingAttackers = defenderIsWhite
            ? position.countAttackersByBlack(kingSq)   // black attacks white king
            : position.countAttackersByWhite(kingSq);  // white attacks black king
    if (kingAttackers > 0) {
        return;
    }
    // f2 (white) on absolute rank 2 (getY==1); f7 (black) on absolute rank 7
    // (getY==6); human f-file is getX==5.
    byte softSq = (byte) Field.toIndex(5, defenderIsWhite ? 1 : 6);
    // Require the defending king to hug the soft square (king-distance 1).
    int dxk = Math.abs(Field.getX(kingSq) - Field.getX(softSq));
    int dyk = Math.abs(Field.getY(kingSq) - Field.getY(softSq));
    if (dxk > 1 || dyk > 1) {
        return;
    }
    // Not the soft spot if a friendly NON-pawn parks on it.
    byte occ = position.getBoard()[softSq];
    if (occ != Piece.EMPTY && Piece.isWhite(occ) == defenderIsWhite && !Piece.isPawn(occ)) {
        return;
    }
    int attackers = defenderIsWhite
            ? position.countAttackersByBlack(softSq)   // attacker = black
            : position.countAttackersByWhite(softSq);  // attacker = white
    int defenders = defenderIsWhite
            ? position.countAttackersByWhite(softSq)   // defender = white
            : position.countAttackersByBlack(softSq);  // defender = black
    if (attackers <= 0 || attackers <= defenders) {
        return; // not over-attacked -> emit nothing
    }
    byte attackerColor = defenderIsWhite ? (byte) -1 : (byte) 1; // attacker opposite of defender
    byte defenderColor = defenderIsWhite ? (byte) 1 : (byte) -1;
    String square = Text.squareNameLower(softSq);
    String detail = "f-spot " + square + " attacked " + attackers
            + "x vs defended " + defenders + "x; "
            + Text.colorNameLower(defenderColor) + " king on " + Text.squareNameLower(kingSq);
    out.add("TACTIC: motif=f7_weakness side=" + Text.colorNameLower(attackerColor)
            + " square=" + square
            + " attackers=" + attackers
            + " defenders=" + defenders
            + " detail=\"" + detail + "\"");
}


    /**
     * Detects decoy (lure) sacrifices for the side to move.
     *
     * <p>A decoy is a forcing sacrifice whose point is to drag an enemy unit onto a
     * square where a concrete, PROVEN gain follows. Fully grounded and
     * deterministic:</p>
     *
     * <ol>
     *   <li>The candidate sacrifice, played on a {@code position.copy()}, must give
     *       check ({@code after.inCheck()}) but not be immediate mate (an immediate
     *       mate is a checkmate motif, not a decoy).</li>
     *   <li>The enemy must have EXACTLY ONE legal reply, and that reply must capture
     *       the just-sacrificed piece on the offered square: this proves the lure is
     *       forcing, the enemy unit is genuinely decoyed onto that square, and we
     *       genuinely give material up.</li>
     *   <li>In the resulting position the point is proven by a bounded, deterministic
     *       tool: a forced mate from {@code MateProver.proveMate(lured, 3, 20000)}
     *       (non-null Proof with {@code mateMoves() > 0} and a real best move), OR a
     *       follow-up capture with strictly positive Static Exchange Evaluation
     *       ({@code See.see > 0}) for the mover.</li>
     * </ol>
     *
     * <p>If neither holds, the candidate is skipped (no speculative tags). All
     * tactical decisions go through frame-agnostic APIs (play / inCheck /
     * isCheckmate / isCapture / legalMoves / See / MateProver).</p>
     *
     * <p>API note: {@code MateProver.proveMate} returns {@code null} when no mate is
     * found within the bounds, so the null check below is required.</p>
     *
     * @param position the position to inspect (side to move is the sacrificing side)
     * @return zero or more newline-terminated {@code TACTIC: motif=decoy ...} lines
     */
    public static String decoys(Position position) {
        StringBuilder out = new StringBuilder();
        boolean moverIsWhite = position.isWhiteToMove();
        String side = moverIsWhite ? "white" : "black";
        MoveList moves = position.legalMoves();
        for (int i = 0; i < moves.size(); i++) {
            short sac = moves.get(i);
            int sacTo = Move.getToIndex(sac);

            Position after = position.copy().play(sac);
            // The lure must be forcing: a check, but not already mate.
            if (!after.inCheck() || after.isCheckmate()) {
                continue;
            }
            MoveList replies = after.legalMoves();
            if (replies.size() != 1) {
                continue;
            }
            short reply = replies.get(0);

            // The single forced reply must DECOY an enemy unit: it is compelled to
            // capture our sacrificed piece on the offered square. Proves the enemy
            // unit is lured to a specific square and we genuinely give up the
            // sacrificed piece (it is taken off the board).
            if (!after.isCapture(reply) || Move.getToIndex(reply) != sacTo) {
                continue;
            }

            Position lured = after.copy().play(reply);

            // Prove the point of the decoy in the resulting position.
            String detail = null;
            chess.engine.MateProver.Proof proof =
                    chess.engine.MateProver.proveMate(lured, 3, 20000);
            if (proof != null && proof.mateMoves() > 0
                    && proof.bestMove() != Move.NO_MOVE) {
                detail = decoyDetail(position, sac, after, reply, sacTo)
                        + ", forced mate in " + proof.mateMoves()
                        + " (bounded mate search)";
            } else {
                short winCap = bestWinningCapture(lured);
                if (winCap != Move.NO_MOVE) {
                    int gain = chess.eval.See.see(lured, winCap);
                    detail = decoyDetail(position, sac, after, reply, sacTo)
                            + ", then " + Move.toString(winCap)
                            + " wins material (See=+" + gain + ")";
                }
            }
            if (detail == null) {
                continue;
            }

            out.append("TACTIC: motif=decoy side=").append(side)
               .append(" move=").append(Move.toString(sac))
               .append(" detail=\"").append(detail).append("\"\n");
        }
        return out.toString();
    }

    /**
     * Builds the concrete decoy phrase naming the sacrificed and lured pieces.
     *
     * <p>Frame note: the engine board is side-to-move-relative and flips after a
     * move. The sacrificed piece is read from the ORIGINAL position at the sac's
     * from-square; the lured enemy unit is read from {@code afterSac} at the
     * reply's from-square (the reply was generated against {@code afterSac}, so its
     * indices match that board). Reading the sacked piece off {@code afterSac}
     * would mislabel it because of the frame flip (verified at runtime).</p>
     */
    private static String decoyDetail(Position position, short sac,
            Position afterSac, short reply, int sacTo) {
        byte sacked = position.getBoard()[Move.getFromIndex(sac)];      // our piece, original frame
        byte luredUnit = afterSac.getBoard()[Move.getFromIndex(reply)]; // enemy unit, afterSac frame
        return decoyPieceName(sacked) + " sacrifice decoys enemy "
                + decoyPieceName(luredUnit) + " to " + decoySquareName(sacTo)
                + " (forced " + Move.toString(reply) + ")";
    }

    /**
     * Returns the mover's capturing move with the highest strictly-positive See in
     * the given position, or {@link Move#NO_MOVE} if no capture has See &gt; 0.
     */
    private static short bestWinningCapture(Position position) {
        MoveList moves = position.legalMoves();
        short best = Move.NO_MOVE;
        int bestSee = 0;
        for (int i = 0; i < moves.size(); i++) {
            short m = moves.get(i);
            if (!position.isCapture(m)) {
                continue;
            }
            int s = chess.eval.See.see(position, m);
            if (s > bestSee) {
                bestSee = s;
                best = m;
            }
        }
        return best;
    }

    /**
     * Lower-case piece name for decoy detail strings.
     */
    private static String decoyPieceName(byte piece) {
        if (Piece.isQueen(piece)) {
            return "queen";
        }
        if (Piece.isRook(piece)) {
            return "rook";
        }
        if (Piece.isBishop(piece)) {
            return "bishop";
        }
        if (Piece.isKnight(piece)) {
            return "knight";
        }
        if (Piece.isPawn(piece)) {
            return "pawn";
        }
        if (Piece.isKing(piece)) {
            return "king";
        }
        return "piece";
    }

    /**
     * Square name derived from X/Y deltas.
     */
    private static String decoySquareName(int index) {
        int x = Field.getX((byte) index);
        int y = Field.getY((byte) index);
        char file = (char) ('a' + x);
        char rank = (char) ('1' + y);
        return "" + file + rank;
    }
}
