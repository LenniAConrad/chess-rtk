package chess.review;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import chess.core.Move;
import chess.core.Position;
import chess.struct.Record;
import chess.uci.Analysis;

/**
 * Converts reviewed mistake rows into study-unit rows and Record objects.
 *
 * <p>Keeping this conversion outside the CLI lets Workbench or future batch
 * commands reuse the same emission rule: only drillable mistake rows become
 * study units, and the first best-line move is applied through the shared chess
 * core to obtain the study position.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class StudyUnitFactory {

	/**
	 * Creation timestamp used for deterministic Record fixtures emitted from a
	 * review row.
	 */
	private static final long RECORD_CREATED = 0L;

	/**
	 * Utility class; prevent instantiation.
	 */
	private StudyUnitFactory() {
		// utility
	}

	/**
	 * Builds all study artifacts from review rows.
	 *
	 * @param rows review rows from one command run
	 * @return study units plus matching Record objects
	 */
	public static Output fromRows(List<ReviewRow> rows) {
		if (rows == null || rows.isEmpty()) {
			return new Output(List.of(), List.of());
		}
		List<StudyUnit> units = new ArrayList<>();
		List<Record> records = new ArrayList<>();
		for (ReviewRow row : rows) {
			StudyUnit unit = fromRow(row);
			if (unit == null) {
				continue;
			}
			units.add(unit);
			records.add(toRecord(unit));
		}
		return new Output(units, records);
	}

	/**
	 * Returns a copy of a row with a deterministic study-unit id, if it should emit.
	 *
	 * @param row source review row
	 * @return row with {@code study_unit_id}, or the original row
	 */
	public static ReviewRow withStudyUnitId(ReviewRow row) {
		Objects.requireNonNull(row, "row");
		if (!shouldEmit(row)) {
			return row;
		}
		String studyId = row.studyUnitId() == null ? id(row) : row.studyUnitId();
		return new ReviewRow(
				row.game(),
				row.ply(),
				row.move(),
				row.assessment(),
				row.tags(),
				row.gamePhase(),
				row.recommendedAction(),
				studyId,
				row.repro());
	}

	/**
	 * Returns whether the row should become a drill study unit.
	 *
	 * @param row review row
	 * @return true when the row is a drillable mistake
	 */
	public static boolean shouldEmit(ReviewRow row) {
		if (row == null || row.move().bestUci() == null || row.move().bestSan() == null) {
			return false;
		}
		return "drill_puzzle".equals(row.recommendedAction());
	}

	/**
	 * Builds one study unit from a review row.
	 *
	 * @param row review row
	 * @return study unit, or {@code null} when the row is not drillable
	 */
	private static StudyUnit fromRow(ReviewRow row) {
		if (!shouldEmit(row)) {
			return null;
		}
		Position parent = new Position(row.ply().fen());
		short bestMove = Move.parse(row.move().bestUci());
		if (!parent.isLegalMove(bestMove)) {
			throw new IllegalArgumentException("best move is not legal in review FEN: " + row.move().bestUci());
		}
		Position position = parent.copy().play(bestMove);
		String studyId = row.studyUnitId() == null ? id(row) : row.studyUnitId();
		return new StudyUnit(
				studyId,
				row.game(),
				row.ply(),
				parent.toString(),
				position.toString(),
				row.move().playedUci(),
				row.move().playedSan(),
				row.move().bestUci(),
				row.move().bestSan(),
				row.move().pvBest(),
				row.assessment().verdict().category().label(),
				row.assessment().mistakeMotif(),
				row.recommendedAction(),
				row.assessment().verdict().severity(),
				row.assessment().verdict().cpLoss(),
				row.assessment().verdict().wdlLoss(),
				difficulty(row),
				tags(row, studyId),
				row.repro());
	}

	/**
	 * Converts a study unit into the existing Record shape for downstream tooling.
	 *
	 * @param unit study unit
	 * @return record object
	 */
	private static Record toRecord(StudyUnit unit) {
		Analysis analysis = new Analysis();
		analysis.add(analysisLine(unit));
		return new Record(
				RECORD_CREATED,
				unit.repro().engine(),
				new Position(unit.parentFen()),
				new Position(unit.positionFen()),
				description(unit),
				unit.tags().toArray(new String[0]),
				analysis);
	}

	/**
	 * Builds the stable study-unit id for a row.
	 *
	 * @param row review row
	 * @return id
	 */
	private static String id(ReviewRow row) {
		return row.game().gameId() + ".p" + row.ply().index();
	}

	/**
	 * Returns a deterministic coarse difficulty label.
	 *
	 * @param row review row
	 * @return difficulty label
	 */
	private static String difficulty(ReviewRow row) {
		int cpLoss = row.assessment().verdict().cpLoss();
		if (cpLoss >= 600 || row.assessment().verdict().severity() >= 0.8d) {
			return "hard";
		}
		if (cpLoss >= 300 || row.assessment().verdict().severity() >= 0.5d) {
			return "medium";
		}
		return "easy";
	}

	/**
	 * Builds tags for both the study unit and exported Record.
	 *
	 * @param row review row
	 * @param studyId study-unit id
	 * @return stable tag list
	 */
	private static List<String> tags(ReviewRow row, String studyId) {
		List<String> out = new ArrayList<>();
		out.add("META: study_unit_id=" + studyId);
		out.add("META: review_game_id=" + row.game().gameId());
		out.add("META: review_source=" + row.game().source());
		out.add("META: review_ply=" + row.ply().index());
		out.add("META: played_uci=" + row.move().playedUci());
		out.add("META: best_uci=" + row.move().bestUci());
		out.add("META: mistake_category=" + row.assessment().verdict().category().label());
		out.add("META: difficulty=" + difficulty(row));
		appendUnique(out, row.tags().added());
		appendUnique(out, row.tags().after());
		return List.copyOf(out);
	}

	/**
	 * Appends unique non-blank values.
	 *
	 * @param out destination
	 * @param values source values
	 */
	private static void appendUnique(List<String> out, List<String> values) {
		if (values == null) {
			return;
		}
		for (String value : values) {
			if (value != null && !value.isBlank() && !out.contains(value)) {
				out.add(value);
			}
		}
	}

	/**
	 * Builds a record description.
	 *
	 * @param unit study unit
	 * @return description
	 */
	private static String description(StudyUnit unit) {
		return "Review " + unit.mistakeCategory()
				+ ": played " + unit.playedSan()
				+ "; study " + unit.bestSan();
	}

	/**
	 * Builds a minimal UCI info line pinning the best refutation move.
	 *
	 * @param unit study unit
	 * @return UCI info line
	 */
	private static String analysisLine(StudyUnit unit) {
		StringBuilder line = new StringBuilder(96)
				.append("info depth 1 multipv 1 score cp ")
				.append(Math.max(0, unit.cpLoss()))
				.append(" pv ");
		if (unit.refutationLine().isEmpty()) {
			line.append(unit.bestUci());
		} else {
			for (int i = 0; i < unit.refutationLine().size(); i++) {
				if (i > 0) {
					line.append(' ');
				}
				line.append(unit.refutationLine().get(i));
			}
		}
		return line.toString();
	}

	/**
	 * Emitted study artifacts.
	 *
	 * @param units schema-pinned study JSONL rows
	 * @param records Record objects for existing tooling
	 */
	public record Output(List<StudyUnit> units, List<Record> records) {

		/**
		 * Creates and normalizes output lists.
		 *
		 * @param units study units to merge
		 * @param records source records indexed by game
		 */
		public Output {
			units = units == null ? List.of() : List.copyOf(units);
			records = records == null ? List.of() : List.copyOf(records);
		}
	}
}
