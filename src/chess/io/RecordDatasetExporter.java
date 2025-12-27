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

	private static final int INPUTS = 64 * 12 + 4 + 8 + 1; // 781
	private static final Pattern STACK_SCORE_RE = Pattern.compile("score\\s+(cp|mate)\\s+(-?\\d+)");
	private static final Pattern STACK_DEPTH_RE = Pattern.compile("depth\\s+(\\d+)");

	@FunctionalInterface
	private interface JsonObjectExporter {
		void export(String obj, float[] featsBuf, NpyFloat32Writer feat, NpyFloat32Writer lab) throws IOException;
	}

	/**
	 * Prevents instantiation of this utility class.
	 */
	private RecordDatasetExporter() {
	}

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

	private static final class StackEval {
		private final int depth;
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

	private static float pawnsFromCp(int centipawns) {
		return clamp(centipawns / 100.0f, -20.0f, 20.0f);
	}

	private static float pawnsFromMate(int mateValue) {
		int sign = mateValue >= 0 ? 1 : -1;
		return 20.0f * sign;
	}

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
		// Keep this fairly large so you never exceed it even for huge datasets.
		private static final int ROWS_FIELD_WIDTH = 20;

		private final RandomAccessFile raf;
		private final FileChannel ch;
		private final boolean oneD;
		private final int cols;

		private final long rowsFieldOffsetInFile; // where the rows digits/spaces begin
		private final int rowsFieldWidth;

		private final ByteBuffer rowBuf;
		private final ByteBuffer scalarBuf;

		private long rows = 0;
		private boolean closed = false;

		static NpyFloat32Writer open2D(Path path, int cols) throws IOException {
			return new NpyFloat32Writer(path, false, cols);
		}

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

		void writeScalar(float v) throws IOException {
			scalarBuf.clear();
			scalarBuf.putFloat(v);
			scalarBuf.flip();
			while (scalarBuf.hasRemaining()) {
				ch.write(scalarBuf);
			}
			rows++;
		}

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
