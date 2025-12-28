package application.cli;

import java.util.List;

import chess.classical.Wdl;
import chess.core.Position;
import chess.eval.Backend;
import chess.eval.Evaluator;
import chess.eval.Result;

import static application.cli.Format.formatBackendLabel;
import static application.cli.Format.formatSigned;
import static application.cli.Format.formatWdl;

/**
 * Evaluation helpers for the CLI.
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class EvalOps {

	/**
	 * Utility class; prevent instantiation.
	 */
	private EvalOps() {
		// utility
	}

	/**
	 * Evaluates a position using classical heuristics and wraps the result in a {@link Result}.
	 *
	 * @param pos            position to evaluate
	 * @param terminalAware  whether to use terminal-aware WDL evaluation
	 * @return evaluation result representing classical backend output
	 */
	public static Result evaluateClassical(Position pos, boolean terminalAware) {
		Wdl wdl = Wdl.evaluate(pos, terminalAware);
		int cp = Wdl.evaluateStmCentipawns(pos);
		double value = (wdl.win() - wdl.loss()) / (double) Wdl.TOTAL;
		return new Result(Backend.CLASSICAL, wdl, value, cp);
	}

	/**
	 * Evaluates a list of FEN strings using the classical backend.
	 *
	 * @param fens           list of raw FEN strings to evaluate
	 * @param terminalAware  whether to consider terminal-aware heuristics
	 * @param includeFen     whether to print the original FEN with each result
	 * @param verbose        whether to print stack traces on failure
	 * @param cmdLabel       subcommand label used for diagnostics
	 * @return {@code true} when all entries were processed successfully
	 */
	public static boolean evalClassicalEntries(
			List<String> fens,
			boolean terminalAware,
			boolean includeFen,
			boolean verbose,
			String cmdLabel) {
		for (String entry : fens) {
			Position pos = EngineOps.parsePositionOrNull(entry, cmdLabel, verbose);
			if (pos == null) {
				continue;
			}
			try {
				Result result = evaluateClassical(pos, terminalAware);
				printEvalResult(pos, result, includeFen);
			} catch (Exception ex) {
				System.err.println(cmdLabel + ": failed to evaluate position: " + ex.getMessage());
				if (verbose) {
					ex.printStackTrace(System.err);
				}
				return false;
			}
		}
		return true;
	}

	/**
	 * Evaluates a list of FEN strings using an {@link Evaluator} instance.
	 *
	 * @param fens           list of raw FENs
	 * @param evaluator      evaluator instance used for LC0/classical evaluation
	 * @param lc0Only        whether to restrict output to LC0-only evaluations
	 * @param includeFen     whether to include the original FEN in the output
	 * @param verbose        whether to print stack traces on failure
	 * @param cmdLabel       subcommand label used for diagnostics
	 * @return {@code true} when evaluation succeeded for every FEN entry
	 */
	public static boolean evalEvaluatorEntries(
			List<String> fens,
			Evaluator evaluator,
			boolean lc0Only,
			boolean includeFen,
			boolean verbose,
			String cmdLabel) {
		for (String entry : fens) {
			Position pos = EngineOps.parsePositionOrNull(entry, cmdLabel, verbose);
			if (pos == null) {
				continue;
			}
			try {
				Result result = lc0Only
						? evaluator.evaluateLc0(pos)
						: evaluator.evaluate(pos);
				printEvalResult(pos, result, includeFen);
			} catch (IllegalStateException ex) {
				System.err.println(cmdLabel + ": LC0 unavailable: " + ex.getMessage());
				if (verbose) {
					ex.printStackTrace(System.err);
				}
				return false;
			} catch (Exception ex) {
				System.err.println(cmdLabel + ": failed to evaluate position: " + ex.getMessage());
				if (verbose) {
					ex.printStackTrace(System.err);
				}
				return false;
			}
		}
		return true;
	}

	/**
	 * Prints a {@link Result} to stdout using the CLI's preferred formatting.
	 *
	 * @param pos        evaluated position (for FEN and backend context)
	 * @param result     evaluation result produced by {@link Evaluator} or {@link #evaluateClassical}
	 * @param includeFen whether to print the original FEN alongside the result
	 */
	public static void printEvalResult(Position pos, Result result, boolean includeFen) {
		String backend = formatBackendLabel(result.backend());
		String wdl = formatWdl(result.wdl());
		String value = String.format("%+.4f", result.value());
		Integer cp = result.centipawns();
		String cpStr = (cp == null) ? "-" : formatSigned(cp);
		if (includeFen) {
			System.out.println(pos.toString() + "\t" + backend + "\t" + value + "\t" + wdl + "\t" + cpStr);
		} else {
			System.out.println("FEN: " + pos.toString());
			System.out.println("backend: " + backend);
			System.out.println("value: " + value);
			System.out.println("wdl: " + wdl);
			System.out.println("cp: " + cpStr);
		}
	}
}
