package application.cli;

import static application.cli.Constants.CMD_GUI;
import static application.cli.Constants.CMD_WORKBENCH;

import java.util.List;

import application.cli.command.AnalyzeCommand;
import application.cli.command.BatchRunCommand;
import application.cli.command.BestMoveCommand;
import application.cli.command.Chess960Command;
import application.cli.command.CleanCommand;
import application.cli.command.ConfigCommand;
import application.cli.command.DoctorCommand;
import application.cli.command.EvalCommand;
import application.cli.command.EngineBatchCommand;
import application.cli.command.EngineBenchmarkCommand;
import application.cli.command.EngineGauntletCommand;
import application.cli.command.EngineSearchCommand;
import application.cli.command.EngineTraceCommand;
import application.cli.command.EngineTreeCommand;
import application.cli.command.FenCommand;
import application.cli.command.GenFensCommand;
import application.cli.command.GpuCommand;
import application.cli.command.HelpCommand;
import application.cli.command.LineCommand;
import application.cli.command.MateCommand;
import application.cli.command.MineCommand;
import application.cli.command.MoveNotationCommand;
import application.cli.command.MovesCommand;
import application.cli.command.PerftCommand;
import application.cli.command.PgnCommand;
import application.cli.command.PositionCommand;
import application.cli.command.PositionDescribeCommand;
import application.cli.command.PuzzleTagsCommand;
import application.cli.command.PuzzleTextCommand;
import application.cli.command.RecordAnalysisDeltaCommand;
import application.cli.command.RecordCommands;
import application.cli.command.StatsCommand;
import application.cli.command.TagTextCommand;
import application.cli.command.TagsCommand;
import application.cli.command.ThreatsCommand;
import application.cli.command.UciSmokeCommand;
import application.cli.command.VersionCommand;
import application.cli.command.BuiltInEngineCommand;
import application.cli.command.book.BookCoverCommand;
import application.cli.command.book.BookPdfCommand;
import application.cli.command.book.BookRenderCommand;
import application.cli.command.book.PuzzleCollectionCommand;
import application.cli.command.book.PuzzleStudyCommand;

import application.gui.workbench.launch.LaunchCommand;

/**
 * Central registry for CLI command paths, aliases, summaries, and handlers.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */

public final class CliRegistry {

	/**
	 * Synthetic root node.
	 */
	private static final CliCommand ROOT = buildRoot();

	/**
	 * Utility class; prevent instantiation.
	 */
	private CliRegistry() {
		// utility
	}

	/**
	 * Returns the synthetic root node.
	 *
	 * @return root command node
	 */
	public static CliCommand root() {
		return ROOT;
	}

	/**
	 * Resolves a command path from canonical tokens or aliases.
	 *
	 * @param tokens command path tokens
	 * @return resolved node or {@code null}
	 */
	public static CliCommand resolve(List<String> tokens) {
		CliCommand current = ROOT;
		if (tokens == null || tokens.isEmpty()) {
			return current;
		}
		for (String token : tokens) {
			if (token == null || token.isBlank()) {
				continue;
			}
			current = current.child(token);
			if (current == null) {
				return null;
			}
		}
		return current;
	}

