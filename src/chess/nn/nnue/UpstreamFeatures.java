package chess.nn.nnue;

import java.util.Arrays;

import chess.core.Piece;
import chess.core.Position;

/**
 * Feature generation for Stockfish-style NNUE networks.
 *
 * <p>
 * Square and piece numbering in this class follows Stockfish conventions:
 * {@code a1 == 0}, {@code h8 == 63}, White pieces are {@code 1..6}, and Black
 * pieces are {@code 9..14}.
 * </p>
 */
final class UpstreamFeatures {

    /**
     * White color id.
     */
    static final int WHITE = 0;

    /**
     * Black color id.
     */
    static final int BLACK = 1;

    /**
     * Number of board squares.
     */
    static final int SQUARE_NB = 64;

    /**
     * Number of Stockfish piece codes.
     */
    static final int PIECE_NB = 16;

    /**
     * Pawn piece type.
     */
    static final int PAWN = 1;

    /**
     * Knight piece type.
     */
    static final int KNIGHT = 2;

    /**
     * Bishop piece type.
     */
    static final int BISHOP = 3;

    /**
     * Rook piece type.
     */
    static final int ROOK = 4;

    /**
     * Queen piece type.
     */
    static final int QUEEN = 5;

    /**
     * King piece type.
     */
    static final int KING = 6;

    /**
     * HalfKAv2_hm feature dimensions.
     */
    static final int HALF_KA_DIMENSIONS = 64 * (11 * 64) / 2;

    /**
     * HalfKAv2_hm feature hash.
     */
    static final int HALF_KA_HASH = 0x7f234cb8;

    /**
     * FullThreats feature hash.
     */
    static final int FULL_THREATS_HASH = 0x8f234cb8;

    /**
     * Piece-square offsets used by HalfKAv2_hm.
     */
    private static final int[][] PIECE_SQUARE_INDEX = buildPieceSquareIndex();

    /**
     * King buckets used by HalfKAv2_hm.
     */
    private static final int[] KING_BUCKETS = buildKingBuckets();

    /**
     * Horizontal-orientation table for HalfKAv2_hm.
     */
    private static final int[] HALF_KA_ORIENT = buildOrientTable(7, 0);

    /**
     * Horizontal-orientation table for FullThreats.
     */
    private static final int[] FULL_THREATS_ORIENT = buildOrientTable(0, 7);

    /**
     * Stockfish piece codes in deterministic order.
     */
    private static final int[] ALL_PIECES = {
            makePiece(WHITE, PAWN), makePiece(WHITE, KNIGHT), makePiece(WHITE, BISHOP),
            makePiece(WHITE, ROOK), makePiece(WHITE, QUEEN), makePiece(WHITE, KING),
            makePiece(BLACK, PAWN), makePiece(BLACK, KNIGHT), makePiece(BLACK, BISHOP),
            makePiece(BLACK, ROOK), makePiece(BLACK, QUEEN), makePiece(BLACK, KING)
    };

    /**
     * Knight deltas.
     */
    private static final int[][] KNIGHT_DELTAS = {
            { 1, 2 }, { 2, 1 }, { 2, -1 }, { 1, -2 },
            { -1, -2 }, { -2, -1 }, { -2, 1 }, { -1, 2 }
    };

    /**
     * King deltas.
     */
    private static final int[][] KING_DELTAS = {
            { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 },
            { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 }
    };

    /**
     * Bishop directions.
     */
    private static final int[][] BISHOP_DIRECTIONS = {
            { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 }
    };

    /**
     * Rook directions.
     */
    private static final int[][] ROOK_DIRECTIONS = {
            { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }
    };

    /**
     * Queen directions.
     */
    private static final int[][] QUEEN_DIRECTIONS = {
            { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 },
            { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }
    };

    /**
     * Current-development FullThreats lookup tables.
     */
    private static final ThreatTables CURRENT_THREAT_TABLES = new ThreatTables(
            60720,
            new int[] { 0, 6, 10, 8, 8, 10, 0, 0, 0, 6, 10, 8, 8, 10, 0, 0 },
            new int[][] {
                    { 0, 1, -1, 2, -1, -1 },
                    { 0, 1, 2, 3, 4, -1 },
                    { 0, 1, 2, 3, -1, -1 },
                    { 0, 1, 2, 3, -1, -1 },
                    { 0, 1, 2, 3, 4, -1 },
                    { -1, -1, -1, -1, -1, -1 }
            });

    /**
     * Stockfish 18 FullThreats lookup tables.
     */
    private static final ThreatTables SF18_THREAT_TABLES = new ThreatTables(
            79856,
            new int[] { 0, 6, 12, 10, 10, 12, 8, 0, 0, 6, 12, 10, 10, 12, 8, 0 },
            new int[][] {
                    { 0, 1, -1, 2, -1, -1 },
                    { 0, 1, 2, 3, 4, 5 },
                    { 0, 1, 2, 3, -1, 4 },
                    { 0, 1, 2, 3, -1, 4 },
                    { 0, 1, 2, 3, 4, 5 },
                    { 0, 1, 2, 3, -1, -1 }
            });

    /**
     * Precomputed knight pseudo targets per square, in {@link #pseudoTargets}
     * order, so the per-evaluation threat scan never rebuilds or sorts them.
     */
    private static final int[][] KNIGHT_TARGETS = buildLeaperTargets(KNIGHT);

    /**
     * Precomputed king pseudo targets per square, in {@link #pseudoTargets}
     * order.
     */
    private static final int[][] KING_TARGETS = buildLeaperTargets(KING);

    /**
     * Prevents instantiation.
     */
    private UpstreamFeatures() {
        // utility
    }

    /**
     * Returns the FullThreats dimension count for a variant.
     *
     * @param variant Stockfish NNUE variant
     * @return feature dimensions
     */
    static int threatDimensions(UpstreamNetwork.Variant variant) {
        return threatTables(variant).dimensions;
    }

    /**
     * Converts a CRTK position into Stockfish piece codes in Stockfish square order.
     *
     * @param position source position
     * @return 64-entry board
     */
    static int[] board(Position position) {
        int[] out = new int[SQUARE_NB];
        fillBoard(position, out);
        return out;
    }

