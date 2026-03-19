package chess.tag.eval;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import application.Config;
import chess.classical.Wdl;
import chess.core.MoveList;
import chess.core.Position;
import chess.eval.Evaluator;
import chess.eval.Result;
import chess.uci.Analysis;
import chess.uci.Output;

/**
 * Produces a compact evaluation-oriented tag set for a position.
 * <p>
 * This helper focuses on side to move, terminal status, mate distance,
 * centipawn evaluation, and optional puzzle classification.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Evaluation {

    /**
     * Prevents instantiation of this utility class.
     */
    private Evaluation() {
        // utility
    }

    /**
     * Builds evaluation tags for a position using no engine analysis.
     *
     * @param position the position to inspect
     * @param evaluator the evaluator used for fallback centipawn scoring
     * @return an immutable list of evaluation tags
     * @throws NullPointerException if {@code position} or {@code evaluator} is {@code null}
     */
    public static List<String> tags(Position position, Evaluator evaluator) {
        return tags(position, evaluator, null);
    }

    /**
     * Builds evaluation tags for a position with optional engine analysis.
     * <p>
     * The method emits early-exit tags for mate and stalemate, otherwise it
     * falls back to centipawn evaluation and optional puzzle labels.
     * </p>
     *
     * @param position the position to inspect
     * @param evaluator the evaluator used for fallback centipawn scoring
     * @param analysis optional engine analysis used for mate and puzzle metadata
     * @return an immutable list of evaluation tags
     * @throws NullPointerException if {@code position} or {@code evaluator} is {@code null}
     */
    public static List<String> tags(Position position, Evaluator evaluator, Analysis analysis) {
        Objects.requireNonNull(position, POSITION);
        Objects.requireNonNull(evaluator, EVALUATOR);

        List<String> tags = new ArrayList<>(3);
        tags.add(META_TO_MOVE_PREFIX + (position.isWhiteTurn() ? WHITE : BLACK));

        if (position.isMate()) {
            tags.add(STATUS_PREFIX + CHECKMATED);
            addPuzzleTagIfAny(tags, analysis);
            return List.copyOf(tags);
        }

        MoveList moves = position.getMoves();
        if (moves.isEmpty()) {
            if (!position.inCheck()) {
                tags.add(STATUS_PREFIX + STALEMATE);
                addPuzzleTagIfAny(tags, analysis);
                return List.copyOf(tags);
            }
            tags.add(STATUS_PREFIX + CHECKMATED);
            addPuzzleTagIfAny(tags, analysis);
            return List.copyOf(tags);
        }

        chess.uci.Evaluation analysisEval = evaluationFrom(analysis);
        if (analysisEval != null && analysisEval.isMate() && analysisEval.getValue() != 0) {
            int mateValue = analysisEval.getValue();
            tags.add(formatMateMeta(mateValue));
            addPuzzleTagIfAny(tags, analysis);
            return List.copyOf(tags);
        }

        for (int i = 0; i < moves.size(); i++) {
            Position next = position.copyOf().play(moves.get(i));
            if (next.isMate()) {
                tags.add(META_MATE_IN_PREFIX + 1);
                addPuzzleTagIfAny(tags, analysis);
                return List.copyOf(tags);
            }
        }
        Integer cp = evalCentipawns(evaluator, position, analysisEval);
        if (cp != null) {
            tags.add(META_EVAL_CP_PREFIX + formatSigned(cp));
        }
        addPuzzleTagIfAny(tags, analysis);
        return List.copyOf(tags);
    }

    /**
     * Extracts a valid evaluation from analysis output when available.
     *
     * @param analysis optional engine analysis
     * @return the valid engine evaluation, or {@code null} if unavailable
     */
    private static chess.uci.Evaluation evaluationFrom(Analysis analysis) {
        if (analysis == null) {
            return null;
        }
        Output output = analysis.getBestOutput();
        if (output == null) {
            return null;
        }
        chess.uci.Evaluation evaluation = output.getEvaluation();
        if (evaluation == null || !evaluation.isValid()) {
            return null;
        }
        return evaluation;
    }

    /**
     * Resolves a centipawn score using analysis, the evaluator, or a fallback heuristic.
     *
     * @param evaluator the evaluator used as the primary fallback
     * @param position the position being evaluated
     * @param analysisEval the analysis evaluation, if present
     * @return a centipawn score for the position
     */
    private static Integer evalCentipawns(Evaluator evaluator, Position position, chess.uci.Evaluation analysisEval) {
        Integer cp = null;
        if (analysisEval != null && !analysisEval.isMate()) {
            cp = analysisEval.getValue();
        }
        if (cp == null) {
            Result result = evaluator.evaluate(position);
            cp = result.centipawns();
        }
        if (cp == null) {
            cp = Wdl.evaluateStmCentipawns(position);
        }
        return cp;
    }

    /**
     * Formats a mate score as a meta tag value.
     *
     * @param mateMoves the mate score in plies or moves, depending on the source
     * @return the serialized mate tag suffix
     */
    private static String formatMateMeta(int mateMoves) {
        int moves = Math.abs(mateMoves);
        if (mateMoves > 0) {
            return META_MATE_IN_PREFIX + moves;
        }
        return META_MATED_IN_PREFIX + moves;
    }

    /**
     * Converts a signed centipawn value to text.
     *
     * @param value the centipawn value to format
     * @return the signed centipawn text
     */
    private static String formatSigned(int value) {
        return Integer.toString(value);
    }

    /**
     * Adds a puzzle tag when the current analysis qualifies as a puzzle.
     *
     * @param tags the mutable tag accumulator
     * @param analysis optional engine analysis
     */
    private static void addPuzzleTagIfAny(List<String> tags, Analysis analysis) {
        String puzzleTag = puzzleTagFromFilters(analysis);
        if (puzzleTag != null) {
            tags.add(puzzleTag);
        }
    }

    /**
     * Derives a puzzle classification tag from configured filters.
     *
     * @param analysis optional engine analysis
     * @return the puzzle tag, or {@code null} if the analysis does not qualify
     */
    private static String puzzleTagFromFilters(Analysis analysis) {
        if (analysis == null || analysis.isEmpty()) {
            return null;
        }
        if (!Config.getPuzzleQuality().apply(analysis)) {
            return null;
        }
        if (Config.getPuzzleWinning().apply(analysis)) {
            return FACT_PUZZLE_WINNING;
        }
        if (Config.getPuzzleDrawing().apply(analysis)) {
            return FACT_PUZZLE_DRAW;
        }
        return null;
    }
}
