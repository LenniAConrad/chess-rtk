package application.cli.command;

import static application.cli.Constants.OPT_CLASSICAL;
import static application.cli.Constants.OPT_EVALUATOR;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_LC0;
import static application.cli.Constants.OPT_OTIS;
import static application.cli.Constants.OPT_TERMINAL;
import static application.cli.Constants.OPT_TERMINAL_AWARE;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_WEIGHTS;
import static application.cli.EvalOps.evalClassicalEntries;
import static application.cli.EvalOps.evalEvaluatorEntries;
import static application.cli.EvalOps.evalOtisEntries;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

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
	 * Supported evaluator modes for {@code engine eval}.
	 */
	private enum EvalMode {
		/**
		 * Try the Java LC0 evaluator and fall back to classical evaluation.
		 */
		AUTO,

		/**
		 * Require the Java LC0 evaluator.
		 */
		LC0,

		/**
		 * Require the OTIS policy/WDL evaluator.
		 */
		OTIS,

		/**
		 * Use only the classical evaluator.
		 */
		CLASSICAL
	}

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
		String evaluatorValue = a.string(OPT_EVALUATOR);
		boolean lc0Shortcut = a.flag(OPT_LC0);
		boolean otisShortcut = a.flag(OPT_OTIS);
		boolean classicalShortcut = a.flag(OPT_CLASSICAL);
		boolean terminalAware = a.flag(OPT_TERMINAL_AWARE, OPT_TERMINAL);
		Path weights = a.path(OPT_WEIGHTS);
		String fen = CommandSupport.resolveFenArgument(a, ENGINE_EVAL, false);
		EvalMode mode = resolveEvalMode(evaluatorValue, lc0Shortcut, otisShortcut, classicalShortcut);
		if (weights != null && mode == EvalMode.CLASSICAL) {
			throw new CommandFailure(ENGINE_EVAL + ": " + OPT_WEIGHTS + " requires "
					+ OPT_EVALUATOR + " auto, lc0, or otis", 2);
		}

		List<String> fens = CommandSupport.resolveFenInputs(ENGINE_EVAL, input, fen);
		boolean includeFen = input != null;
		Bar bar = positionProgressBar(fens, ENGINE_EVAL);

		if (mode == EvalMode.CLASSICAL) {
			if (!evalClassicalEntries(fens, terminalAware, includeFen, verbose, ENGINE_EVAL, progressStep(bar))) {
				finishProgress(bar);
				throw new CommandFailure("", 2);
			}
			finishProgress(bar);
			return;
		}

		if (mode == EvalMode.OTIS) {
			Path weightsPath = weights == null ? chess.nn.otis.Model.DEFAULT_WEIGHTS : weights;
			try (chess.eval.Otis evaluator = new chess.eval.Otis(weightsPath)) {
				if (!evalOtisEntries(fens, evaluator, includeFen, verbose, ENGINE_EVAL, progressStep(bar))) {
					throw new CommandFailure("", 2);
				}
				finishProgress(bar);
				return;
			} catch (CommandFailure failure) {
				finishProgress(bar);
				throw failure;
			} catch (Exception ex) {
				finishProgress(bar);
				throw new CommandFailure(ENGINE_EVAL + ": failed to initialize OTIS evaluator: "
						+ ex.getMessage(), ex, 2, verbose);
			}
		}

		Path weightsPath = (weights == null) ? Path.of(Config.getLc0ModelPath()) : weights;
		try (Evaluator evaluator = new Evaluator(weightsPath, terminalAware)) {
			if (!evalEvaluatorEntries(fens, evaluator, mode == EvalMode.LC0, includeFen, verbose, ENGINE_EVAL,
					progressStep(bar))) {
				throw new CommandFailure("", 2);
			}
			finishProgress(bar);
		} catch (CommandFailure failure) {
			finishProgress(bar);
			throw failure;
		} catch (Exception ex) {
			finishProgress(bar);
			throw new CommandFailure(ENGINE_EVAL + ": failed to initialize evaluator: " + ex.getMessage(), ex, 2,
					verbose);
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
			throw new CommandFailure("", 2);
		}
		finishProgress(bar);
	}

	/**
	 * Resolves the evaluator mode from the value option and shortcut flags.
	 *
	 * @param value optional {@code --evaluator} value
	 * @param lc0 whether {@code --lc0} was provided
	 * @param otis whether {@code --otis} was provided
	 * @param classical whether {@code --classical} was provided
	 * @return selected evaluator mode
	 */
	private static EvalMode resolveEvalMode(String value, boolean lc0, boolean otis, boolean classical) {
		int shortcuts = (lc0 ? 1 : 0) + (otis ? 1 : 0) + (classical ? 1 : 0);
		if (value != null && shortcuts > 0) {
			throw new CommandFailure(ENGINE_EVAL + ": use either " + OPT_EVALUATOR
					+ " or evaluator shortcut flags, not both", 2);
		}
		if (shortcuts > 1) {
			throw new CommandFailure(ENGINE_EVAL + ": choose only one evaluator flag", 2);
		}
		if (value != null) {
			return parseEvalMode(value);
		}
		if (lc0) {
			return EvalMode.LC0;
		}
		if (otis) {
			return EvalMode.OTIS;
		}
		if (classical) {
			return EvalMode.CLASSICAL;
		}
		return EvalMode.AUTO;
	}

	/**
	 * Parses the {@code --evaluator} value.
	 *
	 * @param value raw evaluator value
	 * @return evaluator mode
	 */
	private static EvalMode parseEvalMode(String value) {
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "auto", "default" -> EvalMode.AUTO;
			case "lc0", "leela", "leelachesszero" -> EvalMode.LC0;
			case "otis" -> EvalMode.OTIS;
			case "classical", "static" -> EvalMode.CLASSICAL;
			default -> throw new CommandFailure(ENGINE_EVAL + ": unsupported " + OPT_EVALUATOR
					+ " value: " + value + " (expected auto, lc0, otis, or classical)", 2);
		};
	}

	/**
	 * Handles position progress bar.
	 * @param fens FEN strings
	 * @param label display label
	 * @return handles position progress bar
	 */
	private static Bar positionProgressBar(List<String> fens, String label) {
		return fens != null && fens.size() > 1 ? new Bar(fens.size(), label, false, System.err) : null;
	}

	/**
	 * Handles progress step.
	 * @param bar progress bar
	 * @return handles progress step
	 */
	private static Runnable progressStep(Bar bar) {
		return bar == null ? null : bar::step;
	}

	/**
	 * Handles finish progress.
	 * @param bar progress bar
	 */
	private static void finishProgress(Bar bar) {
		if (bar != null) {
			bar.finish();
		}
	}
}
