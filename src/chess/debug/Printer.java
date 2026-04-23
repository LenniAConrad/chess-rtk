package chess.debug;

import java.io.PrintStream;

import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;

/**
 * Console helper for fast, human-readable debugging of chess positions.
 *
 * <ul>
 * <li>{@link #board(Position)} – prints a UTF-8 board plus FEN, clocks,
 * castling rights,
 * checkers, king squares and a wrapped SAN move list.</li>
 * <li>{@link #perft(Position,int)} – runs per-move PERFT at a given depth,
 * reporting nodes,
 * time, NPS and share of total.</li>
 * <li>{@link #testPerft()} – validates PERFT(6) against known reference
 * positions.</li>
 * </ul>
 *
 * <p>
 * Stateless utility; all methods are {@code static}. Output is written to
 * {@code System.out}, so concurrent calls will interleave.
 * </p>
 *
 * @author Lennart A. Conrad
 * @since 2025
 */
public class Printer {

	/**
	 * Private constructor to prevent instantiation of this class.
	 */
	private Printer() {
		// Prevent instantiation
	}

	/**
	 * Used for printing the chess board and metadata to standard output. Delegates
	 * to the
	 * {@link #board(Position, java.io.PrintStream)} overload.
	 *
	 * @param position the position to render
	 */
	public static void board(Position position) {
		board(position, System.out);
	}

	/**
	 * Used for printing the chess board and metadata including FEN, side to move,
	 * en passant square,
	 * move clocks, castling rights, checkers, Chess960 status, king positions, and
	 * moves list with line
	 * breaks for long move lists to the provided {@link java.io.PrintStream}.
	 *
	 * @param position the position to render
	 * @param out      the print stream to receive the output
	 */
	public static void board(Position position, PrintStream out) {
		String[] boardLines = buildBoardLines(position);
		java.util.List<String> metaLines = buildMetaLines(position);
		int totalLines = Math.max(boardLines.length, metaLines.size());
		for (int i = 0; i < totalLines; i++) {
			String boardLine = (i < boardLines.length) ? boardLines[i] : "";
			String metaLine = (i < metaLines.size()) ? metaLines.get(i) : "";
			out.printf("%-35s%s%n", boardLine, metaLine);
		}
		out.flush();
	}

	/**
	 * Used for constructing an array of text lines representing the board grid.
	 *
	 * @param position the position whose board to build
	 * @return an array of strings, one per display line
	 */
	private static String[] buildBoardLines(Position position) {
		final String topBorder = "┌───┬───┬───┬───┬───┬───┬───┬───┐";
		final String middleBorder = "├───┼───┼───┼───┼───┼───┼───┼───┤";
		final String bottomBorder = "└───┴───┴───┴───┴───┴───┴───┴───┘";

		String[] lines = new String[18];
		lines[0] = topBorder;
		byte[] board = position.getBoard();
		for (int rank = 0; rank < 8; rank++) {
			StringBuilder row = new StringBuilder("│");
			for (int file = 0; file < 8; file++) {
				int index = rank * 8 + file;
				row.append(' ').append(Piece.toLowerCaseChar(board[index])).append(" │");
			}
			row.append(' ').append(8 - rank);
			lines[1 + rank * 2] = row.toString();
			lines[2 + rank * 2] = (rank == 7) ? bottomBorder : middleBorder;
		}
		lines[17] = "  a   b   c   d   e   f   g   h";
		return lines;
	}

	/**
	 * Used for assembling a list of human-readable meta lines that describe the
	 * current {@code position}. Refactored to reduce complexity by extracting
	 * helper methods for repetitive tasks.
	 *
	 * @param position the position whose meta data should be rendered
	 * @return a list of left-padded strings, one entry per line
	 */
	private static java.util.List<String> buildMetaLines(Position position) {
		java.util.List<String> metaLines = new java.util.ArrayList<>();

		addMeta(metaLines, "Fen", position.toString());
		addMeta(metaLines, "Side to move", position.isWhiteToMove() ? "White" : "Black");
		addMeta(metaLines, "En passant", Field.toString(position.enPassantSquare()));
		addMeta(metaLines, "Half move clock", Integer.toString(position.halfMoveClock()));
		addMeta(metaLines, "Full move number", Integer.toString(position.fullMoveNumber()));

		String whiteCast = buildCastlingRights(position, true);
		String blackCast = buildCastlingRights(position, false);
		addMeta(metaLines, "White castling", whiteCast);
		addMeta(metaLines, "Black castling", blackCast);

		addMeta(metaLines, "Checkers", buildCheckersString(position));
		addMeta(metaLines, "Chess960", Boolean.toString(position.isChess960()));
		addMeta(metaLines, "White King", Field.toString(position.kingSquare(true)));
		addMeta(metaLines, "Black King", Field.toString(position.kingSquare(false)));

		addMoveLines(position, metaLines);
		return metaLines;
	}

