package application.gui.window;

/**
 * Persisted GUI state stored in ~/.crtk/gui.properties.
 *
 * Tracks theme flags, board orientation, panel visibility, and divider positions so the window can be restored with the user’s preferred layout on the next launch.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
final class GuiState {
	/**
	 * lightMode field.
	 */
	boolean lightMode;
	/**
	 * whiteDown field.
	 */
	boolean whiteDown;
	/**
	 * showLegal field.
	 */
	boolean showLegal;
	/**
	 * showMoves field.
	 */
	boolean showMoves;
	/**
	 * showTags field.
	 */
	boolean showTags;
	/**
	 * showBestMove field.
	 */
	boolean showBestMove;
	/**
	 * showCoords field.
	 */
	boolean showCoords;
	/**
	 * hoverLegal field.
	 */
	boolean hoverLegal;
	/**
	 * hoverHighlight field.
	 */
	boolean hoverHighlight;
	/**
	 * hoverOnlyLegal field.
	 */
	boolean hoverOnlyLegal;
	/**
	 * highContrast field.
	 */
	boolean highContrast;
	/**
	 * compactMode field.
	 */
	boolean compactMode;
	/**
	 * figurineSan field.
	 */
	boolean figurineSan;
	/**
	 * animationMillis field.
	 */
	int animationMillis;
	/**
	 * boardHueDegrees field.
	 */
	int boardHueDegrees;
	/**
	 * boardBrightness field.
	 */
	int boardBrightness;
	/**
	 * boardSaturation field.
	 */
	int boardSaturation;
	/**
	 * splitDivider field.
	 */
	int splitDivider;
	/**
	 * analysisDivider field.
	 */
	int analysisDivider;
	/**
	 * sidebarVisible field.
	 */
	boolean sidebarVisible;
	/**
	 * windowWidth field.
	 */
	int windowWidth;
	/**
	 * windowHeight field.
	 */
	int windowHeight;
	/**
	 * rightTabIndex field.
	 */
	int rightTabIndex;
	/**
	 * uiScale field.
	 */
	float uiScale;

	/**
	 * defaults method.
	 * @param defaultLightMode parameter.
	 * @param defaultWhiteDown parameter.
	 * @return return value.
	 */
	static GuiState defaults(boolean defaultLightMode, boolean defaultWhiteDown) {
		GuiState state = new GuiState();
		state.lightMode = defaultLightMode;
		state.whiteDown = defaultWhiteDown;
		state.showLegal = true;
		state.showMoves = true;
		state.showTags = true;
		state.showBestMove = true;
		state.showCoords = true;
		state.hoverLegal = true;
		state.hoverHighlight = true;
		state.hoverOnlyLegal = false;
		state.highContrast = false;
		state.compactMode = false;
		state.figurineSan = false;
		state.animationMillis = 100;
		state.boardHueDegrees = 0;
		state.boardBrightness = 100;
		state.boardSaturation = 100;
		state.splitDivider = -1;
		state.analysisDivider = -1;
		state.sidebarVisible = true;
		state.windowWidth = -1;
		state.windowHeight = -1;
		state.rightTabIndex = 0;
		state.uiScale = 1.0f;
		return state;
	}
}