	/**
	 * Builds the command tree.
	 *
	 * @return root node
	 */
	private static CliCommand buildRoot() {
		CliCommand root = CliCommand.group("", "ChessRTK command-line interface")
				.usage("<area> <action> [options] [args]")
				.about("ChessRTK uses a domain-first command layout: pick an area like `fen`, `move`, "
						+ "or `engine`, then the action you want.")
				.convention("Prefer explicit flags for structured values: `--fen`, `--input`, `--output`, and `--format`.")
				.convention("When scripting, put options before free-form args and use `--` or `--end-of-options` when a value could look like a flag.")
				.convention("Use machine-oriented formats for automation: `--json`, `--jsonl`, or `--format uci|san|both` where supported.")
				.example("crtk move list --fen \"<FEN>\" --format both")
				.example("crtk move list --fen \"<FEN>\" --jsonl")
				.example("crtk engine bestmove --fen \"<FEN>\" --max-duration 2s --format both")
				.example("crtk move list --help");

		root.add(recordGroup());
		root.add(fenGroup());
		root.add(genGroup());
		root.add(batchGroup());
		root.add(moveGroup());
		root.add(engineGroup());
		root.add(CliCommand.leaf("mate", "Brute-force prove a forced mate without NN evaluation",
				MateCommand::runMate)
				.detailHelpKey("engine mate")
				.alias("find-mate")
				.usage("[options]")
				.about("Shortcut for `engine mate`: deterministic AND/OR proof search for forced mates "
						+ "without loading an evaluator or neural network.")
				.example("crtk mate --fen \"<FEN>\" --mate 4")
				.example("crtk find-mate --fen \"<FEN>\" --mate 6 --max-nodes 5000000 --threads 4 --format summary")
				.related("engine mate"));
		root.add(CliCommand.leaf("gauntlet", "Run a deterministic self-play A/B engine gauntlet",
				EngineGauntletCommand::runGauntlet)
				.detailHelpKey("engine gauntlet")
				.alias("selfplay")
				.usage("[options]")
				.about("Shortcut for `engine gauntlet`: measure a search/eval change's strength by playing a "
						+ "candidate configuration against a baseline at an equal per-move budget over varied "
						+ "openings, then report the score and a point Elo estimate.")
				.example("crtk gauntlet --a all --b none --nodes 3000 --openings 8")
				.example("crtk gauntlet --searchA mcts --searchB alpha-beta --movetime 200 --workers 4")
				.related("engine gauntlet"));
		root.add(positionGroup());
		root.add(bookGroup());
		root.add(puzzleGroup());
		root.add(configGroup());
		root.add(CliCommand.leaf(CMD_WORKBENCH, "Launch the native command and analysis workbench",
                LaunchCommand::runWorkbench)
				.detailHelpKey(CMD_WORKBENCH)
				.alias(CMD_GUI)
				.usage("[options]")
				.about("Launch a focused Swing workbench for position analysis, command-specific flag building, and batch research workflows.")
				.example("crtk gui")
				.example("crtk workbench")
				.example("crtk workbench --fen \"<FEN>\""));
		root.add(CliCommand.leaf("doctor", "Check Java, config, protocol, engine, and local artifacts",
				DoctorCommand::runDoctor)
				.detailHelpKey("doctor")
				.usage("[options]")
				.about("Run environment and configuration diagnostics before longer workflows.")
				.example("crtk doctor")
				.example("crtk doctor --strict"));
		root.add(CliCommand.leaf("clean", "Delete session cache/logs", CleanCommand::runClean)
				.detailHelpKey("clean")
				.usage("[options]")
				.about("Delete generated session cache artifacts while preserving the cache directory.")
				.example("crtk clean"));
		root.add(CliCommand.leaf("help", "Show command help", HelpCommand::runHelp)
				.detailHelpKey("help")
				.usage("[command...]")
				.about("Show summary help, contextual help for a command path, or the full built-in reference.")
				.example("crtk help")
				.example("crtk help move list")
				.example("crtk help --full"));
		root.add(CliCommand.leaf("version", "Print ChessRTK version metadata", VersionCommand::runVersion)
				.detailHelpKey("version")
				.usage("[options]")
				.about("Print the launcher version for scripts and release checks.")
				.example("crtk version")
				.example("crtk version --json"));
		return root;
	}

	/**
	 * Builds the batch command group.
	 *
	 * @return batch group
	 */
	private static CliCommand batchGroup() {
		CliCommand batch = CliCommand.group("batch", "Run multiple ChessRTK CLI commands")
				.detailHelpKey("batch")
				.usage("<action> [options]")
				.about("Batch execution helpers for command scripts.")
				.example("crtk batch run --input commands.crtk")
				.example("printf 'version\\nhelp move list\\n' | crtk batch run --stdin");
		batch.add(CliCommand.leaf("run", "Run one ChessRTK command per script line", BatchRunCommand::runBatch)
				.detailHelpKey("batch run")
				.alias("script")
				.usage("[options]")
				.about("Reads UTF-8 command rows. Blank rows and rows starting with `#` are ignored; a leading `crtk` token is optional.")
				.example("crtk batch run --input commands.crtk")
				.example("crtk batch run --stdin --keep-going"));
		return batch;
	}

