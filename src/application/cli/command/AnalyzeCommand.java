package application.cli.command;

import static application.cli.Constants.CMD_ANALYZE;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_HASH;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_MAX_DURATION;
import static application.cli.Constants.OPT_MAX_NODES;
import static application.cli.Constants.OPT_MULTIPV;
import static application.cli.Constants.OPT_NODES;
import static application.cli.Constants.OPT_NO_WDL;
import static application.cli.Constants.OPT_PROTOCOL_PATH;
import static application.cli.Constants.OPT_PROTOCOL_PATH_SHORT;
import static application.cli.Constants.OPT_THREADS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_WDL;
import static application.cli.EngineOps.analysePositionOrExit;
import static application.cli.EngineOps.configureEngine;
import static application.cli.EngineOps.parsePositionOrNull;
import static application.cli.EngineOps.resolveWdlFlag;
import static application.cli.Format.formatBound;
import static application.cli.Format.formatChances;
import static application.cli.Format.formatEvaluation;
import static application.cli.Format.formatPvMovesSan;
import static application.cli.Format.safeSan;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import application.Config;
import chess.core.Move;
import chess.core.Position;
import chess.uci.Analysis;
import chess.uci.Engine;
import chess.uci.Output;
import chess.uci.Protocol;
import utility.Argv;

/**
 * Implements the {@code analyze} CLI command.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class AnalyzeCommand {

	/**
	 * Maximum line width used when wrapping PV move output.
	 */
	private static final int ANALYSIS_PV_WRAP_WIDTH = 100;

	/**
	 * Utility class; prevent instantiation.
	 */
	private AnalyzeCommand() {
		// utility
	}

	/**
	 * Handles {@code analyze}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runAnalyze(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		String fen = a.string(OPT_FEN);
		String protoPath = CommandSupport.optional(a.string(OPT_PROTOCOL_PATH, OPT_PROTOCOL_PATH_SHORT),
				Config.getProtocolPath());
		long nodesCap = Math.max(1, CommandSupport.optional(a.lng(OPT_MAX_NODES, OPT_NODES), Config.getMaxNodes()));
		long durMs = Math.max(1,
				CommandSupport.optionalDurationMs(a.duration(OPT_MAX_DURATION), Config.getMaxDuration()));
		Integer multipv = a.integer(OPT_MULTIPV);
		Integer threads = a.integer(OPT_THREADS);
		Integer hash = a.integer(OPT_HASH);
		boolean wdl = a.flag(OPT_WDL);
		boolean noWdl = a.flag(OPT_NO_WDL);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}
		a.ensureConsumed();

		if (wdl && noWdl) {
			System.err.println(String.format("analyze: only one of %s or %s may be set", OPT_WDL, OPT_NO_WDL));
			System.exit(2);
			return;
		}

		List<String> fens = CommandSupport.resolveFenInputs(CMD_ANALYZE, input, fen);
		Protocol protocol = EngineSupport.loadProtocolOrExit(protoPath, verbose);
		Optional<Boolean> wdlFlag = resolveWdlFlag(wdl, noWdl);

		try (Engine engine = new Engine(protocol)) {
			configureEngine(CMD_ANALYZE, engine, threads, hash, multipv, wdlFlag);
			String engineLabel = protocol.getName() != null ? protocol.getName() : protocol.getPath();
			System.out.println("Engine: " + engineLabel);
			for (int i = 0; i < fens.size(); i++) {
				String entry = fens.get(i);
				Position pos = parsePositionOrNull(entry, CMD_ANALYZE, verbose);
				if (pos == null) {
					continue;
				}
				Analysis analysis = analysePositionOrExit(engine, pos, nodesCap, durMs, CMD_ANALYZE, verbose);
				if (analysis == null) {
					return;
				}

				if (i > 0) {
					System.out.println();
				}
				printAnalysisSummary(pos, analysis);
			}
		} catch (Exception ex) {
			System.err.println("analyze: failed to initialize engine: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
		}
	}

	/**
	 * Prints a formatted analysis summary for a position.
	 *
	 * @param pos      analyzed position
	 * @param analysis engine analysis results
	 */
	private static void printAnalysisSummary(Position pos, Analysis analysis) {
		System.out.println(String.format("FEN: %s", pos.toString()));
		System.out.println();
		if (analysis == null || analysis.isEmpty()) {
			System.out.println("analysis: (no output)");
			return;
		}
		int pivots = Math.max(1, analysis.getPivots());
		for (int pv = 1; pv <= pivots; pv++) {
			Output best = analysis.getBestOutput(pv);
			if (best == null) {
				continue;
			}
			String eval = formatEvaluation(best.getEvaluation());
			String wdl = formatChances(best.getChances());
			String bound = formatBound(best.getBound());
			System.out.printf("PV%d%n", pv);
			System.out.printf("  eval: %s%n", eval);
			System.out.printf("  depth: %d (sel %d)%n", best.getDepth(), best.getSelectiveDepth());
			System.out.printf(
					"  nodes: %s  nps: %s  time: %s%n",
					CommandSupport.formatCount(best.getNodes()),
					CommandSupport.formatCount(best.getNodesPerSecond()),
					formatMillis(best.getTime()));
			System.out.printf("  wdl: %s  bound: %s%n", wdl, bound);
			short bestMove = analysis.getBestMove(pv);
			if (bestMove != Move.NO_MOVE) {
				System.out.printf("  best: %s (%s)%n", Move.toString(bestMove), safeSan(pos, bestMove));
			}
			String pvLine = formatPvMovesSan(pos, best.getMoves());
			if (!pvLine.isEmpty()) {
				printWrappedLine("  line: ", pvLine, ANALYSIS_PV_WRAP_WIDTH);
			}
			if (pv < pivots) {
				System.out.println();
			}
		}
	}

	/**
	 * Formats a millisecond duration into a human-readable label.
	 *
	 * @param millis duration in milliseconds
	 * @return formatted duration string
	 */
	private static String formatMillis(long millis) {
		if (millis < 1_000L) {
			return millis + "ms";
		}
		if (millis < 60_000L) {
			return String.format(Locale.US, "%.1fs", millis / 1000.0);
		}
		long seconds = millis / 1000L;
		long minutes = seconds / 60L;
		long remSeconds = seconds % 60L;
		return String.format(Locale.US, "%dm%02ds", minutes, remSeconds);
	}

	/**
	 * Wraps a long line at word boundaries and prints it with indentation.
	 *
	 * @param prefix   prefix for the first line
	 * @param content  content to wrap
	 * @param maxWidth maximum line width
	 */
	private static void printWrappedLine(String prefix, String content, int maxWidth) {
		if (content == null || content.isBlank()) {
			return;
		}
		String trimmed = content.trim();
		String indent = " ".repeat(prefix.length());
		int width = Math.max(prefix.length() + 10, maxWidth);
		int available = width - prefix.length();
		String[] tokens = trimmed.split("\\s+");
		StringBuilder line = new StringBuilder();
		String currentPrefix = prefix;
		int currentAvailable = available;
		for (String token : tokens) {
			int separatorLen = line.isEmpty() ? 0 : 1;
			boolean fits = line.length() + separatorLen + token.length() <= currentAvailable;

			if (line.isEmpty()) {
				line.append(token);
			} else if (fits) {
				line.append(' ').append(token);
			} else {
				System.out.println(currentPrefix + line);

				currentPrefix = indent;
				currentAvailable = width - currentPrefix.length();

				line.setLength(0);
				line.append(token);
			}
		}

		if (!line.isEmpty()) {
			System.out.println(currentPrefix + line);
		}
	}
}
