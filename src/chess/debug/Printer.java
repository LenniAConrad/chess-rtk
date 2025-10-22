package chess.debug;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.stream.IntStream;

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
		addMeta(metaLines, "Side to move", position.isWhiteTurn() ? "White" : "Black");
		addMeta(metaLines, "En passant", Field.toString(position.getEnPassant()));
		addMeta(metaLines, "Half move clock", Integer.toString(position.getHalfMove()));
		addMeta(metaLines, "Full move number", Integer.toString(position.getFullMove()));

		String whiteCast = buildCastlingRights(position, true);
		String blackCast = buildCastlingRights(position, false);
		addMeta(metaLines, "White castling", whiteCast);
		addMeta(metaLines, "Black castling", blackCast);

		addMeta(metaLines, "Checkers", buildCheckersString(position));
		addMeta(metaLines, "Chess960", Boolean.toString(position.isChess960()));
		addMeta(metaLines, "White King", Field.toString(position.getWhiteKing()));
		addMeta(metaLines, "Black King", Field.toString(position.getBlackKing()));

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
			sb.append(position.getWhiteKingside() != Field.NO_SQUARE ? "K" : "");
			sb.append(position.getWhiteQueenside() != Field.NO_SQUARE ? "Q" : "-");
		} else {
			sb.append(position.getBlackKingside() != Field.NO_SQUARE ? "k" : "");
			sb.append(position.getBlackQueenside() != Field.NO_SQUARE ? "q" : "-");
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
			appendFileIfSquare(position.getWhiteKingside(), sb, true);
			appendFileIfSquare(position.getWhiteQueenside(), sb, true);
		} else {
			appendFileIfSquare(position.getBlackKingside(), sb, false);
			appendFileIfSquare(position.getBlackQueenside(), sb, false);
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
		MoveList moves = position.getMoves();
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

		MoveList moveList = position.getMoves();
		int numMoves = moveList.size();

		// collect move-strings and determine max width
		String[] moves = new String[numMoves];
		int maxMoveLen = "Move".length();
		for (int i = 0; i < numMoves; i++) {
			moves[i] = Move.toString(moveList.get(i));
			maxMoveLen = Math.max(maxMoveLen, moves[i].length());
		}

		long[] nodesPerMove = new long[numMoves];
		long[] timePerMove = new long[numMoves];
		long totalNodes = 0L;
		long globalStart = System.nanoTime();

		for (int i = 0; i < numMoves; i++) {
			Position posCopy = position.copyOf();
			posCopy.play(moveList.get(i));
			long start = System.nanoTime();
			long nodes = posCopy.perft(depth - 1);
			timePerMove[i] = System.nanoTime() - start;
			nodesPerMove[i] = nodes;
			totalNodes += nodes;
		}

		long globalDuration = System.nanoTime() - globalStart;
		double totalSeconds = globalDuration / 1_000_000_000.0;

		// avoid division by zero on numMoves
		double avgBranching = numMoves > 0
				? (double) totalNodes / numMoves
				: 0.0;

		// build format strings with dynamic width for the Move column
		String headerFmt = String.format("%%%ds %%12s %%12s %%10s%n", maxMoveLen);
		String rowFmt = String.format("%%%ds %%12d %%12.3f %%10.0f (%%.2f%%%%)%n", maxMoveLen);

		// print header
		System.out.printf(headerFmt, "Move", "Nodes", "Time(s)", "NPS");

		// print each line, all right-aligned
		for (int i = 0; i < numMoves; i++) {
			double seconds = timePerMove[i] / 1_000_000_000.0;
			double nps = seconds > 0
					? nodesPerMove[i] / seconds
					: 0.0;

			// avoid division by zero on totalNodes
			double pct = totalNodes > 0
					? (nodesPerMove[i] * 100.0 / totalNodes)
					: 0.0;

			System.out.printf(rowFmt, moves[i], nodesPerMove[i], seconds, nps, pct);
		}

		System.out.println("\nPerft for position '" + position.toString() + "'");

		// summary
		System.out.printf(
				"Total at depth %d: %d nodes in %.3f s, branches: %d, avg branch: %.2f%n",
				depth, totalNodes, totalSeconds, numMoves, avgBranching);
	}

	/**
	 * Verifies the perft implementation at depth 6 against known reference
	 * positions and prints a neatly aligned comparison table.
	 */
	public static void testPerft() {
		System.out.println("Testing perft function (depth 6); this may take a few minutes...\n");

		String[] fens = {
				"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
				"r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
				"8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
				"r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
				"rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
				"r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
				"bb3rkr/pq1p2pp/1p2pn2/2p2p2/2P2PnP/1P2PN2/PQBP1NP1/B4RKR w HFhf - 9 10",
				"brkqrbnn/pppppppp/8/8/8/8/PPPPPPPP/BRKQRBNN w EBeb - 0 1",
				"1qrkr2n/pp1ppbpp/4np2/2p1b3/8/2P1NPN1/PPBPPBPP/Q1RKR3 w ECec - 4 7",
				"3r1kr1/8/8/3bb3/3BB3/8/8/3R1KR1 w GDgd - 0 1",
				"rk4r1/8/8/3nn3/3NN3/8/8/RK4R1 b GAga - 0 1",
				"3rkr2/8/8/3nn3/3NN3/8/8/3RKR2 b FDfd - 0 1",
				"krN1N1N1/pp1N1N1N/N1N1N1N1/1N1N1N1N/N1N1N1N1/1N1N1N1N/N1N1N1N1/1N1N1N1K w - - 0 1",
		};

		long[] targets = {
				119060324L, 8031647685L, 11030083L,
				706045033L, 3048196529L, 6923051137L,
				2412004068L, 89994927L, 2257539632L,
				1966029236L, 1858702397L, 819354710L,
				528930290L,
		};

		long[] calculated = IntStream.range(0, fens.length)
				.parallel()
				.mapToLong(i -> new Position(fens[i]).perft(6))
				.toArray();

		int fenWidth = Arrays.stream(fens).mapToInt(String::length).max().orElse(0);
		int numberWidth = 3; // “No” column width
		int decimalWidth = 15; // width for big integers
		int booleanWidth = 5; // “true” / “false”

		String headerFmt = "%-" + numberWidth + "s %-" + fenWidth + "s %" + decimalWidth + "s %" +
				decimalWidth + "s %" + booleanWidth + "s%n";
		String rowFmt = "%-" + numberWidth + "d %-" + fenWidth + "s %" + decimalWidth + "d %" +
				decimalWidth + "d %" + booleanWidth + "b%n";

		System.out.printf(headerFmt, "No", "FEN", "Target", "Calculated", "Match");
		for (int i = 0; i < fens.length; i++) {
			System.out.printf(rowFmt,
					i + 1,
					fens[i],
					targets[i],
					calculated[i],
					targets[i] == calculated[i]);
		}
	}

}
