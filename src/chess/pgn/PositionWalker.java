package chess.pgn;

import java.util.ArrayList;
import java.util.List;

import chess.core.Position;
import chess.core.SAN;
import chess.struct.Game;

/**
 * Walks a {@link Game}'s mainline and produces one
 * {@link PositionObservation} per ply.
 *
 * <p>Each observation captures the ply index, the resulting FEN, and
 * {@link Position#signatureCore()} so the store's position index can answer
 * "which games passed through this position" lookups in O(N collisions)
 * with a FEN-equality verification step to defeat the FNV-1a collision
 * risk the synthesis explicitly called out.</p>
 *
 * <p>Variation arms are intentionally not walked in this version. The
 * mainline is the high-value path for "find games through this position",
 * and walking only the mainline keeps the position index compact for the
 * first 100k-game-scale workloads. A future expansion can extend the
 * walker without changing the store's on-disk shape: new observations
 * simply append to the same line-oriented sidecar.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PositionWalker {

	/**
	 * Utility class; prevent instantiation.
	 */
	private PositionWalker() {
		// utility
	}

	/**
	 * Walks the mainline and returns one observation per ply, including the
	 * starting position at ply 0.
	 *
	 * @param game source game
	 * @return ordered list of observations
	 */
	public static List<PositionObservation> walkMainline(Game game) {
		List<PositionObservation> out = new ArrayList<>();
		Position cursor = game.getStartPosition() != null
				? game.getStartPosition().copy()
				: new Position(Game.STANDARD_START_FEN);
		int ply = 0;
		out.add(new PositionObservation(ply, cursor.toString(), cursor.signatureCore()));
		Game.Node node = game.getMainline();
		while (node != null) {
			String san = node.getSan();
			if (san == null || san.isBlank()) {
				node = node.getNext();
				continue;
			}
			short move;
			try {
				move = SAN.fromAlgebraic(cursor, san);
			} catch (RuntimeException ex) {
				// An invalid SAN in the mainline halts the walk; partial
				// observations already collected are still useful.
				break;
			}
			cursor = cursor.play(move);
			ply++;
			out.add(new PositionObservation(ply, cursor.toString(), cursor.signatureCore()));
			node = node.getNext();
		}
		return out;
	}

	/**
	 * One ply observation along the walked mainline.
	 *
	 * @param ply           zero-based ply index (0 = starting position)
	 * @param fen           full FEN at the observed position
	 * @param signatureCore {@link Position#signatureCore()} value at the
	 *                      observed position
	 */
	public record PositionObservation(int ply, String fen, long signatureCore) {
	}
}
