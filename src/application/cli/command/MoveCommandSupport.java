package application.cli.command;

import static application.cli.Constants.ERR_INVALID_FEN;
import static application.cli.Constants.MSG_FEN_REQUIRED_HINT;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_RANDOMPOS;
import static application.cli.Constants.OPT_STARTPOS;

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
@SuppressWarnings("java:S3776")
final class MoveCommandSupport {

	/**
	 * Supported move parsing formats.
	 */
	enum MoveFormat {
		 /**
		 * Shared auto constant.
		 */
		 AUTO,
		 /**
		 * Shared uci constant.
		 */
		 UCI,
		 /**
		 * Shared san constant.
		 */
		 SAN
	}

	/**
	 * Parsed move command input (position + move tokens).
	 */
	static final class ParsedInput {
		 /**
		 * Stores the position.
		 */
		 final Position position;
		 /**
		 * Stores the moves.
		 */
		 final List<String> moves;

		 /**
		 * Creates a new parsed input instance.
		 * @param position position
		 * @param moves moves
		 */
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
		boolean startPos = a.flag(OPT_STARTPOS);
		boolean randomPos = a.flag(OPT_RANDOMPOS);
		String fen = a.string(OPT_FEN);
		List<String> rest = a.positionals();
		a.ensureConsumed();

		if (startPos || randomPos) {
			if ((fen != null && !fen.isBlank()) || looksLikeFenTokens(rest)) {
				throw new CommandFailure(
						cmd + ": choose at most one of " + OPT_FEN + " <FEN>, " + OPT_STARTPOS + ", or " + OPT_RANDOMPOS,
						2);
			}
			Position pos = startPos ? Setup.getStandardStartPosition() : CommandSupport.randomPlayablePosition();
			return new ParsedInput(pos, tokenizeMoves(rest));
		}

		if (fen != null && !fen.isBlank()) {
			Position pos = CommandSupport.parsePositionOrExit(fen.trim(), cmd, verbose);
			return new ParsedInput(pos, tokenizeMoves(rest));
		}

		if (rest.isEmpty()) {
			if (allowDefaultStart) {
				return new ParsedInput(Setup.getStandardStartPosition(), List.of());
			}
			throw new CommandFailure(cmd + " requires a FEN (" + MSG_FEN_REQUIRED_HINT + ")", 2);
		}

		String first = rest.get(0);
		if (first.contains("/")) {
			if (first.indexOf(' ') >= 0) {
				Position pos = CommandSupport.parsePositionOrExit(first.trim(), cmd, verbose);
				return new ParsedInput(pos, tokenizeMoves(rest.subList(1, rest.size())));
			}
			return parseFenFromTokens(rest, cmd, verbose);
		}

		if (!allowDefaultStart) {
			throw new CommandFailure(cmd + " requires a FEN (" + MSG_FEN_REQUIRED_HINT + ")", 2);
		}

		return new ParsedInput(Setup.getStandardStartPosition(), tokenizeMoves(rest));
	}

	/**
	 * Returns whether the positional tail looks like a FEN instead of move tokens.
	 *
	 * @param tokens positional CLI tail
	 * @return true when the first token looks like a FEN board field
	 */
	private static boolean looksLikeFenTokens(List<String> tokens) {
		return tokens != null && !tokens.isEmpty() && tokens.get(0) != null && tokens.get(0).contains("/");
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
			case SAN -> SAN.fromAlgebraic(pos, trimmed);
			case AUTO -> {
				String uciCandidate = trimmed.toLowerCase(Locale.ROOT);
				if (Move.isMove(uciCandidate)) {
					yield parseUci(pos, uciCandidate);
				}
				yield SAN.fromAlgebraic(pos, trimmed);
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

	/**
	 * Parses the uci.
	 * @param pos pos
	 * @param token token
	 * @return computed value
	 */
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

	/**
	 * Parses the fen from tokens.
	 * @param rest rest
	 * @param cmd cmd
	 * @param verbose verbose
	 * @return computed value
	 */
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
		LogService.error(last, cmd + ": invalid FEN", "FEN: " + String.join(" ", rest));
		throw new CommandFailure(ERR_INVALID_FEN + (msg == null ? "" : msg), last, 3, verbose);
	}

	/**
	 * Handles join tokens.
	 * @param tokens tokens
	 * @param count count
	 * @return computed value
	 */
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