	/**
	 * Builds the record command group.
	 *
	 * @return record group
	 */
	private static CliCommand recordGroup() {
		CliCommand records = CliCommand.group("record", "Export, filter, split, and summarize .record files")
				.detailHelpKey("record")
				.usage("<action> [options] [args]")
				.about("Batch-oriented record plumbing for exports, filtering, and dataset generation.")
				.convention("Use `record export <format>` and `record dataset <kind>` as the canonical nested forms.")
				.example("crtk record files --input dump/ --output merged.json --recursive")
				.example("crtk record export training-jsonl --input dump/run.json --output training.jsonl");

		CliCommand export = CliCommand.group("export",
				"Export records as plain, csv, pgn, puzzle-jsonl, puzzle-elo-jsonl, or training-jsonl")
				.detailHelpKey("record export")
				.usage("<format> [options]")
				.about("Canonical export entry point for record-derived text formats.");
		export.add(CliCommand.leaf("plain", "Convert .record JSON to .plain", RecordCommands::runRecordToPlain)
				.detailHelpKey("record export plain")
				.usage("[options]")
				.example("crtk record export plain --input dump/run.json"));
		export.add(CliCommand.leaf("csv", "Convert .record JSON to CSV", RecordCommands::runRecordToCsv)
				.detailHelpKey("record export csv")
				.usage("[options]")
				.example("crtk record export csv --input dump/run.json"));
		export.add(CliCommand.leaf("pgn", "Convert .record JSON to PGN games", RecordCommands::runRecordToPgn)
				.detailHelpKey("record export pgn")
				.usage("[options]")
				.example("crtk record export pgn --input dump/run.json"));
		export.add(CliCommand.leaf("puzzle-jsonl", "Export verified puzzle rows as JSONL",
				RecordCommands::runRecordToPuzzleJsonl)
				.detailHelpKey("record export puzzle-jsonl")
				.usage("[options]")
				.example("crtk record export puzzle-jsonl --input dump/run.json"));
		export.add(CliCommand.leaf("puzzle-elo-jsonl", "Export verified puzzle records with Elo tags",
				RecordCommands::runRecordToPuzzleEloJsonl)
				.detailHelpKey("record export puzzle-elo-jsonl")
				.usage("[options]")
				.example("crtk record export puzzle-elo-jsonl --input dump/run.json --output puzzles.elo.jsonl"));
		export.add(CliCommand.leaf("training-jsonl", "Export FEN JSONL labels for training",
				RecordCommands::runRecordToTrainingJsonl)
				.detailHelpKey("record export training-jsonl")
				.usage("[options]")
				.example("crtk record export training-jsonl --input dump/run.json"));

		CliCommand dataset = CliCommand.group("dataset", "Export tensors as npy, lc0, or classifier")
				.detailHelpKey("record dataset")
				.usage("[kind] [options]")
				.handler(RecordCommands::runRecordToDataset)
				.about("Dataset export entry point. Omitting `kind` keeps the current default `npy` behavior.");
		dataset.add(CliCommand.leaf("npy", "Convert .record JSON to NPY tensors", RecordCommands::runRecordToDataset)
				.detailHelpKey("record dataset npy")
				.usage("[options]")
				.example("crtk record dataset npy --input dump/run.json --output training/run"));
		dataset.add(CliCommand.leaf("lc0", "Convert .record JSON to LC0 tensors", RecordCommands::runRecordToLc0)
				.detailHelpKey("record dataset lc0")
				.usage("[options]")
				.example("crtk record dataset lc0 --input dump/run.json --output training/run"));
		dataset.add(CliCommand.leaf("classifier", "Convert .record JSON to classifier tensors",
				RecordCommands::runRecordToClassifier)
				.detailHelpKey("record dataset classifier")
				.usage("[options]")
				.example("crtk record dataset classifier --input dump/ --output training/run --recursive"));

		records.add(export);
		records.add(dataset);
		records.add(CliCommand.leaf("files", "Merge, filter, or split record files", RecordCommands::runRecords)
				.detailHelpKey("record files")
				.usage("[options]")
				.example("crtk record files --input dump/ --output merged.json --recursive"));
		records.add(CliCommand.leaf("stats", "Summarize record files", StatsCommand::runStats)
				.detailHelpKey("record stats")
				.usage("[options]")
				.example("crtk record stats --input merged.json"));
		records.add(CliCommand.leaf("tag-stats", "Summarize tag distributions", StatsCommand::runStatsTags)
				.detailHelpKey("record tag-stats")
				.usage("[options]")
				.example("crtk record tag-stats --input merged.json"));
		records.add(CliCommand.leaf("analysis-delta", "Compare parent/child analysis changes",
				RecordAnalysisDeltaCommand::runRecordAnalysisDelta)
				.detailHelpKey("record analysis-delta")
				.usage("[options]")
				.example("crtk record analysis-delta --input merged.json"));
		records.add(CliCommand.leaf("plain", "Alias for `record export plain`", RecordCommands::runRecordToPlain)
				.detailHelpKey("record export plain")
				.usage("[options]")
				.about("Compatibility shortcut for `record export plain`."));
		records.add(CliCommand.leaf("csv", "Alias for `record export csv`", RecordCommands::runRecordToCsv)
				.detailHelpKey("record export csv")
				.usage("[options]")
				.about("Compatibility shortcut for `record export csv`."));
		records.add(CliCommand.leaf("pgn", "Alias for `record export pgn`", RecordCommands::runRecordToPgn)
				.detailHelpKey("record export pgn")
				.usage("[options]")
				.about("Compatibility shortcut for `record export pgn`."));
		records.add(CliCommand.leaf("puzzle-jsonl", "Alias for `record export puzzle-jsonl`",
				RecordCommands::runRecordToPuzzleJsonl)
				.detailHelpKey("record export puzzle-jsonl")
				.usage("[options]")
				.about("Compatibility shortcut for `record export puzzle-jsonl`."));
		records.add(CliCommand.leaf("training-jsonl", "Alias for `record export training-jsonl`",
				RecordCommands::runRecordToTrainingJsonl)
				.detailHelpKey("record export training-jsonl")
				.usage("[options]")
				.about("Compatibility shortcut for `record export training-jsonl`."));
		records.add(CliCommand.leaf("npy", "Alias for `record dataset npy`", RecordCommands::runRecordToDataset)
				.detailHelpKey("record dataset npy")
				.usage("[options]")
				.about("Compatibility shortcut for `record dataset npy`."));
		records.add(CliCommand.leaf("lc0", "Alias for `record dataset lc0`", RecordCommands::runRecordToLc0)
				.detailHelpKey("record dataset lc0")
				.usage("[options]")
				.about("Compatibility shortcut for `record dataset lc0`."));
		records.add(CliCommand.leaf("classifier", "Alias for `record dataset classifier`",
				RecordCommands::runRecordToClassifier)
				.detailHelpKey("record dataset classifier")
				.usage("[options]")
				.about("Compatibility shortcut for `record dataset classifier`."));
		return records;
	}

