package application.gui.studio;

import java.awt.Color;

/**
 * Color palette for the ChessRTK Studio GUI.
 *
 * @param lightMode whether this is the light palette
 * @param background page background
 * @param panel panel surface
 * @param panelAlt alternate panel surface
 * @param border border color
 * @param text primary text
 * @param muted secondary text
 * @param boardLight light board square
 * @param boardDark dark board square
 * @param selected selected-square overlay
 * @param legal legal move marker
 * @param capture legal capture marker
 * @param lastMove last-move highlight
 * @param accent action accent
 * @param danger error/danger color
 */
public record StudioTheme(
		boolean lightMode,
		Color background,
		Color panel,
		Color panelAlt,
		Color border,
		Color text,
		Color muted,
		Color boardLight,
		Color boardDark,
		Color selected,
		Color legal,
		Color capture,
		Color lastMove,
		Color accent,
		Color danger) {

	/**
	 * Light theme inspired by chess-web.
	 *
	 * @return light theme
	 */
	public static StudioTheme light() {
		return new StudioTheme(
				true,
				new Color(231, 223, 210),
				new Color(248, 246, 240),
				new Color(239, 234, 224),
				new Color(202, 190, 174),
				new Color(18, 18, 18),
				new Color(89, 83, 74),
				new Color(242, 219, 183),
				new Color(183, 137, 100),
				new Color(112, 153, 78, 120),
				new Color(96, 136, 67, 170),
				new Color(86, 128, 54, 190),
				new Color(236, 218, 96, 120),
				new Color(183, 137, 100),
				new Color(157, 28, 28));
	}

	/**
	 * Dark theme inspired by chess-web.
	 *
	 * @return dark theme
	 */
	public static StudioTheme dark() {
		return new StudioTheme(
				false,
				new Color(21, 19, 17),
				new Color(31, 29, 26),
				new Color(42, 37, 32),
				new Color(80, 70, 60),
				new Color(237, 237, 237),
				new Color(190, 184, 176),
				new Color(224, 201, 164),
				new Color(148, 111, 80),
				new Color(112, 153, 78, 120),
				new Color(126, 165, 91, 185),
				new Color(104, 148, 78, 210),
				new Color(236, 218, 96, 115),
				new Color(216, 177, 138),
				new Color(240, 183, 183));
	}
}
