package application.cli.command;

import static application.cli.Constants.CMD_DIFF;
import static application.cli.Constants.CMD_POSITION;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_OTHER;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;

import java.util.ArrayList;
import java.util.List;

import application.cli.command.CommandSupport.OutputMode;
import chess.core.Bits;
import chess.core.Piece;
import chess.core.Position;
import utility.Argv;

/**
 * Implements deterministic position-inspection commands.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PositionCommand {

	/**
	 * Command label for {@code position diff}.
	 */
	private static final String POSITION_DIFF = CMD_POSITION + " " + CMD_DIFF;

	/**
	 * State labels matching the FEN fields after piece placement.
	 */
	private static final String[] STATE_LABELS = {
			"side-to-move",
			"castling",
			"en-passant",
			"halfmove",
			"fullmove"
	};

	/**
	 * Utility class; prevent instantiation.
	 */
	private PositionCommand() {
		// utility
	}

	/**
	 * Handles {@code position diff}.
	 *
	 * @param a argument parser
	 */
	public static void runDiff(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		OutputMode outputMode = CommandSupport.resolveOutputMode(a, POSITION_DIFF);
		String leftFen = a.string(OPT_FEN);
		String rightFen = a.string(OPT_OTHER, "--right");
		List<String> rest = new ArrayList<>(a.positionals());
		a.ensureConsumed();
		Pair pair = resolvePair(leftFen, rightFen, rest);
		Position left = CommandSupport.parsePositionOrExit(pair.left(), POSITION_DIFF, verbose);
		Position right = CommandSupport.parsePositionOrExit(pair.right(), POSITION_DIFF, verbose);
		DiffResult diff = buildDiff(left, right);

		if (outputMode == OutputMode.JSON || outputMode == OutputMode.JSONL) {
			System.out.println(diff.toJson());
		} else {
			printText(diff);
		}
	}

	/**
	 * Resolves left/right FEN inputs.
	 *
	 * @param left optional left FEN
	 * @param right optional right FEN
	 * @param rest positional FEN arguments
	 * @return resolved pair
	 */
	private static Pair resolvePair(String left, String right, List<String> rest) {
		if (left == null && !rest.isEmpty()) {
			left = rest.remove(0);
		}
		if (right == null && !rest.isEmpty()) {
			right = rest.remove(0);
		}
		if (!rest.isEmpty()) {
			throw new CommandFailure(POSITION_DIFF
					+ ": provide two quoted FENs, or use --fen <left> --other <right>", 2);
		}
		if (left == null || left.isBlank() || right == null || right.isBlank()) {
			throw new CommandFailure(POSITION_DIFF + " requires --fen <left> and --other <right>", 2);
		}
		return new Pair(left, right);
	}

	/**
	 * Builds a structured position diff.
	 *
	 * @param left left position
	 * @param right right position
	 * @return diff result
	 */
	private static DiffResult buildDiff(Position left, Position right) {
		List<StateDiff> state = stateDiffs(left.toString(), right.toString());
		List<BoardDiff> board = boardDiffs(left, right);
		return new DiffResult(left.toString(), right.toString(), state, board);
	}

	/**
	 * Compares non-board FEN fields.
	 *
	 * @param leftFen left normalized FEN
	 * @param rightFen right normalized FEN
	 * @return state diffs
	 */
	private static List<StateDiff> stateDiffs(String leftFen, String rightFen) {
		String[] left = leftFen.split(" ");
		String[] right = rightFen.split(" ");
		List<StateDiff> diffs = new ArrayList<>();
		for (int i = 1; i < Math.min(left.length, right.length); i++) {
			if (!left[i].equals(right[i])) {
				diffs.add(new StateDiff(STATE_LABELS[i - 1], left[i], right[i]));
			}
		}
		return diffs;
	}

	/**
	 * Compares board occupancy square by square.
	 *
	 * @param left left position
	 * @param right right position
	 * @return board diffs
	 */
	private static List<BoardDiff> boardDiffs(Position left, Position right) {
		byte[] leftBoard = left.getBoard();
		byte[] rightBoard = right.getBoard();
		List<BoardDiff> diffs = new ArrayList<>();
		for (int square = 0; square < leftBoard.length; square++) {
			String leftPiece = pieceText(leftBoard[square]);
			String rightPiece = pieceText(rightBoard[square]);
			if (!leftPiece.equals(rightPiece)) {
				diffs.add(new BoardDiff(Bits.name(square), leftPiece, rightPiece));
			}
		}
		return diffs;
	}

	/**
	 * Converts a piece code to compact text.
	 *
	 * @param piece piece code
	 * @return FEN piece character or {@code .}
	 */
	private static String pieceText(byte piece) {
		if (piece == Piece.EMPTY) {
			return ".";
		}
		return Character.toString(Piece.toLowerCaseChar(piece));
	}

	/**
	 * Prints a human-readable diff.
	 *
	 * @param diff diff result
	 */
	private static void printText(DiffResult diff) {
		if (diff.isEqual()) {
			System.out.println("positions match");
			return;
		}
		for (StateDiff state : diff.state()) {
			System.out.println(state.field() + ": " + state.left() + " -> " + state.right());
		}
		for (BoardDiff board : diff.board()) {
			System.out.println(board.square() + ": " + board.left() + " -> " + board.right());
		}
	}

	/**
	 * Resolved FEN pair.
	 *
	 * @param left left FEN
	 * @param right right FEN
	 */
	private record Pair(String left, String right) {
	}

	/**
	 * Non-board state diff.
	 *
	 * @param field state field
	 * @param left left value
	 * @param right right value
	 */
	private record StateDiff(String field, String left, String right) {

		/**
		 * Converts this diff to JSON.
		 *
		 * @return JSON object
		 */
		String toJson() {
			return "{\"field\":" + CommandSupport.jsonString(field)
					+ ",\"left\":" + CommandSupport.jsonString(left)
					+ ",\"right\":" + CommandSupport.jsonString(right)
					+ "}";
		}
	}

	/**
	 * Board square diff.
	 *
	 * @param square square name
	 * @param left left piece
	 * @param right right piece
	 */
	private record BoardDiff(String square, String left, String right) {

		/**
		 * Converts this diff to JSON.
		 *
		 * @return JSON object
		 */
		String toJson() {
			return "{\"square\":" + CommandSupport.jsonString(square)
					+ ",\"left\":" + CommandSupport.jsonString(left)
					+ ",\"right\":" + CommandSupport.jsonString(right)
					+ "}";
		}
	}

	/**
	 * Full diff result.
	 *
	 * @param left left FEN
	 * @param right right FEN
	 * @param state state diffs
	 * @param board board diffs
	 */
	private record DiffResult(String left, String right, List<StateDiff> state, List<BoardDiff> board) {

		/**
		 * Returns whether the positions match.
		 *
		 * @return true when no diffs exist
		 */
		boolean isEqual() {
			return state.isEmpty() && board.isEmpty();
		}

		/**
		 * Converts this result to JSON.
		 *
		 * @return JSON object
		 */
		String toJson() {
			return "{\"equal\":" + isEqual()
					+ ",\"left\":" + CommandSupport.jsonString(left)
					+ ",\"right\":" + CommandSupport.jsonString(right)
					+ ",\"state\":[" + String.join(",", state.stream().map(StateDiff::toJson).toList())
					+ "],\"board\":[" + String.join(",", board.stream().map(BoardDiff::toJson).toList())
					+ "]}";
		}
	}
}
