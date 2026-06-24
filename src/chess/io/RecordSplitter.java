package chess.io;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import chess.core.Position;
import utility.Json;

/**
 * Group-aware, deterministic train/val/test (or any-N-way) splitter for
 * {@code .record} JSON-array and JSONL inputs.
 *
 * <p>Determinism is structural: every record's bucket is chosen by
 * {@code SHA-256(seed-bytes ‖ group-key-bytes)}, never by input order, file
 * mtime, or wall clock. Group awareness is encoded by the {@link Strategy}
 * key choice — by default the canonical FEN (board, side-to-move, castling
 * rights, en-passant target) of the record's {@code position}, with the
 * halfmove and fullmove counters stripped so transpositions of the same
 * position can never straddle splits.</p>
 *
 * <p>Output is written as JSONL (one record JSON per line) to one file per
 * split. Records are emitted in input order within their bucket, so the
 * split is byte-stable across runs of the same input.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class RecordSplitter {

	/**
	 * Field used by {@link Strategy#FEN} to derive the group key.
	 */
	private static final String POSITION_FIELD = "position";

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordSplitter() {
		// utility
	}

	/**
	 * Returns the bucket name a record JSON would land in under the given
	 * seed, strategy, and split spec.
	 *
	 * @param json     record JSON object text
	 * @param spec     split ratio spec
	 * @param seed     deterministic seed
	 * @param strategy group-key strategy
	 * @return chosen split name
	 */
	public static String bucketFor(String json, SplitSpec spec, long seed, Strategy strategy) {
		return chooseBucket(seed, groupKey(json, strategy), spec);
	}

	/**
	 * Computes the conventional output path for a named split under a given prefix.
	 *
	 * @param outputPrefix source output prefix
	 * @param splitName    source split name
	 * @return on-disk path {@code <prefix>.<split>.jsonl}
	 */
	public static Path outputPathFor(Path outputPrefix, String splitName) {
		return outputPrefix.resolveSibling(outputPrefix.getFileName() + "." + splitName + ".jsonl");
	}

	/**
	 * Computes a record's group key under the given strategy.
	 *
	 * @param json     record JSON line text
	 * @param strategy group-key strategy
	 * @return group key text (empty when the record has no parseable position)
	 */
	public static String groupKey(String json, Strategy strategy) {
		if (strategy != Strategy.FEN) {
			throw new IllegalArgumentException(
					"only Strategy.FEN is supported in v1 (got " + strategy + ")");
		}
		String rawFen = Json.parseStringField(json, POSITION_FIELD);
		if (rawFen == null || rawFen.isBlank()) {
			return "";
		}
		try {
			String canonical = new Position(rawFen).toString();
			return positionIdentity(canonical);
		} catch (RuntimeException ex) {
			return "";
		}
	}

	/**
	 * Parses a user-facing split grouping strategy token.
	 *
	 * @param token strategy token; {@code null} defaults to {@code fen}
	 * @return parsed strategy
	 */
	public static Strategy parseStrategy(String token) {
		if (token == null || token.isBlank()) {
			return Strategy.FEN;
		}
		String chosen = token.toLowerCase(Locale.ROOT);
		return switch (chosen) {
			case "fen", "position", "position-signature", "fen-position" -> Strategy.FEN;
			default -> throw new IllegalArgumentException("unsupported split strategy '" + token
					+ "' (only fen/position-signature is available today)");
		};
	}

	/**
	 * Returns the user-facing token for a split grouping strategy.
	 *
	 * @param strategy split strategy
	 * @return stable token
	 */
	public static String strategyToken(Strategy strategy) {
		if (strategy != Strategy.FEN) {
			throw new IllegalArgumentException("unknown split strategy: " + strategy);
		}
		return "fen";
	}

	/**
	 * Strips the halfmove-clock and fullmove-counter fields from a FEN,
	 * leaving the four position-identity fields (board, side to move,
	 * castling, en-passant).
	 *
	 * @param fen canonical FEN string
	 * @return four-field position-identity string
	 */
	private static String positionIdentity(String fen) {
		String[] fields = fen.split(" ");
		if (fields.length < 4) {
			return fen;
		}
		return fields[0] + " " + fields[1] + " " + fields[2] + " " + fields[3];
	}

	/**
	 * Chooses the bucket name for a given seed + group key.
	 *
	 * @param seed      deterministic seed
	 * @param groupKey  group key text
	 * @param spec      split ratio spec
	 * @return chosen split name
	 */
	public static String chooseBucket(long seed, String groupKey, SplitSpec spec) {
		double position = scoreFor(seed, groupKey);
		double cumulative = 0.0;
		List<String> names = spec.names();
		for (int i = 0; i < names.size(); i++) {
			String name = names.get(i);
			cumulative += spec.ratio(name);
			if (position < cumulative || i == names.size() - 1) {
				return name;
			}
		}
		return names.get(names.size() - 1);
	}

	/**
	 * Returns a stable {@code [0, 1)} score from a seed and group key.
	 *
	 * @param seed     deterministic seed
	 * @param groupKey source group key
	 * @return score in {@code [0, 1)}
	 */
	private static double scoreFor(long seed, String groupKey) {
		byte[] keyBytes = groupKey.getBytes(StandardCharsets.UTF_8);
		byte[] combined = new byte[8 + keyBytes.length];
		for (int i = 0; i < 8; i++) {
			combined[i] = (byte) (seed >>> (8 * (7 - i)));
		}
		System.arraycopy(keyBytes, 0, combined, 8, keyBytes.length);
		MessageDigest digest = newDigest();
		byte[] hash = digest.digest(combined);
		long top = 0L;
		for (int i = 0; i < 8; i++) {
			top = (top << 8) | (hash[i] & 0xFFL);
		}
		long positive = top & 0x7FFFFFFFFFFFFFFFL;
		return positive / (double) Long.MAX_VALUE;
	}

	/**
	 * Constructs a fresh SHA-256 digest.
	 *
	 * @return ready-to-use digest
	 */
	private static MessageDigest newDigest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable on this JDK", ex);
		}
	}

	/**
	 * Group-key strategy.
	 */
	public enum Strategy {

		/**
		 * Group by the record's {@code position} FEN, stripped of halfmove
		 * and fullmove counters so transpositions group together.
		 */
		FEN
	}

	/**
	 * Named split specification (e.g. {@code train=0.7, val=0.15, test=0.15}).
	 *
	 * <p>Ratios are normalised at construction so a caller-supplied
	 * {@code 70:15:15} reads identically to {@code 0.70:0.15:0.15}.</p>
	 */
	public static final class SplitSpec {

		/**
		 * Names in declaration order.
		 */
		private final List<String> names;

		/**
		 * Name → normalised ratio map.
		 */
		private final LinkedHashMap<String, Double> ratios;

		/**
		 * Constructs a spec from name/weight pairs.
		 *
		 * @param entries ordered (name, raw weight) pairs
		 * @param weights split weights
		 */
		private SplitSpec(List<String> entries, List<Double> weights) {
			if (entries.isEmpty()) {
				throw new IllegalArgumentException("split spec must contain at least one bucket");
			}
			if (entries.size() != weights.size()) {
				throw new IllegalArgumentException("split spec names and weights have different lengths");
			}
			double sum = 0.0;
			for (double w : weights) {
				if (w < 0.0) {
					throw new IllegalArgumentException("split weights must be non-negative (got " + w + ")");
				}
				sum += w;
			}
			if (sum == 0.0) {
				throw new IllegalArgumentException("split weights must sum to a positive value");
			}
			this.names = List.copyOf(entries);
			this.ratios = new LinkedHashMap<>();
			for (int i = 0; i < entries.size(); i++) {
				ratios.put(entries.get(i), weights.get(i) / sum);
			}
		}

		/**
		 * Parses a {@code train:val:test} (or similar named) ratio spec.
		 *
		 * @param raw    raw spec string
		 * @param names  ordered bucket names corresponding to the colon-separated weights
		 * @return parsed spec
		 */
		public static SplitSpec parse(String raw, List<String> names) {
			if (raw == null || raw.isBlank()) {
				throw new IllegalArgumentException("--split spec must not be empty");
			}
			String[] tokens = raw.split(":");
			if (tokens.length != names.size()) {
				throw new IllegalArgumentException(
						"--split expects " + names.size() + " weights (one per " + names + "), got "
								+ tokens.length);
			}
			List<Double> weights = new ArrayList<>(tokens.length);
			for (String token : tokens) {
				try {
					weights.add(Double.parseDouble(token.strip()));
				} catch (NumberFormatException ex) {
					throw new IllegalArgumentException(
							"--split weight '" + token + "' is not a number", ex);
				}
			}
			return new SplitSpec(names, weights);
		}

		/**
		 * Returns split names in declaration order.
		 *
		 * @return immutable name list
		 */
		public List<String> names() {
			return names;
		}

		/**
		 * Returns the normalised ratio for a named split.
		 *
		 * @param name split name
		 * @return ratio in {@code [0, 1]}
		 */
		public double ratio(String name) {
			Double value = ratios.get(name);
			if (value == null) {
				throw new IllegalArgumentException("unknown split: " + name);
			}
			return value;
		}
	}

}