	/**
	 * Builds the generation shortcut group.
	 *
	 * @return gen group
	 */
	private static CliCommand genGroup() {
		CliCommand gen = CliCommand.group("gen", "Generate reusable data seeds and artifacts")
				.detailHelpKey("gen")
				.usage("<kind> [options]")
				.about("Short generation entry points for batch data artifacts.")
				.example("crtk gen fens --output seeds/ --endgame");

		gen.add(CliCommand.leaf("fens", "Alias for `fen generate`", GenFensCommand::runGenerateFens)
				.detailHelpKey("fen generate")
				.alias("fen")
				.usage("[options]")
				.about("Compatibility shortcut for `fen generate`.")
				.example("crtk gen fens --output seeds/ --files 2 --per-file 20")
				.example("crtk gen fens --rook-endgame --rooks 2 --max-material-imbalance 200"));
		return gen;
	}

	/**
	 * Builds the fen command group.
	 *
	 * @return fen group
	 */
	private static CliCommand fenGroup() {
		CliCommand fen = CliCommand.group("fen", "Validate, normalize, generate, print, and transform FENs")
				.detailHelpKey("fen")
				.usage("<action> [options] [args]")
				.about("Position-oriented commands for validation, generation, rendering, and tagging.")
				.convention("Use `--fen` for explicit position input; positional FEN remains available for one-off use.")
				.convention("Treat `fen` commands as the canonical place for position validation and generation, not for move conversion.")
				.example("crtk fen normalize --fen \"<FEN>\"")
				.example("crtk fen validate --fen \"<FEN>\"")
				.example("crtk fen chess960 518");

		fen.add(CliCommand.leaf("normalize", "Normalize and validate a FEN", FenCommand::runFenNormalize)
				.detailHelpKey("fen normalize")
				.alias("normalise")
				.usage("[options] [FEN]")
				.example("crtk fen normalize \"<FEN>\""));
		fen.add(CliCommand.leaf("validate", "Validate a FEN", FenCommand::runFenValidate)
				.detailHelpKey("fen validate")
				.usage("[options] [FEN]")
				.example("crtk fen validate --fen \"<FEN>\""));
		fen.add(CliCommand.leaf("after", "Apply one move and print the resulting FEN", LineCommand::runFenAfter)
				.detailHelpKey("fen after")
				.usage("[options] MOVE")
				.about("Apply one UCI or SAN move from a starting position.")
				.example("crtk fen after --fen \"<FEN>\" e2e4"));
		fen.add(CliCommand.leaf("line", "Apply a move line and print the resulting FEN", LineCommand::runPlayLine)
				.detailHelpKey("fen line")
				.alias("play")
				.usage("[options] MOVES...")
				.about("Apply a UCI or SAN sequence. `fen play` remains an alias.")
				.example("crtk fen line --fen \"<FEN>\" \"e4 e5 Nf3 Nc6\""));
		fen.add(CliCommand.leaf("generate", "Generate random legal FEN shards", GenFensCommand::runGenerateFens)
				.detailHelpKey("fen generate")
				.alias("gen")
				.usage("[options]")
				.example("crtk fen generate --output seeds/")
				.example("crtk fen generate --rook-endgame --rooks 2 --max-material-imbalance 200"));
		fen.add(CliCommand.leaf("pgn", "Convert PGN games to FEN lists", PgnCommand::runPgnToFens)
				.detailHelpKey("fen pgn")
				.alias("from-pgn")
				.usage("[options]")
				.example("crtk fen pgn --input games.pgn --output seeds.txt"));
		fen.add(CliCommand.leaf("chess960", "Print Chess960 starting positions by index or range",
				Chess960Command::runChess960)
				.detailHelpKey("fen chess960")
				.alias("960")
				.usage("[options] [N]")
				.example("crtk fen chess960 518")
				.example("crtk fen chess960 --all --format layout"));
		fen.add(CliCommand.leaf("print", "Pretty-print a position", application.cli.command.PositionViewCommand::runPrint)
				.detailHelpKey("fen print")
				.usage("[options]")
				.example("crtk fen print --startpos")
				.example("crtk fen print --fen \"<FEN>\""));
		fen.add(CliCommand.leaf("display", "Render a position in a window",
				application.cli.command.PositionViewCommand::runDisplay)
				.detailHelpKey("fen display")
				.usage("[options]")
				.example("crtk fen display --startpos")
				.example("crtk fen display --fen \"<FEN>\""));
		fen.add(CliCommand.leaf("render", "Save a position image to disk",
				application.cli.command.PositionViewCommand::runRenderImage)
				.detailHelpKey("fen render")
				.usage("[options]")
				.example("crtk fen render --randompos --output dist/random-position.png")
				.example("crtk fen render --fen \"<FEN>\" --output dist/position.png"));
		fen.add(CliCommand.leaf("relations", "Render the OTIS tactical-incidence relation channels",
				application.cli.command.PositionRelationsCommand::runRelations)
				.detailHelpKey("fen relations")
				.usage("[options]")
				.about("Draw the 12-channel tactical-incidence graph (typed colour-coded arrows) over the "
						+ "board: attacks, defenses, king-zone pressure, slider rays, knight/pawn attacks, "
						+ "and pin candidates. Deterministic and weightless; the edges match the OTIS network input.")
				.example("crtk fen relations --startpos --montage --output dist/relations.png")
				.example("crtk fen relations --fen \"<FEN>\" --channel knight_attack --output dist/knights.svg")
				.example("crtk fen relations --fen \"<FEN>\" --channels \"0,1\" --legend --output dist/attacks.png"));
		fen.add(CliCommand.leaf("tags", "Generate tags for FENs, PGNs, or variations", TagsCommand::runTags)
				.detailHelpKey("fen tags")
				.usage("[options]")
				.example("crtk fen tags --fen \"<FEN>\" --include-fen"));
		fen.add(CliCommand.leaf("text", "Summarize position tags with T5", TagTextCommand::runTagText)
				.detailHelpKey("fen text")
				.usage("[options]")
				.example("crtk fen text --fen \"<FEN>\" --include-fen"));
		return fen;
	}

