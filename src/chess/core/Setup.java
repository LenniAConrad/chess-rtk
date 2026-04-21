package chess.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Container class containing the standard chess setup, as well as all Chess960
 * board setups.
 *
 * @since 2024
 * @author Lennart A. Conrad
 * @see Position
 */
public class Setup {

	/**
	 * Number of legal Chess960 starting positions in Scharnagl order.
	 */
	private static final int CHESS960_POSITION_COUNT = 960;

	/**
	 * Number of files on a chess board.
	 */
	private static final int BOARD_WIDTH = 8;

	/**
	 * Knight placements for the five still-empty files after both bishops and the
	 * queen have been placed.
	 */
	private static final int[][] KNIGHT_PAIRS = {
			{ 0, 1 },
			{ 0, 2 },
			{ 0, 3 },
			{ 0, 4 },
			{ 1, 2 },
			{ 1, 3 },
			{ 1, 4 },
			{ 2, 3 },
			{ 2, 4 },
			{ 3, 4 }
	};

	/**
	 * Pieces filled into the remaining files after bishops, queen, and knights.
	 */
	private static final char[] ROOK_KING_ROOK = { 'R', 'K', 'R' };

	/**
	 * The standard starting position in FEN format.
	 */
	private static final String STANDARD_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

	/**
	 * All Chess960 starting positions, generated once in Scharnagl index order.
	 */
	private static final Position[] CHESS_960_POSITIONS = generateChess960Positions();

	/**
	 * The normal chess starting {@code Position} of any game.
	 */
	private static final Position STANDARD_START_POSITION = new Position(STANDARD_FEN);

	/**
	 * Private constructor to prevent instantiation of this class.
	 */
	private Setup() {
		// Prevent instantiation
	}

	/**
	 * Used for generating the cached Chess960 starting positions.
	 *
	 * @return all Chess960 positions in index order.
	 */
	private static Position[] generateChess960Positions() {
		Position[] positions = new Position[CHESS960_POSITION_COUNT];
		for (int index = 0; index < positions.length; index++) {
			positions[index] = new Position(chess960Fen(index));
		}
		return positions;
	}

	/**
	 * Used for building the Chess960 starting FEN for a Scharnagl index.
	 *
	 * @param index the Chess960 index from 0 through 959.
	 * @return the starting FEN at {@code index}.
	 */
	private static String chess960Fen(int index) {
		char[] rank = chess960Rank(index);
		return piecePlacement(rank) + " w " + castlingAvailability(rank) + " - 0 1";
	}

	/**
	 * Used for building the back-rank layout for a Scharnagl Chess960 index.
	 *
	 * @param index the Chess960 index from 0 through 959.
	 * @return an uppercase white back-rank layout.
	 */
	private static char[] chess960Rank(int index) {
		int value = index;
		char[] rank = new char[BOARD_WIDTH];

		rank[(value % 4) * 2 + 1] = 'B';
		value /= 4;

		rank[(value % 4) * 2] = 'B';
		value /= 4;

		placeNthEmpty(rank, value % 6, 'Q');
		value /= 6;

		int[] knightPair = KNIGHT_PAIRS[value % KNIGHT_PAIRS.length];
		int[] emptyFiles = emptyFiles(rank);
		rank[emptyFiles[knightPair[0]]] = 'N';
		rank[emptyFiles[knightPair[1]]] = 'N';

		fillRemainingWithRooksAndKing(rank);
		return rank;
	}

	/**
	 * Used for placing a piece into the nth currently empty file.
	 *
	 * @param rank         the rank being assembled.
	 * @param emptyOrdinal zero-based empty-file ordinal.
	 * @param piece        the piece to place.
	 */
	private static void placeNthEmpty(char[] rank, int emptyOrdinal, char piece) {
		for (int file = 0; file < rank.length; file++) {
			if (rank[file] == '\0') {
				if (emptyOrdinal == 0) {
					rank[file] = piece;
					return;
				}
				emptyOrdinal--;
			}
		}
		throw new IllegalArgumentException("No empty file for ordinal " + emptyOrdinal);
	}

