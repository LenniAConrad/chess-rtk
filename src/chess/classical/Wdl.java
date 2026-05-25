package chess.classical;

import chess.core.Bits;
import chess.core.Field;
import chess.core.MoveGenerator;
import chess.core.Piece;
import chess.core.Position;
import utility.Numbers;

/**
 * Heuristic win/draw/loss (WDL) evaluator for {@link Position}.
 *
 * <p>
 * This record represents a WDL triplet, scaled to {@link #TOTAL} and expressed
 * from the side-to-move perspective. The evaluator converts a position into
 * these probabilities using a fast material-plus-PST centipawn estimate, then
 * maps that score through a symmetric sigmoid with a draw margin. It optionally
 * checks for terminal states and handles a few conservative insufficient-material
 * cases, but it is not a full engine.
 * </p>
 *
 * @param win win probability for the side to move, scaled 0..1000
 * @param draw draw probability for the side to move, scaled 0..1000
 * @param loss loss probability for the side to move, scaled 0..1000
 *
 * @since 2025
 * @author Lennart A. Conrad
 */

public record Wdl(
    /**
     * Stores the win.
     */
    short win,
    /**
     * Stores the draw.
     */
    short draw,
    /**
     * Stores the loss.
     */
    short loss
) {

    /**
     * Total sum for the WDL triplet.
     *
     * <p>
     * This matches the common UCI "wdl" scaling where values sum to 1000.
     * </p>
     */
    public static final short TOTAL = 1000;

    /**
     * Width of the central band that favors draws.
     *
     * <p>
     * Units: centipawns (from the side-to-move perspective). Values close to zero
     * bias probability mass towards draws.
     * </p>
     */
    private static final int DRAW_MARGIN_CP = 200;

    /**
     * Logistic scale used to turn centipawns into probabilities.
     *
     * <p>
     * Larger values make probabilities change more slowly with centipawns.
     * Units: centipawns.
     * </p>
     */
    private static final double SCALE_CP = 170.0;

    /**
     * Extra draw mass in low-material endgames (added by scaling W/L down).
     *
     * <p>
     * This is applied after the centipawn→probability mapping and increases as
     * material leaves the board.
     * </p>
     */
    private static final double ENDGAME_DRAW_BONUS = 0.12;

    /**
     * Small tempo term (from White perspective).
     *
     * <p>
     * This makes the evaluator slightly prefer the side to move in quiet positions.
     * Units: centipawns.
     * </p>
     */
    private static final int TEMPO_CP = 8;

    /**
     * Penalty for being in check (applied to the side in check).
     *
     * <p>
     * Units: centipawns.
     * </p>
     */
    private static final int IN_CHECK_CP = 35;

    /**
     * Bishop pair bonus.
     *
     * <p>
     * Units: centipawns (applied from White perspective).
     * </p>
     */
    private static final int BISHOP_PAIR_CP = 30;

    /**
     * Attack-table slot containing all piece attacks for one side.
     */
    static final int ALL_ATTACKS = 0;

    /**
     * Attack-table side index for White.
     */
    static final int WHITE = 0;

    /**
     * Attack-table side index for Black.
     */
    static final int BLACK = 1;

    /**
     * Knight midgame mobility score by reachable safe targets.
     */
    private static final int[] KNIGHT_MOBILITY_CP = { -18, -12, -6, -2, 3, 7, 10, 13, 15 };

    /**
     * Knight endgame mobility score by reachable safe targets.
     */
    private static final int[] KNIGHT_MOBILITY_EG_CP = { -24, -16, -8, -2, 4, 9, 13, 16, 18 };

    /**
     * Bishop midgame mobility score by reachable safe targets.
     */
    private static final int[] BISHOP_MOBILITY_CP = { -14, -8, -2, 4, 9, 14, 18, 22, 25, 28, 30, 32, 34, 35 };

    /**
     * Bishop endgame mobility score by reachable safe targets.
     */
    private static final int[] BISHOP_MOBILITY_EG_CP = { -18, -10, -2, 5, 11, 17, 23, 28, 32, 35, 38, 40, 42, 44 };

    /**
     * Rook midgame mobility score by reachable safe targets.
     */
    private static final int[] ROOK_MOBILITY_CP = { -12, -7, -2, 2, 6, 10, 14, 17, 20, 22, 24, 26, 28, 30, 31 };

    /**
     * Rook endgame mobility score by reachable safe targets.
     */
    private static final int[] ROOK_MOBILITY_EG_CP = { -18, -9, -1, 7, 15, 23, 30, 36, 41, 45, 49, 52, 55, 57, 59 };

    /**
     * Queen midgame mobility score by reachable safe targets.
     */
    private static final int[] QUEEN_MOBILITY_CP = {
            -8, -5, -2, 0, 3, 6, 8, 10, 12, 14, 16, 18, 19, 20, 21, 22,
            23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34
    };

    /**
     * Queen endgame mobility score by reachable safe targets.
     */
    private static final int[] QUEEN_MOBILITY_EG_CP = {
            -12, -8, -4, 0, 5, 10, 14, 18, 22, 26, 30, 34, 37, 40, 43, 46,
            49, 52, 55, 58, 61, 64, 66, 68, 70, 72, 74, 76
    };

    /**
     * Midgame weight assigned to attackers of the enemy king zone.
     */
    static final int[] KING_ATTACK_WEIGHT = { 0, 0, 11, 9, 13, 18, 0 };

    /**
     * Center four files, used for space and flank scoring.
     */
    private static final long CENTER_FILES = Bits.FILE_C | Bits.FILE_D | Bits.FILE_E | Bits.FILE_F;

    /**
     * Central four squares.
     */
    static final long CENTER_SQUARES = Bits.bit(Field.D4) | Bits.bit(Field.E4)
            | Bits.bit(Field.D5) | Bits.bit(Field.E5);

    /**
     * White-side space mask, following the classical engine convention of
     * counting safe central squares behind the pawn front.
     */
    private static final long WHITE_SPACE_MASK = CENTER_FILES & (Bits.RANK_2 | Bits.RANK_3 | Bits.RANK_4);

    /**
     * Black-side space mask.
     */
    private static final long BLACK_SPACE_MASK = CENTER_FILES & (Bits.RANK_7 | Bits.RANK_6 | Bits.RANK_5);

    /**
     * Knight midgame outpost bonus.
     */
    static final int KNIGHT_OUTPOST_CP = 22;

    /**
     * Bishop midgame outpost bonus.
     */
    static final int BISHOP_OUTPOST_CP = 12;

    /**
     * Bitboard mask for one square-color complex.
     */
    private static final long LIGHT_SQUARES = squareColorMask(0);

    /**
     * Bitboard mask for the opposite square-color complex.
     */
    private static final long DARK_SQUARES = ~LIGHT_SQUARES;

    /**
     * Total starting material in centipawns (both sides, kings excluded).
     *
     * <p>
     * Used to compute a simple phase factor:
     * {@code phase = totalMaterial / START_TOTAL_MATERIAL_CP} clamped to [0..1],
     * where 1 means "opening-like" and 0 means "endgame-like".
     * </p>
     */
    private static final int START_TOTAL_MATERIAL_CP = 2 * (8 * Piece.VALUE_PAWN + 2 * Piece.VALUE_KNIGHT
            + 2 * Piece.VALUE_BISHOP + 2 * Piece.VALUE_ROOK + Piece.VALUE_QUEEN);

    /**
     * Per-thread scratch buffers to avoid allocations in hot paths.
     *
     * <p>
     * This makes {@link #evaluate(Position)} and related methods safe to call from
     * multiple threads without sharing mutable arrays.
     * </p>
     */
    private static final ThreadLocal<EvalBuffers> BUFFERS = ThreadLocal.withInitial(EvalBuffers::new);

    /**
     * Compact pawn piece-square table (PST), from White's perspective.
     *
     * <p>
     * Indexing matches the internal board: {@code 0 == A8} ... {@code 63 == H1}.
     * For Black pieces, squares are flipped vertically via {@link #flip(int)}.
     * Values are deliberately small because the WDL mapping is coarse and we want
     * stability.
     * Units: centipawns.
     * </p>
     */
    private static final int[] PAWN_PST = {
            0, 0, 0, 0, 0, 0, 0, 0,
            10, 12, 12, 14, 14, 12, 12, 10,
            8, 10, 12, 16, 16, 12, 10, 8,
            6, 8, 10, 14, 14, 10, 8, 6,
            4, 6, 8, 12, 12, 8, 6, 4,
            2, 4, 6, 8, 8, 6, 4, 2,
            0, 0, 0, -6, -6, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0
    };

    /**
     * Compact knight piece-square table (PST), from White's perspective.
     *
     * <p>
     * Units: centipawns.
     * </p>
     */
    private static final int[] KNIGHT_PST = {
            -40, -25, -15, -10, -10, -15, -25, -40,
            -25, -10, 0, 5, 5, 0, -10, -25,
            -15, 0, 10, 15, 15, 10, 0, -15,
            -10, 5, 15, 20, 20, 15, 5, -10,
            -10, 5, 15, 20, 20, 15, 5, -10,
            -15, 0, 10, 15, 15, 10, 0, -15,
            -25, -10, 0, 5, 5, 0, -10, -25,
            -40, -25, -15, -10, -10, -15, -25, -40
    };

    /**
     * Compact bishop piece-square table (PST), from White's perspective.
     *
     * <p>
     * Units: centipawns.
     * </p>
     */
    private static final int[] BISHOP_PST = {
            -15, -10, -10, -10, -10, -10, -10, -15,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 8, 8, 5, 0, -10,
            -10, 3, 8, 12, 12, 8, 3, -10,
            -10, 3, 8, 12, 12, 8, 3, -10,
            -10, 0, 5, 8, 8, 5, 0, -10,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -15, -10, -10, -10, -10, -10, -10, -15
    };

    /**
     * Compact rook piece-square table (PST), from White's perspective.
     *
     * <p>
     * Units: centipawns.
     * </p>
     */
    private static final int[] ROOK_PST = {
            5, 5, 5, 8, 8, 5, 5, 5,
            0, 0, 0, 4, 4, 0, 0, 0,
            -4, -4, -2, 0, 0, -2, -4, -4,
            -6, -6, -4, -2, -2, -4, -6, -6,
            -6, -6, -4, -2, -2, -4, -6, -6,
            -4, -4, -2, 0, 0, -2, -4, -4,
            0, 0, 0, 4, 4, 0, 0, 0,
            5, 5, 5, 8, 8, 5, 5, 5
    };

    /**
     * Compact queen piece-square table (PST), from White's perspective.
     *
     * <p>
     * Units: centipawns.
     * </p>
     */
    private static final int[] QUEEN_PST = {
            -10, -8, -6, -4, -4, -6, -8, -10,
            -8, -4, -2, -1, -1, -2, -4, -8,
            -6, -2, 0, 1, 1, 0, -2, -6,
            -4, -1, 1, 2, 2, 1, -1, -4,
            -4, -1, 1, 2, 2, 1, -1, -4,
            -6, -2, 0, 1, 1, 0, -2, -6,
            -8, -4, -2, -1, -1, -2, -4, -8,
            -10, -8, -6, -4, -4, -6, -8, -10
    };

    /**
     * King table for opening/middlegame (safety and castling incentives).
     *
     * <p>
     * Units: centipawns, from White's perspective. Blended with
     * {@link #KING_PST_ENDGAME} using the phase factor.
     * </p>
     */
    private static final int[] KING_PST_OPENING = {
            20, 25, 10, 0, 0, 10, 25, 20,
            10, 10, 0, -8, -8, 0, 10, 10,
            0, 0, -10, -15, -15, -10, 0, 0,
            -10, -10, -15, -20, -20, -15, -10, -10,
            -15, -15, -20, -25, -25, -20, -15, -15,
            -20, -20, -25, -30, -30, -25, -20, -20,
            -25, -25, -30, -35, -35, -30, -25, -25,
            -30, -30, -35, -40, -40, -35, -30, -30
    };

    /**
     * King table for endgames (activity and centralization).
     *
     * <p>
     * Units: centipawns, from White's perspective. Blended with
     * {@link #KING_PST_OPENING} using the phase factor.
     * </p>
     */
    private static final int[] KING_PST_ENDGAME = {
            -10, -5, 0, 5, 5, 0, -5, -10,
            -5, 0, 5, 10, 10, 5, 0, -5,
            0, 5, 10, 15, 15, 10, 5, 0,
            5, 10, 15, 20, 20, 15, 10, 5,
            5, 10, 15, 20, 20, 15, 10, 5,
            0, 5, 10, 15, 15, 10, 5, 0,
            -5, 0, 5, 10, 10, 5, 0, -5,
            -10, -5, 0, 5, 5, 0, -5, -10
    };

    /**
     * Enforces the record invariants for WDL probabilities.
     *
     * <p>All values must be non-negative and their sum has to equal {@link #TOTAL}.</p>
     *
     * @param win win probability scaled to {@link #TOTAL}
     * @param draw draw probability scaled to {@link #TOTAL}
     * @param loss loss probability scaled to {@link #TOTAL}
     * @throws IllegalArgumentException if any value is negative or the sum is not {@link #TOTAL}
     */
    public Wdl {
        if (win < 0 || draw < 0 || loss < 0 || (win + draw + loss) != TOTAL) {
            throw new IllegalArgumentException("win/draw/loss must be non-negative and sum to " + TOTAL);
        }
    }

    /**
     * Evaluate a position into a WDL triplet from the side-to-move perspective.
     *
     * <p>
     * This is the fast path: it does not attempt to detect terminal states via
     * move generation. It is therefore suitable for bulk processing.
     * </p>
     *
     * @param pos position to evaluate (non-null)
     * @return WDL triplet from the side-to-move perspective, summing to {@code 1000}
     * @throws IllegalArgumentException if {@code pos} is {@code null}
     */
    public static Wdl evaluate(Position pos) {
        return evaluate(pos, false);
    }

    /**
     * Evaluate a position into a WDL triplet.
     *
     * @param pos            position to evaluate (non-null)
     * @param terminalAware  if true, detects checkmate/stalemate via move generation
     * @return WDL triplet from the side-to-move perspective, summing to {@code 1000}
     * @throws IllegalArgumentException if {@code pos} is {@code null}
     */
    public static Wdl evaluate(Position pos, boolean terminalAware) {
        if (pos == null) {
            throw new IllegalArgumentException("pos == null");
        }

        if (terminalAware) {
            // Note: isCheckmate() and legalMoves() can be expensive; keep it opt-in.
            if (pos.isCheckmate()) {
                return new Wdl((short) 0, (short) 0, TOTAL);
            }
            if (!pos.inCheck() && pos.legalMoves().isEmpty()) {
                return new Wdl((short) 0, TOTAL, (short) 0);
            }
        }

        byte[] board = pos.getBoard();
        if (isInsufficientMaterial(board)) {
            return new Wdl((short) 0, TOTAL, (short) 0);
        }

        EvalBuffers buffers = BUFFERS.get();
        buffers.reset();
        int whiteScoreCp = evaluateWhiteCentipawns(pos, board, buffers);
        int stmScoreCp = pos.isWhiteToMove() ? whiteScoreCp : -whiteScoreCp;

        double materialFactor = buffers.phase; // == totalMaterial / START_TOTAL_MATERIAL_CP, clamped
        double endgame = 1.0 - materialFactor;

        // Make the draw region slightly wider and the curve slightly flatter as
        // material disappears, reflecting the increased drawing tendency in
        // simplified positions.
        double margin = DRAW_MARGIN_CP * (1.0 + 0.40 * endgame);
        double scale = SCALE_CP * (1.0 + 0.20 * endgame);

        double pWin = sigmoid((stmScoreCp - margin) / scale);
        double pLoss = sigmoid((-stmScoreCp - margin) / scale);

        // Clamp for pathological numeric cases, then derive draw.
        pWin = Numbers.clamp01(pWin);
        pLoss = Numbers.clamp01(pLoss);
        double winLossSum = pWin + pLoss;
        if (winLossSum > 1.0) {
            double renorm = winLossSum != 0.0 ? (1.0 / winLossSum) : 0.0;
            pWin *= renorm;
            pLoss *= renorm;
        }
        // Endgame: low material tends to draw more often.
        double extraDraw = endgame * ENDGAME_DRAW_BONUS;
        pWin *= (1.0 - extraDraw);
        pLoss *= (1.0 - extraDraw);
        double pDraw = 1.0 - pWin - pLoss;

        return fromProbabilities(pWin, pDraw, pLoss);
    }

    /**
     * Returns a heuristic centipawn score from the side-to-move perspective.
     *
     * <p>
     * This is a helper for callers that still want a centipawn-like scalar while
     * using the same underlying feature set as {@link #evaluate(Position)}.
     * </p>
     *
     * @param pos position to evaluate (non-null)
     * @return centipawn score from the side-to-move perspective
     * @throws IllegalArgumentException if {@code pos} is {@code null}
     */
    public static int evaluateStmCentipawns(Position pos) {
        if (pos == null) {
            throw new IllegalArgumentException("pos == null");
        }
        byte[] board = pos.getBoard();
        EvalBuffers buffers = BUFFERS.get();
        buffers.reset();
        int whiteScore = evaluateWhiteCentipawns(pos, board, buffers);
        return pos.isWhiteToMove() ? whiteScore : -whiteScore;
    }

    /**
     * Returns a heuristic centipawn score from White's perspective.
     *
     * <p>
     * Positive values mean White is better, negative values mean Black is better.
     * </p>
     *
     * @param pos position to evaluate (non-null)
     * @return centipawn score from White's perspective
     * @throws IllegalArgumentException if {@code pos} is {@code null}
     */
    public static int evaluateWhiteCentipawns(Position pos) {
        if (pos == null) {
            throw new IllegalArgumentException("pos == null");
        }
        byte[] board = pos.getBoard();
        EvalBuffers buffers = BUFFERS.get();
        buffers.reset();
        return evaluateWhiteCentipawns(pos, board, buffers);
    }

    /**
     * Implementation of the White-perspective heuristic evaluation.
     *
     * <p>
     * This method is kept package-private/private to allow the public entry
     * points to reuse the same scratch buffers for both centipawn and WDL
     * evaluation without allocating transient arrays.
     * </p>
     *
     * <p>
     * Callers must provide a {@link EvalBuffers} instance that has been freshly
     * {@link EvalBuffers#reset() reset}. As a side-effect, this method writes the
     * derived phase factor into {@link EvalBuffers#phase}.
     * </p>
     *
     * @param pos     position (non-null)
     * @param board   raw board array (non-null, typically {@code pos.getBoard()})
     * @param buffers scratch buffers (reset before call)
     * @return centipawn score from White's perspective
     */
    private static int evaluateWhiteCentipawns(Position pos, byte[] board, EvalBuffers buffers) {
        // This is a deliberately simple, phase-aware evaluation:
        // - material balance (computed while scanning pieces)
        // - small PST terms
        // - a few coarse structural features
        // - mobility, tempo and check penalty
        EvalScan scan = scanMaterialAndPst(board, buffers);
        double phase = updatePhase(scan, buffers);
        AttackInfo attacks = buffers.attacks;
        attacks.build(pos);

        int score = scan.score + (scan.whiteMaterial - scan.blackMaterial);
        score += bishopPairCp(scan.whiteBishops, scan.blackBishops);
        score += pawnStructureCp(pos, buffers.whitePawnsPerFile, buffers.blackPawnsPerFile,
                buffers.minBlackPawnRank, buffers.maxWhitePawnRank, attacks, phase);
        score += rookFileCp(buffers.whiteRooksFileCount, buffers.blackRooksFileCount, buffers.whitePawnsPerFile,
                buffers.blackPawnsPerFile);
        score += kingSafetyCp(pos, buffers, attacks, phase);
        score += activityCp(attacks, phase);
        score += threatsCp(pos, attacks, phase);
        score += spaceCp(pos, attacks, scan, phase);
        score += tempoCp(pos);
        score += checkPenaltyCp(pos);

        return score;
    }

    /**
     * Scans the board to collect material, PST, and structural signals.
     *
     * <p>Results are written into {@link EvalScan} and the {@link EvalBuffers} arrays.</p>
     *
     * @param board  raw board data
     * @param buffers scratch buffers for pawn/rook tracking and the scan result
     * @return populated {@link EvalScan} instance
     */
    private static EvalScan scanMaterialAndPst(byte[] board, EvalBuffers buffers) {
        EvalScan scan = buffers.scan;
        scan.whiteMaterial = 0;
        scan.blackMaterial = 0;
        scan.score = 0;
        scan.whiteBishops = 0;
        scan.blackBishops = 0;
        scan.whitePawns = 0;
        scan.blackPawns = 0;

        for (int square = 0; square < board.length; square++) {
            byte piece = board[square];
            if (piece == Piece.EMPTY || Piece.isKing(piece)) {
                continue;
            }
            boolean white = Piece.isWhite(piece);
            int psq = white ? square : flip(square);
            applyMaterial(scan, piece, white);
            applyPieceSquareAndStructure(scan, buffers, piece, square, white, psq);
        }

        return scan;
    }

    /**
     * Adds a piece's base material value to the running totals.
     *
     * <p>The caller already determined the piece color so the method only
     * dispatches to either white or black totals.</p>
     *
     * @param scan  accumulator for material totals
     * @param piece piece code whose value to add
     * @param white whether the piece belongs to White
     */
    private static void applyMaterial(EvalScan scan, byte piece, boolean white) {
        int value = Piece.getValue(piece);
        if (white) {
            scan.whiteMaterial += value;
        } else {
            scan.blackMaterial += value;
        }
    }

    /**
     * Updates position-specific heuristics for a single piece.
     *
     * <p>This dispatches to the appropriate helper based on the piece type and color.</p>
     *
     * @param scan    running accumulation of related signals
     * @param buffers scratch buffers that track pawn and rook structure
     * @param piece   piece code being processed
     * @param square  raw square index
     * @param white   whether the piece is White
     * @param psq     piece-square table index for White perspective
     */
    private static void applyPieceSquareAndStructure(EvalScan scan, EvalBuffers buffers, byte piece, int square,
            boolean white, int psq) {
        int type = Math.abs(piece);
        switch (type) {
            case 1:
                applyPawn(scan, buffers, white, square, psq);
                break;
            case 2:
                applyKnight(scan, white, psq);
                break;
            case 3:
                applyBishop(scan, white, psq);
                break;
            case 4:
                applyRook(scan, buffers, white, square, psq);
                break;
            case 5:
                applyQueen(scan, white, psq);
                break;
            default:
                break;
        }
    }

    /**
     * Applies knight PST contributions for the current color.
     *
     * <p>Knights only affect the score, no structural state is tracked.</p>
     *
     * @param scan running score accumulator
     * @param white whether the knight belongs to White
     * @param psq piece-square index for the knight
     */
    private static void applyKnight(EvalScan scan, boolean white, int psq) {
        int v = KNIGHT_PST[psq];
        scan.score += white ? v : -v;
    }

    /**
     * Applies bishop PST value and bishop-count updates.
     *
     * <p>Bishop pair bonuses rely on the tallied counts rather than the PST.</p>
     *
     * @param scan  running score and bishop counts
     * @param white whether the bishop belongs to White
     * @param psq   piece-square index for the bishop
     */
    private static void applyBishop(EvalScan scan, boolean white, int psq) {
        int v = BISHOP_PST[psq];
        scan.score += white ? v : -v;
        if (white) {
            scan.whiteBishops++;
        } else {
            scan.blackBishops++;
        }
    }

    /**
     * Applies rook PST value and notes rooks on their files.
     *
     * <p>The file counts are used later to reward open and semi-open files.</p>
     *
     * @param scan    score accumulator
     * @param buffers rook file count buffers
     * @param white   whether the rook belongs to White
     * @param square  raw square index
     * @param psq     piece-square index for the rook
     */
    private static void applyRook(EvalScan scan, EvalBuffers buffers, boolean white, int square, int psq) {
        int file = square & 7;
        int v = ROOK_PST[psq];
        scan.score += white ? v : -v;
        if (white) {
            buffers.whiteRooksFileCount[file]++;
        } else {
            buffers.blackRooksFileCount[file]++;
        }
    }

    /**
     * Applies queen PST contribution for the current side.
     *
     * @param scan  running score accumulator
     * @param white whether the queen belongs to White
     * @param psq   piece-square index for the queen
     */
    private static void applyQueen(EvalScan scan, boolean white, int psq) {
        int v = QUEEN_PST[psq];
        scan.score += white ? v : -v;
    }

    /**
     * Updates pawn structure metrics along with PST contributions.
     *
     * <p>The buffers capture file counts and extreme pawn ranks per color.</p>
     *
     * @param scan   score accumulator
     * @param buffers pawn/rook structure buffers
     * @param white  whether the pawn belongs to White
     * @param square raw square index
     * @param psq    piece-square index for the pawn
     */
    private static void applyPawn(EvalScan scan, EvalBuffers buffers, boolean white, int square, int psq) {
        int file = square & 7;
        int rank = square >>> 3;
        int pawnPst = PAWN_PST[psq];
        scan.score += white ? pawnPst : -pawnPst;
        if (white) {
            scan.whitePawns++;
            buffers.whitePawnsPerFile[file]++;
            if (rank < buffers.minWhitePawnRank[file]) {
                buffers.minWhitePawnRank[file] = rank;
            }
            if (rank > buffers.maxWhitePawnRank[file]) {
                buffers.maxWhitePawnRank[file] = rank;
            }
        } else {
            scan.blackPawns++;
            buffers.blackPawnsPerFile[file]++;
            if (rank > buffers.maxBlackPawnRank[file]) {
                buffers.maxBlackPawnRank[file] = rank;
            }
            if (rank < buffers.minBlackPawnRank[file]) {
                buffers.minBlackPawnRank[file] = rank;
            }
        }
    }

    /**
     * Updates the shared phase factor used to interpolate PST tables.
     *
     * <p>Derived from the clamped material ratio stored back into the buffers.</p>
     *
     * @param scan    scan result containing material totals
     * @param buffers buffers that store the phase scalar
     * @return computed phase factor in [0,1]
     */
    private static double updatePhase(EvalScan scan, EvalBuffers buffers) {
        int totalMaterial = scan.whiteMaterial + scan.blackMaterial;
        double phase = Numbers.clamp01(totalMaterial / (double) START_TOTAL_MATERIAL_CP); // 1.0 = opening, 0.0 = endgame
        buffers.phase = phase;
        return phase;
    }

    /**
     * Applies the bishop pair bonus/penalty.
     *
     * <p>Encourages having two bishops while penalizing the opponent for the same.</p>
     *
     * @param whiteBishops count of White bishops
     * @param blackBishops count of Black bishops
     * @return centipawn adjustment from bishop pairs
     */
    private static int bishopPairCp(int whiteBishops, int blackBishops) {
        int score = 0;
        if (whiteBishops >= 2) {
            score += BISHOP_PAIR_CP;
        }
        if (blackBishops >= 2) {
            score -= BISHOP_PAIR_CP;
        }
        return score;
    }

    /**
     * Blends opening and endgame king PST scores according to the phase factor.
     *
     * @param pos     current position
     * @param buffers pawn-file scratch data collected during the board scan
     * @param attacks shared attack information
     * @param phase   interpolation scalar from {@link #updatePhase(EvalScan, EvalBuffers)}
     * @return centipawn adjustment for both kings
     */
    private static int kingSafetyCp(Position pos, EvalBuffers buffers, AttackInfo attacks, double phase) {
        int score = 0;
        int whiteKing = pos.kingSquare(true);
        if (whiteKing >= 0) {
            int psq = whiteKing;
            score += (int) Math.round(KING_PST_OPENING[psq] * phase + KING_PST_ENDGAME[psq] * (1.0 - phase));
            score += sideKingSafetyCp(pos, buffers, attacks, true, whiteKing, phase);
        }
        int blackKing = pos.kingSquare(false);
        if (blackKing >= 0) {
            int psq = flip(blackKing);
            score -= (int) Math.round(KING_PST_OPENING[psq] * phase + KING_PST_ENDGAME[psq] * (1.0 - phase));
            score -= sideKingSafetyCp(pos, buffers, attacks, false, blackKing, phase);
        }
        return score;
    }

    /**
     * Scores king shelter and nearby enemy pressure for one side.
     *
     * @param pos     current position
     * @param buffers pawn-file scratch data
     * @param attacks shared attack information
     * @param white   side whose king is being scored
     * @param king    king square
     * @param phase   game phase
     * @return positive when this side's king is safer
     */
    private static int sideKingSafetyCp(Position pos, EvalBuffers buffers, AttackInfo attacks, boolean white, int king,
            double phase) {
        if (phase <= 0.0) {
            return 0;
        }
        int shelter = bestShelterCp(pos, buffers, white, king);
        int pressure = kingZonePressureCp(pos, attacks, white, king);
        long enemyQueens = pos.pieces(white ? Position.BLACK_QUEEN : Position.WHITE_QUEEN);
        double enemyQueenFactor = 0.55;
        if (enemyQueens != 0L) {
            enemyQueenFactor = 1.0;
        }
        return (int) Math.round((shelter - pressure * enemyQueenFactor) * phase);
    }

    /**
     * Returns the best available king shelter, considering current castling rights.
     *
     * @param pos     current position
     * @param buffers pawn-file scratch data
     * @param white   side whose king is being scored
     * @param king    current king square
     * @return shelter score in centipawns
     */
    private static int bestShelterCp(Position pos, EvalBuffers buffers, boolean white, int king) {
        int shelter = shelterAt(buffers, white, king);
        if (white) {
            if (pos.canCastle(Position.WHITE_KINGSIDE)) {
                shelter = Math.max(shelter, shelterAt(buffers, true, Field.G1));
            }
            if (pos.canCastle(Position.WHITE_QUEENSIDE)) {
                shelter = Math.max(shelter, shelterAt(buffers, true, Field.C1));
            }
        } else {
            if (pos.canCastle(Position.BLACK_KINGSIDE)) {
                shelter = Math.max(shelter, shelterAt(buffers, false, Field.G8));
            }
            if (pos.canCastle(Position.BLACK_QUEENSIDE)) {
                shelter = Math.max(shelter, shelterAt(buffers, false, Field.C8));
            }
        }
        return shelter;
    }

    /**
     * Scores pawn shelter and pawn storms around a candidate king square.
     *
     * @param buffers pawn-file scratch data
     * @param white   side whose king is being scored
     * @param king    candidate king square
     * @return positive shelter score
     */
    private static int shelterAt(EvalBuffers buffers, boolean white, int king) {
        int file = king & 7;
        int rank = king >>> 3;
        int centerFile = Math.max(1, Math.min(6, file));
        int score = 6;
        for (int f = centerFile - 1; f <= centerFile + 1; f++) {
            int ownDistance = ownShieldDistance(buffers, white, f, rank);
            int enemyDistance = enemyStormDistance(buffers, white, f, rank);
            score += shieldScore(ownDistance);
            score -= stormPenalty(enemyDistance);
            if (f == file && ownDistance > 2) {
                score -= 8;
            }
            boolean hasOwnPawn = (white ? buffers.whitePawnsPerFile[f] : buffers.blackPawnsPerFile[f]) != 0;
            boolean hasEnemyPawn = (white ? buffers.blackPawnsPerFile[f] : buffers.whitePawnsPerFile[f]) != 0;
            if (!hasOwnPawn) {
                score -= hasEnemyPawn ? 7 : 13;
            }
        }
        return score;
    }

    /**
     * Returns the distance from king to closest friendly shelter pawn on a file.
     *
     * @param buffers pawn-file scratch data
     * @param white   side whose king is being scored
     * @param file    file to inspect
     * @param rank    king rank index in board-array coordinates
     * @return distance in ranks, or {@code 8} when no useful shelter pawn exists
     */
    private static int ownShieldDistance(EvalBuffers buffers, boolean white, int file, int rank) {
        if (white) {
            int pawnRank = buffers.maxWhitePawnRank[file];
            return pawnRank >= 0 && pawnRank < rank ? rank - pawnRank : 8;
        }
        int pawnRank = buffers.minBlackPawnRank[file];
        return pawnRank < 8 && pawnRank > rank ? pawnRank - rank : 8;
    }

    /**
     * Returns the distance from king to the closest enemy pawn storm on a file.
     *
     * @param buffers pawn-file scratch data
     * @param white   side whose king is being scored
     * @param file    file to inspect
     * @param rank    king rank index in board-array coordinates
     * @return distance in ranks, or {@code 8} when no relevant enemy pawn exists
     */
    private static int enemyStormDistance(EvalBuffers buffers, boolean white, int file, int rank) {
        if (white) {
            int pawnRank = buffers.maxBlackPawnRank[file];
            return pawnRank >= 0 && pawnRank < rank ? rank - pawnRank : 8;
        }
        int pawnRank = buffers.minWhitePawnRank[file];
        return pawnRank < 8 && pawnRank > rank ? pawnRank - rank : 8;
    }

    /**
     * Converts shelter-pawn distance into centipawns.
     *
     * @param distance pawn distance from the king
     * @return shelter score
     */
    private static int shieldScore(int distance) {
        return switch (distance) {
            case 1 -> 14;
            case 2 -> 6;
            case 3 -> -4;
            default -> -16;
        };
    }

    /**
     * Converts enemy pawn-storm distance into a penalty.
     *
     * @param distance pawn distance from the king
     * @return storm penalty
     */
    private static int stormPenalty(int distance) {
        return switch (distance) {
            case 1 -> 22;
            case 2 -> 15;
            case 3 -> 9;
            case 4 -> 4;
            default -> 0;
        };
    }

    /**
     * Scores enemy attacks into the king zone.
     *
     * @param pos       current position
     * @param attacks   shared attack information
     * @param whiteKing true when scoring White's king safety
     * @param king      king square
     * @return pressure penalty before phase scaling
     */
    private static int kingZonePressureCp(Position pos, AttackInfo attacks, boolean whiteKing, int king) {
        int us = sideIndex(whiteKing);
        int them = 1 - us;
        int attackers = attacks.kingAttackersCount[them];
        int weight = attacks.kingAttackersWeight[them];
        if (attackers == 0) {
            return 0;
        }
        long weak = attacks.attackedBy[them][ALL_ATTACKS]
                & ~attacks.attackedBy2[us]
                & (~attacks.attackedBy[us][ALL_ATTACKS]
                        | attacks.attackedBy[us][Piece.KING]
                        | attacks.attackedBy[us][Piece.QUEEN]);
        long flank = kingFlankMask(king) & campMask(whiteKing);
        int flankAttack = Long.bitCount(attacks.attackedBy[them][ALL_ATTACKS] & flank)
                + Long.bitCount(attacks.attackedBy2[them] & flank);
        int flankDefense = Long.bitCount(attacks.attackedBy[us][ALL_ATTACKS] & flank);
        int pressure = weight
                + attackers * attackers * 4
                + 12 * attacks.kingAttacksCount[them]
                + 9 * Long.bitCount(attacks.kingZone[us] & weak)
                + flankAttack * flankAttack / 2
                + Math.max(0, attacks.mobilityMg[them] - attacks.mobilityMg[us]) / 2
                - flankDefense * 3;
        if (((pos.pieces(Position.WHITE_PAWN) | pos.pieces(Position.BLACK_PAWN)) & flank) == 0L) {
            pressure += 18;
        }
        return attackers == 1 ? pressure / 2 : pressure;
    }

    /**
     * Converts a side color into the attack-table side index.
     *
     * @param white side color
     * @return {@link #WHITE} or {@link #BLACK}
     */
    static int sideIndex(boolean white) {
        return white ? WHITE : BLACK;
    }

    /**
     * Returns a broad flank mask around a king file.
     *
     * @param king king square
     * @return flank mask
     */
    private static long kingFlankMask(int king) {
        int file = king & 7;
        int center = Math.max(1, Math.min(6, file));
        return fileBitboard(center - 1) | fileBitboard(center) | fileBitboard(center + 1);
    }

    /**
     * Returns the side's home half of the board.
     *
     * @param white side color
     * @return camp mask
     */
    private static long campMask(boolean white) {
        return white
                ? ~(Bits.RANK_6 | Bits.RANK_7 | Bits.RANK_8)
                : ~(Bits.RANK_1 | Bits.RANK_2 | Bits.RANK_3);
    }

    /**
     * Small tempo bonus favoring the side to move.
     *
     * @param pos current position
     * @return centipawn bonus (positive when White to move, negative otherwise)
     */
    private static int tempoCp(Position pos) {
        return pos.isWhiteToMove() ? TEMPO_CP : -TEMPO_CP;
    }

    /**
     * Applies a check penalty to the side that is currently in check.
     *
     * @param pos current position
     * @return centipawn penalty, positive if White in check, negative if Black
     */
    private static int checkPenaltyCp(Position pos) {
        if (!pos.inCheck()) {
            return 0;
        }
        return pos.isWhiteToMove() ? -IN_CHECK_CP : IN_CHECK_CP;
    }

    /**
     * Blends attack-state piece activity and mobility scores.
     *
     * @param attacks shared attack and activity information
     * @param phase game phase
     * @return centipawn contribution from White's perspective
     */
    private static int activityCp(AttackInfo attacks, double phase) {
        int mg = attacks.mobilityMg[WHITE] + attacks.pieceMg[WHITE]
                - attacks.mobilityMg[BLACK] - attacks.pieceMg[BLACK];
        int eg = attacks.mobilityEg[WHITE] + attacks.pieceEg[WHITE]
                - attacks.mobilityEg[BLACK] - attacks.pieceEg[BLACK];
        return blend(mg, eg, phase);
    }

    /**
     * Looks up a mobility score for one piece type.
     *
     * @param type     0 knight, 1 bishop, 2 rook, 3 queen
     * @param mobility reachable safe target count
     * @return score in centipawns
     */

    static int mobilityScore(int type, int mobility) {
        int[] table = switch (type) {
            case 0 -> KNIGHT_MOBILITY_CP;
            case 1 -> BISHOP_MOBILITY_CP;
            case 2 -> ROOK_MOBILITY_CP;
            case 3 -> QUEEN_MOBILITY_CP;
            default -> KNIGHT_MOBILITY_CP;
        };
        return table[Math.min(mobility, table.length - 1)];
    }

    /**
     * Looks up an endgame mobility score for one piece type.
     *
     * @param type     0 knight, 1 bishop, 2 rook, 3 queen
     * @param mobility reachable safe target count
     * @return score in centipawns
     */

    static int mobilityEgScore(int type, int mobility) {
        int[] table = switch (type) {
            case 0 -> KNIGHT_MOBILITY_EG_CP;
            case 1 -> BISHOP_MOBILITY_EG_CP;
            case 2 -> ROOK_MOBILITY_EG_CP;
            case 3 -> QUEEN_MOBILITY_EG_CP;
            default -> KNIGHT_MOBILITY_EG_CP;
        };
        return table[Math.min(mobility, table.length - 1)];
    }

    /**
     * Blends midgame and endgame terms by phase.
     *
     * @param mg midgame score
     * @param eg endgame score
     * @param phase phase in {@code [0,1]}
     * @return blended score
     */
    private static int blend(int mg, int eg, double phase) {
        return (int) Math.round(mg * phase + eg * (1.0 - phase));
    }

    /**
     * Returns whether a minor piece occupies a pawn-protected outpost.
     *
     * @param white            side to score
     * @param square           piece square
     * @param ownPawnAttacks   friendly pawn attack mask
     * @param enemyPawnAttacks enemy pawn attack mask
     * @return true when the square is an outpost
     */

    static boolean isOutpost(boolean white, int square, long ownPawnAttacks, long enemyPawnAttacks) {
        long bit = 1L << square;
        if ((ownPawnAttacks & bit) == 0L || (enemyPawnAttacks & bit) != 0L) {
            return false;
        }
        int relativeRank = white ? Bits.rank(square) : 7 - Bits.rank(square);
        return relativeRank >= 3 && relativeRank <= 5;
    }

    /**
     * Penalizes bishops buried behind too many same-color friendly pawns.
     *
     * @param bishop    bishop square
     * @param mobility  safe mobility count
     * @param ownPawns  friendly pawn bitboard
     * @return penalty in centipawns
     */

    static int badBishopPenalty(int bishop, int mobility, long ownPawns) {
        if (mobility > 4) {
            return 0;
        }
        long colorMask = (((bishop & 7) + (bishop >>> 3)) & 1) == 0 ? LIGHT_SQUARES : DARK_SQUARES;
        int sameColorPawns = Long.bitCount(ownPawns & colorMask);
        return sameColorPawns <= 4 ? 0 : (sameColorPawns - 4) * 4 + (4 - mobility) * 3;
    }

    /**
     * Scores tactical threats from the shared attack maps.
     *
     * @param pos current position
     * @param attacks shared attack information
     * @param phase game phase
     * @return threat score from White's perspective
     */
    private static int threatsCp(Position pos, AttackInfo attacks, double phase) {
        int white = sideThreatsCp(pos, attacks, true, phase);
        int black = sideThreatsCp(pos, attacks, false, phase);
        return white - black;
    }

    /**
     * Scores attacked, weak, hanging, and pawn-threatened enemy pieces for one side.
     *
     * @param pos current position
     * @param attacks shared attack information
     * @param white side to score
     * @param phase game phase
     * @return score for the side
     */
    private static int sideThreatsCp(Position pos, AttackInfo attacks, boolean white, double phase) {
        int us = sideIndex(white);
        int them = 1 - us;
        long enemies = pos.occupancy(!white);
        long nonPawnEnemies = enemies & ~pos.pieces(white ? Position.BLACK_PAWN : Position.WHITE_PAWN);
        long stronglyProtected = attacks.attackedBy[them][Piece.PAWN]
                | (attacks.attackedBy2[them] & ~attacks.attackedBy2[us]);
        long weak = enemies & ~stronglyProtected & attacks.attackedBy[us][ALL_ATTACKS];

        int mg = 0;
        int eg = 0;
        long minorTargets = (weak | (nonPawnEnemies & stronglyProtected))
                & (attacks.attackedBy[us][Piece.KNIGHT] | attacks.attackedBy[us][Piece.BISHOP]);
        while (minorTargets != 0L) {
            int square = Long.numberOfTrailingZeros(minorTargets);
            minorTargets &= minorTargets - 1L;
            int type = Math.abs(pos.pieceAt(square));
            mg += minorThreatMg(type);
            eg += minorThreatEg(type);
        }

        long rookTargets = weak & attacks.attackedBy[us][Piece.ROOK];
        while (rookTargets != 0L) {
            int square = Long.numberOfTrailingZeros(rookTargets);
            rookTargets &= rookTargets - 1L;
            int type = Math.abs(pos.pieceAt(square));
            mg += rookThreatMg(type);
            eg += rookThreatEg(type);
        }

        long hanging = weak & (~attacks.attackedBy[them][ALL_ATTACKS] | attacks.attackedBy2[us]);
        int hangingCount = Long.bitCount(hanging);
        mg += hangingCount * 22;
        eg += hangingCount * 14;

        long restricted = attacks.attackedBy[them][ALL_ATTACKS]
                & ~stronglyProtected
                & attacks.attackedBy[us][ALL_ATTACKS];
        mg += Long.bitCount(restricted) * 4;

        long safe = ~attacks.attackedBy[them][ALL_ATTACKS] | attacks.attackedBy[us][ALL_ATTACKS];
        long safePawnThreats = attacks.attackedBy[us][Piece.PAWN] & nonPawnEnemies & safe;
        mg += Long.bitCount(safePawnThreats) * 32;
        eg += Long.bitCount(safePawnThreats) * 26;

        long pushedPawns = pawnPushMask(white, pos.pieces(white ? Position.WHITE_PAWN : Position.BLACK_PAWN),
                ~pos.occupancy());
        long pawnPushThreats = pawnAttackMask(white, pushedPawns)
                & nonPawnEnemies
                & ~attacks.attackedBy[them][Piece.PAWN]
                & safe;
        mg += Long.bitCount(pawnPushThreats) * 18;
        eg += Long.bitCount(pawnPushThreats) * 16;

        long enemyQueens = pos.pieces(white ? Position.BLACK_QUEEN : Position.WHITE_QUEEN);
        if (enemyQueens != 0L) {
            int queen = Long.numberOfTrailingZeros(enemyQueens);
            long safeQueenAttackers = attacks.attackedBy[us][Piece.KNIGHT]
                    & MoveGenerator.knightAttacks(queen)
                    & safe;
            mg += Long.bitCount(safeQueenAttackers) * 16;
            eg += Long.bitCount(safeQueenAttackers) * 10;
            long sliderAttackers = (attacks.attackedBy[us][Piece.BISHOP]
                    & MoveGenerator.bishopAttacks(queen, pos.occupancy()))
                    | (attacks.attackedBy[us][Piece.ROOK]
                            & MoveGenerator.rookAttacks(queen, pos.occupancy()));
            int sliderPressure = Long.bitCount(sliderAttackers & attacks.attackedBy2[us] & safe);
            mg += sliderPressure * 18;
            eg += sliderPressure * 12;
        }
        return blend(mg, eg, phase);
    }

    /**
     * Returns a midgame threat score for minor-piece attacks by target type.
     *
     * @param type attacked piece type
     * @return score
     */
    private static int minorThreatMg(int type) {
        return switch (type) {
            case Piece.PAWN -> 6;
            case Piece.KNIGHT, Piece.BISHOP -> 28;
            case Piece.ROOK -> 44;
            case Piece.QUEEN -> 58;
            default -> 0;
        };
    }

    /**
     * Returns an endgame threat score for minor-piece attacks by target type.
     *
     * @param type attacked piece type
     * @return score
     */
    private static int minorThreatEg(int type) {
        return switch (type) {
            case Piece.PAWN -> 16;
            case Piece.KNIGHT, Piece.BISHOP -> 24;
            case Piece.ROOK -> 38;
            case Piece.QUEEN -> 70;
            default -> 0;
        };
    }

    /**
     * Returns a midgame threat score for rook attacks by target type.
     *
     * @param type attacked piece type
     * @return score
     */
    private static int rookThreatMg(int type) {
        return switch (type) {
            case Piece.PAWN -> 4;
            case Piece.KNIGHT, Piece.BISHOP -> 22;
            case Piece.QUEEN -> 42;
            default -> 0;
        };
    }

    /**
     * Returns an endgame threat score for rook attacks by target type.
     *
     * @param type attacked piece type
     * @return score
     */
    private static int rookThreatEg(int type) {
        return switch (type) {
            case Piece.PAWN -> 28;
            case Piece.KNIGHT, Piece.BISHOP -> 34;
            case Piece.ROOK -> 18;
            case Piece.QUEEN -> 36;
            default -> 0;
        };
    }

    /**
     * Scores safe central space in the opening and early middlegame.
     *
     * @param pos current position
     * @param attacks shared attack information
     * @param scan material scan
     * @param phase game phase
     * @return space score from White's perspective
     */
    private static int spaceCp(Position pos, AttackInfo attacks, EvalScan scan, double phase) {
        if (phase < 0.45 || nonPawnMaterial(scan) < 2400) {
            return 0;
        }
        int white = sideSpaceCp(pos, attacks, true);
        int black = sideSpaceCp(pos, attacks, false);
        return (int) Math.round((white - black) * phase);
    }

    /**
     * Scores safe central space for one side.
     *
     * @param pos current position
     * @param attacks shared attack information
     * @param white side to score
     * @return side space score
     */
    private static int sideSpaceCp(Position pos, AttackInfo attacks, boolean white) {
        int them = sideIndex(!white);
        long pawns = pos.pieces(white ? Position.WHITE_PAWN : Position.BLACK_PAWN);
        long mask = white ? WHITE_SPACE_MASK : BLACK_SPACE_MASK;
        long safe = mask & ~pawns & ~attacks.attackedBy[them][Piece.PAWN];
        long behind = pawns;
        long oneBehind = shiftBackward(white, pawns);
        long twoBehind = shiftBackward(white, oneBehind);
        long threeBehind = shiftBackward(white, twoBehind);
        behind |= oneBehind | twoBehind | threeBehind;
        int bonus = Long.bitCount(safe)
                + Long.bitCount(behind & safe & ~attacks.attackedBy[them][ALL_ATTACKS]);
        int blocked = blockedPawnCount(pos, white);
        int weight = Math.max(0, Long.bitCount(pos.occupancy(white)) - 3 + Math.min(blocked, 9));
        return bonus * weight * weight / 12;
    }

    /**
     * Returns non-pawn material from the scan.
     *
     * @param scan material scan
     * @return non-pawn material estimate
     */
    private static int nonPawnMaterial(EvalScan scan) {
        return scan.whiteMaterial + scan.blackMaterial
                - (scan.whitePawns + scan.blackPawns) * Piece.VALUE_PAWN;
    }

    /**
     * Shifts squares one rank back toward the side's home rank.
     *
     * @param white side color
     * @param mask input mask
     * @return shifted mask
     */
    private static long shiftBackward(boolean white, long mask) {
        return white ? (mask << 8) : (mask >>> 8);
    }

    /**
     * Counts blocked pawns for a side.
     *
     * @param pos current position
     * @param white side color
     * @return blocked pawn count
     */
    private static int blockedPawnCount(Position pos, boolean white) {
        long pawns = pos.pieces(white ? Position.WHITE_PAWN : Position.BLACK_PAWN);
        long occupied = pos.occupancy();
        long blocked = white ? ((pawns >>> 8) & occupied) : ((pawns << 8) & occupied);
        return Long.bitCount(blocked);
    }

    /**
     * Fast file-based pawn-structure evaluation.
     *
     * <p>
     * This is intentionally coarse and only uses file occupancy and a per-file
     * rank summary. It captures a few high-signal heuristics cheaply:
     * </p>
     * <ul>
     * <li>Doubled pawns</li>
     * <li>Isolated pawns</li>
     * <li>Passed pawns (file/adjacent file check)</li>
     * </ul>
     *
     * @param pos               current position
     * @param whitePawnsPerFile white pawn counts per file
     * @param blackPawnsPerFile black pawn counts per file
     * @param minBlackPawnRank  minimum rank of black pawn per file
     * @param maxWhitePawnRank  maximum rank of white pawn per file
     * @param attacks           shared attack information
     * @param phase             phase scalar used for scaling passed pawns
     * @return score in centipawns from White's perspective
     */
    private static int pawnStructureCp(Position pos, int[] whitePawnsPerFile, int[] blackPawnsPerFile,
            int[] minBlackPawnRank, int[] maxWhitePawnRank, AttackInfo attacks, double phase) {
        int whiteFileMask = fileMask(whitePawnsPerFile);
        int blackFileMask = fileMask(blackPawnsPerFile);

        int score = 0;
        score += doubledPawnsScore(whitePawnsPerFile, blackPawnsPerFile);
        score += isolatedPawnsScore(whitePawnsPerFile, blackPawnsPerFile, whiteFileMask, blackFileMask);
        score += passedPawnsScore(pos, minBlackPawnRank, maxWhitePawnRank, attacks, phase);
        return score;
    }

    /**
     * Generates a bitmask of files containing at least one pawn.
     *
     * <p>Each bit corresponds to a file (0 == A, 7 == H).</p>
     *
     * @param pawnsPerFile pawn counts per file
     * @return bitmask of occupied files
     */
    private static int fileMask(int[] pawnsPerFile) {
        int mask = 0;
        for (int f = 0; f < 8; f++) {
            if (pawnsPerFile[f] != 0) {
                mask |= (1 << f);
            }
        }
        return mask;
    }

    /**
     * Scores doubled pawns by penalizing stacks per file.
     *
     * @param whitePawnsPerFile white pawn counts per file
     * @param blackPawnsPerFile black pawn counts per file
     * @return centipawn adjustment (White negative, Black positive)
     */
    private static int doubledPawnsScore(int[] whitePawnsPerFile, int[] blackPawnsPerFile) {
        int score = 0;
        for (int f = 0; f < 8; f++) {
            int w = whitePawnsPerFile[f];
            int b = blackPawnsPerFile[f];
            if (w > 1) {
                score -= (w - 1) * 12;
            }
            if (b > 1) {
                score += (b - 1) * 12;
            }
        }
        return score;
    }

    /**
     * Applies isolation penalties by checking adjacent files.
     *
     * @param whitePawnsPerFile white pawn counts per file
     * @param blackPawnsPerFile black pawn counts per file
     * @param whiteFileMask     mask of files containing White pawns
     * @param blackFileMask     mask of files containing Black pawns
     * @return centipawn adjustment for isolated pawns
     */
    private static int isolatedPawnsScore(int[] whitePawnsPerFile, int[] blackPawnsPerFile, int whiteFileMask,
            int blackFileMask) {
        int score = 0;
        for (int f = 0; f < 8; f++) {
            int adjacentMask = adjacentFileMask(f);
            if (whitePawnsPerFile[f] != 0 && (whiteFileMask & adjacentMask) == 0) {
                score -= 10;
            }
            if (blackPawnsPerFile[f] != 0 && (blackFileMask & adjacentMask) == 0) {
                score += 10;
            }
        }
        return score;
    }

    /**
     * Computes the mask of files adjacent to {@code file}.
     *
     * @param file file index 0..7
     * @return mask with bits set for neighboring files
     */
    private static int adjacentFileMask(int file) {
        int mask = 0;
        if (file > 0) {
            mask |= (1 << (file - 1));
        }
        if (file < 7) {
            mask |= (1 << (file + 1));
        }
        return mask;
    }

    /**
     * Rewards passed pawns based on their rank and absence of opposing pawns ahead.
     *
     * <p>This method also blends the bonus based on the phase value.</p>
     *
     * @param pos               current position
     * @param minBlackPawnRank  minimum rank of black pawn per file
     * @param maxWhitePawnRank  maximum rank of white pawn per file
     * @param attacks           shared attack information
     * @param phase             phase scalar for endgame scaling
     * @return centipawn passed pawn contribution
     */
    private static int passedPawnsScore(Position pos, int[] minBlackPawnRank, int[] maxWhitePawnRank,
            AttackInfo attacks, double phase) {
        long whitePassed = passedPawnMask(true, pos.pieces(Position.WHITE_PAWN), minBlackPawnRank);
        long blackPassed = passedPawnMask(false, pos.pieces(Position.BLACK_PAWN), maxWhitePawnRank);
        int white = passedPawnScore(pos, attacks, true, whitePassed, pos.pieces(Position.WHITE_PAWN), phase);
        int black = passedPawnScore(pos, attacks, false, blackPassed, pos.pieces(Position.BLACK_PAWN), phase);
        return white - black;
    }

    /**
     * Builds a mask of passed pawns for one side.
     *
     * @param white           side to inspect
     * @param pawns           pawn bitboard
     * @param enemyFrontRanks per-file frontmost enemy pawn ranks
     * @return passed-pawn mask
     */
    private static long passedPawnMask(boolean white, long pawns, int[] enemyFrontRanks) {
        long passed = 0L;
        while (pawns != 0L) {
            int square = Long.numberOfTrailingZeros(pawns);
            pawns &= pawns - 1L;
            int file = square & 7;
            int rank = square >>> 3;
            boolean blocked = white
                    ? enemyPawnInFrontForWhite(file, rank, enemyFrontRanks)
                    : enemyPawnInFrontForBlack(file, rank, enemyFrontRanks);
            if (!blocked) {
                passed |= 1L << square;
            }
        }
        return passed;
    }

    /**
     * Scores all passed pawns for one side.
     *
     * @param pos      current position
     * @param attacks shared attack information
     * @param white   side to score
     * @param passed  passed-pawn mask for the side
     * @param pawns   all pawns for the side
     * @param phase   game phase
     * @return passed-pawn score for the side
     */

    private static int passedPawnScore(Position pos, AttackInfo attacks, boolean white, long passed, long pawns,
            double phase) {
        int mg = 0;
        int eg = 0;
        long defendedByPawn = pawnAttackMask(white, pawns);
        int us = sideIndex(white);
        int them = 1 - us;
        long remaining = passed;
        while (remaining != 0L) {
            int square = Long.numberOfTrailingZeros(remaining);
            remaining &= remaining - 1L;
            int relRank = white ? 7 - (square >>> 3) : square >>> 3;
            int baseMg = 8 + 7 * relRank + relRank * relRank;
            int baseEg = 18 + 10 * relRank + relRank * relRank * 2;
            long bit = 1L << square;
            if ((defendedByPawn & bit) != 0L) {
                baseMg += 8 + 2 * relRank;
                baseEg += 11 + 3 * relRank;
            }
            if (hasConnectedPasser(passed, square)) {
                baseMg += 7 + 2 * relRank;
                baseEg += 9 + 3 * relRank;
            }
            int blockSquare = white ? square - 8 : square + 8;
            if (blockSquare >= 0 && blockSquare < 64) {
                long blockBit = 1L << blockSquare;
                if ((pos.occupancy() & blockBit) != 0L) {
                    baseMg -= 10 + 3 * relRank;
                    baseEg -= 13 + 4 * relRank;
                } else {
                    long path = forwardFileMask(white, square);
                    long unsafe = path & attacks.attackedBy[them][ALL_ATTACKS] & ~attacks.attackedBy[us][ALL_ATTACKS];
                    if (unsafe == 0L) {
                        baseMg += 10 + 4 * relRank;
                        baseEg += 18 + 6 * relRank;
                    } else if ((unsafe & blockBit) == 0L) {
                        baseMg += 5 + 2 * relRank;
                        baseEg += 9 + 3 * relRank;
                    }
                    int ownKing = pos.kingSquare(white);
                    int enemyKing = pos.kingSquare(!white);
                    if (ownKing >= 0 && enemyKing >= 0 && relRank >= 4) {
                        int ownDistance = kingDistance(ownKing, blockSquare);
                        int enemyDistance = kingDistance(enemyKing, blockSquare);
                        baseEg += (enemyDistance - ownDistance) * (3 + relRank);
                    }
                }
            }
            mg += baseMg - edgeFileDistance(square & 7) * 3;
            eg += baseEg - edgeFileDistance(square & 7) * 2;
        }
        return blend(mg, eg, phase);
    }

    /**
     * Returns forward same-file squares for a pawn.
     *
     * @param white pawn side
     * @param square pawn square
     * @return forward file mask
     */
    private static long forwardFileMask(boolean white, int square) {
        long mask = 0L;
        int file = square & 7;
        int row = square >>> 3;
        if (white) {
            for (int r = row - 1; r >= 0; r--) {
                mask |= 1L << ((r << 3) | file);
            }
        } else {
            for (int r = row + 1; r < 8; r++) {
                mask |= 1L << ((r << 3) | file);
            }
        }
        return mask;
    }

    /**
     * Chebyshev distance between two king-relevant squares.
     *
     * @param a first square
     * @param b second square
     * @return king move distance
     */
    static int kingDistance(int a, int b) {
        return Math.max(Math.abs((a & 7) - (b & 7)), Math.abs((a >>> 3) - (b >>> 3)));
    }

    /**
     * Distance from the closest edge file.
     *
     * @param file file index
     * @return edge distance
     */
    private static int edgeFileDistance(int file) {
        return Math.min(file, 7 - file);
    }

    /**
     * Returns whether a passed pawn is connected to another passed pawn nearby.
     *
     * @param passed passed-pawn mask for one side
     * @param square pawn square
     * @return true when an adjacent-file passer is close enough to support it
     */
    private static boolean hasConnectedPasser(long passed, int square) {
        int file = square & 7;
        int rank = square >>> 3;
        long adjacent = passed & adjacentFileBitboard(file);
        while (adjacent != 0L) {
            int other = Long.numberOfTrailingZeros(adjacent);
            adjacent &= adjacent - 1L;
            if (Math.abs((other >>> 3) - rank) <= 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a Black pawn exists on the same or adjacent file, in front
     * of a given White pawn.
     *
     * <p>
     * This is a file-based approximation of "passed pawn" status.
     * </p>
     *
     * @param file            pawn file 0..7
     * @param whiteRank       rank index 0..7 (0 = 8th rank, 7 = 1st rank)
     * @param minBlackPawnRank per-file minimum rank of any Black pawn (8 means none)
     * @return true if a blocking Black pawn exists ahead on file/adjacent files
     */
    private static boolean enemyPawnInFrontForWhite(int file, int whiteRank, int[] minBlackPawnRank) {
        for (int df = -1; df <= 1; df++) {
            int f = file + df;
            if (f < 0 || f > 7) {
                continue;
            }
            if (minBlackPawnRank[f] < whiteRank) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a White pawn exists on the same or adjacent file, in front
     * of a given Black pawn.
     *
     * <p>
     * This is a file-based approximation of "passed pawn" status.
     * </p>
     *
     * @param file             pawn file 0..7
     * @param blackRank        rank index 0..7 (0 = 8th rank, 7 = 1st rank)
     * @param maxWhitePawnRank per-file maximum rank of any White pawn (-1 means none)
     * @return true if a blocking White pawn exists ahead on file/adjacent files
     */
    private static boolean enemyPawnInFrontForBlack(int file, int blackRank, int[] maxWhitePawnRank) {
        for (int df = -1; df <= 1; df++) {
            int f = file + df;
            if (f < 0 || f > 7) {
                continue;
            }
            if (maxWhitePawnRank[f] > blackRank) {
                return true;
            }
        }
        return false;
    }

    /**
     * Bonus/penalty for rooks on open and semi-open files.
     *
     * <p>
     * This uses only file occupancy:
     * </p>
     * <ul>
     * <li><b>Open file:</b> no pawns of either side on the file.</li>
     * <li><b>Semi-open file:</b> no friendly pawns on the file, but enemy pawns exist.</li>
     * </ul>
     *
     * @param whiteRooksFileCount white rook counts per file
     * @param blackRooksFileCount black rook counts per file
     * @param whitePawnsPerFile   white pawn counts per file
     * @param blackPawnsPerFile   black pawn counts per file
     * @return score in centipawns from White's perspective
     */
    private static int rookFileCp(int[] whiteRooksFileCount, int[] blackRooksFileCount, int[] whitePawnsPerFile,
            int[] blackPawnsPerFile) {
        int score = 0;
        for (int f = 0; f < 8; f++) {
            boolean hasAnyPawn = (whitePawnsPerFile[f] + blackPawnsPerFile[f]) != 0;
            boolean hasWhitePawn = whitePawnsPerFile[f] != 0;
            boolean hasBlackPawn = blackPawnsPerFile[f] != 0;

            int whiteRooks = whiteRooksFileCount[f];
            int blackRooks = blackRooksFileCount[f];

            if (whiteRooks != 0) {
                if (!hasAnyPawn) {
                    score += 14 * whiteRooks;
                } else if (!hasWhitePawn && hasBlackPawn) {
                    score += 8 * whiteRooks;
                }
            }
            if (blackRooks != 0) {
                if (!hasAnyPawn) {
                    score -= 14 * blackRooks;
                } else if (!hasBlackPawn && hasWhitePawn) {
                    score -= 8 * blackRooks;
                }
            }
        }
        return score;
    }

    /**
     * Builds the pawn attack mask for one side.
     *
     * @param white side to inspect
     * @param pawns pawn bitboard
     * @return attacked squares
     */
    static long pawnAttackMask(boolean white, long pawns) {
        if (white) {
            return ((pawns & ~Bits.FILE_A) >>> 9) | ((pawns & ~Bits.FILE_H) >>> 7);
        }
        return ((pawns & ~Bits.FILE_A) << 7) | ((pawns & ~Bits.FILE_H) << 9);
    }

    /**
     * Returns legal one-step pawn pushes for a side.
     *
     * @param white pawn side
     * @param pawns pawn bitboard
     * @param empty empty-square mask
     * @return pushed pawn destinations
     */
    private static long pawnPushMask(boolean white, long pawns, long empty) {
        long single = white ? ((pawns >>> 8) & empty) : ((pawns << 8) & empty);
        if (white) {
            return single | (((single & Bits.RANK_3) >>> 8) & empty);
        }
        return single | (((single & Bits.RANK_6) << 8) & empty);
    }

    /**
     * Returns a bitboard mask for one file.
     *
     * @param file file index 0..7
     * @return file bitboard
     */
    static long fileBitboard(int file) {
        return Bits.FILE_A << file;
    }

    /**
     * Returns adjacent-file bitboards for one file.
     *
     * @param file file index 0..7
     * @return adjacent-file mask
     */
    private static long adjacentFileBitboard(int file) {
        long mask = 0L;
        if (file > 0) {
            mask |= fileBitboard(file - 1);
        }
        if (file < 7) {
            mask |= fileBitboard(file + 1);
        }
        return mask;
    }

    /**
     * Builds a square-color mask.
     *
     * @param color square color parity
     * @return bitboard containing squares of the requested parity
     */
    private static long squareColorMask(int color) {
        long mask = 0L;
        for (int square = 0; square < 64; square++) {
            if ((((square & 7) + (square >>> 3)) & 1) == color) {
                mask |= 1L << square;
            }
        }
        return mask;
    }

    /**
     * Converts floating-point win/draw/loss probabilities into an integer triplet
     * that sums exactly to {@link #TOTAL}.
     *
     * <p>
     * Uses a <em>largest remainder</em> approach to avoid bias from rounding.
     * </p>
     *
     * @param pWin  win probability
     * @param pDraw draw probability
     * @param pLoss loss probability
     * @return WDL triplet summing to {@link #TOTAL}
     */
    private static Wdl fromProbabilities(double pWin, double pDraw, double pLoss) {
        pWin = Numbers.clamp01(pWin);
        pDraw = Numbers.clamp01(pDraw);
        pLoss = Numbers.clamp01(pLoss);

        double sum = pWin + pDraw + pLoss;
        if (sum <= 0.0) {
            return new Wdl((short) 0, TOTAL, (short) 0);
        }
        pWin /= sum;
        pDraw /= sum;
        pLoss /= sum;

        // Largest remainder method to ensure exact sum == TOTAL after rounding.
        int winBase = (int) Math.floor(pWin * TOTAL);
        int drawBase = (int) Math.floor(pDraw * TOTAL);
        int lossBase = (int) Math.floor(pLoss * TOTAL);

        double winFrac = (pWin * TOTAL) - winBase;
        double drawFrac = (pDraw * TOTAL) - drawBase;
        double lossFrac = (pLoss * TOTAL) - lossBase;

        int sumBase = winBase + drawBase + lossBase;
        int remainder = TOTAL - sumBase;

        int win = winBase;
        int draw = drawBase;
        int loss = lossBase;

        for (int i = 0; i < remainder; i++) {
            if (winFrac >= drawFrac && winFrac >= lossFrac) {
                win++;
                winFrac = -1.0;
            } else if (drawFrac >= lossFrac) {
                draw++;
                drawFrac = -1.0;
            } else {
                loss++;
                lossFrac = -1.0;
            }
        }

        return new Wdl((short) win, (short) draw, (short) loss);
    }

    /**
     * A numerically safe sigmoid used for centipawn→probability mapping.
     *
     * @param x unbounded input
     * @return value in [0,1]
     */
    private static double sigmoid(double x) {
        if (x > 20.0) {
            return 1.0;
        }
        if (x < -20.0) {
            return 0.0;
        }
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /**
     * Returns true for a few common configurations where mate is impossible.
     *
     * <p>
     * This is intentionally conservative: it only claims "insufficient" in cases
     * that are widely recognized as trivially drawn.
     * </p>
     *
     * <p>
     * Currently handled:
     * </p>
     * <ul>
     * <li>K vs K</li>
     * <li>K + (B|N) vs K</li>
     * <li>K + B vs K + B when both bishops live on the same square color</li>
     * </ul>
     *
     * @param board raw 64-square board array
     * @return true if the position is treated as trivially drawn
     */
    private static boolean isInsufficientMaterial(byte[] board) {
        MinorMaterialState state = new MinorMaterialState();
        for (int square = 0; square < board.length; square++) {
            if (!state.accept(square, board[square])) {
                return false;
            }
        }
        return state.isInsufficient();
    }


    /**
     * Flip a square vertically to reuse White-oriented tables for Black pieces.
     *
     * <p>
     * The internal square indexing is {@code A8==0 .. H1==63}. Vertical flipping
     * maps ranks 8↔1 while keeping files unchanged.
     * </p>
     *
     * @param square square index 0..63
     * @return vertically flipped square index 0..63
     */
    private static int flip(int square) {
        // Vertical flip (A8 <-> A1, H8 <-> H1) to reuse White-oriented tables.
        return square ^ 56;
    }

}
