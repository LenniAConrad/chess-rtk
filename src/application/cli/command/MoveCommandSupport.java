package application.cli.command;

import static application.cli.Constants.ERR_INVALID_FEN;
import static application.cli.Constants.MSG_FEN_REQUIRED_HINT;
import static application.cli.Constants.OPT_FEN;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.core.Setup;
import chess.debug.LogService;
import utility.Argv;

/**
 * Shared helpers for CLI commands that accept FEN + move input.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class MoveCommandSupport {

	/**
	 * Supported move parsing formats.
	 */
	enum MoveFormat {
		AUTO,
		UCI,
		SAN
	}

	/**
	 * Parsed move command input (position + move tokens).
	 */
	static final class ParsedInput {
		final Position position;
		final List<String> moves;

		private ParsedInput(Position position, List<String> moves) {
			this.position = position;
			this.moves = moves;
		}
	}

	/**
	 * Utility class; prevent instantiation.
	 */
	private MoveCommandSupport() {
		// utility
	}

	/**
	 * Parses a CLI input into a starting position and a normalized move list.
	 *
	 * @param a                 argument parser
	 * @param cmd               command label for diagnostics
	 * @param allowDefaultStart whether to fall back to the standard start position
	 * @param verbose           whether to print stack traces on failure
	 * @return parsed input (position + move tokens)
	 */
	static ParsedInput parseInputs(Argv a, String cmd, boolean allowDefaultStart, boolean verbose) {
		String fen = a.string(OPT_FEN);
		List<String> rest = a.positionals();
		a.ensureConsumed();

		if (fen != null && !fen.isBlank()) {
			Position pos = parseFenOrExit(fen.trim(), cmd, verbose);
			return new ParsedInput(pos, tokenizeMoves(rest));
		}

		if (rest.isEmpty()) {
			if (allowDefaultStart) {
				return new ParsedInput(Setup.getStandardStartPosition(), List.of());
			}
			System.err.println(cmd + " requires a FEN (" + MSG_FEN_REQUIRED_HINT + ")");
			System.exit(2);
			return new ParsedInput(Setup.getStandardStartPosition(), List.of());
		}

		String first = rest.get(0);
		if (first.contains("/")) {
			if (first.indexOf(' ') >= 0) {
				Position pos = parseFenOrExit(first.trim(), cmd, verbose);
				return new ParsedInput(pos, tokenizeMoves(rest.subList(1, rest.size())));
			}
			return parseFenFromTokens(rest, cmd, verbose);
		}

		if (!allowDefaultStart) {
			System.err.println(cmd + " requires a FEN (" + MSG_FEN_REQUIRED_HINT + ")");
			System.exit(2);
			return new ParsedInput(Setup.getStandardStartPosition(), List.of());
		}

		return new ParsedInput(Setup.getStandardStartPosition(), tokenizeMoves(rest));
	}

	/**
	 * Parses a move token using the specified format.
	 *
	 * @param pos    current position
	 * @param token  move token
	 * @param format expected format (UCI, SAN, or AUTO)
	 * @return encoded move
	 */
	static short parseMove(Position pos, String token, MoveFormat format) {
		if (pos == null) {
			throw new IllegalArgumentException("Missing position");
		}
		if (token == null || token.trim().isEmpty()) {
			throw new IllegalArgumentException("Missing move token");
		}
		String trimmed = token.trim();
		return switch (format) {
			case UCI -> parseUci(pos, trimmed);
			case SAN -> SAN.fromAlgebraic(pos, normalizeSanToken(trimmed));
			case AUTO -> {
				String uciCandidate = trimmed.toLowerCase(Locale.ROOT);
				if (Move.isMove(uciCandidate)) {
					yield parseUci(pos, uciCandidate);
				}
				yield SAN.fromAlgebraic(pos, normalizeSanToken(trimmed));
			}
		};
	}

	/**
	 * Normalizes a list of move tokens by cleaning PGN-like input and splitting
	 * into individual tokens.
	 *
	 * @param tokens raw move tokens
	 * @return normalized list of move tokens
	 */
	static List<String> tokenizeMoves(List<String> tokens) {
		if (tokens == null || tokens.isEmpty()) {
			return List.of();
		}
		String raw = String.join(" ", tokens).trim();
		if (raw.isEmpty()) {
			return List.of();
		}
		String cleaned = SAN.cleanMoveString(raw);
		if (cleaned.isEmpty()) {
			return List.of();
		}
		return Arrays.asList(cleaned.split("\\s+"));
	}

	private static short parseUci(Position pos, String token) {
		String uci = token.toLowerCase(Locale.ROOT);
		if (!Move.isMove(uci)) {
			throw new IllegalArgumentException("Invalid UCI move: '" + token + "'");
		}
		short move = Move.parse(uci);
		if (!pos.isLegalMove(move)) {
			throw new IllegalArgumentException("Illegal move '" + token + "' in position '" + pos.toString() + "'");
		}
		return move;
	}

	private static ParsedInput parseFenFromTokens(List<String> rest, String cmd, boolean verbose) {
		int size = rest.size();
		IllegalArgumentException last = null;

		if (size >= 6) {
			String fen6 = joinTokens(rest, 6);
			try {
				Position pos = new Position(fen6);
				return new ParsedInput(pos, tokenizeMoves(rest.subList(6, size)));
			} catch (IllegalArgumentException ex) {
				last = ex;
			}
		}

		if (size >= 4) {
			String fen4 = joinTokens(rest, 4);
			try {
				Position pos = new Position(fen4);
				return new ParsedInput(pos, tokenizeMoves(rest.subList(4, size)));
			} catch (IllegalArgumentException ex) {
				last = ex;
			}
		}

		String msg = last != null ? last.getMessage() : "Invalid FEN (expected 4 or 6 fields)";
		System.err.println(ERR_INVALID_FEN + (msg == null ? "" : msg));
		LogService.error(last, cmd + ": invalid FEN", "FEN: " + String.join(" ", rest));
		if (verbose && last != null) {
			last.printStackTrace(System.err);
		}
		System.exit(3);
		return new ParsedInput(Setup.getStandardStartPosition(), List.of());
	}

	private static String normalizeSanToken(String token) {
		if (token.startsWith("0-0-0")) {
			return "O-O-O" + token.substring(5);
		}
		if (token.startsWith("0-0")) {
			return "O-O" + token.substring(3);
		}
		if (token.startsWith("o-o-o") || token.startsWith("O-O-O")) {
			return "O-O-O" + token.substring(5);
		}
		if (token.startsWith("o-o") || token.startsWith("O-O")) {
			return "O-O" + token.substring(3);
		}
		return token;
	}

	private static Position parseFenOrExit(String fen, String cmd, boolean verbose) {
		try {
			return new Position(fen);
		} catch (IllegalArgumentException ex) {
			System.err.println(ERR_INVALID_FEN + (ex.getMessage() == null ? "" : ex.getMessage()));
			LogService.error(ex, cmd + ": invalid FEN", "FEN: " + fen);
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
			return Setup.getStandardStartPosition();
		}
	}

	private static String joinTokens(List<String> tokens, int count) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; i++) {
			if (i > 0) {
				sb.append(' ');
			}
			sb.append(tokens.get(i));
		}
		return sb.toString();
	}
}
