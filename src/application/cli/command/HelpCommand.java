package application.cli.command;

import static application.cli.Constants.CMD_BOOK;
import static application.cli.Constants.CMD_CLEAN;
import static application.cli.Constants.CMD_CONFIG;
import static application.cli.Constants.CMD_DOCTOR;
import static application.cli.Constants.CMD_ENGINE;
import static application.cli.Constants.CMD_FEN;
import static application.cli.Constants.CMD_GUI;
import static application.cli.Constants.CMD_GUI_NEXT;
import static application.cli.Constants.CMD_GUI_WEB;
import static application.cli.Constants.CMD_HELP;
import static application.cli.Constants.CMD_HELP_LONG;
import static application.cli.Constants.CMD_HELP_SHORT;
import static application.cli.Constants.CMD_MOVE;
import static application.cli.Constants.CMD_PUZZLE;
import static application.cli.Constants.CMD_RECORD;
import static application.cli.Constants.CMD_UCI_SMOKE;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import utility.Argv;

/**
 * Implements the {@code help} command output for the CLI.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class HelpCommand {

	/**
	 * Help marker for {@code record}.
	 */
	private static final String RECORD_SUBCOMMANDS_MARKER = "record subcommands:";

	/**
	 * Help marker for {@code record export}.
	 */
	private static final String RECORD_EXPORT_SUBCOMMANDS_MARKER = "record export subcommands:";

	/**
	 * Help marker for {@code record dataset}.
	 */
	private static final String RECORD_DATASET_SUBCOMMANDS_MARKER = "record dataset subcommands:";

	/**
	 * Help marker for {@code fen}.
	 */
	private static final String FEN_SUBCOMMANDS_MARKER = "fen subcommands:";

	/**
	 * Help marker for {@code move}.
	 */
	private static final String MOVE_SUBCOMMANDS_MARKER = "move subcommands:";

	/**
	 * Help marker for {@code engine}.
	 */
	private static final String ENGINE_SUBCOMMANDS_MARKER = "engine subcommands:";

	/**
	 * Help marker for {@code book}.
	 */
	private static final String BOOK_SUBCOMMANDS_MARKER = "book subcommands:";

	/**
	 * Help marker for {@code puzzle}.
	 */
	private static final String PUZZLE_SUBCOMMANDS_MARKER = "puzzle subcommands:";

	/**
	 * Help marker for {@code record export plain}.
	 */
	private static final String RECORD_TO_PLAIN_OPTIONS_MARKER = "record export plain options:";

	/**
	 * Help marker for {@code record export csv}.
	 */
	private static final String RECORD_TO_CSV_OPTIONS_MARKER = "record export csv options:";

	/**
	 * Help marker for {@code record dataset npy}.
	 */
	private static final String RECORD_TO_DATASET_OPTIONS_MARKER = "record dataset npy options:";

	/**
	 * Help marker for {@code record dataset lc0}.
	 */
	private static final String RECORD_TO_LC0_OPTIONS_MARKER = "record dataset lc0 options:";

	/**
	 * Help marker for {@code record dataset classifier}.
	 */
	private static final String RECORD_TO_CLASSIFIER_OPTIONS_MARKER =
			"record dataset classifier options:";

	/**
	 * Help marker for {@code record export training-jsonl}.
	 */
	private static final String RECORD_TO_TRAINING_JSONL_OPTIONS_MARKER =
			"record export training-jsonl options:";

	/**
	 * Help marker for {@code record export puzzle-jsonl}.
	 */
	private static final String RECORD_TO_PUZZLE_JSONL_OPTIONS_MARKER =
			"record export puzzle-jsonl options:";

	/**
	 * Help marker for {@code record export pgn}.
	 */
	private static final String RECORD_TO_PGN_OPTIONS_MARKER = "record export pgn options:";

	/**
	 * Help marker for {@code puzzle pgn}.
	 */
	private static final String PUZZLES_TO_PGN_OPTIONS_MARKER = "puzzle pgn options:";

	/**
	 * Help marker for {@code record files}.
	 */
	private static final String RECORDS_OPTIONS_MARKER = "record files options:";

	/**
	 * Help marker for {@code record analysis-delta}.
	 */
	private static final String RECORD_ANALYSIS_DELTA_OPTIONS_MARKER =
			"record analysis-delta options:";

	/**
	 * Help marker for {@code engine gpu}.
	 */
	private static final String GPU_INFO_OPTIONS_MARKER = "engine gpu options:";

	/**
	 * Help marker for {@code engine uci-smoke}.
	 */
	private static final String UCI_SMOKE_OPTIONS_MARKER = "engine uci-smoke options:";

	/**
	 * Help marker for {@code fen generate}.
	 */
	private static final String GEN_FENS_OPTIONS_MARKER = "fen generate options:";

	/**
	 * Help marker for {@code puzzle mine}.
	 */
	private static final String MINE_PUZZLES_OPTIONS_MARKER = "puzzle mine options (overrides & inputs):";

	/**
	 * Help marker for {@code fen print}.
	 */
	private static final String PRINT_OPTIONS_MARKER = "fen print options:";

	/**
	 * Help marker for {@code fen display}.
	 */
	private static final String DISPLAY_OPTIONS_MARKER = "fen display options:";

	/**
	 * Help marker for {@code fen render}.
	 */
	private static final String RENDER_OPTIONS_MARKER = "fen render options:";

	/**
	 * Help marker for {@code book render}.
	 */
	private static final String CHESS_BOOK_OPTIONS_MARKER = "book render options:";

	/**
	 * Help marker for {@code book cover}.
	 */
	private static final String CHESS_BOOK_COVER_OPTIONS_MARKER = "book cover options:";

	/**
	 * Help marker for {@code fen chess960}.
	 */
	private static final String CHESS960_OPTIONS_MARKER = "fen chess960 options:";

	/**
	 * Help marker for {@code book pdf}.
	 */
	private static final String CHESS_PDF_OPTIONS_MARKER = "book pdf options:";

	/**
	 * Help marker for {@code gui}.
	 */
	private static final String GUI_OPTIONS_MARKER = "gui options:";

	/**
	 * Help marker for {@code gui-web}.
	 */
	private static final String GUI_WEB_OPTIONS_MARKER = "gui-web options:";

	/**
	 * Help marker for {@code gui-next}.
	 */
	private static final String GUI_NEXT_OPTIONS_MARKER = "gui-next options:";

	/**
	 * Help marker for {@code config}.
	 */
	private static final String CONFIG_SUBCOMMANDS_MARKER = "config subcommands:";

	/**
	 * Help marker for {@code record stats}.
	 */
	private static final String STATS_OPTIONS_MARKER = "record stats options:";

	/**
	 * Help marker for {@code record tag-stats}.
	 */
	private static final String STATS_TAGS_OPTIONS_MARKER = "record tag-stats options:";

	/**
	 * Help marker for {@code fen tags}.
	 */
	private static final String TAGS_OPTIONS_MARKER = "fen tags options:";

	/**
	 * Help marker for {@code puzzle tags}.
	 */
	private static final String PUZZLE_TAGS_OPTIONS_MARKER = "puzzle tags options:";

	/**
	 * Help marker for {@code puzzle text}.
	 */
	private static final String PUZZLE_TEXT_OPTIONS_MARKER = "puzzle text options:";

	/**
	 * Help marker for {@code fen text}.
	 */
	private static final String TAG_TEXT_OPTIONS_MARKER = "fen text options:";

	/**
	 * Help marker for {@code move list}.
	 */
	private static final String MOVES_OPTIONS_MARKER = "move list options:";

	/**
	 * Help marker for {@code move uci}.
	 */
	private static final String MOVES_UCI_OPTIONS_MARKER = "move uci options:";

	/**
	 * Help marker for {@code move san}.
	 */
	private static final String MOVES_SAN_OPTIONS_MARKER = "move san options:";

	/**
	 * Help marker for {@code move both}.
	 */
	private static final String MOVES_BOTH_OPTIONS_MARKER = "move both options:";

	/**
	 * Help marker for {@code move to-san}.
	 */
	private static final String UCI_TO_SAN_OPTIONS_MARKER = "move to-san options:";

	/**
	 * Help marker for {@code move to-uci}.
	 */
	private static final String SAN_TO_UCI_OPTIONS_MARKER = "move to-uci options:";

	/**
	 * Help marker for {@code move after}.
	 */
	private static final String FEN_AFTER_OPTIONS_MARKER = "move after options:";

	/**
	 * Help marker for {@code move play}.
	 */
	private static final String PLAY_LINE_OPTIONS_MARKER = "move play options:";

	/**
	 * Help marker for {@code fen normalize}.
	 */
	private static final String FEN_NORMALIZE_OPTIONS_MARKER = "fen normalize options:";

	/**
	 * Help marker for {@code fen validate}.
	 */
	private static final String FEN_VALIDATE_OPTIONS_MARKER = "fen validate options:";

	/**
	 * Help marker for {@code engine analyze}.
	 */
	private static final String ANALYZE_OPTIONS_MARKER = "engine analyze options:";

	/**
	 * Help marker for {@code engine bestmove}.
	 */
	private static final String BESTMOVE_OPTIONS_MARKER = "engine bestmove options:";

	/**
	 * Help marker for {@code engine bestmove-uci}.
	 */
	private static final String BESTMOVE_UCI_OPTIONS_MARKER = "engine bestmove-uci options:";

	/**
	 * Help marker for {@code engine bestmove-san}.
	 */
	private static final String BESTMOVE_SAN_OPTIONS_MARKER = "engine bestmove-san options:";

	/**
	 * Help marker for {@code engine bestmove-both}.
	 */
	private static final String BESTMOVE_BOTH_OPTIONS_MARKER = "engine bestmove-both options:";

	/**
	 * Help marker for {@code engine builtin}.
	 */
	private static final String BUILTIN_ENGINE_OPTIONS_MARKER = "engine builtin options:";

	/**
	 * Help marker for {@code engine threats}.
	 */
	private static final String THREATS_OPTIONS_MARKER = "engine threats options:";

	/**
	 * Help marker for {@code engine perft}.
	 */
	private static final String PERFT_OPTIONS_MARKER = "engine perft options:";

	/**
	 * Help marker for {@code engine perft-suite}.
	 */
	private static final String PERFT_SUITE_OPTIONS_MARKER = "engine perft-suite options:";

	/**
	 * Help marker for {@code fen pgn}.
	 */
	private static final String PGN_TO_FENS_OPTIONS_MARKER = "fen pgn options:";

	/**
	 * Help marker for {@code engine eval}.
	 */
	private static final String EVAL_OPTIONS_MARKER = "engine eval options:";

	/**
	 * Help marker for {@code engine static}.
	 */
	private static final String EVAL_STATIC_OPTIONS_MARKER = "engine static options:";

	/**
	 * Help marker for {@code clean}.
	 */
	private static final String CLEAN_OPTIONS_MARKER = "clean options:";

	/**
	 * Help marker for {@code doctor}.
	 */
	private static final String DOCTOR_OPTIONS_MARKER = "doctor options:";

	/**
	 * Help marker for {@code help} variants.
	 */
	private static final String HELP_OPTIONS_MARKER = "help options:";

	/**
	 * Maps command names to their help section markers.
	 */
	private static final Map<String, String> HELP_MARKERS = Map.ofEntries(
			Map.entry(CMD_RECORD, RECORD_SUBCOMMANDS_MARKER),
			Map.entry("record export", RECORD_EXPORT_SUBCOMMANDS_MARKER),
			Map.entry("record export plain", RECORD_TO_PLAIN_OPTIONS_MARKER),
			Map.entry("record export csv", RECORD_TO_CSV_OPTIONS_MARKER),
			Map.entry("record export pgn", RECORD_TO_PGN_OPTIONS_MARKER),
			Map.entry("record export puzzle-jsonl", RECORD_TO_PUZZLE_JSONL_OPTIONS_MARKER),
			Map.entry("record export training-jsonl", RECORD_TO_TRAINING_JSONL_OPTIONS_MARKER),
			Map.entry("record dataset", RECORD_DATASET_SUBCOMMANDS_MARKER),
			Map.entry("record dataset npy", RECORD_TO_DATASET_OPTIONS_MARKER),
			Map.entry("record dataset lc0", RECORD_TO_LC0_OPTIONS_MARKER),
			Map.entry("record dataset classifier", RECORD_TO_CLASSIFIER_OPTIONS_MARKER),
			Map.entry("record files", RECORDS_OPTIONS_MARKER),
			Map.entry("record stats", STATS_OPTIONS_MARKER),
			Map.entry("record tag-stats", STATS_TAGS_OPTIONS_MARKER),
			Map.entry("record analysis-delta", RECORD_ANALYSIS_DELTA_OPTIONS_MARKER),
			Map.entry(CMD_FEN, FEN_SUBCOMMANDS_MARKER),
			Map.entry("fen normalize", FEN_NORMALIZE_OPTIONS_MARKER),
			Map.entry("fen validate", FEN_VALIDATE_OPTIONS_MARKER),
			Map.entry("fen after", FEN_AFTER_OPTIONS_MARKER),
			Map.entry("fen line", PLAY_LINE_OPTIONS_MARKER),
			Map.entry("fen generate", GEN_FENS_OPTIONS_MARKER),
			Map.entry("fen pgn", PGN_TO_FENS_OPTIONS_MARKER),
			Map.entry("fen chess960", CHESS960_OPTIONS_MARKER),
			Map.entry("fen print", PRINT_OPTIONS_MARKER),
			Map.entry("fen display", DISPLAY_OPTIONS_MARKER),
			Map.entry("fen render", RENDER_OPTIONS_MARKER),
			Map.entry("fen tags", TAGS_OPTIONS_MARKER),
			Map.entry("fen text", TAG_TEXT_OPTIONS_MARKER),
			Map.entry(CMD_MOVE, MOVE_SUBCOMMANDS_MARKER),
			Map.entry("move list", MOVES_OPTIONS_MARKER),
			Map.entry("move uci", MOVES_UCI_OPTIONS_MARKER),
			Map.entry("move san", MOVES_SAN_OPTIONS_MARKER),
			Map.entry("move both", MOVES_BOTH_OPTIONS_MARKER),
			Map.entry("move to-san", UCI_TO_SAN_OPTIONS_MARKER),
			Map.entry("move to-uci", SAN_TO_UCI_OPTIONS_MARKER),
			Map.entry("move after", FEN_AFTER_OPTIONS_MARKER),
			Map.entry("move play", PLAY_LINE_OPTIONS_MARKER),
			Map.entry(CMD_ENGINE, ENGINE_SUBCOMMANDS_MARKER),
			Map.entry("engine analyze", ANALYZE_OPTIONS_MARKER),
			Map.entry("engine bestmove", BESTMOVE_OPTIONS_MARKER),
			Map.entry("engine bestmove-uci", BESTMOVE_UCI_OPTIONS_MARKER),
			Map.entry("engine bestmove-san", BESTMOVE_SAN_OPTIONS_MARKER),
			Map.entry("engine bestmove-both", BESTMOVE_BOTH_OPTIONS_MARKER),
			Map.entry("engine builtin", BUILTIN_ENGINE_OPTIONS_MARKER),
			Map.entry("engine java", BUILTIN_ENGINE_OPTIONS_MARKER),
			Map.entry("engine threats", THREATS_OPTIONS_MARKER),
			Map.entry("engine eval", EVAL_OPTIONS_MARKER),
			Map.entry("engine static", EVAL_STATIC_OPTIONS_MARKER),
			Map.entry("engine perft", PERFT_OPTIONS_MARKER),
			Map.entry("engine perft-suite", PERFT_SUITE_OPTIONS_MARKER),
			Map.entry("engine gpu", GPU_INFO_OPTIONS_MARKER),
			Map.entry(CMD_ENGINE + " " + CMD_UCI_SMOKE, UCI_SMOKE_OPTIONS_MARKER),
			Map.entry(CMD_BOOK, BOOK_SUBCOMMANDS_MARKER),
			Map.entry("book render", CHESS_BOOK_OPTIONS_MARKER),
			Map.entry("book cover", CHESS_BOOK_COVER_OPTIONS_MARKER),
			Map.entry("book pdf", CHESS_PDF_OPTIONS_MARKER),
			Map.entry(CMD_PUZZLE, PUZZLE_SUBCOMMANDS_MARKER),
			Map.entry("puzzle mine", MINE_PUZZLES_OPTIONS_MARKER),
			Map.entry("puzzle pgn", PUZZLES_TO_PGN_OPTIONS_MARKER),
			Map.entry("puzzle tags", PUZZLE_TAGS_OPTIONS_MARKER),
			Map.entry("puzzle text", PUZZLE_TEXT_OPTIONS_MARKER),
			Map.entry(CMD_GUI, GUI_OPTIONS_MARKER),
			Map.entry(CMD_GUI_WEB, GUI_WEB_OPTIONS_MARKER),
			Map.entry(CMD_GUI_NEXT, GUI_NEXT_OPTIONS_MARKER),
			Map.entry(CMD_CONFIG, CONFIG_SUBCOMMANDS_MARKER),
			Map.entry(CMD_CLEAN, CLEAN_OPTIONS_MARKER),
			Map.entry(CMD_DOCTOR, DOCTOR_OPTIONS_MARKER),
			Map.entry(CMD_HELP, HELP_OPTIONS_MARKER),
			Map.entry(CMD_HELP_SHORT, HELP_OPTIONS_MARKER),
			Map.entry(CMD_HELP_LONG, HELP_OPTIONS_MARKER));

	/**
	 * Width of the command-name column in command list output.
	 */
	private static final int COMMAND_NAME_WIDTH = 10;

	/**
	 * Spaces between the padded command name and its description.
	 */
	private static final int COMMAND_DESCRIPTION_GAP = 2;

	/**
	 * Shared command summary used by short and full help output.
	 */
	private static final String COMMAND_LIST = String.join("\n",
			commandLine(CMD_RECORD, "Export, filter, split, and summarize .record files"),
			commandLine(CMD_FEN, "Validate, normalize, generate, print, and transform FENs"),
			commandLine(CMD_MOVE, "List, convert, and apply moves"),
			commandLine(CMD_ENGINE, "Analyze, evaluate, search, and run movegen checks"),
			commandLine(CMD_BOOK, "Render chess books, covers, and diagram PDFs"),
			commandLine(CMD_PUZZLE, "Mine, convert, tag, and summarize puzzle lines"),
			commandLine(CMD_GUI, "Launch the GUI"),
			commandLine(CMD_GUI_WEB, "Launch the chess-web-inspired GUI"),
			commandLine(CMD_GUI_NEXT, "Launch the Studio GUI v3"),
			commandLine(CMD_CONFIG, "Show/validate configuration"),
			commandLine(CMD_DOCTOR, "Check Java, config, protocol, engine, and local artifacts"),
			commandLine(CMD_CLEAN, "Delete session cache/logs"),
			commandLine(CMD_HELP, "Show command help"));

	/**
	 * Utility class; prevent instantiation.
	 */
	private HelpCommand() {
		// utility
	}

	/**
	 * Handles {@code help}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runHelp(Argv a) {
		boolean full = a.flag("--full");
		List<String> rest = a.positionals();
		a.ensureConsumed();

		if (!rest.isEmpty()) {
			helpCommand(String.join(" ", rest));
			return;
		}

		if (full) {
			helpFull();
		} else {
			helpSummary();
		}
	}

	/**
	 * Prints the short help summary to standard output.
	 * Shows the command list along with quick usage tips.
	 */
	public static void helpSummary() {
		System.out.println("""
				crtk — ChessRTK (chess research toolkit)

				usage: crtk <command> [options]

				commands:
				""" + COMMAND_LIST + "\n\n" + """
				tips:
				  crtk help <command>       Show help for one command
				  crtk help --full          Show full help output
				""");
	}

	/**
	 * Prints the full help text to standard output.
	 * Includes per-command option blocks for the CLI.
	 */
	private static void helpFull() {
		System.out.println(HELP_FULL_TEXT);
	}

	/**
	 * Prints help for a single command or falls back to the summary.
	 * Looks up the command section inside the full help text.
	 *
	 * @param command command name to display help for
	 */
	private static void helpCommand(String command) {
		String marker = helpMarkerFor(command);
		if (marker == null) {
			System.err.println("Unknown command for help: " + command);
			helpSummary();
			return;
		}
		String section = extractHelpSection(HELP_FULL_TEXT, marker);
		if (section == null) {
			System.err.println("No help available for: " + command);
			helpSummary();
			return;
		}
		System.out.println("usage: crtk " + command + " [options]\n\n" + section);
	}

	/**
	 * Resolves the section marker used inside the full help text.
	 * Returns {@code null} when the command is unknown.
	 *
	 * @param command command name to look up
	 * @return marker line for the command, or {@code null} if not found
	 */
	private static String helpMarkerFor(String command) {
		String marker = HELP_MARKERS.get(command);
		while (marker == null) {
			int split = command.lastIndexOf(' ');
			if (split < 0) {
				break;
			}
			command = command.substring(0, split);
			marker = HELP_MARKERS.get(command);
		}
		return marker;
	}

	/**
	 * Formats a command-list row with a stable command column.
	 *
	 * @param command     command token.
	 * @param description command description.
	 * @return formatted command-list row.
	 */
	private static String commandLine(String command, String description) {
		int spaces = Math.max(COMMAND_DESCRIPTION_GAP,
				COMMAND_NAME_WIDTH - command.length() + COMMAND_DESCRIPTION_GAP);
		return "  " + command + " ".repeat(spaces) + description;
	}

	/**
	 * Extracts a command-specific help block from the full help text.
	 * Trims leading and trailing blank lines around the section.
	 *
	 * @param fullText full help text to search
	 * @param marker   marker line that begins the section
	 * @return section text, or {@code null} if the marker is not found
	 */
	private static String extractHelpSection(String fullText, String marker) {
		String[] lines = fullText.split("\n", -1);
		int start = -1;
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].trim().equals(marker)) {
				start = i;
				break;
			}
		}
		if (start < 0) {
			return null;
		}
		int end = lines.length;
		for (int i = start + 1; i < lines.length; i++) {
			String trimmed = lines[i].trim();
			if ((trimmed.endsWith("options:") || trimmed.endsWith("subcommands:")) && !trimmed.equals(marker)) {
				end = i;
				break;
			}
		}
		while (start < end && lines[start].trim().isEmpty()) {
			start++;
		}
		while (end > start && lines[end - 1].trim().isEmpty()) {
			end--;
		}
		return String.join("\n", Arrays.copyOfRange(lines, start, end));
	}

	/**
	 * Full help text used by the {@code help} command.
	 * Contains per-command option blocks for the CLI.
	 */
	private static final String HELP_FULL_TEXT = """
			crtk — ChessRTK (chess research toolkit)

			usage: crtk <command> [options]

			commands:
			""" + COMMAND_LIST + "\n\n" + """
			record subcommands:
			  export FORMAT              Export records as plain, csv, pgn, puzzle-jsonl, or training-jsonl
			  dataset KIND               Export tensors as npy, lc0, or classifier
			  files                      Merge, filter, or split record files
			  stats                      Summarize record files
			  tag-stats                  Summarize tag distributions
			  analysis-delta             Compare parent/child analysis changes

			record export subcommands:
			  plain                      Convert .record JSON to .plain
			  csv                        Convert .record JSON to CSV
			  pgn                        Convert .record JSON to PGN games
			  puzzle-jsonl               Export verified puzzle rows as JSONL
			  training-jsonl             Export FEN JSONL labels for training

			record dataset subcommands:
			  npy                        Convert .record JSON to NPY tensors
			  lc0                        Convert .record JSON to LC0 tensors
			  classifier                 Convert .record JSON to classifier tensors

			fen subcommands:
			  normalize                  Normalize and validate a FEN
			  validate                   Validate a FEN
			  after                      Apply one move and print the resulting FEN
			  line                       Apply a move line and print the resulting FEN
			  generate                   Generate random legal FEN shards
			  pgn                        Convert PGN games to FEN lists
			  chess960                   Print Chess960 starting positions by index or range
			  print                      Pretty-print a FEN
			  display                    Render a board image in a window
			  render                     Save a board image to disk
			  tags                       Generate tags for FENs, PGNs, or variations
			  text                       Summarize position tags with T5

			move subcommands:
			  list                       List legal moves for a FEN
			  uci                        List legal moves in UCI
			  san                        List legal moves in SAN
			  both                       List legal moves in UCI and SAN
			  to-san                     Convert one UCI move to SAN
			  to-uci                     Convert one SAN move to UCI
			  after                      Apply one move and print the resulting FEN
			  play                       Apply a move line and print the resulting FEN

			engine subcommands:
			  analyze                    Analyze a FEN with the engine
			  bestmove                   Print the best move for a FEN
			  bestmove-uci               Print the best move in UCI
			  bestmove-san               Print the best move in SAN
			  bestmove-both              Print the best move in UCI and SAN
			  builtin                    Search with the built-in Java engine
			  java                       Run the built-in Java engine
			  threats                    Analyze opponent threats
			  eval                       Evaluate a FEN with LC0 or classical
			  static                     Evaluate a FEN with the classical backend
			  perft                      Run perft on a FEN
			  perft-suite                Run a small perft regression suite
			  gpu                        Print GPU JNI backend status
			  uci-smoke                  Start engine and run a tiny UCI search

			book subcommands:
			  render                     Render a chess-book JSON/TOML file to a native PDF
			  cover                      Render a native PDF cover for a chess-book file
			  pdf                        Export chess diagrams to a PDF

			puzzle subcommands:
			  mine                       Mine chess puzzles
			  pgn                        Convert mixed puzzle dumps to PGN games
			  tags                       Generate per-move tags for puzzle PVs
			  text                       Run T5 over puzzle PVs

			record export plain options:
			  --input|-i PATH            Input .record JSON file
			  --output|-o PATH           Output .plain file (default: input stem + .plain)
			  --export-all|--sidelines   Include all sidelines (default: mainline only)
			  --filter|-f DSL            Filter-DSL to select records
			  --csv                      Also export a CSV file
			  --csv-output|-C PATH       Output CSV file path (default: input stem + .csv)

			record export csv options:
			  --input|-i PATH            Input .record JSON file
			  --output|-o PATH           Output CSV file (default: input stem + .csv)
			  --filter|-f DSL            Filter-DSL to select records

			record dataset npy options:
			  --input|-i PATH            Input .record JSON file
			  --output|-o PATH           Output dataset prefix (default: input stem + .dataset)

			record dataset lc0 options:
			  --input|-i PATH            Input .record JSON file
			  --output|-o PATH           Output dataset prefix (default: input stem + .lc0)
			  --weights PATH             Optional LC0 weights for policy-map compression

			record dataset classifier options:
			  --input|-i PATH            Input record file(s) or directories
			  --output|-o PATH           Output dataset prefix (default: input stem + .classifier)
			  --filter|-f DSL            Optional row-selection Filter DSL
			  --label-filter DSL         Optional positive-label Filter DSL (overrides kind)
			  --max-positives N          Cap positive rows
			  --max-negatives N          Cap negative rows
			  --recursive                Recurse into input directories
			  --verbose|-v               Print stack trace on failure

			record export puzzle-jsonl options:
			  --input|-i PATH            Input .record JSON file
			  --output|-o PATH           Output JSONL file (default: input stem + .puzzle.jsonl)
			  --weights PATH             LC0J weights path
			  --filter|-f DSL            Optional row-selection Filter DSL
			  --puzzles                  Keep only puzzle records
			  --nonpuzzles               Keep only non-puzzle records
			  --verbose|-v               Print stack trace on failure

			record export training-jsonl options:
			  --input|-i PATH            Input record file(s) or directories
			  --output|-o PATH           Output JSONL file (default: input stem + .training.jsonl)
			  --filter|-f DSL            Puzzle Filter DSL; matching rows become verified_puzzle
			  --recursive                Recurse into input directories
			  --include-engine-metadata  Include engine/PV metadata as metadata only
			  --max-records N            Stop after writing N rows (0/default: no cap)
			  --verbose|-v               Print stack trace on failure

			  Writes one chess position per JSONL line. Rows matching the puzzle DSL
			  get coarse_label=1/fine_label=2, rows with the same parent FEN as a
			  puzzle get coarse_label=1/fine_label=1, and all remaining rows get
			  coarse_label=0/fine_label=0. Engine metadata is not model input.

			record export pgn options:
			  --input|-i PATH            Input .record JSON file
			  --output|-o PATH           Output PGN file (default: input stem + .pgn)

			puzzle pgn options:
			  --input|-i PATH            Input mixed puzzle dump file
			  --output|-o PATH           Output PGN file (default: input stem + .pgn)

			record files options:
			  --input|-i PATH            Input record file(s) or directories
			  --output|-o PATH           Output record file or directory
			  --filter|-f DSL            Filter-DSL to select records
			  --puzzles                  Keep only puzzle records
			  --nonpuzzles               Keep only non-puzzle records
			  --max-records N            Split output after N records
			  --recursive                Recurse into input directories
			  --verbose|-v               Print stack trace on failure

			record analysis-delta options:
			  --input|-i PATH            Input record file
			  --output|-o PATH           Output JSONL path (default: input stem + .analysis-delta.jsonl)
			  --verbose|-v               Print stack trace on failure

			engine gpu options:
			  --verbose|-v               Print detailed output

			engine uci-smoke options:
			  --protocol-path|-P PATH    Engine protocol TOML file (default: config)
			  --nodes|--max-nodes N      Nodes for the smoke search (default: 1)
			  --max-duration D           Timeout for the smoke search (default: 5s)
			  --threads N                Optional engine thread count
			  --hash N                   Optional hash size in MB
			  --wdl                      Enable WDL output (if supported)
			  --no-wdl                   Disable WDL output
			  --verbose|-v               Print stack trace on failure

			fen generate options:
			  --output|-o PATH           Output directory
			  --files N                  Number of shard files to write
			  --per-file N               FENs per shard file
			  --batch N                  Positions per RNG batch
			  --chess960-files N         Additional shard files with Chess960 FENs
			  --verbose|-v               Print progress and output paths

			puzzle mine options (overrides & inputs):
			  --input|-i PATH            Input .txt (FENs) or .pgn (games) seed file
			  --output|-o PATH           Output root file or directory ("-" for stdout)
			  --protocol|-p PATH         Engine protocol TOML file
			  --engine-instances|-E N    Engine pool size
			  --max-nodes N              Max nodes per position
			  --max-duration D           Max duration per position (e.g. 5s)
			  --random-count N           Random seed count when no input is provided
			  --random-infinite          Keep generating random seeds
			  --max-waves N              Cap number of waves
			  --max-frontier N           Cap frontier size per wave
			  --max-total N              Cap total processed positions
			  --puzzle-quality DSL       Override puzzle quality filter DSL
			  --puzzle-winning DSL       Override puzzle winning filter DSL
			  --puzzle-drawing DSL       Override puzzle drawing filter DSL
			  --puzzle-accelerate DSL    Override accelerate filter DSL
			  --chess960|-9              Generate Chess960 positions
			  --verbose|-v               Print stack trace on failure

			fen print options:
			  --fen FEN                  FEN string to render
			  --verbose|-v               Print stack trace on failure

			fen display options:
			  --fen FEN                  FEN string to render
			  --backend B                Renderer backend (default: best available)
			  --show-backend             Print renderer backend info
			  --flip                     Render from Black's perspective
			  --black-down               Flip board so Black is at the bottom
			  --size N                   Window size (pixels)
			  --width N                  Override window width
			  --height N                 Override window height
			  --zoom Z                   Zoom factor (default: 1)
			  --dark|--dark-mode         Use dark theme
			  --arrow|--arrows MOVES     Draw arrow overlays (comma-separated squares)
			  --special-arrows           Draw special arrow overlays
			  --circle|--circles SQUARES Draw circle overlays
			  --legal SQUARE             Overlay legal move dots from a square
			  --details-inside           Show eval details inside board
			  --details-outside          Show eval details outside board
			  --ablation                 Overlay evaluator ablation heatmap
			  --verbose|-v               Print stack trace on failure

			fen render options:
			  --fen FEN                  FEN string to render
			  --output|-o PATH           Output image/SVG path
			  --format FORMAT            Image format (png, jpg, bmp, svg)
			  --backend B                Renderer backend (default: best available)
			  --show-backend             Print renderer backend info
			  --flip                     Render from Black's perspective
			  --black-down               Flip board so Black is at the bottom
			  --size N                   Board size (pixels)
			  --width N                  Override image width
			  --height N                 Override image height
			  --zoom Z                   Zoom factor (default: 1)
			  --dark|--dark-mode         Use dark theme
			  --drop-shadow|--shadow     Add a subtle drop shadow
			  --no-border                Hide the outer border
			  --arrow|--arrows MOVES     Draw arrow overlays (comma-separated squares)
			  --special-arrows           Draw special arrow overlays
			  --circle|--circles SQUARES Draw circle overlays
			  --legal SQUARE             Overlay legal move dots from a square
			  --details-inside           Show eval details inside board
			  --details-outside          Show eval details outside board
			  --ablation                 Overlay evaluator ablation heatmap
			  --verbose|-v               Print stack trace on failure

			book render options:
			  --input|-i PATH            Input chess-book JSON/TOML file
			  --output|-o PATH           Output PDF path (default: input stem + .pdf)
			  --title TEXT               Optional title override
			  --subtitle TEXT            Optional subtitle override
			  --limit N                  Render first N puzzles and update count text
			  --check|--validate         Validate manifest, dimensions, FENs, and solution lines without writing
			  --free-watermark|--watermark
			                             Add noisy free-edition watermark and print restrictions
			  --watermark-id TEXT        Add a traceable ID to the watermark; implies --watermark
			  --verbose|-v               Print stack trace on failure

			book cover options:
			  --input|-i PATH            Input chess-book JSON/TOML file
			  --output|-o PATH           Output cover PDF path (default: input stem + -cover.pdf)
			  --title TEXT               Optional title override
			  --subtitle TEXT            Optional subtitle override
			  --binding TYPE             paperback, hardcover, or ebook (default: paperback)
			  --interior TYPE            white-bw, cream-bw, white-standard-color, or white-premium-color
			  --pages N                  Printed page count for spine width (default: book pages/estimate)
			  --check|--validate         Validate manifest and cover dimensions without writing
			  --verbose|-v               Print stack trace on failure

			fen chess960 options:
			  --index N                  Print one Chess960 start position by Scharnagl index
			  --random                   Print one random Chess960 start position
			  --count N                  Number of random positions with --random
			  --all                      Print all 960 positions in index order
			  --format FORMAT            fen, layout, or both (default: fen)
			  N                          Positional shorthand for --index N

			book pdf options:
			  --fen FEN                  Input FEN (repeatable; positional FEN also allowed)
			  --input|-i PATH            Input FEN list / FEN-pair text file
			  --pgn PATH                 Input PGN file (exports one composition per mainline game)
			  --output|-o PATH           Output PDF path
			  --title TEXT               Document title override
			  --page-size SIZE           Page size: a4, a5, letter (default: a4)
			  --diagrams-per-row N       Diagrams per row (default: 2)
			  --board-pixels N           Raster size per diagram before embedding (default: 900)
			  --flip|--black-down        Render Black at the bottom
			  --no-fen                   Hide FEN text under diagrams
			  --verbose|-v               Print stack trace on failure

			gui options:
			  --fen FEN                  Start position (default: standard start FEN)
			  --flip|--black-down        Render Black at the bottom
			  --dark|--dark-mode         Start in dark UI theme
			  --light                    Start in light UI theme
			  -h|--help                  Show help

			gui-web options:
			  --fen FEN                  Start position (default: standard start FEN)
			  --flip|--black-down        Render Black at the bottom
			  --dark|--dark-mode         Start in dark UI theme
			  --light                    Start in light UI theme
			  -h|--help                  Show help

			gui-next options:
			  --fen FEN                  Start position (default: standard start FEN)
			  --flip|--black-down        Render Black at the bottom
			  --dark|--dark-mode         Start in dark UI theme
			  --light                    Start in light UI theme
			  -h|--help                  Show help

			config subcommands:
			  show                       Print config values
			  validate                   Validate config file

			record stats options:
			  --input|-i PATH            Input record file
			  --top N                    Number of top tags/engines to show
			  --verbose|-v               Print stack trace on failure

			record tag-stats options:
			  --input|-i PATH            Input record file
			  --top N                    Number of top tags to show
			  --verbose|-v               Print stack trace on failure

			fen tags options:
			  --fen FEN                  Input FEN (default: stdin)
			  --input|-i PATH            Input FEN list (supports parent/child pairs)
			  --pgn PATH                 Input PGN (use --sidelines for variations)
			  --include-fen              Include FEN in output
			  --sequence                 Interpret input as an ordered line
			  --delta                    Emit per-move tag deltas (JSONL)
			  --mainline                 Only export mainline from PGN
			  --sidelines                Include PGN variations
			  --analyze                  Run engine analysis to enrich tags
			  --protocol|-p PATH         Engine protocol TOML file
			  --max-nodes N              Max nodes per position
			  --max-duration D           Max duration per position (e.g. 5s)
			  --multipv N                Number of PVs
			  --threads N                Engine threads
			  --hash N                   Engine hash (MB)
			  --wdl                      Enable WDL output (if supported)
			  --no-wdl                   Disable WDL output
			  --verbose|-v               Print stack trace on failure

			puzzle tags options:
			  --fen FEN                  Root puzzle FEN (required)
			  --multipv N                Number of PVs to expand (default: 3)
			  --pv-plies N               PV plies per line (default: 12)
			  --tag-multipv N            Engine multipv during tag analysis (default: 1)
			  --analyze                  Run engine analysis to enrich tags (default)
			  --no-analyze               Skip per-move analysis
			  --protocol|-p PATH         Engine protocol TOML file
			  --max-nodes N              Max nodes per position
			  --max-duration D           Max duration per position (e.g. 5s)
			  --threads N                Engine threads
			  --hash N                   Engine hash (MB)
			  --wdl                      Enable WDL output (if supported)
			  --no-wdl                   Disable WDL output
			  --verbose|-v               Print stack trace on failure

			puzzle text options:
			  --model PATH               T5 .bin model path (default: config t5-model-path)
			  --fen FEN                  Root puzzle FEN (required)
			  --multipv N                Number of PVs to expand (default: 3)
			  --pv-plies N               PV plies per line (default: 12)
			  --tag-multipv N            Engine multipv during tag analysis (default: 1)
			  --max-new N                Max generated tokens (default: 128)
			  --include-fen              Emit JSON with fen + move + summary
			  --analyze                  Run engine analysis to enrich tags (default)
			  --no-analyze               Skip per-move analysis
			  --protocol|-p PATH         Engine protocol TOML file
			  --max-nodes N              Max nodes per position
			  --max-duration D           Max duration per position (e.g. 5s)
			  --threads N                Engine threads
			  --hash N                   Engine hash (MB)
			  --wdl                      Enable WDL output (if supported)
			  --no-wdl                   Disable WDL output
			  --verbose|-v               Print stack trace on failure

			fen text options:
			  --model PATH               T5 .bin model path (default: config t5-model-path)
			  --fen FEN                  Input FEN (default: stdin)
			  --input|-i PATH            Input FEN file
			  --include-fen              Emit JSON with fen + summary
			  --max-new N                Max generated tokens (default: 128)
			  --analyze                  Run engine analysis to enrich tags
			  --protocol|-p PATH         Engine protocol TOML file
			  --max-nodes N              Max nodes per position
			  --max-duration D           Max duration per position (e.g. 5s)
			  --multipv N                Number of PVs
			  --threads N                Engine threads
			  --hash N                   Engine hash (MB)
			  --wdl                      Enable WDL output (if supported)
			  --no-wdl                   Disable WDL output
			  --verbose|-v               Print stack trace on failure

			move list options:
			  --fen FEN                  Input FEN (default: stdin)
			  --format FORMAT            uci, san, or both (default: uci)
			  --san                      Alias for --format san
			  --both                     Alias for --format both
			  --verbose|-v               Print stack trace on failure

			move uci options:
			  --fen FEN                  Input FEN (default: stdin)
			  --verbose|-v               Print stack trace on failure

			move san options:
			  --fen FEN                  Input FEN (default: stdin)
			  --verbose|-v               Print stack trace on failure

			move both options:
			  --fen FEN                  Input FEN (default: stdin)
			  --verbose|-v               Print stack trace on failure

			move to-san options:
			  --fen FEN                  Starting FEN (default: standard start)
			  MOVE                       UCI move to convert
			  --verbose|-v               Print stack trace on failure

			move to-uci options:
			  --fen FEN                  Starting FEN (default: standard start)
			  MOVE                       SAN move to convert
			  --verbose|-v               Print stack trace on failure

			move after options:
			  --fen FEN                  Starting FEN (default: standard start)
			  MOVE                       UCI or SAN move to apply
			  --verbose|-v               Print stack trace on failure

			move play options:
			  --fen FEN                  Starting FEN (default: standard start)
			  MOVES                      UCI/SAN move sequence to apply
			  --intermediate             Print FEN after each ply
			  --verbose|-v               Print stack trace on failure

			fen normalize options:
			  --fen FEN                  FEN to normalize, or positional FEN
			  --verbose|-v               Print stack trace on failure

			fen validate options:
			  --fen FEN                  FEN to validate, or positional FEN
			  --verbose|-v               Print stack trace on failure

			engine analyze options:
			  --fen FEN                  Input FEN (default: stdin)
			  --input|-i PATH            Input FEN file
			  --protocol|-p PATH         Engine protocol TOML file
			  --max-nodes N              Max nodes per position
			  --max-duration D           Max duration per position (e.g. 5s)
			  --multipv N                Number of PVs
			  --threads N                Engine threads
			  --hash N                   Engine hash (MB)
			  --wdl                      Enable WDL output (if supported)
			  --no-wdl                   Disable WDL output
			  --verbose|-v               Print stack trace on failure

			engine bestmove options:
			  --fen FEN                  Input FEN (default: stdin)
			  --input|-i PATH            Input FEN file
			  --format FORMAT            uci, san, or both (default: uci)
			  --protocol|-p PATH         Engine protocol TOML file
			  --max-nodes N              Max nodes per position
			  --max-duration D           Max duration per position (e.g. 5s)
			  --multipv N                Number of PVs
			  --threads N                Engine threads
			  --hash N                   Engine hash (MB)
			  --wdl                      Enable WDL output (if supported)
			  --no-wdl                   Disable WDL output
			  --san                      Print SAN output
			  --both                     Print UCI + SAN output
			  --verbose|-v               Print stack trace on failure

			engine bestmove-uci options:
			  --fen FEN                  Input FEN (default: stdin)
			  --input|-i PATH            Input FEN file
			  --protocol|-p PATH         Engine protocol TOML file
			  --max-nodes N              Max nodes per position
			  --max-duration D           Max duration per position (e.g. 5s)
			  --multipv N                Number of PVs
			  --threads N                Engine threads
			  --hash N                   Engine hash (MB)
			  --wdl                      Enable WDL output (if supported)
			  --no-wdl                   Disable WDL output
			  --verbose|-v               Print stack trace on failure

			engine bestmove-san options:
			  --fen FEN                  Input FEN (default: stdin)
			  --input|-i PATH            Input FEN file
			  --protocol|-p PATH         Engine protocol TOML file
			  --max-nodes N              Max nodes per position
			  --max-duration D           Max duration per position (e.g. 5s)
			  --multipv N                Number of PVs
			  --threads N                Engine threads
			  --hash N                   Engine hash (MB)
			  --wdl                      Enable WDL output (if supported)
			  --no-wdl                   Disable WDL output
			  --verbose|-v               Print stack trace on failure

			engine bestmove-both options:
			  --fen FEN                  Input FEN (default: stdin)
			  --input|-i PATH            Input FEN file
			  --protocol|-p PATH         Engine protocol TOML file
			  --max-nodes N              Max nodes per position
			  --max-duration D           Max duration per position (e.g. 5s)
			  --multipv N                Number of PVs
			  --threads N                Engine threads
			  --hash N                   Engine hash (MB)
			  --wdl                      Enable WDL output (if supported)
			  --no-wdl                   Disable WDL output
			  --verbose|-v               Print stack trace on failure

			engine builtin options:
			  --fen FEN                  Input FEN
			  --input|-i PATH            Input FEN file
			  --evaluator KIND           classical, nnue, or lc0 (default: classical)
			  --classical|--nnue|--lc0   Shortcut evaluator selectors
			  --weights PATH             NNUE or LC0 weights path
			  --depth|-d N               Search depth in plies (default: 3)
			  --max-nodes|--nodes N      Node budget; 0 means unlimited; omitted means unlimited with --depth
			  --max-duration D           Time budget, e.g. 5s; 0 means unlimited; omitted means unlimited with --depth
			  --format FORMAT            uci-info, uci, san, both, or summary (default: uci-info)
			  --verbose|-v               Print stack trace on failure

			engine threats options:
			  --fen FEN                  Input FEN (default: stdin)
			  --protocol|-p PATH         Engine protocol TOML file
			  --max-nodes N              Max nodes per position
			  --max-duration D           Max duration per position (e.g. 5s)
			  --multipv N                Number of PVs
			  --threads N                Engine threads
			  --hash N                   Engine hash (MB)
			  --wdl                      Enable WDL output (if supported)
			  --no-wdl                   Disable WDL output
			  --verbose|-v               Print stack trace on failure

			engine perft options:
			  --fen FEN                  Input FEN
			  --depth|-d N               Depth for perft
			  --divide|--per-move        Print per-root-move detailed counters
			  --verbose|-v               Print stack trace on failure

			engine perft-suite options:
			  --depth|-d N               Depth to compare (default: 6)
			  --threads N                Worker threads for positions (default: 1)
			  --stockfish PATH           Stockfish executable (default: stockfish)

			fen pgn options:
			  --input|-i PATH            Input PGN file
			  --output|-o PATH           Output FEN list (default: input stem + .txt)
			  --mainline                 Only output mainline positions
			  --pairs                    Output (parent, child) pairs
			  --verbose|-v               Print stack trace on failure

			engine eval options:
			  --fen FEN                  Input FEN (default: stdin)
			  --input|-i PATH            Input FEN file
			  --lc0                      Use LC0 evaluator only
			  --classical                Use classical evaluator only
			  --terminal-aware|--terminal Enable terminal-aware evaluation
			  --weights PATH             LC0 weights path
			  --verbose|-v               Print stack trace on failure

			engine static options:
			  --fen FEN                  Input FEN (default: stdin)
			  --input|-i PATH            Input FEN file
			  --terminal-aware|--terminal Enable terminal-aware evaluation
			  --verbose|-v               Print stack trace on failure

			clean options:
			  --verbose|-v               Print stack trace on failure

			doctor options:
			  --strict                   Exit non-zero when warnings are present
			  --verbose|-v               Print stack trace on failure

			help options:
			  --full                      Show full help output
			  <command>                   Show help for one command
			""";
}
