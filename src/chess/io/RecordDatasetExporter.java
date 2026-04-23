package chess.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;
import chess.struct.Record;
import chess.uci.Analysis;
import chess.uci.Evaluation;
import chess.uci.Output;
import utility.Json;
import utility.Numbers;

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
		export(recordFile, outStem, null);
	}

	/**
	 * Exports a {@code .record} JSON array into NPY feature/label tensors while
	 * reporting progress once per input record.
	 *
	 * @param recordFile input record JSON array file
	 * @param outStem output stem path
	 * @param byteProgress optional callback receiving cumulative bytes read
	 * @throws IOException if reading or writing fails
	 */
	public static void export(Path recordFile, Path outStem, LongConsumer byteProgress) throws IOException {
		if (recordFile == null || outStem == null) {
			throw new IllegalArgumentException("recordFile and outStem must be non-null");
		}
		exportInternal(recordFile, outStem, RecordDatasetExporter::exportRecordObject, byteProgress);
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
	private static void exportInternal(
			Path jsonFile,
			Path outStem,
			JsonObjectExporter exporter,
			LongConsumer byteProgress) throws IOException {
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

			Json.streamTopLevelObjects(jsonFile, sink, byteProgress);
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
	 * Converts a centipawn score into pawns and clamps to [-20, 20].
	 * Used for stable training targets.
	 *
	 * @param centipawns evaluation in centipawns
	 * @return evaluation in pawns, clamped to [-20, 20]
	 */
	private static float pawnsFromCp(int centipawns) {
		return Numbers.clamp(centipawns / 100.0f, -20.0f, 20.0f);
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
		feats[idx++] = position.activeCastlingMoveTarget(Position.WHITE_KINGSIDE) != Field.NO_SQUARE ? 1.0f : 0.0f;
		feats[idx++] = position.activeCastlingMoveTarget(Position.WHITE_QUEENSIDE) != Field.NO_SQUARE ? 1.0f : 0.0f;
		feats[idx++] = position.activeCastlingMoveTarget(Position.BLACK_KINGSIDE) != Field.NO_SQUARE ? 1.0f : 0.0f;
		feats[idx++] = position.activeCastlingMoveTarget(Position.BLACK_QUEENSIDE) != Field.NO_SQUARE ? 1.0f : 0.0f;

		byte ep = position.enPassantSquare();
		if (ep != Field.NO_SQUARE) {
			int file = Field.getX(ep);
			feats[idx + file] = 1.0f;
		}
		idx += 8;

		feats[idx] = position.isWhiteToMove() ? 1.0f : -1.0f;
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
}