	/**
	 * Builds the move command group.
	 *
	 * @return move group
	 */
	private static CliCommand moveGroup() {
		CliCommand move = CliCommand.group("move", "List, convert, and apply moves")
				.detailHelpKey("move")
				.usage("<action> [options] [args]")
				.about("Deterministic move primitives for humans, scripts, and agents.")
				.convention("Prefer `move list --format uci|san|both` as the canonical move-listing command.")
				.convention("Use `to-san`, `to-uci`, `after`, and `play` when you need one narrow transformation instead of parsing mixed prose output.")
				.example("crtk move list --fen \"<FEN>\" --format both")
				.example("crtk move to-san --fen \"<FEN>\" e2e4")
				.example("crtk move play --fen \"<FEN>\" \"e4 e5 Nf3 Nc6\"");

		move.add(CliCommand.leaf("list", "List legal moves for a position", MovesCommand::runMoves)
				.detailHelpKey("move list")
				.usage("[options]")
				.about("Canonical move-listing command. The `uci`, `san`, and `both` subcommands remain convenience shortcuts.")
				.example("crtk move list --startpos --format both")
				.example("crtk move list --fen \"<FEN>\" --format both"));
		move.add(CliCommand.leaf("uci", "List legal moves in UCI", MovesCommand::runMovesUci)
				.detailHelpKey("move uci")
				.alias("list-uci")
				.usage("[options]")
				.about("Shortcut for UCI-only move listing.")
				.related("move list"));
		move.add(CliCommand.leaf("san", "List legal moves in SAN", MovesCommand::runMovesSan)
				.detailHelpKey("move san")
				.alias("list-san")
				.usage("[options]")
				.about("Shortcut for SAN-only move listing.")
				.related("move list"));
		move.add(CliCommand.leaf("both", "List legal moves in UCI and SAN", MovesCommand::runMovesBoth)
				.detailHelpKey("move both")
				.alias("list-both")
				.usage("[options]")
				.about("Shortcut for side-by-side UCI and SAN move listing.")
				.related("move list"));
		move.add(CliCommand.leaf("to-san", "Convert one UCI move to SAN", MoveNotationCommand::runUciToSan)
				.detailHelpKey("move to-san")
				.usage("[options] MOVE")
				.example("crtk move to-san --fen \"<FEN>\" e2e4"));
		move.add(CliCommand.leaf("to-uci", "Convert one SAN move to UCI", MoveNotationCommand::runSanToUci)
				.detailHelpKey("move to-uci")
				.usage("[options] MOVE")
				.example("crtk move to-uci --fen \"<FEN>\" Nf3"));
		move.add(CliCommand.leaf("after", "Apply one move and print the resulting FEN", LineCommand::runFenAfter)
				.detailHelpKey("move after")
				.usage("[options] MOVE")
				.example("crtk move after --fen \"<FEN>\" e2e4"));
		move.add(CliCommand.leaf("play", "Apply a move line and print the resulting FEN", LineCommand::runPlayLine)
				.detailHelpKey("move play")
				.alias("line")
				.usage("[options] MOVES...")
				.about("Apply a UCI or SAN sequence and print the final position.")
				.example("crtk move play --fen \"<FEN>\" \"e4 e5 Nf3 Nc6\" --intermediate"));
		return move;
	}