	/**
	 * Used for adding a metadata line with consistent padding.
	 *
	 * @param metaLines the list to add to
	 * @param label     the metadata label
	 * @param value     the metadata value
	 */
	private static void addMeta(java.util.List<String> metaLines,
			String label,
			String value) {
		metaLines.add(String.format("    %-17s %s", label + ":", value));
	}

	/**
	 * Used for building a castling‐rights string for the given position and color.
	 *
	 * @param position the position object
	 * @param white    true for White rights, false for Black rights
	 * @return the castling rights string ("-" if none)
	 */
	private static String buildCastlingRights(Position position, boolean white) {
		if (!position.isChess960()) {
			return buildStandardCastlingRights(position, white);
		}
		return buildChess960CastlingRights(position, white);
	}

	/**
	 * Used for building standard (non‑Chess960) castling rights.
	 *
	 * @param position the position object
	 * @param white    true for White rights, false for Black rights
	 * @return the castling rights string ("-" if none)
	 */
	private static String buildStandardCastlingRights(Position position, boolean white) {
		StringBuilder sb = new StringBuilder();
		if (white) {
			sb.append(position.activeCastlingMoveTarget(Position.WHITE_KINGSIDE) != Field.NO_SQUARE ? "K" : "");
			sb.append(position.activeCastlingMoveTarget(Position.WHITE_QUEENSIDE) != Field.NO_SQUARE ? "Q" : "-");
		} else {
			sb.append(position.activeCastlingMoveTarget(Position.BLACK_KINGSIDE) != Field.NO_SQUARE ? "k" : "");
			sb.append(position.activeCastlingMoveTarget(Position.BLACK_QUEENSIDE) != Field.NO_SQUARE ? "q" : "-");
		}
		String result = sb.toString();
		return result.isEmpty() ? "-" : result;
	}

	/**
	 * Used for building Chess960 castling rights by appending file letters.
	 *
	 * @param position the position object
	 * @param white    true for White rights (uppercase), false for Black rights
	 * @return the castling rights string ("-" if none)
	 */
	private static String buildChess960CastlingRights(Position position, boolean white) {
		StringBuilder sb = new StringBuilder();
		if (white) {
			appendFileIfSquare(position.activeCastlingMoveTarget(Position.WHITE_KINGSIDE), sb, true);
			appendFileIfSquare(position.activeCastlingMoveTarget(Position.WHITE_QUEENSIDE), sb, true);
		} else {
			appendFileIfSquare(position.activeCastlingMoveTarget(Position.BLACK_KINGSIDE), sb, false);
			appendFileIfSquare(position.activeCastlingMoveTarget(Position.BLACK_QUEENSIDE), sb, false);
		}
		String result = sb.toString();
		return result.isEmpty() ? "-" : result;
	}

	/**
	 * Used for appending the file letter of a square if it is set.
	 *
	 * @param square    the square index or Field.NO_SQUARE
	 * @param sb        the StringBuilder to append to
	 * @param uppercase true to use uppercase file letter, false for lowercase
	 */
	private static void appendFileIfSquare(byte square, StringBuilder sb, boolean uppercase) {
		if (square == Field.NO_SQUARE) {
			return;
		}
		if (uppercase) {
			sb.append(Field.getFileUppercase(square));
		} else {
			sb.append(Field.getFile(square));
		}
	}

