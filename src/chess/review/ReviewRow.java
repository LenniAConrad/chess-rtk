package chess.review;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import utility.Json;

/**
 * Byte-stable JSONL row contract for one reviewed ply.
 *
 * <p>The row is intentionally engine-free: callers provide already-computed
 * engine scores, classifier verdicts, tags, and reproducibility metadata, and
 * this class owns only validation plus deterministic JSON emission. Keeping the
 * output shape here gives CLI, Workbench, and tests one shared contract.</p>
 *
 * @param game game-level provenance and PGN tags
 * @param ply pre-move ply identity
 * @param move played and recommended moves
 * @param assessment engine scores plus mistake verdict
 * @param tags pre/post tag snapshots and their delta
 * @param gamePhase coarse game phase label, or {@code null}
 * @param recommendedAction action suggested by the router
 * @param studyUnitId emitted study unit id, or {@code null}
 * @param repro reproducibility metadata for the analysis pass
 * @since 2026
 * @author Lennart A. Conrad
 */
public record ReviewRow(
		GameRef game,
		Ply ply,
		MoveChoice move,
		Assessment assessment,
		Tags tags,
		String gamePhase,
		String recommendedAction,
		String studyUnitId,
		Repro repro) {

	/**
	 * Published JSON Schema id for review JSONL rows.
	 */
	public static final String SCHEMA_VERSION = "crtk.review.ply.v1";

	/**
	 * Creates and validates a row.
	 *
	 * @param game game identifier or metadata
	 * @param ply zero-based ply index
	 * @param move encoded chess move
	 * @param assessment move assessment payload
	 * @param tags tag collection to update
	 * @param gamePhase classified game phase
	 * @param recommendedAction training recommendation
	 * @param studyUnitId linked study-unit identifier
	 * @param repro reproduction command or data
	 */
	public ReviewRow {
		Objects.requireNonNull(game, "game");
		Objects.requireNonNull(ply, "ply");
		Objects.requireNonNull(move, "move");
		Objects.requireNonNull(assessment, "assessment");
		tags = tags == null ? Tags.empty() : tags;
		repro = Objects.requireNonNull(repro, "repro");
		recommendedAction = recommendedAction == null ? "none" : recommendedAction;
	}

	/**
	 * Emits the compact, deterministic JSON object for this row.
	 *
	 * @return one JSON object suitable for JSONL output
	 */
	public String toJson() {
		StringBuilder sb = new StringBuilder(1024);
		JsonObject out = new JsonObject(sb);
		out.string("schemaVersion", SCHEMA_VERSION);
		out.string("game_id", game.gameId());
		out.string("source", game.source());
		out.string("event", game.event());
		out.string("white", game.white());
		out.string("black", game.black());
		out.string("eco", game.eco());
		out.string("opening", game.opening());
		out.number("ply", ply.index());
		out.number("move_number", ply.moveNumber());
		out.string("color", ply.color().label());
		out.string("fen", ply.fen());
		out.string("played_uci", move.playedUci());
		out.string("played_san", move.playedSan());
		out.string("best_uci", move.bestUci());
		out.string("best_san", move.bestSan());
		out.raw("eval_before", assessment.evalBefore().toJson());
		out.raw("eval_after", assessment.evalAfter().toJson());
		out.number("cp_loss", assessment.verdict().cpLoss());
		out.raw("wdl_loss", jsonDoubleOrNull(assessment.verdict().wdlLoss()));
		out.raw("pv_best", stringArray(move.pvBest()));
		out.raw("second_best_cp", jsonIntegerOrNull(move.secondBestCp()));
		out.bool("is_only_move_position", assessment.verdict().onlyMovePosition());
		out.raw("tags_before", stringArray(tags.before()));
		out.raw("tags_after", stringArray(tags.after()));
		out.raw("tags_delta", tags.deltaJson());
		out.string("game_phase", gamePhase);
		out.string("mistake_category", assessment.verdict().category().label());
		out.string("mistake_motif", assessment.mistakeMotif());
		out.raw("severity", jsonDouble(assessment.verdict().severity()));
		out.string("recommended_action", recommendedAction);
		out.string("study_unit_id", studyUnitId);
		out.raw("repro", repro.toJson());
		out.end();
		return sb.toString();
	}

	/**
	 * Game-level identity and source metadata.
	 *
	 * @param gameId stable game id
	 * @param source source provenance label
	 * @param event PGN Event tag, or {@code null}
	 * @param white PGN White tag, or {@code null}
	 * @param black PGN Black tag, or {@code null}
	 * @param eco ECO code, or {@code null}
	 * @param opening opening name, or {@code null}
	 */
	public record GameRef(
			String gameId,
			String source,
			String event,
			String white,
			String black,
			String eco,
			String opening) {

		/**
		 * Creates and validates game metadata.
		 *
		 * @param gameId stable game identifier
		 * @param source source buffer or source-side selector
		 * @param event PGN event name
		 * @param white White player name or side flag
		 * @param black Black player name
		 * @param eco ECO opening code
		 * @param opening opening position or line
		 */
		public GameRef {
			Objects.requireNonNull(gameId, "gameId");
			Objects.requireNonNull(source, "source");
		}
	}

	/**
	 * Pre-move ply identity.
	 *
	 * @param index zero-based ply index from the game start
	 * @param moveNumber human move number
	 * @param color side to move
	 * @param fen pre-move FEN
	 */
	public record Ply(int index, int moveNumber, Color color, String fen) {

		/**
		 * Creates and validates ply metadata.
		 *
		 * @param index zero-based index
		 * @param moveNumber fullmove number
		 * @param color display color or side color
		 * @param fen FEN string
		 */
		public Ply {
			if (index < 0) {
				throw new IllegalArgumentException("ply index must be non-negative");
			}
			if (moveNumber <= 0) {
				throw new IllegalArgumentException("move number must be positive");
			}
			Objects.requireNonNull(color, "color");
			Objects.requireNonNull(fen, "fen");
		}
	}

	/**
	 * Side-to-move label.
	 */
	public enum Color {

		/**
		 * White to move.
		 */
		WHITE("white"),

		/**
		 * Black to move.
		 */
		BLACK("black");

		/**
		 * Stable JSON label.
		 */
		private final String label;

		/**
		 * Creates a color label.
		 *
		 * @param label JSON label
		 */
		Color(String label) {
			this.label = label;
		}

		/**
		 * Returns the stable JSON label.
		 *
		 * @return lower-case color label
		 */
		public String label() {
			return label;
		}
	}

	/**
	 * Played move, recommended move, and PV context.
	 *
	 * @param playedUci played move in UCI notation
	 * @param playedSan played move in SAN notation
	 * @param bestUci recommended best move in UCI notation, or {@code null}
	 * @param bestSan recommended best move in SAN notation, or {@code null}
	 * @param pvBest capped best-line PV in UCI notation
	 * @param secondBestCp multipv-2 score in centipawns, or {@code null}
	 */
	public record MoveChoice(
			String playedUci,
			String playedSan,
			String bestUci,
			String bestSan,
			List<String> pvBest,
			Integer secondBestCp) {

		/**
		 * Creates and validates move metadata.
		 *
		 * @param playedUci played move in UCI notation
		 * @param playedSan played move in SAN
		 * @param bestUci best move in UCI notation
		 * @param bestSan best move in SAN
		 * @param pvBest principal variation for the best move
		 * @param secondBestCp second-best move score in centipawns
		 */
		public MoveChoice {
			Objects.requireNonNull(playedUci, "playedUci");
			Objects.requireNonNull(playedSan, "playedSan");
			pvBest = immutableStrings(pvBest);
		}
	}

	/**
	 * Engine score fields used by the row contract.
	 *
	 * @param cp centipawn score from the mover's perspective, or {@code null}
	 * @param mate mate score from the mover's perspective, or {@code null}
	 * @param wdl optional WDL tuple from the mover's perspective
	 */
	public record Eval(Integer cp, Integer mate, Wdl wdl) {

		/**
		 * Creates and validates an eval block.
		 *
		 * @param cp centipawn score
		 * @param mate mate score, or null when absent
		 * @param wdl win-draw-loss probabilities
		 */
		public Eval {
			if (cp == null && mate == null) {
				throw new IllegalArgumentException("eval must include cp or mate");
			}
		}

		/**
		 * Emits this eval block as deterministic JSON.
		 *
		 * @return compact JSON object
		 */
		public String toJson() {
			StringBuilder sb = new StringBuilder(64);
			JsonObject out = new JsonObject(sb);
			out.raw("cp", jsonIntegerOrNull(cp));
			out.raw("mate", jsonIntegerOrNull(mate));
			out.raw("wdl", wdl == null ? "null" : wdl.toJson());
			out.end();
			return sb.toString();
		}
	}

	/**
	 * WDL tuple from the mover's perspective.
	 *
	 * @param win win count or share bucket
	 * @param draw draw count or share bucket
	 * @param loss loss count or share bucket
	 */
	public record Wdl(int win, int draw, int loss) {

		/**
		 * Creates and validates a WDL tuple.
		 *
		 * @param win win probability or win count
		 * @param draw draw probability or draw count
		 * @param loss loss probability or loss count
		 */
		public Wdl {
			if (win < 0 || draw < 0 || loss < 0) {
				throw new IllegalArgumentException("WDL values must be non-negative");
			}
		}

		/**
		 * Emits the tuple as the published compact array shape.
		 *
		 * @return compact JSON array
		 */
		public String toJson() {
			return "[" + win + "," + draw + "," + loss + "]";
		}
	}

	/**
	 * Assessment fields generated from engine scores and classifier verdict.
	 *
	 * @param evalBefore best-line score before the played move
	 * @param evalAfter score after the played move
	 * @param verdict mistake classifier verdict
	 * @param mistakeMotif grounded mistake motif, or {@code null}
	 */
	public record Assessment(
			Eval evalBefore,
			Eval evalAfter,
			Classifier.Verdict verdict,
			String mistakeMotif) {

		/**
		 * Creates and validates assessment metadata.
		 *
		 * @param evalBefore evaluation before the move
		 * @param evalAfter evaluation after the move
		 * @param verdict classification verdict
		 * @param mistakeMotif mistake motif label
		 */
		public Assessment {
			Objects.requireNonNull(evalBefore, "evalBefore");
			Objects.requireNonNull(evalAfter, "evalAfter");
			Objects.requireNonNull(verdict, "verdict");
		}
	}

	/**
	 * Tag snapshots and deterministic deltas.
	 *
	 * @param before tags generated on the pre-move FEN
	 * @param after tags generated after the played move
	 * @param added tags present only after the move
	 * @param removed tags present only before the move
	 * @param changed changed tag identities
	 */
	public record Tags(
			List<String> before,
			List<String> after,
			List<String> added,
			List<String> removed,
			List<String> changed) {

		/**
		 * Creates and normalizes tag lists.
		 *
		 * @param before position before the move
		 * @param after position after the move
		 * @param added added tag names
		 * @param removed removed tag names
		 * @param changed changed tag names
		 */
		public Tags {
			before = immutableStrings(before);
			after = immutableStrings(after);
			added = immutableStrings(added);
			removed = immutableStrings(removed);
			changed = immutableStrings(changed);
		}

		/**
		 * Returns an empty tag block.
		 *
		 * @return empty tag snapshots and delta lists
		 */
		public static Tags empty() {
			return new Tags(List.of(), List.of(), List.of(), List.of(), List.of());
		}

		/**
		 * Emits the tag delta object.
		 *
		 * @return compact JSON object
		 */
		public String deltaJson() {
			StringBuilder sb = new StringBuilder(128);
			JsonObject out = new JsonObject(sb);
			out.raw("added", stringArray(added));
			out.raw("removed", stringArray(removed));
			out.raw("changed", stringArray(changed));
			out.end();
			return sb.toString();
		}
	}

	/**
	 * Reproducibility metadata for one analysis pass.
	 *
	 * @param engine engine name or offline backend label
	 * @param protocolPath protocol TOML path, or {@code null} for offline
	 * @param maxNodes node budget
	 * @param maxDurationMillis watchdog duration budget in milliseconds
	 * @param multipv requested MultiPV count
	 * @param threads requested engine threads
	 * @param hash requested UCI hash size in MB
	 * @param searchMode review search mode
	 * @param crtkVersion CRTK version string
	 * @param deterministic whether identical inputs should reproduce byte-identical rows
	 */
	public record Repro(
			String engine,
			String protocolPath,
			long maxNodes,
			long maxDurationMillis,
			int multipv,
			int threads,
			int hash,
			String searchMode,
			String crtkVersion,
			boolean deterministic) {

		/**
		 * Creates and validates reproducibility metadata.
		 *
		 * @param engine engine identifier
		 * @param protocolPath external engine protocol path
		 * @param maxNodes maximum search node budget
		 * @param maxDurationMillis maximum search duration in milliseconds
		 * @param multipv number of principal variations to request
		 * @param threads engine thread count
		 * @param hash engine hash size in megabytes
		 * @param searchMode engine search mode label
		 * @param crtkVersion ChessRTK version string
		 * @param deterministic whether deterministic engine mode was used
		 */
		public Repro {
			Objects.requireNonNull(engine, "engine");
			if (maxNodes < 0L || maxDurationMillis < 0L) {
				throw new IllegalArgumentException("analysis budgets must be non-negative");
			}
			if (multipv <= 0 || threads <= 0 || hash < 0) {
				throw new IllegalArgumentException("multipv/threads must be positive and hash non-negative");
			}
			Objects.requireNonNull(searchMode, "searchMode");
			Objects.requireNonNull(crtkVersion, "crtkVersion");
		}

		/**
		 * Emits this reproducibility block as deterministic JSON.
		 *
		 * @return compact JSON object
		 */
		public String toJson() {
			StringBuilder sb = new StringBuilder(192);
			JsonObject out = new JsonObject(sb);
			out.string("engine", engine);
			out.string("protocol_path", protocolPath);
			out.number("max_nodes", maxNodes);
			out.number("max_duration_ms", maxDurationMillis);
			out.number("multipv", multipv);
			out.number("threads", threads);
			out.number("hash", hash);
			out.string("search_mode", searchMode);
			out.string("crtk_version", crtkVersion);
			out.bool("deterministic", deterministic);
			out.end();
			return sb.toString();
		}
	}

	/**
	 * Returns an immutable string list, treating {@code null} as empty.
	 *
	 * @param values source list
	 * @return immutable string list
	 */
	private static List<String> immutableStrings(List<String> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		for (String value : values) {
			Objects.requireNonNull(value, "list values");
		}
		return List.copyOf(values);
	}

	/**
	 * Emits a nullable integer JSON value.
	 *
	 * @param value integer value or {@code null}
	 * @return JSON number or {@code null}
	 */
	private static String jsonIntegerOrNull(Integer value) {
		return value == null ? "null" : value.toString();
	}

	/**
	 * Emits a nullable double JSON value.
	 *
	 * @param value double value or {@code null}
	 * @return JSON number or {@code null}
	 */
	private static String jsonDoubleOrNull(Double value) {
		return value == null ? "null" : jsonDouble(value.doubleValue());
	}

	/**
	 * Emits a finite double with stable rounding and no binary tail noise.
	 *
	 * @param value finite double value
	 * @return JSON number
	 */
	private static String jsonDouble(double value) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException("JSON double must be finite");
		}
		return BigDecimal.valueOf(value)
				.setScale(6, RoundingMode.HALF_UP)
				.stripTrailingZeros()
				.toPlainString();
	}

	/**
	 * Emits a string array.
	 *
	 * @param values string values
	 * @return compact JSON array
	 */
	private static String stringArray(List<String> values) {
		StringBuilder sb = new StringBuilder(32);
		sb.append('[');
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append('"').append(Json.esc(values.get(i))).append('"');
		}
		sb.append(']');
		return sb.toString();
	}

	/**
	 * Small deterministic object writer for this row contract.
	 */
	private static final class JsonObject {

		/**
		 * Target builder.
		 */
		private final StringBuilder sb;

		/**
		 * Tracks comma insertion.
		 */
		private boolean first = true;

		/**
		 * Starts a JSON object.
		 *
		 * @param sb target builder
		 */
		private JsonObject(StringBuilder sb) {
			this.sb = sb;
			sb.append('{');
		}

		/**
		 * Emits a string or null field.
		 *
		 * @param name field name
		 * @param value string value or {@code null}
		 */
		private void string(String name, String value) {
			field(name);
			if (value == null) {
				sb.append("null");
			} else {
				sb.append('"').append(Json.esc(value)).append('"');
			}
		}

		/**
		 * Emits a long number field.
		 *
		 * @param name field name
		 * @param value numeric value
		 */
		private void number(String name, long value) {
			field(name);
			sb.append(value);
		}

		/**
		 * Emits a boolean field.
		 *
		 * @param name field name
		 * @param value boolean value
		 */
		private void bool(String name, boolean value) {
			field(name);
			sb.append(value);
		}

		/**
		 * Emits a preformatted raw JSON field.
		 *
		 * @param name field name
		 * @param rawJson valid JSON value text
		 */
		private void raw(String name, String rawJson) {
			field(name);
			sb.append(rawJson);
		}

		/**
		 * Emits a field name and leading comma if needed.
		 *
		 * @param name field name
		 */
		private void field(String name) {
			if (!first) {
				sb.append(',');
			}
			first = false;
			sb.append('"').append(name).append('"').append(':');
		}

		/**
		 * Ends the JSON object.
		 */
		private void end() {
			sb.append('}');
		}
	}
}
