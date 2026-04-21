package application.gui.model;

import chess.uci.Analysis;
import chess.uci.Chances;
import chess.uci.Evaluation;

/**
 * Batched engine update emitted by the worker.
 *
 * Captures versioning, textual output, best move data, and status/error flags so the GUI can refresh the PV list, eval strip, and background indicators in one shot.
 *
 * @param version update sequence version.
 * @param output raw textual output.
 * @param bestMoves comma-separated best moves string.
 * @param eval evaluation metadata.
 * @param chances W/D/L chance metadata.
 * @param analysis additional analysis metadata.
 * @param bestMove numeric best move index.
 * @param status current engine status text.
 * @param error whether this update signals an error.
  * @since 2026
  * @author Lennart A. Conrad
 */
public record EngineUpdate(
	/**
	 * Stores the version.
	 */
	long version,
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
	short bestMove,
	/**
	 * Stores the status.
	 */
	String status,
	/**
	 * Stores the error.
	 */
	boolean error
) {
}
