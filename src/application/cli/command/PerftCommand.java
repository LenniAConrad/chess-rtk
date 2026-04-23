package application.cli.command;

import static application.cli.Constants.CMD_PERFT;
import static application.cli.Constants.ERR_INVALID_FEN;
import static application.cli.Constants.OPT_DEPTH;
import static application.cli.Constants.OPT_DEPTH_SHORT;
import static application.cli.Constants.OPT_DIVIDE;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_PER_MOVE;
import static application.cli.Constants.OPT_THREADS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Validation.requireNonNegative;
import static application.cli.Validation.requirePositive;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import application.console.Bar;
import chess.core.Move;
import chess.core.Position;
import chess.core.Setup;
import chess.debug.LogService;
import chess.debug.Perft;
import chess.debug.Perft.DivideEntry;
import chess.debug.Perft.DivideResult;
import chess.debug.Perft.Result;
import chess.debug.Perft.Stats;
import chess.debug.PerftSuite;
import utility.Argv;

/**
 * Implements the core {@code perft} and {@code perft-suite} commands.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PerftCommand {

	/**
	 * Stockfish executable option for {@code engine perft-suite}.
	 */
	private static final String OPT_STOCKFISH = "--stockfish";

	/**
	 * Utility class; prevent instantiation.
	 */
	private PerftCommand() {
		// utility
	}

	/**
	 * Handles {@code perft}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runPerft(Argv a) {
		runPerft(a, "engine " + CMD_PERFT, "perft");
	}

	/**
	 * Handles {@code perft-suite}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runPerftSuite(Argv a) {
		runPerftSuite(a, "engine perft-suite");
	}

	/**
	 * Handles a perft command using the core move generator.
	 *
	 * @param a argument parser
	 * @param commandName command name for diagnostics
	 * @param heading heading prefix for output
	 */
	private static void runPerft(Argv a, String commandName, String heading) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean divide = a.flag(OPT_DIVIDE, OPT_PER_MOVE);
		Integer depth = a.integer(OPT_DEPTH, OPT_DEPTH_SHORT);
		String fen = a.string(OPT_FEN);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}
		a.ensureConsumed();

		if (depth == null) {
			System.err.println(commandName + " requires " + OPT_DEPTH + " <n>");
			System.exit(2);
			return;
		}
		requireNonNegative(commandName, OPT_DEPTH, depth);

		Position position;
		try {
			position = (fen == null || fen.isEmpty())
					? Setup.getStandardStartPosition()
					: new Position(fen.trim());
		} catch (IllegalArgumentException ex) {
			System.err.println(ERR_INVALID_FEN + (ex.getMessage() == null ? "" : ex.getMessage()));
			LogService.error(ex, commandName + ": invalid FEN", "FEN: " + fen);
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
			return;
		}

		if (divide) {
			printDivide(position, Perft.divide(position, depth), heading);
		} else {
			printResult(position, Perft.run(position, depth), heading);
		}
	}

	/**
	 * Handles a perft-suite command using the core move generator.
	 *
	 * @param a argument parser
	 * @param commandName command name for diagnostics
	 */
	private static void runPerftSuite(Argv a, String commandName) {
		Integer depth = a.integer(OPT_DEPTH, OPT_DEPTH_SHORT);
		String stockfish = a.string(OPT_STOCKFISH);
		Integer threads = a.integer(OPT_THREADS);
		a.ensureConsumed();

		int maxDepth = depth == null ? PerftSuite.DEFAULT_MAX_DEPTH : depth;
		requirePositive(commandName, OPT_DEPTH, maxDepth);
		int workerThreads = threads == null ? 1 : threads;
		requirePositive(commandName, OPT_THREADS, workerThreads);
		String command = stockfish == null || stockfish.isBlank()
				? PerftSuite.DEFAULT_STOCKFISH
				: stockfish.trim();

		Bar bar = new Bar(PerftSuite.rowCount(maxDepth), "perft-suite", false, System.err);
		try {
			PerftSuite.Summary summary = PerftSuite.compareWithStockfish(maxDepth, command, workerThreads, bar::step);
			bar.finish();
			PerftSuite.print(summary);
			if (!summary.matches()) {
				System.exit(3);
			}
		} catch (IOException ex) {
			bar.finish();
			System.err.println(commandName + ": Stockfish comparison failed: " + ex.getMessage());
			LogService.error(ex, commandName + " failed", "stockfish: " + command);
			System.exit(4);
		} catch (InterruptedException ex) {
			bar.finish();
			Thread.currentThread().interrupt();
			System.err.println(commandName + ": interrupted");
			LogService.error(ex, commandName + " interrupted", "stockfish: " + command);
			System.exit(130);
		}
	}

	/**
	 * Prints a detailed perft result.
	 *
	 * @param position root position
	 * @param result result
	 * @param heading heading prefix for output
	 */
	private static void printResult(Position position, Result result, String heading) {
		System.out.println("FEN: " + position);
		System.out.println(heading + " depth " + result.depth());
		printStats(result.stats());
		printTiming(result.nanos(), result.nodesPerSecond());
	}

	/**
	 * Prints divide output.
	 *
	 * @param position root position
	 * @param result divide result
	 * @param heading heading prefix for output
	 */
	private static void printDivide(Position position, DivideResult result, String heading) {
		System.out.println("FEN: " + position);
		System.out.println(heading + " divide depth " + result.depth());
		for (DivideEntry entry : result.entries()) {
			System.out.println(Move.toString(entry.move()) + ": " + oneLineStats(entry.stats()));
		}
		System.out.println("total:");
		printStats(result.total());
		printTiming(result.nanos(), result.nodesPerSecond());
	}

	/**
	 * Prints multi-line counters.
	 *
	 * @param stats counters
	 */
	private static void printStats(Stats stats) {
		System.out.println("nodes: " + stats.nodes());
		System.out.println("captures: " + stats.captures());
		System.out.println("en-passant: " + stats.enPassant());
		System.out.println("castles: " + stats.castles());
		System.out.println("promotions: " + stats.promotions());
		System.out.println("checks: " + stats.checks());
		System.out.println("checkmates: " + stats.checkmates());
	}

	/**
	 * Formats one divide row.
	 *
	 * @param stats counters
	 * @return one-line text
	 */
	private static String oneLineStats(Stats stats) {
		return "nodes=" + stats.nodes()
				+ " captures=" + stats.captures()
				+ " en-passant=" + stats.enPassant()
				+ " castles=" + stats.castles()
				+ " promotions=" + stats.promotions()
				+ " checks=" + stats.checks()
				+ " checkmates=" + stats.checkmates();
	}

	/**
	 * Prints elapsed time and throughput.
	 *
	 * @param nanos elapsed nanoseconds
	 * @param nps nodes per second
	 */
	private static void printTiming(long nanos, double nps) {
		System.out.printf(Locale.ROOT, "time-ms: %.3f%n", nanos / 1_000_000.0);
		System.out.printf(Locale.ROOT, "nps: %.0f%n", nps);
	}
}
