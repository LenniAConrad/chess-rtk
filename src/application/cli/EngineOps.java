package application.cli;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import static application.cli.Constants.*;

import chess.core.Position;
import chess.uci.Engine;
import chess.uci.Analysis;

/**
 * Engine-related CLI helpers.
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class EngineOps {

	/**
	 * Utility class; prevent instantiation.
	 */
	private EngineOps() {
		// utility
	}

	/**
	 * Applies common engine options (threads/hash/multipv/WDL) with validation.
	 *
	 * @param cmd     command label for validation errors
	 * @param engine  engine instance to configure
	 * @param threads optional thread count
	 * @param hash    optional hash size (MB)
	 * @param multipv optional MultiPV value
	 * @param wdl     optional WDL toggle
	 */
	public static void configureEngine(
			String cmd,
			Engine engine,
			Integer threads,
			Integer hash,
			Integer multipv,
			Optional<Boolean> wdl) {
		Objects.requireNonNull(wdl, "wdl");
		if (threads != null) {
			Validation.requirePositive(cmd, OPT_THREADS, threads);
			engine.setThreadAmount(threads);
		}
		if (hash != null) {
			Validation.requirePositive(cmd, OPT_HASH, hash);
			engine.setHashSize(hash);
		}
		if (multipv != null) {
			Validation.requirePositive(cmd, OPT_MULTIPV, multipv);
			engine.setMultiPivot(multipv);
		}
		if (wdl.isPresent()) {
			engine.showWinDrawLoss(wdl.get());
		}
	}

	/**
	 * Parses a FEN string into a {@link Position}, returning null on failure.
	 *
	 * @param entry   raw FEN string
	 * @param cmd     command label used in diagnostics
	 * @param verbose whether to print stack traces on failure
	 * @return parsed position or {@code null} when invalid
	 */
	public static Position parsePositionOrNull(String entry, String cmd, boolean verbose) {
		try {
			return new Position(entry.trim());
		} catch (IllegalArgumentException ex) {
			System.err.println(cmd + ": invalid FEN skipped: " + entry);
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			return null;
		}
	}

	/**
	 * Runs analysis on a single position and exits on IO failure.
	 *
	 * @param engine  engine to run
	 * @param pos     position to analyze
	 * @param nodesCap node limit
	 * @param durMs   time limit in milliseconds
	 * @param cmd     command label used in diagnostics
	 * @param verbose whether to print stack traces on failure
	 * @return analysis result or {@code null} if the process exits
	 */
	public static Analysis analysePositionOrExit(
			Engine engine,
			Position pos,
			long nodesCap,
			long durMs,
			String cmd,
			boolean verbose) {
		Analysis analysis = new Analysis();
		try {
			engine.analyse(pos, analysis, null, nodesCap, durMs);
			return analysis;
		} catch (IOException ioe) {
			System.err.println(cmd + ": engine failed: " + ioe.getMessage());
			if (verbose) {
				ioe.printStackTrace(System.err);
			}
			System.exit(2);
			return null;
		}
	}

	/**
	 * Resolves WDL flags into a tri-state optional.
	 *
	 * @param wdl   {@code true} to enable WDL output
	 * @param noWdl {@code true} to disable WDL output
	 * @return optional WDL state (empty when no flag was set)
	 */
	public static Optional<Boolean> resolveWdlFlag(boolean wdl, boolean noWdl) {
		if (wdl) {
			return Optional.of(Boolean.TRUE);
		}
		if (noWdl) {
			return Optional.of(Boolean.FALSE);
		}
		return Optional.empty();
	}
}
