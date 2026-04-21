package application.gui.model;

import chess.uci.Analysis;
import chess.uci.Chances;
import chess.uci.Evaluation;

/**
 * Cached engine output for identical positions/config.
 *
 * Keeps the raw UCI output along with parsed best moves, evaluation, chances, and analysis metadata so repeated requests can reuse prior work while still exposing the structured data consumers expect.
 *
 * @param output raw engine output text.
 * @param bestMoves best-move string returned by the engine.
 * @param eval parsed evaluation object.
 * @param chances win/draw/loss chances object.
 * @param analysis analysis metadata provided by the engine.
 * @param bestMove numeric best-move move id.
  * @since 2026
  * @author Lennart A. Conrad
 */
public record EngineCacheEntry(
	/**
	 * Stores the output.
	 */
	String output,
	/**
	 * Stores the best moves.
	 */
	String bestMoves,
	/**
	 * Stores the eval.
	 */
	Evaluation eval,
	/**
	 * Stores the chances.
	 */
	Chances chances,
	/**
	 * Stores the analysis.
	 */
	Analysis analysis,
	/**
	 * Stores the best move.
	 */
	short bestMove
) {
}
