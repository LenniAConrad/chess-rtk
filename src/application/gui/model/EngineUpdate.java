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
public record EngineUpdate(long version, String output, String bestMoves, Evaluation eval, Chances chances, Analysis analysis,
		short bestMove, String status, boolean error) {
}
