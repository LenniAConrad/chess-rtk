package application.cli.command;

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
	 * Help marker for {@code record-to-plain}.
	 */
	private static final String RECORD_TO_PLAIN_OPTIONS_MARKER = "record-to-plain options:";

	/**
	 * Help marker for {@code record-to-csv}.
	 */
	private static final String RECORD_TO_CSV_OPTIONS_MARKER = "record-to-csv options:";

	/**
	 * Help marker for {@code record-to-dataset}.
	 */
	private static final String RECORD_TO_DATASET_OPTIONS_MARKER = "record-to-dataset options:";

	/**
	 * Help marker for {@code record-to-pgn}.
	 */
	private static final String RECORD_TO_PGN_OPTIONS_MARKER = "record-to-pgn options:";

	/**
	 * Help marker for {@code puzzles-to-pgn}.
	 */
	private static final String PUZZLES_TO_PGN_OPTIONS_MARKER = "puzzles-to-pgn options:";

	/**
	 * Help marker for {@code records}.
	 */
	private static final String RECORDS_OPTIONS_MARKER = "records options:";

	/**
	 * Help marker for {@code stack-to-dataset}.
	 */
	private static final String STACK_TO_DATASET_OPTIONS_MARKER = "stack-to-dataset options:";

	/**
	 * Help marker for {@code gpu-info} and {@code cuda-info}.
	 */
	private static final String GPU_INFO_OPTIONS_MARKER = "gpu-info options:";

	/**
	 * Help marker for {@code gen-fens}.
	 */
	private static final String GEN_FENS_OPTIONS_MARKER = "gen-fens options:";

	/**
	 * Help marker for {@code mine-puzzles}.
	 */
	private static final String MINE_PUZZLES_OPTIONS_MARKER = "mine-puzzles options (overrides & inputs):";

	/**
	 * Help marker for {@code print}.
	 */
	private static final String PRINT_OPTIONS_MARKER = "print options:";

	/**
	 * Help marker for {@code display}.
	 */
	private static final String DISPLAY_OPTIONS_MARKER = "display options:";

	/**
	 * Help marker for {@code render}.
	 */
	private static final String RENDER_OPTIONS_MARKER = "render options:";

	/**
	 * Help marker for {@code config}.
	 */
	private static final String CONFIG_SUBCOMMANDS_MARKER = "config subcommands:";

	/**
	 * Help marker for {@code stats}.
	 */
	private static final String STATS_OPTIONS_MARKER = "stats options:";

	/**
	 * Help marker for {@code stats-tags}.
	 */
	private static final String STATS_TAGS_OPTIONS_MARKER = "stats-tags options:";

	/**
	 * Help marker for {@code tags}.
	 */
	private static final String TAGS_OPTIONS_MARKER = "tags options:";

	/**
	 * Help marker for {@code moves}.
	 */
	private static final String MOVES_OPTIONS_MARKER = "moves options:";

	/**
	 * Help marker for {@code moves-uci}.
	 */
	private static final String MOVES_UCI_OPTIONS_MARKER = "moves-uci options:";

	/**
	 * Help marker for {@code moves-san}.
	 */
	private static final String MOVES_SAN_OPTIONS_MARKER = "moves-san options:";

	/**
	 * Help marker for {@code moves-both}.
	 */
	private static final String MOVES_BOTH_OPTIONS_MARKER = "moves-both options:";

	/**
	 * Help marker for {@code analyze}.
	 */
	private static final String ANALYZE_OPTIONS_MARKER = "analyze options:";

	/**
	 * Help marker for {@code bestmove}.
	 */
	private static final String BESTMOVE_OPTIONS_MARKER = "bestmove options:";

	/**
	 * Help marker for {@code bestmove-uci}.
	 */
	private static final String BESTMOVE_UCI_OPTIONS_MARKER = "bestmove-uci options:";

	/**
	 * Help marker for {@code bestmove-san}.
	 */
	private static final String BESTMOVE_SAN_OPTIONS_MARKER = "bestmove-san options:";

	/**
	 * Help marker for {@code bestmove-both}.
	 */
	private static final String BESTMOVE_BOTH_OPTIONS_MARKER = "bestmove-both options:";

	/**
	 * Help marker for {@code threats}.
	 */
	private static final String THREATS_OPTIONS_MARKER = "threats options:";

	/**
	 * Help marker for {@code perft}.
	 */
	private static final String PERFT_OPTIONS_MARKER = "perft options:";

	/**
	 * Help marker for {@code perft-suite}.
	 */
	private static final String PERFT_SUITE_OPTIONS_MARKER = "perft-suite options:";

	/**
	 * Help marker for {@code pgn-to-fens}.
	 */
	private static final String PGN_TO_FENS_OPTIONS_MARKER = "pgn-to-fens options:";

	/**
	 * Help marker for {@code eval} and {@code evaluate}.
	 */
	private static final String EVAL_OPTIONS_MARKER = "eval options:";

	/**
	 * Help marker for {@code eval-static}.
	 */
	private static final String EVAL_STATIC_OPTIONS_MARKER = "eval-static options:";

	/**
	 * Help marker for {@code clean}.
	 */
	private static final String CLEAN_OPTIONS_MARKER = "clean options:";

	/**
	 * Help marker for {@code help} variants.
	 */
	private static final String HELP_OPTIONS_MARKER = "help options:";

	/**
	 * Maps command names to their help section markers.
	 */
	private static final Map<String, String> HELP_MARKERS = Map.ofEntries(
			Map.entry(CMD_RECORD_TO_PLAIN, RECORD_TO_PLAIN_OPTIONS_MARKER),
			Map.entry(CMD_RECORD_TO_CSV, RECORD_TO_CSV_OPTIONS_MARKER),
			Map.entry(CMD_RECORD_TO_DATASET, RECORD_TO_DATASET_OPTIONS_MARKER),
			Map.entry(CMD_RECORD_TO_PGN, RECORD_TO_PGN_OPTIONS_MARKER),
			Map.entry(CMD_PUZZLES_TO_PGN, PUZZLES_TO_PGN_OPTIONS_MARKER),
			Map.entry(CMD_RECORDS, RECORDS_OPTIONS_MARKER),
			Map.entry(CMD_STACK_TO_DATASET, STACK_TO_DATASET_OPTIONS_MARKER),
			Map.entry(CMD_GPU_INFO, GPU_INFO_OPTIONS_MARKER),
			Map.entry(CMD_CUDA_INFO, GPU_INFO_OPTIONS_MARKER),
			Map.entry(CMD_GEN_FENS, GEN_FENS_OPTIONS_MARKER),
			Map.entry(CMD_MINE_PUZZLES, MINE_PUZZLES_OPTIONS_MARKER),
			Map.entry(CMD_MINE, MINE_PUZZLES_OPTIONS_MARKER),
			Map.entry(CMD_PRINT, PRINT_OPTIONS_MARKER),
			Map.entry(CMD_DISPLAY, DISPLAY_OPTIONS_MARKER),
			Map.entry(CMD_RENDER, RENDER_OPTIONS_MARKER),
			Map.entry(CMD_CONFIG, CONFIG_SUBCOMMANDS_MARKER),
			Map.entry(CMD_STATS, STATS_OPTIONS_MARKER),
			Map.entry(CMD_STATS_TAGS, STATS_TAGS_OPTIONS_MARKER),
			Map.entry(CMD_TAGS, TAGS_OPTIONS_MARKER),
			Map.entry(CMD_MOVES, MOVES_OPTIONS_MARKER),
			Map.entry(CMD_MOVES_UCI, MOVES_UCI_OPTIONS_MARKER),
			Map.entry(CMD_MOVES_SAN, MOVES_SAN_OPTIONS_MARKER),
			Map.entry(CMD_MOVES_BOTH, MOVES_BOTH_OPTIONS_MARKER),
			Map.entry(CMD_ANALYZE, ANALYZE_OPTIONS_MARKER),
			Map.entry(CMD_BESTMOVE, BESTMOVE_OPTIONS_MARKER),
			Map.entry(CMD_BESTMOVE_UCI, BESTMOVE_UCI_OPTIONS_MARKER),
			Map.entry(CMD_BESTMOVE_SAN, BESTMOVE_SAN_OPTIONS_MARKER),
			Map.entry(CMD_BESTMOVE_BOTH, BESTMOVE_BOTH_OPTIONS_MARKER),
			Map.entry(CMD_THREATS, THREATS_OPTIONS_MARKER),
			Map.entry(CMD_PERFT, PERFT_OPTIONS_MARKER),
			Map.entry(CMD_PERFT_SUITE, PERFT_SUITE_OPTIONS_MARKER),
			Map.entry(CMD_PGN_TO_FENS, PGN_TO_FENS_OPTIONS_MARKER),
			Map.entry(CMD_EVAL, EVAL_OPTIONS_MARKER),
			Map.entry(CMD_EVALUATE, EVAL_OPTIONS_MARKER),
			Map.entry(CMD_EVAL_STATIC, EVAL_STATIC_OPTIONS_MARKER),
			Map.entry(CMD_CLEAN, CLEAN_OPTIONS_MARKER),
			Map.entry(CMD_HELP, HELP_OPTIONS_MARKER),
			Map.entry(CMD_HELP_SHORT, HELP_OPTIONS_MARKER),
			Map.entry(CMD_HELP_LONG, HELP_OPTIONS_MARKER));

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
			helpCommand(rest.get(0));
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
				  record-to-plain   Convert .record JSON to .plain
				  record-to-csv     Convert .record JSON to CSV (no .plain output)
				  record-to-pgn     Convert .record JSON to PGN games
				  puzzles-to-pgn    Convert mixed puzzle dumps to PGN games
				  records           Merge/filter/split record files
				  record-to-dataset Convert .record JSON to NPY tensors (features/labels)
				  stack-to-dataset  Convert Stack-*.json puzzle dumps to NPY tensors
				  gpu-info          Print GPU JNI backend status
				  gen-fens          Generate random legal FEN shards (standard + Chess960 mix)
				  mine-puzzles      Mine chess puzzles (supports Chess960 / PGN / FEN list / random)
				  print             Pretty-print a FEN
				  display           Render a board image in a window
				  render            Save a board image to disk
				  config            Show/validate configuration
				  stats             Summarize .record or puzzle dumps
				  stats-tags        Summarize tag distributions
				  tags              Generate tags for a FEN (or list)
				  moves             List legal moves for a FEN
				  moves-uci         List legal moves (UCI)
				  moves-san         List legal moves (SAN)
				  moves-both        List legal moves (UCI + SAN)
				  analyze           Analyze a FEN with the engine
				  bestmove          Print the best move for a FEN
				  bestmove-uci      Print the best move (UCI)
				  bestmove-san      Print the best move (SAN)
				  bestmove-both     Print the best move (UCI + SAN)
				  threats           Analyze opponent threats (null move)
				  perft             Run perft on a FEN (movegen validation)
				  perft-suite       Run a small perft regression suite
				  pgn-to-fens       Convert PGN games to FEN lists
				  eval              Evaluate a FEN with LC0 or classical (alias: evaluate)
				  eval-static       Evaluate a FEN with the classical backend
				  clean             Delete session cache/logs
				  help              Show command help

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
		return HELP_MARKERS.get(command);
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
			  record-to-plain Convert .record JSON to .plain
			  record-to-csv  Convert .record JSON to CSV (no .plain output)
			  record-to-pgn  Convert .record JSON to PGN games
			  puzzles-to-pgn Convert mixed puzzle dumps to PGN games
			  records   Merge/filter/split record files
			  record-to-dataset Convert .record JSON to NPY tensors (features/labels)
			  stack-to-dataset Convert Stack-*.json puzzle dumps to NPY tensors
			  gpu-info Print GPU JNI backend status
			  gen-fens  Generate random legal FEN shards (standard + Chess960 mix)
			  mine-puzzles Mine chess puzzles (supports Chess960 / PGN / FEN list / random)
			  print     Pretty-print a FEN
			  display   Render a board image in a window
			  render    Save a board image to disk
			  config    Show/validate configuration
			  stats     Summarize .record or puzzle dumps
			  stats-tags Summarize tag distributions
			  tags      Generate tags for a FEN (or list)
			  moves     List legal moves for a FEN
			  moves-uci List legal moves (UCI)
			  moves-san List legal moves (SAN)
			  moves-both List legal moves (UCI + SAN)
			  analyze   Analyze a FEN with the engine
			  bestmove  Print the best move for a FEN
			  bestmove-uci Print the best move (UCI)
			  bestmove-san Print the best move (SAN)
			  bestmove-both Print the best move (UCI + SAN)
			  threats   Analyze opponent threats (null move)
			  perft     Run perft on a FEN (movegen validation)
			  perft-suite Run a small perft regression suite
			  pgn-to-fens Convert PGN games to FEN lists
			  eval      Evaluate a FEN with LC0 or classical (alias: evaluate)
			  eval-static Evaluate a FEN with the classical backend
			  clean     Delete session cache/logs
			  help      Show command help

			record-to-plain options:
			  --input|-i PATH            Input .record JSON file
			  --output|-o PATH           Output .plain file (default: input stem + .plain)
			  --export-all|--sidelines   Include all sidelines (default: mainline only)
			  --filter|-f DSL            Filter-DSL to select records
			  --csv                      Also export a CSV file
			  --csv-output|-C PATH       Output CSV file path (default: input stem + .csv)

			record-to-csv options:
			  --input|-i PATH            Input .record JSON file
			  --output|-o PATH           Output CSV file (default: input stem + .csv)
			  --filter|-f DSL            Filter-DSL to select records

			record-to-dataset options:
			  --input|-i PATH            Input .record JSON file
			  --output|-o PATH           Output dataset prefix (default: input stem + .dataset)

			record-to-pgn options:
			  --input|-i PATH            Input .record JSON file
			  --output|-o PATH           Output PGN file (default: input stem + .pgn)

			puzzles-to-pgn options:
			  --input|-i PATH            Input mixed puzzle dump file
			  --output|-o PATH           Output PGN file (default: input stem + .pgn)

			records options:
			  --input|-i PATH            Input record file(s) or directories
			  --output|-o PATH           Output record file or directory
			  --filter|-f DSL            Filter-DSL to select records
			  --puzzles                  Keep only puzzle records
			  --nonpuzzles               Keep only non-puzzle records
			  --max-records N            Split output after N records
			  --recursive                Recurse into input directories
			  --verbose|-v               Print stack trace on failure

			stack-to-dataset options:
			  --input|-i PATH            Input Stack-*.json dump file
			  --output|-o PATH           Output dataset prefix (default: input stem + .dataset)

			gpu-info options:
			  --verbose|-v               Print detailed output

			gen-fens options:
			  --output|-o PATH           Output directory
			  --files N                  Number of shard files to write
			  --per-file N               FENs per shard file
			  --batch N                  Positions per RNG batch
			  --chess960-files N         Additional shard files with Chess960 FENs
			  --verbose|-v               Print progress and output paths

			mine-puzzles options (overrides & inputs):
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

			print options:
			  --fen FEN                  FEN string to render
			  --verbose|-v               Print stack trace on failure

			display options:
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
			  --arrows MOVES             Draw arrow overlays (comma-separated squares)
			  --special-arrows MOVES     Draw special arrow overlays
			  --circles SQUARES          Draw circle overlays
			  --show-legal               Overlay legal move dots
			  --details-inside           Show eval details inside board
			  --details-outside          Show eval details outside board
			  --ablation                 Overlay evaluator ablation heatmap
			  --verbose|-v               Print stack trace on failure

			render options:
			  --fen FEN                  FEN string to render
			  --output|-o PATH           Output image path
			  --format FORMAT            Image format (png, jpg)
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
			  --arrows MOVES             Draw arrow overlays (comma-separated squares)
			  --special-arrows MOVES     Draw special arrow overlays
			  --circles SQUARES          Draw circle overlays
			  --show-legal               Overlay legal move dots
			  --details-inside           Show eval details inside board
			  --details-outside          Show eval details outside board
			  --ablation                 Overlay evaluator ablation heatmap
			  --verbose|-v               Print stack trace on failure

			config subcommands:
			  show                       Print config values
			  validate                   Validate config file

			stats options:
			  --input|-i PATH            Input record file
			  --top N                    Number of top tags/engines to show
			  --verbose|-v               Print stack trace on failure

			stats-tags options:
			  --input|-i PATH            Input record file
			  --top N                    Number of top tags to show
			  --verbose|-v               Print stack trace on failure

			tags options:
			  --fen FEN                  Input FEN (default: stdin)
			  --include-fen              Include FEN in output
			  --verbose|-v               Print stack trace on failure

			moves options:
			  --fen FEN                  Input FEN (default: stdin)
			  --include-fen              Include FEN in output
			  --verbose|-v               Print stack trace on failure

			moves-uci options:
			  --fen FEN                  Input FEN (default: stdin)
			  --include-fen              Include FEN in output
			  --verbose|-v               Print stack trace on failure

			moves-san options:
			  --fen FEN                  Input FEN (default: stdin)
			  --include-fen              Include FEN in output
			  --verbose|-v               Print stack trace on failure

			moves-both options:
			  --fen FEN                  Input FEN (default: stdin)
			  --include-fen              Include FEN in output
			  --verbose|-v               Print stack trace on failure

			analyze options:
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

			bestmove options:
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
			  --san                      Print SAN output
			  --both                     Print UCI + SAN output
			  --verbose|-v               Print stack trace on failure

			bestmove-uci options:
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

			bestmove-san options:
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

			bestmove-both options:
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

			threats options:
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

			perft options:
			  --fen FEN                  Input FEN
			  --depth N                  Depth for perft
			  --divide                   Print divide output

			perft-suite options:
			  (no options)

			pgn-to-fens options:
			  --input|-i PATH            Input PGN file
			  --output|-o PATH           Output FEN list (default: input stem + .txt)
			  --mainline                 Only output mainline positions
			  --pairs                    Output (parent, child) pairs
			  --verbose|-v               Print stack trace on failure

			eval options:
			  --fen FEN                  Input FEN (default: stdin)
			  --input|-i PATH            Input FEN file
			  --lc0                      Use LC0 evaluator only
			  --classical                Use classical evaluator only
			  --terminal-aware|--terminal Enable terminal-aware evaluation
			  --weights PATH             LC0 weights path
			  --verbose|-v               Print stack trace on failure

			eval-static options:
			  --fen FEN                  Input FEN (default: stdin)
			  --input|-i PATH            Input FEN file
			  --terminal-aware|--terminal Enable terminal-aware evaluation
			  --verbose|-v               Print stack trace on failure

			clean options:
			  --verbose|-v               Print stack trace on failure

			help options:
			  --full                      Show full help output
			  <command>                   Show help for one command
			""";
}
