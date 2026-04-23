package application.gui.history.variation;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JList;

import application.gui.window.PgnNode;
import application.gui.model.HistoryEntry;
import application.gui.model.MovePair;
import chess.core.SAN;
import chess.core.Move;
import chess.core.Position;

/**
 * Selection/index helpers for history list widgets.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class HistorySelectionSupport {

	/**
	 * HistorySelectionSupport method.
	 */
	private HistorySelectionSupport() {
	}

	/**
	 * selectHistoryNode method.
	 *
	 * @param model parameter.
	 * @param list parameter.
	 * @param node parameter.
	 */
	public static void selectHistoryNode(DefaultListModel<HistoryEntry> model, JList<HistoryEntry> list, PgnNode node) {
		if (model == null || list == null || node == null) {
			return;
		}
		for (int i = 0; i < model.size(); i++) {
			HistoryEntry entry = model.get(i);
			if (entry != null && entry.node() == node) {
				list.setSelectedIndex(i);
				list.ensureIndexIsVisible(i);
				return;
			}
		}
	}

	/**
	 * historyIndexAtPoint method.
	 *
	 * @param list parameter.
	 * @param model parameter.
	 * @param point parameter.
	 * @param uiScale parameter.
	 * @return return value.
	 */
	public static int historyIndexAtPoint(JList<MovePair> list, DefaultListModel<MovePair> model, Point point,
			float uiScale) {
		if (list == null || model == null || point == null) {
			return -1;
		}
		int row = list.locationToIndex(point);
		if (row < 0 || row >= model.getSize()) {
			return -1;
		}
		Rectangle bounds = list.getCellBounds(row, row);
		if (bounds == null || !bounds.contains(point)) {
			return -1;
		}
		MovePair pair = model.getElementAt(row);
		if (pair == null) {
			return -1;
		}
		int numberWidth = Math.round(36 * uiScale);
		/**
		 * usableWidth field.
		 */
		int usableWidth = bounds.width - numberWidth;
		if (usableWidth <= 0) {
			return -1;
		}
		/**
		 * colWidth field.
		 */
		int colWidth = usableWidth / 2;
		/**
		 * relX field.
		 */
		int relX = point.x - bounds.x;
		if (relX < numberWidth) {
			return -1;
		}
		if (relX < numberWidth + colWidth) {
			return pair.whiteIndex();
		}
		return pair.blackIndex();
	}

	/**
	 * nodeAtHistoryIndex method.
	 *
	 * @param idx parameter.
	 * @param currentLineNodes parameter.
	 * @return return value.
	 */
	public static PgnNode nodeAtHistoryIndex(int idx, List<PgnNode> currentLineNodes) {
		if (currentLineNodes == null || idx < 0 || idx >= currentLineNodes.size()) {
			return null;
		}
		return currentLineNodes.get(idx);
	}

	/**
	 * previewPositionAtHistoryIndex method.
	 *
	 * @param idx parameter.
	 * @param currentLineNodes parameter.
	 * @param history parameter.
	 * @param moveHistory parameter.
	 * @return return value.
	 */
	public static Position previewPositionAtHistoryIndex(int idx, List<PgnNode> currentLineNodes, List<Position> history,
			List<String> moveHistory) {
		if (idx < 0) {
			return null;
		}
		PgnNode node = nodeAtHistoryIndex(idx, currentLineNodes);
		if (node != null && node.getPositionAfter() != null) {
			return node.getPositionAfter().copy();
		}
		if (history == null || moveHistory == null || idx >= history.size() || idx >= moveHistory.size()) {
			return null;
		}
		Position before = history.get(idx);
		if (before == null) {
			return null;
		}
		try {
			short move = SAN.fromAlgebraic(before, moveHistory.get(idx));
			if (!before.isLegalMove(move)) {
				return null;
			}
			return before.copy().play(move);
		} catch (RuntimeException ex) {
			return null;
		}
	}

	/**
	 * resolveHistoryMove method.
	 *
	 * @param idx parameter.
	 * @param currentLineNodes parameter.
	 * @param moveHistory parameter.
	 * @param history parameter.
	 * @return return value.
	 */
	public static short resolveHistoryMove(int idx, List<PgnNode> currentLineNodes, List<String> moveHistory,
			List<Position> history) {
		if (idx < 0) {
			return Move.NO_MOVE;
		}
		PgnNode node = nodeAtHistoryIndex(idx, currentLineNodes);
		if (node != null && node.getMove() != Move.NO_MOVE) {
			return node.getMove();
		}
		if (moveHistory == null || history == null || idx >= moveHistory.size() || idx >= history.size()) {
			return Move.NO_MOVE;
		}
		Position before = history.get(idx);
		if (before == null) {
			return Move.NO_MOVE;
		}
		try {
			return SAN.fromAlgebraic(before, moveHistory.get(idx));
		} catch (RuntimeException ex) {
			return Move.NO_MOVE;
		}
	}
}
