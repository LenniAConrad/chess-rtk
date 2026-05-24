package application.cli.command;

import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.PathOps.ensureParentDir;
import static application.cli.RecordIO.streamRecordFile;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import application.console.Bar;
import application.cli.RecordIO.RecordConsumer;
import chess.core.Position;
import chess.struct.Record;
import chess.uci.Analysis;
import chess.uci.Evaluation;
import chess.uci.Output;
import utility.Argv;
import utility.Json;

/**
 * Implements {@code record analysis-delta}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings({"java:S3398", "java:S3776", "java:S6541"})
public final class RecordAnalysisDeltaCommand {

	/**
	 * Current command label used in diagnostics.
	 */
	private static final String COMMAND_LABEL = "record analysis-delta";

	/**
	 * Shared ext analysis delta jsonl constant.
	 */
	private static final String EXT_ANALYSIS_DELTA_JSONL = ".analysis-delta.jsonl";
	/**
	 * Delta type for records with no valid initial/final evaluation.
	 */
	private static final String DELTA_NONE = "none";
	/**
	 * Delta type for centipawn evaluations with a stable type.
	 */
	private static final String DELTA_CP = "cp";
	/**
	 * Delta type for mate evaluations with a stable type.
	 */
	private static final String DELTA_MATE = "mate";
	/**
	 * Delta type for mixed evaluation kinds between initial and final.
	 */
	private static final String DELTA_MIXED = "mixed";

	/**
	 * Fluctuation type for records with no valid sample evaluations.
	 */
	private static final String FLUCTUATION_NONE = "none";
	/**
	 * Fluctuation type when all valid samples are centipawn evaluations.
	 */
	private static final String FLUCTUATION_CP = "cp";
	/**
	 * Fluctuation type when all valid samples are mate evaluations.
	 */
	private static final String FLUCTUATION_MATE = "mate";
	/**
	 * Fluctuation type when valid samples include both mate and centipawn evaluations.
	 */
	private static final String FLUCTUATION_MIXED = "mixed";

	/**
	 * Shared output order constant.
	 */
	private static final Comparator<Output> OUTPUT_ORDER = Comparator
			.comparingInt(Output::getDepth)
			.thenComparingLong(Output::getTime);

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordAnalysisDeltaCommand() {
		// utility
	}

	/**
	 * Handles {@code record analysis-delta}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runRecordAnalysisDelta(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path output = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		a.ensureConsumed();

		if (output == null) {
			output = deriveOutput(input, EXT_ANALYSIS_DELTA_JSONL);
		}

		try {
			ensureParentDir(output);
		} catch (IOException ex) {
			throw new CommandFailure(COMMAND_LABEL + ": failed to prepare output: " + ex.getMessage(), ex, 2, verbose);
		}

		Bar bar = fileProgressBar(input, COMMAND_LABEL);
		try (BufferedWriter out = Files.newBufferedWriter(output)) {
			DeltaWriter writer = new DeltaWriter(out);
			streamRecordFile(input, verbose, COMMAND_LABEL, writer, bar == null ? null : bar::set);
			finishProgress(bar);
			System.out.printf(Locale.ROOT,
					"record analysis-delta: wrote %d records (%d invalid) to %s%n",
					writer.writtenCount(),
					writer.invalidCount(),
					output);
		} catch (IOException | UncheckedIOException ex) {
			finishProgress(bar);
			throw new CommandFailure(COMMAND_LABEL + ": failed to write output: " + ex.getMessage(), ex, 2, verbose);
		}
	}

	/**
	 * Handles file progress bar.
	 * @param input input
	 * @param label label
	 * @return computed value
	 */
	private static Bar fileProgressBar(Path input, String label) {
		try {
			long size = Files.size(input);
			return size > 0L ? new Bar(size, label) : null;
		} catch (IOException ex) {
			return null;
		}
	}

	/**
	 * Handles finish progress.
	 * @param bar bar
	 */
	private static void finishProgress(Bar bar) {
		if (bar != null) {
			bar.finish();
		}
	}

	/**
	 * Handles derive output.
	 * @param input input
	 * @param suffix suffix
	 * @return computed value
	 */
	private static Path deriveOutput(Path input, String suffix) {
		String name = input.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String stem = (dot > 0) ? name.substring(0, dot) : name;
		return input.resolveSibling(stem + suffix);
	}

	/**
	 * Provides delta writer behavior.
	 */
	private static final class DeltaWriter implements RecordConsumer {

		 /**
		 * Stores the out.
		 */
		 private final BufferedWriter out;
		 /**
		 * Stores the index.
		 */
		 private long index;
		 /**
		 * Stores the invalid.
		 */
		 private long invalid;
		 /**
		 * Stores the written.
		 */
		 private long written;

		 /**
		 * Creates a new delta writer instance.
		 * @param out out
		 */
		 private DeltaWriter(BufferedWriter out) {
			this.out = out;
		}

		 /**
		 * Handles accept.
		 * @param rec rec
		 */
		 @Override
		public void accept(Record rec) {
			String line = buildJsonLine(rec, index++);
			try {
				out.write(line);
				out.newLine();
			} catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
			written++;
		}

		 /**
		 * Handles invalid.
		 */
		 @Override
		public void invalid() {
			invalid++;
		}

		 /**
		 * Handles written count.
		 * @return computed value
		 */
		 private long writtenCount() {
			return written;
		}

		 /**
		 * Handles invalid count.
		 * @return computed value
		 */
		 private long invalidCount() {
			return invalid;
		}
	}

	/**
	 * Handles build json line.
	 * @param rec rec
	 * @param index index
	 * @return computed value
	 */
	private static String buildJsonLine(Record rec, long index) {
		Analysis analysis = (rec == null) ? null : rec.getAnalysis();
		Position position = (rec == null) ? null : rec.getPosition();

		boolean analysisPresent = analysis != null && !analysis.isEmpty();
		List<Output> samples = analysisPresent ? collectSamples(analysis) : List.of();

		Output initial = firstSample(samples);
		Output fin = lastSample(samples);

		Evaluation initialEval = (initial == null) ? null : initial.getEvaluation();
		Evaluation finalEval = (fin == null) ? null : fin.getEvaluation();

		DeltaInfo delta = computeDelta(initialEval, finalEval);
		FluctuationInfo fluctuation = computeFluctuation(samples, finalEval, delta.type);

		StringBuilder sb = new StringBuilder(256).append('{');
		appendField(sb, "index", Long.toString(index));
		appendField(sb, "created", (rec == null) ? "null" : Long.toString(rec.getCreated()));
		appendField(sb, "engine", jsonString((rec == null) ? null : rec.getEngine()));
		appendField(sb, "position", jsonString(position == null ? null : position.toString()));
		appendField(sb, "analysis_present", analysisPresent ? "true" : "false");
		appendField(sb, "eval_samples", Integer.toString(samples.size()));
		appendField(sb, "initial_eval", jsonString(evalLabel(initialEval)));
		appendField(sb, "initial_is_mate", jsonBoolean(isMate(initialEval)));
		appendField(sb, "initial_value", jsonInt(evalValue(initialEval)));
		appendField(sb, "initial_depth", jsonInt(depthOf(initial)));
		appendField(sb, "initial_time_ms", jsonLong(timeOf(initial)));
		appendField(sb, "final_eval", jsonString(evalLabel(finalEval)));
		appendField(sb, "final_is_mate", jsonBoolean(isMate(finalEval)));
		appendField(sb, "final_value", jsonInt(evalValue(finalEval)));
		appendField(sb, "final_depth", jsonInt(depthOf(fin)));
		appendField(sb, "final_time_ms", jsonLong(timeOf(fin)));
		appendField(sb, "delta_type", jsonString(delta.type));
		appendField(sb, "delta_value", jsonInt(delta.value));
		appendField(sb, "time_to_final_ms", jsonLong(fluctuation.timeToFinal));
		appendField(sb, "time_to_final_depth", jsonInt(fluctuation.depthToFinal));
		appendField(sb, "fluctuation_type", jsonString(fluctuation.type));
		appendField(sb, "fluctuation_range", jsonInt(fluctuation.range));
		appendField(sb, "fluctuation_min", jsonInt(fluctuation.min));
		appendField(sb, "fluctuation_max", jsonInt(fluctuation.max));
		appendField(sb, "max_abs_delta_from_final", jsonInt(fluctuation.maxAbsDelta));
		return sb.append('}').toString();
	}

	/**
	 * Handles first sample.
	 * @param samples samples
	 * @return computed value
	 */
	private static Output firstSample(List<Output> samples) {
		return samples.isEmpty() ? null : samples.get(0);
	}

	/**
	 * Handles last sample.
	 * @param samples samples
	 * @return computed value
	 */
	private static Output lastSample(List<Output> samples) {
		return samples.isEmpty() ? null : samples.get(samples.size() - 1);
	}

	/**
	 * Handles compute delta.
	 * @param initialEval initial eval
	 * @param finalEval final eval
	 * @return computed value
	 */
	private static DeltaInfo computeDelta(Evaluation initialEval, Evaluation finalEval) {
		String deltaType = DELTA_NONE;
		Integer deltaValue = null;
		if (isValid(initialEval) && isValid(finalEval)) {
			if (initialEval.isMate() == finalEval.isMate()) {
				deltaType = initialEval.isMate() ? DELTA_MATE : DELTA_CP;
				deltaValue = finalEval.getValue() - initialEval.getValue();
			} else {
				deltaType = DELTA_MIXED;
			}
		}
		return new DeltaInfo(deltaType, deltaValue);
	}

	/**
	 * Handles compute fluctuation.
	 * @param samples samples
	 * @param finalEval final eval
	 * @param deltaType delta type
	 * @return computed value
	 */
	private static FluctuationInfo computeFluctuation(List<Output> samples, Evaluation finalEval, String deltaType) {
		FluctuationInfo info = new FluctuationInfo();
		if (!isValid(finalEval) || samples.isEmpty()) {
			info.type = FLUCTUATION_NONE;
			return info;
		}

		long bestTime = Long.MAX_VALUE;
		int bestDepth = Integer.MAX_VALUE;
		boolean timeSeen = false;

		int minVal = Integer.MAX_VALUE;
		int maxVal = Integer.MIN_VALUE;
		int cpCount = 0;
		int mateCount = 0;
		int maxAbs = Integer.MIN_VALUE;

		for (Output out : samples) {
			Evaluation eval = out.getEvaluation();
			if (!isValid(eval)) {
				continue;
			}
			if (eval.isMate()) {
				mateCount++;
			} else {
				cpCount++;
			}
			int value = eval.getValue();
			if (value < minVal) {
				minVal = value;
			}
			if (value > maxVal) {
				maxVal = value;
			}

			if (finalEval.isMate() == eval.isMate()) {
				int abs = Math.abs(value - finalEval.getValue());
				if (abs > maxAbs) {
					maxAbs = abs;
				}
			}

			if (sameEval(eval, finalEval)) {
				int depth = out.getDepth();
				if (depth < bestDepth) {
					bestDepth = depth;
				}
				long time = out.getTime();
				if (time > 0 && time < bestTime) {
					bestTime = time;
					timeSeen = true;
				}
			}
		}

		if (bestDepth != Integer.MAX_VALUE) {
			info.depthToFinal = bestDepth;
		}
		if (timeSeen) {
			info.timeToFinal = bestTime;
		}

		if (cpCount > 0 && mateCount == 0) {
			info.type = FLUCTUATION_CP;
		} else if (mateCount > 0 && cpCount == 0) {
			info.type = FLUCTUATION_MATE;
		} else if (cpCount > 0 && mateCount > 0) {
			info.type = FLUCTUATION_MIXED;
		} else {
			info.type = FLUCTUATION_NONE;
		}

		if (!FLUCTUATION_MIXED.equals(info.type) && (cpCount > 0 || mateCount > 0)) {
			info.min = minVal;
			info.max = maxVal;
			info.range = maxVal - minVal;
		}

		if (maxAbs != Integer.MIN_VALUE && !DELTA_MIXED.equals(deltaType)) {
			info.maxAbsDelta = maxAbs;
		}

		return info;
	}

	/**
	 * Provides delta info behavior.
	 */
	private static final class DeltaInfo {
		 /**
		 * Stores the type.
		 */
		 private final String type;
		 /**
		 * Stores the value.
		 */
		 private final Integer value;

		 /**
		 * Creates a new delta info instance.
		 * @param type type
		 * @param value value
		 */
		 private DeltaInfo(String type, Integer value) {
			this.type = type;
			this.value = value;
		}
	}

	/**
	 * Provides fluctuation info behavior.
	 */
	private static final class FluctuationInfo {
		 /**
		 * Stores the time to final.
		 */
		 private Long timeToFinal;
		 /**
		 * Stores the depth to final.
		 */
		 private Integer depthToFinal;
		 /**
		 * Stores the max abs delta.
		 */
		 private Integer maxAbsDelta;
		 /**
		 * Stores the range.
		 */
		 private Integer range;
		 /**
		 * Stores the min.
		 */
		 private Integer min;
		 /**
		 * Stores the max.
		 */
		 private Integer max;
		 /**
		 * Stores the type.
		 */
		 private String type = FLUCTUATION_NONE;
	}

	/**
	 * Handles collect samples.
	 * @param analysis analysis
	 * @return computed value
	 */
	private static List<Output> collectSamples(Analysis analysis) {
		Output[] outputs = analysis.getOutputs();
		List<Output> list = new ArrayList<>();
		for (Output out : outputs) {
			if (out == null) {
				continue;
			}
			boolean accept = out.getPrincipalVariation() == 1;
			if (accept) {
				Evaluation eval = out.getEvaluation();
				accept = eval != null && eval.isValid();
			}
			if (accept) {
				list.add(out);
			}
		}
		list.sort(OUTPUT_ORDER);
		return list;
	}

	/**
	 * Returns whether valid.
	 * @param eval eval
	 * @return true when valid
	 */
	private static boolean isValid(Evaluation eval) {
		return eval != null && eval.isValid();
	}

	/**
	 * Handles same eval.
	 * @param a a
	 * @param b b
	 * @return computed value
	 */
	private static boolean sameEval(Evaluation a, Evaluation b) {
		if (!isValid(a) || !isValid(b)) {
			return false;
		}
		return a.isMate() == b.isMate() && a.getValue() == b.getValue();
	}

	/**
	 * Handles eval label.
	 * @param eval eval
	 * @return computed value
	 */
	private static String evalLabel(Evaluation eval) {
		if (!isValid(eval)) {
			return null;
		}
		if (eval.isMate()) {
			return "#" + eval.getValue();
		}
		return String.format(Locale.ROOT, "%+d", eval.getValue());
	}

	/**
	 * Returns whether mate.
	 * @param eval eval
	 * @return true when mate
	 */
	private static Boolean isMate(Evaluation eval) {
		return isValid(eval) ? eval.isMate() : null;
	}

	/**
	 * Handles eval value.
	 * @param eval eval
	 * @return computed value
	 */
	private static Integer evalValue(Evaluation eval) {
		return isValid(eval) ? eval.getValue() : null;
	}

	/**
	 * Handles depth of.
	 * @param out out
	 * @return computed value
	 */
	private static Integer depthOf(Output out) {
		return out == null ? null : (int) out.getDepth();
	}

	/**
	 * Handles time of.
	 * @param out out
	 * @return computed value
	 */
	private static Long timeOf(Output out) {
		if (out == null) {
			return null;
		}
		long time = out.getTime();
		return time > 0 ? time : null;
	}

	/**
	 * Handles append field.
	 * @param sb sb
	 * @param name name
	 * @param value value
	 */
	private static void appendField(StringBuilder sb, String name, String value) {
		if (sb.length() > 1) {
			sb.append(',');
		}
		sb.append('"').append(name).append("\":").append(value);
	}

	/**
	 * Handles json string.
	 * @param value value
	 * @return computed value
	 */
	private static String jsonString(String value) {
		if (value == null) {
			return "null";
		}
		return "\"" + Json.esc(value) + "\"";
	}

	/**
	 * Handles json boolean.
	 * @param value value
	 * @return computed value
	 */
	private static String jsonBoolean(Boolean value) {
		if (value == null) {
			return "null";
		}
		return value ? "true" : "false";
	}

	/**
	 * Handles json int.
	 * @param value value
	 * @return computed value
	 */
	private static String jsonInt(Integer value) {
		return value == null ? "null" : Integer.toString(value);
	}

	/**
	 * Handles json long.
	 * @param value value
	 * @return computed value
	 */
	private static String jsonLong(Long value) {
		return value == null ? "null" : Long.toString(value);
	}
}
