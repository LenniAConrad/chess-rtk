package application.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

import application.cli.command.CommandFailure;
import chess.core.Position;
import chess.core.SAN;
import chess.struct.Game;
import chess.struct.Pgn;
import chess.struct.Record;

/**
 * PGN extraction helpers for CLI commands.
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class PgnOps {

	/**
	 * Utility class; prevent instantiation.
	 */
	private PgnOps() {
		// utility
	}

	/**
	 * Reads a PGN file and returns its parsed games or exits on failure.
	 *
	 * @param input   path to the PGN file
	 * @param verbose whether to print stack traces when parsing fails
	 * @param cmd     label of the invoking command (used in error output)
	 * @return list of {@link Game} instances found in the file
	 */
	public static List<Game> readPgnOrExit(Path input, boolean verbose, String cmd) {
		return readPgnOrExit(input, verbose, cmd, null);
	}

	/**
	 * Reads a PGN file and returns its parsed games or exits on failure.
	 *
	 * @param input        path to the PGN file
	 * @param verbose      whether to print stack traces when parsing fails
	 * @param cmd          label of the invoking command (used in error output)
	 * @param byteProgress optional callback receiving cumulative bytes read
	 * @return list of {@link Game} instances found in the file
	 */
	public static List<Game> readPgnOrExit(Path input, boolean verbose, String cmd, LongConsumer byteProgress) {
		try {
			return Pgn.read(input, byteProgress);
		} catch (Exception ex) {
			throw new CommandFailure(cmd + ": failed to read PGN: " + ex.getMessage(), ex, 2, verbose);
		}
	}

	/**
	 * Prepares a buffered writer for the given output path, ensuring parent directories exist.
	 *
	 * @param output  target file path
	 * @param verbose whether to print stack traces when creation fails
	 * @param cmd     label of the invoking command (used in error output)
	 * @return buffered writer open for writing
	 */
	public static BufferedWriter openWriterOrExit(Path output, boolean verbose, String cmd) {
		try {
			PathOps.ensureParentDir(output);
			return Files.newBufferedWriter(output);
		} catch (IOException e) {
			throw new CommandFailure(cmd + ": failed to prepare output: " + e.getMessage(), e, 2, verbose);
		}
	}

	/**
	 * Writes FEN positions derived from the provided games to the supplied writer.
	 *
	 * @param games    parsed PGN games
	 * @param writer   output writer where each line becomes a FEN (or FEN pair)
	 * @param mainline whether to include only each game's mainline
	 * @param pairs    whether to emit parent/child FEN pairs instead of single positions
	 * @return number of lines written
	 * @throws IOException when writing to the writer fails
	 */
	public static long writePgnFens(
			List<Game> games,
			BufferedWriter writer,
			boolean mainline,
			boolean pairs) throws IOException {
		return writePgnFens(games, writer, mainline, pairs, null);
	}

	/**
	 * Writes FEN positions and reports progress once per game.
	 */
	public static long writePgnFens(
			List<Game> games,
			BufferedWriter writer,
			boolean mainline,
			boolean pairs,
			Runnable progress) throws IOException {
		long lines = 0;
		for (Game game : games) {
			try {
				lines += writeGameFens(game, writer, mainline, pairs);
			} finally {
				if (progress != null) {
					progress.run();
				}
			}
		}
		return lines;
	}

	/**
	 * Writes FEN lines for a single game, honoring mainline/variation and pairing options.
	 *
	 * @param game     game whose records should be written
	 * @param writer   output writer for FEN lines
	 * @param mainline whether to include only the mainline
	 * @param pairs    whether to write parent/child FEN pairs
	 * @return number of lines written for this game
	 * @throws IOException when writing to the writer fails
	 */
	private static long writeGameFens(
			Game game,
			BufferedWriter writer,
			boolean mainline,
			boolean pairs) throws IOException {
		List<Record> records = mainline
				? extractRecordsMainline(game)
				: extractRecordsWithVariations(game);
		long lines = 0;
		for (Record rec : records) {
			if (!shouldWriteRecord(rec, pairs)) {
				continue;
			}
			Position parent = rec.getParent();
			Position pos = rec.getPosition();
			if (pairs) {
				writer.write(parent.toString());
				writer.write(' ');
			}
			writer.write(pos.toString());
			writer.newLine();
			lines++;
		}
		return lines;
	}

	/**
	 * Determines whether a record should be written for the current mode.
	 *
	 * @param rec   record to inspect
	 * @param pairs whether parent/child pairs are required
	 * @return {@code true} when the record has a position (and parent if pairs)
	 */
	private static boolean shouldWriteRecord(Record rec, boolean pairs) {
		Position pos = rec.getPosition();
		if (pos == null) {
			return false;
		}
		if (!pairs) {
			return true;
		}
		return rec.getParent() != null;
	}

	/**
	 * Extracts {@link Record} instances for every ply along the game's mainline.
	 *
	 * <p>Each record includes both the parent position and the child position
	 * resulting from that ply.
	 *
	 * @param game PGN game whose mainline to traverse
	 * @return list of records corresponding to each move on the mainline
	 */
	public static List<Record> extractRecordsMainline(Game game) {
		List<Record> positions = new ArrayList<>();
		Position start = game.getStartPosition() != null
				? game.getStartPosition().copy()
				: new Position(Game.STANDARD_START_FEN);

		Game.Node cur = game.getMainline();
		Position current = start.copy();
		while (cur != null) {
			Position parent = current.copy();
			Position child;
			try {
				short move = SAN.fromAlgebraic(parent, cur.getSan());
				child = parent.copy().play(move);
			} catch (IllegalArgumentException ex) {
				break;
			}
			positions.add(new Record().withParent(parent).withPosition(child.copy()));
			current = child;
			cur = cur.getNext();
		}
		return positions;
	}

	/**
	 * Extracts {@link Record} instances for every reachable ply in the movetext,
	 * including all variations and sidelines.
	 *
	 * <p>Illegal SAN moves terminate the current line but do not stop overall
	 * processing, as alternate variation paths are still visited.
	 *
	 * @param game PGN game whose moves and variations to traverse
	 * @return records for all traversed parent/child positions
	 */
	public static List<Record> extractRecordsWithVariations(Game game) {
		List<Record> positions = new ArrayList<>();
		Position start = game.getStartPosition() != null
				? game.getStartPosition().copy()
				: new Position(Game.STANDARD_START_FEN);

		/**
		 * Work item pairing a PGN node with its current position.
		 * Used to traverse variations without recursion.
		 *
		 * @param node node to process
		 * @param pos position at the node
		 */
		record Work(
			/**
			 * Stores the node.
			 */
			Game.Node node,
			/**
			 * Stores the pos.
			 */
			Position pos
		) {
		}

		java.util.ArrayDeque<Work> stack = new java.util.ArrayDeque<>();
		if (game.getMainline() != null) {
			stack.push(new Work(game.getMainline(), start.copy()));
		}
		for (Game.Node rootVar : game.getRootVariations()) {
			stack.push(new Work(rootVar, start.copy()));
		}

		while (!stack.isEmpty()) {
			Work work = stack.pop();
			Position current = work.pos();
			Game.Node cur = work.node();
			while (cur != null) {
				Position parent = current.copy();
				Position child;
				try {
					short move = SAN.fromAlgebraic(parent, cur.getSan());
					child = parent.copy().play(move);
				} catch (IllegalArgumentException ex) {
					break;
				}
				positions.add(new Record().withParent(parent).withPosition(child.copy()));
				for (Game.Node variation : cur.getVariations()) {
					// PGN variations branch from the position before the current move.
					stack.push(new Work(variation, parent.copy()));
				}
				current = child;
				cur = cur.getNext();
			}
		}
		return positions;
	}
}