	/**
	 * Used for converting the array of checking pieces to a space-separated list.
	 *
	 * @param position the position whose checkers to list
	 * @return a string of checker square names or "-" if none
	 */
	private static String buildCheckersString(Position position) {
		byte[] checkersArray = position.getCheckers();
		if (checkersArray.length == 0) {
			return "-";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < checkersArray.length; i++) {
			sb.append(Field.toString(checkersArray[i]));
			if (i < checkersArray.length - 1) {
				sb.append(' ');
			}
		}
		return sb.toString();
	}

	/**
	 * Used for formatting the move list into wrapped metadata lines.
	 *
	 * @param position  the position whose moves to render
	 * @param metaLines the metadata lines list to append to
	 */
	private static void addMoveLines(Position position, java.util.List<String> metaLines) {
		MoveList moves = position.legalMoves();
		StringBuilder allMoves = new StringBuilder();
		for (int i = 0; i < moves.size(); i++) {
			if (i > 0) {
				allMoves.append(' ');
			}
			allMoves.append(SAN.toAlgebraic(position, moves.get(i)));
		}
		String indent = "                     ";
		int maxLen = 90 + indent.length();
		String[] moveArray = allMoves.toString().split(" ");
		StringBuilder lineBuilder = new StringBuilder();
		lineBuilder.append("    Moves:           ");
		for (String mv : moveArray) {
			if (lineBuilder.length() + mv.length() + 1 > maxLen) {
				metaLines.add(lineBuilder.toString());
				lineBuilder = new StringBuilder().append(indent);
			}
			if (!lineBuilder.isEmpty()) {
				lineBuilder.append(' ');
			}
			lineBuilder.append(mv);
		}
		if (!lineBuilder.isEmpty()) {
			metaLines.add(lineBuilder.toString());
		}
	}

	/**
	 * Used for performing a perft search and printing detailed statistics for each
	 * move.
	 * <p>
	 * This method iterates through all legal moves, plays each move on a copy
	 * of the position, and counts the nodes searched at the specified depth.
	 * It also measures per-move and total execution time, computes
	 * nodes-per-second,
	 * percentage contribution, and average branching factor.
	 * </p>
	 *
	 * @param depth the depth to search down to
	 */
	public static void perft(Position position, int depth) {
		depth = Math.max(depth, 1);
		Position root = position.copy();
		Perft.DivideResult result = Perft.divide(root, depth);
		int maxMoveLen = Math.max("Move".length(), result.entries().stream()
				.mapToInt(entry -> Move.toString(entry.move()).length())
				.max()
				.orElse(0));
		String headerFmt = String.format("%%-%ds %%12s %%10s %%10s %%10s %%10s %%10s%n", maxMoveLen);
		String rowFmt = String.format("%%-%ds %%12d %%10d %%10d %%10d %%10d %%10d%n", maxMoveLen);
		System.out.printf(headerFmt, "Move", "Nodes", "Captures", "EP", "Castles", "Promos", "Checks");
		for (Perft.DivideEntry entry : result.entries()) {
			Perft.Stats stats = entry.stats();
			System.out.printf(rowFmt,
					Move.toString(entry.move()),
					stats.nodes(),
					stats.captures(),
					stats.enPassant(),
					stats.castles(),
					stats.promotions(),
					stats.checks());
		}
		System.out.println("\nPerft for position '" + position + "'");
		System.out.printf(
				"Total at depth %d: %d nodes in %.3f s, branches: %d, nps: %.0f%n",
				depth,
				result.total().nodes(),
				result.nanos() / 1_000_000_000.0,
				result.entries().size(),
				result.nodesPerSecond());
	}

	/**
	 * Verifies the perft implementation at depth 6 against known reference
	 * positions and prints a neatly aligned comparison table.
	 */
	public static void testPerft() {
		testPerft(null);
	}

	/**
	 * Verifies the perft implementation at depth 6 while reporting progress once
	 * per completed reference position.
	 *
	 * @param progress optional callback invoked after each reference position
	 */
	public static void testPerft(Runnable progress) {
		try {
			PerftSuite.Summary summary = PerftSuite.validate(
					PerftSuite.DEFAULT_MAX_DEPTH,
					1,
					progress);
			PerftSuite.print(summary);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("perft validation interrupted", ex);
		}
	}

}
