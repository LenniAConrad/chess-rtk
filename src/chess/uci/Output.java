package chess.uci;

import java.util.Arrays;

import chess.core.Move;

/**
 * Used for storing the output and its data from UCI Chess Engines.
 *
 * <p>
 * As an example, one output line for the FEN
 * '8/p1p3Q1/1p4r1/5qk1/5pp1/P7/1P5R/K7 w - - 0 1' could look like:
 * </p>
 *
 * <blockquote>
 * info depth 31 seldepth 60 multipv 1 score cp 1315 wdl 1000 0 0
 * nodes 116712829 nps 3978756 hashfull 628 tbhits 0 time 29334
 * pv g7h8 f4f3 h2h5 ...
 * </blockquote>
 *
 * @see Analysis
 * @see Engine
 * @since 2023
 *        author Lennart A. Conrad
 */
public class Output {

	/**
	 * Used for marking presence of the search depth field in this output’s bitmask.
	 */
	private static final int F_DEPTH = 1 << 0;

	/**
	 * Used for marking presence of the selective search depth field in this
	 * output’s bitmask.
	 */
	private static final int F_SELDEPTH = 1 << 1;

	/**
	 * Used for marking presence of the principal variation (multipv) index in this
	 * output’s bitmask.
	 */
	private static final int F_PV = 1 << 2;

	/**
	 * Used for marking presence of the hash table fullness value in this output’s
	 * bitmask.
	 */
	private static final int F_HASHFULL = 1 << 3;

	/**
	 * Used for marking presence of the elapsed time value in this output’s bitmask.
	 */
	private static final int F_TIME = 1 << 4;

	/**
	 * Used for marking presence of the tablebase hits count in this output’s
	 * bitmask.
	 */
	private static final int F_TBHITS = 1 << 5;

	/**
	 * Used for marking presence of the total searched nodes count in this output’s
	 * bitmask.
	 */
	private static final int F_NODES = 1 << 6;

	/**
	 * Used for marking presence of the nodes-per-second metric in this output’s
	 * bitmask.
	 */
	private static final int F_NPS = 1 << 7;

	/**
	 * Used for marking presence of the parsed principal variation move list in this
	 * output’s bitmask.
	 */
	private static final int F_MOVES = 1 << 8;

	/**
	 * Used for marking presence of the evaluation score (cp or mate) in this
	 * output’s bitmask.
	 */
	private static final int F_EVAL = 1 << 9;

	/**
	 * Used for marking presence of the win/draw/loss chances (WDL) triple in this
	 * output’s bitmask.
	 */
	private static final int F_CHANCES = 1 << 10;

	/**
	 * Used for tracking which optional fields are present (replaces many booleans).
	 */
	private int flags;

	/**
	 * Used for knowing what depth the {@code Output} has reached.
	 */
	private short depth = 1;

	/**
	 * Used for knowing what selective depth the {@code Output} has reached.
	 */
	private short selectiveDepth = 1;

	/**
	 * Used for knowing what principal variation the {@code Output} represents.
	 */
	private short principalVariation = 1;

	/**
	 * Used for knowing how full the hash was when the {@code Output} was generated.
	 */
	private short hashfull = 0;

	/**
	 * Used for knowing how long the {@code Engine} took to generate the
	 * {@code Output}.
	 */
	private long time = 0;

	/**
	 * Used for knowing how many tablebase {@code Positions} have been found.
	 */
	private long tableBaseHits = 0;

	/**
	 * Used for knowing the total amount of searched nodes.
	 */
	private long nodes = 0;

	/**
	 * Used for knowing the amount of nodes searched per second.
	 */
	private long nodesPerSecond = 0;

	/**
	 * Used for storing the UCI {@code Engine} moves of the current {@code Output}.
	 */
	private short[] moves;

	/**
	 * Used for storing the {@code Evaluation} of the current {@code Output}.
	 */
	private Evaluation evaluation;

	/**
	 * Used for storing the {@code Chances} of the current {@code Output}.
	 */
	private Chances chances;

