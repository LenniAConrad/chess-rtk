package application.gui.window;

import java.util.ArrayList;
import java.util.List;

import chess.core.Move;
import chess.core.Position;
import chess.uci.Chances;
import chess.uci.Evaluation;

/**
 * Internal PGN tree node with position snapshot.
 *
 * Stores the SAN, resulting position, variations, comments, and analysis metadata so the UI can walk lines and show engine data for each ply.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class PgnNode {
	/**
	 * SAN string for this move.
	 */
	final String san;
	/**
	 * Parent node reference.
	 */
	final PgnNode parent;
	/**
	 * Position snapshot after the move.
	 */
	final Position positionAfter;
	/**
	 * Numerical move code.
	 */
	final short move;
	/**
	 * Ply index since the root.
	 */
	final int ply;
	/**
	 * Next node on the mainline.
	 */
	PgnNode mainNext;
	/**
	 * Variation roots branching from this node.
	 */
	final List<PgnNode> variations = new ArrayList<>();
	/**
	 * Optional comment for the node.
	 */
	String comment;
	/**
	 * Numeric annotation glyph code.
	 */
	int nag;
	/**
	 * Cached evaluation metadata from engines.
	 */
	Evaluation analysisEval;
	/**
	 * Cached WDL chances.
	 */
	Chances analysisChances;
	/**
	 * Best move suggested by analysis.
	 */
	short analysisBestMove = Move.NO_MOVE;
	/**
	 * Depth captured by the last analysis call.
	 */
	int analysisDepth;
	/**
	 * Milliseconds spent analyzing this node.
	 */
	long analysisTimeMs;
	/**
	 * Whether the analysis run completed.
	 */
	boolean analysisComplete;

	/**
	 * Creates a PGN node snapshot.
	 *
	 * @param san san string for this move.
	 * @param parent parent node above this ply.
	 * @param positionAfter board position after the move.
	 * @param move underlying move value.
	 * @param ply ply index since the root.
	 */
	public PgnNode(String san, PgnNode parent, Position positionAfter, short move, int ply) {
		this.san = san;
		this.parent = parent;
		this.positionAfter = positionAfter;
		this.move = move;
		this.ply = ply;
	}

	/**
	 * getSan method.
	 *
	 * @return return value.
	 */
	public String getSan() {
		return san;
	}

	/**
	 * getParent method.
	 *
	 * @return return value.
	 */
	public PgnNode getParent() {
		return parent;
	}

	/**
	 * getPositionAfter method.
	 *
	 * @return return value.
	 */
	public Position getPositionAfter() {
		return positionAfter;
	}

	/**
	 * getMove method.
	 *
	 * @return return value.
	 */
	public short getMove() {
		return move;
	}

	/**
	 * getPly method.
	 *
	 * @return return value.
	 */
	public int getPly() {
		return ply;
	}

	/**
	 * getMainNext method.
	 *
	 * @return return value.
	 */
	public PgnNode getMainNext() {
		return mainNext;
	}

	/**
	 * setMainNext method.
	 *
	 * @param mainNext parameter.
	 */
	public void setMainNext(PgnNode mainNext) {
		this.mainNext = mainNext;
	}

	/**
	 * getVariations method.
	 *
	 * @return return value.
	 */
	public List<PgnNode> getVariations() {
		return variations;
	}

	/**
	 * Returns the comment attached to this node, if any.
	 *
	 * @return comment text or null when absent.
	 */
	public String getComment() {
		return comment;
	}

	/**
	 * setComment method.
	 *
	 * @param comment parameter.
	 */
	public void setComment(String comment) {
		this.comment = comment;
	}

	/**
	 * getNag method.
	 *
	 * @return return value.
	 */
	public int getNag() {
		return nag;
	}

	/**
	 * setNag method.
	 *
	 * @param nag parameter.
	 */
	public void setNag(int nag) {
		this.nag = nag;
	}
}
