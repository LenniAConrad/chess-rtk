package chess.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import chess.core.Position;
import chess.nn.lc0.Encoder;
import chess.nn.lc0.Network;
import chess.nn.lc0.PolicyEncoder;
import chess.struct.Record;
import chess.uci.Analysis;
import chess.uci.Chances;
import chess.uci.Evaluation;
import chess.uci.Output;
import utility.Json;
import utility.Numbers;

/**
 * Exporter that converts {@code .record} JSON arrays into LC0-style training tensors.
 *
 * <p>
 * Writes four files next to the requested output stem:
 * <ul>
 *   <li>{@code <stem>.lc0.inputs.npy} shaped {@code (N, 112*64)} float32</li>
 *   <li>{@code <stem>.lc0.policy.npy} shaped {@code (N, policySize)} float32 (one-hot)</li>
 *   <li>{@code <stem>.lc0.value.npy} shaped {@code (N,)} float32 (scalar in [-1, 1])</li>
 *   <li>{@code <stem>.lc0.meta.json} metadata for policy/value encoding</li>
 * </ul>
 * </p>
 *
 * <p>
 * Policy encoding uses the LC0 73-plane format (see {@link PolicyEncoder}).
 * If {@code weights} are provided, the policy is compressed using the model's
 * policy map to match the LC0 network output size.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class RecordLc0Exporter {

    /**
     * LC0 input planes length (112 * 64).
     */
    private static final int INPUTS = 112 * 64;

    /**
     * Clamp range for centipawn conversion (in pawns).
     */
    private static final float PAWN_CLAMP = 20.0f;

    /**
     * Utility class; prevent instantiation.
     */
    private RecordLc0Exporter() {
        // utility
    }

    /**
     * Export a {@code .record} JSON array into LC0 input/policy/value tensors.
     *
     * @param recordFile input record JSON array
     * @param outStem output stem path
     * @param weights optional LC0 weights file to load a policy map for compression (nullable)
     * @throws IOException if reading or writing fails
     */
    public static void export(Path recordFile, Path outStem, Path weights) throws IOException {
        export(recordFile, outStem, weights, null);
    }

    /**
     * Export a {@code .record} JSON array into LC0 tensors while reporting progress
     * once per input record.
     *
     * @param recordFile input record JSON array
     * @param outStem output stem path
     * @param weights optional LC0 weights file to load a policy map for compression
     *                (nullable)
     * @param byteProgress optional callback receiving cumulative bytes read
     * @throws IOException if reading or writing fails
     */
    public static void export(Path recordFile, Path outStem, Path weights, LongConsumer byteProgress) throws IOException {
        if (recordFile == null || outStem == null) {
            throw new IllegalArgumentException("recordFile and outStem must be non-null");
        }

        int[] policyMap = null;
        int[] policyMapInverseTmp = null;
        String policyMapSourceTmp = null;

        if (weights != null) {
            policyMap = Network.loadPolicyMap(weights);
            policyMapInverseTmp = invertPolicyMap(policyMap);
            policyMapSourceTmp = weights.toString();
        }

        final int[] policyMapInverse = policyMapInverseTmp;
        final String policyMapSource = policyMapSourceTmp;
        final int policySize = policyMap != null ? policyMap.length : PolicyEncoder.RAW_POLICY_SIZE;

        Path inputsPath = outStem.resolveSibling(outStem.getFileName().toString() + ".lc0.inputs.npy");
        Path policyPath = outStem.resolveSibling(outStem.getFileName().toString() + ".lc0.policy.npy");
        Path valuePath = outStem.resolveSibling(outStem.getFileName().toString() + ".lc0.value.npy");
        Path metaPath = outStem.resolveSibling(outStem.getFileName().toString() + ".lc0.meta.json");

        boolean success = false;
        long[] counters = new long[2]; // [0]=written, [1]=skipped

        try (NpyFloat32Writer inputsWriter = NpyFloat32Writer.open2D(inputsPath, INPUTS);
                NpyFloat32Writer policyWriter = NpyFloat32Writer.open2D(policyPath, policySize);
                NpyFloat32Writer valueWriter = NpyFloat32Writer.open1D(valuePath)) {

            float[] policyBuffer = new float[policySize];

            Consumer<String> sink = objJson -> {
                try {
                    if (!exportRecordObject(objJson, inputsWriter, policyWriter, valueWriter,
                            policyBuffer, policyMapInverse)) {
                        counters[1]++;
                    } else {
                        counters[0]++;
                    }
                } catch (IOException io) {
                    throw new UncheckedIOException(io);
                }
            };

            Json.streamTopLevelObjects(recordFile, sink, byteProgress);
            success = true;
        } catch (UncheckedIOException uio) {
            throw uio.getCause();
        } finally {
            if (!success) {
                Files.deleteIfExists(inputsPath);
                Files.deleteIfExists(policyPath);
                Files.deleteIfExists(valuePath);
            }
        }

        writeMetadata(metaPath, recordFile, counters[0], counters[1], policySize, policyMapSource);
    }

     /**
     * Handles export record object.
     * @param json json
     * @param inputsWriter inputs writer
     * @param policyWriter policy writer
     * @param valueWriter value writer
     * @param policyBuffer policy buffer
     * @param policyMapInverse policy map inverse
     * @return computed value
     * @throws IOException if the operation fails
     */
     private static boolean exportRecordObject(
            String json,
            NpyFloat32Writer inputsWriter,
            NpyFloat32Writer policyWriter,
            NpyFloat32Writer valueWriter,
            float[] policyBuffer,
            int[] policyMapInverse) throws IOException {
        Record parsedRecord = Record.fromJson(json);
        Output best = bestOutput(parsedRecord);
        if (best == null) {
            return false;
        }
        Position position = parsedRecord.getPosition();
        if (position == null) {
            return false;
        }
        Evaluation evaluation = best.getEvaluation();
        Chances chances = best.getChances();
        if (!hasUsableValue(evaluation, chances)) {
            return false;
        }
        int policyIndex = resolvePolicyIndex(position, best, policyMapInverse);
        if (policyIndex < 0) {
            return false;
        }
        float[] inputs = encodeInputs(position);
        if (inputs == null) {
            return false;
        }
        Arrays.fill(policyBuffer, 0.0f);
        policyBuffer[policyIndex] = 1.0f;
        float value = valueFromEvaluation(evaluation, chances);

        inputsWriter.writeRow(inputs);
        policyWriter.writeRow(policyBuffer);
        valueWriter.writeScalar(value);
        return true;
    }

     /**
     * Returns the best usable engine output for one record.
     *
     * @param parsedRecord parsed record
     * @return best output, or {@code null} when unavailable
     */
     private static Output bestOutput(Record parsedRecord) {
        if (parsedRecord == null) {
            return null;
        }
        Analysis analysis = parsedRecord.getAnalysis();
        if (analysis == null) {
            return null;
        }
        Output best = analysis.getBestOutput();
        if (best == null || best.getMoves() == null || best.getMoves().length == 0) {
            return null;
        }
        return best;
    }

     /**
     * Returns whether the record contains a usable scalar value target.
     *
     * @param evaluation centipawn or mate evaluation
     * @param chances optional WDL chances
     * @return true when a value target can be derived
     */
     private static boolean hasUsableValue(Evaluation evaluation, Chances chances) {
        return chances != null || (evaluation != null && evaluation.isValid());
    }

     /**
     * Resolves the policy row index for the best move.
     *
     * @param position training position
     * @param best best engine output
     * @param policyMapInverse optional policy-map inverse
     * @return policy index, or {@code -1} when invalid
     */
     private static int resolvePolicyIndex(Position position, Output best, int[] policyMapInverse) {
        short bestMove = best.getMoves()[0];
        if (!position.isLegalMove(bestMove)) {
            return -1;
        }
        int rawIndex = PolicyEncoder.rawPolicyIndex(position, bestMove);
        if (rawIndex < 0) {
            return -1;
        }
        if (policyMapInverse == null) {
            return rawIndex;
        }
        if (rawIndex >= policyMapInverse.length) {
            return -1;
        }
        int policyIndex = policyMapInverse[rawIndex];
        return policyIndex >= 0 ? policyIndex : -1;
    }

     /**
     * Encodes the input planes for one training row.
     *
     * @param position training position
     * @return encoded inputs, or {@code null} when the encoder shape is unexpected
     */
     private static float[] encodeInputs(Position position) {
        float[] inputs = Encoder.encode(position);
        return inputs.length == INPUTS ? inputs : null;
    }

     /**
     * Handles value from evaluation.
     * @param evaluation evaluation
     * @param chances chances
     * @return computed value
     */
     private static float valueFromEvaluation(Evaluation evaluation, Chances chances) {
        if (chances != null) {
            float win = chances.getWinChance() / 1000.0f;
            float loss = chances.getLossChance() / 1000.0f;
            return Numbers.clamp(win - loss, -1.0f, 1.0f);
        }
        if (evaluation == null || !evaluation.isValid()) {
            return 0.0f;
        }
        if (evaluation.isMate()) {
            return evaluation.getValue() >= 0 ? 1.0f : -1.0f;
        }
        float pawns = evaluation.getValue() / 100.0f;
        pawns = Numbers.clamp(pawns, -PAWN_CLAMP, PAWN_CLAMP);
        return pawns / PAWN_CLAMP;
    }

     /**
     * Handles invert policy map.
     * @param policyMap policy map
     * @return computed value
     */
     private static int[] invertPolicyMap(int[] policyMap) {
        int maxIndex = -1;
        for (int value : policyMap) {
            if (value > maxIndex) {
                maxIndex = value;
            }
        }
        int[] inverse = new int[maxIndex + 1];
        Arrays.fill(inverse, -1);
        for (int compressedIndex = 0; compressedIndex < policyMap.length; compressedIndex++) {
            int rawIndex = policyMap[compressedIndex];
            if (rawIndex >= 0 && rawIndex < inverse.length) {
                inverse[rawIndex] = compressedIndex;
            }
        }
        return inverse;
    }

     /**
     * Writes the metadata.
     * @param metaPath meta path
     * @param recordFile record file
     * @param written written
     * @param skipped skipped
     * @param policySize policy size
     * @param policyMapSource policy map source
     * @throws IOException if the operation fails
     */
     private static void writeMetadata(Path metaPath, Path recordFile, long written, long skipped,
            int policySize, String policyMapSource) throws IOException {
        StringBuilder builder = new StringBuilder(1024);
        builder.append("{\n");
        builder.append("  \"source\": \"").append(escapeJson(recordFile.toString())).append("\",\n");
        builder.append("  \"rows_written\": ").append(written).append(",\n");
        builder.append("  \"rows_skipped\": ").append(skipped).append(",\n");
        builder.append("  \"inputs\": {\n");
        builder.append("    \"planes\": 112,\n");
        builder.append("    \"squares\": 64,\n");
        builder.append("    \"order\": \"channel-major (plane * 64 + square), a1..h8\",\n");
        builder.append("    \"side_to_move_perspective\": true\n");
        builder.append("  },\n");
        builder.append("  \"policy\": {\n");
        builder.append("    \"encoding\": \"lc0-73plane\",\n");
        builder.append("    \"size\": ").append(policySize).append(",\n");
        builder.append("    \"compressed\": ").append(policyMapSource != null).append(",\n");
        builder.append("    \"weights_path\": ");
        if (policyMapSource != null) {
            builder.append("\"").append(escapeJson(policyMapSource)).append("\"");
        } else {
            builder.append("null");
        }
        builder.append(",\n");
        builder.append("    \"plane_order\": \"N,S,E,W,NE,NW,SE,SW\",\n");
        builder.append("    \"knight_order\": \"(1,2),(2,1),(2,-1),(1,-2),(-1,-2),(-2,-1),(-2,1),(-1,2)\",\n");
        builder.append("    \"underpromotions\": \"N,B,R x forward,forward-left,forward-right\"\n");
        builder.append("  },\n");
        builder.append("  \"value\": {\n");
        builder.append("    \"encoding\": \"scalar\",\n");
        builder.append("    \"range\": \"[-1,1]\",\n");
        builder.append("    \"source\": \"wdl if available else centipawn/mate\",\n");
        builder.append("    \"cp_clamp_pawns\": ").append(PAWN_CLAMP).append("\n");
        builder.append("  }\n");
        builder.append("}\n");

        Files.writeString(metaPath, builder.toString(), StandardCharsets.UTF_8);
    }

     /**
     * Handles escape json.
     * @param value value
     * @return computed value
     */
     private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