	/**
	 * Used for creating {@code Output} objects and parsing the provided UCI engine
	 * output.
	 *
	 * @param string the raw engine output line to parse
	 */
	public Output(String string) {
		initialize(string);
	}

	/**
	 * Used for creating a deep copy of another {@code Output} instance.
	 *
	 * @param other the {@code Output} to copy; must not be {@code null}
	 */
	public Output(Output other) {
		this.flags = other.flags;
		this.depth = other.depth;
		this.selectiveDepth = other.selectiveDepth;
		this.principalVariation = other.principalVariation;
		this.hashfull = other.hashfull;
		this.time = other.time;
		this.tableBaseHits = other.tableBaseHits;
		this.nodes = other.nodes;
		this.nodesPerSecond = other.nodesPerSecond;
		this.moves = (other.moves == null) ? null : Arrays.copyOf(other.moves, other.moves.length);
		this.evaluation = (other.evaluation == null) ? null : new Evaluation(other.evaluation);
		this.chances = (other.chances == null) ? null : new Chances(other.chances);
	}

	/**
	 * Used for initializing all values from a single UCI engine output line with
	 * minimal allocations,
	 * refactored to lower complexity and avoid multiple loop breaks/continues
	 * (Sonar S6541/S135).
	 *
	 * <p>
	 * The loop advances by delegating token handling to
	 * {@link #dispatchToken(String, String, int, int)},
	 * which returns the next scan index or {@code -1} to signal termination.
	 * </p>
	 *
	 * @param input the UCI output line; if {@code null}, the method returns
	 *              immediately
	 */
	private void initialize(String input) {
		if (input == null) {
			return;
		}
		final int n = input.length();
		int i = 0;
		for (;;) {
			i = skipWs(input, i, n);
			if (i >= n) {
				return;
			}
			final int j = nextTokenEnd(input, i, n);
			final String tok = input.substring(i, j);
			final int next = dispatchToken(input, tok, j, n);
			if (next < 0) {
				return;
			}
			i = next;
		}
	}

	/**
	 * Used for handling a single token and updating parsing state, returning the
	 * next scan index.
	 *
	 * <p>
	 * This method centralizes the dispatch to reduce nesting/complexity in
	 * {@link #initialize(String)}.
	 * Returning {@code -1} signals that parsing should terminate (e.g., on
	 * {@code bestmove}).
	 * </p>
	 *
	 * @param input full input line
	 * @param tok   token text for dispatch
	 * @param from  index to continue scanning after {@code tok}
	 * @param end   exclusive upper bound
	 * @return next scan index, or {@code -1} to end parsing
	 */
	private int dispatchToken(String input, String tok, int from, int end) {
		final String key = tok.toLowerCase(java.util.Locale.ROOT);
		switch (key) {
			case "bestmove", "upperbound", "lowerbound":
				return -1;
			case "depth":
				return readShortInto(input, from, end, v -> {
					depth = v;
					setFlag(F_DEPTH);
				});
			case "seldepth":
				return readShortInto(input, from, end, v -> {
					selectiveDepth = v;
					setFlag(F_SELDEPTH);
				});
			case "multipv":
				return readShortInto(input, from, end, v -> {
					principalVariation = v;
					setFlag(F_PV);
				});
			case "cp":
				return readIntInto(input, from, end, v -> {
					evaluation = new Evaluation(false, v);
					setFlag(F_EVAL);
				});
			case "mate":
				return readIntInto(input, from, end, v -> {
					evaluation = new Evaluation(true, v);
					setFlag(F_EVAL);
				});
			case "wdl":
				return readTripleShortsInto(input, from, end, (a, b, c) -> {
					chances = new Chances(a, b, c);
					setFlag(F_CHANCES);
				});
			case "nodes":
				return readLongInto(input, from, end, v -> {
					nodes = v;
					setFlag(F_NODES);
				});
			case "nps":
				return readLongInto(input, from, end, v -> {
					nodesPerSecond = v;
					setFlag(F_NPS);
				});
			case "hashfull":
				return readShortInto(input, from, end, v -> {
					hashfull = v;
					setFlag(F_HASHFULL);
				});
			case "tbhits":
				return readLongInto(input, from, end, v -> {
					tableBaseHits = v;
					setFlag(F_TBHITS);
				});
			case "time":
				return readLongInto(input, from, end, v -> {
					time = v;
					setFlag(F_TIME);
				});
			case "pv":
				return handlePvStreaming(input, from, end);
			default:
				return from;
		}
	}