	/**
	 * Builds the engine command group.
	 *
	 * @return engine group
	 */
	private static CliCommand engineGroup() {
		CliCommand engine = CliCommand.group("engine", "Analyze, evaluate, search, and run movegen checks")
				.detailHelpKey("engine")
				.usage("<action> [options] [args]")
				.about("Engine-backed analysis, evaluation, search, and move-generation workflows.")
				.convention("Put an explicit budget on engine work: `--max-duration`, `--nodes`, `--depth`, or `--threads`.")
				.convention("Use `bestmove --format both` when you need a compact machine-readable result.")
				.example("crtk engine bestmove --fen \"<FEN>\" --max-duration 2s --format both")
				.example("crtk engine analyze --fen \"<FEN>\" --multipv 3 --max-nodes 1000000")
				.example("crtk engine perft --depth 5 --threads 4");

		engine.add(CliCommand.leaf("analyze", "Analyze a position with the engine", AnalyzeCommand::runAnalyze)
				.detailHelpKey("engine analyze")
				.usage("[options]")
				.example("crtk engine analyze --startpos --max-duration 5s")
				.example("crtk engine analyze --fen \"<FEN>\" --multipv 3 --max-duration 5s"));
		engine.add(CliCommand.leaf("bestmove", "Print the best move for a position", BestMoveCommand::runBestMove)
				.detailHelpKey("engine bestmove")
				.usage("[options]")
				.about("Canonical best-move command. Format-specific shortcuts remain available.")
				.example("crtk engine bestmove --startpos --format both --max-duration 2s")
				.example("crtk engine bestmove --fen \"<FEN>\" --format both --max-duration 2s"));
		engine.add(CliCommand.leaf("bestmove-uci", "Print the best move in UCI", BestMoveCommand::runBestMoveUci)
				.detailHelpKey("engine bestmove-uci")
				.usage("[options]")
				.related("engine bestmove"));
		engine.add(CliCommand.leaf("bestmove-san", "Print the best move in SAN", BestMoveCommand::runBestMoveSan)
				.detailHelpKey("engine bestmove-san")
				.usage("[options]")
				.related("engine bestmove"));
		engine.add(CliCommand.leaf("bestmove-both", "Print the best move in UCI and SAN",
				BestMoveCommand::runBestMoveBoth)
				.detailHelpKey("engine bestmove-both")
				.usage("[options]")
				.related("engine bestmove"));
		engine.add(CliCommand.leaf("analyze-batch", "Analyze FEN batches as JSONL",
				EngineBatchCommand::runAnalyzeBatch)
				.detailHelpKey("engine analyze-batch")
				.usage("[options]")
				.example("crtk engine analyze-batch --input positions.txt --max-duration 1s --jsonl")
				.example("crtk engine analyze-batch --stdin --multipv 3 --output analysis.jsonl"));
		engine.add(CliCommand.leaf("bestmove-batch", "Find best moves for FEN batches as JSONL",
				EngineBatchCommand::runBestMoveBatch)
				.detailHelpKey("engine bestmove-batch")
				.usage("[options]")
				.example("crtk engine bestmove-batch --input positions.txt --max-nodes 100000")
				.example("crtk engine bestmove-batch --stdin --json"));
		engine.add(CliCommand.leaf("mate", "Brute-force prove a forced mate without NN evaluation",
				MateCommand::runMate)
				.detailHelpKey("engine mate")
				.alias("find-mate")
				.usage("[options]")
				.about("Deterministic AND/OR proof search for forced mates. Uses legal move generation, forcing move ordering, and memoized proof bounds; it never loads an evaluator or neural network.")
				.example("crtk engine mate --fen \"<FEN>\" --max-mate 4")
				.example("crtk engine mate --fen \"<FEN>\" --max-mate 6 --nodes 5000000 --threads 4 --format summary"));
		engine.add(CliCommand.leaf("compare", "Compare best moves from two UCI protocols",
				EngineBatchCommand::runCompare)
				.detailHelpKey("engine compare")
				.usage("[options]")
				.example("crtk engine compare --input positions.txt --left-protocol a.toml --right-protocol b.toml")
				.example("crtk engine compare --fen \"<FEN>\" --protocol-a a.toml --protocol-b b.toml --json"));
		engine.add(CliCommand.leaf("benchmark", "Benchmark the core Java move generator",
				EngineBenchmarkCommand::runBenchmark)
				.detailHelpKey("engine benchmark")
				.usage("[options]")
				.example("crtk engine benchmark --startpos --depth 5 --iterations 5")
				.example("crtk engine benchmark --fen \"<FEN>\" --depth 4 --json"));
		engine.add(CliCommand.leaf("gauntlet", "Run a deterministic self-play A/B engine gauntlet",
				EngineGauntletCommand::runGauntlet)
				.detailHelpKey("engine gauntlet")
				.alias("selfplay")
				.usage("[options]")
				.about("Pit a candidate engine configuration (A) against a baseline (B) at an equal, fixed "
						+ "per-move budget over varied openings played from both colors, then report the "
						+ "candidate-perspective score and a point Elo estimate. Deterministic for a given seed; "
						+ "use `--json` for a single machine-readable summary.")
				.example("crtk engine gauntlet --a all --b none --nodes 3000 --openings 8")
				.example("crtk engine gauntlet --searchA mcts --searchB alpha-beta --movetime 200 --workers 4")
				.example("crtk engine gauntlet --engineB /usr/bin/stockfish --movetimeA 200 --movetimeB 50 --openings 20")
				.example("crtk engine gauntlet --evalA nnue --evalB classical --openings 50 --seed 42 --json"));
		engine.add(CliCommand.leaf("search", "Run a PUCT search and print root-move statistics",
				EngineSearchCommand::runSearch)
				.detailHelpKey("engine search")
				.alias("mcts")
				.usage("[options]")
				.about("Run a Monte-Carlo (PUCT) search and report each root move's visits, prior, value, and "
						+ "centipawn score — the data the workbench Search panel shows. Backend is selected like "
						+ "`engine builtin`. Use `--json` for one machine-readable object.")
				.example("crtk engine search --startpos --nodes 5000")
				.example("crtk engine search --fen \"<FEN>\" --nnue --nodes 8000 --moves 5")
				.example("crtk engine search --startpos --otis --max-duration 2s --json"));
		engine.add(CliCommand.leaf("tree", "Run a PUCT search and dump the search tree",
				EngineTreeCommand::runTree)
				.detailHelpKey("engine tree")
				.usage("[options]")
				.about("Run a Monte-Carlo (PUCT) search and dump the resulting tree — per-node visits, prior, "
						+ "value, and score — the structure the workbench Tree panel visualizes. Use `--depth` and "
						+ "`--branches` to bound the output, and `--json` for a nested tree.")
				.example("crtk engine tree --startpos --nodes 4000 --depth 3 --branches 4")
				.example("crtk engine tree --fen \"<FEN>\" --lc0 --nodes 2000 --depth 2 --json"));
		engine.add(CliCommand.leaf("trace", "Trace a neural evaluation: value, WDL, policy, and layers",
				EngineTraceCommand::runTrace)
				.detailHelpKey("engine trace")
				.usage("[options]")
				.about("Run a neural network over a position and print the value, win/draw/loss probabilities, the "
						+ "top policy moves, and a per-layer activation summary — the data the workbench Evaluator "
						+ "(neural trace) panel visualizes. Use `--json` for a machine-readable object.")
				.example("crtk engine trace --startpos --nnue")
				.example("crtk engine trace --fen \"<FEN>\" --otis --top 8")
				.example("crtk engine trace --startpos --lc0 --json"));
		engine.add(CliCommand.leaf("builtin", "Search with the built-in engine", BuiltInEngineCommand::runBuiltIn)
				.detailHelpKey("engine builtin")
				.alias("java")
				.usage("[options]")
				.example("crtk engine builtin --startpos --depth 4 --format summary")
				.example("crtk engine builtin --fen \"<FEN>\" --depth 4 --format summary")
				.example("crtk engine builtin --startpos --max-strength --max-duration 5s")
				.example("crtk engine builtin --uci"));
		engine.add(CliCommand.leaf("threats", "Analyze opponent threats", ThreatsCommand::runThreats)
				.detailHelpKey("engine threats")
				.usage("[options]")
				.example("crtk engine threats --startpos --max-duration 2s")
				.example("crtk engine threats --fen \"<FEN>\" --max-duration 2s"));
		engine.add(CliCommand.leaf("eval", "Evaluate a position with LC0, OTIS, or classical", EvalCommand::runEval)
				.detailHelpKey("engine eval")
				.usage("[options]")
				.example("crtk engine eval --startpos")
				.example("crtk engine eval --fen \"<FEN>\""));
		engine.add(CliCommand.leaf("static", "Evaluate a position with the classical backend", EvalCommand::runEvalStatic)
				.detailHelpKey("engine static")
				.usage("[options]")
				.related("engine eval"));
		engine.add(CliCommand.leaf("perft", "Run perft on a position", PerftCommand::runPerft)
				.detailHelpKey("engine perft")
				.usage("[options]")
				.example("crtk engine perft --randompos --depth 4")
				.example("crtk engine perft --fen \"<FEN>\" --depth 5 --divide --threads 4"));
		engine.add(CliCommand.leaf("perft-suite", "Run a small perft regression suite", PerftCommand::runPerftSuite)
				.detailHelpKey("engine perft-suite")
				.usage("[options]")
				.example("crtk engine perft-suite --depth 6 --threads 4"));
		engine.add(CliCommand.leaf("gpu", "Print GPU JNI backend status", GpuCommand::runGpuInfo)
				.detailHelpKey("engine gpu")
				.usage("[options]")
				.example("crtk engine gpu"));
		engine.add(CliCommand.leaf("uci-smoke", "Start engine and run a tiny UCI search", UciSmokeCommand::runUciSmoke)
				.detailHelpKey("engine uci-smoke")
				.usage("[options]")
				.example("crtk engine uci-smoke --nodes 1 --max-duration 5s"));
		return engine;
	}

