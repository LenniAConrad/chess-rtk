package chess.review;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import utility.Json;

/**
 * Schema-pinned study unit emitted from reviewed mistake rows.
 *
 * <p>The study unit is the durable bridge between game review and downstream
 * drill/study consumers. It keeps review provenance, the pre-move parent FEN,
 * the best-line child FEN, and a small difficulty signal in one flat JSONL row.
 * The sibling {@link chess.struct.Record} export remains the re-import path for
 * existing record/puzzle tooling.</p>
 *
 * @param id stable study-unit id
 * @param game review game metadata
 * @param ply pre-move ply metadata
 * @param parentFen pre-move FEN
 * @param positionFen FEN after the recommended best move
 * @param playedUci move played in the reviewed game
 * @param playedSan SAN for the played move
 * @param bestUci recommended best move in UCI notation
 * @param bestSan SAN for the recommended best move
 * @param refutationLine capped best-line PV in UCI notation
 * @param mistakeCategory classifier category label
 * @param mistakeMotif grounded motif, or {@code null}
 * @param recommendedAction router action that caused emission
 * @param severity classifier severity
 * @param cpLoss centipawn loss from the review row
 * @param wdlLoss WDL loss from the review row, or {@code null}
 * @param difficulty deterministic coarse difficulty label
 * @param tags grounded tags and META linkage carried into the study unit
 * @param repro review reproducibility metadata
 * @since 2026
 * @author Lennart A. Conrad
 */
public record StudyUnit(
		String id,
		ReviewRow.GameRef game,
		ReviewRow.Ply ply,
		String parentFen,
		String positionFen,
		String playedUci,
		String playedSan,
		String bestUci,
		String bestSan,
		List<String> refutationLine,
		String mistakeCategory,
		String mistakeMotif,
		String recommendedAction,
		double severity,
		int cpLoss,
		Double wdlLoss,
		String difficulty,
		List<String> tags,
		ReviewRow.Repro repro) {

	/**
	 * Published JSON Schema id for study-unit JSONL rows.
	 */
	public static final String SCHEMA_VERSION = "crtk.review.study_unit.v1";

	/**
	 * Creates and validates one study unit.
	 */
	public StudyUnit {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(game, "game");
		Objects.requireNonNull(ply, "ply");
		Objects.requireNonNull(parentFen, "parentFen");
		Objects.requireNonNull(positionFen, "positionFen");
		Objects.requireNonNull(playedUci, "playedUci");
		Objects.requireNonNull(playedSan, "playedSan");
		Objects.requireNonNull(bestUci, "bestUci");
		Objects.requireNonNull(bestSan, "bestSan");
		refutationLine = immutableStrings(refutationLine);
		Objects.requireNonNull(mistakeCategory, "mistakeCategory");
		Objects.requireNonNull(recommendedAction, "recommendedAction");
		if (!Double.isFinite(severity)) {
			throw new IllegalArgumentException("severity must be finite");
		}
		if (cpLoss < 0) {
			throw new IllegalArgumentException("cpLoss must be non-negative");
		}
		if (wdlLoss != null && !Double.isFinite(wdlLoss.doubleValue())) {
			throw new IllegalArgumentException("wdlLoss must be finite");
		}
		Objects.requireNonNull(difficulty, "difficulty");
		tags = immutableStrings(tags);
		Objects.requireNonNull(repro, "repro");
	}

	/**
	 * Emits the compact, deterministic JSON object for this study unit.
	 *
	 * @return one JSON object suitable for JSONL output
	 */
	public String toJson() {
		StringBuilder sb = new StringBuilder(1024);
		JsonObject out = new JsonObject(sb);
		out.string("schemaVersion", SCHEMA_VERSION);
		out.string("id", id);
		out.string("game_id", game.gameId());
		out.string("source", game.source());
		out.string("event", game.event());
		out.string("white", game.white());
		out.string("black", game.black());
		out.number("ply", ply.index());
		out.number("move_number", ply.moveNumber());
		out.string("color", ply.color().label());
		out.string("parent_fen", parentFen);
		out.string("position_fen", positionFen);
		out.string("played_uci", playedUci);
		out.string("played_san", playedSan);
		out.string("best_uci", bestUci);
		out.string("best_san", bestSan);
		out.raw("refutation_line", stringArray(refutationLine));
		out.string("mistake_category", mistakeCategory);
		out.string("mistake_motif", mistakeMotif);
		out.string("recommended_action", recommendedAction);
		out.raw("severity", jsonDouble(severity));
		out.number("cp_loss", cpLoss);
		out.raw("wdl_loss", wdlLoss == null ? "null" : jsonDouble(wdlLoss.doubleValue()));
		out.string("difficulty", difficulty);
		out.raw("tags", stringArray(tags));
		out.raw("repro", repro.toJson());
		out.end();
		return sb.toString();
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
	 * Emits a finite double with stable rounding.
	 *
	 * @param value finite double value
	 * @return JSON number
	 */
	private static String jsonDouble(double value) {
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
			this.sb.append('{');
		}

		/**
		 * Emits a string field.
		 *
		 * @param name field name
		 * @param value field value, or {@code null}
		 */
		private void string(String name, String value) {
			field(name);
			sb.append('"').append(Json.esc(value)).append('"');
		}

		/**
		 * Emits a long-valued number field.
		 *
		 * @param name field name
		 * @param value number value
		 */
		private void number(String name, long value) {
			field(name);
			sb.append(value);
		}

		/**
		 * Emits a raw JSON field.
		 *
		 * @param name field name
		 * @param rawJson already-encoded JSON value
		 */
		private void raw(String name, String rawJson) {
			field(name);
			sb.append(rawJson);
		}

		/**
		 * Opens a named field and handles comma insertion.
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
		 * Closes the JSON object.
		 */
		private void end() {
			sb.append('}');
		}
	}
}