	/** Used for consuming a parsed {@code short} value during token scanning. */
	@FunctionalInterface
	private interface ShortConsumer {
		/**
		 * Used for receiving one parsed {@code short}.
		 *
		 * @param v parsed {@code short} value
		 */
		void accept(short v);
	}

	/** Used for consuming a parsed {@code int} value during token scanning. */
	@FunctionalInterface
	private interface IntConsumer {
		/**
		 * Used for receiving one parsed {@code int}.
		 *
		 * @param v parsed {@code int} value
		 */
		void accept(int v);
	}

	/** Used for consuming a parsed {@code long} value during token scanning. */
	@FunctionalInterface
	private interface LongConsumer {
		/**
		 * Used for receiving one parsed {@code long}.
		 *
		 * @param v parsed {@code long} value
		 */
		void accept(long v);
	}

	/**
	 * Used for consuming three parsed {@code short} values (e.g., WDL triplet)
	 * during token
	 * scanning.
	 */
	@FunctionalInterface
	private interface ShortTripleConsumer {
		/**
		 * Used for receiving three parsed {@code short} values.
		 *
		 * @param a first {@code short} value
		 * @param b second {@code short} value
		 * @param c third {@code short} value
		 */
		void accept(short a, short b, short c);
	}

	/**
	 * Used for scanning forward from {@code i}, parsing the next space-delimited
	 * token as a {@code short},
	 * and delivering it to the provided consumer.
	 *
	 * <p>
	 * Skips leading whitespace, reads the contiguous non-whitespace token, and
	 * attempts to parse it as a
	 * {@code short}. If parsing succeeds, the value is passed to {@code c}. In all
	 * cases, returns the index
	 * immediately after the token’s last character.
	 * </p>
	 *
	 * @param s source string to scan
	 * @param i start index (inclusive) to begin scanning from
	 * @param n upper bound (exclusive) for scanning
	 * @param c consumer that receives the parsed {@code short} value on success
	 * @return index just after the parsed token (or unchanged segment if no token
	 *         was parsed)
	 */
	private int readShortInto(String s, int i, int n, ShortConsumer c) {
		while (i < n && s.charAt(i) <= ' ') {
			i++;
		}
		int j = i;
		while (j < n && s.charAt(j) > ' ') {
			j++;
		}
		if (j > i) {
			try {
				c.accept(Short.parseShort(s.substring(i, j)));
			} catch (NumberFormatException ignored) {
				// Used for tolerating non-numeric tokens in UCI streams; consumer intentionally
				// not called.
			}
		}
		return j;
	}

	/**
	 * Used for scanning forward from {@code i}, parsing the next space-delimited
	 * token as an {@code int},
	 * and delivering it to the provided consumer.
	 *
	 * <p>
	 * Skips leading whitespace, reads the contiguous non-whitespace token, and
	 * attempts to parse it as an
	 * {@code int}. If parsing succeeds, the value is passed to {@code c}. In all
	 * cases, returns the index
	 * immediately after the token’s last character.
	 * </p>
	 *
	 * @param s source string to scan
	 * @param i start index (inclusive) to begin scanning from
	 * @param n upper bound (exclusive) for scanning
	 * @param c consumer that receives the parsed {@code int} value on success
	 * @return index just after the parsed token (or unchanged segment if no token
	 *         was parsed)
	 */
	private int readIntInto(String s, int i, int n, IntConsumer c) {
		while (i < n && s.charAt(i) <= ' ') {
			i++;
		}
		int j = i;
		while (j < n && s.charAt(j) > ' ') {
			j++;
		}
		if (j > i) {
			try {
				c.accept(Integer.parseInt(s.substring(i, j)));
			} catch (NumberFormatException ignored) {
				// Used for tolerating non-numeric tokens in UCI streams; consumer intentionally
				// not called.
			}
		}
		return j;
	}

