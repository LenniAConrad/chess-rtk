package application.gui.history.variation;

import application.gui.window.PgnNode;

/**
 * Tree mutation/state helpers for PGN variation management.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class VariationTreeSupport {

	/**
	 * VariationUiState record.
	 *
	 * Provides record behavior for the GUI module.
	 *
	 * @since 2026
	 * @author Lennart A. Conrad
	 */
	public record VariationUiState(
		/**
		 * Stores the has node.
		 */
		boolean hasNode,
		/**
		 * Stores the is variation.
		 */
		boolean isVariation,
		/**
		 * Stores the can up.
		 */
		boolean canUp,
		/**
		 * Stores the can down.
		 */
		boolean canDown,
		/**
		 * Stores the has comment.
		 */
		boolean hasComment,
		/**
		 * Stores the has nag.
		 */
		boolean hasNag
	) {
	}

	/**
	 * DeleteResult record.
	 *
	 * Provides record behavior for the GUI module.
	 *
	 * @since 2026
	 * @author Lennart A. Conrad
	 */
	public record DeleteResult(
		/**
		 * Stores the changed.
		 */
		boolean changed,
		/**
		 * Stores the current was inside deleted branch.
		 */
		boolean currentWasInsideDeletedBranch,
		/**
		 * Stores the fallback node.
		 */
		PgnNode fallbackNode
	) {
	}

	/**
	 * VariationTreeSupport method.
	 */
	private VariationTreeSupport() {
	}

	/**
	 * uiState method.
	 *
	 * @param node parameter.
	 * @return return value.
	 */
	public static VariationUiState uiState(PgnNode node) {
		boolean hasNode = node != null && node.getParent() != null;
		boolean isVariation = hasNode && node.getParent().getMainNext() != node;
		int varIndex = -1;
		int varCount = 0;
		boolean canUp = false;
		boolean canDown = false;
		if (isVariation) {
			varIndex = node.getParent().getVariations().indexOf(node);
			varCount = node.getParent().getVariations().size();
			canUp = varIndex > 0;
			canDown = varIndex >= 0 && varIndex < varCount - 1;
		}
		boolean hasComment = hasNode && node.getComment() != null && !node.getComment().isBlank();
		boolean hasNag = hasNode && node.getNag() != 0;
		return new VariationUiState(hasNode, isVariation, canUp, canDown, hasComment, hasNag);
	}

	/**
	 * promoteToMainline method.
	 *
	 * @param node parameter.
	 * @return return value.
	 */
	public static boolean promoteToMainline(PgnNode node) {
		if (node == null || node.getParent() == null) {
			return false;
		}
		PgnNode parent = node.getParent();
		if (parent.getMainNext() == node) {
			return false;
		}
		parent.getVariations().remove(node);
		if (parent.getMainNext() != null) {
			parent.getVariations().add(parent.getMainNext());
		}
		parent.setMainNext(node);
		return true;
	}

	/**
	 * deleteVariationNode method.
	 *
	 * @param node parameter.
	 * @param current parameter.
	 * @return return value.
	 */
	public static DeleteResult deleteVariationNode(PgnNode node, PgnNode current) {
		if (node == null || node.getParent() == null) {
			return new DeleteResult(false, false, null);
		}
		PgnNode parent = node.getParent();
		if (parent.getMainNext() == node) {
			if (!parent.getVariations().isEmpty()) {
				PgnNode promoted = parent.getVariations().remove(0);
				parent.setMainNext(promoted);
			} else {
				parent.setMainNext(null);
			}
		} else {
			parent.getVariations().remove(node);
		}
		return new DeleteResult(true, isAncestor(node, current), parent);
	}

	/**
	 * moveVariationSibling method.
	 *
	 * @param node parameter.
	 * @param delta parameter.
	 * @return return value.
	 */
	public static boolean moveVariationSibling(PgnNode node, int delta) {
		if (node == null || node.getParent() == null) {
			return false;
		}
		PgnNode parent = node.getParent();
		if (parent.getMainNext() == node) {
			return false;
		}
		int index = parent.getVariations().indexOf(node);
		if (index < 0) {
			return false;
		}
		int nextIndex = Math.max(0, Math.min(parent.getVariations().size() - 1, index + delta));
		if (nextIndex == index) {
			return false;
		}
		parent.getVariations().remove(index);
		parent.getVariations().add(nextIndex, node);
		return true;
	}

	/**
	 * isAncestor method.
	 *
	 * @param ancestor parameter.
	 * @param node parameter.
	 * @return return value.
	 */
	public static boolean isAncestor(PgnNode ancestor, PgnNode node) {
		PgnNode cursor = node;
		while (cursor != null) {
			if (cursor == ancestor) {
				return true;
			}
			cursor = cursor.getParent();
		}
		return false;
	}
}
