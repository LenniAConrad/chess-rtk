package application.cli;

import java.util.List;

import application.cli.command.AnalyzeCommand;
import application.cli.command.ChessArtOfChessCommand;
import application.cli.command.BestMoveCommand;
import application.cli.command.Chess960Command;
import application.cli.command.ChessBookCommand;
import application.cli.command.ChessBookCoverCommand;
import application.cli.command.ChessILoveChessCommand;
import application.cli.command.ChessPdfCommand;
import application.cli.command.CleanCommand;
import application.cli.command.ConfigCommand;
import application.cli.command.DoctorCommand;
import application.cli.command.EvalCommand;
import application.cli.command.FenCommand;
import application.cli.command.GenFensCommand;
import application.cli.command.GpuCommand;
import application.cli.command.HelpCommand;
import application.cli.command.LineCommand;
import application.cli.command.MineCommand;
import application.cli.command.MoveNotationCommand;
import application.cli.command.MovesCommand;
import application.cli.command.PerftCommand;
import application.cli.command.PgnCommand;
import application.cli.command.PuzzleTagsCommand;
import application.cli.command.PuzzleTextCommand;
import application.cli.command.RecordAnalysisDeltaCommand;
import application.cli.command.RecordCommands;
import application.cli.command.StatsCommand;
import application.cli.command.TagTextCommand;
import application.cli.command.TagsCommand;
import application.cli.command.ThreatsCommand;
import application.cli.command.UciSmokeCommand;
import application.cli.command.BuiltInEngineCommand;
import application.gui.GuiCommand;
import application.gui.GuiNextCommand;
import application.gui.GuiWebCommand;

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
				.convention("Use machine-oriented formats for automation: `--format uci|san|both` today, and avoid parsing prose output when a stable format exists.")
				.example("crtk move list --fen \"<FEN>\" --format both")
				.example("crtk engine bestmove --fen \"<FEN>\" --max-duration 2s --format both")
				.example("crtk move list --help");

		root.add(recordGroup());
		root.add(fenGroup());
		root.add(moveGroup());
		root.add(engineGroup());
		root.add(bookGroup());
		root.add(puzzleGroup());
		root.add(configGroup());
		root.add(CliCommand.leaf("gui", "Launch the GUI", GuiCommand::runGui)
				.helpKey("gui")
				.usage("[options]")
				.about("Launch the desktop board GUI.")
				.example("crtk gui --fen \"<FEN>\""));
		root.add(CliCommand.leaf("gui-web", "Launch the chess-web-inspired GUI", GuiWebCommand::runGuiWeb)
				.helpKey("gui-web")
				.usage("[options]")
				.about("Launch the chess-web-inspired desktop GUI.")
				.example("crtk gui-web --fen \"<FEN>\""));
		root.add(CliCommand.leaf("gui-next", "Launch the Studio GUI v3", GuiNextCommand::runGuiNext)
				.helpKey("gui-next")
				.usage("[options]")
				.about("Launch the Studio research workbench.")
				.example("crtk gui-next --fen \"<FEN>\""));
		root.add(CliCommand.leaf("doctor", "Check Java, config, protocol, engine, and local artifacts",
				DoctorCommand::runDoctor)
				.helpKey("doctor")
				.usage("[options]")
				.about("Run environment and configuration diagnostics before longer workflows.")
				.example("crtk doctor")
				.example("crtk doctor --strict"));
		root.add(CliCommand.leaf("clean", "Delete session cache/logs", CleanCommand::runClean)
				.helpKey("clean")
				.usage("[options]")
				.about("Delete generated session cache artifacts while preserving the cache directory.")
				.example("crtk clean"));
		root.add(CliCommand.leaf("help", "Show command help", HelpCommand::runHelp)
				.helpKey("help")
				.usage("[command...]")
				.about("Show summary help, contextual help for a command path, or the full built-in reference.")
				.example("crtk help")
				.example("crtk help move list")
				.example("crtk help --full"));
		return root;
	}

	/**
	 * Builds the record command group.
	 *
	 * @return record group
	 */
	private static CliCommand recordGroup() {
		CliCommand records = CliCommand.group("record", "Export, filter, split, and summarize .record files")
				.helpKey("record")
				.usage("<action> [options] [args]")
				.about("Batch-oriented record plumbing for exports, filtering, and dataset generation.")
				.convention("Use `record export <format>` and `record dataset <kind>` as the canonical nested forms.")
				.example("crtk record files --input dump/ --output merged.json --recursive")
				.example("crtk record export training-jsonl --input dump/run.json --output training.jsonl");

		CliCommand export = CliCommand.group("export",
				"Export records as plain, csv, pgn, puzzle-jsonl, or training-jsonl")
				.helpKey("record export")
				.usage("<format> [options]")
				.about("Canonical export entry point for record-derived text formats.");
		export.add(CliCommand.leaf("plain", "Convert .record JSON to .plain", RecordCommands::runRecordToPlain)
				.helpKey("record export plain")
				.usage("[options]")
				.example("crtk record export plain --input dump/run.json"));
		export.add(CliCommand.leaf("csv", "Convert .record JSON to CSV", RecordCommands::runRecordToCsv)
				.helpKey("record export csv")
				.usage("[options]")
				.example("crtk record export csv --input dump/run.json"));
		export.add(CliCommand.leaf("pgn", "Convert .record JSON to PGN games", RecordCommands::runRecordToPgn)
				.helpKey("record export pgn")
				.usage("[options]")
				.example("crtk record export pgn --input dump/run.json"));
		export.add(CliCommand.leaf("puzzle-jsonl", "Export verified puzzle rows as JSONL",
				RecordCommands::runRecordToPuzzleJsonl)
				.helpKey("record export puzzle-jsonl")
				.usage("[options]")
				.example("crtk record export puzzle-jsonl --input dump/run.json"));
		export.add(CliCommand.leaf("training-jsonl", "Export FEN JSONL labels for training",
				RecordCommands::runRecordToTrainingJsonl)
				.helpKey("record export training-jsonl")
				.usage("[options]")
				.example("crtk record export training-jsonl --input dump/run.json"));

		CliCommand dataset = CliCommand.group("dataset", "Export tensors as npy, lc0, or classifier")
				.helpKey("record dataset")
				.usage("[kind] [options]")
				.handler(RecordCommands::runRecordToDataset)
				.about("Dataset export entry point. Omitting `kind` keeps the current default `npy` behavior.");
		dataset.add(CliCommand.leaf("npy", "Convert .record JSON to NPY tensors", RecordCommands::runRecordToDataset)
				.helpKey("record dataset npy")
				.usage("[options]")
				.example("crtk record dataset npy --input dump/run.json --output training/run"));
		dataset.add(CliCommand.leaf("lc0", "Convert .record JSON to LC0 tensors", RecordCommands::runRecordToLc0)
				.helpKey("record dataset lc0")
				.usage("[options]")
				.example("crtk record dataset lc0 --input dump/run.json --output training/run"));
		dataset.add(CliCommand.leaf("classifier", "Convert .record JSON to classifier tensors",
				RecordCommands::runRecordToClassifier)
				.helpKey("record dataset classifier")
				.usage("[options]")
				.example("crtk record dataset classifier --input dump/ --output training/run --recursive"));

		records.add(export);
		records.add(dataset);
		records.add(CliCommand.leaf("files", "Merge, filter, or split record files", RecordCommands::runRecords)
				.helpKey("record files")
				.usage("[options]")
				.example("crtk record files --input dump/ --output merged.json --recursive"));
		records.add(CliCommand.leaf("stats", "Summarize record files", StatsCommand::runStats)
				.helpKey("record stats")
				.usage("[options]")
				.example("crtk record stats --input merged.json"));
		records.add(CliCommand.leaf("tag-stats", "Summarize tag distributions", StatsCommand::runStatsTags)
				.helpKey("record tag-stats")
				.usage("[options]")
				.example("crtk record tag-stats --input merged.json"));
		records.add(CliCommand.leaf("analysis-delta", "Compare parent/child analysis changes",
				RecordAnalysisDeltaCommand::runRecordAnalysisDelta)
				.helpKey("record analysis-delta")
				.usage("[options]")
				.example("crtk record analysis-delta --input merged.json"));
		records.add(CliCommand.leaf("plain", "Alias for `record export plain`", RecordCommands::runRecordToPlain)
				.helpKey("record export plain")
				.usage("[options]")
				.about("Compatibility shortcut for `record export plain`."));
		records.add(CliCommand.leaf("csv", "Alias for `record export csv`", RecordCommands::runRecordToCsv)
				.helpKey("record export csv")
				.usage("[options]")
				.about("Compatibility shortcut for `record export csv`."));
		records.add(CliCommand.leaf("pgn", "Alias for `record export pgn`", RecordCommands::runRecordToPgn)
				.helpKey("record export pgn")
				.usage("[options]")
				.about("Compatibility shortcut for `record export pgn`."));
		records.add(CliCommand.leaf("puzzle-jsonl", "Alias for `record export puzzle-jsonl`",
				RecordCommands::runRecordToPuzzleJsonl)
				.helpKey("record export puzzle-jsonl")
				.usage("[options]")
				.about("Compatibility shortcut for `record export puzzle-jsonl`."));
		records.add(CliCommand.leaf("training-jsonl", "Alias for `record export training-jsonl`",
				RecordCommands::runRecordToTrainingJsonl)
				.helpKey("record export training-jsonl")
				.usage("[options]")
				.about("Compatibility shortcut for `record export training-jsonl`."));
		records.add(CliCommand.leaf("npy", "Alias for `record dataset npy`", RecordCommands::runRecordToDataset)
				.helpKey("record dataset npy")
				.usage("[options]")
				.about("Compatibility shortcut for `record dataset npy`."));
		records.add(CliCommand.leaf("lc0", "Alias for `record dataset lc0`", RecordCommands::runRecordToLc0)
				.helpKey("record dataset lc0")
				.usage("[options]")
				.about("Compatibility shortcut for `record dataset lc0`."));
		records.add(CliCommand.leaf("classifier", "Alias for `record dataset classifier`",
				RecordCommands::runRecordToClassifier)
				.helpKey("record dataset classifier")
				.usage("[options]")
				.about("Compatibility shortcut for `record dataset classifier`."));
		return records;
	}

	/**
	 * Builds the fen command group.
	 *
	 * @return fen group
	 */
	private static CliCommand fenGroup() {
		CliCommand fen = CliCommand.group("fen", "Validate, normalize, generate, print, and transform FENs")
				.helpKey("fen")
				.usage("<action> [options] [args]")
				.about("Position-oriented commands for validation, generation, rendering, and tagging.")
				.convention("Use `--fen` for explicit position input; positional FEN remains available for one-off use.")
				.convention("Treat `fen` commands as the canonical place for position validation and generation, not for move conversion.")
				.example("crtk fen normalize --fen \"<FEN>\"")
				.example("crtk fen validate --fen \"<FEN>\"")
				.example("crtk fen chess960 518");

		fen.add(CliCommand.leaf("normalize", "Normalize and validate a FEN", FenCommand::runFenNormalize)
				.helpKey("fen normalize")
				.alias("normalise")
				.usage("[options] [FEN]")
				.example("crtk fen normalize \"<FEN>\""));
		fen.add(CliCommand.leaf("validate", "Validate a FEN", FenCommand::runFenValidate)
				.helpKey("fen validate")
				.usage("[options] [FEN]")
				.example("crtk fen validate --fen \"<FEN>\""));
		fen.add(CliCommand.leaf("after", "Apply one move and print the resulting FEN", LineCommand::runFenAfter)
				.helpKey("fen after")
				.usage("[options] MOVE")
				.about("Apply one UCI or SAN move from a starting position.")
				.example("crtk fen after --fen \"<FEN>\" e2e4"));
		fen.add(CliCommand.leaf("line", "Apply a move line and print the resulting FEN", LineCommand::runPlayLine)
				.helpKey("fen line")
				.alias("play")
				.usage("[options] MOVES...")
				.about("Apply a UCI or SAN sequence. `fen play` remains an alias.")
				.example("crtk fen line --fen \"<FEN>\" \"e4 e5 Nf3 Nc6\""));
		fen.add(CliCommand.leaf("generate", "Generate random legal FEN shards", GenFensCommand::runGenerateFens)
				.helpKey("fen generate")
				.alias("gen")
				.usage("[options]")
				.example("crtk fen generate --output seeds/"));
		fen.add(CliCommand.leaf("pgn", "Convert PGN games to FEN lists", PgnCommand::runPgnToFens)
				.helpKey("fen pgn")
				.alias("from-pgn")
				.usage("[options]")
				.example("crtk fen pgn --input games.pgn --output seeds.txt"));
		fen.add(CliCommand.leaf("chess960", "Print Chess960 starting positions by index or range",
				Chess960Command::runChess960)
				.helpKey("fen chess960")
				.alias("960")
				.usage("[options] [N]")
				.example("crtk fen chess960 518")
				.example("crtk fen chess960 --all --format layout"));
		fen.add(CliCommand.leaf("print", "Pretty-print a position", application.cli.command.PositionViewCommand::runPrint)
				.helpKey("fen print")
				.usage("[options]")
				.example("crtk fen print --startpos")
				.example("crtk fen print --fen \"<FEN>\""));
		fen.add(CliCommand.leaf("display", "Render a position in a window",
				application.cli.command.PositionViewCommand::runDisplay)
				.helpKey("fen display")
				.usage("[options]")
				.example("crtk fen display --startpos")
				.example("crtk fen display --fen \"<FEN>\""));
		fen.add(CliCommand.leaf("render", "Save a position image to disk",
				application.cli.command.PositionViewCommand::runRenderImage)
				.helpKey("fen render")
				.usage("[options]")
				.example("crtk fen render --randompos --output dist/random-position.png")
				.example("crtk fen render --fen \"<FEN>\" --output dist/position.png"));
		fen.add(CliCommand.leaf("tags", "Generate tags for FENs, PGNs, or variations", TagsCommand::runTags)
				.helpKey("fen tags")
				.usage("[options]")
				.example("crtk fen tags --fen \"<FEN>\" --include-fen"));
		fen.add(CliCommand.leaf("text", "Summarize position tags with T5", TagTextCommand::runTagText)
				.helpKey("fen text")
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
				.helpKey("move")
				.usage("<action> [options] [args]")
				.about("Deterministic move primitives for humans, scripts, and agents.")
				.convention("Prefer `move list --format uci|san|both` as the canonical move-listing command.")
				.convention("Use `to-san`, `to-uci`, `after`, and `play` when you need one narrow transformation instead of parsing mixed prose output.")
				.example("crtk move list --fen \"<FEN>\" --format both")
				.example("crtk move to-san --fen \"<FEN>\" e2e4")
				.example("crtk move play --fen \"<FEN>\" \"e4 e5 Nf3 Nc6\"");

		move.add(CliCommand.leaf("list", "List legal moves for a position", MovesCommand::runMoves)
				.helpKey("move list")
				.usage("[options]")
				.about("Canonical move-listing command. The `uci`, `san`, and `both` subcommands remain convenience shortcuts.")
				.example("crtk move list --startpos --format both")
				.example("crtk move list --fen \"<FEN>\" --format both"));
		move.add(CliCommand.leaf("uci", "List legal moves in UCI", MovesCommand::runMovesUci)
				.helpKey("move uci")
				.alias("list-uci")
				.usage("[options]")
				.about("Shortcut for UCI-only move listing.")
				.related("move list"));
		move.add(CliCommand.leaf("san", "List legal moves in SAN", MovesCommand::runMovesSan)
				.helpKey("move san")
				.alias("list-san")
				.usage("[options]")
				.about("Shortcut for SAN-only move listing.")
				.related("move list"));
		move.add(CliCommand.leaf("both", "List legal moves in UCI and SAN", MovesCommand::runMovesBoth)
				.helpKey("move both")
				.alias("list-both")
				.usage("[options]")
				.about("Shortcut for side-by-side UCI and SAN move listing.")
				.related("move list"));
		move.add(CliCommand.leaf("to-san", "Convert one UCI move to SAN", MoveNotationCommand::runUciToSan)
				.helpKey("move to-san")
				.usage("[options] MOVE")
				.example("crtk move to-san --fen \"<FEN>\" e2e4"));
		move.add(CliCommand.leaf("to-uci", "Convert one SAN move to UCI", MoveNotationCommand::runSanToUci)
				.helpKey("move to-uci")
				.usage("[options] MOVE")
				.example("crtk move to-uci --fen \"<FEN>\" Nf3"));
		move.add(CliCommand.leaf("after", "Apply one move and print the resulting FEN", LineCommand::runFenAfter)
				.helpKey("move after")
				.usage("[options] MOVE")
				.example("crtk move after --fen \"<FEN>\" e2e4"));
		move.add(CliCommand.leaf("play", "Apply a move line and print the resulting FEN", LineCommand::runPlayLine)
				.helpKey("move play")
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
				.helpKey("engine")
				.usage("<action> [options] [args]")
				.about("Engine-backed analysis, evaluation, search, and move-generation workflows.")
				.convention("Put an explicit budget on engine work: `--max-duration`, `--nodes`, `--depth`, or `--threads`.")
				.convention("Use `bestmove --format both` when you need a compact machine-readable result.")
				.example("crtk engine bestmove --fen \"<FEN>\" --max-duration 2s --format both")
				.example("crtk engine analyze --fen \"<FEN>\" --multipv 3 --max-nodes 1000000")
				.example("crtk engine perft --depth 5 --threads 4");

		engine.add(CliCommand.leaf("analyze", "Analyze a position with the engine", AnalyzeCommand::runAnalyze)
				.helpKey("engine analyze")
				.usage("[options]")
				.example("crtk engine analyze --startpos --max-duration 5s")
				.example("crtk engine analyze --fen \"<FEN>\" --multipv 3 --max-duration 5s"));
		engine.add(CliCommand.leaf("bestmove", "Print the best move for a position", BestMoveCommand::runBestMove)
				.helpKey("engine bestmove")
				.usage("[options]")
				.about("Canonical best-move command. Format-specific shortcuts remain available.")
				.example("crtk engine bestmove --startpos --format both --max-duration 2s")
				.example("crtk engine bestmove --fen \"<FEN>\" --format both --max-duration 2s"));
		engine.add(CliCommand.leaf("bestmove-uci", "Print the best move in UCI", BestMoveCommand::runBestMoveUci)
				.helpKey("engine bestmove-uci")
				.usage("[options]")
				.related("engine bestmove"));
		engine.add(CliCommand.leaf("bestmove-san", "Print the best move in SAN", BestMoveCommand::runBestMoveSan)
				.helpKey("engine bestmove-san")
				.usage("[options]")
				.related("engine bestmove"));
		engine.add(CliCommand.leaf("bestmove-both", "Print the best move in UCI and SAN",
				BestMoveCommand::runBestMoveBoth)
				.helpKey("engine bestmove-both")
				.usage("[options]")
				.related("engine bestmove"));
		engine.add(CliCommand.leaf("builtin", "Search with the built-in Java engine", BuiltInEngineCommand::runBuiltIn)
				.helpKey("engine builtin")
				.alias("java")
				.usage("[options]")
				.example("crtk engine builtin --startpos --depth 4 --format summary")
				.example("crtk engine builtin --fen \"<FEN>\" --depth 4 --format summary"));
		engine.add(CliCommand.leaf("threats", "Analyze opponent threats", ThreatsCommand::runThreats)
				.helpKey("engine threats")
				.usage("[options]")
				.example("crtk engine threats --startpos --max-duration 2s")
				.example("crtk engine threats --fen \"<FEN>\" --max-duration 2s"));
		engine.add(CliCommand.leaf("eval", "Evaluate a position with LC0 or classical", EvalCommand::runEval)
				.helpKey("engine eval")
				.usage("[options]")
				.example("crtk engine eval --startpos")
				.example("crtk engine eval --fen \"<FEN>\""));
		engine.add(CliCommand.leaf("static", "Evaluate a position with the classical backend", EvalCommand::runEvalStatic)
				.helpKey("engine static")
				.usage("[options]")
				.related("engine eval"));
		engine.add(CliCommand.leaf("perft", "Run perft on a position", PerftCommand::runPerft)
				.helpKey("engine perft")
				.usage("[options]")
				.example("crtk engine perft --randompos --depth 4")
				.example("crtk engine perft --fen \"<FEN>\" --depth 5 --divide --threads 4"));
		engine.add(CliCommand.leaf("perft-suite", "Run a small perft regression suite", PerftCommand::runPerftSuite)
				.helpKey("engine perft-suite")
				.usage("[options]")
				.example("crtk engine perft-suite --depth 6 --threads 4"));
		engine.add(CliCommand.leaf("gpu", "Print GPU JNI backend status", GpuCommand::runGpuInfo)
				.helpKey("engine gpu")
				.usage("[options]")
				.example("crtk engine gpu"));
		engine.add(CliCommand.leaf("uci-smoke", "Start engine and run a tiny UCI search", UciSmokeCommand::runUciSmoke)
				.helpKey("engine uci-smoke")
				.usage("[options]")
				.example("crtk engine uci-smoke --nodes 1 --max-duration 5s"));
		return engine;
	}

	/**
	 * Builds the book command group.
	 *
	 * @return book group
	 */
	private static CliCommand bookGroup() {
		CliCommand book = CliCommand.group("book", "Render chess books, covers, and diagram PDFs")
				.helpKey("book")
				.usage("<action> [options] [args]")
				.about("Publishing and diagram-rendering workflows.")
				.example("crtk book pdf --fen \"<FEN>\" --output diagrams.pdf");
		book.add(CliCommand.leaf("render", "Render a chess-book JSON/TOML file to a native PDF",
				ChessBookCommand::runChessBook)
				.helpKey("book render")
				.usage("[options]"));
		book.add(CliCommand.leaf("ilovechess", "Build an I Love Chess-style book from record JSON/JSONL",
				ChessILoveChessCommand::runILoveChess)
				.helpKey("book ilovechess")
				.usage("[options]"));
		book.add(CliCommand.leaf("artofchess",
				"Render an Art of Chess annotated book from a rich JSON/TOML manifest",
				ChessArtOfChessCommand::runArtOfChess)
				.helpKey("book artofchess")
				.alias("art")
				.usage("[options]"));
		book.add(CliCommand.leaf("cover", "Render a native PDF cover for a chess-book file",
				ChessBookCoverCommand::runChessBookCover)
				.helpKey("book cover")
				.usage("[options]"));
		book.add(CliCommand.leaf("pdf", "Export chess diagrams to a PDF", ChessPdfCommand::runChessPdf)
				.helpKey("book pdf")
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
				.helpKey("puzzle")
				.usage("<action> [options] [args]")
				.about("Puzzle mining, conversion, tag generation, and text summarization.")
				.example("crtk puzzle mine --input seeds.txt --output dump/run.json");
		puzzle.add(CliCommand.leaf("mine", "Mine chess puzzles", MineCommand::runMine)
				.helpKey("puzzle mine")
				.usage("[options]"));
		puzzle.add(CliCommand.leaf("pgn", "Convert mixed puzzle dumps to PGN games", RecordCommands::runPuzzlesToPgn)
				.helpKey("puzzle pgn")
				.alias("to-pgn")
				.usage("[options]"));
		puzzle.add(CliCommand.leaf("tags", "Generate per-move tags for puzzle PVs", PuzzleTagsCommand::runPuzzleTags)
				.helpKey("puzzle tags")
				.usage("[options]"));
		puzzle.add(CliCommand.leaf("text", "Run T5 over puzzle PVs", PuzzleTextCommand::runPuzzleText)
				.helpKey("puzzle text")
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
				.helpKey("config")
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
