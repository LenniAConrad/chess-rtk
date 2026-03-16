package chess.tag.eval;

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
import chess.uci.Evaluation;
import chess.uci.Output;

/**
 * Emits evaluation-derived tags that are quick to detect from the current position.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class EvaluationTagger {

    /**
     * Utility class, prevents instantiation.
     */
    private EvaluationTagger() {
        // utility
    }

    /**
     * Returns tags for immediate tactical evaluation cues and centipawn estimates.
     *
     * <p>
     * Detects immediate mates, stalemates, and emits an evaluation tag based on
     * engine analysis when available (or a local evaluator fallback).
     * </p>
     *
     * @param position position to evaluate
     * @param evaluator evaluator instance to reuse
     * @return immutable list of evaluation tags
     */
    public static List<String> tags(Position position, Evaluator evaluator) {
        return tags(position, evaluator, null);
    }

    /**
     * Returns tags for immediate tactical evaluation cues and centipawn estimates.
     *
     * @param position position to evaluate
     * @param evaluator evaluator instance to reuse
     * @param analysis optional engine analysis (may be null)
     * @return immutable list of evaluation tags
     */
    public static List<String> tags(Position position, Evaluator evaluator, Analysis analysis) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(evaluator, "evaluator");

        List<String> tags = new ArrayList<>(3);
        tags.add("META: to_move=" + (position.isWhiteTurn() ? "white" : "black"));

        if (position.isMate()) {
            tags.add("FACT: status=checkmated");
            addPuzzleTagIfAny(tags, analysis);
            return List.copyOf(tags);
        }

        MoveList moves = position.getMoves();
        if (moves.isEmpty()) {
            if (!position.inCheck()) {
                tags.add("FACT: status=stalemate");
                addPuzzleTagIfAny(tags, analysis);
                return List.copyOf(tags);
            }
            tags.add("FACT: status=checkmated");
            addPuzzleTagIfAny(tags, analysis);
            return List.copyOf(tags);
        }

        Evaluation analysisEval = evaluationFrom(analysis);
        if (analysisEval != null && analysisEval.isMate() && analysisEval.getValue() != 0) {
            int mateValue = analysisEval.getValue();
            tags.add(formatMateMeta(mateValue));
            addPuzzleTagIfAny(tags, analysis);
            return List.copyOf(tags);
        }

        for (int i = 0; i < moves.size(); i++) {
            Position next = position.copyOf().play(moves.get(i));
            if (next.isMate()) {
                tags.add("META: mate_in=1");
                addPuzzleTagIfAny(tags, analysis);
                return List.copyOf(tags);
            }
        }
        Integer cp = evalCentipawns(evaluator, position, analysisEval);
        if (cp != null) {
            tags.add("META: eval_cp=" + formatSigned(cp));
        }
        addPuzzleTagIfAny(tags, analysis);
        return List.copyOf(tags);
    }

    private static Evaluation evaluationFrom(Analysis analysis) {
        if (analysis == null) {
            return null;
        }
        Output output = analysis.getBestOutput();
        if (output == null) {
            return null;
        }
        Evaluation evaluation = output.getEvaluation();
        if (evaluation == null || !evaluation.isValid()) {
            return null;
        }
        return evaluation;
    }

    private static Integer evalCentipawns(Evaluator evaluator, Position position, Evaluation analysisEval) {
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

    private static String formatMateMeta(int mateMoves) {
        int moves = Math.abs(mateMoves);
        if (mateMoves > 0) {
            return "META: mate_in=" + moves;
        }
        return "META: mated_in=" + moves;
    }

    private static String formatSigned(int value) {
        return Integer.toString(value);
    }

    private static void addPuzzleTagIfAny(List<String> tags, Analysis analysis) {
        String puzzleTag = puzzleTagFromFilters(analysis);
        if (puzzleTag != null) {
            tags.add(puzzleTag);
        }
    }

    private static String puzzleTagFromFilters(Analysis analysis) {
        if (analysis == null || analysis.isEmpty()) {
            return null;
        }
        if (!Config.getPuzzleQuality().apply(analysis)) {
            return null;
        }
        if (Config.getPuzzleWinning().apply(analysis)) {
            return "FACT: puzzle=winning";
        }
        if (Config.getPuzzleDrawing().apply(analysis)) {
            return "FACT: puzzle=draw";
        }
        return null;
    }
}
