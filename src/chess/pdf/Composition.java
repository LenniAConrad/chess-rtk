package chess.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import chess.core.Fen;
import chess.core.Position;

/**
 * Chess composition model inspired by the older LaTeX-backed package, but meant
 * for direct PDF generation.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Composition {

	/**
	 * Optional time label printed in composition metadata.
	 */
	private String time = "";

	/**
	 * Composition title.
	 */
	private String title = "";

	/**
	 * Introductory description text.
	 */
	private String description = "";

	/**
	 * Analysis paragraph text.
	 */
	private String analysis = "";

	/**
	 * General comment text.
	 */
	private String comment = "";

	/**
	 * First hint text.
	 */
	private String hintLevel1 = "";

	/**
	 * Second hint text.
	 */
	private String hintLevel2 = "";

	/**
	 * Third hint text.
	 */
	private String hintLevel3 = "";

	/**
	 * Fourth hint text.
	 */
	private String hintLevel4 = "";

	/**
	 * Optional composition identifier.
	 */
	private String id = "";

	/**
	 * Captions for figure moves.
	 */
	private final List<String> figureMovesAlgebraic = new ArrayList<>();

	/**
	 * Secondary text for figure moves.
	 */
	private final List<String> figureMovesDetail = new ArrayList<>();

	/**
	 * FEN strings for rendered figures.
	 */
	private final List<String> figureFens = new ArrayList<>();

	/**
	 * Optional UCI arrows for rendered figures.
	 */
	private final List<String> figureArrows = new ArrayList<>();

	/**
	 * Sets the title.
	 *
	 * @param title title text
	 * @return this composition
	 */
	public Composition setTitle(String title) {
		this.title = normalize(title);
		return this;
	}

	/**
	 * Sets the description.
	 *
	 * @param description description text
	 * @return this composition
	 */
	public Composition setDescription(String description) {
		this.description = normalize(description);
		return this;
	}

	/**
	 * Sets the analysis.
	 *
	 * @param analysis analysis text
	 * @return this composition
	 */
	public Composition setAnalysis(String analysis) {
		this.analysis = normalize(analysis);
		return this;
	}

	/**
	 * Sets the comment.
	 *
	 * @param comment comment text
	 * @return this composition
	 */
	public Composition setComment(String comment) {
		this.comment = normalize(comment);
		return this;
	}

	/**
	 * Sets hint level 1.
	 *
	 * @param hintLevel1 hint text
	 * @return this composition
	 */
	public Composition setHintLevel1(String hintLevel1) {
		this.hintLevel1 = normalize(hintLevel1);
		return this;
	}

	/**
	 * Sets hint level 2.
	 *
	 * @param hintLevel2 hint text
	 * @return this composition
	 */
	public Composition setHintLevel2(String hintLevel2) {
		this.hintLevel2 = normalize(hintLevel2);
		return this;
	}

	/**
	 * Sets hint level 3.
	 *
	 * @param hintLevel3 hint text
	 * @return this composition
	 */
	public Composition setHintLevel3(String hintLevel3) {
		this.hintLevel3 = normalize(hintLevel3);
		return this;
	}

	/**
	 * Sets hint level 4.
	 *
	 * @param hintLevel4 hint text
	 * @return this composition
	 */
	public Composition setHintLevel4(String hintLevel4) {
		this.hintLevel4 = normalize(hintLevel4);
		return this;
	}

	/**
	 * Sets the composition identifier.
	 *
	 * @param id identifier text
	 * @return this composition
	 */
	public Composition setId(String id) {
		this.id = normalize(id);
		return this;
	}

	/**
	 * Sets the time label.
	 *
	 * @param time time text
	 * @return this composition
	 */
	public Composition setTime(String time) {
		this.time = normalize(time);
		return this;
	}

	/**
	 * Replaces the figure caption list.
	 *
	 * @param values caption list
	 * @return this composition
	 */
	public Composition setFigureMovesAlgebraic(List<String> values) {
		return replace(figureMovesAlgebraic, values);
	}

	/**
	 * Replaces the figure detail list.
	 *
	 * @param values detail list
	 * @return this composition
	 */
	public Composition setFigureMovesDetail(List<String> values) {
		return replace(figureMovesDetail, values);
	}

	/**
	 * Replaces the figure FEN list.
	 *
	 * @param values FEN list
	 * @return this composition
	 */
	public Composition setFigureFens(List<String> values) {
		return replaceFens(values);
	}

	/**
	 * Replaces the figure arrow list.
	 *
	 * @param values arrow list
	 * @return this composition
	 */
	public Composition setFigureArrows(List<String> values) {
		return replace(figureArrows, values);
	}

	/**
	 * Appends one diagram entry using a FEN string.
	 *
	 * @param fen            diagram FEN
	 * @param algebraicMove  caption text
	 * @param detail         secondary text
	 * @param arrowUci       optional arrow in UCI form such as {@code e2e4}
	 * @return this composition
	 */
	public Composition addFigure(String fen, String algebraicMove, String detail, String arrowUci) {
		figureFens.add(requiredFen(fen));
		figureMovesAlgebraic.add(normalize(algebraicMove));
		figureMovesDetail.add(normalize(detail));
		figureArrows.add(normalize(arrowUci));
		return this;
	}

	/**
	 * Appends one diagram entry using a {@link Position}.
	 *
	 * @param position       position to serialize
	 * @param algebraicMove  caption text
	 * @param detail         secondary text
	 * @param arrowUci       optional arrow in UCI form such as {@code e2e4}
	 * @return this composition
	 */
	public Composition addFigure(Position position, String algebraicMove, String detail, String arrowUci) {
		if (position == null) {
			throw new IllegalArgumentException("position cannot be null");
		}
		return addFigure(position.toString(), algebraicMove, detail, arrowUci);
	}

	/**
	 * Returns the title.
	 *
	 * @return title text
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Returns the description.
	 *
	 * @return description text
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Returns the analysis.
	 *
	 * @return analysis text
	 */
	public String getAnalysis() {
		return analysis;
	}

	/**
	 * Returns the comment.
	 *
	 * @return comment text
	 */
	public String getComment() {
		return comment;
	}

	/**
	 * Returns hint level 1.
	 *
	 * @return hint text
	 */
	public String getHintLevel1() {
		return hintLevel1;
	}

	/**
	 * Returns hint level 2.
	 *
	 * @return hint text
	 */
	public String getHintLevel2() {
		return hintLevel2;
	}

	/**
	 * Returns hint level 3.
	 *
	 * @return hint text
	 */
	public String getHintLevel3() {
		return hintLevel3;
	}

	/**
	 * Returns hint level 4.
	 *
	 * @return hint text
	 */
	public String getHintLevel4() {
		return hintLevel4;
	}

	/**
	 * Returns the identifier text.
	 *
	 * @return identifier
	 */
	public String getId() {
		return id;
	}

	/**
	 * Returns the time label.
	 *
	 * @return time label
	 */
	public String getTime() {
		return time;
	}

	/**
	 * Returns figure captions.
	 *
	 * @return unmodifiable caption list
	 */
	public List<String> getFigureMovesAlgebraic() {
		return Collections.unmodifiableList(figureMovesAlgebraic);
	}

	/**
	 * Returns figure detail strings.
	 *
	 * @return unmodifiable detail list
	 */
	public List<String> getFigureMovesDetail() {
		return Collections.unmodifiableList(figureMovesDetail);
	}

	/**
	 * Returns figure FEN strings.
	 *
	 * @return unmodifiable FEN list
	 */
	public List<String> getFigureFens() {
		return Collections.unmodifiableList(figureFens);
	}

	/**
	 * Returns figure arrow strings.
	 *
	 * @return unmodifiable arrow list
	 */
	public List<String> getFigureArrows() {
		return Collections.unmodifiableList(figureArrows);
	}

	/**
	 * Replaces one mutable string list with normalized source values.
	 *
	 * @param target target list to mutate
	 * @param source source values
	 * @return this composition
	 */
	private Composition replace(List<String> target, List<String> source) {
		target.clear();
		if (source == null) {
			return this;
		}
		for (String value : source) {
			target.add(normalize(value));
		}
		return this;
	}

	/**
	 * Replaces the FEN list with core-normalized values.
	 *
	 * @param source source FEN values
	 * @return this composition
	 */
	private Composition replaceFens(List<String> source) {
		figureFens.clear();
		if (source == null) {
			return this;
		}
		for (String value : source) {
			figureFens.add(Fen.normalize(value));
		}
		return this;
	}

	/**
	 * Normalizes nullable text fields.
	 *
	 * @param value source value
	 * @return trimmed non-null value
	 */
	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	/**
	 * Normalizes and validates a required FEN string.
	 *
	 * @param fen source FEN
	 * @return normalized FEN
	 */
	private String requiredFen(String fen) {
		String normalized = Fen.normalize(fen);
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("fen cannot be blank");
		}
		return normalized;
	}
}