	/**
	 * Builds the position command group.
	 *
	 * @return position group
	 */
	private static CliCommand positionGroup() {
		CliCommand position = CliCommand.group("position", "Inspect and compare positions")
				.detailHelpKey("position")
				.usage("<action> [options] [args]")
				.about("Deterministic position inspection helpers for scripts and research workflows.")
				.example("crtk position diff --fen \"<FEN>\" --other \"<FEN>\"")
				.example("crtk position diff \"<LEFT_FEN>\" \"<RIGHT_FEN>\" --json");
		position.add(CliCommand.leaf("diff", "Compare two FEN positions", PositionCommand::runDiff)
				.detailHelpKey("position diff")
				.usage("[options] [LEFT_FEN RIGHT_FEN]")
				.example("crtk position diff --fen \"<FEN>\" --other \"<FEN>\"")
				.example("crtk position diff \"<LEFT_FEN>\" \"<RIGHT_FEN>\" --json"));
		position.add(CliCommand.leaf("describe", "Describe a position with deterministic text",
				PositionDescribeCommand::runDescribe)
				.detailHelpKey("position describe")
				.alias("text")
				.usage("[options] [FEN]")
				.about("Builds a shared structured position-description input and renders it with a deterministic classical generator. The T5 path is accepted for configuration checks but remains unavailable until trained weights exist.")
				.example("crtk position describe --fen \"<FEN>\" --detail normal")
				.example("crtk position describe --input positions.txt --format jsonl")
				.example("crtk position describe --input positions.txt --format training-jsonl")
				.example("crtk position describe --fen \"<FEN>\" --engine t5 --model models/t5-position.bin"));
		return position;
	}

