package application;

import static application.cli.Constants.CMD_ANALYZE;
import static application.cli.Constants.CMD_BESTMOVE;
import static application.cli.Constants.CMD_BESTMOVE_BOTH;
import static application.cli.Constants.CMD_BESTMOVE_SAN;
import static application.cli.Constants.CMD_BESTMOVE_UCI;
import static application.cli.Constants.CMD_CLEAN;
import static application.cli.Constants.CMD_CONFIG;
import static application.cli.Constants.CMD_CUDA_INFO;
import static application.cli.Constants.CMD_DISPLAY;
import static application.cli.Constants.CMD_EVAL;
import static application.cli.Constants.CMD_EVAL_STATIC;
import static application.cli.Constants.CMD_EVALUATE;
import static application.cli.Constants.CMD_GEN_FENS;
import static application.cli.Constants.CMD_GPU_INFO;
import static application.cli.Constants.CMD_HELP;
import static application.cli.Constants.CMD_HELP_LONG;
import static application.cli.Constants.CMD_HELP_SHORT;
import static application.cli.Constants.CMD_MINE;
import static application.cli.Constants.CMD_MINE_PUZZLES;
import static application.cli.Constants.CMD_MOVES;
import static application.cli.Constants.CMD_MOVES_BOTH;
import static application.cli.Constants.CMD_MOVES_SAN;
import static application.cli.Constants.CMD_MOVES_UCI;
import static application.cli.Constants.CMD_PERFT;
import static application.cli.Constants.CMD_PERFT_SUITE;
import static application.cli.Constants.CMD_PGN_TO_FENS;
import static application.cli.Constants.CMD_PRINT;
import static application.cli.Constants.CMD_PUZZLES_TO_PGN;
import static application.cli.Constants.CMD_RECORDS;
import static application.cli.Constants.CMD_RECORD_TO_CSV;
import static application.cli.Constants.CMD_RECORD_TO_DATASET;
import static application.cli.Constants.CMD_RECORD_TO_PGN;
import static application.cli.Constants.CMD_RECORD_TO_PLAIN;
import static application.cli.Constants.CMD_RENDER;
import static application.cli.Constants.CMD_STACK_TO_DATASET;
import static application.cli.Constants.CMD_STATS;
import static application.cli.Constants.CMD_STATS_TAGS;
import static application.cli.Constants.CMD_TAGS;
import static application.cli.Constants.CMD_THREATS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import application.cli.command.AnalyzeCommand;
import application.cli.command.BestMoveCommand;
import application.cli.command.CleanCommand;
import application.cli.command.ConfigCommand;
import application.cli.command.EvalCommand;
import application.cli.command.GenFensCommand;
import application.cli.command.GpuCommand;
import application.cli.command.HelpCommand;
import application.cli.command.MineCommand;
import application.cli.command.MovesCommand;
import application.cli.command.PerftCommand;
import application.cli.command.PgnCommand;
import application.cli.command.PositionViewCommand;
import application.cli.command.RecordCommands;
import application.cli.command.StatsCommand;
import application.cli.command.TagsCommand;
import application.cli.command.ThreatsCommand;
import utility.Argv;

