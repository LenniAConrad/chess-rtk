package application.cli;

/**
 * Shared CLI string constants.
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Constants {

	/**
	 * Utility class; prevent instantiation.
	 */
	private Constants() {
		// utility
	}

	/**
	 * {@code record} grouped subcommand token.
	 */
	public static final String CMD_RECORD = "record";

	/**
	 * {@code fen} grouped subcommand token.
	 */
	public static final String CMD_FEN = "fen";

	/**
	 * {@code move} grouped subcommand token.
	 */
	public static final String CMD_MOVE = "move";

	/**
	 * {@code engine} grouped subcommand token.
	 */
	public static final String CMD_ENGINE = "engine";

	/**
	 * {@code book} grouped subcommand token.
	 */
	public static final String CMD_BOOK = "book";

	/**
	 * {@code puzzle} grouped subcommand token.
	 */
	public static final String CMD_PUZZLE = "puzzle";

	/**
	 * {@code uci-smoke} subcommand token.
	 */
	public static final String CMD_UCI_SMOKE = "uci-smoke";

	/**
	 * {@code print} subcommand token.
	 */
	public static final String CMD_PRINT = "print";

	/**
	 * {@code display} subcommand token.
	 */
	public static final String CMD_DISPLAY = "display";

	/**
	 * {@code render} subcommand token.
	 */
	public static final String CMD_RENDER = "render";

	/**
	 * {@code chess960} subcommand token.
	 */
	public static final String CMD_CHESS960 = "chess960";

	/**
	 * {@code gui} subcommand token.
	 */
	public static final String CMD_GUI = "gui";

	/**
	 * {@code gui-web} subcommand token.
	 */
	public static final String CMD_GUI_WEB = "gui-web";

	/**
	 * {@code gui-next} subcommand token.
	 */
	public static final String CMD_GUI_NEXT = "gui-next";

	/**
	 * {@code clean} subcommand token.
	 */
	public static final String CMD_CLEAN = "clean";

	/**
	 * {@code doctor} subcommand token.
	 */
	public static final String CMD_DOCTOR = "doctor";

	/**
	 * {@code help} subcommand token.
	 */
	public static final String CMD_HELP = "help";

	/**
	 * {@code config} subcommand token.
	 */
	public static final String CMD_CONFIG = "config";

	/**
	 * {@code tags} subcommand token.
	 */
	public static final String CMD_TAGS = "tags";

	/**
	 * {@code analyze} subcommand token.
	 */
	public static final String CMD_ANALYZE = "analyze";

	/**
	 * {@code bestmove} subcommand token.
	 */
	public static final String CMD_BESTMOVE = "bestmove";

	/**
	 * {@code bestmove-uci} subcommand token.
	 */
	public static final String CMD_BESTMOVE_UCI = "bestmove-uci";

	/**
	 * {@code bestmove-san} subcommand token.
	 */
	public static final String CMD_BESTMOVE_SAN = "bestmove-san";

	/**
	 * {@code bestmove-both} subcommand token.
	 */
	public static final String CMD_BESTMOVE_BOTH = "bestmove-both";

	/**
	 * {@code threats} subcommand token.
	 */
	public static final String CMD_THREATS = "threats";

	/**
	 * {@code perft} subcommand token.
	 */
	public static final String CMD_PERFT = "perft";

	/**
	 * {@code perft-suite} subcommand token.
	 */
	public static final String CMD_PERFT_SUITE = "perft-suite";

	/**
	 * {@code eval} subcommand token.
	 */
	public static final String CMD_EVAL = "eval";

	/**
	 * Short help flag alias.
	 */
	public static final String CMD_HELP_SHORT = "-h";

	/**
	 * Long help flag alias.
	 */
	public static final String CMD_HELP_LONG = "--help";

	/**
	 * {@code --input} option flag.
	 */
	public static final String OPT_INPUT = "--input";

	/**
	 * Short {@code --input} flag alias.
	 */
	public static final String OPT_INPUT_SHORT = "-i";

	/**
	 * {@code --pgn} option flag.
	 */
	public static final String OPT_PGN = "--pgn";

	/**
	 * {@code --pdf} option flag.
	 */
	public static final String OPT_PDF = "--pdf";

	/**
	 * {@code --fen} option flag.
	 */
	public static final String OPT_FEN = "--fen";

	/**
	 * {@code --startpos} option flag.
	 */
	public static final String OPT_STARTPOS = "--startpos";

	/**
	 * {@code --randompos} option flag.
	 */
	public static final String OPT_RANDOMPOS = "--randompos";

	/**
	 * {@code --include-fen} option flag.
	 */
	public static final String OPT_INCLUDE_FEN = "--include-fen";

	/**
	 * {@code --output} option flag.
	 */
	public static final String OPT_OUTPUT = "--output";

	/**
	 * Short {@code --output} flag alias.
	 */
	public static final String OPT_OUTPUT_SHORT = "-o";

	/**
	 * {@code --format} option flag.
	 */
	public static final String OPT_FORMAT = "--format";

	/**
	 * {@code --index} option flag.
	 */
	public static final String OPT_INDEX = "--index";

	/**
	 * {@code --all} option flag.
	 */
	public static final String OPT_ALL = "--all";

	/**
	 * {@code --random} option flag.
	 */
	public static final String OPT_RANDOM = "--random";

	/**
	 * {@code --count} option flag.
	 */
	public static final String OPT_COUNT = "--count";

	/**
	 * {@code --check} option flag.
	 */
	public static final String OPT_CHECK = "--check";

	/**
	 * {@code --validate} option flag alias.
	 */
	public static final String OPT_VALIDATE = "--validate";

	/**
	 * {@code --title} option flag.
	 */
	public static final String OPT_TITLE = "--title";

	/**
	 * {@code --subtitle} option flag.
	 */
	public static final String OPT_SUBTITLE = "--subtitle";

	/**
	 * {@code --limit} option flag.
	 */
	public static final String OPT_LIMIT = "--limit";

	/**
	 * {@code --pages} option flag.
	 */
	public static final String OPT_PAGES = "--pages";

	/**
	 * {@code --binding} option flag.
	 */
	public static final String OPT_BINDING = "--binding";

	/**
	 * {@code --interior} option flag.
	 */
	public static final String OPT_INTERIOR = "--interior";

	/**
	 * {@code --free-watermark} option flag.
	 */
	public static final String OPT_FREE_WATERMARK = "--free-watermark";

	/**
	 * {@code --watermark} option flag alias.
	 */
	public static final String OPT_WATERMARK = "--watermark";

	/**
	 * {@code --watermark-id} option flag.
	 */
	public static final String OPT_WATERMARK_ID = "--watermark-id";

	/**
	 * {@code --page-size} option flag.
	 */
	public static final String OPT_PAGE_SIZE = "--page-size";

	/**
	 * {@code --diagrams-per-row} option flag.
	 */
	public static final String OPT_DIAGRAMS_PER_ROW = "--diagrams-per-row";

	/**
	 * {@code --board-pixels} option flag.
	 */
	public static final String OPT_BOARD_PIXELS = "--board-pixels";

	/**
	 * {@code --no-fen} option flag.
	 */
	public static final String OPT_NO_FEN = "--no-fen";

	/**
	 * {@code --verbose} option flag.
	 */
	public static final String OPT_VERBOSE = "--verbose";

	/**
	 * Short {@code --verbose} flag alias.
	 */
	public static final String OPT_VERBOSE_SHORT = "-v";

	/**
	 * {@code --analyze} option flag.
	 */
	public static final String OPT_ANALYZE = "--analyze";

	/**
	 * {@code --no-analyze} option flag.
	 */
	public static final String OPT_NO_ANALYZE = "--no-analyze";

	/**
	 * {@code --sequence} option flag.
	 */
	public static final String OPT_SEQUENCE = "--sequence";

	/**
	 * {@code --delta} option flag.
	 */
	public static final String OPT_DELTA = "--delta";

	/**
	 * {@code --intermediate} option flag.
	 */
	public static final String OPT_INTERMEDIATE = "--intermediate";

	/**
	 * {@code --sidelines} option flag.
	 */
	public static final String OPT_SIDELINES = "--sidelines";

	/**
	 * {@code --export-all} option flag (alias for {@link #OPT_SIDELINES}).
	 */
	public static final String OPT_EXPORT_ALL = "--export-all";

	/**
	 * Short {@code --export-all} flag alias.
	 */
	public static final String OPT_EXPORT_ALL_SHORT = "-a";

	/**
	 * {@code --filter} option flag.
	 */
	public static final String OPT_FILTER = "--filter";

	/**
	 * Short {@code --filter} flag alias.
	 */
	public static final String OPT_FILTER_SHORT = "-f";

	/**
	 * {@code --max-records} option flag.
	 */
	public static final String OPT_MAX_RECORDS = "--max-records";

	/**
	 * {@code --label-filter} option flag.
	 */
	public static final String OPT_LABEL_FILTER = "--label-filter";

	/**
	 * {@code --include-engine-metadata} option flag.
	 */
	public static final String OPT_INCLUDE_ENGINE_METADATA = "--include-engine-metadata";

	/**
	 * {@code --max-positives} option flag.
	 */
	public static final String OPT_MAX_POSITIVES = "--max-positives";

	/**
	 * {@code --max-negatives} option flag.
	 */
	public static final String OPT_MAX_NEGATIVES = "--max-negatives";

	/**
	 * {@code --puzzles} option flag.
	 */
	public static final String OPT_PUZZLES = "--puzzles";

	/**
	 * {@code --nonpuzzles} option flag.
	 */
	public static final String OPT_NONPUZZLES = "--nonpuzzles";

	/**
	 * {@code --recursive} option flag.
	 */
	public static final String OPT_RECURSIVE = "--recursive";

	/**
	 * {@code --csv} option flag.
	 */
	public static final String OPT_CSV = "--csv";

	/**
	 * {@code --csv-output} option flag.
	 */
	public static final String OPT_CSV_OUTPUT = "--csv-output";

	/**
	 * {@code --ratings-csv} option flag.
	 */
	public static final String OPT_RATINGS_CSV = "--ratings-csv";

	/**
	 * Short {@code --csv-output} flag alias.
	 */
	public static final String OPT_CSV_OUTPUT_SHORT = "-c";

	/**
	 * {@code --protocol-path} option flag.
	 */
	public static final String OPT_PROTOCOL_PATH = "--protocol-path";

	/**
	 * Short {@code --protocol-path} flag alias.
	 */
	public static final String OPT_PROTOCOL_PATH_SHORT = "-P";

	/**
	 * {@code --engine-instances} option flag.
	 */
	public static final String OPT_ENGINE_INSTANCES = "--engine-instances";

	/**
	 * Short {@code --engine-instances} flag alias.
	 */
	public static final String OPT_ENGINE_INSTANCES_SHORT = "-e";

	/**
	 * {@code --max-nodes} option flag.
	 */
	public static final String OPT_MAX_NODES = "--max-nodes";

	/**
	 * {@code --nodes} option flag (alias for {@link #OPT_MAX_NODES}).
	 */
	public static final String OPT_NODES = "--nodes";

	/**
	 * {@code --max-duration} option flag.
	 */
	public static final String OPT_MAX_DURATION = "--max-duration";

	/**
	 * {@code --multipv} option flag.
	 */
	public static final String OPT_MULTIPV = "--multipv";

	/**
	 * {@code --pv-plies} option flag.
	 */
	public static final String OPT_PV_PLIES = "--pv-plies";

	/**
	 * {@code --tag-multipv} option flag.
	 */
	public static final String OPT_TAG_MULTIPV = "--tag-multipv";

	/**
	 * {@code --threads} option flag.
	 */
	public static final String OPT_THREADS = "--threads";

	/**
	 * {@code --hash} option flag.
	 */
	public static final String OPT_HASH = "--hash";

	/**
	 * {@code --wdl} option flag.
	 */
	public static final String OPT_WDL = "--wdl";

	/**
	 * {@code --no-wdl} option flag.
	 */
	public static final String OPT_NO_WDL = "--no-wdl";

	/**
	 * {@code --lc0} option flag.
	 */
	public static final String OPT_LC0 = "--lc0";

	/**
	 * {@code --classical} option flag.
	 */
	public static final String OPT_CLASSICAL = "--classical";

	/**
	 * {@code --terminal-aware} option flag.
	 */
	public static final String OPT_TERMINAL_AWARE = "--terminal-aware";

	/**
	 * {@code --terminal} option flag (alias for {@link #OPT_TERMINAL_AWARE}).
	 */
	public static final String OPT_TERMINAL = "--terminal";

	/**
	 * {@code --weights} option flag.
	 */
	public static final String OPT_WEIGHTS = "--weights";

	/**
	 * {@code --depth} option flag.
	 */
	public static final String OPT_DEPTH = "--depth";

	/**
	 * Short {@code --depth} flag alias.
	 */
	public static final String OPT_DEPTH_SHORT = "-d";

	/**
	 * {@code --divide} option flag.
	 */
	public static final String OPT_DIVIDE = "--divide";

	/**
	 * {@code --per-move} option flag (alias for {@link #OPT_DIVIDE}).
	 */
	public static final String OPT_PER_MOVE = "--per-move";

	/**
	 * {@code --mainline} option flag.
	 */
	public static final String OPT_MAINLINE = "--mainline";

	/**
	 * {@code --pairs} option flag.
	 */
	public static final String OPT_PAIRS = "--pairs";

	/**
	 * {@code --san} option flag.
	 */
	public static final String OPT_SAN = "--san";

	/**
	 * {@code --both} option flag.
	 */
	public static final String OPT_BOTH = "--both";

	/**
	 * {@code --top} option flag.
	 */
	public static final String OPT_TOP = "--top";

	/**
	 * {@code --ablation} option flag.
	 */
	public static final String OPT_ABLATION = "--ablation";

	/**
	 * {@code --show-backend} option flag.
	 */
	public static final String OPT_SHOW_BACKEND = "--show-backend";

	/**
	 * {@code --backend} option flag (alias for {@link #OPT_SHOW_BACKEND}).
	 */
	public static final String OPT_BACKEND = "--backend";

	/**
	 * {@code --flip} option flag.
	 */
	public static final String OPT_FLIP = "--flip";

	/**
	 * {@code --black-down} option flag (alias for {@link #OPT_FLIP}).
	 */
	public static final String OPT_BLACK_DOWN = "--black-down";

	/**
	 * {@code --no-border} option flag.
	 */
	public static final String OPT_NO_BORDER = "--no-border";

	/**
	 * {@code --size} option flag.
	 */
	public static final String OPT_SIZE = "--size";

	/**
	 * {@code --width} option flag.
	 */
	public static final String OPT_WIDTH = "--width";

	/**
	 * {@code --height} option flag.
	 */
	public static final String OPT_HEIGHT = "--height";

	/**
	 * {@code --zoom} option flag.
	 */
	public static final String OPT_ZOOM = "--zoom";

	/**
	 * {@code --dark} option flag.
	 */
	public static final String OPT_DARK = "--dark";

	/**
	 * {@code --light} option flag.
	 */
	public static final String OPT_LIGHT = "--light";

	/**
	 * {@code --dark-mode} option flag (alias for {@link #OPT_DARK}).
	 */
	public static final String OPT_DARK_MODE = "--dark-mode";

	/**
	 * {@code --arrow} option flag.
	 */
	public static final String OPT_ARROW = "--arrow";

	/**
	 * {@code --arrows} option flag.
	 */
	public static final String OPT_ARROWS = "--arrows";

	/**
	 * {@code --special-arrows} option flag.
	 */
	public static final String OPT_SPECIAL_ARROWS = "--special-arrows";

	/**
	 * {@code --details-inside} option flag.
	 */
	public static final String OPT_DETAILS_INSIDE = "--details-inside";

	/**
	 * {@code --details-outside} option flag.
	 */
	public static final String OPT_DETAILS_OUTSIDE = "--details-outside";

	/**
	 * {@code --shadow} option flag.
	 */
	public static final String OPT_SHADOW = "--shadow";

	/**
	 * {@code --drop-shadow} option flag (alias for {@link #OPT_SHADOW}).
	 */
	public static final String OPT_DROP_SHADOW = "--drop-shadow";

	/**
	 * {@code --circle} option flag.
	 */
	public static final String OPT_CIRCLE = "--circle";

	/**
	 * {@code --circles} option flag.
	 */
	public static final String OPT_CIRCLES = "--circles";

	/**
	 * {@code --legal} option flag.
	 */
	public static final String OPT_LEGAL = "--legal";

	/**
	 * {@code --files} option flag.
	 */
	public static final String OPT_FILES = "--files";

	/**
	 * {@code --per-file} option flag.
	 */
	public static final String OPT_PER_FILE = "--per-file";

	/**
	 * {@code --fens-per-file} option flag.
	 */
	public static final String OPT_FENS_PER_FILE = "--fens-per-file";

	/**
	 * {@code --chess960-files} option flag.
	 */
	public static final String OPT_CHESS960_FILES = "--chess960-files";

	/**
	 * {@code --chess960} option flag.
	 */
	public static final String OPT_CHESS960 = "--chess960";

	/**
	 * Short {@code --chess960} flag alias.
	 */
	public static final String OPT_CHESS960_SHORT = "-9";

	/**
	 * {@code --batch} option flag.
	 */
	public static final String OPT_BATCH = "--batch";

	/**
	 * {@code --ascii} option flag.
	 */
	public static final String OPT_ASCII = "--ascii";

	/**
	 * {@code --random-count} option flag.
	 */
	public static final String OPT_RANDOM_COUNT = "--random-count";

	/**
	 * {@code --random-infinite} option flag.
	 */
	public static final String OPT_RANDOM_INFINITE = "--random-infinite";

	/**
	 * {@code --max-waves} option flag.
	 */
	public static final String OPT_MAX_WAVES = "--max-waves";

	/**
	 * {@code --max-frontier} option flag.
	 */
	public static final String OPT_MAX_FRONTIER = "--max-frontier";

	/**
	 * {@code --max-total} option flag.
	 */
	public static final String OPT_MAX_TOTAL = "--max-total";

	/**
	 * {@code --puzzle-quality} option flag.
	 */
	public static final String OPT_PUZZLE_QUALITY = "--puzzle-quality";

	/**
	 * {@code --puzzle-winning} option flag.
	 */
	public static final String OPT_PUZZLE_WINNING = "--puzzle-winning";

	/**
	 * {@code --puzzle-drawing} option flag.
	 */
	public static final String OPT_PUZZLE_DRAWING = "--puzzle-drawing";

	/**
	 * {@code --puzzle-accelerate} option flag.
	 */
	public static final String OPT_PUZZLE_ACCELERATE = "--puzzle-accelerate";

	/**
	 * Prefix used when printing invalid FEN diagnostics.
	 */
	public static final String ERR_INVALID_FEN = "Error: invalid FEN. ";

	/**
	 * Shared hint for commands that accept a FEN either via flag or positional input.
	 */
	public static final String MSG_FEN_REQUIRED_HINT =
			"use " + OPT_FEN + ", " + OPT_STARTPOS + ", " + OPT_RANDOMPOS + ", or positional";
}