	/**
	 * Used for listing the currently empty files on a rank.
	 *
	 * @param rank the rank being assembled.
	 * @return empty file indexes in left-to-right order.
	 */
	private static int[] emptyFiles(char[] rank) {
		int[] files = new int[5];
		int count = 0;
		for (int file = 0; file < rank.length; file++) {
			if (rank[file] == '\0') {
				files[count++] = file;
			}
		}
		if (count != files.length) {
			throw new IllegalStateException("Expected five empty files, found " + count);
		}
		return files;
	}

	/**
	 * Used for filling the remaining three files with rook, king, rook.
	 *
	 * @param rank the rank being assembled.
	 */
	private static void fillRemainingWithRooksAndKing(char[] rank) {
		int next = 0;
		for (int file = 0; file < rank.length; file++) {
			if (rank[file] == '\0') {
				rank[file] = ROOK_KING_ROOK[next++];
			}
		}
	}

	/**
	 * Used for building the piece placement segment for the two starting ranks and
	 * pawns.
	 *
	 * @param whiteRank the uppercase white back rank.
	 * @return the FEN piece-placement segment.
	 */
	private static String piecePlacement(char[] whiteRank) {
		String white = new String(whiteRank);
		String black = white.toLowerCase(Locale.ROOT);
		return black + "/pppppppp/8/8/8/8/PPPPPPPP/" + white;
	}

	/**
	 * Used for building Shredder-FEN castling availability letters from the back
	 * rank.
	 *
	 * @param rank the uppercase white back rank.
	 * @return castling availability in king-side then queen-side order.
	 */
	private static String castlingAvailability(char[] rank) {
		int king = indexOf(rank, 'K');
		int queenSideRook = -1;
		int kingSideRook = -1;

		for (int file = 0; file < rank.length; file++) {
			if (rank[file] == 'R') {
				if (file < king) {
					queenSideRook = file;
				} else if (file > king) {
					kingSideRook = file;
					break;
				}
			}
		}

		if (queenSideRook < 0 || kingSideRook < 0) {
			throw new IllegalStateException("Chess960 rank does not contain rooks around king: " + new String(rank));
		}

		return new StringBuilder(4)
				.append(fileUppercase(kingSideRook))
				.append(fileUppercase(queenSideRook))
				.append(fileLowercase(kingSideRook))
				.append(fileLowercase(queenSideRook))
				.toString();
	}

	/**
	 * Used for finding a required piece on a rank.
	 *
	 * @param rank  the rank to search.
	 * @param piece the required piece.
	 * @return the file containing {@code piece}.
	 */
	private static int indexOf(char[] rank, char piece) {
		for (int file = 0; file < rank.length; file++) {
			if (rank[file] == piece) {
				return file;
			}
		}
		throw new IllegalStateException("Missing piece " + piece + " in rank " + new String(rank));
	}

	/**
	 * Used for converting a zero-based file index to an uppercase file letter.
	 *
	 * @param file zero-based file index.
	 * @return uppercase file letter.
	 */
	private static char fileUppercase(int file) {
		return (char) ('A' + file);
	}

	/**
	 * Used for converting a zero-based file index to a lowercase file letter.
	 *
	 * @param file zero-based file index.
	 * @return lowercase file letter.
	 */
	private static char fileLowercase(int file) {
		return (char) ('a' + file);
	}

	/**
	 * Used for retrieving a random Chess960 {@code Position}.
	 *
	 * @return A random Chess960 {@code Position}
	 * @see #getChess960ByIndex(int)
	 */
	public static Position getRandomChess960() {
		return getChess960ByIndex(ThreadLocalRandom.current().nextInt(0, CHESS_960_POSITIONS.length));
	}

	/**
	 * Used for retrieving a Chess960 {@code Position} by its index.
	 *
	 * @param index between 0 and 959 inclusive.
	 * @return A Chess960 {@code Position} by its index.
	 */
	public static Position getChess960ByIndex(int index) {
		return CHESS_960_POSITIONS[index].copyOf();
	}

	/**
	 * Used for retrieving all Chess960 {@code Position}s.
	 *
	 * @return An array of all Chess960 {@code Position}s
	 */
	public static Position[] getAllChess960Positions() {
		Position[] positions = new Position[CHESS_960_POSITIONS.length];
		for (int i = 0; i < CHESS_960_POSITIONS.length; i++) {
			positions[i] = CHESS_960_POSITIONS[i].copyOf();
		}
		return positions;
	}

