package application.gui.history.text;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import application.cli.Format;
import chess.core.Move;
import chess.core.Position;
import chess.struct.Game;
import chess.uci.Evaluation;
import chess.uci.Output;

/**
 * Text and labeling helpers for PGN/history/eval related UI strings.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class HistoryTextSupport {

	/**
	 * HistoryTextSupport method.
	 */
	private HistoryTextSupport() {
	}

	/**
	 * formatEvalLabel method.
	 *
	 * @param value parameter.
	 * @param mate parameter.
	 * @return return value.
	 */
	public static String formatEvalLabel(int value, boolean mate) {
		if (mate) {
			return "#" + value;
		}
		return String.format("%+.2f", value / 100.0);
	}

	/**
	 * formatEvalLabelForWhite method.
	 *
	 * @param eval parameter.
	 * @param whiteTurn parameter.
	 * @return return value.
	 */
	public static String formatEvalLabelForWhite(Evaluation eval, boolean whiteTurn) {
		if (eval == null || !eval.isValid()) {
			return "";
		}
		int value = eval.getValue();
		if (!whiteTurn) {
			value = -value;
		}
		return formatEvalLabel(value, eval.isMate());
	}

	/**
	 * buildPvSnippet method.
	 *
	 * @param out parameter.
	 * @param maxMoves parameter.
	 * @param base parameter.
	 * @param sanFormatter parameter.
	 * @return return value.
	 */
	public static String buildPvSnippet(Output out, int maxMoves, Position base, Function<String, String> sanFormatter) {
		if (out == null || maxMoves <= 0 || base == null) {
			return "";
		}
		short[] moves = out.getMoves();
		if (moves == null || moves.length == 0) {
			return "";
		}
		Position cursor = base.copy();
		StringBuilder sb = new StringBuilder();
		int count = 0;
		for (short move : moves) {
			if (move == Move.NO_MOVE) {
				break;
			}
			if (!cursor.isLegalMove(move)) {
				break;
			}
			String san = Format.safeSan(cursor, move);
			if (san == null || san.isBlank()) {
				break;
			}
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(sanFormatter != null ? sanFormatter.apply(san) : san);
			cursor = cursor.copy().play(move);
			count++;
			if (count >= maxMoves) {
				break;
			}
		}
		return sb.toString();
	}

	/**
	 * looksLikePgn method.
	 *
	 * @param text parameter.
	 * @return return value.
	 */
	public static boolean looksLikePgn(String text) {
		if (text == null) {
			return false;
		}
		String upper = text.toUpperCase();
		return upper.contains("[EVENT") || upper.contains("[SITE") || upper.contains("[DATE")
				|| upper.contains("1.") && upper.contains("2.");
	}

	/**
	 * pgnGameLabel method.
	 *
	 * @param game parameter.
	 * @param index parameter.
	 * @return return value.
	 */
	public static String pgnGameLabel(Game game, int index) {
		Map<String, String> tags = game.getTags();
		String white = tags.getOrDefault("White", "").trim();
		String black = tags.getOrDefault("Black", "").trim();
		String event = tags.getOrDefault("Event", "").trim();
		String result = tags.getOrDefault("Result", "").trim();
		StringBuilder sb = new StringBuilder("Game ").append(index);
		if (!white.isEmpty() || !black.isEmpty()) {
			sb.append(": ").append(white.isEmpty() ? "?" : white)
					.append(" vs ")
					.append(black.isEmpty() ? "?" : black);
		}
		if (!event.isEmpty()) {
			sb.append(" (").append(event).append(")");
		}
		if (!result.isEmpty()) {
			sb.append(" ").append(result);
		}
		return sb.toString();
	}

	/**
	 * buildPgnLabel method.
	 *
	 * @param game parameter.
	 * @param plyCount parameter.
	 * @return return value.
	 */
	public static String buildPgnLabel(Game game, int plyCount) {
		Map<String, String> tags = game.getTags();
		String white = tags.getOrDefault("White", "").trim();
		String black = tags.getOrDefault("Black", "").trim();
		String result = tags.getOrDefault("Result", "").trim();
		StringBuilder sb = new StringBuilder("PGN");
		if (!white.isEmpty() || !black.isEmpty()) {
			sb.append(": ").append(white.isEmpty() ? "?" : white)
					.append(" vs ")
					.append(black.isEmpty() ? "?" : black);
		}
		if (!result.isEmpty()) {
			sb.append(" ").append(result);
		}
		if (plyCount > 0) {
			sb.append(" (").append(plyCount).append(" plies)");
		}
		return sb.toString();
	}

	/**
	 * joinComments method.
	 *
	 * @param comments parameter.
	 * @return return value.
	 */
	public static String joinComments(List<String> comments) {
		if (comments == null || comments.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (String comment : comments) {
			if (comment == null || comment.isBlank()) {
				continue;
			}
			if (!sb.isEmpty()) {
				sb.append('\n');
			}
			sb.append(comment.trim());
		}
		return sb.isEmpty() ? null : sb.toString();
	}

	/**
	 * pickNag method.
	 *
	 * @param nags parameter.
	 * @return return value.
	 */
	public static int pickNag(List<Integer> nags) {
		if (nags == null || nags.isEmpty()) {
			return 0;
		}
		for (Integer nag : nags) {
			if (nag == null) {
				continue;
			}
			int code = nag.intValue();
			if (code >= 1 && code <= 6) {
				return code;
			}
		}
		return 0;
	}

	/**
	 * extractFen method.
	 *
	 * @param line parameter.
	 * @return return value.
	 */
	public static String extractFen(String line) {
		if (line == null) {
			return null;
		}
		String trimmed = line.replace('\t', ' ').trim();
		if (trimmed.isEmpty() || trimmed.startsWith("#")) {
			return null;
		}
		int hash = trimmed.indexOf('#');
		if (hash >= 0) {
			trimmed = trimmed.substring(0, hash).trim();
		}
		int semi = trimmed.indexOf(';');
		if (semi >= 0) {
			trimmed = trimmed.substring(0, semi).trim();
		}
		if (trimmed.isEmpty()) {
			return null;
		}
		String[] tokens = trimmed.split("\\s+");
		if (tokens.length < 4) {
			return null;
		}
		int take = Math.min(tokens.length, 6);
		return String.join(" ", Arrays.copyOf(tokens, take));
	}

	/**
	 * nagGlyphFromCode method.
	 *
	 * @param nag parameter.
	 * @return return value.
	 */
	public static String nagGlyphFromCode(int nag) {
		switch (nag) {
			case 1:
				return "!";
			case 2:
				return "?";
			case 3:
				return "!!";
			case 4:
				return "??";
			case 5:
				return "!?";
			case 6:
				return "?!";
			default:
				return "";
		}
	}
}