/**
 * Used for providing the CLI entry point and dispatching subcommands.
 *
 * <p>
 * Recognized subcommands are {@code record-to-plain}, {@code record-to-csv},
 * {@code record-to-pgn}, {@code puzzles-to-pgn}, {@code records},
 * {@code record-to-dataset}, {@code stack-to-dataset}, {@code gpu-info},
 * {@code gen-fens}, {@code mine-puzzles}, {@code print}, {@code display},
 * {@code render}, {@code clean}, {@code config}, {@code stats},
 * {@code stats-tags}, {@code tags}, {@code moves}, {@code moves-uci},
 * {@code moves-san}, {@code moves-both}, {@code analyze}, {@code bestmove},
 * {@code bestmove-uci}, {@code bestmove-san}, {@code bestmove-both},
 * {@code threats}, {@code perft}, {@code perft-suite}, {@code pgn-to-fens},
 * {@code eval}, {@code eval-static}, and {@code help}.
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Main {

	private static final Map<String, Consumer<Argv>> SUBCOMMANDS = buildSubcommands();

	private static Map<String, Consumer<Argv>> buildSubcommands() {
		Map<String, Consumer<Argv>> map = new HashMap<>(64);
		map.put(CMD_RECORD_TO_PLAIN, RecordCommands::runRecordToPlain);
		map.put(CMD_RECORD_TO_CSV, RecordCommands::runRecordToCsv);
		map.put(CMD_RECORD_TO_DATASET, RecordCommands::runRecordToDataset);
		map.put(CMD_RECORD_TO_PGN, RecordCommands::runRecordToPgn);
		map.put(CMD_RECORDS, RecordCommands::runRecords);
		map.put(CMD_PUZZLES_TO_PGN, RecordCommands::runPuzzlesToPgn);
		map.put(CMD_STACK_TO_DATASET, RecordCommands::runStackToDataset);
		map.put(CMD_GPU_INFO, GpuCommand::runGpuInfo);
		map.put(CMD_CUDA_INFO, GpuCommand::runGpuInfo);
		map.put(CMD_GEN_FENS, GenFensCommand::runGenerateFens);
		map.put(CMD_MINE_PUZZLES, MineCommand::runMine);
		map.put(CMD_MINE, MineCommand::runMine);
		map.put(CMD_PRINT, PositionViewCommand::runPrint);
		map.put(CMD_DISPLAY, PositionViewCommand::runDisplay);
		map.put(CMD_RENDER, PositionViewCommand::runRenderImage);
		map.put(CMD_CLEAN, CleanCommand::runClean);
		map.put(CMD_CONFIG, ConfigCommand::runConfig);
		map.put(CMD_STATS, StatsCommand::runStats);
		map.put(CMD_TAGS, TagsCommand::runTags);
		map.put(CMD_MOVES, MovesCommand::runMoves);
		map.put(CMD_MOVES_UCI, MovesCommand::runMovesUci);
		map.put(CMD_MOVES_SAN, MovesCommand::runMovesSan);
		map.put(CMD_MOVES_BOTH, MovesCommand::runMovesBoth);
		map.put(CMD_ANALYZE, AnalyzeCommand::runAnalyze);
		map.put(CMD_BESTMOVE, BestMoveCommand::runBestMove);
		map.put(CMD_BESTMOVE_UCI, BestMoveCommand::runBestMoveUci);
		map.put(CMD_BESTMOVE_SAN, BestMoveCommand::runBestMoveSan);
		map.put(CMD_BESTMOVE_BOTH, BestMoveCommand::runBestMoveBoth);
		map.put(CMD_THREATS, ThreatsCommand::runThreats);
		map.put(CMD_PERFT, PerftCommand::runPerft);
		map.put(CMD_PERFT_SUITE, PerftCommand::runPerftSuite);
		map.put(CMD_PGN_TO_FENS, PgnCommand::runPgnToFens);
		map.put(CMD_STATS_TAGS, StatsCommand::runStatsTags);
		map.put(CMD_EVAL, EvalCommand::runEval);
		map.put(CMD_EVALUATE, EvalCommand::runEval);
		map.put(CMD_EVAL_STATIC, EvalCommand::runEvalStatic);
		map.put(CMD_HELP, HelpCommand::runHelp);
		map.put(CMD_HELP_SHORT, HelpCommand::runHelp);
		map.put(CMD_HELP_LONG, HelpCommand::runHelp);
		return map;
	}

	/**
	 * Utility class; prevent instantiation.
	 */
	private Main() {
		// utility
	}

	/**
	 * Used for parsing top-level CLI arguments and delegating to a subcommand
	 * handler.
	 *
	 * <p>
	 * Behavior:
	 * </p>
	 * <ul>
	 * <li>Attempts to read the first positional argument as the subcommand.</li>
	 * <li>Delegates remaining positionals to the corresponding command handler.</li>
	 * <li>On unknown subcommands, prints help and exits with status {@code 2}.</li>
	 * </ul>
	 *
	 * @param argv raw command-line arguments; first positional must be a valid
	 *             subcommand
	 */
	public static void main(String[] argv) {
		Argv a = new Argv(argv);

		List<String> head = a.positionals();

		a.ensureConsumed();

		if (head.isEmpty()) {
			HelpCommand.helpSummary();
			return;
		}

		String sub = head.get(0);
		String[] tail = head.subList(1, head.size()).toArray(new String[0]);
		Argv b = new Argv(tail);

		Consumer<Argv> handler = SUBCOMMANDS.get(sub);
		if (handler != null) {
			handler.accept(b);
			return;
		}
		System.err.println("Unknown command: " + sub);
		HelpCommand.helpSummary();
		System.exit(2);
	}
}
