package chess.book.model;

import chess.core.Fen;
import utility.JsonFields;

/**
 * Represents one puzzle entry inside a chess book.
 *
 * <p>
 * The native renderer uses one starting position in FEN and one SAN solution
 * line for each puzzle entry.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Element implements Comparable<Element> {

	/**
	 * Starting position in Forsyth-Edwards Notation.
	 */
	private String position = "";

	/**
	 * Solution line in SAN text.
	 */
	private String moves = "";

	/**
	 * Creates an empty element.
	 */
	public Element() {
		// default
	}

	/**
	 * Creates an element with both content fields populated.
	 *
	 * @param position starting FEN position
	 * @param moves    SAN solution line
	 */
	public Element(String position, String moves) {
		this.position = Fen.normalize(position);
		this.moves = moves == null ? "" : moves.trim();
	}

	/**
	 * Returns the starting position.
	 *
	 * @return FEN string, never null
	 */
	public String getPosition() {
		return position;
	}

	/**
	 * Sets the starting position.
	 *
	 * @param position FEN string
	 * @return this element for chaining
	 */
	public Element setPosition(String position) {
		this.position = Fen.normalize(position);
		return this;
	}

	/**
	 * Returns the SAN solution line.
	 *
	 * @return SAN text, never null
	 */
	public String getMoves() {
		return moves;
	}

	/**
	 * Sets the SAN solution line.
	 *
	 * @param moves SAN text
	 * @return this element for chaining
	 */
	public Element setMoves(String moves) {
		this.moves = moves == null ? "" : moves.trim();
		return this;
	}

	/**
	 * Creates a deep copy of the element.
	 *
	 * @return copied element
	 */
	public Element copy() {
		return new Element(position, moves);
	}

	/**
	 * Parses one book element from a JSON object.
	 *
	 * @param json raw JSON object string
	 * @return parsed element
	 */
	public static Element fromJson(String json) {
		return new Element(
				JsonFields.stringField(json, "position"),
				JsonFields.stringField(json, "moves"));
	}

	/**
	 * Compares two elements by their starting position.
	 *
	 * @param other other element to compare
	 * @return comparison result by FEN text
	 */
	@Override
	public int compareTo(Element other) {
		if (other == null) {
			return 1;
		}
		return position.compareTo(other.position);
	}

	/**
	 * Checks whether another object represents the same starting position.
	 *
	 * @param obj object to compare
	 * @return true when both elements sort to the same position
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Element other)) {
			return false;
		}
		return position.equals(other.position);
	}

	/**
	 * Returns the hash code matching the position-based comparison contract.
	 *
	 * @return position hash code
	 */
	@Override
	public int hashCode() {
		return position.hashCode();
	}

	/**
	 * Returns a debug-friendly JSON-like string.
	 *
	 * @return compact string representation
	 */
	@Override
	public String toString() {
		return "{\"position\":\"" + utility.Json.esc(position) + "\",\"moves\":\"" + utility.Json.esc(moves) + "\"}";
	}
}
