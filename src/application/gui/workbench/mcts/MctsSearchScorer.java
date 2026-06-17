package application.gui.workbench.mcts;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.eval.See;

/**
 * Leaf evaluation and handcrafted move-prior scoring for Workbench MCTS.
 */
final class MctsSearchScorer {

    /**
     * Handcrafted prior bonus for moves that give check.
     */
    private static final int CHECK_PRIOR_BONUS = 4_000;

    /**
     * Handcrafted prior penalty for captures that lose material by SEE.
     */
    private static final int LOSING_CAPTURE_PRIOR_PENALTY = 8_000;

    /**
     * Scale applied to positive SEE values for winning-capture priors.
     */
    private static final int WINNING_CAPTURE_PRIOR_SCALE = 8;

    /**
     * Maximum forcing plies searched when valuing tactically unstable leaves.
     */
    private static final int QUIESCENCE_MAX_PLY = 4;

    /**
     * Maximum non-evasion forcing moves searched at one quiescence node.
     */
    private static final int QUIESCENCE_MAX_MOVES = 12;

    /**
     * Policy/value backend used for leaf values and move priors.
     */
    private final SearchBackend backend;

    MctsSearchScorer(SearchBackend backend) {
        this.backend = backend;
    }

    /**
     * Evaluates a standalone position from the side-to-move perspective.
     *
     * @param position position to evaluate
     * @return structured evaluation
     */
    SearchEvaluation evaluate(Position position) {
        MoveList legal = position.legalMoves();
        if (legal.isEmpty()) {
            return position.inCheck() ? SearchEvaluation.loss() : SearchEvaluation.draw();
        }
        if (isStaticDraw(position)) {
            return SearchEvaluation.draw();
        }
        return quiescence(position, 0, -1.0, 1.0);
    }

    /**
     * Builds normalized move priors.
     *
     * @param position position to evaluate
     * @param moves legal moves
     * @param childPositions child positions reached by {@code moves}
     * @return normalized priors
     */
    double[] priors(Position position, short[] moves, Position[] childPositions) {
        int[] scores = new int[moves.length];
        backend.prepareMoveOrdering(position);
        backend.scoreMoves(position, moves, scores);
        double max = -Double.MAX_VALUE;
        double[] fallback = new double[moves.length];
        for (int i = 0; i < moves.length; i++) {
            fallback[i] = (scores[i] + tacticalPrior(position, moves[i], childPositions[i])) / 18_000.0;
            if (fallback[i] > max) {
                max = fallback[i];
            }
        }
        double sum = 0.0;
        for (int i = 0; i < fallback.length; i++) {
            fallback[i] = Math.exp(fallback[i] - max);
            sum += fallback[i];
        }
        if (!Double.isFinite(sum) || sum <= 0.0) {
            double uniform = 1.0 / Math.max(1, moves.length);
            for (int i = 0; i < fallback.length; i++) {
                fallback[i] = uniform;
            }
            return backend.priors(position, moves, fallback);
        }
        for (int i = 0; i < fallback.length; i++) {
            fallback[i] /= sum;
        }
        return backend.priors(position, moves, fallback);
    }

    /**
     * Returns whether the position is terminal by workbench search draw rules.
     *
     * @param position position to inspect
     * @return true for automatic static draws
     */
    static boolean isStaticDraw(Position position) {
        return position.halfMoveClock() >= 100 || position.isInsufficientMaterial();
    }

    /**
     * Values a leaf after resolving immediate mates, captures/promotions, and
     * forced check evasions. The returned value is from the side-to-move
     * perspective of {@code position}.
     *
     * @param position position to evaluate
     * @param qply quiescence ply
     * @param alpha alpha bound
     * @param beta beta bound
     * @return quiescence evaluation
     */
    private SearchEvaluation quiescence(Position position, int qply, double alpha, double beta) {
        if (isStaticDraw(position)) {
            return SearchEvaluation.draw();
        }
        MoveList legal = position.legalMoves();
        if (legal.isEmpty()) {
            return position.inCheck() ? SearchEvaluation.loss() : SearchEvaluation.draw();
        }
        if (qply == 0 && findMateInOne(position, legal) != Move.NO_MOVE) {
            return SearchEvaluation.win();
        }

        boolean inCheck = position.inCheck();
        if (qply >= QUIESCENCE_MAX_PLY) {
            return backend.evaluate(position);
        }

        SearchEvaluation best = inCheck ? SearchEvaluation.loss() : backend.evaluate(position);
        double currentAlpha = Math.max(alpha, best.value());
        if (!inCheck && best.value() >= beta) {
            return best;
        }
        short[] moves = quiescenceMoves(position, legal, inCheck);
        for (short move : moves) {
            Position.State state = new Position.State();
            position.play(move, state);
            try {
                SearchEvaluation value = quiescence(position, qply + 1, -beta, -currentAlpha).flipped();
                if (value.value() >= beta) {
                    return value;
                }
                if (value.value() > best.value()) {
                    best = value;
                    currentAlpha = Math.max(currentAlpha, value.value());
                }
            } finally {
                position.undo(move, state);
            }
        }
        return best;
    }

