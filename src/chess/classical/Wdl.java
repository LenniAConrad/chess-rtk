package chess.classical;

import chess.core.Piece;
import chess.core.Position;

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
public record Wdl(short win, short draw, short loss) {

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
     * Units: centipawns.
     * </p>
     */
    // Simple piece-square tables (from White perspective, A8..H1 indices).
    // Values are deliberately small: WDL mapping is coarse and we want stability.
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
     */
    public static Wdl evaluate(Position pos, boolean terminalAware) {
        if (pos == null) {
            throw new IllegalArgumentException("pos == null");
        }

        if (terminalAware) {
            // Note: isMate() and getMoves() can be expensive; keep it opt-in.
            if (pos.isMate()) {
                return new Wdl((short) 0, (short) 0, TOTAL);
            }
            if (!pos.inCheck() && pos.getMoves().isEmpty()) {
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
        int stmScoreCp = pos.isWhiteTurn() ? whiteScoreCp : -whiteScoreCp;

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
        pWin = clamp01(pWin);
        pLoss = clamp01(pLoss);
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
     */
    public static int evaluateStmCentipawns(Position pos) {
        if (pos == null) {
            throw new IllegalArgumentException("pos == null");
        }
        byte[] board = pos.getBoard();
        EvalBuffers buffers = BUFFERS.get();
        buffers.reset();
        int whiteScore = evaluateWhiteCentipawns(pos, board, buffers);
        return pos.isWhiteTurn() ? whiteScore : -whiteScore;
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

        int score = scan.score + (scan.whiteMaterial - scan.blackMaterial);
        score += bishopPairCp(scan.whiteBishops, scan.blackBishops);
        score += pawnStructureCp(buffers.whitePawnsPerFile, buffers.blackPawnsPerFile, buffers.minWhitePawnRank,
                buffers.maxBlackPawnRank, buffers.minBlackPawnRank, buffers.maxWhitePawnRank, phase);
        score += rookFileCp(buffers.whiteRooksFileCount, buffers.blackRooksFileCount, buffers.whitePawnsPerFile,
                buffers.blackPawnsPerFile);
        score += kingSafetyCp(pos, phase);
        score += tempoCp(pos);
        score += checkPenaltyCp(pos);
        score += mobilityCp(pos, phase);

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
            buffers.whitePawnsPerFile[file]++;
            if (rank < buffers.minWhitePawnRank[file]) {
                buffers.minWhitePawnRank[file] = rank;
            }
            if (rank > buffers.maxWhitePawnRank[file]) {
                buffers.maxWhitePawnRank[file] = rank;
            }
        } else {
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
        double phase = clamp01(totalMaterial / (double) START_TOTAL_MATERIAL_CP); // 1.0 = opening, 0.0 = endgame
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
     * @param pos   current position
     * @param phase interpolation scalar from {@link #updatePhase(EvalScan, EvalBuffers)}
     * @return centipawn adjustment for both kings
     */
    private static int kingSafetyCp(Position pos, double phase) {
        int score = 0;
        int whiteKing = pos.getWhiteKing();
        if (whiteKing >= 0) {
            int psq = whiteKing;
            score += (int) Math.round(KING_PST_OPENING[psq] * phase + KING_PST_ENDGAME[psq] * (1.0 - phase));
        }
        int blackKing = pos.getBlackKing();
        if (blackKing >= 0) {
            int psq = flip(blackKing);
            score -= (int) Math.round(KING_PST_OPENING[psq] * phase + KING_PST_ENDGAME[psq] * (1.0 - phase));
        }
        return score;
    }

    /**
     * Small tempo bonus favoring the side to move.
     *
     * @param pos current position
     * @return centipawn bonus (positive when White to move, negative otherwise)
     */
    private static int tempoCp(Position pos) {
        return pos.isWhiteTurn() ? TEMPO_CP : -TEMPO_CP;
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
        return pos.isWhiteTurn() ? -IN_CHECK_CP : IN_CHECK_CP;
    }

    /**
     * Scales the mobility score according to the current phase.
     *
     * @param pos   current position
     * @param phase phase scalar [0,1] indicating endgame weight
     * @return centipawn contribution from mobility
     */
    private static int mobilityCp(Position pos, double phase) {
        int mobilityCp = (int) Math.round(Mobility.evaluate(pos) * 100.0);
        return (int) Math.round(mobilityCp * (0.35 + 0.65 * phase));
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
     * @param whitePawnsPerFile white pawn counts per file
     * @param blackPawnsPerFile black pawn counts per file
     * @param minWhitePawnRank  minimum rank of white pawn per file
     * @param maxBlackPawnRank  maximum rank of black pawn per file
     * @param minBlackPawnRank  minimum rank of black pawn per file
     * @param maxWhitePawnRank  maximum rank of white pawn per file
     * @param phase             phase scalar used for scaling passed pawns
     * @return score in centipawns from White's perspective
     */
    private static int pawnStructureCp(int[] whitePawnsPerFile, int[] blackPawnsPerFile, int[] minWhitePawnRank,
            int[] maxBlackPawnRank, int[] minBlackPawnRank, int[] maxWhitePawnRank, double phase) {
        int whiteFileMask = fileMask(whitePawnsPerFile);
        int blackFileMask = fileMask(blackPawnsPerFile);

        int score = 0;
        score += doubledPawnsScore(whitePawnsPerFile, blackPawnsPerFile);
        score += isolatedPawnsScore(whitePawnsPerFile, blackPawnsPerFile, whiteFileMask, blackFileMask);
        score += passedPawnsScore(whitePawnsPerFile, blackPawnsPerFile, minWhitePawnRank, maxBlackPawnRank,
                minBlackPawnRank, maxWhitePawnRank, phase);
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
     * @param whitePawnsPerFile white pawn counts per file
     * @param blackPawnsPerFile black pawn counts per file
     * @param minWhitePawnRank  minimum rank of white pawn per file
     * @param maxBlackPawnRank  maximum rank of black pawn per file
     * @param minBlackPawnRank  minimum rank of black pawn per file
     * @param maxWhitePawnRank  maximum rank of white pawn per file
     * @param phase             phase scalar for endgame scaling
     * @return centipawn passed pawn contribution
     */
    private static int passedPawnsScore(int[] whitePawnsPerFile, int[] blackPawnsPerFile, int[] minWhitePawnRank,
            int[] maxBlackPawnRank, int[] minBlackPawnRank, int[] maxWhitePawnRank, double phase) {
        int score = 0;
        double passedScale = 0.45 + 0.85 * (1.0 - phase);
        for (int f = 0; f < 8; f++) {
            if (whitePawnsPerFile[f] != 0 && minWhitePawnRank[f] < 8) {
                int whiteRank = minWhitePawnRank[f];
                if (!enemyPawnInFrontForWhite(f, whiteRank, minBlackPawnRank)) {
                    int relRank = 7 - whiteRank;
                    score += (int) Math.round((12 + 6 * relRank) * passedScale);
                }
            }
            if (blackPawnsPerFile[f] != 0 && maxBlackPawnRank[f] >= 0) {
                int blackRank = maxBlackPawnRank[f];
                if (!enemyPawnInFrontForBlack(f, blackRank, maxWhitePawnRank)) {
                    int relRank = blackRank;
                    score -= (int) Math.round((12 + 6 * relRank) * passedScale);
                }
            }
        }
        return score;
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
        pWin = clamp01(pWin);
        pDraw = clamp01(pDraw);
        pLoss = clamp01(pLoss);

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
     * Clamp a value to the [0,1] range.
     *
     * @param v raw value
     * @return clamped value in [0,1]
     */
    private static double clamp01(double v) {
        if (v <= 0.0) {
            return 0.0;
        }
        if (v >= 1.0) {
            return 1.0;
        }
        return v;
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
     * <li>K + (B|N) vs K + (B|N)</li>
     * <li>K + NN vs K</li>
     * </ul>
     *
     * @param board raw 64-square board array
     * @return true if the position is treated as trivially drawn
     */
    private static boolean isInsufficientMaterial(byte[] board) {
        int packedCounts = countMinorPiecesPacked(board);
        return packedCounts != SUFFICIENT_MATERIAL && isTriviallyDrawn(packedCounts);
    }

    /**
     * Sentinel indicating a position has sufficient material to avoid quick draw claims.
     *
     * <p>Used when non-minor material is detected during insufficiency checks.</p>
     */
    private static final int SUFFICIENT_MATERIAL = -1;

    /**
     * Counts minor pieces and packs the result into a 32-bit int.
     *
     * <p>Layout: {@code [blackBishops][blackKnights][whiteBishops][whiteKnights]}.</p>
     *
     * @param board raw 64-square board array
     * @return packed count, or {@link #SUFFICIENT_MATERIAL} when non-minors exist
     */
    private static int countMinorPiecesPacked(byte[] board) {
        int whiteKnights = 0;
        int whiteBishops = 0;
        int blackKnights = 0;
        int blackBishops = 0;

        for (int square = 0; square < board.length; square++) {
            byte piece = board[square];
            int kind = classifyNonKingPiece(piece);
            if (kind == 0) {
                continue;
            }
            if (kind < 0) {
                return SUFFICIENT_MATERIAL;
            }
            if ((kind & 1) != 0) {
                whiteKnights += (kind >>> 1) & 1;
                whiteBishops += (kind >>> 2) & 1;
            } else {
                blackKnights += (kind >>> 1) & 1;
                blackBishops += (kind >>> 2) & 1;
            }
        }

        return whiteKnights | (whiteBishops << 8) | (blackKnights << 16) | (blackBishops << 24);
    }

    /**
     * Classifies a non-king piece for minor-material detection.
     *
     * <p>Returns 0 for empty/king, negative for sufficient material, and a small
     * bit-encoded value for minor pieces.</p>
     *
     * @param piece piece code from {@link Piece}
     * @return classification code for the caller to interpret
     */
    private static int classifyNonKingPiece(byte piece) {
        if (piece == Piece.EMPTY || Piece.isKing(piece)) {
            return 0;
        }
        if (Piece.isPawn(piece) || Piece.isRook(piece) || Piece.isQueen(piece)) {
            return -1;
        }
        if (Piece.isKnight(piece)) {
            return Piece.isWhite(piece) ? 0b0011 : 0b0010;
        }
        if (Piece.isBishop(piece)) {
            return Piece.isWhite(piece) ? 0b0101 : 0b0100;
        }
        // Any other piece type (shouldn't exist) => assume sufficient.
        return -1;
    }

    /**
     * Checks whether a packed minor-piece configuration is a trivial draw.
     *
     * @param packedCounts packed minor piece counts from {@link #countMinorPiecesPacked(byte[])}
     * @return true if the position is treated as insufficient material
     */
    private static boolean isTriviallyDrawn(int packedCounts) {
        int whiteKnights = packedCounts & 0xFF;
        int whiteBishops = (packedCounts >>> 8) & 0xFF;
        int blackKnights = (packedCounts >>> 16) & 0xFF;
        int blackBishops = (packedCounts >>> 24) & 0xFF;

        int whiteMinors = whiteKnights + whiteBishops;
        int blackMinors = blackKnights + blackBishops;

        if (whiteMinors <= 1 && blackMinors <= 1) {
            return true;
        }

        return (whiteKnights == 2 && whiteBishops == 0 && blackMinors == 0)
                || (blackKnights == 2 && blackBishops == 0 && whiteMinors == 0);
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

    /**
     * Internal scratch space used for file-based pawn/rook analysis.
     *
     * <p>
     * Instances are thread-confined via {@link #BUFFERS}. Call {@link #reset()}
     * before use.
     * </p>
     */
    private static final class EvalBuffers {

        /**
         * Number of White pawns found on each file during this scan.
         * {@link #reset()} zeroes all entries before the next evaluation.
         */
        final int[] whitePawnsPerFile = new int[8];

        /**
         * Number of Black pawns found on each file during this scan.
         * {@link #reset()} zeroes all entries before the next evaluation.
         */
        final int[] blackPawnsPerFile = new int[8];

        /**
         * Lowest rank index (0..7) on each file containing a White pawn.
         * Starts at 8 and is clamped downwards as pawns are discovered.
         */
        final int[] minWhitePawnRank = new int[8];

        /**
         * Highest rank index (0..7) on each file containing a Black pawn.
         * Starts at -1 and moves upward as pawns are discovered.
         */
        final int[] maxBlackPawnRank = new int[8];

        /**
         * Lowest rank index (0..7) on each file containing a Black pawn.
         * Starts at 8 and is clamped downwards as pawns are discovered.
         */
        final int[] minBlackPawnRank = new int[8];

        /**
         * Highest rank index (0..7) on each file containing a White pawn.
         * Starts at -1 and moves upward as pawns are discovered.
         */
        final int[] maxWhitePawnRank = new int[8];

        /**
         * Count of White rooks on each file observed during the current scan.
         * Values are reset to zero when {@link #reset()} is called.
         */
        final int[] whiteRooksFileCount = new int[8];

        /**
         * Count of Black rooks on each file observed during the current scan.
         * Values are reset to zero when {@link #reset()} is called.
         */
        final int[] blackRooksFileCount = new int[8];

        /**
         * Transient scan state that accumulates material totals and PST scores.
         * This object is reused across evaluations to avoid allocations.
         */
        final EvalScan scan = new EvalScan();

        /**
         * Estimated game phase between 0.0 (endgame) and 1.0 (opening/middlegame).
         * Reset to 1.0 before each evaluation and dampened as material is collected.
         */
        double phase = 1.0;

        /**
         * Reset all arrays to their sentinel values for a fresh evaluation pass.
         */
        void reset() {
            for (int i = 0; i < 8; i++) {
                whitePawnsPerFile[i] = 0;
                blackPawnsPerFile[i] = 0;
                minWhitePawnRank[i] = 8;
                maxBlackPawnRank[i] = -1;
                minBlackPawnRank[i] = 8;
                maxWhitePawnRank[i] = -1;
                whiteRooksFileCount[i] = 0;
                blackRooksFileCount[i] = 0;
            }
            phase = 1.0;
        }
    }

    /**
     * Accumulates material and PST-derived signals during a board scan.
     */
    private static final class EvalScan {
        /** White material total in centipawns (kings excluded). */
        int whiteMaterial;
        /** Black material total in centipawns (kings excluded). */
        int blackMaterial;
        /** PST-derived score from White's perspective. */
        int score;
        /** Number of White bishops on the board. */
        int whiteBishops;
        /** Number of Black bishops on the board. */
        int blackBishops;
    }
}
