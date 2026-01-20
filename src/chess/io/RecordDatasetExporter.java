package chess.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;
import chess.struct.Record;
import chess.uci.Analysis;
import chess.uci.Evaluation;
import chess.uci.Output;
import utility.Json;

/**
 * Exporter that converts {@code .record} JSON arrays into Numpy-compatible
 * {@code .npy} tensors (float32) for training.
 *
 * Writes two files next to the requested output stem:
 *  - {@code <stem>.features.npy} shaped (N, 781)
 *  - {@code <stem>.labels.npy} shaped (N,)
 *
 * Streams directly to .npy (constant memory) by writing a placeholder header
 * and patching N in-place at the end.
 *
 * @author Lennart A. Conrad
 */
public final class RecordDatasetExporter {

	/**
	 * Feature vector length per position (781 floats).
	 * Combines piece planes, castling, en-passant, and side-to-move features.
	 */
	private static final int INPUTS = 64 * 12 + 4 + 8 + 1; // 781

	/**
	 * Regex for extracting score kind/value from Stack analysis lines.
	 * Matches both centipawn and mate evaluations.
	 */
	private static final Pattern STACK_SCORE_RE = Pattern.compile("score\\s+(cp|mate)\\s+(-?\\d+)");

	/**
	 * Regex for extracting depth from Stack analysis lines.
	 * Used to pick the deepest multipv=1 line.
	 */
	private static final Pattern STACK_DEPTH_RE = Pattern.compile("depth\\s+(\\d+)");

	/**
	 * Strategy interface for exporting a JSON object into feature/label rows.
	 * Allows reuse of streaming logic across different input formats.
	 */
	@FunctionalInterface
	private interface JsonObjectExporter {

		/**
		 * Exports a parsed JSON object into the feature/label writers.
		 * Implementations may skip rows by returning without writing.
		 *
		 * @param obj JSON object text
		 * @param featsBuf reusable feature buffer
		 * @param feat feature writer
		 * @param lab label writer
		 * @throws IOException if writing fails
		 */
		void export(String obj, float[] featsBuf, NpyFloat32Writer feat, NpyFloat32Writer lab) throws IOException;
	}

	/**
	 * Prevents instantiation of this utility class.
	 */
	private RecordDatasetExporter() {
	}

	/**
	 * Exports a {@code .record} JSON array into NPY feature/label tensors.
	 * Writes sibling {@code .features.npy} and {@code .labels.npy} files.
	 *
	 * @param recordFile input record JSON array file
	 * @param outStem output stem path
	 * @throws IOException if reading or writing fails
	 */
	public static void export(Path recordFile, Path outStem) throws IOException {
		if (recordFile == null || outStem == null) {
			throw new IllegalArgumentException("recordFile and outStem must be non-null");
		}
		exportInternal(recordFile, outStem, RecordDatasetExporter::exportRecordObject);
	}

	/**
	 * Export a Stack-*.json puzzle dump (JSON array) to NPY tensors.
	 *
	 * <p>
	 * Input objects are expected to contain:
	 * <ul>
	 * <li>{@code position}: FEN string</li>
	 * <li>{@code analysis}: array of engine output lines</li>
	 * </ul>
	 * </p>
	 *
	 * <p>
	 * Writes two files next to the requested output stem:
	 * <ul>
	 * <li>{@code <stem>.features.npy} shaped (N, 781)</li>
	 * <li>{@code <stem>.labels.npy} shaped (N,)</li>
	 * </ul>
	 * </p>
	 *
	 * @param jsonFile input Stack JSON array file.
	 * @param outStem  output stem path.
	 * @throws IOException if writing fails.
	 */
	public static void exportStack(Path jsonFile, Path outStem) throws IOException {
		if (jsonFile == null || outStem == null) {
			throw new IllegalArgumentException("jsonFile and outStem must be non-null");
		}
		exportInternal(jsonFile, outStem, RecordDatasetExporter::exportStackObject);
	}

