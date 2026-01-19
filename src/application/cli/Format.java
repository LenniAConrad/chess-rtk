package application.cli;

import chess.classical.Wdl;
import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.eval.Backend;
import chess.uci.Chances;
import chess.uci.Evaluation;
import chess.uci.Output;

/**
 * Formatting helpers for CLI output.
 *
 * <p>Provides shared logic for rendering backend labels, move SAN strings, WDL
 * output, and various engine/evaluation metadata across the CLI.
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Format {

	/**
	 * Utility class; prevent instantiation.
	 */
	private Format() {
		// utility
	}

	/**
	 * Formats a human-readable backend label used when displaying evaluator info.
	 *
	 * @param backend engine backend enum
	 * @return label such as {@code LC0 (cuda)} or {@code classical}
	 */
	public static String formatBackendLabel(Backend backend) {
		if (backend == Backend.LC0_CUDA) {
			return "LC0 (cuda)";
		}
		if (backend == Backend.LC0_ROCM) {
			return "LC0 (rocm)";
		}
		if (backend == Backend.LC0_ONEAPI) {
			return "LC0 (oneapi)";
		}
		if (backend == Backend.LC0_CPU) {
			return "LC0 (cpu)";
		}
		return "classical";
	}

	/**
	 * Formats an integer with an explicit sign for CLI output.
	 *
	 * @param value integer value to format
	 * @return string like {@code +123} or {@code -45}
	 */
	public static String formatSigned(int value) {
		return String.format("%+d", value);
	}

	/**
	 * Safely converts a move to SAN, falling back to UCI when SAN cannot be derived.
	 *
	 * @param pos  parent position
	 * @param move move code (UCI-style short)
	 * @return SAN string or UCI fallback on failure
	 */
	public static String safeSan(Position pos, short move) {
		if (move == Move.NO_MOVE) {
			return "-";
		}
		try {
			return SAN.toAlgebraic(pos, move);
		} catch (RuntimeException ex) {
			return Move.toString(move);
		}
	}

	/**
	 * Renders an evaluation (centipawns or mate) as a CLI-friendly string.
	 *
	 * @param eval evaluation object produced by the engine
	 * @return {@code -} when invalid, {@code #N} for mate, or signed centipawns
	 */
	public static String formatEvaluation(Evaluation eval) {
		if (eval == null || !eval.isValid()) {
			return "-";
		}
		if (eval.isMate()) {
			return "#" + eval.getValue();
		}
		return formatSigned(eval.getValue());
	}

	/**
	 * Formats the raw win/draw/loss chances returned by LC0.
	 *
	 * @param chances probabilities object provided by the engine
	 * @return string such as {@code Win:0.42 Draw:0.25 Loss:0.33} or {@code -}
	 */
	public static String formatChances(Chances chances) {
		if (chances == null) {
			return "-";
		}
		return chances.toString();
	}

	/**
	 * Converts an {@link Output.Bound} value into a textual label for CLI logs.
	 *
	 * @param bound bound indicator produced by the engine
	 * @return {@code lower}, {@code upper}, or {@code -} when unbounded
	 */
	public static String formatBound(Output.Bound bound) {
		if (bound == null || bound == Output.Bound.NONE) {
			return "-";
		}
		return bound == Output.Bound.LOWER ? "lower" : "upper";
	}

	/**
	 * Formats a principal variation as a space-delimited sequence of UCI moves.
	 *
	 * @param moves move array returned from the engine
	 * @return PV string or empty string when no moves are present
	 */
	public static String formatPvMoves(short[] moves) {
		if (moves == null || moves.length == 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder(moves.length * 5);
		boolean first = true;
		for (short move : moves) {
			if (move == Move.NO_MOVE) {
				continue;
			}
			if (!first) {
				sb.append(' ');
			}
			sb.append(Move.toString(move));
			first = false;
		}
		return sb.toString();
	}

	/**
	 * Formats a principal variation as a space-delimited sequence of SAN moves.
	 *
	 * <p>Falls back to UCI for remaining moves if the position becomes invalid
	 * while replaying the PV.</p>
	 *
	 * @param pos   starting position for the PV
	 * @param moves move array returned from the engine
	 * @return PV string or empty string when no moves are present
	 */
	public static String formatPvMovesSan(Position pos, short[] moves) {
		if (moves == null || moves.length == 0) {
			return "";
		}
		if (pos == null) {
			return formatPvMoves(moves);
		}
		Position cursor = pos.copyOf();
		StringBuilder sb = new StringBuilder(moves.length * 6);
		boolean first = true;
		boolean canAdvance = true;
		for (short move : moves) {
			if (move == Move.NO_MOVE) {
				continue;
			}
			String token;
			if (canAdvance) {
				token = safeSan(cursor, move);
				try {
					cursor.play(move);
				} catch (RuntimeException ex) {
					canAdvance = false;
				}
			} else {
				token = Move.toString(move);
			}
			if (!first) {
				sb.append(' ');
			}
			sb.append(token);
			first = false;
		}
		return sb.toString();
	}

	/**
	 * Converts a {@link Wdl} distribution into the canonical {@code win draw loss}
	 * triple representation used in CLI outputs.
	 *
	 * @param wdl Win/Draw/Loss counts
	 * @return string such as {@code 40 30 30} or {@code -} if unavailable
	 */
	public static String formatWdl(Wdl wdl) {
		if (wdl == null) {
			return "-";
		}
		return wdl.win() + " " + wdl.draw() + " " + wdl.loss();
	}
}