	/**
	 * Used for retrieving the standard starting {@code Position} of a chess game.
	 *
	 * @return The standard starting {@code Position} of a chess game
	 */
	public static Position getStandardStartPosition() {
		return STANDARD_START_POSITION.copyOf();
	}

	/**
	 * Used for retrieving the standard starting FEN string of a chess game.
	 *
	 * @return The standard starting FEN string of a chess game
	 */
	public static String getStandardStartFEN() {
		return STANDARD_FEN;
	}

	/**
	 * Used for generating {@code count} randomized, legal positions. Start from
	 * either the standard initial setup or a random Chess960 start when
	 * {@code chess960} is true, advance a random warmup, then sample positions while
	 * sometimes branching from earlier states and occasionally restarting to
	 * increase variety.
	 *
	 * @param count    the number of positions to generate.
	 * @param chess960 whether to use a Chess960 initial position instead of the
	 *                 standard start.
	 * @return a list of randomized, legal positions.
	 */
	public static List<Position> getRandomPositions(int count, boolean chess960) {
		return getRandomPositionSeeds(count, chess960)
				.stream()
				.map(PositionSeed::position)
				.toList();
	}

	/**
	 * Generates random positions together with their immediate parents (the position
	 * before the last random move). When a seed was not able to play a move, the
	 * parent is {@code null}.
	 *
	 * @param count    number of positions to generate
	 * @param chess960 whether to start from Chess960 initial positions
	 * @return list of position/parent pairs
	 */
	public static List<PositionSeed> getRandomPositionSeeds(int count, boolean chess960) {
		if (count <= 0) {
			return Collections.emptyList();
		}

		final ThreadLocalRandom rnd = ThreadLocalRandom.current();

		final int warmupMinPlies = 5;
		final int warmupMaxPlies = 200;
		final int stepMinPlies = 1;
		final int stepMaxPlies = 8;
		final double branchProb = 0.35;
		final double restartProb = 0.05;

		Supplier<Position> freshStart = freshStartSupplier(chess960);

		Position seed = freshStart.get();
		Position[] holder = new Position[1];
		playRandomPlies(seed, randomRange(rnd, warmupMinPlies, warmupMaxPlies), holder);

		List<PositionSeed> positions = new ArrayList<>(count);
		List<Position> pool = new ArrayList<>(Math.max(32, count));
		pool.add(seed.copyOf());

		while (positions.size() < count) {
			Position base = chooseBase(pool, branchProb, rnd);
			holder[0] = null;
			playRandomPlies(base, randomRange(rnd, stepMinPlies, stepMaxPlies), holder);

			if (needsRestart(base, restartProb, rnd)) {
				addSnapshot(positions, pool, base, holder[0]);
				Position restart = freshStart.get();
				int kick = randomRange(rnd, warmupMinPlies / 2, warmupMaxPlies / 2);
				holder[0] = null;
				playRandomPlies(restart, kick, holder);
				pool.add(restart.copyOf());
				continue;
			}

			addSnapshot(positions, pool, base, holder[0]);
		}

		return positions;
	}

	/**
	 * Used for providing a supplier of fresh start positions, either Chess960 or
	 * the standard start.
	 *
	 * @param chess960 whether to use a Chess960 start; otherwise use the standard
	 *                 initial position.
	 * @return a supplier that yields fresh starting positions on demand.
	 */
	private static Supplier<Position> freshStartSupplier(boolean chess960) {
		return chess960 ? Setup::getRandomChess960 : Setup::getStandardStartPosition;
	}

	/**
	 * Used for applying up to {@code plies} random legal moves to {@code position},
	 * stopping early when no moves are available or an error occurs.
	 *
	 * @param position   the position to mutate by playing random moves.
	 * @param plies      the maximum number of plies to play.
	 * @param lastParent optional slot to capture the last parent position
	 */
	private static void playRandomPlies(Position position, int plies, Position[] lastParent) {
		for (int i = 0; i < plies; i++) {
			if (stopOnNoMove(position, lastParent)) {
				break;
			}
		}
	}