	/**
	 * Streams a JSON array file and writes feature/label rows via the exporter.
	 *
	 * <p>
	 * The method opens both {@code .features.npy} and {@code .labels.npy} with
	 * placeholder headers, then iterates each top-level JSON object, letting the
	 * {@link JsonObjectExporter} implementation decide whether to emit a row.
	 * </p>
	 *
	 * <p>
	 * When the exporter throws, the partially written files are deleted to avoid
	 * leaving corrupt artifacts.
	 * </p>
	 *
	 * @param jsonFile input JSON array file
	 * @param outStem output stem path
	 * @param exporter object-to-row exporter implementation
	 * @throws IOException if reading or writing fails
	 */
	private static void exportInternal(Path jsonFile, Path outStem, JsonObjectExporter exporter) throws IOException {
		Path featPath = outStem.resolveSibling(outStem.getFileName().toString() + ".features.npy");
		Path labPath = outStem.resolveSibling(outStem.getFileName().toString() + ".labels.npy");

		// If we crash mid-way, delete partial outputs.
		boolean success = false;
		try (NpyFloat32Writer feat = NpyFloat32Writer.open2D(featPath, INPUTS);
				NpyFloat32Writer lab = NpyFloat32Writer.open1D(labPath)) {

			final float[] featsBuf = new float[INPUTS];

			Consumer<String> sink = obj -> {
				try {
					exporter.export(obj, featsBuf, feat, lab);
				} catch (IOException io) {
					throw new UncheckedIOException(io);
				}
			};

			Json.streamTopLevelObjects(jsonFile, sink);
			success = true;
		} catch (UncheckedIOException uio) {
			throw uio.getCause();
		} finally {
			if (!success) {
				Files.deleteIfExists(featPath);
				Files.deleteIfExists(labPath);
			}
		}
	}

	/**
	 * Exports a single record object into the feature/label writers.
	 * Skips records that are missing required position or evaluation data.
	 *
	 * @param json JSON object text
	 * @param featsBuf reusable feature buffer
	 * @param feat feature writer
	 * @param lab label writer
	 * @throws IOException if writing fails
	 */
	private static void exportRecordObject(String json, float[] featsBuf, NpyFloat32Writer feat, NpyFloat32Writer lab)
			throws IOException {
		Record rec = Record.fromJson(json);
		if (rec == null) return;

		Position pos = rec.getPosition();
		if (pos == null) return;

		Analysis a = rec.getAnalysis();
		if (a == null) return;

		Output best = a.getBestOutput();
		if (best == null) return;

		Evaluation ev = best.getEvaluation();
		if (ev == null || !ev.isValid()) return;

		float pawns = ev.isMate() ? pawnsFromMate(ev.getValue()) : pawnsFromCp(ev.getValue());

		encodeInto(pos, featsBuf);
		feat.writeRow(featsBuf);
		lab.writeScalar(pawns);
	}

	/**
	 * Exports a single stack dump object into the feature/label writers.
	 * Skips entries that do not parse to a valid position or evaluation.
	 *
	 * @param obj JSON object text
	 * @param featsBuf reusable feature buffer
	 * @param feat feature writer
	 * @param lab label writer
	 * @throws IOException if writing fails
	 */
	private static void exportStackObject(String obj, float[] featsBuf, NpyFloat32Writer feat, NpyFloat32Writer lab)
			throws IOException {
		String posFen = Json.parseStringField(obj, "position");
		if (posFen == null) return;

		String[] analysis = Json.parseStringArrayField(obj, "analysis");
		float pawns = selectBestStackEval(analysis);
		if (Float.isNaN(pawns)) return;

		Position pos;
		try {
			pos = new Position(posFen);
		} catch (IllegalArgumentException e) {
			return;
		}

		encodeInto(pos, featsBuf);
		feat.writeRow(featsBuf);
		lab.writeScalar(pawns);
	}

