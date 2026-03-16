package application.gui.window;

/**
 * Entry point for the GUI window (thin wrapper over the split base implementation).
 *
 * Applies the chosen FEN, orientation, and theme options before handing control to the shared layout so the frame can be consistently constructed.
 *
 * @param fen starting position FEN string (defaults to standard if blank).
 * @param whiteDown whether to render White at the bottom.
 * @param lightMode whether to start in light theme instead of dark.
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class GuiWindow extends GuiWindowLayout {
	/**
	 * Default initial FEN for GUI startup.
	 */
	public static final String DEFAULT_FEN = GuiWindowBase.DEFAULT_FEN;

	/**
	 * Constructor.
	 * @param fen parameter.
	 * @param whiteDown parameter.
	 * @param lightMode parameter.
	 */
	public GuiWindow(String fen, boolean whiteDown, boolean lightMode) {
		super(fen, whiteDown, lightMode);
	}
}
