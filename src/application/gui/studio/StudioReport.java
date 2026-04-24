package application.gui.studio;

import java.util.List;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;

/**
 * Builds lightweight copy/export reports for the current position.
 */
public final class StudioReport {

	/**
	 * Utility constructor.
	 */
	private StudioReport() {
		// utility
	}

	/**
	 * Creates a text report.
	 *
	 * @param position position
	 * @param tags tags
	 * @param engine latest engine snapshot
	 * @param note position note
	 * @return report text
	 */
	public static String currentPosition(Position position, List<String> tags,
			StudioEngineSnapshot engine, String note) {
		StringBuilder sb = new StringBuilder();
		sb.append("FEN: ").append(position).append(System.lineSeparator());
		sb.append("Side: ").append(position.isWhiteToMove() ? "White" : "Black").append(" to move")
				.append(System.lineSeparator());
		sb.append("Legal moves:").append(System.lineSeparator());
		MoveList moves = position.legalMoves();
		for (int i = 0; i < moves.size(); i++) {
			short move = moves.get(i);
			sb.append("  ").append(Move.toString(move)).append('\t').append(SAN.toAlgebraic(position, move))
					.append(System.lineSeparator());
		}
		sb.append("Tags: ").append(tags == null || tags.isEmpty() ? "(none)" : String.join(", ", tags))
				.append(System.lineSeparator());
		if (engine != null) {
			sb.append("Engine: ").append(engine.eval()).append(" depth ").append(engine.depth())
					.append(" pv ").append(engine.pv()).append(System.lineSeparator());
		}
		if (note != null && !note.isBlank()) {
			sb.append("Note: ").append(note).append(System.lineSeparator());
		}
		return sb.toString();
	}
}