	/**
	 * Selects the best evaluation from an array of UCI engine output lines in the
	 * Stack puzzle dump format.
	 *
	 * <p>
	 * Only lines containing {@code multipv 1} are considered. If multiple matching
	 * lines exist, the one with the highest {@code depth} is selected. The score
	 * is converted into pawn units and clamped to [-20, 20]. If no valid score is
	 * found, returns {@link Float#NaN}.
	 * </p>
	 *
	 * @param analysis array of UCI output lines
	 * @return best evaluation in pawns, or {@link Float#NaN} if none
	 */
	private static float selectBestStackEval(String[] analysis) {
		if (analysis == null || analysis.length == 0) {
			return Float.NaN;
		}
		int bestDepth = -1;
		float best = Float.NaN;
		for (String line : analysis) {
			StackEval parsed = parseStackEvalLine(line);
			if (parsed == null) {
				continue;
			}
			if (parsed.depth >= bestDepth) {
				bestDepth = parsed.depth;
				best = parsed.pawns;
			}
		}
		return best;
	}

	/**
	 * Parses a single stack analysis line into depth and pawn value.
	 * Returns {@code null} when the line does not match expected patterns.
	 *
	 * @param line raw UCI output line
	 * @return parsed stack evaluation, or {@code null} if not applicable
	 */
	private static StackEval parseStackEvalLine(String line) {
		if (line == null || !line.contains("multipv 1")) {
			return null;
		}

		Matcher scoreMatcher = STACK_SCORE_RE.matcher(line);
		if (!scoreMatcher.find()) {
			return null;
		}

		String kind = scoreMatcher.group(1);
		int value;
		try {
			value = Integer.parseInt(scoreMatcher.group(2));
		} catch (NumberFormatException e) {
			return null;
		}

		int depth = 0;
		Matcher depthMatcher = STACK_DEPTH_RE.matcher(line);
		if (depthMatcher.find()) {
			try {
				depth = Integer.parseInt(depthMatcher.group(1));
			} catch (NumberFormatException e) {
				depth = 0;
			}
		}

		float pawns = "cp".equals(kind) ? pawnsFromCp(value) : pawnsFromMate(value);
		return new StackEval(depth, pawns);
	}

	/**
	 * Parsed stack evaluation containing depth and pawn value.
	 * Used to compare and select the best analysis line.
	 */
	private static final class StackEval {

		/**
		 * Search depth reported by the engine.
		 * Used to pick the deepest line.
		 */
		private final int depth;

		/**
		 * Evaluation value in pawns after conversion/clamping.
		 * Used as the training label.
		 */
		private final float pawns;

		/**
		 * Creates a parsed Stack evaluation summary.
		 *
		 * @param depth search depth
		 * @param pawns evaluation in pawns
		 */
		private StackEval(int depth, float pawns) {
			this.depth = depth;
			this.pawns = pawns;
		}
	}

	/**
	 * Converts a centipawn score into pawns and clamps to [-20, 20].
	 * Used for stable training targets.
	 *
	 * @param centipawns evaluation in centipawns
	 * @return evaluation in pawns, clamped to [-20, 20]
	 */
	private static float pawnsFromCp(int centipawns) {
		return clamp(centipawns / 100.0f, -20.0f, 20.0f);
	}

	/**
	 * Converts a mate score to a signed capped pawn value.
	 * Positive values favor the side to move, negative values the opponent.
	 *
	 * @param mateValue mate score reported by the engine
	 * @return capped pawn value for mate outcomes
	 */
	private static float pawnsFromMate(int mateValue) {
		int sign = mateValue >= 0 ? 1 : -1;
		return 20.0f * sign;
	}

