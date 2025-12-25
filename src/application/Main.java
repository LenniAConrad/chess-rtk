package application;

import java.io.IOException;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import application.console.Bar;
import chess.core.Field;
import chess.core.Move;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;
import chess.core.Setup;
import chess.debug.LogService;
import chess.debug.SessionCache;
import chess.debug.Printer;
import chess.eval.Backend;
import chess.eval.Evaluator;
import chess.eval.Result;
import chess.images.render.Render;
import chess.io.Converter;
import chess.struct.Pgn;
import chess.struct.Record;
import chess.io.Reader;
import chess.io.Writer;
import chess.uci.Filter;
import chess.uci.Filter.FilterDSL;
import utility.Argv;
import utility.Display;

/**
 * Used for providing the CLI entry point and dispatching subcommands.
 *
 * <p>
 * Recognized subcommands are {@code record-to-plain}, {@code record-to-csv},
 * {@code record-to-pgn}, {@code record-to-dataset}, {@code stack-to-dataset},
 * {@code cuda-info}, {@code mine}, {@code gen-fens}, {@code print}, {@code display},
 * {@code clean},
 * and {@code help}.
 * Prints usage information when no subcommand is supplied. For unknown
 * subcommands, prints an
 * error and exits with status {@code 2}.
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Main {

	/**
	 * Used for attaching FEN context to log entries.
	 */
	private static final String LOG_CTX_FEN_PREFIX = "FEN: ";

	/**
	 * Used for the default display window size when no overrides are supplied.
	 */
	private static final int DEFAULT_DISPLAY_WINDOW_SIZE = 640;

	/**
	 * Subcommand for converting {@code .record} JSON to {@code .plain}.
	 */
	private static final String CMD_RECORD_TO_PLAIN = "record-to-plain";

	/**
	 * Subcommand for converting {@code .record} JSON to CSV.
	 */
	private static final String CMD_RECORD_TO_CSV = "record-to-csv";

	/**
	 * Subcommand for converting {@code .record} JSON to dataset tensors.
	 */
	private static final String CMD_RECORD_TO_DATASET = "record-to-dataset";

	/**
	 * Subcommand for converting {@code .record} JSON to PGN.
	 */
	private static final String CMD_RECORD_TO_PGN = "record-to-pgn";

	/**
	 * Subcommand for converting Stack puzzle dumps to dataset tensors.
	 */
	private static final String CMD_STACK_TO_DATASET = "stack-to-dataset";

	/**
	 * Subcommand for printing CUDA JNI backend status.
	 */
	private static final String CMD_CUDA_INFO = "cuda-info";

	/**
	 * Subcommand for generating random legal FEN shards.
	 */
	private static final String CMD_GEN_FENS = "gen-fens";

	/**
	 * Subcommand for mining puzzles.
	 */
	private static final String CMD_MINE = "mine";

	/**
	 * Subcommand for pretty-printing a FEN.
	 */
	private static final String CMD_PRINT = "print";

	/**
	 * Subcommand for rendering a FEN in a window.
	 */
	private static final String CMD_DISPLAY = "display";

	/**
	 * Subcommand for deleting cached session artifacts.
	 */
	private static final String CMD_CLEAN = "clean";

	/**
	 * Subcommand for printing usage information.
	 */
	private static final String CMD_HELP = "help";

	/**
	 * Help command alias for printing usage information.
	 */
	private static final String CMD_HELP_SHORT = "-h";

	/**
	 * Help command alias for printing usage information.
	 */
	private static final String CMD_HELP_LONG = "--help";

	/**
	 * Used for the {@code --input | -i} option.
	 */
	private static final String OPT_INPUT = "--input";

	/**
	 * Used for the {@code --output | -o} option.
	 */
	private static final String OPT_OUTPUT = "--output";

	/**
	 * Used for the {@code --verbose | -v} option.
	 */
	private static final String OPT_VERBOSE = "--verbose";

	/**
	 * Used for parsing top-level CLI arguments and delegating to a subcommand
	 * handler.
	 *
	 * <p>
	 * Behavior:
	 * <ul>
	 * <li>Attempts to read the first positional argument as the subcommand.</li>
	 * <li>Delegates remaining positionals to the corresponding {@code run*}
	 * method.</li>
	 * <li>On unknown subcommands, prints help and exits with non-zero status.</li>
	 * </ul>
	 *
	 * @param argv raw command-line arguments; first positional must be a valid
	 *             subcommand.
	 */
	public static void main(String[] argv) {
		Argv a = new Argv(argv);

		List<String> head = a.positionals();

		a.ensureConsumed();

		if (head.isEmpty()) {
			help();
			return;
		}

		String sub = head.get(0);
		String[] tail = head.subList(1, head.size()).toArray(new String[0]);
		Argv b = new Argv(tail);

		switch (sub) {
			case CMD_RECORD_TO_PLAIN -> runConvert(b);
			case CMD_RECORD_TO_CSV -> runConvertCsv(b);
			case CMD_RECORD_TO_DATASET -> runRecordToDataset(b);
			case CMD_RECORD_TO_PGN -> runRecordToPgn(b);
			case CMD_STACK_TO_DATASET -> runStackToDataset(b);
			case CMD_CUDA_INFO -> runCudaInfo(b);
			case CMD_GEN_FENS -> runGenerateFens(b);
			case CMD_MINE -> runMine(b);
			case CMD_PRINT -> runPrint(b);
			case CMD_DISPLAY -> runDisplay(b);
			case CMD_CLEAN -> runClean(b);
			case CMD_HELP, CMD_HELP_SHORT, CMD_HELP_LONG -> help();
			default -> {
				System.err.println("Unknown command: " + sub);
				help();
				System.exit(2);
			}
		}
	}

	/**
	 * Used for handling the {@code record-to-plain} subcommand.
	 *
	 * <p>
	 * Converts a {@code .record} JSON file into a {@code .plain} file. Optionally
	 * filters
	 * records using a Filter-DSL string and/or includes sidelines in the output.
	 *
	 * <p>
	 * Side effects:
	 * <ul>
	 * <li>Reads from the provided input path.</li>
	 * <li>Writes a new file to the output path (derived when omitted).</li>
	 * </ul>
	 *
	 * @param a parsed argument vector for the subcommand. Recognized options:
	 *          <ul>
	 *          <li>{@code -a | --export-all | --sidelines} — include sidelines in
	 *          the output.</li>
	 *          <li>{@code -f | --filter <dsl>} — Filter-DSL used to select
	 *          records.</li>
	 *          <li>{@code -i | --input <path>} — required input {@code .record}
	 *          file.</li>
	 *          <li>{@code -o | --output <path>} — optional output {@code .plain}
	 *          file path.</li>
	 *          </ul>
	 */
	private static void runConvert(Argv a) {
		boolean exportAll = a.flag("--sidelines", "--export-all", "-a");
		String filterDsl = a.string("--filter", "-f");
		Path in = a.pathRequired(OPT_INPUT, "-i");
		Path out = a.path(OPT_OUTPUT, "-o");
		boolean csv = a.flag("--csv");
		Path csvOut = a.path("--csv-output", "-c");
		a.ensureConsumed();

		Filter filter = null;
		if (filterDsl != null && !filterDsl.isEmpty()) {
			filter = FilterDSL.fromString(filterDsl);
		}

		Converter.recordToPlain(exportAll, filter, in, out);
		if (csv || csvOut != null) {
			Converter.recordToCsv(filter, in, csvOut);
		}
	}

	/**
	 * Used for handling the {@code record-to-csv} subcommand.
	 *
	 * <p>
	 * Converts a {@code .record} JSON file into a CSV export without also writing
	 * a {@code .plain} file. This mirrors {@link #runConvert(Argv)} but only emits
	 * CSV output.
	 *
	 * @param a parsed argument vector for the subcommand. Recognized options:
	 *          <ul>
	 *          <li>{@code -f | --filter <dsl>} — Filter-DSL used to select
	 *          records.</li>
	 *          <li>{@code -i | --input <path>} — required input {@code .record}
	 *          file.</li>
	 *          <li>{@code -o | --output <path>} — optional output {@code .csv}
	 *          file path.</li>
	 *          </ul>
	 */
	private static void runConvertCsv(Argv a) {
		String filterDsl = a.string("--filter", "-f");
		Path in = a.pathRequired(OPT_INPUT, "-i");
		Path out = a.path(OPT_OUTPUT, "-o");
		a.ensureConsumed();

		Filter filter = null;
		if (filterDsl != null && !filterDsl.isEmpty()) {
			filter = FilterDSL.fromString(filterDsl);
		}

		Converter.recordToCsv(filter, in, out);
	}

	/**
	 * Convert a .record JSON array into Numpy .npy tensors (features/labels).
	 */
	private static void runRecordToDataset(Argv a) {
		Path in = a.pathRequired(OPT_INPUT, "-i");
		Path out = a.path(OPT_OUTPUT, "-o");
		a.ensureConsumed();

		if (out == null) {
			// Default: alongside input with stem + ".dataset"
			String stem = in.getFileName().toString();
			int dot = stem.lastIndexOf('.');
			if (dot > 0) {
				stem = stem.substring(0, dot);
			}
			out = in.resolveSibling(stem + ".dataset");
		}

		try {
			chess.io.RecordDatasetExporter.export(in, out);
			System.out.printf("Wrote %s.features.npy and %s.labels.npy%n", out, out);
		} catch (IOException e) {
			System.err.println("Failed to export dataset: " + e.getMessage());
			System.exit(2);
		}
	}

	/**
	 * Converts a {@code .record} JSON file into one or more PGN games by linking
	 * record {@code parent} and {@code position} fields.
	 *
	 * @param a parsed argument vector for the subcommand. Recognized options:
	 *          <ul>
	 *          <li>{@code -i | --input <path>} — required input {@code .record}
	 *          file.</li>
	 *          <li>{@code -o | --output <path>} — optional output {@code .pgn}
	 *          file path.</li>
	 *          </ul>
	 */
	private static void runRecordToPgn(Argv a) {
		Path in = a.pathRequired(OPT_INPUT, "-i");
		Path out = a.path(OPT_OUTPUT, "-o");
		a.ensureConsumed();

		Converter.recordToPgn(in, out);
	}

	/**
	 * Convert a Stack-*.json puzzle dump (JSON array) into NPY tensors.
	 */
	private static void runStackToDataset(Argv a) {
		Path in = a.pathRequired(OPT_INPUT, "-i");
		Path out = a.path(OPT_OUTPUT, "-o");
		a.ensureConsumed();

		if (out == null) {
			String stem = in.getFileName().toString();
			int dot = stem.lastIndexOf('.');
			if (dot > 0) {
				stem = stem.substring(0, dot);
			}
			out = in.resolveSibling(stem + ".dataset");
		}

		try {
			chess.io.RecordDatasetExporter.exportStack(in, out);
			System.out.printf("Wrote %s.features.npy and %s.labels.npy%n", out, out);
		} catch (IOException e) {
			System.err.println("Failed to export stack dataset: " + e.getMessage());
			System.exit(2);
		}
	}

	/**
	 * Prints whether the optional CUDA JNI backend is available and how many
	 * devices are visible.
	 *
	 * <p>
	 * This is a lightweight diagnostic command that does not require a GUI.
	 * If you built the native library under {@code native-cuda/}, run with:
	 * {@code -Djava.library.path=native-cuda/build}.
	 * </p>
	 */
	private static void runCudaInfo(Argv a) {
		a.ensureConsumed();

		boolean loaded = chess.lc0.cuda.Support.isLoaded();
		int count = chess.lc0.cuda.Support.deviceCount();
		boolean available = chess.lc0.cuda.Support.isAvailable();
		System.out.printf(
				"CUDA JNI backend: loaded=%s, available=%s (deviceCount=%d)%n",
				loaded ? "yes" : "no",
				available ? "yes" : "no",
				count);
	}

	/**
	 * Generate shard files containing random legal FENs (standard or Chess960).
	 *
	 * <p>
	 * Defaults: 1,000 files, 100,000 FENs each, first 100 files Chess960, output
	 * directory {@code all_positions_shards}, batch size 2,048.
	 *
	 * @param a parsed argument vector
	 */
	private static void runGenerateFens(Argv a) {
		final boolean verbose = a.flag(OPT_VERBOSE, "-v");
		Path outDir = a.path(OPT_OUTPUT, "-o");
		final int files = a.integerOr(1_000, "--files");
		final int perFile = a.integerOr(100_000, "--per-file", "--fens-per-file");
		final int chess960Files = a.integerOr(100, "--chess960-files", "--chess960");
		final int batch = a.integerOr(2_048, "--batch");
		final boolean ascii = a.flag("--ascii");

		a.ensureConsumed();

		validateGenFensArgs(files, perFile, batch, chess960Files);

		if (outDir == null) {
			outDir = Paths.get("all_positions_shards");
		}

		ensureDirectoryOrExit(CMD_GEN_FENS, outDir, verbose);

		final long total = (long) files * (long) perFile;
		final int barTotal = (total > Integer.MAX_VALUE) ? 0 : (int) total;
		final Bar bar = new Bar(barTotal, "fens", ascii);
		final int width = Math.max(4, String.valueOf(Math.max(files - 1, 0)).length());

		for (int i = 0; i < files; i++) {
			boolean useChess960 = i < chess960Files;
			Path target = outDir.resolve(fenShardFileName(i, width, useChess960));
			writeFenShardOrExit(target, perFile, useChess960, batch, bar, verbose);
		}

		bar.finish();
		System.out.printf(
				"gen-fens wrote %d files (%d Chess960) to %s%n",
				files,
				chess960Files,
				outDir.toAbsolutePath());
	}

	private static void validateGenFensArgs(int files, int perFile, int batch, int chess960Files) {
		requirePositive(CMD_GEN_FENS, "--files", files);
		requirePositive(CMD_GEN_FENS, "--per-file", perFile);
		requirePositive(CMD_GEN_FENS, "--batch", batch);
		requireBetweenInclusive(CMD_GEN_FENS, "--chess960-files", chess960Files, 0, files);
	}

	private static void requirePositive(String cmd, String opt, int value) {
		if (value <= 0) {
			System.err.printf("%s: %s must be positive%n", cmd, opt);
			System.exit(2);
		}
	}

	private static void requireBetweenInclusive(String cmd, String opt, int value, int min, int max) {
		if (value < min || value > max) {
			System.err.printf("%s: %s must be between %d and %d%n", cmd, opt, min, max);
			System.exit(2);
		}
	}

	private static void ensureDirectoryOrExit(String cmd, Path dir, boolean verbose) {
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			System.err.println(cmd + ": failed to create output directory: " + e.getMessage());
			if (verbose) {
				e.printStackTrace(System.err);
			}
			System.exit(2);
		}
	}

	/**
	 * Builds the output filename for a generated FEN shard.
	 *
	 * <p>
	 * Uses zero-padding for the shard index so filenames sort lexicographically
	 * (e.g. {@code fens-0001-std.txt}).
	 * </p>
	 *
	 * @param index    shard index (0-based)
	 * @param width    minimum number of digits for {@code index}
	 * @param chess960 whether the shard contains Chess960-start-derived positions
	 * @return filename (no directory component)
	 */
	private static String fenShardFileName(int index, int width, boolean chess960) {
		String suffix = chess960 ? "-960" : "-std";
		return "fens-" + zeroPad(index, width) + suffix + ".txt";
	}

	/**
	 * Pads a decimal integer with leading zeros to reach a minimum width.
	 *
	 * @param value non-negative integer value
	 * @param width minimum number of digits to return
	 * @return zero-padded decimal string (or the unmodified value when already wide enough)
	 */
	private static String zeroPad(int value, int width) {
		String raw = Integer.toString(value);
		if (raw.length() >= width) {
			return raw;
		}
		StringBuilder sb = new StringBuilder(width);
		for (int i = raw.length(); i < width; i++) {
			sb.append('0');
		}
		sb.append(raw);
		return sb.toString();
	}

	/**
	 * Writes a single FEN shard file and terminates the process on failure.
	 *
	 * @param target    output path
	 * @param fenCount  number of FENs to write
	 * @param chess960  whether to seed from Chess960 starts
	 * @param batchSize how many random positions to generate per batch
	 * @param bar       progress bar
	 * @param verbose   whether to print stack traces on failure
	 */
	private static void writeFenShardOrExit(
			Path target,
			int fenCount,
			boolean chess960,
			int batchSize,
			Bar bar,
			boolean verbose) {
		try {
			writeFenShard(target, fenCount, chess960, batchSize, bar);
		} catch (Exception e) {
			System.err.println("gen-fens: failed to write " + target + ": " + e.getMessage());
			if (verbose) {
				e.printStackTrace(System.err);
			}
			System.exit(1);
		}
	}

	/**
	 * Used for writing a shard file of random FENs to disk.
	 *
	 * @param target    output path
	 * @param fenCount  number of FENs to write
	 * @param chess960  whether to seed from Chess960 starts
	 * @param batchSize how many random positions to generate per batch
	 * @param bar       progress bar (disabled when total is zero)
	 * @throws IOException when writing fails
	 */
	private static void writeFenShard(
			Path target,
			int fenCount,
			boolean chess960,
			int batchSize,
			Bar bar) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(target)) {
			int remaining = fenCount;
			while (remaining > 0) {
				int chunk = Math.min(batchSize, remaining);
				List<Position> positions = Setup.getRandomPositions(chunk, chess960);
				for (Position p : positions) {
					writer.write(p.toString());
					writer.newLine();
					bar.step();
				}
				remaining -= chunk;
			}
		}
	}

	/**
	 * Used for handling the {@code print} subcommand.
	 *
	 * <p>
	 * Parses a FEN supplied via {@code --fen} or as a single positional argument
	 * after the
	 * subcommand and pretty-prints the position. Exits with status {@code 2} when
	 * no FEN is provided.
	 *
	 * <p>
	 * Options:
	 * <ul>
	 * <li>{@code --fen "<FEN...>"} — FEN string; may also be provided
	 * positionally.</li>
	 * <li>{@code --verbose} or {@code -v} — also print stack traces to stderr on
	 * errors.</li>
	 * </ul>
	 */
	private static void runPrint(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, "-v");
		String fen = a.string("--fen");
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}

		a.ensureConsumed();

		if (fen == null || fen.isEmpty()) {
			System.err.println("print requires a FEN (use --fen or positional)");
			System.exit(2);
			return;
		}

		try {
			Position pos = new Position(fen.trim());
			Printer.board(pos);
		} catch (IllegalArgumentException ex) {
			// Invalid FEN or position construction error
			System.err.println("Error: invalid FEN. " + (ex.getMessage() == null ? "" : ex.getMessage()));
			LogService.error(ex, "print: invalid FEN", LOG_CTX_FEN_PREFIX + fen);
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
		} catch (Exception t) {
			System.err.println("Error: failed to print position. " + (t.getMessage() == null ? "" : t.getMessage()));
			LogService.error(t, "print: unexpected failure while printing position", LOG_CTX_FEN_PREFIX + fen);
			if (verbose) {
				t.printStackTrace(System.err);
			}
			System.exit(3);
		}
	}

	/**
	 * Parsed options for the {@code display} subcommand.
	 *
	 * @param verbose     whether to print stack traces on failure
	 * @param fen         FEN string (may be null until resolved)
	 * @param showBorder  whether to render the board frame
	 * @param whiteDown   whether White is rendered at the bottom
	 * @param light       whether to use light window styling
	 * @param showBackend whether to print and display the evaluator backend
	 * @param ablation    whether to overlay per-piece inverted ablation scores
	 * @param size        square window size fallback (0 means unset)
	 * @param width       explicit window width (0 means unset)
	 * @param height      explicit window height (0 means unset)
	 * @param arrows      UCI moves to render as arrows
	 * @param circles     squares to highlight with circles
	 * @param legal       squares whose legal moves should be highlighted
	 */
	private record DisplayOptions(
			boolean verbose,
			String fen,
			boolean showBorder,
			boolean whiteDown,
			boolean light,
			boolean showBackend,
			boolean ablation,
			int size,
			int width,
			int height,
			List<String> arrows,
			List<String> circles,
			List<String> legal) {
	}

	/**
	 * Used for handling the {@code display} subcommand.
	 *
	 * <p>
	 * Parses a FEN and renders a board image with optional overlays, then opens
	 * a {@link Display} window to show it.
	 * </p>
	 *
	 * <p>
	 * Options:
	 * <ul>
	 * <li>{@code --fen "<FEN...>"} — FEN string; may also be provided
	 * positionally.</li>
	 * <li>{@code --arrow <uci>} — add an arrow (repeatable, UCI move format).</li>
	 * <li>{@code --circle <sq>} — add a circle highlight (repeatable, e.g.
	 * e4).</li>
	 * <li>{@code --legal <sq>} — highlight legal moves from a square
	 * (repeatable).</li>
	 * <li>{@code --ablation} — overlay per-piece inverted ablation scores.</li>
	 * <li>{@code --show-backend} — print and display which evaluator was used.</li>
	 * <li>{@code --flip} or {@code --black-down} — render Black at the bottom.</li>
	 * <li>{@code --no-border} — hide the board frame.</li>
	 * <li>{@code --size <px>} — window size (square).</li>
	 * <li>{@code --width <px>} and {@code --height <px>} — window size
	 * override.</li>
	 * <li>{@code --dark} or {@code --dark-mode} — use dark display window
	 * styling.</li>
	 * <li>{@code --verbose} or {@code -v} — also print stack traces on errors.</li>
	 * </ul>
	 */
	private static void runDisplay(Argv a) {
		DisplayOptions opts = parseDisplayOptions(a);

		if (opts.fen() == null || opts.fen().isEmpty()) {
			System.err.println("display requires a FEN (use --fen or positional)");
			System.exit(2);
			return;
		}

		try {
			Position pos = new Position(opts.fen().trim());
			Render render = createRender(pos, opts.whiteDown(), opts.showBorder());
			applyDisplayOverlays(render, pos, opts.arrows(), opts.circles(), opts.legal());
			String backendLabel = applyDisplayEvaluatorOverlays(render, pos, opts.showBackend(), opts.ablation());

			int windowWidth = resolveWindowDimension(opts.width(), opts.size(), DEFAULT_DISPLAY_WINDOW_SIZE);
			int windowHeight = resolveWindowDimension(opts.height(), opts.size(), DEFAULT_DISPLAY_WINDOW_SIZE);
			Display display = new Display(render.render(), windowWidth, windowHeight, opts.light());
			if (backendLabel != null) {
				display.setTitle("Backend: " + backendLabel);
			}
		} catch (IllegalArgumentException ex) {
			System.err.println("Error: invalid display input. " + (ex.getMessage() == null ? "" : ex.getMessage()));
			LogService.error(ex, "display: invalid input", LOG_CTX_FEN_PREFIX + opts.fen());
			if (opts.verbose()) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
		} catch (Exception t) {
			System.err.println("Error: failed to display position. " + (t.getMessage() == null ? "" : t.getMessage()));
			LogService.error(t, "display: unexpected failure while rendering position",
					LOG_CTX_FEN_PREFIX + opts.fen());
			if (opts.verbose()) {
				t.printStackTrace(System.err);
			}
			System.exit(3);
		}
	}

	/**
	 * Parses CLI arguments for {@code display} and returns a normalized options
	 * bundle.
	 *
	 * <p>
	 * Accepts the FEN either via {@code --fen} or as remaining positionals (joined
	 * with spaces).
	 * </p>
	 *
	 * @param a argument parser positioned after the subcommand token
	 * @return parsed display options
	 */
	private static DisplayOptions parseDisplayOptions(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, "-v");
		String fen = a.string("--fen");
		boolean showBorder = !a.flag("--no-border");
		boolean whiteDown = !a.flag("--flip", "--black-down");
		boolean light = !a.flag("--dark", "--dark-mode");

		boolean showBackend = a.flag("--show-backend", "--backend");
		boolean ablation = a.flag("--ablation");
		int size = a.integerOr(0, "--size");
		int width = a.integerOr(0, "--width");
		int height = a.integerOr(0, "--height");
		List<String> arrows = a.strings("--arrow", "--arrows");
		List<String> circles = a.strings("--circle", "--circles");
		List<String> legal = a.strings("--legal");
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}

		a.ensureConsumed();
		return new DisplayOptions(
				verbose,
				fen,
				showBorder,
				whiteDown,
				light,
				showBackend,
				ablation,
				size,
				width,
				height,
				arrows,
				circles,
				legal);
	}

	/**
	 * Creates a {@link Render} instance configured for the given position and
	 * basic orientation settings.
	 *
	 * @param pos        position to render
	 * @param whiteDown  whether White is displayed at the bottom
	 * @param showBorder whether to show the board frame
	 * @return configured render instance
	 */
	private static Render createRender(Position pos, boolean whiteDown, boolean showBorder) {
		return new Render()
				.setPosition(pos)
				.setWhiteSideDown(whiteDown)
				.setShowBorder(showBorder);
	}

	/**
	 * Applies display overlays (arrows/circles/legal-move highlights) to a render
	 * instance.
	 *
	 * @param render       render instance to annotate
	 * @param pos          position used for legal-move overlays
	 * @param arrows       UCI moves to add as arrows
	 * @param circles      squares to highlight with circles
	 * @param legalSquares squares whose legal moves should be highlighted
	 */
	private static void applyDisplayOverlays(
			Render render,
			Position pos,
			List<String> arrows,
			List<String> circles,
			List<String> legalSquares) {
		for (String arrow : arrows) {
			short move = Move.parse(arrow);
			render.addArrow(move);
		}
		for (String circle : circles) {
			byte index = parseSquare(circle);
			render.addCircle(index);
		}
		for (String sq : legalSquares) {
			byte index = parseSquare(sq);
			render.addLegalMoves(pos, index);
		}
	}

		/**
		 * Optionally evaluates a position for backend selection and/or ablation scores,
		 * and applies resulting overlays to the renderer.
		 *
		 * @param render      render instance to annotate
		 * @param pos         position to evaluate
		 * @param showBackend whether to evaluate and print the backend label
		 * @param ablation    whether to compute and overlay ablation scores
		 * @return backend label when {@code showBackend} is enabled, otherwise {@code null}
		 */
		private static String applyDisplayEvaluatorOverlays(
				Render render,
				Position pos,
				boolean showBackend,
			boolean ablation) {
		if (!showBackend && !ablation) {
			return null;
		}

		String backendLabel = null;
		try (Evaluator evaluator = new Evaluator()) {
			if (showBackend) {
				Result result = evaluator.evaluate(pos);
				backendLabel = formatBackendLabel(result.backend());
				System.out.println("Display backend: " + backendLabel);
			}
			if (ablation) {
				applyAblationOverlay(render, pos, evaluator);
			}
		}
		return backendLabel;
	}

	/**
	 * Resolves a window dimension given explicit and square-size overrides.
	 *
	 * @param explicit explicit width/height (takes precedence when {@code > 0})
	 * @param size     square window size fallback (used when {@code > 0})
	 * @param fallback default value when neither override is set
	 * @return resolved window dimension in pixels
	 */
	private static int resolveWindowDimension(int explicit, int size, int fallback) {
		if (explicit > 0) {
			return explicit;
		}
		if (size > 0) {
			return size;
		}
		return fallback;
	}

	/**
	 * Validates and parses a square string into a board index.
	 *
	 * @param square algebraic square (e.g. "e4")
	 * @return board index 0..63
	 */
	private static byte parseSquare(String square) {
		if (square == null || "-".equals(square) || !Field.isField(square)) {
			throw new IllegalArgumentException("Invalid square: " + square);
		}
		return Field.toIndex(square);
	}

	/**
	 * Adds an ablation overlay to the renderer using inverted ablation scores.
	 *
	 * @param render render instance to annotate
	 * @param pos    position to evaluate
	 */
	private static void applyAblationOverlay(Render render, Position pos, Evaluator evaluator) {
		int[][] matrix = evaluator.ablation(pos);
		byte[] board = pos.getBoard();
		double[] scales = ablationMaterialScales(matrix, board);

		for (int index = 0; index < 64; index++) {
			byte piece = board[index];
			if (piece == Piece.EMPTY) {
				continue;
			}
			int file = Field.getX((byte) index);
			int rankFromBottom = Field.getY((byte) index);
			int delta = matrix[rankFromBottom][file];
			int type = Math.abs(piece);
			double scaled = delta * scales[type];
			int signed = (int) Math.round(Piece.isWhite(piece) ? scaled : -scaled);
			render.setSquareText((byte) index, formatSigned(signed));
		}
	}

	/**
	 * Computes per-piece-type scaling factors that normalize ablation magnitudes
	 * towards classical material values.
	 *
	 * @param matrix ablation scores
	 * @param board  board array
	 * @return scale factors indexed by piece type (1..6)
	 */
	private static double[] ablationMaterialScales(int[][] matrix, byte[] board) {
		int[] counts = new int[7];
		long[] sumAbs = new long[7];
		for (int index = 0; index < 64; index++) {
			byte piece = board[index];
			if (piece == Piece.EMPTY) {
				continue;
			}
			int file = Field.getX((byte) index);
			int rankFromBottom = Field.getY((byte) index);
			int raw = matrix[rankFromBottom][file];
			int type = Math.abs(piece);
			sumAbs[type] += Math.abs(raw);
			counts[type]++;
		}

		double[] scales = new double[7];
		for (int type = 1; type <= 6; type++) {
			if (counts[type] == 0) {
				scales[type] = 1.0;
				continue;
			}
			double avg = sumAbs[type] / (double) counts[type];
			int material = Math.abs(Piece.getValue((byte) type));
			if (material <= 0 || avg <= 0.0) {
				scales[type] = 1.0;
			} else {
				scales[type] = material / avg;
			}
		}
		return scales;
	}

	/**
	 * Formats a backend label for display/logging.
	 *
	 * @param backend evaluation backend
	 * @return human-friendly label
	 */
	private static String formatBackendLabel(Backend backend) {
		if (backend == Backend.LC0_CUDA) {
			return "LC0 (cuda)";
		}
		if (backend == Backend.LC0_CPU) {
			return "LC0 (cpu)";
		}
		return "classical";
	}

	/**
	 * Formats a signed integer with an explicit sign.
	 *
	 * @param value numeric score
	 * @return signed string (e.g. "+12", "-4", "+0")
	 */
	private static String formatSigned(int value) {
		return String.format("%+d", value);
	}

	/**
	 * Used for handling the {@code clean} subcommand.
	 *
	 * <p>
	 * Deletes cached session artifacts under the default session directory while
	 * preserving the directory itself.
	 *
	 * @param a parsed argument vector for the subcommand. Recognized options:
	 *          <ul>
	 *          <li>{@code --verbose} or {@code -v} — print stack traces on
	 *          failure.</li>
	 *          </ul>
	 */
	private static void runClean(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, "-v");
		a.ensureConsumed();

		try {
			SessionCache.clean();
			SessionCache.ensureDirectory();
			System.out.println("Session cache cleared: " + SessionCache.directory().toAbsolutePath());
		} catch (Exception ex) {
			System.err.println("Failed to clean session cache: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(1);
		}
	}

	/**
	 * Used for printing usage information to standard output.
	 *
	 * <p>
	 * Includes brief explanations for each subcommand and the most relevant flags.
	 * Intended for
	 * interactive use from the command line.
	 */
	private static void help() {
		System.out.println(
				"""
						usage: ucicli <command> [options]

						commands:
						  record-to-plain Convert .record JSON to .plain
						  record-to-csv  Convert .record JSON to CSV (no .plain output)
						  record-to-pgn  Convert .record JSON to PGN games
						  record-to-dataset Convert .record JSON to NPY tensors (features/labels)
						  stack-to-dataset Convert Stack-*.json puzzle dumps to NPY tensors
						  cuda-info Print CUDA JNI backend status
						  gen-fens  Generate random legal FEN shards (standard + Chess960 mix)
						  mine      Mine chess puzzles (supports Chess960 / PGN / FEN list / random)
						  print     Pretty-print a FEN
						  display   Render a board image in a window
						  clean     Delete session cache/logs

						record-to-plain options:
						  --input|-i <path>          Input .record file (required)
						  --output|-o <path>         Output .plain file (optional; default derived)
						  --csv                      Also emit a CSV export (default path derived)
						  --csv-output|-c <path>     CSV output path (enables CSV export)
						  --filter|-f <dsl>          Filter-DSL string for selecting records
						  --sidelines|--export-all|-a Include sidelines in output

						record-to-csv options:
						  --input|-i <path>          Input .record file (required)
						  --output|-o <path>         Output .csv file (optional; default derived)
						  --filter|-f <dsl>          Filter-DSL string for selecting records

						record-to-dataset options:
						  --input|-i <path>          Input .record file (required, JSON array)
						  --output|-o <path>         Output stem (writes <stem>.features.npy, <stem>.labels.npy)

						record-to-pgn options:
						  --input|-i <path>          Input .record file (required, JSON array)
						  --output|-o <path>         Output .pgn file (optional; default derived)

						stack-to-dataset options:
						  --input|-i <path>          Input Stack-*.json file (required, JSON array)
						  --output|-o <path>         Output stem (writes <stem>.features.npy, <stem>.labels.npy)

						cuda-info options:
						  (no options)

						gen-fens options:
						  --output|-o <dir>          Output directory (default all_positions_shards/)
						  --files <n>                Number of files to generate (default 1000)
						  --per-file <n>             FENs per file (default 100000)
						  --fens-per-file <n>        Alias for --per-file
						  --chess960-files <n>       Files to seed from Chess960 (default 100)
						  --chess960 <n>             Alias for --chess960-files
						  --batch <n>                Random positions per batch (default 2048)
						  --ascii                    Render ASCII progress bar
						  --verbose|-v               Print stack trace on failure

						mine options (overrides & inputs):
						  --chess960|-9               Enable Chess960 mining
						  --input|-i <path>           PGN or TXT with FENs; omit to use random
						  --output|-o <path>          Output path/dir for puzzles

						  --protocol-path|-P <toml>   Override Config.getProtocolPath()
						  --engine-instances|-e <n>   Override Config.getEngineInstances()
						  --max-nodes <n>             Override Config.getMaxNodes()
						  --max-duration <dur>        Override Config.getMaxDuration(), e.g. 60s, 2m, 60000

						  --random-count <n>          Random seeds to generate (default 100)
						  --random-infinite           Continuously add random seeds (ignores waves/total caps)
						  --max-waves <n>             Override maximum waves (default 100; ignored with --random-infinite)
						  --max-frontier <n>          Override frontier cap (default 5_000)
						  --max-total <n>             Override total processed cap (default 500_000; ignored with --random-infinite)

						  --puzzle-quality <dsl>      Override quality gate DSL
						  --puzzle-winning <dsl>      Override winning gate DSL
						  --puzzle-drawing <dsl>      Override drawing gate DSL
						  --puzzle-accelerate <dsl>   Override accelerate prefilter DSL
						  --verbose|-v                Print stack trace on failure

						print options:
						  --fen "<FEN...>"            FEN string (or supply as positional)
						  --verbose|-v                Print stack trace on failure (parsing errors)

						display options:
						  --fen "<FEN...>"            FEN string (or supply as positional)
						  --arrow <uci>               Add an arrow (repeatable)
						  --circle <sq>               Add a circle (repeatable)
						  --legal <sq>                Highlight legal moves from a square (repeatable)
						  --ablation                  Overlay per-piece inverted ablation scores
						  --show-backend              Print and display which evaluator was used
						  --flip|--black-down         Render Black at the bottom
						  --no-border                 Hide the board frame
						  --size <px>                 Window size (square)
						  --width <px>                Window width override
						  --height <px>               Window height override
						  --dark|--dark-mode          Use dark display window styling
						  --verbose|-v                Print stack trace on failure

						clean options:
						  --verbose|-v                Print stack trace on failure
						""");
	}

	/**
	 * Used for handling the {@code mine} subcommand.
	 *
	 * <p>
	 * Resolves runtime configuration and filters, loads or generates seed
	 * positions,
	 * runs batched engine analysis in bounded waves, expands verified puzzles, and
	 * appends
	 * JSONL outputs for puzzles and non-puzzles.
	 *
	 * @param a parsed argument vector for the subcommand
	 */
	private static void runMine(Argv a) {
		final boolean verbose = a.flag(OPT_VERBOSE, "-v");
		final boolean chess960 = a.flag("--chess960", "-9");
		final Path input = a.path(OPT_INPUT, "-i");
		final String outRoot = optional(a.string(OPT_OUTPUT, "-o"), Config.getOutput());
		final String proto = optional(a.string("--protocol-path", "-P"), Config.getProtocolPath());
		final long engineInstances = optional(a.lng("--engine-instances", "-e"), Config.getEngineInstances());
		final long nodesCap = Math.max(1, optional(a.lng("--max-nodes"), Config.getMaxNodes()));
		final long durMs = Math.max(
				1,
				optionalDurationMs(a.duration("--max-duration"), Config.getMaxDuration()));
		final Long randomSeedOverride = a.lng("--random-count");
		final boolean randomInfinite = a.flag("--random-infinite");
		final Long maxWavesOverride = a.lng("--max-waves");
		final Long maxFrontierOverride = a.lng("--max-frontier");
		final Long maxTotalOverride = a.lng("--max-total");

		final String qGate = a.string("--puzzle-quality");
		final String wGate = a.string("--puzzle-winning");
		final String dGate = a.string("--puzzle-drawing");
		final String accelDsl = a.string("--puzzle-accelerate");

		final Filter accel = filterOrDefault(accelDsl, Config::getPuzzleAccelerate);
		final Filter qF = filterOrDefault(qGate, Config::getPuzzleQuality);
		final Filter wF = filterOrDefault(wGate, Config::getPuzzleWinning);
		final Filter dF = filterOrDefault(dGate, Config::getPuzzleDrawing);
		final boolean anyOverride = (qGate != null) || (wGate != null) || (dGate != null);
		final Filter verify = anyOverride ? Config.buildPuzzleVerify(qF, wF, dF) : Config.getPuzzleVerify();

		a.ensureConsumed();

		final int randomSeeds = Math.toIntExact(Math.max(1, optional(randomSeedOverride, DEFAULT_RANDOM_SEEDS)));
		int maxWaves = Math.toIntExact(Math.max(1, optional(maxWavesOverride, DEFAULT_MAX_WAVES)));
		int maxFrontier = Math.toIntExact(Math.max(1, optional(maxFrontierOverride, DEFAULT_MAX_FRONTIER)));
		long maxTotal = Math.max(1, optional(maxTotalOverride, DEFAULT_MAX_TOTAL));

		if (randomInfinite) {
			maxWaves = Integer.MAX_VALUE;
			maxTotal = Long.MAX_VALUE;
		}

		OutputTargets outs = resolveOutputs(outRoot, chess960);
		List<Record> seeds;

		try {
			if (input != null) {
				seeds = loadRecordsFromInput(input);
			} else {
				seeds = wrapSeeds(Setup.getRandomPositionSeeds(randomSeeds, chess960));
			}
		} catch (Exception ex) {
			LogService.error(ex, "Failed to load seed positions (input=%s)", String.valueOf(input));
			System.out.println("Failed to load seed positions; see log for details.");
			if (verbose) {
				ex.printStackTrace(System.out);
			}
			System.exit(2);
			return;
		}

		final List<Record> frontier = seeds;
		final MiningConfig config = new MiningConfig(
				accel,
				verify,
				nodesCap,
				durMs,
				outs,
				randomInfinite,
				chess960,
				randomSeeds,
				maxFrontier,
				maxWaves,
				maxTotal);

		try (Pool pool = Pool.create(Math.toIntExact(Math.max(1, engineInstances)), proto)) {
			// Touch output files up front so incremental flushes can append safely.
			flushJsonLines(outs.puzzles, List.of());
			flushJsonLines(outs.nonpuzzles, List.of());

			mine(pool, frontier, config);
		} catch (Exception e) {
			LogService.error(e, "Failed during mining (pool/create/analyse/flush)");
			System.out.println("Mining failed; see log for details.");
			if (verbose) {
				e.printStackTrace(System.out);
			}
			System.exit(1);
		}
	}

	/**
	 * Used for wrapping positions into mining records (with null parents).
	 *
	 * @param seeds source positions
	 * @return records initialized with positions
	 */
	private static List<Record> wrapSeeds(List<Setup.PositionSeed> seeds) {
		final List<Record> out = new ArrayList<>(seeds.size());
		for (Setup.PositionSeed seed : seeds) {
			out.add(new Record()
					.withPosition(seed.position())
					.withParent(seed.parent()));
		}
		return out;
	}

	/**
	 * Used for performing bounded multi-wave mining and expansion.
	 *
	 * <p>
	 * Applies an accelerate pre-filter, verifies puzzles, expands best-move
	 * replies,
	 * and prevents cycles via canonical FEN de-duplication.
	 *
	 * @param pool        shared engine pool
	 * @param frontier    initial frontier records
	 * @param accel       accelerate pre-filter
	 * @param verify      puzzle verification filter
	 * @param nodesCap    max nodes per position
	 * @param durMs       max duration per position (ms)
	 * @param outs        output targets for incremental persistence
	 * @param infinite    whether to keep generating random seeds when frontier is
	 *                    empty
	 * @param chess960    whether to generate Chess960 random seeds (when infinite)
	 * @param randomSeeds number of random seeds to generate per refill
	 * @param maxFrontier cap on frontier size per wave
	 * @param maxWaves    maximum waves to execute (Integer.MAX_VALUE for unbounded)
	 * @param maxTotal    maximum records to process (Long.MAX_VALUE for unbounded)
	 */
	private static void mine(
			Pool pool,
			List<Record> frontier,
			MiningConfig config) throws IOException {
		final Set<String> seenFen = new HashSet<>(frontier.size() * 2);
		final Set<String> analyzedFen = new HashSet<>(frontier.size() * 2);

		int waves = 0;
		int processed = 0;

		while (waves < config.maxWaves() && processed < config.maxTotal()) {
			frontier = prepareFrontierForWave(frontier, config, seenFen, analyzedFen, processed, waves);
			if (frontier.isEmpty()) {
				break;
			}

			frontier = capFrontier(frontier, config.maxFrontier());
			analyzeWave(pool, frontier, config.accel(), config.nodesCap(), config.durMs());

			final WaveState state = processFrontier(
					frontier,
					config.verify(),
					seenFen,
					analyzedFen,
					processed,
					config.maxTotal());

			if (!state.wavePuzzles.isEmpty()) {
				flushJsonLines(config.outs().puzzles, state.wavePuzzles);
			}
			if (!state.waveNonPuzzles.isEmpty()) {
				flushJsonLines(config.outs().nonpuzzles, state.waveNonPuzzles);
			}

			frontier = state.next;
			processed = state.processed;
			waves++;
		}
	}

	/**
	 * Prepares the frontier for the next mining wave.
	 *
	 * <p>
	 * When {@link MiningConfig#infinite()} is enabled and the frontier becomes
	 * empty, this method refills it with new random seeds until either a
	 * non-empty, unique frontier is produced or mining limits are reached.
	 * </p>
	 *
	 * @param frontier    current frontier (possibly empty)
	 * @param config      mining configuration
	 * @param seenFen     global de-duplication set (mutated)
	 * @param analyzedFen already analyzed FENs (used to skip re-analysis)
	 * @param processed   processed count so far
	 * @param waves       waves completed so far
	 * @return deduplicated frontier for the next wave (may be empty)
	 */
	private static List<Record> prepareFrontierForWave(
			List<Record> frontier,
			MiningConfig config,
			Set<String> seenFen,
			Set<String> analyzedFen,
			int processed,
			int waves) {
		List<Record> prepared = frontier;
		while (prepared.isEmpty() && config.infinite() && waves < config.maxWaves() && processed < config.maxTotal()) {
			prepared = wrapSeeds(Setup.getRandomPositionSeeds(config.randomSeeds(), config.chess960()));
			prepared = deduplicateFrontier(prepared, seenFen, analyzedFen);
		}
		return deduplicateFrontier(prepared, seenFen, analyzedFen);
	}

	/**
	 * Used for holding per-wave results.
	 */
	private static final class WaveState {

		/**
		 * Next frontier to analyze in the following wave.
		 */
		final List<Record> next;

		/**
		 * Total processed count after this wave.
		 */
		final int processed;

		/**
		 * Verified puzzles encountered in this wave.
		 */
		final List<Record> wavePuzzles;

		/**
		 * Non-puzzles encountered in this wave.
		 */
		final List<Record> waveNonPuzzles;

		/**
		 * Creates a new per-wave state snapshot.
		 *
		 * @param next           next frontier
		 * @param processed      processed count after the wave
		 * @param wavePuzzles    puzzles found in the wave
		 * @param waveNonPuzzles non-puzzles found in the wave
		 */
		WaveState(List<Record> next, int processed, List<Record> wavePuzzles, List<Record> waveNonPuzzles) {
			this.next = next;
			this.processed = processed;
			this.wavePuzzles = wavePuzzles;
			this.waveNonPuzzles = waveNonPuzzles;
		}
	}

	/**
	 * Immutable configuration bundle for the mining loop.
	 *
	 * @param accel       accelerate pre-filter
	 * @param verify      verification filter for classifying puzzles
	 * @param nodesCap    maximum nodes per position
	 * @param durMs       maximum duration per position (ms)
	 * @param outs        output targets for incremental persistence
	 * @param infinite    whether to keep generating random seeds when frontier is
	 *                    empty
	 * @param chess960    whether to generate Chess960 random seeds when refilling
	 * @param randomSeeds number of random seeds to generate per refill
	 * @param maxFrontier cap on frontier size per wave
	 * @param maxWaves    maximum waves to execute
	 * @param maxTotal    maximum records to process
	 */
	private record MiningConfig(
			Filter accel,
			Filter verify,
			long nodesCap,
			long durMs,
			OutputTargets outs,
			boolean infinite,
			boolean chess960,
			int randomSeeds,
			int maxFrontier,
			int maxWaves,
			long maxTotal) {
	}

	/**
	 * Used for default limiting the number of waves executed.
	 */
	private static final int DEFAULT_MAX_WAVES = 100;

	/**
	 * Used for default capping the number of records per frontier.
	 */
	private static final int DEFAULT_MAX_FRONTIER = 5_000;

	/**
	 * Used for default capping the total number of processed records.
	 */
	private static final long DEFAULT_MAX_TOTAL = 500_000;

	/**
	 * Used for default random seed count when none are provided.
	 */
	private static final int DEFAULT_RANDOM_SEEDS = 100;

	/**
	 * Used for capping the frontier size to a fixed maximum.
	 *
	 * @param frontier current frontier
	 * @param limit    maximum allowed size
	 * @return possibly trimmed frontier
	 */
	private static List<Record> capFrontier(List<Record> frontier, int limit) {
		if (frontier.size() <= limit) {
			return frontier;
		}
		return new ArrayList<>(frontier.subList(0, limit));
	}

	/**
	 * Used for filtering out already-processed or duplicate positions from the
	 * frontier.
	 *
	 * @param frontier    current frontier
	 * @param seenFen     global de-duplication set (mutated to register queued
	 *                    positions)
	 * @param analyzedFen positions that have already been fully analyzed
	 * @return possibly trimmed frontier
	 */
	private static List<Record> deduplicateFrontier(
			List<Record> frontier,
			Set<String> seenFen,
			Set<String> analyzedFen) {
		if (frontier.isEmpty()) {
			return frontier;
		}

		final List<Record> unique = new ArrayList<>(frontier.size());
		final Set<String> waveSeen = new HashSet<>(frontier.size() * 2);

		for (Record rec : frontier) {
			final Position pos = rec.getPosition();
			if (pos != null) {
				final String fen = pos.toString();
				if (!analyzedFen.contains(fen) && waveSeen.add(fen)) {
					seenFen.add(fen); // Register for child de-duplication across waves.
					unique.add(rec);
				}
			}
		}

		return (unique.size() == frontier.size()) ? frontier : unique;
	}

	/**
	 * Used for analyzing a wave of records via the engine pool.
	 *
	 * @param pool     engine pool
	 * @param frontier current frontier
	 * @param accel    accelerate filter
	 * @param nodesCap nodes limit
	 * @param durMs    duration limit (ms)
	 */
	private static void analyzeWave(
			Pool pool,
			List<Record> frontier,
			Filter accel,
			long nodesCap,
			long durMs) {
		pool.analyseAll(frontier, accel, nodesCap, durMs);
	}

	/**
	 * Used for processing the analyzed frontier and building the next wave.
	 *
	 * @param frontier    current frontier
	 * @param verify      puzzle verification filter
	 * @param seenFen     FEN de-duplication set
	 * @param analyzedFen processed FEN set for skipping re-analysis
	 * @param puzzles     collected puzzles
	 * @param nonPuzzles  collected non-puzzles
	 * @param processed   processed count so far
	 * @param maxTotal    maximum records permitted
	 * @return next frontier and updated processed count
	 */
	private static WaveState processFrontier(
			List<Record> frontier,
			Filter verify,
			Set<String> seenFen,
			Set<String> analyzedFen,
			int processed,
			long maxTotal) {
		final List<Record> next = new ArrayList<>(frontier.size() * 2);
		final List<Record> wavePuzzles = new ArrayList<>();
		final List<Record> waveNonPuzzles = new ArrayList<>();

		for (Record r : frontier) {
			processed++;
			final Position pos = r.getPosition();
			if (pos != null) {
				analyzedFen.add(pos.toString());
			}
			if (verify.apply(r.getAnalysis())) {
				wavePuzzles.add(r);
				expandBestMoveChildren(r, seenFen, analyzedFen, next, processed, maxTotal);
			} else {
				waveNonPuzzles.add(r);
			}
			if (processed >= maxTotal) {
				break;
			}
		}

		return new WaveState(next, processed, wavePuzzles, waveNonPuzzles);
	}

	/**
	 * Used for expanding a record's best move and queuing all child replies.
	 *
	 * @param r           analyzed record
	 * @param seenFen     de-duplication set
	 * @param analyzedFen processed FEN set for skipping re-analysis
	 * @param next        accumulator for next frontier
	 * @param processed   processed count so far
	 * @param maxTotal    maximum records permitted
	 */
	private static void expandBestMoveChildren(
			Record r,
			Set<String> seenFen,
			Set<String> analyzedFen,
			List<Record> next,
			int processed,
			long maxTotal) {
		final short best = r.getAnalysis().getBestMove();
		final Position parent = r.getPosition().copyOf().play(best);

		for (Position child : parent.generateSubPositions()) {
			final String fen = child.toString(); // assumes FEN canonicalization
			if (!analyzedFen.contains(fen) && seenFen.add(fen)) {
				next.add(new Record().withPosition(child).withParent(parent));
				if (processed + next.size() >= maxTotal) {
					break;
				}
			}
		}
	}

	/**
	 * Used for returning {@code value} when non-null, otherwise the
	 * {@code def}ault.
	 *
	 * @param value candidate value
	 * @param def   default value
	 * @return chosen value
	 */
	private static String optional(String value, String def) {
		return (value == null || value.isEmpty()) ? def : value;
	}

	/**
	 * Used for returning {@code value} when non-null, otherwise the
	 * {@code def}ault.
	 *
	 * @param value candidate value
	 * @param def   default value
	 * @return chosen value
	 */
	private static long optional(Long value, long def) {
		return (value == null) ? def : value;
	}

	/**
	 * Used for converting the optional duration to milliseconds with a default.
	 *
	 * @param value candidate duration
	 * @param defMs default duration in milliseconds
	 * @return chosen duration in milliseconds
	 */
	private static long optionalDurationMs(Duration value, long defMs) {
		return (value == null) ? defMs : value.toMillis();
	}

	/**
	 * Used for grouping output target paths for puzzles and non-puzzles.
	 */
	private static final class OutputTargets {
		Path puzzles;
		Path nonpuzzles;

		/**
		 * Used for holding both puzzle and non-puzzle output targets.
		 *
		 * @param p path for puzzle JSONL output
		 * @param n path for non-puzzle JSONL output
		 */
		OutputTargets(Path p, Path n) {
			this.puzzles = p;
			this.nonpuzzles = n;
		}
	}

	/**
	 * Used for resolving output file paths from a root path or filename.
	 *
	 * <p>
	 * When {@code outputRoot} is file-like ({@code .json} or {@code .jsonl}), the
	 * method derives sibling {@code .puzzles.json} and {@code .nonpuzzles.json}
	 * files. Otherwise, generates timestamped filenames inside the provided
	 * directory, prefixed with the chess variant.
	 *
	 * @param outputRoot directory or file-like root specified on the CLI
	 * @param chess960   whether to tag outputs for Chess960
	 * @return resolved pair of output targets
	 */
	private static OutputTargets resolveOutputs(String outputRoot, boolean chess960) {
		boolean isFileLike = outputRoot.endsWith(".json") || outputRoot.endsWith(".jsonl");
		Path basePath = Paths.get(outputRoot);
		String baseStem;

		if (isFileLike) {
			String fn = basePath.getFileName().toString();
			int dot = fn.lastIndexOf('.');
			baseStem = (dot > 0) ? fn.substring(0, dot) : fn;
			Path dir = basePath.getParent() == null
					? Paths.get(".")
					: basePath.getParent();
			Path puzzles = dir.resolve(baseStem + ".puzzles.json");
			Path nonpuzzle = dir.resolve(baseStem + ".nonpuzzles.json");
			return new OutputTargets(puzzles, nonpuzzle);
		} else {
			String tag = chess960 ? "chess960" : "standard";
			String ts = String.valueOf(System.currentTimeMillis());
			Path dir = basePath;
			Path puzzles = dir.resolve(tag + "-" + ts + ".puzzles.json");
			Path nonpuzzle = dir.resolve(tag + "-" + ts + ".nonpuzzles.json");
			return new OutputTargets(puzzles, nonpuzzle);
		}
	}

	/**
	 * Used for loading seed records from a supported input file.
	 *
	 * @param input path to a {@code .txt} or {@code .pgn} file
	 * @return list of parsed records (position + optional parent)
	 * @throws java.io.IOException when the input is unsupported or unreadable
	 */
	private static List<Record> loadRecordsFromInput(Path input) throws java.io.IOException {
		String name = input.getFileName().toString().toLowerCase();

		if (name.endsWith(".txt")) {
			return Reader.readPositionRecords(input);
		}

		if (name.endsWith(".pgn")) {
			return loadRecordsFromPgn(input);
		}

		throw new IOException("Unsupported input file (expect .txt or .pgn): " + input);
	}

	/**
	 * Reads a PGN file and extracts all mainline positions (after each ply) for
	 * every game.
	 *
	 * @param input PGN file
	 * @return list of records parsed from PGN movetext (variations preserved)
	 * @throws IOException if reading fails
	 */
	private static List<Record> loadRecordsFromPgn(Path input) throws IOException {
		List<chess.struct.Game> games = Pgn.read(input);
		List<Record> positions = new ArrayList<>();
		for (chess.struct.Game g : games) {
			positions.addAll(extractRecordsWithVariations(g));
		}
		return positions;
	}

	/**
	 * Extracts {@link Record} instances for every reachable ply in a game's
	 * movetext, including all variations.
	 *
	 * <p>
	 * Each returned record contains both the parent position (before the SAN move)
	 * and the resulting child position. Illegal SAN moves terminate the current
	 * line but do not stop processing other queued variations.
	 * </p>
	 *
	 * @param game PGN game
	 * @return list of records (parent/child position pairs)
	 */
	private static List<Record> extractRecordsWithVariations(chess.struct.Game game) {
		List<Record> positions = new ArrayList<>();
		Position start = game.getStartPosition() != null
				? game.getStartPosition().copyOf()
				: new Position(chess.struct.Game.STANDARD_START_FEN);

		record Work(chess.struct.Game.Node node, Position pos) {
		}

		java.util.ArrayDeque<Work> stack = new java.util.ArrayDeque<>();
		if (game.getMainline() != null) {
			stack.push(new Work(game.getMainline(), start.copyOf()));
		}
		for (chess.struct.Game.Node rootVar : game.getRootVariations()) {
			stack.push(new Work(rootVar, start.copyOf()));
		}

		while (!stack.isEmpty()) {
			Work work = stack.pop();
			Position current = work.pos();
			chess.struct.Game.Node cur = work.node();
			while (cur != null) {
				Position parent = current.copyOf();
				Position child;
				try {
					short move = SAN.fromAlgebraic(parent, cur.getSan());
					child = parent.copyOf().play(move);
				} catch (IllegalArgumentException ex) {
					break; // stop this line on illegal SAN
				}
				positions.add(new Record().withParent(parent).withPosition(child.copyOf()));
				for (chess.struct.Game.Node variation : cur.getVariations()) {
					stack.push(new Work(variation, child.copyOf()));
				}
				current = child;
				cur = cur.getNext();
			}
		}
		return positions;
	}

	/**
	 * Used for writing records as JSON Lines to the target path (touching the file
	 * when empty).
	 *
	 * @param target  output path
	 * @param records puzzle or non-puzzle records to persist
	 * @throws IOException when writing fails
	 */
	private static void flushJsonLines(Path target, List<Record> records) throws IOException {
		if (records.isEmpty()) {
			ensureParentDir(target);
			// still touch file so downstream tooling can find it
			Files.createDirectories(target.getParent() == null ? Paths.get(".") : target.getParent());
			if (!Files.exists(target))
				Files.createFile(target);
			return;
		}
		// Convert records to JSON strings (assumes Record::toJson exists; otherwise
		// adapt here).
		List<String> jsons = new ArrayList<>(records.size());
		for (Record r : records) {
			jsons.add(r.toJson());
		}
		ensureParentDir(target);
		Writer.appendJsonObjects(target, jsons);
	}

	/**
	 * Used for ensuring the parent directory of a target path exists.
	 *
	 * @param p path whose parent should be created if missing
	 * @throws IOException when directory creation fails
	 */
	private static void ensureParentDir(Path p) throws IOException {
		Path parent = p.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
	}

	/**
	 * Parses a Filter-DSL string or returns a default Filter when the CLI value is
	 * null.
	 * 
	 * @param cliValue CLI-provided Filter-DSL string; may be null
	 * @param def      supplier of the default Filter to use when {@code cliValue}
	 *                 is absent
	 * @return the parsed Filter or the default value
	 */
	private static Filter filterOrDefault(String cliValue, Supplier<Filter> def) {
		if (cliValue == null)
			return def.get();
		try {
			return FilterDSL.fromString(cliValue);
		} catch (RuntimeException ex) {
			throw new IllegalArgumentException("Invalid filter expression: " + cliValue, ex);
		}
	}
}
