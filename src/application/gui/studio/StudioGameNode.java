package application.gui.studio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import chess.core.Move;

/**
 * Mutable node in the GUI v3 game tree.
 */
public final class StudioGameNode {

	/**
	 * Parent node, or null for the root.
	 */
	private final StudioGameNode parent;

	/**
	 * Mutable child list owned by this node.
	 */
	private final List<StudioGameNode> children = new ArrayList<>();

	/**
	 * Encoded move that reaches this node.
	 */
	private final short move;

	/**
	 * SAN label for the move.
	 */
	private final String san;

	/**
	 * UCI label for the move.
	 */
	private final String uci;

	/**
	 * FEN before the move was played.
	 */
	private final String beforeFen;

	/**
	 * FEN after the move was played.
	 */
	private final String afterFen;

	/**
	 * Numeric annotation glyphs attached to the node.
	 */
	private final List<Integer> nags = new ArrayList<>();

	/**
	 * Free-form comment attached to the node.
	 */
	private String comment = "";

	/**
	 * Constructor.
	 *
	 * @param parent parent node
	 * @param move encoded move
	 * @param san SAN label
	 * @param beforeFen FEN before the move
	 * @param afterFen FEN after the move
	 */
	StudioGameNode(StudioGameNode parent, short move, String san, String beforeFen, String afterFen) {
		this.parent = parent;
		this.move = move;
		this.san = san == null ? "" : san;
		this.uci = move == Move.NO_MOVE ? "" : Move.toString(move);
		this.beforeFen = beforeFen;
		this.afterFen = afterFen;
	}

	/**
	 * Creates and appends a child node.
	 *
	 * @param childMove child move
	 * @param childSan child SAN label
	 * @param childBeforeFen child before-FEN
	 * @param childAfterFen child after-FEN
	 * @return created child node
	 */
	StudioGameNode addChild(short childMove, String childSan, String childBeforeFen, String childAfterFen) {
		StudioGameNode child = new StudioGameNode(this, childMove, childSan, childBeforeFen, childAfterFen);
		children.add(child);
		return child;
	}

	/**
	 * Returns parent.
	 *
	 * @return parent node or null for root
	 */
	public StudioGameNode parent() {
		return parent;
	}

	/**
	 * Returns child nodes.
	 *
	 * @return immutable children
	 */
	public List<StudioGameNode> children() {
		return Collections.unmodifiableList(children);
	}

	/**
	 * Returns the mutable child list for tree maintenance.
	 *
	 * @return mutable children
	 */
	List<StudioGameNode> mutableChildren() {
		return children;
	}

	/**
	 * Returns encoded move.
	 *
	 * @return move or {@link Move#NO_MOVE}
	 */
	public short move() {
		return move;
	}

	/**
	 * Returns SAN.
	 *
	 * @return SAN text
	 */
	public String san() {
		return san;
	}

	/**
	 * Returns UCI.
	 *
	 * @return UCI text
	 */
	public String uci() {
		return uci;
	}

	/**
	 * Returns FEN before the move.
	 *
	 * @return before FEN
	 */
	public String beforeFen() {
		return beforeFen;
	}

	/**
	 * Returns FEN after the move.
	 *
	 * @return after FEN
	 */
	public String afterFen() {
		return afterFen;
	}

	/**
	 * Returns comment text.
	 *
	 * @return comment
	 */
	public String comment() {
		return comment;
	}

	/**
	 * Sets comment text.
	 *
	 * @param comment comment text
	 */
	public void setComment(String comment) {
		this.comment = comment == null ? "" : comment;
	}

	/**
	 * Returns NAGs.
	 *
	 * @return immutable NAG list
	 */
	public List<Integer> nags() {
		return Collections.unmodifiableList(nags);
	}

	/**
	 * Replaces NAGs.
	 *
	 * @param values NAG values
	 */
	public void setNags(List<Integer> values) {
		nags.clear();
		if (values != null) {
			nags.addAll(values);
		}
	}

	/**
	 * Adds a NAG.
	 *
	 * @param nag NAG value
	 */
	public void addNag(int nag) {
		nags.add(nag);
	}

	/**
	 * Formats the node label used by Swing list renderers.
	 *
	 * @return display label
	 */
	@Override
	public String toString() {
		return parent == null ? "Start" : san;
	}
}