	/**
	 * Used for scanning forward from {@code i}, parsing the next space-delimited
	 * token as a {@code long},
	 * and delivering it to the provided consumer.
	 *
	 * <p>
	 * Skips leading whitespace, reads the contiguous non-whitespace token, and
	 * attempts to parse it as a
	 * {@code long}. If parsing succeeds, the value is passed to {@code c}. In all
	 * cases, returns the index
	 * immediately after the token’s last character.
	 * </p>
	 *
	 * @param s source string to scan
	 * @param i start index (inclusive) to begin scanning from
	 * @param n upper bound (exclusive) for scanning
	 * @param c consumer that receives the parsed {@code long} value on success
	 * @return index just after the parsed token (or unchanged segment if no token
	 *         was parsed)
	 */
	private int readLongInto(String s, int i, int n, LongConsumer c) {
		while (i < n && s.charAt(i) <= ' ') {
			i++;
		}
		int j = i;
		while (j < n && s.charAt(j) > ' ') {
			j++;
		}
		if (j > i) {
			try {
				c.accept(Long.parseLong(s.substring(i, j)));
			} catch (NumberFormatException ignored) {
				// Used for tolerating non-numeric tokens in UCI streams; consumer intentionally
				// not called.
			}
		}
		return j;
	}

	/**
	 * Used for scanning three consecutive space-delimited tokens and parsing them
	 * as {@code short}
	 * values, delivering the triplet to the provided consumer.
	 *
	 * <p>
	 * Starting at {@code i}, this method skips leading whitespace, reads a token,
	 * and parses it as
	 * a {@code short}. It repeats the process two more times to obtain three values
	 * total. For each
	 * token, non-numeric input is tolerated and yields {@code 0}. The returned
	 * index is positioned
	 * immediately after the third token.
	 * </p>
	 *
	 * @param s source string to scan
	 * @param i start index (inclusive) of the first token
	 * @param n upper bound (exclusive) for scanning
	 * @param c consumer that receives the three parsed {@code short} values
	 * @return index just after the third parsed token
	 */
	private int readTripleShortsInto(String s, int i, int n, ShortTripleConsumer c) {
		// first
		while (i < n && s.charAt(i) <= ' ') {
			i++;
		}
		int e1 = i;
		while (e1 < n && s.charAt(e1) > ' ') {
			e1++;
		}
		short a = parseShortSafe(s, i, e1);

		// second
		i = e1;
		while (i < n && s.charAt(i) <= ' ') {
			i++;
		}
		int e2 = i;
		while (e2 < n && s.charAt(e2) > ' ') {
			e2++;
		}
		short b = parseShortSafe(s, i, e2);

		// third
		i = e2;
		while (i < n && s.charAt(i) <= ' ') {
			i++;
		}
		int e3 = i;
		while (e3 < n && s.charAt(e3) > ' ') {
			e3++;
		}
		short c3 = parseShortSafe(s, i, e3);

		c.accept(a, b, c3);
		return e3;
	}