	/**
	 * Encodes a position into the provided feature buffer.
	 * Writes piece planes, castling rights, en-passant file, and side-to-move.
	 *
	 * <p>The buffer layout is:
	 * <ol>
	 * <li>12 piece planes (one-hot per square).</li>
	 * <li>Four castling rights (white/black, kingside/queenside).</li>
	 * <li>Eight en-passant file indicators (rank is implicit).</li>
	 * <li>Side-to-move encoded as +1 for White, -1 for Black.</li>
	 * </ol>
	 * </p>
	 *
	 * @param position position to encode
	 * @param feats destination feature buffer
	 */
	private static void encodeInto(Position position, float[] feats) {
		Arrays.fill(feats, 0.0f);

		byte[] board = position.getBoard(); // Field order (A8 index 0)
		for (int sq = 0; sq < 64; sq++) {
			byte piece = board[sq];
			int ch = channel(piece);
			if (ch >= 0) {
				feats[sq * 12 + ch] = 1.0f;
			}
		}

		int idx = 64 * 12;
		feats[idx++] = position.getWhiteKingside() != Field.NO_SQUARE ? 1.0f : 0.0f;
		feats[idx++] = position.getWhiteQueenside() != Field.NO_SQUARE ? 1.0f : 0.0f;
		feats[idx++] = position.getBlackKingside() != Field.NO_SQUARE ? 1.0f : 0.0f;
		feats[idx++] = position.getBlackQueenside() != Field.NO_SQUARE ? 1.0f : 0.0f;

		byte ep = position.getEnPassant();
		if (ep != Field.NO_SQUARE) {
			int file = Field.getX(ep);
			feats[idx + file] = 1.0f;
		}
		idx += 8;

		feats[idx] = position.isWhiteTurn() ? 1.0f : -1.0f;
	}

	/**
	 * Maps a piece code to the channel index in the feature vector.
	 * Returns {@code -1} for empty or unsupported pieces.
	 *
	 * @param piece piece code from the board array
	 * @return channel index, or {@code -1} when not applicable
	 */
	private static int channel(byte piece) {
		return switch (piece) {
		case Piece.WHITE_PAWN -> 0;
		case Piece.WHITE_KNIGHT -> 1;
		case Piece.WHITE_BISHOP -> 2;
		case Piece.WHITE_ROOK -> 3;
		case Piece.WHITE_QUEEN -> 4;
		case Piece.WHITE_KING -> 5;
		case Piece.BLACK_PAWN -> 6;
		case Piece.BLACK_KNIGHT -> 7;
		case Piece.BLACK_BISHOP -> 8;
		case Piece.BLACK_ROOK -> 9;
		case Piece.BLACK_QUEEN -> 10;
		case Piece.BLACK_KING -> 11;
		default -> -1;
		};
	}

	/**
	 * Clamps a value to the provided inclusive range.
	 * Used to keep training targets bounded.
	 *
	 * @param v value to clamp
	 * @param lo lower bound
	 * @param hi upper bound
	 * @return clamped value
	 */
	private static float clamp(float v, float lo, float hi) {
		if (v < lo) {
			return lo;
		}
		if (v > hi) {
			return hi;
		}
		return v;
	}

	/**
	 * Streaming .npy float32 writer that writes a placeholder header and patches
	 * the row count in-place on close. Constant memory.
	 */
	private static final class NpyFloat32Writer implements Closeable {

		/**
		 * Width of the row-count placeholder field in the header.
		 * Keeps header patching stable even for very large datasets.
		 */
		private static final int ROWS_FIELD_WIDTH = 20;

		/**
		 * Random-access file backing the output.
		 * Used to patch the header on close.
		 */
		private final RandomAccessFile raf;

		/**
		 * File channel used for streaming writes.
		 * Keeps payload writes efficient and sequential.
		 */
		private final FileChannel ch;

		/**
		 * Whether the output is one-dimensional (labels).
		 * Controls header shape and write behavior.
		 */
		private final boolean oneD;

		/**
		 * Column count for 2D outputs (features).
		 * Ignored for 1D label outputs.
		 */
		private final int cols;