	/**
	 * Builds the book command group.
	 *
	 * @return book group
	 */
	private static CliCommand bookGroup() {
		CliCommand book = CliCommand.group("book", "Render chess books, covers, and diagram PDFs")
				.detailHelpKey("book")
				.usage("<action> [options] [args]")
				.about("Publishing and diagram-rendering workflows.")
				.example("crtk book pdf --fen \"<FEN>\" --output diagrams.pdf");
		book.add(CliCommand.leaf("render", "Render a chess-book JSON/TOML file to a native PDF",
				BookRenderCommand::runBookRender)
				.detailHelpKey("book render")
				.usage("[options]"));
		book.add(CliCommand.leaf("collection", "Build a dense puzzle collection from record JSON/JSONL",
				PuzzleCollectionCommand::runPuzzleCollection)
				.detailHelpKey("book collection")
				.usage("[options]"));
		book.add(CliCommand.leaf("study",
				"Render deeply annotated puzzle studies from a rich JSON/TOML manifest",
				PuzzleStudyCommand::runPuzzleStudy)
				.detailHelpKey("book study")
				.usage("[options]"));
		book.add(CliCommand.leaf("cover", "Render a native PDF cover for a chess-book file",
				BookCoverCommand::runBookCover)
				.detailHelpKey("book cover")
				.usage("[options]"));
		book.add(CliCommand.leaf("pdf", "Export chess diagrams to a PDF", BookPdfCommand::runBookPdf)
				.detailHelpKey("book pdf")
				.alias("diagrams")
				.usage("[options]"));
		return book;
	}

	/**
	 * Builds the puzzle command group.
	 *
	 * @return puzzle group
	 */
	private static CliCommand puzzleGroup() {
		CliCommand puzzle = CliCommand.group("puzzle", "Mine, convert, tag, and summarize puzzle lines")
				.detailHelpKey("puzzle")
				.usage("<action> [options] [args]")
				.about("Puzzle mining, conversion, tag generation, and text summarization.")
				.example("crtk puzzle mine --input seeds.txt --output dump/run.json");
		puzzle.add(CliCommand.leaf("mine", "Mine chess puzzles", MineCommand::runMine)
				.detailHelpKey("puzzle mine")
				.usage("[options]"));
		puzzle.add(CliCommand.leaf("pgn", "Convert mixed puzzle dumps to PGN games", RecordCommands::runPuzzlesToPgn)
				.detailHelpKey("puzzle pgn")
				.alias("to-pgn")
				.usage("[options]"));
		puzzle.add(CliCommand.leaf("tags", "Generate per-move tags for puzzle PVs", PuzzleTagsCommand::runPuzzleTags)
				.detailHelpKey("puzzle tags")
				.usage("[options]"));
		puzzle.add(CliCommand.leaf("text", "Run T5 over puzzle PVs", PuzzleTextCommand::runPuzzleText)
				.detailHelpKey("puzzle text")
				.usage("[options]"));
		return puzzle;
	}

	/**
	 * Builds the config command group.
	 *
	 * @return config group
	 */
	private static CliCommand configGroup() {
		CliCommand config = CliCommand.group("config", "Show/validate configuration")
				.detailHelpKey("config")
				.usage("<action>")
				.about("Inspect resolved configuration values or validate the current config/protocol files.")
				.example("crtk config show")
				.example("crtk config validate");
		config.add(CliCommand.leaf("show", "Print config values", argv -> {
			argv.ensureConsumed();
			ConfigCommand.runConfigShow();
		})
				.usage("")
				.about("Print resolved configuration values used by the CLI."));
		config.add(CliCommand.leaf("validate", "Validate config file", argv -> {
			argv.ensureConsumed();
			ConfigCommand.runConfigValidate();
		})
				.usage("")
				.about("Validate the current config file, protocol file, and configured model paths."));
		return config;
	}
}
