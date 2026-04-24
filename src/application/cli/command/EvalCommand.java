package application.cli.command;

import static application.cli.Constants.OPT_CLASSICAL;
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

import application.Config;
import application.console.Bar;
import chess.eval.Evaluator;
import utility.Argv;

/**
 * Implements {@code engine eval} and {@code engine static}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class EvalCommand {

	/**
	 * Current command label for evaluator-backed evaluation.
	 */
	private static final String ENGINE_EVAL = "engine eval";

	/**
	 * Current command label for classical evaluation.
	 */
	private static final String ENGINE_STATIC = "engine static";

	/**
	 * Utility class; prevent instantiation.
	 */
	private EvalCommand() {
		// utility
	}

	/**
	 * Handles {@code engine eval}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runEval(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		boolean lc0Only = a.flag(OPT_LC0);
		boolean classicalOnly = a.flag(OPT_CLASSICAL);
		boolean terminalAware = a.flag(OPT_TERMINAL_AWARE, OPT_TERMINAL);
		Path weights = a.path(OPT_WEIGHTS);
		String fen = CommandSupport.resolveFenArgument(a, ENGINE_EVAL, false);

		if (lc0Only && classicalOnly) {
			System.err.println(ENGINE_EVAL + ": only one of " + OPT_LC0 + " or " + OPT_CLASSICAL + " may be set");
			System.exit(2);
			return;
		}

		List<String> fens = CommandSupport.resolveFenInputs(ENGINE_EVAL, input, fen);
		boolean includeFen = input != null;
		Bar bar = positionProgressBar(fens, ENGINE_EVAL);

		if (classicalOnly) {
			if (!evalClassicalEntries(fens, terminalAware, includeFen, verbose, ENGINE_EVAL, progressStep(bar))) {
				finishProgress(bar);
				System.exit(2);
			}
			finishProgress(bar);
			return;
		}

		Path weightsPath = (weights == null) ? Path.of(Config.getLc0ModelPath()) : weights;
		try (Evaluator evaluator = new Evaluator(weightsPath, terminalAware)) {
			if (!evalEvaluatorEntries(fens, evaluator, lc0Only, includeFen, verbose, ENGINE_EVAL, progressStep(bar))) {
				finishProgress(bar);
				System.exit(2);
			}
			finishProgress(bar);
		} catch (Exception ex) {
			finishProgress(bar);
			System.err.println(ENGINE_EVAL + ": failed to initialize evaluator: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
		}
	}

	/**
	 * Handles {@code engine static}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runEvalStatic(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		boolean terminalAware = a.flag(OPT_TERMINAL_AWARE, OPT_TERMINAL);
		String fen = CommandSupport.resolveFenArgument(a, ENGINE_STATIC, false);

		List<String> fens = CommandSupport.resolveFenInputs(ENGINE_STATIC, input, fen);
		boolean includeFen = input != null;
		Bar bar = positionProgressBar(fens, ENGINE_STATIC);
		if (!evalClassicalEntries(fens, terminalAware, includeFen, verbose, ENGINE_STATIC, progressStep(bar))) {
			finishProgress(bar);
			System.exit(2);
		}
		finishProgress(bar);
	}

	/**
	 * Handles position progress bar.
	 * @param fens fens
	 * @param label label
	 * @return computed value
	 */
	private static Bar positionProgressBar(List<String> fens, String label) {
		return fens != null && fens.size() > 1 ? new Bar(fens.size(), label, false, System.err) : null;
	}

	/**
	 * Handles progress step.
	 * @param bar bar
	 * @return computed value
	 */
	private static Runnable progressStep(Bar bar) {
		return bar == null ? null : bar::step;
	}

	/**
	 * Handles finish progress.
	 * @param bar bar
	 */
	private static void finishProgress(Bar bar) {
		if (bar != null) {
			bar.finish();
		}
	}
}