    /**
     * Fills a caller-owned 64-entry board with Stockfish piece codes in
     * Stockfish square order, so per-node evaluation can reuse one buffer
     * instead of allocating. Reads squares through
     * {@link Position#pieceAt(int)} rather than the defensive
     * {@link Position#getBoard()} copy, keeping this path allocation-free.
     *
     * @param position source position
     * @param out 64-entry destination board; every entry is overwritten
     */
    static void fillBoard(Position position, int[] out) {
        for (int index = 0; index < SQUARE_NB; index++) {
            int square = squareFromPositionIndex(index);
            out[square] = encodedPiece(position.pieceAt(index));
        }
    }

    /**
     * Counts occupied squares.
     *
     * @param board Stockfish-order board
     * @return piece count
     */
    static int pieceCount(int[] board) {
        int count = 0;
        for (int piece : board) {
            if (piece != 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns active HalfKAv2_hm indices for one perspective.
     *
     * @param board Stockfish-order board
     * @param perspective perspective color
     * @return active indices
     */
    static int[] activeHalfKa(int[] board, int perspective) {
        int kingSquare = kingSquare(board, perspective);
        int[] active = new int[32];
        int count = 0;
        for (int square = 0; square < SQUARE_NB; square++) {
            int piece = board[square];
            if (piece == 0) {
                continue;
            }
            if (count == active.length) {
                active = Arrays.copyOf(active, active.length + 8);
            }
            active[count++] = halfKaIndex(perspective, square, piece, kingSquare);
        }
        return Arrays.copyOf(active, count);
    }

    /**
     * Returns active FullThreats indices for one perspective.
     *
     * @param board Stockfish-order board
     * @param perspective perspective color
     * @param variant Stockfish NNUE variant
     * @return active indices
     */
    static int[] activeThreats(int[] board, int perspective, UpstreamNetwork.Variant variant) {
        IntList active = new IntList(128);
        collectThreats(board, perspective, variant, active);
        return active.toArray();
    }

    /**
     * Collects active FullThreats indices into a caller-owned reusable list,
     * emitting exactly the indices {@link #activeThreats} would in the same
     * order, without any per-call allocation.
     *
     * @param board Stockfish-order board
     * @param perspective perspective color
     * @param variant Stockfish NNUE variant
     * @param active output list; cleared before features are appended
     */
    static void collectThreats(int[] board, int perspective, UpstreamNetwork.Variant variant, IntList active) {
        ThreatTables tables = threatTables(variant);
        int kingSquare = kingSquare(board, perspective);
        active.clear();
        ThreatContext context = new ThreatContext(perspective, 0, 0, kingSquare, tables, active);

        for (int colorSelector = WHITE; colorSelector <= BLACK; colorSelector++) {
            int color = perspective ^ colorSelector;
            for (int pieceType = PAWN; pieceType < KING; pieceType++) {
                int attacker = makePiece(color, pieceType);
                for (int from = 0; from < SQUARE_NB; from++) {
                    if (board[from] != attacker) {
                        continue;
                    }
                    context.attacker = attacker;
                    context.from = from;
                    appendThreatsFrom(board, context);
                }
            }
        }
    }

    /**
     * Reusable working storage for {@link #collectMoveThreatDeltas}, owned by
     * one search state so delta extraction allocates nothing per move.
     */
    static final class ThreatDeltaBuffers {

        /**
         * Maximum number of squares whose occupancy one move can change
         * (from, king target, captured square, rook origin, rook target).
         */
        static final int MAX_CHANGED = 5;

        /**
         * Stockfish squares whose occupancy changed, deduplicated.
         */
        final int[] changedSquares = new int[MAX_CHANGED];

        /**
         * Pre-move occupants parallel to {@link #changedSquares}.
         */
        final int[] beforePieces = new int[MAX_CHANGED];

        /**
         * Number of valid changed-square entries.
         */
        int changedCount;

        /**
         * Packed threat instances active before the move but not after.
         */
        final IntList removes = new IntList(64);

        /**
         * Packed threat instances active after the move but not before.
         */
        final IntList adds = new IntList(64);

        /**
         * Bitset over (square, direction) pairs marking slider rays already
         * re-walked, so overlapping changed squares process each ray once.
         */
        final long[] sliderSeen = new long[8];

        /**
         * Registers one changed square, skipping unchanged occupants.
         *
         * @param square Stockfish square
         * @param before pre-move Stockfish piece code, or {@code 0}
         * @param after post-move Stockfish piece code, or {@code 0}
         */
        void addChange(int square, int before, int after) {
            if (before == after) {
                return;
            }
            for (int i = 0; i < changedCount; i++) {
                if (changedSquares[i] == square) {
                    return;
                }
            }
            changedSquares[changedCount] = square;
            beforePieces[changedCount] = before;
            changedCount++;
        }

        /**
         * Clears all per-move state.
         */
        void reset() {
            changedCount = 0;
            removes.clear();
            adds.clear();
            Arrays.fill(sliderSeen, 0L);
        }
    }

    /**
     * Packs one threat instance into an int for delta bookkeeping.
     *
     * @param from attacker square
     * @param to target square
     * @param attacker attacker Stockfish piece code (4 bits)
     * @param victim victim Stockfish piece code (4 bits)
     * @return packed instance
     */
    private static int packInstance(int from, int to, int attacker, int victim) {
        return from | (to << 6) | (attacker << 12) | (victim << 16);
    }

    /**
     * Collects the exact set of threat instances that differ between the
     * pre-move board and {@code board} (the post-move board), as packed
     * remove/add lists in {@code buf}. The caller registers the changed
     * squares (with pre-move occupants) via
     * {@link ThreatDeltaBuffers#addChange} before calling.
     *
     * <p>
     * Every emitted remove is active on the pre-move board and every add on
     * the post-move board; after internal deduplication and cross-list
     * cancellation the two lists are exactly the symmetric difference of the
     * active instance sets, so applying them to a threat accumulator is
     * bit-identical to a full rebuild (integer addition commutes).
     * </p>
     *
     * @param board post-move Stockfish-order board
     * @param buf working buffers with changed squares pre-registered
     */
    static void collectMoveThreatDeltas(int[] board, ThreatDeltaBuffers buf) {
        for (int i = 0; i < buf.changedCount; i++) {
            int square = buf.changedSquares[i];
            int before = buf.beforePieces[i];
            int after = board[square];
            // (a) victim role: attackers of the square on each board.
            if (before != 0) {
                appendAttackersOf(square, before, board, buf, true, buf.removes);
            }
            if (after != 0) {
                appendAttackersOf(square, after, board, buf, false, buf.adds);
            }
            // (b) attacker role: full emission by each occupant on its board.
            if (before != 0 && typeOf(before) != KING) {
                emitThreatsBy(before, square, board, buf, true, buf.removes);
            }
            if (after != 0 && typeOf(after) != KING) {
                emitThreatsBy(after, square, board, buf, false, buf.adds);
            }
            // (c) sliders elsewhere whose ray crosses this square: re-walk the
            // whole ray from the slider on both boards.
            appendSliderRewalks(square, board, buf);
        }
        cancelCommonInstances(buf);
    }

    /**
     * Reads a pre-move occupant through the changed-square overrides.
     *
     * @param square Stockfish square
     * @param board post-move board
     * @param buf buffers holding the overrides
     * @return pre-move Stockfish piece code, or {@code 0}
     */
    private static int pieceBefore(int square, int[] board, ThreatDeltaBuffers buf) {
        for (int i = 0; i < buf.changedCount; i++) {
            if (buf.changedSquares[i] == square) {
                return buf.beforePieces[i];
            }
        }
        return board[square];
    }

    /**
     * Returns whether a square is one of the registered changed squares.
     *
     * @param square Stockfish square
     * @param buf buffers holding the changed squares
     * @return true when changed
     */
    private static boolean isChangedSquare(int square, ThreatDeltaBuffers buf) {
        for (int i = 0; i < buf.changedCount; i++) {
            if (buf.changedSquares[i] == square) {
                return true;
            }
        }
        return false;
    }

    /**
     * Appends all threat instances whose victim sits on {@code square},
     * mirroring the emission rules exactly: pawn diagonal captures, the
     * pawn push-block threat (only when the victim is a pawn), knight
     * leaps, and slider first-blocker rays. Kings never attack.
     *
     * @param square victim square
     * @param victim victim piece on the relevant board
     * @param board post-move board
     * @param buf buffers (supply the pre-move view)
     * @param before true to evaluate on the pre-move board
     * @param out packed-instance sink
     */
    private static void appendAttackersOf(
            int square,
            int victim,
            int[] board,
            ThreatDeltaBuffers buf,
            boolean before,
            IntList out) {
        int file = file(square);
        int rank = rank(square);
        for (int color = WHITE; color <= BLACK; color++) {
            int pawn = makePiece(color, PAWN);
            int behindRank = rank - (color == WHITE ? 1 : -1);
            if (behindRank >= 0 && behindRank < 8) {
                if (file > 0 && occupant(square(file - 1, behindRank), board, buf, before) == pawn) {
                    out.add(packInstance(square(file - 1, behindRank), square, pawn, victim));
                }
                if (file < 7 && occupant(square(file + 1, behindRank), board, buf, before) == pawn) {
                    out.add(packInstance(square(file + 1, behindRank), square, pawn, victim));
                }
                if (typeOf(victim) == PAWN
                        && occupant(square(file, behindRank), board, buf, before) == pawn) {
                    out.add(packInstance(square(file, behindRank), square, pawn, victim));
                }
            }
        }
        for (int target : KNIGHT_TARGETS[square]) {
            int piece = occupant(target, board, buf, before);
            if (piece != 0 && typeOf(piece) == KNIGHT) {
                out.add(packInstance(target, square, piece, victim));
            }
        }
        for (int direction = 0; direction < QUEEN_DIRECTIONS.length; direction++) {
            int attacker = firstPieceFrom(square, direction, board, buf, before);
            if (attacker == -1) {
                continue;
            }
            int piece = occupant(attacker, board, buf, before);
            if (slidesAlong(piece, direction)) {
                out.add(packInstance(attacker, square, piece, victim));
            }
        }
    }

    /**
     * Appends all threat instances emitted by one non-king piece, mirroring
     * {@link #appendThreatsFrom} against a board view.
     *
     * @param piece attacker piece
     * @param from attacker square
     * @param board post-move board
     * @param buf buffers (supply the pre-move view)
     * @param before true to evaluate on the pre-move board
     * @param out packed-instance sink
     */
    private static void emitThreatsBy(
            int piece,
            int from,
            int[] board,
            ThreatDeltaBuffers buf,
            boolean before,
            IntList out) {
        int type = typeOf(piece);
        if (type == PAWN) {
            int color = colorOf(piece);
            int nextRank = rank(from) + (color == WHITE ? 1 : -1);
            if (nextRank < 0 || nextRank >= 8) {
                return;
            }
            int file = file(from);
            if (file > 0) {
                int victim = occupant(square(file - 1, nextRank), board, buf, before);
                if (victim != 0) {
                    out.add(packInstance(from, square(file - 1, nextRank), piece, victim));
                }
            }
            if (file < 7) {
                int victim = occupant(square(file + 1, nextRank), board, buf, before);
                if (victim != 0) {
                    out.add(packInstance(from, square(file + 1, nextRank), piece, victim));
                }
            }
            int pushVictim = occupant(square(file, nextRank), board, buf, before);
            if (typeOf(pushVictim) == PAWN) {
                out.add(packInstance(from, square(file, nextRank), piece, pushVictim));
            }
            return;
        }
        if (type == KNIGHT) {
            for (int target : KNIGHT_TARGETS[from]) {
                int victim = occupant(target, board, buf, before);
                if (victim != 0) {
                    out.add(packInstance(from, target, piece, victim));
                }
            }
            return;
        }
        for (int direction = 0; direction < QUEEN_DIRECTIONS.length; direction++) {
            if (!slidesAlong(piece, direction)) {
                continue;
            }
            int target = firstPieceFrom(from, direction, board, buf, before);
            if (target != -1) {
                out.add(packInstance(from, target, piece, occupant(target, board, buf, before)));
            }
        }
    }

    /**
     * Re-walks every slider ray that crosses one changed square: for each
     * slider found by reverse scan on either board (and itself not on a
     * changed square), removes its pre-move first-blocker instance and adds
     * its post-move one. Walking the full ray from the slider on each board
     * makes the result immune to multiple changed squares on one ray.
     *
     * @param square changed square the rays cross
     * @param board post-move board
     * @param buf buffers
     */
    private static void appendSliderRewalks(int square, int[] board, ThreatDeltaBuffers buf) {
        for (int direction = 0; direction < QUEEN_DIRECTIONS.length; direction++) {
            scanForSlider(square, direction, board, buf, true);
            scanForSlider(square, direction, board, buf, false);
        }
    }

    /**
     * Finds the first piece from a changed square along one direction on one
     * board and, when it is an unmoved slider aimed back at the square,
     * re-walks its whole ray on both boards.
     *
     * @param square changed square
     * @param direction outgoing scan direction index
     * @param board post-move board
     * @param buf buffers
     * @param before true to scan the pre-move view
     */
    private static void scanForSlider(int square, int direction, int[] board, ThreatDeltaBuffers buf,
            boolean before) {
        int sliderSquare = firstPieceFrom(square, direction, board, buf, before);
        if (sliderSquare == -1 || isChangedSquare(sliderSquare, buf)) {
            return;
        }
        int piece = board[sliderSquare];
        int towardSquare = OPPOSITE_DIRECTION[direction];
        if (!slidesAlong(piece, towardSquare)) {
            return;
        }
        int seenKey = sliderSquare * QUEEN_DIRECTIONS.length + towardSquare;
        if ((buf.sliderSeen[seenKey >>> 6] & (1L << (seenKey & 63))) != 0) {
            return;
        }
        buf.sliderSeen[seenKey >>> 6] |= 1L << (seenKey & 63);
        int beforeTarget = firstPieceFrom(sliderSquare, towardSquare, board, buf, true);
        if (beforeTarget != -1) {
            buf.removes.add(packInstance(sliderSquare, beforeTarget, piece,
                    occupant(beforeTarget, board, buf, true)));
        }
        int afterTarget = firstPieceFrom(sliderSquare, towardSquare, board, buf, false);
        if (afterTarget != -1) {
            buf.adds.add(packInstance(sliderSquare, afterTarget, piece,
                    occupant(afterTarget, board, buf, false)));
        }
    }

    /**
     * Opposite ray direction indexes into {@link #QUEEN_DIRECTIONS}.
     */
    private static final int[] OPPOSITE_DIRECTION = buildOppositeDirections();

    /**
     * Builds the opposite-direction lookup table.
     *
     * @return per-direction opposite indexes
     */
    private static int[] buildOppositeDirections() {
        int[] out = new int[QUEEN_DIRECTIONS.length];
        for (int direction = 0; direction < QUEEN_DIRECTIONS.length; direction++) {
            int[] d = QUEEN_DIRECTIONS[direction];
            for (int i = 0; i < QUEEN_DIRECTIONS.length; i++) {
                if (QUEEN_DIRECTIONS[i][0] == -d[0] && QUEEN_DIRECTIONS[i][1] == -d[1]) {
                    out[direction] = i;
                }
            }
        }
        return out;
    }

    /**
     * Returns whether a piece slides along one {@link #QUEEN_DIRECTIONS}
     * index (bishop on diagonals, rook on straights, queen on both).
     *
     * @param piece Stockfish piece code
     * @param direction direction index
     * @return true when the piece attacks along the direction
     */
    private static boolean slidesAlong(int piece, int direction) {
        int type = typeOf(piece);
        if (type == QUEEN) {
            return true;
        }
        boolean diagonal = QUEEN_DIRECTIONS[direction][0] != 0 && QUEEN_DIRECTIONS[direction][1] != 0;
        return diagonal ? type == BISHOP : type == ROOK;
    }

    /**
     * Walks from a square along one direction and returns the first occupied
     * square on the requested board view, or {@code -1}.
     *
     * @param from origin square (excluded from the walk)
     * @param direction direction index into {@link #QUEEN_DIRECTIONS}
     * @param board post-move board
     * @param buf buffers (supply the pre-move view)
     * @param before true to read the pre-move view
     * @return first occupied square, or {@code -1}
     */
    private static int firstPieceFrom(int from, int direction, int[] board, ThreatDeltaBuffers buf,
            boolean before) {
        int df = QUEEN_DIRECTIONS[direction][0];
        int dr = QUEEN_DIRECTIONS[direction][1];
        int file = file(from) + df;
        int rank = rank(from) + dr;
        while (onBoard(file, rank)) {
            int square = square(file, rank);
            if (occupant(square, board, buf, before) != 0) {
                return square;
            }
            file += df;
            rank += dr;
        }
        return -1;
    }

    /**
     * Reads one square on the requested board view.
     *
     * @param square Stockfish square
     * @param board post-move board
     * @param buf buffers holding pre-move overrides
     * @param before true for the pre-move view
     * @return Stockfish piece code, or {@code 0}
     */
    private static int occupant(int square, int[] board, ThreatDeltaBuffers buf, boolean before) {
        return before ? pieceBefore(square, board, buf) : board[square];
    }

    /**
     * Deduplicates each packed-instance list and cancels instances present in
     * both, leaving exactly the symmetric difference.
     *
     * @param buf buffers whose lists are compacted in place
     */
    private static void cancelCommonInstances(ThreatDeltaBuffers buf) {
        sortUnique(buf.removes);
        sortUnique(buf.adds);
        int[] removes = buf.removes.array();
        int[] adds = buf.adds.array();
        int removeCount = buf.removes.size();
        int addCount = buf.adds.size();
        int r = 0;
        int a = 0;
        int rOut = 0;
        int aOut = 0;
        while (r < removeCount && a < addCount) {
            if (removes[r] == adds[a]) {
                r++;
                a++;
            } else if (removes[r] < adds[a]) {
                removes[rOut++] = removes[r++];
            } else {
                adds[aOut++] = adds[a++];
            }
        }
        while (r < removeCount) {
            removes[rOut++] = removes[r++];
        }
        while (a < addCount) {
            adds[aOut++] = adds[a++];
        }
        buf.removes.truncate(rOut);
        buf.adds.truncate(aOut);
    }

    /**
     * Sorts a packed-instance list and removes duplicates in place.
     *
     * @param list list to compact
     */
    private static void sortUnique(IntList list) {
        int size = list.size();
        if (size <= 1) {
            return;
        }
        int[] values = list.array();
        Arrays.sort(values, 0, size);
        int out = 1;
        for (int i = 1; i < size; i++) {
            if (values[i] != values[out - 1]) {
                values[out++] = values[i];
            }
        }
        list.truncate(out);
    }

    /**
     * Returns the FullThreats orientation selector for a king square. Threat
     * feature indices depend on the king square only through this value, so a
     * perspective's incremental threat accumulator stays valid across own-king
     * moves that do not change it.
     *
     * @param kingSquare king square in Stockfish order
     * @return orientation xor value (0 for files a-d, 7 for e-h)
     */
    static int threatOrientation(int kingSquare) {
        return FULL_THREATS_ORIENT[kingSquare];
    }

    /**
     * Maps one packed threat instance to a perspective's feature index.
     *
     * @param perspective perspective color
     * @param packed packed instance
     * @param kingSquare perspective king square (Stockfish order)
     * @param variant Stockfish NNUE variant
     * @return feature index, or the dimensions sentinel for excluded pairs
     */
    static int packedThreatIndex(int perspective, int packed, int kingSquare, UpstreamNetwork.Variant variant) {
        int from = packed & 63;
        int to = (packed >>> 6) & 63;
        int attacker = (packed >>> 12) & 15;
        int victim = (packed >>> 16) & 15;
        return threatIndex(perspective, attacker, from, to, victim, kingSquare, threatTables(variant));
    }

    /**
     * Returns the side to move as a Stockfish color id.
     *
     * @param position position to inspect
     * @return {@link #WHITE} or {@link #BLACK}
     */
    static int sideToMove(Position position) {
        return position.isWhiteToMove() ? WHITE : BLACK;
    }

    /**
     * Converts {@link Position}'s board order ({@code a8..h1}) to Stockfish square
     * order ({@code a1..h8}).
     *
     * @param positionIndex index in {@link Position}'s board
     * @return Stockfish square
     */
    static int squareFromPositionIndex(int positionIndex) {
        int rankFromTop = positionIndex >>> 3;
        int file = positionIndex & 7;
        return ((7 - rankFromTop) << 3) | file;
    }

    /**
     * Maps a CRTK piece code to Stockfish's piece code.
     *
     * @param piece CRTK piece
     * @return Stockfish piece
     */
    static int encodedPiece(byte piece) {
        if (piece == Piece.EMPTY) {
            return 0;
        }
        int type = Math.abs(piece);
        int color = piece > 0 ? WHITE : BLACK;
        return makePiece(color, type);
    }

    /**
     * Builds a Stockfish piece code.
     *
     * @param color color id
     * @param type piece type
     * @return Stockfish piece code
     */
    private static int makePiece(int color, int type) {
        return type + (color == BLACK ? 8 : 0);
    }

    /**
     * Returns a Stockfish piece's type.
     *
     * @param piece Stockfish piece
     * @return piece type
     */
    private static int typeOf(int piece) {
        return piece & 7;
    }

    /**
     * Returns a Stockfish piece's color.
     *
     * @param piece Stockfish piece
     * @return color id
     */
    private static int colorOf(int piece) {
        return piece >>> 3;
    }

    /**
     * Returns the king square for a perspective.
     *
     * @param board Stockfish-order board
     * @param color color id
     * @return king square
     */
    private static int kingSquare(int[] board, int color) {
        int king = makePiece(color, KING);
        for (int square = 0; square < SQUARE_NB; square++) {
            if (board[square] == king) {
                return square;
            }
        }
        throw new IllegalArgumentException("Position is missing a king.");
    }

    /**
     * Computes a HalfKAv2_hm feature index.
     *
     * @param perspective perspective color
     * @param square piece square
     * @param piece piece code
     * @param kingSquare perspective king square
     * @return feature index
     */
    private static int halfKaIndex(int perspective, int square, int piece, int kingSquare) {
        int flip = 56 * perspective;
        return (square ^ HALF_KA_ORIENT[kingSquare] ^ flip)
                + PIECE_SQUARE_INDEX[perspective][piece]
                + KING_BUCKETS[kingSquare ^ flip];
    }

    /**
     * Computes one public HalfKAv2_hm feature index.
     *
     * @param perspective perspective color
     * @param square piece square in Stockfish order
     * @param piece Stockfish piece code
     * @param kingSquare perspective king square in Stockfish order
     * @return feature index
     */
    static int halfKaFeatureIndex(int perspective, int square, int piece, int kingSquare) {
        return halfKaIndex(perspective, square, piece, kingSquare);
    }

    /**
     * Computes a FullThreats feature index.
     *
     * @param perspective perspective color
     * @param attacker attacker piece
     * @param from origin square
     * @param to target square
     * @param attacked attacked piece
     * @param kingSquare perspective king square
     * @param tables variant-specific tables
     * @return feature index, or dimensions sentinel
     */
    private static int threatIndex(
            int perspective,
            int attacker,
            int from,
            int to,
            int attacked,
            int kingSquare,
            ThreatTables tables) {
        int orientation = FULL_THREATS_ORIENT[kingSquare] ^ (56 * perspective);
        int fromOriented = from ^ orientation;
        int toOriented = to ^ orientation;
        int swap = 8 * perspective;
        int attackerOriented = attacker ^ swap;
        int attackedOriented = attacked ^ swap;

        return tables.indexLut1[attackerOriented][attackedOriented][fromOriented < toOriented ? 1 : 0]
                + tables.offsets[attackerOriented][fromOriented]
                + tables.indexLut2[attackerOriented][fromOriented][toOriented];
    }

    /**
     * Appends all active threats from one attacker square.
     *
     * @param board Stockfish-order board
     * @param perspective perspective color
     * @param attacker attacker piece
     * @param from attacker square
     * @param kingSquare perspective king square
     * @param tables variant-specific tables
     * @param active output list
     * @param ctx search context
     */
    private static void appendThreatsFrom(
            int[] board,
            ThreatContext ctx) {
        int type = typeOf(ctx.attacker);
        if (type == PAWN) {
            appendPawnThreats(board, ctx);
            return;
        }
        if (type == KNIGHT || type == KING) {
            int[] targets = type == KNIGHT ? KNIGHT_TARGETS[ctx.from] : KING_TARGETS[ctx.from];
            for (int to : targets) {
                appendThreatIfOccupied(board, ctx, to);
            }
            return;
        }

        int[][] directions = switch (type) {
        case BISHOP -> BISHOP_DIRECTIONS;
        case ROOK -> ROOK_DIRECTIONS;
        case QUEEN -> QUEEN_DIRECTIONS;
        default -> throw new IllegalArgumentException("Unsupported attacker type: " + type);
        };
        for (int[] direction : directions) {
            int file = file(ctx.from) + direction[0];
            int rank = rank(ctx.from) + direction[1];
            while (onBoard(file, rank)) {
                int to = square(file, rank);
                if (board[to] != 0) {
                    appendThreat(ctx, to, board[to]);
                    break;
                }
                file += direction[0];
                rank += direction[1];
            }
        }
    }

    /**
     * Appends pawn capture and blocked-pawn threats.
     *
     * @param board Stockfish-order board
     * @param ctx threat-emission context
     */
    private static void appendPawnThreats(
            int[] board,
            ThreatContext ctx) {
        int color = colorOf(ctx.attacker);
        int forward = color == WHITE ? 1 : -1;
        int nextRank = rank(ctx.from) + forward;
        if (nextRank >= 0 && nextRank < 8) {
            int leftFile = file(ctx.from) - 1;
            int rightFile = file(ctx.from) + 1;
            if (leftFile >= 0) {
                int to = square(leftFile, nextRank);
                appendThreatIfOccupied(board, ctx, to);
            }
            if (rightFile < 8) {
                int to = square(rightFile, nextRank);
                appendThreatIfOccupied(board, ctx, to);
            }

            int pushTo = square(file(ctx.from), nextRank);
            if (typeOf(board[pushTo]) == PAWN) {
                appendThreat(ctx, pushTo, board[pushTo]);
            }
        }
    }

    /**
     * Appends a threat only when the target square is occupied.
     *
     * @param board Stockfish-order board
     * @param ctx threat-emission context
     * @param to target square
     */
    private static void appendThreatIfOccupied(
            int[] board,
            ThreatContext ctx,
            int to) {
        int attacked = board[to];
        if (attacked != 0) {
            appendThreat(ctx, to, attacked);
        }
    }

    /**
     * Appends one threat if it is valid for the variant.
     *
     * @param ctx threat-emission context
     * @param to target square
     * @param attacked attacked piece
     */
    private static void appendThreat(ThreatContext ctx, int to, int attacked) {
        int index = threatIndex(ctx.perspective, ctx.attacker, ctx.from, to, attacked, ctx.kingSquare, ctx.tables);
        if (index < ctx.tables.dimensions) {
            ctx.active.add(index);
        }
    }

    /**
     * Returns variant-specific threat tables.
     *
     * @param variant variant
     * @return tables
     */
    private static ThreatTables threatTables(UpstreamNetwork.Variant variant) {
        return variant == UpstreamNetwork.Variant.SF_18 ? SF18_THREAT_TABLES : CURRENT_THREAT_TABLES;
    }

    /**
     * Builds the HalfKAv2_hm piece-square offset table.
     *
     * @return table
     */
    private static int[][] buildPieceSquareIndex() {
        int[][] out = new int[2][PIECE_NB];
        int psWPawn = 0;
        int psBPawn = 64;
        int psWKnight = 2 * 64;
        int psBKnight = 3 * 64;
        int psWBishop = 4 * 64;
        int psBBishop = 5 * 64;
        int psWRook = 6 * 64;
        int psBRook = 7 * 64;
        int psWQueen = 8 * 64;
        int psBQueen = 9 * 64;
        int psKing = 10 * 64;

        out[WHITE][makePiece(WHITE, PAWN)] = psWPawn;
        out[WHITE][makePiece(WHITE, KNIGHT)] = psWKnight;
        out[WHITE][makePiece(WHITE, BISHOP)] = psWBishop;
        out[WHITE][makePiece(WHITE, ROOK)] = psWRook;
        out[WHITE][makePiece(WHITE, QUEEN)] = psWQueen;
        out[WHITE][makePiece(WHITE, KING)] = psKing;
        out[WHITE][makePiece(BLACK, PAWN)] = psBPawn;
        out[WHITE][makePiece(BLACK, KNIGHT)] = psBKnight;
        out[WHITE][makePiece(BLACK, BISHOP)] = psBBishop;
        out[WHITE][makePiece(BLACK, ROOK)] = psBRook;
        out[WHITE][makePiece(BLACK, QUEEN)] = psBQueen;
        out[WHITE][makePiece(BLACK, KING)] = psKing;

        out[BLACK][makePiece(WHITE, PAWN)] = psBPawn;
        out[BLACK][makePiece(WHITE, KNIGHT)] = psBKnight;
        out[BLACK][makePiece(WHITE, BISHOP)] = psBBishop;
        out[BLACK][makePiece(WHITE, ROOK)] = psBRook;
        out[BLACK][makePiece(WHITE, QUEEN)] = psBQueen;
        out[BLACK][makePiece(WHITE, KING)] = psKing;
        out[BLACK][makePiece(BLACK, PAWN)] = psWPawn;
        out[BLACK][makePiece(BLACK, KNIGHT)] = psWKnight;
        out[BLACK][makePiece(BLACK, BISHOP)] = psWBishop;
        out[BLACK][makePiece(BLACK, ROOK)] = psWRook;
        out[BLACK][makePiece(BLACK, QUEEN)] = psWQueen;
        out[BLACK][makePiece(BLACK, KING)] = psKing;
        return out;
    }

    /**
     * Builds the HalfKAv2_hm king bucket table.
     *
     * @return bucket offsets
     */
    private static int[] buildKingBuckets() {
        int[] out = new int[SQUARE_NB];
        for (int rank = 0; rank < 8; rank++) {
            int rankFromTop = 7 - rank;
            for (int file = 0; file < 8; file++) {
                int mirroredFileBucket = file < 4 ? file : 7 - file;
                out[square(file, rank)] = ((rankFromTop * 4) + mirroredFileBucket) * 11 * 64;
            }
        }
        return out;
    }

    /**
     * Builds an orientation table that selects one xor value for files a-d and
     * another for files e-h.
     *
     * @param leftValue value for files a-d
     * @param rightValue value for files e-h
     * @return orientation table
     */
    private static int[] buildOrientTable(int leftValue, int rightValue) {
        int[] out = new int[SQUARE_NB];
        for (int square = 0; square < SQUARE_NB; square++) {
            out[square] = file(square) < 4 ? leftValue : rightValue;
        }
        return out;
    }

    /**
     * Precomputes color-independent leaper pseudo targets for every square.
     *
     * @param pieceType {@link #KNIGHT} or {@link #KING}
     * @return per-square sorted target squares, identical to
     *         {@link #pseudoTargets}
     */
    private static int[][] buildLeaperTargets(int pieceType) {
        int[][] out = new int[SQUARE_NB][];
        for (int from = 0; from < SQUARE_NB; from++) {
            out[from] = pseudoTargets(pieceType, WHITE, from);
        }
        return out;
    }

    /**
     * Returns pseudo targets used by Stockfish's threat-index tables.
     *
     * @param pieceType piece type
     * @param color piece color
     * @param from origin square
     * @return sorted target squares
     */
    private static int[] pseudoTargets(int pieceType, int color, int from) {
        IntList targets = new IntList(16);
        switch (pieceType) {
        case PAWN -> appendPawnPushOrAttacks(color, from, targets);
        case KNIGHT -> appendLeaper(from, KNIGHT_DELTAS, targets);
        case BISHOP -> appendRayTargets(from, BISHOP_DIRECTIONS, targets);
        case ROOK -> appendRayTargets(from, ROOK_DIRECTIONS, targets);
        case QUEEN -> appendRayTargets(from, QUEEN_DIRECTIONS, targets);
        case KING -> appendLeaper(from, KING_DELTAS, targets);
        default -> {
            // no targets
        }
        }
        int[] out = targets.toArray();
        Arrays.sort(out);
        return out;
    }

    /**
     * Appends pawn push and attack squares.
     *
     * @param color pawn color
     * @param from pawn square
     * @param out target list
     */
    private static void appendPawnPushOrAttacks(int color, int from, IntList out) {
        int nextRank = rank(from) + (color == WHITE ? 1 : -1);
        if (nextRank < 0 || nextRank >= 8) {
            return;
        }
        out.add(square(file(from), nextRank));
        if (file(from) > 0) {
            out.add(square(file(from) - 1, nextRank));
        }
        if (file(from) < 7) {
            out.add(square(file(from) + 1, nextRank));
        }
    }

    /**
     * Appends leaper targets.
     *
     * @param from origin square
     * @param deltas file/rank deltas
     * @param out target list
     */
    private static void appendLeaper(int from, int[][] deltas, IntList out) {
        for (int[] delta : deltas) {
            int file = file(from) + delta[0];
            int rank = rank(from) + delta[1];
            if (onBoard(file, rank)) {
                out.add(square(file, rank));
            }
        }
    }

    /**
     * Appends empty-board sliding targets.
     *
     * @param from origin square
     * @param directions ray directions
     * @param out target list
     */
    private static void appendRayTargets(int from, int[][] directions, IntList out) {
        for (int[] direction : directions) {
            int file = file(from) + direction[0];
            int rank = rank(from) + direction[1];
            while (onBoard(file, rank)) {
                out.add(square(file, rank));
                file += direction[0];
                rank += direction[1];
            }
        }
    }

    /**
     * Returns a square's file.
     *
     * @param square square
     * @return file 0..7
     */
    private static int file(int square) {
        return square & 7;
    }

    /**
     * Returns a square's rank.
     *
     * @param square square
     * @return rank 0..7
     */
    private static int rank(int square) {
        return square >>> 3;
    }

    /**
     * Builds a square from file/rank.
     *
     * @param file file
     * @param rank rank
     * @return square
     */
    private static int square(int file, int rank) {
        return rank * 8 + file;
    }

    /**
     * Returns whether coordinates are on the board.
     *
     * @param file file
     * @param rank rank
     * @return true when valid
     */
    private static boolean onBoard(int file, int rank) {
        return file >= 0 && file < 8 && rank >= 0 && rank < 8;
    }

    /**
     * Variant-specific FullThreats lookup data.
     */
    private static final class ThreatTables {

        /**
         * Feature dimension count.
         */
        final int dimensions;

        /**
         * Valid target counts by Stockfish piece code.
         */
        final int[] numValidTargets;

        /**
         * Attacker/attacked type mapping.
         */
        final int[][] map;

        /**
         * Helper offsets by piece code.
         */
        final HelperOffset[] helperOffsets;

        /**
         * Per-piece per-from offsets.
         */
        final int[][] offsets;

        /**
         * Base-feature lookup indexed by attacker, attacked, from-to ordering.
         */
        final int[][][] indexLut1;

        /**
         * Target-order lookup indexed by attacker, from, to.
         */
        final int[][][] indexLut2;

        /**
         * Creates tables.
         *
         * @param dimensions feature dimension count
         * @param numValidTargets valid target counts
         * @param map attacker/attacked mapping
         */
        ThreatTables(int dimensions, int[] numValidTargets, int[][] map) {
            this.dimensions = dimensions;
            this.numValidTargets = numValidTargets;
            this.map = map;
            this.helperOffsets = new HelperOffset[PIECE_NB];
            this.offsets = new int[PIECE_NB][SQUARE_NB];
            this.indexLut2 = new int[PIECE_NB][SQUARE_NB][SQUARE_NB];
            initThreatOffsets();
            this.indexLut1 = initIndexLut1();
        }

        /**
         * Initializes helper offsets and target-order lookup data.
         */
        private void initThreatOffsets() {
            int cumulativeOffset = 0;
            for (int piece : ALL_PIECES) {
                int cumulativePieceOffset = 0;
                for (int from = 0; from < SQUARE_NB; from++) {
                    offsets[piece][from] = cumulativePieceOffset;
                    int[] targets = pseudoTargets(typeOf(piece), colorOf(piece), from);
                    for (int order = 0; order < targets.length; order++) {
                        indexLut2[piece][from][targets[order]] = order;
                    }
                    if (typeOf(piece) != PAWN || (from >= 8 && from <= 55)) {
                        cumulativePieceOffset += targets.length;
                    }
                }
                helperOffsets[piece] = new HelperOffset(cumulativePieceOffset, cumulativeOffset);
                cumulativeOffset += numValidTargets[piece] * cumulativePieceOffset;
            }
        }

        /**
         * Builds base-feature lookup data.
         *
         * @return lookup table
         */
        private int[][][] initIndexLut1() {
            int[][][] indices = new int[PIECE_NB][PIECE_NB][2];
            fillMissingThreatIndices(indices);
            for (int attacker : ALL_PIECES) {
                for (int attacked : ALL_PIECES) {
                    populateIndexLut1(indices, attacker, attacked);
                }
            }
            return indices;
        }

        /**
         * Fills the lookup table with the default out-of-range marker.
         *
         * @param indices lookup table under construction
         */
        private void fillMissingThreatIndices(int[][][] indices) {
            for (int[][] byAttacked : indices) {
                for (int[] order : byAttacked) {
                    Arrays.fill(order, dimensions);
                }
            }
        }

        /**
         * Populates one attacker/attacked entry in the lookup table.
         *
         * @param indices lookup table under construction
         * @param attacker attacking piece
         * @param attacked attacked piece
         */
        private void populateIndexLut1(int[][][] indices, int attacker, int attacked) {
            int attackerType = typeOf(attacker);
            int attackedType = typeOf(attacked);
            int mapped = map[attackerType - 1][attackedType - 1];
            if (mapped < 0) {
                return;
            }
            int feature = helperOffsets[attacker].cumulativeOffset
                    + (colorOf(attacked) * (numValidTargets[attacker] / 2) + mapped)
                            * helperOffsets[attacker].cumulativePieceOffset;
            indices[attacker][attacked][0] = feature;
            if (!isSemiExcluded(attacker, attacked, attackerType)) {
                indices[attacker][attacked][1] = feature;
            }
        }

        /**
         * Returns whether the ordered-target feature should be suppressed.
         *
         * @param attacker attacking piece
         * @param attacked attacked piece
         * @param attackerType normalized attacker type
         * @return true when the ordered entry should remain excluded
         */
        private boolean isSemiExcluded(int attacker, int attacked, int attackerType) {
            boolean enemy = (attacker ^ attacked) == 8;
            int attackedType = typeOf(attacked);
            return attackerType == attackedType && (enemy || attackerType != PAWN);
        }
    }

    /**
     * Shared context for threat feature emission from one attacker square.
     */
    private static final class ThreatContext {

        /**
         * Perspective color.
         */
        private final int perspective;

        /**
         * Attacking piece. Mutable so one context can be reused across the
         * attacker scan of a single {@link #collectThreats} call.
         */
        private int attacker;

        /**
         * Attacker square. Mutable for the same reuse reason as
         * {@link #attacker}.
         */
        private int from;

        /**
         * Perspective king square.
         */
        private final int kingSquare;

        /**
         * Variant-specific tables.
         */
        private final ThreatTables tables;

        /**
         * Output feature list.
         */
        private final IntList active;

        /**
         * Creates one threat-emission context.
         *
         * @param perspective perspective color
         * @param attacker attacking piece
         * @param from attacker square
         * @param kingSquare perspective king square
         * @param tables variant-specific tables
         * @param active output feature list
         */
        private ThreatContext(
                int perspective,
                int attacker,
                int from,
                int kingSquare,
                ThreatTables tables,
                IntList active) {
            this.perspective = perspective;
            this.attacker = attacker;
            this.from = from;
            this.kingSquare = kingSquare;
            this.tables = tables;
            this.active = active;
        }
    }

    /**
     * Helper offsets for FullThreats.
     */
    private record HelperOffset(
        /**
         * Stores the cumulative piece offset.
         */
        int cumulativePieceOffset,
        /**
         * Stores the cumulative global offset.
         */
        int cumulativeOffset
    ) {
    }

    /**
     * Small growable int list, reusable across calls via {@link #clear()}.
     */
    static final class IntList {

        /**
         * Backing array.
         */
        private int[] values;

        /**
         * Number of values.
         */
        private int size;

        /**
         * Creates a list.
         *
         * @param capacity initial capacity
         */
        IntList(int capacity) {
            values = new int[Math.max(1, capacity)];
        }

        /**
         * Adds a value.
         *
         * @param value value to add
         */
        void add(int value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = value;
        }

        /**
         * Resets the list to empty without releasing the backing array.
         */
        void clear() {
            size = 0;
        }

        /**
         * Shrinks the list to a smaller size, keeping the backing array.
         *
         * @param newSize new size, not larger than the current size
         */
        void truncate(int newSize) {
            size = newSize;
        }

        /**
         * Returns the number of stored values.
         *
         * @return value count
         */
        int size() {
            return size;
        }

        /**
         * Returns the backing array; only the first {@link #size()} entries are
         * valid.
         *
         * @return backing array
         */
        int[] array() {
            return values;
        }

        /**
         * Returns a compact array copy.
         *
         * @return values
         */
        int[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
