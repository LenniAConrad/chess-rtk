package application.cli.command;

import static application.cli.Constants.CMD_PERFT;
import static application.cli.Constants.OPT_DEPTH;
import static application.cli.Constants.OPT_DEPTH_SHORT;
import static application.cli.Constants.OPT_DIVIDE;
import static application.cli.Constants.OPT_FORMAT;
import static application.cli.Constants.OPT_PER_MOVE;
import static application.cli.Constants.OPT_SUITE;
import static application.cli.Constants.OPT_THREADS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Validation.requireNonNegative;
import static application.cli.Validation.requirePositive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToLongFunction;

import application.console.Bar;
import chess.core.Move;
import chess.core.Position;
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
	 * Detailed key-value divide output.
	 */
	private static final String FORMAT_DETAIL = "detail";

	/**
	 * Aligned table divide output.
	 */
	private static final String FORMAT_TABLE = "table";

	/**
	 * Stockfish-compatible divide output.
	 */
	private static final String FORMAT_STOCKFISH = "stockfish";

	/**
	 * Divide table move column heading.
	 */
	private static final String TABLE_MOVE_HEADING = "Move";

	/**
	 * Divide table total row label.
	 */
	private static final String TABLE_TOTAL_LABEL = "Total";

	/**
	 * Numeric columns shown in table divide output.
	 */
	private static final List<StatColumn> TABLE_COLUMNS = List.of(
			new StatColumn("Nodes", Stats::nodes),
			new StatColumn("Captures", Stats::captures),
			new StatColumn("En-passant", Stats::enPassant),
			new StatColumn("Castles", Stats::castles),
			new StatColumn("Promotions", Stats::promotions),
			new StatColumn("Checks", Stats::checks),
			new StatColumn("Checkmates", Stats::checkmates));

	/**
	 * Utility class; prevent instantiation.
	 */
	private PerftCommand() {
		// utility
	}

	/**
	 * Supported perft output formats.
	 */
	private enum PerftFormat {
		/**
		 * Existing multi-line or key-value output.
		 */
		DETAIL,
		/**
		 * Human-readable per-root-move table.
		 */
		TABLE,
		/**
		 * Stockfish-style divide rows.
		 */
		STOCKFISH
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
		boolean divideFlag = a.flag(OPT_DIVIDE, OPT_PER_MOVE);
		Integer depth = a.integer(OPT_DEPTH, OPT_DEPTH_SHORT);
		Integer threads = a.integer(OPT_THREADS);
		String formatValue = a.string(OPT_FORMAT);
		PerftFormat format = parseFormat(formatValue, commandName);
		boolean divide = divideFlag || format == PerftFormat.TABLE || format == PerftFormat.STOCKFISH;
		Position position = CommandSupport.resolvePositionArgument(a, commandName, true, verbose);

		if (depth == null) {
			throw new CommandFailure(commandName + " requires " + OPT_DEPTH + " <n>", 2);
		}
		requireNonNegative(commandName, OPT_DEPTH, depth);
		int workerThreads = threads == null ? 1 : threads;
		requirePositive(commandName, OPT_THREADS, workerThreads);

		try {
			if (divide) {
				PerftFormat divideFormat = formatValue == null || formatValue.isBlank() ? PerftFormat.TABLE : format;
				DivideResult result = divideFormat == PerftFormat.STOCKFISH
						? Perft.divideNodes(position, depth, workerThreads)
						: Perft.divide(position, depth, workerThreads);
				printDivide(position, result, heading, divideFormat);
			} else {
				printResult(position, Perft.run(position, depth, workerThreads), heading);
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			LogService.error(ex, commandName + " interrupted");
			throw new CommandFailure(commandName + ": interrupted", ex, 130, verbose);
		}
	}

	/**
	 * Resolves perft output format.
	 *
	 * @param value optional {@code --format} value
	 * @param commandName command name for diagnostics
	 * @return output format
	 */
	private static PerftFormat parseFormat(String value, String commandName) {
		if (value == null || value.isBlank()) {
			return PerftFormat.DETAIL;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case FORMAT_DETAIL, "detailed", "lines" -> PerftFormat.DETAIL;
			case FORMAT_TABLE -> PerftFormat.TABLE;
			case FORMAT_STOCKFISH, "sf" -> PerftFormat.STOCKFISH;
			default -> throw new CommandFailure(commandName + ": unsupported " + OPT_FORMAT + " value: " + value
					+ " (expected detail, table, or stockfish)", 2);
		};
	}

	/**
	 * Handles a perft-suite command using the core move generator.
	 *
	 * @param a argument parser
	 * @param commandName command name for diagnostics
	 */
	private static void runPerftSuite(Argv a, String commandName) {
		Integer depth = a.integer(OPT_DEPTH, OPT_DEPTH_SHORT);
		Integer threads = a.integer(OPT_THREADS);
		Path suite = a.path(OPT_SUITE);
		a.ensureConsumed();

		int workerThreads = threads == null ? 1 : threads;
		requirePositive(commandName, OPT_THREADS, workerThreads);
		if (suite != null) {
			runCustomPerftSuite(commandName, suite, depth, workerThreads);
			return;
		}

		int maxDepth = depth == null ? PerftSuite.DEFAULT_MAX_DEPTH : depth;
		requirePositive(commandName, OPT_DEPTH, maxDepth);
		if (maxDepth > PerftSuite.MAX_REFERENCE_DEPTH) {
			throw new CommandFailure(commandName + " supports " + OPT_DEPTH + " 1.." + PerftSuite.MAX_REFERENCE_DEPTH,
					2);
		}
		Bar bar = new Bar(PerftSuite.rowCount(maxDepth), "perft-suite", false, System.err);
		try {
			PerftSuite.Summary summary = PerftSuite.validate(maxDepth, workerThreads, bar::step);
			bar.finish();
			PerftSuite.print(summary);
			if (!summary.matches()) {
				throw new CommandFailure(commandName + ": validation failed", 3);
			}
		} catch (InterruptedException ex) {
			bar.finish();
			Thread.currentThread().interrupt();
			LogService.error(ex, commandName + " interrupted");
			throw new CommandFailure(commandName + ": interrupted", ex, 130, false);
		}
	}

	/**
	 * Runs a caller-provided perft suite file.
	 *
	 * @param commandName command name for diagnostics
	 * @param suite suite path
	 * @param defaultDepth optional default depth
	 * @param workerThreads worker threads
	 */
	private static void runCustomPerftSuite(
			String commandName,
			Path suite,
			Integer defaultDepth,
			int workerThreads) {
		List<CustomPerftCase> cases = readCustomPerftSuite(commandName, suite, defaultDepth);
		Bar bar = cases.size() > 1 ? new Bar(cases.size(), "perft-suite", false, System.err) : null;
		List<PerftSuite.Row> rows = new ArrayList<>();
		int maxDepth = 1;
		long started = System.nanoTime();
		try {
			for (int i = 0; i < cases.size(); i++) {
				CustomPerftCase testCase = cases.get(i);
				Result result = Perft.run(new Position(testCase.fen()), testCase.depth(), workerThreads);
				rows.add(new PerftSuite.Row(
						i + 1,
						testCase.name(),
						testCase.depth(),
						testCase.fen(),
						testCase.nodes(),
						result.stats().nodes(),
						result.nanos()));
				maxDepth = Math.max(maxDepth, testCase.depth());
				CommandSupport.step(bar);
			}
			CommandSupport.finish(bar);
			bar = null;
			PerftSuite.Summary summary = new PerftSuite.Summary(maxDepth, rows, System.nanoTime() - started);
			PerftSuite.print(summary);
			if (!summary.matches()) {
				throw new CommandFailure(commandName + ": validation failed", 3);
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			LogService.error(ex, commandName + " interrupted");
			throw new CommandFailure(commandName + ": interrupted", ex, 130, false);
		} finally {
			CommandSupport.finish(bar);
		}
	}

	/**
	 * Reads custom perft suite cases.
	 *
	 * @param commandName command name for diagnostics
	 * @param suite suite path
	 * @param defaultDepth optional default depth
	 * @return parsed cases
	 */
	private static List<CustomPerftCase> readCustomPerftSuite(
			String commandName,
			Path suite,
			Integer defaultDepth) {
		int fallbackDepth = defaultDepth == null ? 1 : defaultDepth;
		requirePositive(commandName, OPT_DEPTH, fallbackDepth);
		try {
			List<CustomPerftCase> cases = new ArrayList<>();
			List<String> lines = Files.readAllLines(suite, StandardCharsets.UTF_8);
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i).trim();
				if (!line.isEmpty() && !line.startsWith("#")) {
					cases.add(parseCustomPerftCase(commandName, line, i + 1, fallbackDepth));
				}
			}
			if (cases.isEmpty()) {
				throw new CommandFailure(commandName + ": custom suite has no cases: " + suite, 2);
			}
			return cases;
		} catch (IOException ex) {
			throw new CommandFailure(commandName + ": failed to read suite: " + ex.getMessage(), ex, 2, false);
		}
	}

	/**
	 * Parses one custom suite line.
	 *
	 * @param commandName command name for diagnostics
	 * @param line suite line
	 * @param lineNumber one-based line number
	 * @param fallbackDepth fallback depth
	 * @return parsed case
	 */
	private static CustomPerftCase parseCustomPerftCase(
			String commandName,
			String line,
			int lineNumber,
			int fallbackDepth) {
		String[] parts = line.split("\\t");
		try {
			CustomPerftCase testCase = switch (parts.length) {
				case 4 -> new CustomPerftCase(parts[0].trim(), parsePositiveInt(parts[1], "depth"),
						parts[2].trim(), parseLong(parts[3], "nodes"));
				case 3 -> parseThreeColumnCase(parts, fallbackDepth);
				case 2 -> new CustomPerftCase("case " + lineNumber, fallbackDepth, parts[0].trim(),
						parseLong(parts[1], "nodes"));
				default -> throw new IllegalArgumentException("expected 2, 3, or 4 tab-separated columns");
			};
			new Position(testCase.fen());
			requirePositive(commandName, OPT_DEPTH, testCase.depth());
			return testCase;
		} catch (RuntimeException ex) {
			throw new CommandFailure(commandName + ": invalid suite line " + lineNumber + ": " + ex.getMessage(),
					ex, 2, false);
		}
	}

	/**
	 * Parses a three-column custom suite row.
	 *
	 * @param parts row parts
	 * @param fallbackDepth fallback depth
	 * @return parsed case
	 */
	private static CustomPerftCase parseThreeColumnCase(String[] parts, int fallbackDepth) {
		if (parts[0].contains("/")) {
			return new CustomPerftCase("case", parsePositiveInt(parts[1], "depth"), parts[0].trim(),
					parseLong(parts[2], "nodes"));
		}
		return new CustomPerftCase(parts[0].trim(), fallbackDepth, parts[1].trim(), parseLong(parts[2], "nodes"));
	}

	/**
	 * Parses a positive integer from a suite field.
	 *
	 * @param value field value
	 * @param field field name
	 * @return parsed value
	 */
	private static int parsePositiveInt(String value, String field) {
		int parsed = Integer.parseInt(value.trim());
		if (parsed <= 0) {
			throw new IllegalArgumentException(field + " must be positive");
		}
		return parsed;
	}

	/**
	 * Parses a long from a suite field.
	 *
	 * @param value field value
	 * @param field field name
	 * @return parsed value
	 */
	private static long parseLong(String value, String field) {
		long parsed = Long.parseLong(value.trim().replace("_", "").replace(",", ""));
		if (parsed < 0L) {
			throw new IllegalArgumentException(field + " must be non-negative");
		}
		return parsed;
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
	 * @param format divide output format
	 */
	private static void printDivide(Position position, DivideResult result, String heading, PerftFormat format) {
		if (format == PerftFormat.STOCKFISH) {
			printStockfishDivide(result);
			return;
		}
		System.out.println("FEN: " + position);
		if (format == PerftFormat.TABLE) {
			System.out.printf("Perft divide (depth %d)%n", result.depth());
			printDivideTable(result);
			printDivideSummary(result);
			return;
		}
		System.out.println(heading + " divide depth " + result.depth());
		for (DivideEntry entry : result.entries()) {
			System.out.println(Move.toString(entry.move()) + ": " + oneLineStats(entry.stats()));
		}
		System.out.println("total:");
		printStats(result.total());
		printTiming(result.nanos(), result.nodesPerSecond());
	}

	/**
	 * Prints Stockfish-style divide output.
	 *
	 * @param result divide result
	 */
	private static void printStockfishDivide(DivideResult result) {
		for (DivideEntry entry : result.entries()) {
			System.out.println(Move.toString(entry.move()) + ": " + entry.stats().nodes());
		}
		System.out.println();
		System.out.println("Nodes searched: " + result.total().nodes());
	}

	/**
	 * Prints aligned detailed divide output.
	 *
	 * @param result divide result
	 */
	private static void printDivideTable(DivideResult result) {
		int moveWidth = moveColumnWidth(result.entries());
		int[] statWidths = statColumnWidths(result);
		printTableHeader(moveWidth, statWidths);
		for (DivideEntry entry : result.entries()) {
			printTableStatsRow(moveWidth, statWidths, Move.toString(entry.move()), entry.stats());
		}
		printTableStatsRow(moveWidth, statWidths, TABLE_TOTAL_LABEL, result.total());
	}

	/**
	 * Prints a compact table summary.
	 *
	 * @param result divide result
	 */
	private static void printDivideSummary(DivideResult result) {
		System.out.printf(Locale.ROOT, "Summary: moves=%d nodes=%d speed=%s time-ms=%.3f%n",
				result.entries().size(),
				result.total().nodes(),
				speed(result.nodesPerSecond()),
				result.nanos() / 1_000_000.0);
	}

	/**
	 * Returns the width needed for the move column.
	 *
	 * @param entries divide entries
	 * @return column width
	 */
	private static int moveColumnWidth(List<DivideEntry> entries) {
		int width = TABLE_TOTAL_LABEL.length();
		for (DivideEntry entry : entries) {
			width = Math.max(width, Move.toString(entry.move()).length());
		}
		return width;
	}

	/**
	 * Calculates widths for every numeric stat column.
	 *
	 * @param result divide result
	 * @return column widths
	 */
	private static int[] statColumnWidths(DivideResult result) {
		int[] widths = new int[TABLE_COLUMNS.size()];
		for (int i = 0; i < TABLE_COLUMNS.size(); i++) {
			StatColumn column = TABLE_COLUMNS.get(i);
			widths[i] = column.heading().length();
			widths[i] = Math.max(widths[i], statText(result.total(), column).length());
		}
		for (DivideEntry entry : result.entries()) {
			for (int i = 0; i < TABLE_COLUMNS.size(); i++) {
				widths[i] = Math.max(widths[i], statText(entry.stats(), TABLE_COLUMNS.get(i)).length());
			}
		}
		return widths;
	}

	/**
	 * Prints the divide table header.
	 *
	 * @param moveWidth move column width
	 * @param statWidths numeric stat column widths
	 */
	private static void printTableHeader(int moveWidth, int[] statWidths) {
		printTableCells(moveWidth, statWidths, TABLE_MOVE_HEADING, tableHeadings());
	}

	/**
	 * Prints one divide table stats row.
	 *
	 * @param move move column width
	 * @param statWidths numeric stat column widths
	 * @param label row label
	 * @param stats row counters
	 */
	private static void printTableStatsRow(int moveWidth, int[] statWidths, String label, Stats stats) {
		String[] cells = new String[TABLE_COLUMNS.size()];
		for (int i = 0; i < TABLE_COLUMNS.size(); i++) {
			cells[i] = statText(stats, TABLE_COLUMNS.get(i));
		}
		printTableCells(moveWidth, statWidths, label, cells);
	}

	/**
	 * Prints one divide table row.
	 *
	 * @param moveWidth move column width
	 * @param statWidths numeric stat column widths
	 * @param moveCell move column text
	 * @param statCells numeric stat cell text
	 */
	private static void printTableCells(int moveWidth, int[] statWidths, String moveCell, String[] statCells) {
		StringBuilder row = new StringBuilder();
		appendPadded(row, moveCell, moveWidth, true);
		for (int i = 0; i < statCells.length; i++) {
			row.append("  ");
			appendPadded(row, statCells[i], statWidths[i], false);
		}
		System.out.println(row);
	}

	/**
	 * Appends one padded table cell.
	 *
	 * @param row target row
	 * @param text cell text
	 * @param width minimum cell width
	 * @param alignLeft whether to left-align text
	 */
	private static void appendPadded(StringBuilder row, String text, int width, boolean alignLeft) {
		int padding = Math.max(0, width - text.length());
		if (!alignLeft) {
			row.append(" ".repeat(padding));
		}
		row.append(text);
		if (alignLeft) {
			row.append(" ".repeat(padding));
		}
	}

	/**
	 * Returns table column headings.
	 *
	 * @return heading cells
	 */
	private static String[] tableHeadings() {
		String[] headings = new String[TABLE_COLUMNS.size()];
		for (int i = 0; i < TABLE_COLUMNS.size(); i++) {
			headings[i] = TABLE_COLUMNS.get(i).heading();
		}
		return headings;
	}

	/**
	 * One caller-provided perft validation case.
	 *
	 * @param name display name
	 * @param depth perft depth
	 * @param fen root FEN
	 * @param nodes expected node count
	 */
	private record CustomPerftCase(
			String name,
			int depth,
			String fen,
			long nodes) {
	}

	/**
	 * Formats one stats value.
	 *
	 * @param stats counters
	 * @param column stat column
	 * @return stats value text
	 */
	private static String statText(Stats stats, StatColumn column) {
		return Long.toString(column.getter().applyAsLong(stats));
	}

	/**
	 * Stores one numeric divide table column.
	 *
	 * @param heading column heading
	 * @param getter stat getter
	 */
	private record StatColumn(
			String heading,
			ToLongFunction<Stats> getter) {
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

	/**
	 * Formats calculated perft speed.
	 *
	 * @param nps nodes per second
	 * @return human-readable speed
	 */
	private static String speed(double nps) {
		if (nps >= 1_000_000.0) {
			return String.format(Locale.ROOT, "%.1fM nps", nps / 1_000_000.0);
		}
		if (nps >= 1_000.0) {
			return String.format(Locale.ROOT, "%.1fk nps", nps / 1_000.0);
		}
		return String.format(Locale.ROOT, "%.0f nps", nps);
	}

}