		/**
		 * Offset in the file where the row-count digits begin.
		 * Used to patch the placeholder on close.
		 */
		private final long rowsFieldOffsetInFile; // where the rows digits/spaces begin

		/**
		 * Width of the row-count field in the header.
		 * Matches the placeholder length.
		 */
		private final int rowsFieldWidth;

		/**
		 * Reusable buffer for writing full feature rows.
		 * Avoids per-row allocations.
		 */
		private final ByteBuffer rowBuf;

		/**
		 * Reusable buffer for writing scalar labels.
		 * Avoids per-write allocations.
		 */
		private final ByteBuffer scalarBuf;

		/**
		 * Number of rows written so far.
		 * Incremented after each successful write.
		 */
		private long rows = 0;

		/**
		 * Whether the writer has been closed.
		 * Prevents double-close operations.
		 */
		private boolean closed = false;

		/**
		 * Opens a 2D writer for feature rows.
		 * Uses the provided column count for the header shape.
		 *
		 * @param path output file path
		 * @param cols number of columns per row
		 * @return new writer instance
		 * @throws IOException if the file cannot be created
		 */
		static NpyFloat32Writer open2D(Path path, int cols) throws IOException {
			return new NpyFloat32Writer(path, false, cols);
		}

		/**
		 * Opens a 1D writer for scalar labels.
		 * Writes a single-column shape in the header.
		 *
		 * @param path output file path
		 * @return new writer instance
		 * @throws IOException if the file cannot be created
		 */
		static NpyFloat32Writer open1D(Path path) throws IOException {
			return new NpyFloat32Writer(path, true, -1);
		}

		/**
		 * Creates a streaming .npy writer and writes the header placeholder.
		 *
		 * @param path output path
		 * @param oneD whether the output is 1D (labels) or 2D (features)
		 * @param cols number of columns for 2D output, ignored for 1D
		 * @throws IOException if the file cannot be created or initialized
		 */
		private NpyFloat32Writer(Path path, boolean oneD, int cols) throws IOException {
			this.oneD = oneD;
			this.cols = cols;

			Path parent = path.toAbsolutePath().getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}

			RandomAccessFile localRaf = new RandomAccessFile(path.toFile(), "rw");
			FileChannel localCh = localRaf.getChannel();

			long localRowsFieldOffsetInFile;
			int localRowsFieldWidth;
			ByteBuffer localRowBuf;
			ByteBuffer localScalarBuf;

			try {
				localRaf.setLength(0);

				// Build header with fixed-width rows field using spaces (NOT leading zeros).
				String rowsPlaceholder = padLeft(0L, ROWS_FIELD_WIDTH);

				String shape = oneD
						? "(" + rowsPlaceholder + ",)"
						: "(" + rowsPlaceholder + ", " + cols + ",)";

				String header = "{'descr': '<f4', 'fortran_order': False, 'shape': " + shape + ", }";

				// NPY v1.0 header: magic(6) + version(2) + headerlen(2) + header bytes, padded to 16-byte alignment.
				int preamble = 10;
				int headerLenNoPad = header.length() + 1; // + '\n'
				int pad = (16 - ((preamble + headerLenNoPad) % 16)) % 16;
				String headerPadded = header + " ".repeat(pad) + "\n";
				byte[] headerBytes = headerPadded.getBytes(StandardCharsets.US_ASCII);

				// Locate where the placeholder lives within the header bytes so we can patch it later.
				int idx = headerPadded.indexOf(rowsPlaceholder);
				if (idx < 0) {
					throw new IOException("Internal error: rows placeholder not found in header");
				}
				localRowsFieldOffsetInFile = (long) preamble + idx;
				localRowsFieldWidth = rowsPlaceholder.length();

				// Write magic + version
				localRaf.write(new byte[] { (byte) 0x93, 'N', 'U', 'M', 'P', 'Y' });
				localRaf.write(new byte[] { 1, 0 }); // v1.0

				// header length (uint16 little-endian)
				int hlen = headerBytes.length;
				if (hlen > 0xFFFF) {
					throw new IOException("NPY header too large for v1.0: " + hlen);
				}
				localRaf.write((byte) (hlen & 0xFF));
				localRaf.write((byte) ((hlen >>> 8) & 0xFF));

				// header bytes
				localRaf.write(headerBytes);

				// Payload buffers (reused; no per-row allocations)
				localScalarBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
				localRowBuf = oneD
						? ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
						: ByteBuffer.allocate(cols * 4).order(ByteOrder.LITTLE_ENDIAN);
			} catch (IOException e) {
				try {
					localCh.close();
				} catch (IOException closeEx) {
					e.addSuppressed(closeEx);
				}
				try {
					localRaf.close();
				} catch (IOException closeEx) {
					e.addSuppressed(closeEx);
				}
				throw e;
			}

