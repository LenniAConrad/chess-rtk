package application.gui.window;

/**
 * Navigation helper for PGN mainline traversal.
 *
 * Keeps the root and current node references while walking through siblings so history tables and move annotations can stay in sync with the selected ply.
 *
 * @param root parsed root node for the main line.
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class PgnNavigator {
	/**
	 * Root node of the mainline.
	 */
	final PgnNode root;
	/**
	 * Node currently selected as the active ply.
	 */
	PgnNode current;

	/**
	 * Constructor.
	 * @param root parameter.
	 */
	public PgnNavigator(PgnNode root) {
		this.root = root;
		this.current = root;
	}

	/**
	 * getRoot method.
	 *
	 * @return return value.
	 */
	public PgnNode getRoot() {
		return root;
	}

	/**
	 * getCurrent method.
	 *
	 * @return return value.
	 */
	public PgnNode getCurrent() {
		return current;
	}
}
