package application.cli.command;

import static application.cli.Constants.CMD_GEN_FENS;
import static application.cli.Constants.OPT_ASCII;
import static application.cli.Constants.OPT_BATCH;
import static application.cli.Constants.OPT_CHESS960;
import static application.cli.Constants.OPT_CHESS960_FILES;
import static application.cli.Constants.OPT_FENS_PER_FILE;
import static application.cli.Constants.OPT_FILES;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_PER_FILE;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Validation.requireBetweenInclusive;
import static application.cli.Validation.requirePositive;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import application.console.Bar;
import chess.core.Position;
import chess.core.Setup;
import utility.Argv;

/**
 * Implements the {@code gen-fens} CLI command.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class GenFensCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private GenFensCommand() {
		// utility
	}

	/**
	 * Handles {@code gen-fens}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runGenerateFens(Argv a) {
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
	 * Writes a shard file of random FENs to disk.
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
}
