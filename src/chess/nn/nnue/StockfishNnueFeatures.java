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
final class StockfishNnueFeatures {

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
     * Prevents instantiation.
     */
    private StockfishNnueFeatures() {
        // utility
    }

    /**
     * Returns the FullThreats dimension count for a variant.
     *
     * @param variant Stockfish NNUE variant
     * @return feature dimensions
     */
    static int threatDimensions(StockfishNnueNetwork.Variant variant) {
        return threatTables(variant).dimensions;
    }

    /**
     * Converts a CRTK position into Stockfish piece codes in Stockfish square order.
     *
     * @param position source position
     * @return 64-entry board
     */
    static int[] board(Position position) {
        byte[] source = position.getBoard();
        int[] out = new int[SQUARE_NB];
        for (int index = 0; index < source.length; index++) {
            int square = squareFromPositionIndex(index);
            out[square] = stockfishPiece(source[index]);
        }
        return out;
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
    static int[] activeThreats(int[] board, int perspective, StockfishNnueNetwork.Variant variant) {
        ThreatTables tables = threatTables(variant);
        int kingSquare = kingSquare(board, perspective);
        IntList active = new IntList(128);

        for (int colorSelector = WHITE; colorSelector <= BLACK; colorSelector++) {
            int color = perspective ^ colorSelector;
            for (int pieceType = PAWN; pieceType < KING; pieceType++) {
                int attacker = makePiece(color, pieceType);
                for (int from = 0; from < SQUARE_NB; from++) {
                    if (board[from] != attacker) {
                        continue;
                    }
                    appendThreatsFrom(board, perspective, attacker, from, kingSquare, tables, active);
                }
            }
        }

        return active.toArray();
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
    private static int stockfishPiece(byte piece) {
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
     */
    private static void appendThreatsFrom(
            int[] board,
            int perspective,
            int attacker,
            int from,
            int kingSquare,
            ThreatTables tables,
            IntList active) {
        int type = typeOf(attacker);
        if (type == PAWN) {
            appendPawnThreats(board, perspective, attacker, from, kingSquare, tables, active);
            return;
        }
        if (type == KNIGHT || type == KING) {
            for (int to : pseudoTargets(type, colorOf(attacker), from)) {
                appendThreatIfOccupied(board, perspective, attacker, from, to, kingSquare, tables, active);
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
            int file = file(from) + direction[0];
            int rank = rank(from) + direction[1];
            while (onBoard(file, rank)) {
                int to = square(file, rank);
                if (board[to] != 0) {
                    appendThreat(board, perspective, attacker, from, to, board[to], kingSquare, tables, active);
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
     * @param perspective perspective color
     * @param attacker attacker pawn
     * @param from pawn square
     * @param kingSquare perspective king square
     * @param tables variant-specific tables
     * @param active output list
     */
    private static void appendPawnThreats(
            int[] board,
            int perspective,
            int attacker,
            int from,
            int kingSquare,
            ThreatTables tables,
            IntList active) {
        int color = colorOf(attacker);
        int forward = color == WHITE ? 1 : -1;
        int nextRank = rank(from) + forward;
        if (nextRank >= 0 && nextRank < 8) {
            int leftFile = file(from) - 1;
            int rightFile = file(from) + 1;
            if (leftFile >= 0) {
                int to = square(leftFile, nextRank);
                appendThreatIfOccupied(board, perspective, attacker, from, to, kingSquare, tables, active);
            }
            if (rightFile < 8) {
                int to = square(rightFile, nextRank);
                appendThreatIfOccupied(board, perspective, attacker, from, to, kingSquare, tables, active);
            }

            int pushTo = square(file(from), nextRank);
            if (typeOf(board[pushTo]) == PAWN) {
                appendThreat(board, perspective, attacker, from, pushTo, board[pushTo], kingSquare, tables, active);
            }
        }
    }

    /**
     * Appends a threat only when the target square is occupied.
     *
     * @param board Stockfish-order board
     * @param perspective perspective color
     * @param attacker attacker piece
     * @param from attacker square
     * @param to target square
     * @param kingSquare perspective king square
     * @param tables variant-specific tables
     * @param active output list
     */
    private static void appendThreatIfOccupied(
            int[] board,
            int perspective,
            int attacker,
            int from,
            int to,
            int kingSquare,
            ThreatTables tables,
            IntList active) {
        int attacked = board[to];
        if (attacked != 0) {
            appendThreat(board, perspective, attacker, from, to, attacked, kingSquare, tables, active);
        }
    }

    /**
     * Appends one threat if it is valid for the variant.
     *
     * @param board unused board context
     * @param perspective perspective color
     * @param attacker attacker piece
     * @param from attacker square
     * @param to target square
     * @param attacked attacked piece
     * @param kingSquare perspective king square
     * @param tables variant-specific tables
     * @param active output list
     */
    private static void appendThreat(
            int[] board,
            int perspective,
            int attacker,
            int from,
            int to,
            int attacked,
            int kingSquare,
            ThreatTables tables,
            IntList active) {
        int index = threatIndex(perspective, attacker, from, to, attacked, kingSquare, tables);
        if (index < tables.dimensions) {
            active.add(index);
        }
    }

    /**
     * Returns variant-specific threat tables.
     *
     * @param variant variant
     * @return tables
     */
    private static ThreatTables threatTables(StockfishNnueNetwork.Variant variant) {
        return variant == StockfishNnueNetwork.Variant.SF_18 ? SF18_THREAT_TABLES : CURRENT_THREAT_TABLES;
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
            for (int[][] byAttacked : indices) {
                for (int[] order : byAttacked) {
                    Arrays.fill(order, dimensions);
                }
            }

            for (int attacker : ALL_PIECES) {
                for (int attacked : ALL_PIECES) {
                    boolean enemy = (attacker ^ attacked) == 8;
                    int attackerType = typeOf(attacker);
                    int attackedType = typeOf(attacked);
                    int mapped = map[attackerType - 1][attackedType - 1];
                    boolean excluded = mapped < 0;
                    boolean semiExcluded = attackerType == attackedType && (enemy || attackerType != PAWN);
                    int feature = dimensions;
                    if (!excluded) {
                        feature = helperOffsets[attacker].cumulativeOffset
                                + (colorOf(attacked) * (numValidTargets[attacker] / 2) + mapped)
                                        * helperOffsets[attacker].cumulativePieceOffset;
                    }

                    indices[attacker][attacked][0] = excluded ? dimensions : feature;
                    indices[attacker][attacked][1] = excluded || semiExcluded ? dimensions : feature;
                }
            }
            return indices;
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
     * Small growable int list.
     */
    private static final class IntList {

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
         * Returns a compact array copy.
         *
         * @return values
         */
        int[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
