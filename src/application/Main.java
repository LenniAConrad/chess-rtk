package application;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import static application.cli.ConfigOps.*;
import static application.cli.Constants.*;
import static application.cli.EngineOps.*;
import static application.cli.EvalOps.*;
import static application.cli.Format.*;
import static application.cli.PathOps.*;
import static application.cli.PgnOps.*;
import static application.cli.RecordIO.*;
import static application.cli.Validation.*;

import application.cli.RecordIO.RecordConsumer;
import application.console.Bar;
import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.Setup;
import chess.debug.LogService;
import chess.debug.SessionCache;
import chess.debug.Printer;
import chess.eval.Evaluator;
import chess.eval.Result;
import chess.images.render.Render;
import chess.io.Converter;
import chess.struct.Pgn;
import chess.struct.Record;
import chess.io.Reader;
import chess.io.Writer;
import chess.tag.Tagging;
import chess.uci.Analysis;
import chess.uci.Engine;
import chess.uci.Evaluation;
import chess.uci.Filter;
import chess.uci.Filter.FilterDSL;
import chess.uci.Output;
import chess.uci.Protocol;
import utility.Argv;
import utility.Display;
import utility.Images;
import utility.Json;

/**
 * Used for providing the CLI entry point and dispatching subcommands.
 *
 * <p>
	 * Recognized subcommands are {@code record-to-plain}, {@code record-to-csv},
	 * {@code record-to-pgn}, {@code record-to-dataset}, {@code stack-to-dataset},
	 * {@code cuda-info}, {@code mine}, {@code gen-fens}, {@code print}, {@code display},
	 * {@code render}, {@code clean}, {@code config}, {@code stats},
	 * {@code stats-tags}, {@code tags}, {@code moves}, {@code analyze},
	 * {@code bestmove}, {@code perft}, {@code pgn-to-fens}, {@code eval},
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
			helpSummary();
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
			case CMD_RENDER -> runRenderImage(b);
			case CMD_CLEAN -> runClean(b);
			case CMD_CONFIG -> runConfig(b);
			case CMD_STATS -> runStats(b);
			case CMD_TAGS -> runTags(b);
			case CMD_MOVES -> runMoves(b);
			case CMD_ANALYZE -> runAnalyze(b);
			case CMD_BESTMOVE -> runBestMove(b);
			case CMD_PERFT -> runPerft(b);
			case CMD_PGN_TO_FENS -> runPgnToFens(b);
			case CMD_STATS_TAGS -> runStatsTags(b);
			case CMD_EVAL, CMD_EVALUATE -> runEval(b);
			case CMD_HELP, CMD_HELP_SHORT, CMD_HELP_LONG -> runHelp(b);
			default -> {
				System.err.println("Unknown command: " + sub);
				helpSummary();
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
		boolean exportAll = a.flag(OPT_SIDELINES, OPT_EXPORT_ALL, OPT_EXPORT_ALL_SHORT);
		String filterDsl = a.string(OPT_FILTER, OPT_FILTER_SHORT);
		Path in = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path out = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		boolean csv = a.flag(OPT_CSV);
		Path csvOut = a.path(OPT_CSV_OUTPUT, OPT_CSV_OUTPUT_SHORT);
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
		String filterDsl = a.string(OPT_FILTER, OPT_FILTER_SHORT);
		Path in = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path out = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
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
		Path in = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path out = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
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
		Path in = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path out = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		a.ensureConsumed();

		Converter.recordToPgn(in, out);
	}

	/**
	 * Convert a Stack-*.json puzzle dump (JSON array) into NPY tensors.
	 */
	private static void runStackToDataset(Argv a) {
		Path in = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path out = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
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
		final boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path outDir = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		final int files = a.integerOr(1_000, OPT_FILES);
		final int perFile = a.integerOr(100_000, OPT_PER_FILE, OPT_FENS_PER_FILE);
		final int chess960Files = a.integerOr(100, OPT_CHESS960_FILES, OPT_CHESS960);
		final int batch = a.integerOr(2_048, OPT_BATCH);
		final boolean ascii = a.flag(OPT_ASCII);

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

	/**
	 * Validates arguments for the {@code gen-fens} command and exits on violation.
	 *
	 * @param files         number of output files requested
	 * @param perFile       FENs per file
	 * @param batch         batch size for random generation
	 * @param chess960Files number of Chess960 shards to emit
	 */
	private static void validateGenFensArgs(int files, int perFile, int batch, int chess960Files) {
		requirePositive(CMD_GEN_FENS, OPT_FILES, files);
		requirePositive(CMD_GEN_FENS, OPT_PER_FILE, perFile);
		requirePositive(CMD_GEN_FENS, OPT_BATCH, batch);
		requireBetweenInclusive(CMD_GEN_FENS, OPT_CHESS960_FILES, chess960Files, 0, files);
	}

	/**
	 * Ensures the target directory exists or exits with a diagnostic.
	 *
	 * @param cmd     command label used in diagnostics
	 * @param dir     output directory to create
	 * @param verbose whether to print stack traces on failure
	 */
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
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		String fen = resolveFenArgument(a);

		if (fen == null || fen.isEmpty()) {
			System.err.println("print requires a FEN (" + MSG_FEN_REQUIRED_HINT + ")");
			System.exit(2);
			return;
		}

		try {
			Position pos = new Position(fen.trim());
			Printer.board(pos);
		} catch (IllegalArgumentException ex) {
			// Invalid FEN or position construction error
			System.err.println(ERR_INVALID_FEN + (ex.getMessage() == null ? "" : ex.getMessage()));
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
	 * @param specialArrows whether to render castling/en passant arrows
	 * @param details     whether to render coordinate labels inside the board
	 * @param detailsOutside whether to render coordinate labels outside the board
	 * @param shadow      whether to render a drop shadow behind the board
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
			boolean specialArrows,
			boolean details,
			boolean detailsOutside,
			boolean shadow,
			List<String> circles,
			List<String> legal) {
	}

	/**
	 * Parsed options for the {@code render} subcommand.
	 *
	 * @param verbose     whether to print stack traces on failure
	 * @param fen         FEN string (may be null until resolved)
	 * @param showBorder  whether to render the board frame
	 * @param whiteDown   whether White is rendered at the bottom
	 * @param showBackend whether to print evaluator backend info
	 * @param ablation    whether to overlay per-piece inverted ablation scores
	 * @param size        output size fallback (square, 0 means unset)
	 * @param width       explicit output width (0 means unset)
	 * @param height      explicit output height (0 means unset)
	 * @param arrows      UCI moves to render as arrows
	 * @param specialArrows whether to render castling/en passant arrows
	 * @param details     whether to render coordinate labels inside the board
	 * @param detailsOutside whether to render coordinate labels outside the board
	 * @param shadow      whether to render a drop shadow behind the board
	 * @param circles     squares to highlight with circles
	 * @param legal       squares whose legal moves should be highlighted
	 * @param output      output image path
	 * @param format      image format override (png/jpg/bmp)
	 */
	private record RenderImageOptions(
			boolean verbose,
			String fen,
			boolean showBorder,
			boolean whiteDown,
			boolean showBackend,
			boolean ablation,
			int size,
			int width,
			int height,
			List<String> arrows,
			boolean specialArrows,
			boolean details,
			boolean detailsOutside,
			boolean shadow,
			List<String> circles,
			List<String> legal,
			Path output,
			String format) {
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
	 * <li>{@code --special-arrows} — show castling/en passant arrows.</li>
	 * <li>{@code --details-inside} — show coordinate labels inside the board.</li>
	 * <li>{@code --details-outside} — show coordinate labels outside the board.</li>
	 * <li>{@code --shadow} — add a drop shadow behind the board.</li>
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
			System.err.println("display requires a FEN (" + MSG_FEN_REQUIRED_HINT + ")");
			System.exit(2);
			return;
		}

		try {
			Position pos = new Position(opts.fen().trim());
			Render render = createRender(pos, opts.whiteDown(), opts.showBorder(), opts.details(), opts.detailsOutside());
			applyDisplayOverlays(render, pos, opts.arrows(), opts.circles(), opts.legal(), opts.specialArrows());
			String backendLabel = applyDisplayEvaluatorOverlays(render, pos, opts.showBackend(), opts.ablation());

			int windowWidth = resolveWindowDimension(opts.width(), opts.size(), DEFAULT_DISPLAY_WINDOW_SIZE);
			int windowHeight = resolveWindowDimension(opts.height(), opts.size(), DEFAULT_DISPLAY_WINDOW_SIZE);
			BufferedImage image = render.render();
			if (opts.shadow()) {
				image = applyDropShadow(image);
			}
			Display display = new Display(image, windowWidth, windowHeight, opts.light());
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
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		String fen = a.string(OPT_FEN);
		boolean showBorder = !a.flag(OPT_NO_BORDER);
		boolean whiteDown = !a.flag(OPT_FLIP, OPT_BLACK_DOWN);
		boolean light = !a.flag(OPT_DARK, OPT_DARK_MODE);

		boolean showBackend = a.flag(OPT_SHOW_BACKEND, OPT_BACKEND);
		boolean ablation = a.flag(OPT_ABLATION);
		int size = a.integerOr(0, OPT_SIZE);
		int width = a.integerOr(0, OPT_WIDTH);
		int height = a.integerOr(0, OPT_HEIGHT);
		List<String> arrows = a.strings(OPT_ARROW, OPT_ARROWS);
		boolean specialArrows = a.flag(OPT_SPECIAL_ARROWS);
		boolean details = a.flag(OPT_DETAILS_INSIDE);
		boolean detailsOutside = a.flag(OPT_DETAILS_OUTSIDE);
		boolean shadow = a.flag(OPT_SHADOW, OPT_DROP_SHADOW);
		List<String> circles = a.strings(OPT_CIRCLE, OPT_CIRCLES);
		List<String> legal = a.strings(OPT_LEGAL);
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
				specialArrows,
				details,
				detailsOutside,
				shadow,
				circles,
				legal);
	}

	/**
	 * Used for handling the {@code render} subcommand.
	 *
	 * <p>
	 * Parses a FEN and renders a board image with optional overlays, then saves
	 * it to disk.
	 * </p>
	 *
	 * <p>
	 * Options:
	 * <ul>
	 * <li>{@code --fen "<FEN...>"} — FEN string; may also be provided
	 * positionally.</li>
	 * <li>{@code --output|-o <path>} — output image path (required).</li>
	 * <li>{@code --format <png|jpg|bmp>} — output format override.</li>
	 * <li>{@code --arrow <uci>} — add an arrow (repeatable, UCI move format).</li>
	 * <li>{@code --special-arrows} — show castling/en passant arrows.</li>
	 * <li>{@code --details-inside} — show coordinate labels inside the board.</li>
	 * <li>{@code --details-outside} — show coordinate labels outside the board.</li>
	 * <li>{@code --circle <sq>} — add a circle highlight (repeatable, e.g.
	 * e4).</li>
	 * <li>{@code --legal <sq>} — highlight legal moves from a square
	 * (repeatable).</li>
	 * <li>{@code --ablation} — overlay per-piece inverted ablation scores.</li>
	 * <li>{@code --show-backend} — print which evaluator was used.</li>
	 * <li>{@code --flip} or {@code --black-down} — render Black at the bottom.</li>
	 * <li>{@code --no-border} — hide the board frame.</li>
	 * <li>{@code --size <px>} — output size (square).</li>
	 * <li>{@code --width <px>} and {@code --height <px>} — output size
	 * override.</li>
	 * <li>{@code --verbose} or {@code -v} — also print stack traces on errors.</li>
	 * </ul>
	 */
	private static void runRenderImage(Argv a) {
		RenderImageOptions opts = parseRenderImageOptions(a);

		if (opts.fen() == null || opts.fen().isEmpty()) {
			System.err.println("render requires a FEN (" + MSG_FEN_REQUIRED_HINT + ")");
			System.exit(2);
			return;
		}
		if (opts.output() == null) {
			System.err.println("render requires --output|-o <path>");
			System.exit(2);
			return;
		}

		try {
			Position pos = new Position(opts.fen().trim());
			Render render = createRender(pos, opts.whiteDown(), opts.showBorder(), opts.details(), opts.detailsOutside());
			applyDisplayOverlays(render, pos, opts.arrows(), opts.circles(), opts.legal(), opts.specialArrows());
			applyDisplayEvaluatorOverlays(render, pos, opts.showBackend(), opts.ablation());

			BufferedImage image = render.render();
			int outWidth = resolveWindowDimension(opts.width(), opts.size(), image.getWidth());
			int outHeight = resolveWindowDimension(opts.height(), opts.size(), image.getHeight());
			if (outWidth <= 0 || outHeight <= 0) {
				throw new IllegalArgumentException("render size must be positive");
			}
			if (outWidth != image.getWidth() || outHeight != image.getHeight()) {
				image = scaleImage(image, outWidth, outHeight);
			}

			String format = resolveImageFormat(opts.output(), opts.format());
			Path out = ensureImageExtension(opts.output(), format);
			ensureParentDir(out);

			if (opts.shadow()) {
				image = applyDropShadow(image);
			}

			BufferedImage toWrite = "jpg".equals(format) ? toOpaqueImage(image, Color.WHITE) : image;
			if (!ImageIO.write(toWrite, format, out.toFile())) {
				throw new IOException("No ImageIO writer for format: " + format);
			}
			System.out.println("Saved board image: " + out.toAbsolutePath());
		} catch (IllegalArgumentException ex) {
			System.err.println("Error: invalid render input. " + (ex.getMessage() == null ? "" : ex.getMessage()));
			LogService.error(ex, "render: invalid input", LOG_CTX_FEN_PREFIX + opts.fen());
			if (opts.verbose()) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
		} catch (IOException ex) {
			System.err.println("Error: failed to write image. " + (ex.getMessage() == null ? "" : ex.getMessage()));
			LogService.error(ex, "render: failed to write image", LOG_CTX_FEN_PREFIX + opts.fen());
			if (opts.verbose()) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
		} catch (Exception t) {
			System.err.println("Error: failed to render image. " + (t.getMessage() == null ? "" : t.getMessage()));
			LogService.error(t, "render: unexpected failure while rendering image", LOG_CTX_FEN_PREFIX + opts.fen());
			if (opts.verbose()) {
				t.printStackTrace(System.err);
			}
			System.exit(3);
		}
	}

	/**
	 * Parses CLI arguments for {@code render} and returns a normalized options
	 * bundle.
	 *
	 * <p>
	 * Accepts the FEN either via {@code --fen} or as remaining positionals (joined
	 * with spaces).
	 * </p>
	 *
	 * @param a argument parser positioned after the subcommand token
	 * @return parsed render options
	 */
	private static RenderImageOptions parseRenderImageOptions(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		String fen = a.string(OPT_FEN);
		boolean showBorder = !a.flag(OPT_NO_BORDER);
		boolean whiteDown = !a.flag(OPT_FLIP, OPT_BLACK_DOWN);

		boolean showBackend = a.flag(OPT_SHOW_BACKEND, OPT_BACKEND);
		boolean ablation = a.flag(OPT_ABLATION);
		int size = a.integerOr(0, OPT_SIZE);
		int width = a.integerOr(0, OPT_WIDTH);
		int height = a.integerOr(0, OPT_HEIGHT);
		List<String> arrows = a.strings(OPT_ARROW, OPT_ARROWS);
		boolean specialArrows = a.flag(OPT_SPECIAL_ARROWS);
		boolean details = a.flag(OPT_DETAILS_INSIDE);
		boolean detailsOutside = a.flag(OPT_DETAILS_OUTSIDE);
		boolean shadow = a.flag(OPT_SHADOW, OPT_DROP_SHADOW);
		List<String> circles = a.strings(OPT_CIRCLE, OPT_CIRCLES);
		List<String> legal = a.strings(OPT_LEGAL);
		Path output = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		String format = a.string(OPT_FORMAT);
		a.flag(OPT_DARK, OPT_DARK_MODE);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}

		a.ensureConsumed();
		return new RenderImageOptions(
				verbose,
				fen,
				showBorder,
				whiteDown,
				showBackend,
				ablation,
				size,
				width,
				height,
				arrows,
				specialArrows,
				details,
				detailsOutside,
				shadow,
				circles,
				legal,
				output,
				format);
	}

	/**
	 * Creates a {@link Render} instance configured for the given position and
	 * basic orientation settings.
	 *
	 * @param pos        position to render
	 * @param whiteDown  whether White is displayed at the bottom
	 * @param showBorder whether to show the board frame
	 * @param showCoordinates whether to draw coordinate labels inside the board
	 * @param showCoordinatesOutside whether to draw coordinate labels outside the board
	 * @return configured render instance
	 */
	private static Render createRender(Position pos, boolean whiteDown, boolean showBorder, boolean showCoordinates,
			boolean showCoordinatesOutside) {
		return new Render()
				.setPosition(pos)
				.setWhiteSideDown(whiteDown)
				.setShowBorder(showBorder)
				.setShowCoordinates(showCoordinates)
				.setShowCoordinatesOutside(showCoordinatesOutside);
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
	 * @param specialArrows whether to add castling/en passant arrows
	 */
	private static void applyDisplayOverlays(
			Render render,
			Position pos,
			List<String> arrows,
			List<String> circles,
			List<String> legalSquares,
			boolean specialArrows) {
		if (specialArrows) {
			render.addCastlingRights(pos).addEnPassant(pos);
		}
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
	 * Resolves the image format from CLI overrides or output file extension.
	 *
	 * @param output output path used for extension lookup
	 * @param formatOverride user-provided format override
	 * @return normalized image format string
	 */
	private static String resolveImageFormat(Path output, String formatOverride) {
		String format = normalizeImageFormat(formatOverride);
		if (format != null) {
			return format;
		}
		String ext = extensionOf(output);
		format = normalizeImageFormat(ext);
		if (format == null) {
			if (ext == null || ext.isBlank()) {
				throw new IllegalArgumentException("Missing image format (use --format png|jpg|bmp or add an extension)");
			}
			throw new IllegalArgumentException("Unsupported image format: " + ext);
		}
		return format;
	}

	/**
	 * Normalizes a format label into a supported ImageIO format name.
	 *
	 * @param format format string (e.g. png, jpg, jpeg, bmp)
	 * @return normalized format or {@code null} if unsupported
	 */
	private static String normalizeImageFormat(String format) {
		if (format == null || format.isBlank()) {
			return null;
		}
		String normalized = format.toLowerCase(Locale.ROOT);
		if ("jpeg".equals(normalized)) {
			normalized = "jpg";
		}
		return switch (normalized) {
			case "png", "jpg", "bmp" -> normalized;
			default -> null;
		};
	}

	/**
	 * Returns the file extension without the dot, if any.
	 *
	 * @param output output path to inspect
	 * @return extension string or {@code null} if missing
	 */
	private static String extensionOf(Path output) {
		if (output == null) {
			return null;
		}
		String name = output.getFileName().toString();
		int dot = name.lastIndexOf('.');
		if (dot <= 0 || dot == name.length() - 1) {
			return null;
		}
		return name.substring(dot + 1);
	}

	/**
	 * Ensures a file path includes the expected extension for the format.
	 *
	 * @param output base output path
	 * @param format target image format
	 * @return output path with extension appended when missing
	 */
	private static Path ensureImageExtension(Path output, String format) {
		if (output == null || format == null || format.isBlank()) {
			return output;
		}
		String ext = extensionOf(output);
		if (ext == null || ext.isBlank()) {
			return output.resolveSibling(output.getFileName().toString() + "." + format);
		}
		return output;
	}

	/**
	 * Scales an image to the requested dimensions using high-quality interpolation.
	 *
	 * @param source source image
	 * @param width target width in pixels
	 * @param height target height in pixels
	 * @return scaled image
	 */
	private static BufferedImage scaleImage(BufferedImage source, int width, int height) {
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scaled.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.drawImage(source, 0, 0, width, height, null);
		g.dispose();
		return scaled;
	}

	/**
	 * Converts an ARGB image into an opaque RGB image using a solid background.
	 *
	 * @param source source image
	 * @param background background fill color
	 * @return opaque image in RGB format
	 */
	private static BufferedImage toOpaqueImage(BufferedImage source, Color background) {
		if (source.getType() == BufferedImage.TYPE_INT_RGB) {
			return source;
		}
		BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.setPaint(background);
		g.fillRect(0, 0, out.getWidth(), out.getHeight());
		g.drawImage(source, 0, 0, null);
		g.dispose();
		return out;
	}

	/**
	 * Adds a blurred drop shadow to an image using a size-aware blur radius.
	 *
	 * @param image image to wrap with a shadow
	 * @return image with drop shadow
	 */
	private static BufferedImage applyDropShadow(BufferedImage image) {
		int width = image.getWidth();
		int blur = (int) (width * 0.05);
		int offset = (int) (width * 0.05);
		return Images.addDropShadow(image, blur, offset, offset, new Color(0, 0, 0, 255));
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
	 * Used for handling the {@code config} subcommand.
	 *
	 * <p>
	 * Supports {@code config show} and {@code config validate}.
	 * </p>
	 *
	 * @param a parsed argument vector after {@code config}
	 */
	private static void runConfig(Argv a) {
		List<String> rest = a.positionals();
		a.ensureConsumed();

		if (rest.isEmpty()) {
			System.err.println("config requires a subcommand: show | validate");
			System.exit(2);
			return;
		}
		if (rest.size() > 1) {
			System.err.println("config accepts only one subcommand: show | validate");
			System.exit(2);
			return;
		}

		String sub = rest.get(0);
		switch (sub) {
			case "show" -> runConfigShow();
			case "validate" -> runConfigValidate();
			default -> {
				System.err.println("Unknown config subcommand: " + sub);
				System.exit(2);
			}
		}
	}

	/**
	 * Prints resolved configuration values to standard output.
	 */
	private static void runConfigShow() {
		Config.reload();
		Path configPath = Config.getConfigPath();
		System.out.println("Config path: " + configPath.toAbsolutePath());
		System.out.println("Protocol path: " + Config.getProtocolPath());
		System.out.println("Output: " + Config.getOutput());
		System.out.println("Engine instances: " + Config.getEngineInstances());
		System.out.println("Max nodes: " + Config.getMaxNodes());
		System.out.println("Max duration (ms): " + Config.getMaxDuration());
		System.out.println("Puzzle quality: " + Config.getPuzzleQuality());
		System.out.println("Puzzle winning: " + Config.getPuzzleWinning());
		System.out.println("Puzzle drawing: " + Config.getPuzzleDrawing());
		System.out.println("Puzzle accelerate: " + Config.getPuzzleAccelerate());
	}

	/**
	 * Validates configuration and protocol files, then exits with status.
	 */
	private static void runConfigValidate() {
		Config.reload();

		List<String> warnings = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		Path configPath = Config.getConfigPath();
		validateConfigToml(configPath, warnings, errors);
		validateProtocolConfig(Config.getProtocolPath(), warnings, errors);
		printValidationResults(warnings, errors);
	}

	/**
	 * Used for handling the {@code stats} subcommand.
	 *
	 * @param a parsed argument vector for the subcommand.
	 */
	private static void runStats(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		int top = a.integerOr(10, OPT_TOP);
		requirePositive(CMD_STATS, OPT_TOP, top);
		a.ensureConsumed();

		StatsAccumulator stats = new StatsAccumulator();
		try {
			streamRecordFile(input, verbose, CMD_STATS, stats);
		} catch (Exception ex) {
			System.err.println("stats: failed to read input: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
			return;
		}

		stats.printSummary(input, top);
	}

	/**
	 * Used for handling the {@code tags} subcommand.
	 *
	 * @param a parsed argument vector for the subcommand.
	 */
	private static void runTags(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		String fen = a.string(OPT_FEN);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}
		a.ensureConsumed();

		if (input != null && fen != null) {
			System.err.println("tags: provide either " + OPT_INPUT + " or a single FEN, not both");
			System.exit(2);
			return;
		}

		if (input != null) {
			try {
				List<String> fens = Reader.readFenList(input);
				for (String entry : fens) {
					printTags(entry, verbose, true);
				}
			} catch (Exception ex) {
				System.err.println("tags: failed to read input: " + ex.getMessage());
				if (verbose) {
					ex.printStackTrace(System.err);
				}
				System.exit(2);
			}
			return;
		}

		if (fen == null || fen.isEmpty()) {
			System.err.println("tags requires a FEN (" + MSG_FEN_REQUIRED_HINT + ")");
			System.exit(2);
			return;
		}
		printTags(fen, verbose, false);
	}

	/**
	 * Used for handling the {@code moves} subcommand.
	 *
	 * @param a parsed argument vector for the subcommand.
	 */
	private static void runMoves(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean san = a.flag(OPT_SAN);
		boolean both = a.flag(OPT_BOTH);
		String fen = resolveFenArgument(a);

		if (fen == null || fen.isEmpty()) {
			System.err.println("moves requires a FEN (" + MSG_FEN_REQUIRED_HINT + ")");
			System.exit(2);
			return;
		}

		try {
			Position pos = new Position(fen.trim());
			printMoves(pos, san, both);
		} catch (IllegalArgumentException ex) {
			System.err.println(ERR_INVALID_FEN + (ex.getMessage() == null ? "" : ex.getMessage()));
			LogService.error(ex, "moves: invalid FEN", LOG_CTX_FEN_PREFIX + fen);
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
		} catch (Exception t) {
			System.err.println("Error: failed to list moves. " + (t.getMessage() == null ? "" : t.getMessage()));
			LogService.error(t, "moves: unexpected failure", LOG_CTX_FEN_PREFIX + fen);
			if (verbose) {
				t.printStackTrace(System.err);
			}
			System.exit(3);
		}
	}

	/**
	 * Resolves a FEN either from {@code --fen} or remaining positionals.
	 *
	 * @param a argument parser for the current subcommand
	 * @return FEN string or {@code null} when none provided
	 */
	private static String resolveFenArgument(Argv a) {
		String fen = a.string(OPT_FEN);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}
		a.ensureConsumed();
		return fen;
	}

	/**
	 * Prints all legal moves for a position using the selected format.
	 *
	 * @param pos  position to list moves for
	 * @param san  whether to print SAN instead of UCI
	 * @param both whether to print UCI and SAN side-by-side
	 */
	private static void printMoves(Position pos, boolean san, boolean both) {
		MoveList moves = pos.getMoves();
		for (int i = 0; i < moves.size(); i++) {
			printMoveLine(pos, moves.get(i), san, both);
		}
	}

	/**
	 * Prints a single move line in UCI/SAN as configured.
	 *
	 * @param pos  position used to compute SAN
	 * @param move move to print
	 * @param san  whether to print SAN instead of UCI
	 * @param both whether to print both UCI and SAN
	 */
	private static void printMoveLine(Position pos, short move, boolean san, boolean both) {
		String uci = Move.toString(move);
		if (both) {
			System.out.println(uci + "\t" + safeSan(pos, move));
			return;
		}
		if (san) {
			System.out.println(safeSan(pos, move));
			return;
		}
		System.out.println(uci);
	}

	/**
	 * Used for handling the {@code analyze} subcommand.
	 *
	 * @param a parsed argument vector for the subcommand.
	 */
	private static void runAnalyze(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		String fen = a.string(OPT_FEN);
		String protoPath = optional(a.string(OPT_PROTOCOL_PATH, OPT_PROTOCOL_PATH_SHORT), Config.getProtocolPath());
		long nodesCap = Math.max(1, optional(a.lng(OPT_MAX_NODES, OPT_NODES), Config.getMaxNodes()));
		long durMs = Math.max(1, optionalDurationMs(a.duration(OPT_MAX_DURATION), Config.getMaxDuration()));
		Integer multipv = a.integer(OPT_MULTIPV);
		Integer threads = a.integer(OPT_THREADS);
		Integer hash = a.integer(OPT_HASH);
		boolean wdl = a.flag(OPT_WDL);
		boolean noWdl = a.flag(OPT_NO_WDL);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}
		a.ensureConsumed();

		if (wdl && noWdl) {
			System.err.println(String.format("analyze: only one of %s or %s may be set", OPT_WDL, OPT_NO_WDL));
			System.exit(2);
			return;
		}

		List<String> fens = resolveFenInputs(CMD_ANALYZE, input, fen);
		Protocol protocol = loadProtocolOrExit(protoPath, verbose);
		Optional<Boolean> wdlFlag = resolveWdlFlag(wdl, noWdl);

		try (Engine engine = new Engine(protocol)) {
			configureEngine(CMD_ANALYZE, engine, threads, hash, multipv, wdlFlag);
			String engineLabel = protocol.getName() != null ? protocol.getName() : protocol.getPath();
			System.out.println("Engine: " + engineLabel);
			for (int i = 0; i < fens.size(); i++) {
				String entry = fens.get(i);
				Position pos = parsePositionOrNull(entry, CMD_ANALYZE, verbose);
				if (pos == null) {
					continue;
				}
				Analysis analysis = analysePositionOrExit(engine, pos, nodesCap, durMs, CMD_ANALYZE, verbose);
				if (analysis == null) {
					return;
				}

				if (i > 0) {
					System.out.println();
				}
				printAnalysisSummary(pos, analysis);
			}
		} catch (Exception ex) {
			System.err.println("analyze: failed to initialize engine: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
		}
	}

	/**
	 * Used for handling the {@code bestmove} subcommand.
	 *
	 * @param a parsed argument vector for the subcommand.
	 */
	private record BestMoveOptions(
			boolean verbose,
			boolean san,
			boolean both,
			Path input,
			String fen,
			String protoPath,
			long nodesCap,
			long durMs,
			Integer multipv,
			Integer threads,
			Integer hash,
			boolean wdl,
			boolean noWdl) {
	}

	/**
	 * Used for handling the {@code bestmove} subcommand.
	 *
	 * @param a parsed argument vector for the subcommand.
	 */
	private static void runBestMove(Argv a) {
		BestMoveOptions opts = parseBestMoveOptions(a);

		if (opts.wdl() && opts.noWdl()) {
			System.err.println(String.format("bestmove: only one of %s or %s may be set", OPT_WDL, OPT_NO_WDL));
			System.exit(2);
			return;
		}

		List<String> fens = resolveFenInputs(CMD_BESTMOVE, opts.input(), opts.fen());
		Protocol protocol = loadProtocolOrExit(opts.protoPath(), opts.verbose());
		Optional<Boolean> wdlFlag = resolveWdlFlag(opts.wdl(), opts.noWdl());

		try (Engine engine = new Engine(protocol)) {
			configureEngine(CMD_BESTMOVE, engine, opts.threads(), opts.hash(), opts.multipv(), wdlFlag);
			for (String entry : fens) {
				Position pos = parsePositionOrNull(entry, CMD_BESTMOVE, opts.verbose());
				if (pos == null) {
					continue;
				}
				Analysis analysis = analysePositionOrExit(engine, pos, opts.nodesCap(), opts.durMs(), CMD_BESTMOVE, opts.verbose());
				if (analysis == null) {
					return;
				}
				printBestMove(entry, pos, analysis, opts.input(), opts.san(), opts.both());
			}
		} catch (Exception ex) {
			System.err.println("bestmove: failed to initialize engine: " + ex.getMessage());
			if (opts.verbose()) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
		}
	}

	/**
	 * Parses arguments for {@code bestmove} into a normalized options record.
	 *
	 * @param a argument parser positioned after the subcommand token
	 * @return parsed bestmove options
	 */
	private static BestMoveOptions parseBestMoveOptions(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean san = a.flag(OPT_SAN);
		boolean both = a.flag(OPT_BOTH);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		String fen = a.string(OPT_FEN);
		String protoPath = optional(a.string(OPT_PROTOCOL_PATH, OPT_PROTOCOL_PATH_SHORT), Config.getProtocolPath());
		long nodesCap = Math.max(1, optional(a.lng(OPT_MAX_NODES, OPT_NODES), Config.getMaxNodes()));
		long durMs = Math.max(1, optionalDurationMs(a.duration(OPT_MAX_DURATION), Config.getMaxDuration()));
		Integer multipv = a.integer(OPT_MULTIPV);
		Integer threads = a.integer(OPT_THREADS);
		Integer hash = a.integer(OPT_HASH);
		boolean wdl = a.flag(OPT_WDL);
		boolean noWdl = a.flag(OPT_NO_WDL);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}
		a.ensureConsumed();
		return new BestMoveOptions(
				verbose,
				san,
				both,
				input,
				fen,
				protoPath,
				nodesCap,
				durMs,
				multipv,
				threads,
				hash,
				wdl,
				noWdl);
	}

	/**
	 * Prints the best move for a single analysis result.
	 *
	 * @param entry   original input line (FEN string)
	 * @param pos     parsed position
	 * @param analysis analysis result with best move
	 * @param input   input file path (when provided)
	 * @param san     whether to output SAN instead of UCI
	 * @param both    whether to print both UCI and SAN
	 */
	private static void printBestMove(
			String entry,
			Position pos,
			Analysis analysis,
			Path input,
			boolean san,
			boolean both) {
		short best = analysis.getBestMove();
		String uci = (best == Move.NO_MOVE) ? "0000" : Move.toString(best);
		String sanMove = (best == Move.NO_MOVE) ? "-" : safeSan(pos, best);
		if (input != null) {
			if (both) {
				System.out.println(entry + "\t" + uci + "\t" + sanMove);
			} else if (san) {
				System.out.println(entry + "\t" + sanMove);
			} else {
				System.out.println(entry + "\t" + uci);
			}
			return;
		}
		if (both) {
			System.out.println(uci + "\t" + sanMove);
		} else if (san) {
			System.out.println(sanMove);
		} else {
			System.out.println(uci);
		}
	}

	/**
	 * Used for handling the {@code perft} subcommand.
	 *
	 * @param a parsed argument vector for the subcommand.
	 */
	private static void runPerft(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean divide = a.flag(OPT_DIVIDE, OPT_PER_MOVE);
		Integer depth = a.integer(OPT_DEPTH, OPT_DEPTH_SHORT);
		String fen = a.string(OPT_FEN);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}
		a.ensureConsumed();

		if (depth == null) {
			System.err.println("perft requires " + OPT_DEPTH + " <n>");
			System.exit(2);
			return;
		}
		requireNonNegative(CMD_PERFT, OPT_DEPTH, depth);

		Position pos;
		try {
			pos = (fen == null || fen.isEmpty())
					? Setup.getStandardStartPosition()
					: new Position(fen.trim());
		} catch (IllegalArgumentException ex) {
			System.err.println(ERR_INVALID_FEN + (ex.getMessage() == null ? "" : ex.getMessage()));
			LogService.error(ex, "perft: invalid FEN", LOG_CTX_FEN_PREFIX + fen);
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
			return;
		}

		if (divide) {
			Printer.perft(pos, depth);
			return;
		}
		long nodes = pos.perft(depth);
		System.out.println(LOG_CTX_FEN_PREFIX + pos.toString());
		System.out.println("perft depth " + depth + ": " + nodes);
	}

	/**
	 * Used for handling the {@code pgn-to-fens} subcommand.
	 *
	 * @param a parsed argument vector for the subcommand.
	 */
	private static void runPgnToFens(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean mainline = a.flag(OPT_MAINLINE);
		boolean pairs = a.flag(OPT_PAIRS);
		Path input = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path output = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		a.ensureConsumed();

		if (output == null) {
			output = deriveOutputPath(input, ".txt");
		}

		List<chess.struct.Game> games = readPgnOrExit(input, verbose, CMD_PGN_TO_FENS);
		if (games == null) {
			return;
		}

		try (BufferedWriter writer = openWriterOrExit(output, verbose, CMD_PGN_TO_FENS)) {
			if (writer == null) {
				return;
			}
			long lines = writePgnFens(games, writer, mainline, pairs);
			System.out.printf("pgn-to-fens wrote %d lines to %s%n", lines, output.toAbsolutePath());
		} catch (IOException ex) {
			System.err.println("pgn-to-fens: failed to write output: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
		}
	}

	/**
	 * Used for handling the {@code stats-tags} subcommand.
	 *
	 * @param a parsed argument vector for the subcommand.
	 */
	private static void runStatsTags(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		int top = a.integerOr(20, OPT_TOP);
		requirePositive(CMD_STATS_TAGS, OPT_TOP, top);
		a.ensureConsumed();

		TagStatsAccumulator stats = new TagStatsAccumulator();
		try {
			streamRecordFile(input, verbose, CMD_STATS_TAGS, stats);
		} catch (Exception ex) {
			System.err.println("stats-tags: failed to read input: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
			return;
		}

		stats.printSummary(input, top);
	}

	/**
	 * Used for handling the {@code eval} subcommand.
	 *
	 * @param a parsed argument vector for the subcommand.
	 */
	private static void runEval(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		String fen = a.string(OPT_FEN);
		boolean lc0Only = a.flag(OPT_LC0);
		boolean classicalOnly = a.flag(OPT_CLASSICAL);
		boolean terminalAware = a.flag(OPT_TERMINAL_AWARE, OPT_TERMINAL);
		Path weights = a.path(OPT_WEIGHTS);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}
		a.ensureConsumed();

		if (lc0Only && classicalOnly) {
			System.err.println("eval: only one of " + OPT_LC0 + " or " + OPT_CLASSICAL + " may be set");
			System.exit(2);
			return;
		}

		List<String> fens = resolveFenInputs(CMD_EVAL, input, fen);
		boolean includeFen = input != null;

		if (classicalOnly) {
			if (!evalClassicalEntries(fens, terminalAware, includeFen, verbose, CMD_EVAL)) {
				System.exit(2);
			}
			return;
		}

		Path weightsPath = (weights == null) ? Evaluator.DEFAULT_WEIGHTS : weights;
		try (Evaluator evaluator = new Evaluator(weightsPath, terminalAware)) {
			if (!evalEvaluatorEntries(fens, evaluator, lc0Only, includeFen, verbose, CMD_EVAL)) {
				System.exit(2);
			}
		} catch (Exception ex) {
			System.err.println("eval: failed to initialize evaluator: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
		}
	}

	/**
	 * Resolves FEN inputs from either a file or a single CLI FEN string.
	 *
	 * @param cmd   command label used in diagnostics
	 * @param input optional input file path
	 * @param fen   optional single FEN string
	 * @return list of FEN strings to process
	 */
	private static List<String> resolveFenInputs(String cmd, Path input, String fen) {
		if (input != null && fen != null) {
			System.err.println(cmd + ": provide either " + OPT_INPUT + " or a single FEN, not both");
			System.exit(2);
			return List.of();
		}
		if (input != null) {
			try {
				List<String> fens = Reader.readFenList(input);
				if (fens.isEmpty()) {
					System.err.println(cmd + ": input file has no FENs");
					System.exit(2);
				}
				return fens;
			} catch (IOException ex) {
				System.err.println(cmd + ": failed to read input: " + ex.getMessage());
				System.exit(2);
				return List.of();
			}
		}
		if (fen == null || fen.isEmpty()) {
			System.err.println(cmd + " requires a FEN (" + MSG_FEN_REQUIRED_HINT + ")");
			System.exit(2);
			return List.of();
		}
		return List.of(fen);
	}

	/**
	 * Tags a position and prints JSON output, optionally prefixed by the FEN.
	 *
	 * @param fen        FEN string to tag
	 * @param verbose    whether to print stack traces on failure
	 * @param includeFen whether to print the FEN before the JSON tags
	 */
	private static void printTags(String fen, boolean verbose, boolean includeFen) {
		try {
			Position pos = new Position(fen.trim());
			List<String> tags = Tagging.tags(pos);
			String json = Json.stringArray(tags.toArray(new String[0]));
			if (includeFen) {
				System.out.println(fen + "\t" + json);
			} else {
				System.out.println(json);
			}
		} catch (IllegalArgumentException ex) {
			System.err.println("tags: invalid FEN skipped: " + fen);
			if (verbose) {
				ex.printStackTrace(System.err);
			}
		} catch (Exception ex) {
			System.err.println("tags: failed to tag position: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
		}
	}

	/**
	 * Loads and validates a protocol TOML file or exits on failure.
	 *
	 * @param protocolPath path to the protocol TOML file
	 * @param verbose      whether to print stack traces on failure
	 * @return parsed protocol instance
	 */
	private static Protocol loadProtocolOrExit(String protocolPath, boolean verbose) {
		Path path = Paths.get(protocolPath);
		try {
			String toml = Files.readString(path);
			Protocol protocol = new Protocol().fromToml(toml);
			String[] errors = protocol.collectValidationErrors();
			if (!protocol.assertValid()) {
				System.err.println("Protocol is missing required values:");
				for (String err : errors) {
					System.err.println("  - " + err);
				}
				System.exit(2);
			}
			if (errors.length > 0) {
				LogService.warn("Protocol has non-essential issues: " + errors.length);
			}
			return protocol;
		} catch (Exception e) {
			System.err.println("Failed to load protocol: " + e.getMessage());
			if (verbose) {
				e.printStackTrace(System.err);
			}
			System.exit(2);
			return null;
		}
	}

	/**
	 * Prints a human-readable analysis summary to standard output.
	 *
	 * @param pos      position that was analyzed
	 * @param analysis analysis output from the engine
	 */
	private static void printAnalysisSummary(Position pos, Analysis analysis) {
		System.out.println(String.format("FEN: %s", pos.toString()));
		if (analysis == null || analysis.isEmpty()) {
			System.out.println("analysis: (no output)");
			return;
		}
		int pivots = Math.max(1, analysis.getPivots());
		for (int pv = 1; pv <= pivots; pv++) {
			Output best = analysis.getBestOutput(pv);
			if (best == null) {
				continue;
			}
			String eval = formatEvaluation(best.getEvaluation());
			String wdl = formatChances(best.getChances());
			String bound = formatBound(best.getBound());
			System.out.printf(
					"pv%d eval=%s depth=%d seldepth=%d nodes=%d nps=%d time=%d wdl=%s bound=%s%n",
					pv,
					eval,
					best.getDepth(),
					best.getSelectiveDepth(),
					best.getNodes(),
					best.getNodesPerSecond(),
					best.getTime(),
					wdl,
					bound);
			short bestMove = analysis.getBestMove(pv);
			if (bestMove != Move.NO_MOVE) {
				System.out.printf("pv%d bestmove=%s san=%s%n", pv, Move.toString(bestMove), safeSan(pos, bestMove));
			}
			String pvLine = formatPvMoves(best.getMoves());
			if (!pvLine.isEmpty()) {
				System.out.printf("pv%d line=%s%n", pv, pvLine);
			}
		}
	}

	/**
	 * Accumulates aggregate statistics for the {@code stats} subcommand.
	 */
	private static final class StatsAccumulator implements RecordConsumer {
		/**
		 * Total records processed.
		 */
		private long total;
		/**
		 * Count of invalid records encountered.
		 */
		private long invalid;
		/**
		 * Records containing a position.
		 */
		private long withPosition;
		/**
		 * Records containing a parent position.
		 */
		private long withParent;
		/**
		 * Records that include tags.
		 */
		private long withTags;
		/**
		 * Records that include analysis output.
		 */
		private long withAnalysis;
		/**
		 * Records that include valid evaluations.
		 */
		private long withEval;
		/**
		 * Records that include mate evaluations.
		 */
		private long withMate;
		/**
		 * Sum of centipawn evaluations.
		 */
		private long sumCp;
		/**
		 * Count of centipawn evaluations.
		 */
		private long countCp;
		/**
		 * Minimum centipawn value observed.
		 */
		private int minCp = Integer.MAX_VALUE;
		/**
		 * Maximum centipawn value observed.
		 */
		private int maxCp = Integer.MIN_VALUE;
		/**
		 * Minimum mate value observed.
		 */
		private int minMate = Integer.MAX_VALUE;
		/**
		 * Maximum mate value observed.
		 */
		private int maxMate = Integer.MIN_VALUE;
		/**
		 * Sum of reported depths.
		 */
		private long sumDepth;
		/**
		 * Count of depth samples.
		 */
		private long countDepth;
		/**
		 * Sum of reported node counts.
		 */
		private long sumNodes;
		/**
		 * Count of node samples.
		 */
		private long countNodes;
		/**
		 * Histogram of engine names.
		 */
		private final Map<String, Long> engineCounts = new HashMap<>();
		/**
		 * Histogram of tag labels.
		 */
		private final Map<String, Long> tagCounts = new HashMap<>();

		/**
		 * Records an invalid record entry.
		 */
		@Override
		public void invalid() {
			invalid++;
		}

		/**
		 * Updates counters based on an incoming record.
		 *
		 * @param rec record to process
		 */
		@Override
		public void accept(Record rec) {
			total++;
			recordPositions(rec);
			recordEngine(rec);
			recordTags(rec);
			Analysis analysis = recordAnalysis(rec);
			Output best = (analysis == null) ? null : analysis.getBestOutput(1);
			if (best == null) {
				return;
			}
			recordEvaluation(best);
			recordDepthNodes(best);
		}

		/**
		 * Updates position/parent counters from the record.
		 *
		 * @param rec record to inspect
		 */
		private void recordPositions(Record rec) {
			if (rec.getPosition() != null) {
				withPosition++;
			}
			if (rec.getParent() != null) {
				withParent++;
			}
		}

		/**
		 * Updates engine name counts from the record.
		 *
		 * @param rec record to inspect
		 */
		private void recordEngine(Record rec) {
			String engine = rec.getEngine();
			if (engine != null && !engine.isEmpty()) {
				increment(engineCounts, engine);
			}
		}

		/**
		 * Updates tag counters from the record.
		 *
		 * @param rec record to inspect
		 */
		private void recordTags(Record rec) {
			String[] tags = rec.getTags();
			if (tags.length == 0) {
				return;
			}
			withTags++;
			for (String tag : tags) {
				if (tag != null && !tag.isEmpty()) {
					increment(tagCounts, tag);
				}
			}
		}

		/**
		 * Records analysis availability for the record.
		 *
		 * @param rec record to inspect
		 * @return analysis instance (may be null)
		 */
		private Analysis recordAnalysis(Record rec) {
			Analysis analysis = rec.getAnalysis();
			if (analysis != null && !analysis.isEmpty()) {
				withAnalysis++;
			}
			return analysis;
		}

		/**
		 * Updates evaluation counters from the best output.
		 *
		 * @param best best output to inspect
		 */
		private void recordEvaluation(Output best) {
			Evaluation eval = best.getEvaluation();
			if (eval == null || !eval.isValid()) {
				return;
			}
			withEval++;
			if (eval.isMate()) {
				withMate++;
				minMate = Math.min(minMate, eval.getValue());
				maxMate = Math.max(maxMate, eval.getValue());
				return;
			}
			countCp++;
			sumCp += eval.getValue();
			minCp = Math.min(minCp, eval.getValue());
			maxCp = Math.max(maxCp, eval.getValue());
		}

		/**
		 * Updates depth/node aggregate counters from the best output.
		 *
		 * @param best best output to inspect
		 */
		private void recordDepthNodes(Output best) {
			short depth = best.getDepth();
			if (depth > 0) {
				sumDepth += depth;
				countDepth++;
			}
			long nodes = best.getNodes();
			if (nodes > 0) {
				sumNodes += nodes;
				countNodes++;
			}
		}

		/**
		 * Prints a human-readable summary of the collected statistics.
		 *
		 * @param input input path used for the summary heading
		 * @param top   number of top entries to include for engines/tags
		 */
		void printSummary(Path input, int top) {
			System.out.println("Input: " + input.toAbsolutePath());
			System.out.println("Records: " + total + " (invalid " + invalid + ")");
			System.out.println("Positions: " + withPosition + " Parents: " + withParent);
			System.out.println("Records with tags: " + withTags);
			System.out.println("Analysis: " + withAnalysis + " Evals: " + withEval + " Mates: " + withMate);
			if (countCp > 0) {
				double avg = sumCp / (double) countCp;
				System.out.println("Eval cp: count=" + countCp
						+ " avg=" + String.format("%+.1f", avg)
						+ " min=" + formatSigned(minCp)
						+ " max=" + formatSigned(maxCp));
			} else {
				System.out.println("Eval cp: n/a");
			}
			if (withMate > 0) {
				System.out.println("Eval mate: min=#" + minMate + " max=#" + maxMate);
			} else {
				System.out.println("Eval mate: n/a");
			}
			if (countDepth > 0) {
				System.out.println("Depth avg: " + String.format("%.1f", sumDepth / (double) countDepth));
			} else {
				System.out.println("Depth avg: n/a");
			}
			if (countNodes > 0) {
				System.out.println("Nodes avg: " + String.format("%.1f", sumNodes / (double) countNodes));
			} else {
				System.out.println("Nodes avg: n/a");
			}
			System.out.println("Top engines: " + formatTopCounts(engineCounts, top));
			System.out.println("Top tags: " + formatTopCounts(tagCounts, top));
		}

		/**
		 * Increments a counter in a map by one.
		 *
		 * @param map counter map to update
		 * @param key key to increment
		 */
		private static void increment(Map<String, Long> map, String key) {
			map.put(key, map.getOrDefault(key, 0L) + 1L);
		}
	}

	/**
	 * Formats the most frequent entries in a count map for CLI output.
	 *
	 * @param counts count map to format
	 * @param limit  maximum number of entries to include
	 * @return formatted list or {@code -} if empty
	 */
	private static String formatTopCounts(Map<String, Long> counts, int limit) {
		if (counts.isEmpty() || limit <= 0) {
			return "-";
		}
		List<Map.Entry<String, Long>> entries = new ArrayList<>(counts.entrySet());
		entries.sort(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
				.thenComparing(Map.Entry::getKey));
		StringBuilder sb = new StringBuilder();
		int n = Math.min(limit, entries.size());
		for (int i = 0; i < n; i++) {
			if (i > 0) {
				sb.append(", ");
			}
			Map.Entry<String, Long> entry = entries.get(i);
			sb.append(entry.getKey()).append('=').append(entry.getValue());
		}
		return sb.toString();
	}

	/**
	 * Accumulates tag-focused statistics for the {@code stats-tags} subcommand.
	 */
	private static final class TagStatsAccumulator implements RecordConsumer {
		/**
		 * Total records processed.
		 */
		private long total;
		/**
		 * Count of invalid records encountered.
		 */
		private long invalid;
		/**
		 * Records that include tags.
		 */
		private long withTags;
		/**
		 * Total number of tags across tagged records.
		 */
		private long totalTags;
		/**
		 * Histogram of tag labels.
		 */
		private final Map<String, Long> tagCounts = new HashMap<>();

		/**
		 * Records an invalid record entry.
		 */
		@Override
		public void invalid() {
			invalid++;
		}

		/**
		 * Updates tag counters for a single record.
		 *
		 * @param rec record to process
		 */
		@Override
		public void accept(Record rec) {
			total++;
			String[] tags = rec.getTags();
			if (tags.length == 0) {
				return;
			}
			withTags++;
			for (String tag : tags) {
				if (tag == null || tag.isEmpty()) {
					continue;
				}
				totalTags++;
				tagCounts.put(tag, tagCounts.getOrDefault(tag, 0L) + 1L);
			}
		}

		/**
		 * Prints a human-readable summary of the collected tag statistics.
		 *
		 * @param input input path used for the summary heading
		 * @param top   number of top entries to include
		 */
		void printSummary(Path input, int top) {
			System.out.println("Input: " + input.toAbsolutePath());
			System.out.println("Records: " + total + " (invalid " + invalid + ")");
			System.out.println("Records with tags: " + withTags);
			System.out.println("Total tags: " + totalTags + " Unique tags: " + tagCounts.size());
			if (withTags > 0) {
				double avg = totalTags / (double) withTags;
				System.out.println("Tags per tagged record: " + String.format("%.2f", avg));
			}
			System.out.println("Top tags: " + formatTopCounts(tagCounts, top));
		}
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
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
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
	private static void runHelp(Argv a) {
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

	private static void helpSummary() {
		System.out.println(
				"""
						usage: ucicli <command> [options]

						commands:
						  record-to-plain  Convert .record JSON to .plain
						  record-to-csv    Convert .record JSON to CSV (no .plain output)
						  record-to-pgn    Convert .record JSON to PGN games
						  record-to-dataset Convert .record JSON to NPY tensors (features/labels)
						  stack-to-dataset Convert Stack-*.json puzzle dumps to NPY tensors
						  cuda-info        Print CUDA JNI backend status
						  gen-fens         Generate random legal FEN shards (standard + Chess960 mix)
						  mine             Mine chess puzzles (supports Chess960 / PGN / FEN list / random)
						  print            Pretty-print a FEN
						  display          Render a board image in a window
						  render           Save a board image to disk
						  config           Show/validate configuration
						  stats            Summarize .record or puzzle dumps
						  stats-tags       Summarize tag distributions
						  tags             Generate tags for a FEN (or list)
						  moves            List legal moves for a FEN
						  analyze          Analyze a FEN with the engine
						  bestmove         Print the best move for a FEN
						  perft            Run perft on a FEN (movegen validation)
						  pgn-to-fens      Convert PGN games to FEN lists
						  eval             Evaluate a FEN with LC0 or classical (alias: evaluate)
						  clean            Delete session cache/logs
						  help             Show command help

						tips:
						  ucicli help <command>       Show help for one command
						  ucicli help --full          Show full help output
						""");
	}

	private static void helpFull() {
		System.out.println(HELP_FULL_TEXT);
	}

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
		System.out.println("usage: ucicli " + command + " [options]\n\n" + section);
	}

	private static String helpMarkerFor(String command) {
		return switch (command) {
			case CMD_RECORD_TO_PLAIN -> "record-to-plain options:";
			case CMD_RECORD_TO_CSV -> "record-to-csv options:";
			case CMD_RECORD_TO_DATASET -> "record-to-dataset options:";
			case CMD_RECORD_TO_PGN -> "record-to-pgn options:";
			case CMD_STACK_TO_DATASET -> "stack-to-dataset options:";
			case CMD_CUDA_INFO -> "cuda-info options:";
			case CMD_GEN_FENS -> "gen-fens options:";
			case CMD_MINE -> "mine options (overrides & inputs):";
			case CMD_PRINT -> "print options:";
			case CMD_DISPLAY -> "display options:";
			case CMD_RENDER -> "render options:";
			case CMD_CONFIG -> "config subcommands:";
			case CMD_STATS -> "stats options:";
			case CMD_STATS_TAGS -> "stats-tags options:";
			case CMD_TAGS -> "tags options:";
			case CMD_MOVES -> "moves options:";
			case CMD_ANALYZE -> "analyze options:";
			case CMD_BESTMOVE -> "bestmove options:";
			case CMD_PERFT -> "perft options:";
			case CMD_PGN_TO_FENS -> "pgn-to-fens options:";
			case CMD_EVAL, CMD_EVALUATE -> "eval options:";
			case CMD_CLEAN -> "clean options:";
			case CMD_HELP, CMD_HELP_SHORT, CMD_HELP_LONG -> "help options:";
			default -> null;
		};
	}

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

	private static final String HELP_FULL_TEXT =
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
						  render    Save a board image to disk
						  config    Show/validate configuration
						  stats     Summarize .record or puzzle dumps
						  stats-tags Summarize tag distributions
						  tags      Generate tags for a FEN (or list)
						  moves     List legal moves for a FEN
						  analyze   Analyze a FEN with the engine
						  bestmove  Print the best move for a FEN
						  perft     Run perft on a FEN (movegen validation)
						  pgn-to-fens Convert PGN games to FEN lists
						  eval      Evaluate a FEN with LC0 or classical (alias: evaluate)
						  clean     Delete session cache/logs
						  help      Show command help

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
						  --arrow|--arrows <uci>       Add an arrow (repeatable)
						  --special-arrows            Show castling/en passant arrows (semi-transparent gray)
						  --details-inside            Show coordinate labels inside the board
						  --details-outside           Show coordinate labels outside the board
						  --shadow|--drop-shadow       Add a drop shadow behind the board
						  --circle|--circles <sq>      Add a circle (repeatable)
						  --legal <sq>                Highlight legal moves from a square (repeatable)
						  --ablation                  Overlay per-piece inverted ablation scores
						  --show-backend|--backend    Print and display which evaluator was used
						  --flip|--black-down         Render Black at the bottom
						  --no-border                 Hide the board frame
						  --size <px>                 Window size (square)
						  --width <px>                Window width override
						  --height <px>               Window height override
						  --dark|--dark-mode          Use dark display window styling
						  --verbose|-v                Print stack trace on failure

						render options:
						  --fen "<FEN...>"            FEN string (or supply as positional)
						  --output|-o <path>          Output image path (required)
						  --format <png|jpg|bmp>      Image format override (optional)
						  --arrow|--arrows <uci>       Add an arrow (repeatable)
						  --special-arrows            Show castling/en passant arrows (semi-transparent gray)
						  --details-inside            Show coordinate labels inside the board
						  --details-outside           Show coordinate labels outside the board
						  --shadow|--drop-shadow       Add a drop shadow behind the board
						  --circle|--circles <sq>      Add a circle (repeatable)
						  --legal <sq>                Highlight legal moves from a square (repeatable)
						  --ablation                  Overlay per-piece inverted ablation scores
						  --show-backend|--backend    Print which evaluator was used
						  --flip|--black-down         Render Black at the bottom
						  --no-border                 Hide the board frame
						  --size <px>                 Output size (square)
						  --width <px>                Output width override
						  --height <px>               Output height override
						  --verbose|-v                Print stack trace on failure

						config subcommands:
						  show                         Print resolved configuration values
						  validate                     Validate config + protocol files

						stats options:
						  --input|-i <path>           Input JSON array/JSONL file (required)
						  --top <n>                   Show top-N tags/engines (default 10)
						  --verbose|-v                Print stack trace on failure

						stats-tags options:
						  --input|-i <path>           Input JSON array/JSONL file (required)
						  --top <n>                   Show top-N tags (default 20)
						  --verbose|-v                Print stack trace on failure

						tags options:
						  --input|-i <path>           FEN list file (optional)
						  --fen "<FEN...>"            FEN string (or supply as positional)
						  --verbose|-v                Print stack trace on failure

						moves options:
						  --fen "<FEN...>"            FEN string (or supply as positional)
						  --san                        Output SAN instead of UCI
						  --both                       Output UCI + SAN per move
						  --verbose|-v                Print stack trace on failure

						analyze options:
						  --input|-i <path>           FEN list file (optional)
						  --fen "<FEN...>"            FEN string (or supply as positional)
						  --protocol-path|-P <toml>   Override Config.getProtocolPath()
						  --max-nodes|--nodes <n>     Override Config.getMaxNodes()
						  --max-duration <dur>        Override Config.getMaxDuration(), e.g. 60s, 2m, 60000
						  --multipv <n>               Set engine MultiPV
						  --threads <n>               Set engine thread count
						  --hash <mb>                 Set engine hash size
						  --wdl|--no-wdl              Enable/disable WDL output
						  --verbose|-v                Print stack trace on failure

						bestmove options:
						  --input|-i <path>           FEN list file (optional)
						  --fen "<FEN...>"            FEN string (or supply as positional)
						  --san                        Output SAN instead of UCI
						  --both                       Output UCI + SAN
						  --protocol-path|-P <toml>   Override Config.getProtocolPath()
						  --max-nodes|--nodes <n>     Override Config.getMaxNodes()
						  --max-duration <dur>        Override Config.getMaxDuration(), e.g. 60s, 2m, 60000
						  --multipv <n>               Set engine MultiPV
						  --threads <n>               Set engine thread count
						  --hash <mb>                 Set engine hash size
						  --wdl|--no-wdl              Enable/disable WDL output
						  --verbose|-v                Print stack trace on failure

						perft options:
						  --fen "<FEN...>"            FEN string (or supply as positional)
						  --depth|-d <n>              Perft depth (required)
						  --divide|--per-move         Print per-move breakdown
						  --verbose|-v                Print stack trace on failure

						pgn-to-fens options:
						  --input|-i <path>           Input PGN file (required)
						  --output|-o <path>          Output .txt (optional; default derived)
						  --pairs                     Write "parent child" FEN pairs per line
						  --mainline                  Only output the mainline (skip variations)
						  --verbose|-v                Print stack trace on failure

						eval options:
						  --input|-i <path>           FEN list file (optional)
						  --fen "<FEN...>"            FEN string (or supply as positional)
						  --lc0                       Force LC0 evaluation
						  --classical                 Force classical evaluation
						  --weights <path>            LC0 weights path (optional)
						  --terminal-aware|--terminal Use terminal-aware classical eval
						  --verbose|-v                Print stack trace on failure

						clean options:
						  --verbose|-v                Print stack trace on failure

						help options:
						  --full                      Show full help output
						  <command>                   Show help for one command
						""";

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
		final boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		final boolean chess960 = a.flag(OPT_CHESS960, OPT_CHESS960_SHORT);
		final Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		final String outRoot = optional(a.string(OPT_OUTPUT, OPT_OUTPUT_SHORT), Config.getOutput());
		final String proto = optional(a.string(OPT_PROTOCOL_PATH, OPT_PROTOCOL_PATH_SHORT), Config.getProtocolPath());
		final long engineInstances = optional(a.lng(OPT_ENGINE_INSTANCES, OPT_ENGINE_INSTANCES_SHORT), Config.getEngineInstances());
		final long nodesCap = Math.max(1, optional(a.lng(OPT_MAX_NODES), Config.getMaxNodes()));
		final long durMs = Math.max(
				1,
				optionalDurationMs(a.duration(OPT_MAX_DURATION), Config.getMaxDuration()));
		final Long randomSeedOverride = a.lng(OPT_RANDOM_COUNT);
		final boolean randomInfinite = a.flag(OPT_RANDOM_INFINITE);
		final Long maxWavesOverride = a.lng(OPT_MAX_WAVES);
		final Long maxFrontierOverride = a.lng(OPT_MAX_FRONTIER);
		final Long maxTotalOverride = a.lng(OPT_MAX_TOTAL);

		final String qGate = a.string(OPT_PUZZLE_QUALITY);
		final String wGate = a.string(OPT_PUZZLE_WINNING);
		final String dGate = a.string(OPT_PUZZLE_DRAWING);
		final String accelDsl = a.string(OPT_PUZZLE_ACCELERATE);

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
