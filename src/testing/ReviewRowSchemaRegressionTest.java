package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.runMain;

import java.util.List;

import chess.review.Classifier;
import chess.review.Classifier.Request;
import chess.review.Classifier.Score;
import chess.review.Classifier.Thresholds;
import chess.review.ReviewRow;
import chess.review.ReviewRow.Assessment;
import chess.review.ReviewRow.Color;
import chess.review.ReviewRow.Eval;
import chess.review.ReviewRow.GameRef;
import chess.review.ReviewRow.MoveChoice;
import chess.review.ReviewRow.Ply;
import chess.review.ReviewRow.Repro;
import chess.review.ReviewRow.Tags;
import chess.review.ReviewRow.Wdl;
import chess.schema.Schemas;
import chess.schema.Violation;

/**
 * Golden-schema regression for {@link ReviewRow}.
 *
 * <p>This test pins the byte-stable row shape before the engine-dependent
 * router exists. Future review CLI code can assemble rows, but it must not
 * quietly rename fields, drop reproducibility metadata, or drift from the
 * published {@code crtk.review.ply.v1} schema.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ReviewRowSchemaRegressionTest {

	/**
	 * Expected compact JSON for the deterministic fixture row.
	 */
	private static final String GOLDEN = "{\"schemaVersion\":\"crtk.review.ply.v1\","
			+ "\"game_id\":\"lastnight-0003\",\"source\":\"pgn:last-night.pgn#3\","
			+ "\"event\":\"Rated Rapid game\",\"white\":\"AxelC\",\"black\":\"opponent\","
			+ "\"eco\":\"B23\",\"opening\":\"Sicilian, Closed\",\"ply\":45,"
			+ "\"move_number\":23,\"color\":\"white\","
			+ "\"fen\":\"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1\","
			+ "\"played_uci\":\"f1d3\",\"played_san\":\"Bd3\","
			+ "\"best_uci\":\"g1f3\",\"best_san\":\"Nf3\","
			+ "\"eval_before\":{\"cp\":100,\"mate\":null,\"wdl\":[700,200,100]},"
			+ "\"eval_after\":{\"cp\":-200,\"mate\":null,\"wdl\":[300,250,450]},"
			+ "\"cp_loss\":300,\"wdl_loss\":0.4,"
			+ "\"pv_best\":[\"g1f3\",\"b8c6\",\"...\"],"
			+ "\"second_best_cp\":28,\"is_only_move_position\":false,"
			+ "\"tags_before\":[\"FACT: phase=middlegame\",\"CAND: move=f1d3\"],"
			+ "\"tags_after\":[\"THREAT: type=hanging piece=bd3\","
			+ "\"TACTIC: motif=hanging_piece\"],"
			+ "\"tags_delta\":{\"added\":[\"THREAT: type=hanging piece=bd3\","
			+ "\"TACTIC: motif=hanging_piece\"],\"removed\":[\"CAND: move=f1d3\"],"
			+ "\"changed\":[]},\"game_phase\":\"middlegame\","
			+ "\"mistake_category\":\"blunder\",\"mistake_motif\":\"hanging_piece\","
			+ "\"severity\":0.7,\"recommended_action\":\"drill_puzzle\","
			+ "\"study_unit_id\":\"lastnight-0003.p45\","
			+ "\"repro\":{\"engine\":\"offline-alpha-beta\",\"protocol_path\":null,"
			+ "\"max_nodes\":25000,\"max_duration_ms\":1000,\"multipv\":2,"
			+ "\"threads\":1,\"hash\":64,\"search_mode\":\"offline\","
			+ "\"crtk_version\":\"test\",\"deterministic\":true}}";

	/**
	 * Utility class; prevent instantiation.
	 */
	private ReviewRowSchemaRegressionTest() {
		// utility
	}

	/**
	 * Runs every review-row schema check.
	 *
	 * @param args ignored command-line arguments
	 */
	public static void main(String[] args) {
		testGoldenJson();
		testGoldenValidatesAgainstPublishedSchema();
		testSchemaIsPublishedThroughCli();
		System.out.println("ReviewRowSchemaRegressionTest: all checks passed");
	}

	/**
	 * Verifies the row emitter stays byte-stable.
	 */
	private static void testGoldenJson() {
		assertEquals(GOLDEN, fixtureRow().toJson(), "review-row golden JSON");
	}

	/**
	 * Verifies the golden row validates against its registered schema.
	 */
	private static void testGoldenValidatesAgainstPublishedSchema() {
		List<Violation> violations = Schemas.load(ReviewRow.SCHEMA_VERSION).validate(GOLDEN);
		if (!violations.isEmpty()) {
			StringBuilder report = new StringBuilder("review row disagrees with schema:\n");
			for (Violation violation : violations) {
				report.append("  ").append(violation.render()).append('\n');
			}
			throw new AssertionError(report.toString());
		}
	}

	/**
	 * Verifies the schema is reachable through the public schema CLI.
	 */
	private static void testSchemaIsPublishedThroughCli() {
		assertTrue(Schemas.isKnown(ReviewRow.SCHEMA_VERSION),
				"schema registry knows " + ReviewRow.SCHEMA_VERSION);
		String list = runMain("schema", "list");
		assertTrue(list.contains(ReviewRow.SCHEMA_VERSION + "\n"),
				"schema list prints " + ReviewRow.SCHEMA_VERSION);
		String shown = runMain("schema", "show", ReviewRow.SCHEMA_VERSION);
		assertTrue(shown.contains("\"$id\": \"crtk.review.ply.v1\""),
				"schema show emits the review schema id");
	}

	/**
	 * Builds the deterministic row used by the golden assertion.
	 *
	 * @return fixture row
	 */
	private static ReviewRow fixtureRow() {
		Score before = Score.withWinShare(100, 0.70d);
		Score after = Score.withWinShare(-200, 0.30d);
		Classifier.Verdict verdict = Classifier.classify(new Request(
				before,
				after,
				Score.centipawns(28),
				Thresholds.classical(),
				false));
		return new ReviewRow(
				new GameRef("lastnight-0003", "pgn:last-night.pgn#3",
						"Rated Rapid game", "AxelC", "opponent", "B23", "Sicilian, Closed"),
				new Ply(45, 23, Color.WHITE,
						"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),
				new MoveChoice("f1d3", "Bd3", "g1f3", "Nf3",
						List.of("g1f3", "b8c6", "..."), 28),
				new Assessment(
						new Eval(100, null, new Wdl(700, 200, 100)),
						new Eval(-200, null, new Wdl(300, 250, 450)),
						verdict,
						"hanging_piece"),
				new Tags(
						List.of("FACT: phase=middlegame", "CAND: move=f1d3"),
						List.of("THREAT: type=hanging piece=bd3", "TACTIC: motif=hanging_piece"),
						List.of("THREAT: type=hanging piece=bd3", "TACTIC: motif=hanging_piece"),
						List.of("CAND: move=f1d3"),
						List.of()),
				"middlegame",
				"drill_puzzle",
				"lastnight-0003.p45",
				new Repro("offline-alpha-beta", null, 25_000L, 1_000L, 2, 1, 64,
						"offline", "test", true));
	}
}