    private static short[] quiescenceMoves(Position position, MoveList legal, boolean inCheck) {
        if (inCheck) {
            return legal.toArray();
        }
        short[] moves = new short[legal.size()];
        int[] scores = new int[legal.size()];
        int count = 0;
        for (int i = 0; i < legal.size(); i++) {
            short move = legal.raw(i);
            if (isQuiescenceMove(position, move)) {
                moves[count] = move;
                scores[count] = quiescenceMoveScore(position, move);
                count++;
            }
        }
        insertionSortDescending(moves, scores, count);
        int outCount = Math.min(count, QUIESCENCE_MAX_MOVES);
        short[] filtered = new short[outCount];
        if (outCount > 0) {
            System.arraycopy(moves, 0, filtered, 0, outCount);
        }
        return filtered;
    }

    private static short findMateInOne(Position position, MoveList legal) {
        for (int i = 0; i < legal.size(); i++) {
            short move = legal.raw(i);
            Position.State state = new Position.State();
            position.play(move, state);
            try {
                if (position.inCheck() && position.legalMoves().isEmpty()) {
                    return move;
                }
            } finally {
                position.undo(move, state);
            }
        }
        return Move.NO_MOVE;
    }

    private static boolean isQuiescenceMove(Position position, short move) {
        return position.isCapture(move) || position.isPromotion(move);
    }

    private static int quiescenceMoveScore(Position position, short move) {
        int score = promotionValue(Move.getPromotion(move)) * 100;
        int captured = position.capturedPiece(move);
        if (captured >= 0) {
            score += Piece.getValue((byte) captured) * 100;
            score -= Piece.getValue(position.pieceAt(Move.getFromIndex(move)));
        }
        return score;
    }

    private static void insertionSortDescending(short[] moves, int[] scores, int count) {
        for (int i = 1; i < count; i++) {
            short move = moves[i];
            int score = scores[i];
            int j = i - 1;
            while (j >= 0 && scores[j] < score) {
                moves[j + 1] = moves[j];
                scores[j + 1] = scores[j];
                j--;
            }
            moves[j + 1] = move;
            scores[j + 1] = score;
        }
    }

    private static int tacticalPrior(Position position, short move, Position childPosition) {
        int bonus = 0;
        byte fromPiece = position.pieceAt(Move.getFromIndex(move));
        byte captured = position.pieceAt(Move.getToIndex(move));
        if (captured != Piece.EMPTY) {
            bonus += Piece.getValue(captured) * 40;
            bonus -= Piece.getValue(fromPiece) * 6;
            int see = See.see(position, move);
            if (see < 0) {
                bonus -= LOSING_CAPTURE_PRIOR_PENALTY;
            } else if (see > 0) {
                bonus += Math.min(900, see) * WINNING_CAPTURE_PRIOR_SCALE;
            }
        }
        if (position.isPromotion(move)) {
            bonus += promotionValue(Move.getPromotion(move)) * 35;
        }
        if (position.isCastle(move)) {
            bonus += 12_000;
        }
        if (childPosition.inCheck()) {
            bonus += CHECK_PRIOR_BONUS;
        }
        return bonus;
    }

    private static int promotionValue(int promotion) {
        return switch (promotion) {
            case 1 -> Piece.VALUE_KNIGHT;
            case 2 -> Piece.VALUE_BISHOP;
            case 3 -> Piece.VALUE_ROOK;
            case 4 -> Piece.VALUE_QUEEN;
            default -> 0;
        };
    }
}