	/**
	 * Plays a random legal move or signals that no move is available.
	 * Updates {@code lastParent} when provided before mutating the position.
	 *
	 * @param position   position to inspect and mutate
	 * @param lastParent optional slot to capture the parent position
	 * @return true if no move could be played or an error occurred
	 */
	private static boolean stopOnNoMove(Position position, Position[] lastParent) {
		MoveList moves = position.getMoves();
		if (moves == null || sizeIsZeroSafe(moves)) {
			return true;
		}

		short move = moves.getRandomMove();
		if (move == Move.NO_MOVE) {
			return true;
		}

		if (lastParent != null) {
			lastParent[0] = position.copyOf();
		}

		try {
			position.play(move);
		} catch (Exception e) {
			return true;
		}

		return false;
	}

	/**
	 * Used for checking whether the provided move list reports zero size without
	 * throwing.
	 *
	 * @param moves the move list to inspect.
	 * @return {@code true} if {@code moves.size() == 0}; {@code false} if a call
	 *         fails or returns non-zero.
	 */
	private static boolean sizeIsZeroSafe(MoveList moves) {
		try {
			return moves.size() == 0;
		} catch (Exception ignore) {
			return false;
		}
	}

	/**
	 * Used for selecting a base position from the pool, branching with probability
	 * {@code branchProb}.
	 *
	 * @param pool       the pool of previously seen positions.
	 * @param branchProb the probability of branching to a random prior position.
	 * @param rnd        the random source.
	 * @return a copy of the chosen base position.
	 */
	private static Position chooseBase(List<Position> pool, double branchProb, ThreadLocalRandom rnd) {
		if (pool.size() > 1 && rnd.nextDouble() < branchProb) {
			return pool.get(rnd.nextInt(pool.size())).copyOf();
		}
		return pool.get(pool.size() - 1).copyOf();
	}

	/**
	 * Used for deciding whether to restart from a fresh start based on terminality
	 * or random chance.
	 *
	 * @param p           the position to test for terminality.
	 * @param restartProb the probability to restart even if not terminal.
	 * @param rnd         the random source.
	 * @return {@code true} if a restart is advised; otherwise {@code false}.
	 */
	private static boolean needsRestart(Position p, double restartProb, ThreadLocalRandom rnd) {
		return isTerminal(p) || rnd.nextDouble() < restartProb;
	}

	/**
	 * Used for checking whether the given position is terminal.
	 *
	 * @param position the position to examine.
	 * @return {@code true} if no legal moves or an error occurs; otherwise
	 *         {@code false}.
	 */
	private static boolean isTerminal(Position position) {
		try {
			MoveList moves = position.getMoves();
			return moves == null || sizeIsZeroSafe(moves);
		} catch (Exception ignore) {
			return true;
		}
	}

	/**
	 * Used for sampling an integer uniformly from the inclusive range
	 * {@code [inclusiveMin, inclusiveMax]}.
	 *
	 * @param rnd          the random source.
	 * @param inclusiveMin the inclusive lower bound.
	 * @param inclusiveMax the inclusive upper bound.
	 * @return a random integer within the inclusive range.
	 */
	private static int randomRange(ThreadLocalRandom rnd, int inclusiveMin, int inclusiveMax) {
		return rnd.nextInt(inclusiveMin, inclusiveMax + 1);
	}

	/**
	 * Used for copying {@code src} and appending it to both the output list and the
	 * reusable pool.
	 *
	 * @param out    the list collecting result positions.
	 * @param pool   the pool of prior positions to branch from.
	 * @param src    the source position to copy.
	 * @param parent optional parent position to copy into the seed.
	 */
	private static void addSnapshot(List<PositionSeed> out, List<Position> pool, Position src, Position parent) {
		Position copy = src.copyOf();
		out.add(new PositionSeed(parent != null ? parent.copyOf() : null, copy));
		pool.add(copy);
	}

	/**
	 * Represents a snapshot of a position in the game, including its parent
	 * position.
	 *
	 * @param parent   the parent position, or {@code null}
	 * @param position the current position
	 */
	public record PositionSeed(
		/**
		 * Stores the parent.
		 */
		Position parent,
		/**
		 * Stores the position.
		 */
		Position position
	) {
	}
}