	/**
	 * Used for parsing a {@code short} from the substring {@code s[from:to]},
	 * returning {@code 0}
	 * when the input is not a valid number.
	 *
	 * @param s    source string
	 * @param from start index (inclusive)
	 * @param to   end index (exclusive)
	 * @return parsed {@code short} value, or {@code 0} on failure
	 */
	private static short parseShortSafe(String s, int from, int to) {
		try {
			return Short.parseShort(s.substring(from, to));
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	/**
	 * Used for handling the "pv" (principal variation) move sequence without
	 * temporary lists,
	 * while complying with Sonar rule S135 (at most one break/continue in the
	 * loop).
	 *
	 * @param s the full input line
	 * @param i index after the "pv" token
	 * @param n input length
	 * @return the index just after the last parsed move token
	 */
	private int handlePvStreaming(String s, int i, int n) {
		short[] buf = new short[8];
		int size = 0;

		int k = i;
		while (k < n) {
			k = skipWs(s, k, n);
			if (k >= n) {
				// No break/continue here: exit by returning once loop condition will fail.
				if (size > 0) {
					moves = java.util.Arrays.copyOf(buf, size);
				}
				return k;
			}
			int e = nextTokenEnd(s, k, n);
			String token = s.substring(k, e);
			try {
				short mv = Move.parse(token);
				if (size == buf.length) {
					buf = java.util.Arrays.copyOf(buf, size + (size >>> 1) + 1);
				}
				buf[size++] = mv;
				setFlag(F_MOVES);
			} catch (IllegalArgumentException ex) {
				// Single allowed loop control (S135): stop on first non-move token.
				break;
			}
			k = e;
		}

		if (size > 0) {
			moves = java.util.Arrays.copyOf(buf, size);
		}
		return k;
	}

	/**
	 * Used for skipping ASCII whitespace starting at {@code from} and returning the
	 * index of the first
	 * non-whitespace character, or {@code end} if only whitespace remains.
	 *
	 * @param s    the source string to scan
	 * @param from start index (inclusive) from which to skip whitespace
	 * @param end  upper bound (exclusive) for scanning
	 * @return index of the first non-whitespace char in {@code [from, end)}, or
	 *         {@code end} if none
	 */
	private static int skipWs(String s, int from, int end) {
		while (from < end && s.charAt(from) <= ' ') {
			from++;
		}
		return from;
	}

	/**
	 * Used for finding the exclusive end index of the next non-whitespace token
	 * that begins at
	 * {@code from}.
	 *
	 * <p>
	 * Precondition: {@code from == end} or {@code s.charAt(from) > ' '} (i.e., call
	 * {@link #skipWs(String, int, int)} first). If {@code from >= end},
	 * {@code from} is returned.
	 * </p>
	 *
	 * @param s    the source string to scan
	 * @param from start index (inclusive) of a non-whitespace token, or {@code end}
	 * @param end  upper bound (exclusive) for scanning
	 * @return exclusive end index of the token starting at {@code from}; returns
	 *         {@code from} if {@code from >= end}
	 */
	private static int nextTokenEnd(String s, int from, int end) {
		int e = from + 1;
		while (e < end && s.charAt(e) > ' ') {
			e++;
		}
		return e;
	}

	/**
	 * Used for retrieving the depth that the {@code Output} contains.
	 *
	 * @return the depth that the {@code Output} contains
	 */
	public short getDepth() {
		return depth;
	}

	/**
	 * Used for retrieving the selective depth that the {@code Output} contains.
	 *
	 * @return the selective depth that the {@code Output} contains
	 */
	public short getSelectiveDepth() {
		return selectiveDepth;
	}

	/**
	 * Used for retrieving which pivot the {@code Output} represents.
	 *
	 * @return which pivot the {@code Output} represents
	 */
	public short getPrincipalVariation() {
		return principalVariation;
	}

	/**
	 * Used for retrieving how full the hash of the current {@code Output} was.
	 *
	 * @return how full the hash of the current {@code Output} was
	 */
	public short getHashfull() {
		return hashfull;
	}

	/**
	 * Used for retrieving the time it took to generate the current {@code Output}.
	 *
	 * @return the time it took to generate the current {@code Output}
	 */
	public long getTime() {
		return time;
	}

	/**
	 * Used for retrieving the tablebase hit amount of the current {@code Output}.
	 *
	 * @return the tablebase hit amount of the current {@code Output}
	 */
	public long getTableBaseHits() {
		return tableBaseHits;
	}

	/**
	 * Used for retrieving the searched nodes amount of the current {@code Output}.
	 *
	 * @return the searched nodes amount of the current {@code Output}
	 */
	public long getNodes() {
		return nodes;
	}

	/**
	 * Used for retrieving the nodes per second searched of the current
	 * {@code Output}.
	 *
	 * @return the nodes per second searched of the current {@code Output}
	 */
	public long getNodesPerSecond() {
		return nodesPerSecond;
	}

	/**
	 * Used for retrieving the {@code Moves} of the current {@code Output}.
	 *
	 * @return the {@code Moves} of the current {@code Output}
	 */
	public short[] getMoves() {
		return moves;
	}

	/**
	 * Used for retrieving the {@code Evaluation} of the current {@code Output}.
	 *
	 * @return the {@code Evaluation} of the current {@code Output}
	 */
	public Evaluation getEvaluation() {
		return evaluation;
	}

	/**
	 * Used for retrieving the {@code Chances} of the current {@code Output}.
	 *
	 * @return the {@code Chances} of the current {@code Output}
	 */
	public Chances getChances() {
		return chances;
	}

	/**
	 * Used for checking whether any meaningful field was parsed (depth, eval,
	 * nodes, pv, etc.).
	 *
	 * @return {@code true} if any data is present, else {@code false}
	 */
	public boolean hasContent() {
		return flags != 0 || (moves != null && moves.length > 0);
	}

	/**
	 * Used for building and returning the UCI-formatted info string, or empty if
	 * invalid.
	 *
	 * @return the UCI-formatted info string, or empty if invalid
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(96);
		sb.append("info");
		appendOption(sb, isSet(F_DEPTH), " depth ", depth);
		appendOption(sb, isSet(F_SELDEPTH), " seldepth ", selectiveDepth);
		appendOption(sb, isSet(F_PV), " multipv ", principalVariation);

		if (isSet(F_EVAL) && evaluation != null) {
			sb.append(" score ")
					.append(evaluation.isMate() ? "mate " : "cp ")
					.append(evaluation.getValue());
		}
		if (isSet(F_CHANCES) && chances != null) {
			sb.append(" wdl ")
					.append(chances.getWinChance()).append(' ')
					.append(chances.getDrawChance()).append(' ')
					.append(chances.getLossChance());
		}

		appendOption(sb, isSet(F_NODES), " nodes ", nodes);
		appendOption(sb, isSet(F_NPS), " nps ", nodesPerSecond);
		appendOption(sb, isSet(F_HASHFULL), " hashfull ", hashfull);
		appendOption(sb, isSet(F_TBHITS), " tbhits ", tableBaseHits);
		appendOption(sb, isSet(F_TIME), " time ", time);

		if (isSet(F_MOVES) && moves != null && moves.length > 0) {
			sb.append(" pv");
			for (int i = 0; i < moves.length; i++) {
				sb.append(' ').append(Move.toString(moves[i]));
			}
		}
		return sb.toString();
	}

	/**
	 * Used for setting a presence bit in the internal flags bitmask.
	 *
	 * @param bit bit value to set in the flags mask
	 */
	private void setFlag(int bit) {
		flags |= bit;
	}

	/**
	 * Used for checking whether the given presence bit is set in the internal flags
	 * bitmask.
	 *
	 * @param bit bit value to test in the flags mask
	 * @return {@code true} if the bit is set; otherwise {@code false}
	 */
	private boolean isSet(int bit) {
		return (flags & bit) != 0;
	}

	/**
	 * Used for conditionally appending a labeled numeric option to the provided
	 * {@link StringBuilder}.
	 * <p>
	 * When {@code present} is {@code true}, this appends {@code prefix} followed by
	 * {@code value}
	 * (e.g., {@code " depth 30"}). When {@code present} is {@code false}, this
	 * performs no action.
	 *
	 * @param sb      target builder to append to
	 * @param present whether the option should be appended
	 * @param prefix  leading label including leading space (e.g.,
	 *                {@code " depth "})
	 * @param value   numeric value to append after the prefix
	 */
	private static void appendOption(StringBuilder sb, boolean present, String prefix, long value) {
		if (present) {
			sb.append(prefix).append(value);
		}
	}
}