			this.raf = localRaf;
			this.ch = localCh;
			this.rowsFieldOffsetInFile = localRowsFieldOffsetInFile;
			this.rowsFieldWidth = localRowsFieldWidth;
			this.rowBuf = localRowBuf;
			this.scalarBuf = localScalarBuf;
		}

		/**
		 * Writes a single feature row to the output file.
		 * Increments the row count after a successful write.
		 *
		 * @param row feature row to write
		 * @throws IOException if writing fails
		 */
		void writeRow(float[] row) throws IOException {
			if (oneD) {
				throw new IllegalStateException("This writer is 1D; use writeScalar()");
			}
			if (row.length != cols) {
				throw new IllegalArgumentException("Expected row length " + cols + " but got " + row.length);
			}

			rowBuf.clear();
			for (int i = 0; i < cols; i++) {
				rowBuf.putFloat(row[i]);
			}
			rowBuf.flip();
			while (rowBuf.hasRemaining()) {
				ch.write(rowBuf);
			}
			rows++;
		}

		/**
		 * Writes a single scalar label to the output file.
		 * Increments the row count after a successful write.
		 *
		 * @param v scalar value to write
		 * @throws IOException if writing fails
		 */
		void writeScalar(float v) throws IOException {
			scalarBuf.clear();
			scalarBuf.putFloat(v);
			scalarBuf.flip();
			while (scalarBuf.hasRemaining()) {
				ch.write(scalarBuf);
			}
			rows++;
		}

		/**
		 * Patches the header with the final row count and closes the file.
		 * Ensures the NPY header stays consistent with the written payload.
		 *
		 * @throws IOException if flushing or closing fails
		 */
		@Override
		public void close() throws IOException {
			if (closed) return;
			closed = true;

			IOException failure = null;
			try {
				// Patch the rows field in-place using spaces (valid Python integer literal formatting).
				String rowsStr = padLeft(rows, rowsFieldWidth);
				byte[] rowsBytes = rowsStr.getBytes(StandardCharsets.US_ASCII);

				raf.seek(rowsFieldOffsetInFile);
				raf.write(rowsBytes);

				ch.force(false);
			} catch (IOException e) {
				failure = e;
			}

			try {
				ch.close();
			} catch (IOException e) {
				if (failure == null) {
					failure = e;
				} else {
					failure.addSuppressed(e);
				}
			}
			try {
				raf.close();
			} catch (IOException e) {
				if (failure == null) {
					failure = e;
				} else {
					failure.addSuppressed(e);
				}
			}

			if (failure != null) {
				throw failure;
			}
		}

		/**
		 * Pads a numeric value with leading spaces to a fixed width.
		 * Used for the NPY header row-count placeholder.
		 *
		 * @param value value to format
		 * @param width field width to pad to
		 * @return padded string representation
		 */
		private static String padLeft(long value, int width) {
			String s = Long.toString(value);
			int pad = width - s.length();
			if (pad <= 0) {
				return s;
			}
			return " ".repeat(pad) + s;
		}
	}
}
