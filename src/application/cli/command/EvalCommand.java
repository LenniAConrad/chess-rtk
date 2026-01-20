package application.cli.command;

import static application.cli.Constants.CMD_EVAL;
import static application.cli.Constants.CMD_EVAL_STATIC;
import static application.cli.Constants.OPT_CLASSICAL;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_LC0;
import static application.cli.Constants.OPT_TERMINAL;
import static application.cli.Constants.OPT_TERMINAL_AWARE;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_WEIGHTS;
import static application.cli.EvalOps.evalClassicalEntries;
import static application.cli.EvalOps.evalEvaluatorEntries;

import java.nio.file.Path;
import java.util.List;

import chess.eval.Evaluator;
import utility.Argv;

/**
 * Implements {@code eval} and {@code eval-static}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class EvalCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private EvalCommand() {
		// utility
	}

	/**
	 * Handles {@code eval}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runEval(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		String fen = a.string(OPT_FEN);
		boolean lc0Only = a.flag(OPT_LC0);
		boolean classicalOnly = a.flag(OPT_CLASSICAL);
		boolean terminalAware = a.flag(OPT_TERMINAL_AWARE, OPT_TERMINAL);
		Path weights = a.path(OPT_WEIGHTS);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}
		a.ensureConsumed();

		if (lc0Only && classicalOnly) {
			System.err.println("eval: only one of " + OPT_LC0 + " or " + OPT_CLASSICAL + " may be set");
			System.exit(2);
			return;
		}

		List<String> fens = CommandSupport.resolveFenInputs(CMD_EVAL, input, fen);
		boolean includeFen = input != null;

		if (classicalOnly) {
			if (!evalClassicalEntries(fens, terminalAware, includeFen, verbose, CMD_EVAL)) {
				System.exit(2);
			}
			return;
		}

		Path weightsPath = (weights == null) ? Evaluator.DEFAULT_WEIGHTS : weights;
		try (Evaluator evaluator = new Evaluator(weightsPath, terminalAware)) {
			if (!evalEvaluatorEntries(fens, evaluator, lc0Only, includeFen, verbose, CMD_EVAL)) {
				System.exit(2);
			}
		} catch (Exception ex) {
			System.err.println("eval: failed to initialize evaluator: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
		}
	}

	/**
	 * Handles {@code eval-static}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runEvalStatic(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		String fen = a.string(OPT_FEN);
		boolean terminalAware = a.flag(OPT_TERMINAL_AWARE, OPT_TERMINAL);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}
		a.ensureConsumed();

		List<String> fens = CommandSupport.resolveFenInputs(CMD_EVAL_STATIC, input, fen);
		boolean includeFen = input != null;
		if (!evalClassicalEntries(fens, terminalAware, includeFen, verbose, CMD_EVAL_STATIC)) {
			System.exit(2);
		}
	}
}
