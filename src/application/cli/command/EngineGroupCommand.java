package application.cli.command;

import static application.cli.Constants.CMD_ANALYZE;
import static application.cli.Constants.CMD_BESTMOVE;
import static application.cli.Constants.CMD_BESTMOVE_BOTH;
import static application.cli.Constants.CMD_BESTMOVE_SAN;
import static application.cli.Constants.CMD_BESTMOVE_UCI;
import static application.cli.Constants.CMD_ENGINE;
import static application.cli.Constants.CMD_EVAL;
import static application.cli.Constants.CMD_PERFT;
import static application.cli.Constants.CMD_PERFT_SUITE;
import static application.cli.Constants.CMD_THREATS;
import static application.cli.Constants.CMD_UCI_SMOKE;

import java.util.List;

import utility.Argv;

/**
 * Implements the grouped {@code engine <subcommand>} command.
 *
 * <p>
 * The group collects engine-backed analysis, evaluation, best-move, perft, and
 * backend status workflows behind one command family.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class EngineGroupCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private EngineGroupCommand() {
		// utility
	}

	/**
	 * Handles {@code engine <subcommand>}.
	 *
	 * @param a argument parser for the grouped command
	 */
	public static void runEngine(Argv a) {
		List<String> rest = a.positionals();
		a.ensureConsumed();
		if (rest.isEmpty()) {
			printUsageAndExit();
			return;
		}

		String subcommand = rest.get(0);
		Argv nested = CommandGroupSupport.nestedArgv(rest);
		switch (subcommand) {
			case CMD_ANALYZE -> AnalyzeCommand.runAnalyze(nested);
			case CMD_BESTMOVE -> BestMoveCommand.runBestMove(nested);
			case CMD_BESTMOVE_UCI -> BestMoveCommand.runBestMoveUci(nested);
			case CMD_BESTMOVE_SAN -> BestMoveCommand.runBestMoveSan(nested);
			case CMD_BESTMOVE_BOTH -> BestMoveCommand.runBestMoveBoth(nested);
			case CMD_THREATS -> ThreatsCommand.runThreats(nested);
			case CMD_EVAL -> EvalCommand.runEval(nested);
			case "static" -> EvalCommand.runEvalStatic(nested);
			case CMD_PERFT -> PerftCommand.runPerft(nested);
			case CMD_PERFT_SUITE -> PerftCommand.runPerftSuite(nested);
			case "gpu" -> GpuCommand.runGpuInfo(nested);
			case CMD_UCI_SMOKE -> UciSmokeCommand.runUciSmoke(nested);
			default -> {
				System.err.println(CMD_ENGINE + ": unknown subcommand: " + subcommand);
				printUsageAndExit();
			}
		}
	}

	/**
	 * Prints grouped command usage and exits with a usage status.
	 */
	private static void printUsageAndExit() {
		System.err.println("""
				usage: crtk engine <subcommand> [options]

				subcommands:
				  analyze         Analyze a FEN with the engine
				  bestmove        Print the best move for a FEN
				  bestmove-uci    Print the best move in UCI
				  bestmove-san    Print the best move in SAN
				  bestmove-both   Print the best move in UCI and SAN
				  threats         Analyze opponent threats
				  eval            Evaluate a FEN
				  static          Evaluate a FEN with the classical backend
				  perft           Run perft on a FEN
				  perft-suite     Run the perft regression suite
				  gpu             Print GPU JNI backend status
				  uci-smoke       Start engine and run a tiny UCI search
				""");
		System.exit(2);
	}
}
