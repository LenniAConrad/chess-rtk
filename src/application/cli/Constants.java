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
	 * {@code record-to-plain} subcommand token.
	 */
	public static final String CMD_RECORD_TO_PLAIN = "record-to-plain";

	/**
	 * {@code record-to-csv} subcommand token.
	 */
	public static final String CMD_RECORD_TO_CSV = "record-to-csv";

	/**
	 * {@code record-to-dataset} subcommand token.
	 */
	public static final String CMD_RECORD_TO_DATASET = "record-to-dataset";

	/**
	 * {@code record-to-pgn} subcommand token.
	 */
	public static final String CMD_RECORD_TO_PGN = "record-to-pgn";

	/**
	 * {@code stack-to-dataset} subcommand token.
	 */
	public static final String CMD_STACK_TO_DATASET = "stack-to-dataset";

	/**
	 * {@code gpu-info} subcommand token.
	 */
	public static final String CMD_GPU_INFO = "gpu-info";

	/**
	 * Legacy alias for {@code gpu-info}.
	 */
	public static final String CMD_CUDA_INFO = "cuda-info";

	/**
	 * {@code gen-fens} subcommand token.
	 */
	public static final String CMD_GEN_FENS = "gen-fens";

	/**
	 * {@code mine} subcommand token.
	 */
	public static final String CMD_MINE = "mine";

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
	 * {@code clean} subcommand token.
	 */
	public static final String CMD_CLEAN = "clean";

	/**
	 * {@code help} subcommand token.
	 */
	public static final String CMD_HELP = "help";

	/**
	 * {@code config} subcommand token.
	 */
	public static final String CMD_CONFIG = "config";

	/**
	 * {@code stats} subcommand token.
	 */
	public static final String CMD_STATS = "stats";

	/**
	 * {@code tags} subcommand token.
	 */
	public static final String CMD_TAGS = "tags";

	/**
	 * {@code moves} subcommand token.
	 */
	public static final String CMD_MOVES = "moves";

	/**
	 * {@code analyze} subcommand token.
	 */
	public static final String CMD_ANALYZE = "analyze";

	/**
	 * {@code bestmove} subcommand token.
	 */
	public static final String CMD_BESTMOVE = "bestmove";

	/**
	 * {@code threats} subcommand token.
	 */
	public static final String CMD_THREATS = "threats";

	/**
	 * {@code perft} subcommand token.
	 */
	public static final String CMD_PERFT = "perft";

	/**
	 * {@code pgn-to-fens} subcommand token.
	 */
	public static final String CMD_PGN_TO_FENS = "pgn-to-fens";

	/**
	 * {@code stats-tags} subcommand token.
	 */
	public static final String CMD_STATS_TAGS = "stats-tags";

	/**
	 * {@code eval} subcommand token.
	 */
	public static final String CMD_EVAL = "eval";

	/**
	 * {@code evaluate} alias for the {@link #CMD_EVAL} subcommand.
	 */
	public static final String CMD_EVALUATE = "evaluate";

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
	 * {@code --fen} option flag.
	 */
	public static final String OPT_FEN = "--fen";

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
	 * {@code --verbose} option flag.
	 */
	public static final String OPT_VERBOSE = "--verbose";

	/**
	 * Short {@code --verbose} flag alias.
	 */
	public static final String OPT_VERBOSE_SHORT = "-v";

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
	 * {@code --csv} option flag.
	 */
	public static final String OPT_CSV = "--csv";

	/**
	 * {@code --csv-output} option flag.
	 */
	public static final String OPT_CSV_OUTPUT = "--csv-output";

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
	public static final String MSG_FEN_REQUIRED_HINT = "use " + OPT_FEN + " or positional";
}
