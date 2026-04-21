package application.gui.web;

import java.awt.Color;
import java.awt.Font;

/**
 * Theme palette for the chess-web-inspired desktop GUI.
 *
 * @param pageTop top page gradient color
 * @param pageBottom bottom page gradient color
 * @param headerText strong header text
 * @param headerMuted muted header text
 * @param surface main card fill
 * @param surfaceAlt alternate surface fill
 * @param surfaceBorder card border
 * @param surfaceShadow card shadow
 * @param inputBackground text-field background
 * @param inputBorder text-field border
 * @param inputText text-field foreground
 * @param buttonBackground primary button background
 * @param buttonHover primary button hover background
 * @param buttonBorder primary button border
 * @param buttonText primary button foreground
 * @param toggleOnBackground enabled toggle background
 * @param toggleOnBorder enabled toggle border
 * @param toggleOffBackground disabled toggle background
 * @param toggleOffBorder disabled toggle border
 * @param boardLight light square color
 * @param boardDark dark square color
 * @param boardFrame board edge color
 * @param boardShadow board shadow color
 * @param boardGlass glass overlay tint
 * @param text standard text
 * @param textMuted muted text
 * @param textStrong strong text
 * @param accent accent color
 * @param accentSoft soft accent color
 * @param legalMove legal move marker
 * @param legalCapture legal capture ring
 * @param lastMove last-move highlight
 * @param selected selected-square highlight
 * @param check check highlight
 * @param evalDark eval-bar dark segment
 * @param evalLight eval-bar light segment
 * @param evalBorder eval-bar border
 * @param titleFont title font
 * @param bodyFont body font
 * @param smallFont small font
 * @param monoFont monospace font
 * @since 2026
 */
public record WebGuiTheme(
		Color pageTop,
		Color pageBottom,
		Color headerText,
		Color headerMuted,
		Color surface,
		Color surfaceAlt,
		Color surfaceBorder,
		Color surfaceShadow,
		Color inputBackground,
		Color inputBorder,
		Color inputText,
		Color buttonBackground,
		Color buttonHover,
		Color buttonBorder,
		Color buttonText,
		Color toggleOnBackground,
		Color toggleOnBorder,
		Color toggleOffBackground,
		Color toggleOffBorder,
		Color boardLight,
		Color boardDark,
		Color boardFrame,
		Color boardShadow,
		Color boardGlass,
		Color text,
		Color textMuted,
		Color textStrong,
		Color accent,
		Color accentSoft,
		Color legalMove,
		Color legalCapture,
		Color lastMove,
		Color selected,
		Color check,
		Color evalDark,
		Color evalLight,
		Color evalBorder,
		Font titleFont,
		Font bodyFont,
		Font smallFont,
		Font monoFont) {

	/**
	 * Returns the light palette inspired by chess-web.
	 *
	 * @return light theme
	 */
	public static WebGuiTheme light() {
		return new WebGuiTheme(
				new Color(245, 240, 231),
				new Color(221, 211, 195),
				new Color(18, 18, 18),
				new Color(66, 66, 66),
				new Color(244, 241, 236),
				new Color(250, 247, 242),
				new Color(198, 190, 177),
				new Color(0, 0, 0, 28),
				Color.WHITE,
				new Color(182, 182, 182),
				new Color(18, 18, 18),
				new Color(242, 219, 183),
				new Color(247, 228, 196),
				new Color(183, 137, 100),
				new Color(43, 34, 24),
				new Color(242, 219, 183),
				new Color(183, 137, 100),
				new Color(229, 224, 216),
				new Color(191, 184, 174),
				new Color(239, 214, 171),
				new Color(188, 141, 98),
				new Color(124, 92, 65),
				new Color(0, 0, 0, 42),
				new Color(255, 255, 255, 55),
				new Color(33, 33, 33),
				new Color(97, 97, 97),
				new Color(18, 18, 18),
				new Color(74, 182, 111),
				new Color(74, 182, 111, 56),
				new Color(96, 136, 67, 186),
				new Color(87, 127, 57, 210),
				new Color(236, 218, 96, 128),
				new Color(112, 153, 78, 132),
				new Color(214, 54, 54, 128),
				new Color(22, 22, 22),
				new Color(246, 246, 246),
				new Color(22, 22, 22, 120),
				new Font("Segoe UI", Font.BOLD, 17),
				new Font("Segoe UI", Font.PLAIN, 13),
				new Font("Segoe UI", Font.PLAIN, 12),
				new Font(Font.MONOSPACED, Font.PLAIN, 12));
	}

	/**
	 * Returns the dark palette inspired by chess-web.
	 *
	 * @return dark theme
	 */
	public static WebGuiTheme dark() {
		return new WebGuiTheme(
				new Color(40, 35, 31),
				new Color(18, 16, 14),
				new Color(246, 248, 251),
				new Color(195, 188, 180),
				new Color(38, 34, 31),
				new Color(31, 28, 26),
				new Color(83, 76, 69),
				new Color(0, 0, 0, 92),
				new Color(32, 30, 28),
				new Color(90, 82, 75),
				new Color(244, 237, 230),
				new Color(242, 219, 183),
				new Color(247, 226, 194),
				new Color(183, 137, 100),
				new Color(43, 34, 24),
				new Color(138, 107, 78),
				new Color(235, 202, 170),
				new Color(56, 46, 39),
				new Color(112, 98, 84),
				new Color(239, 214, 171),
				new Color(188, 141, 98),
				new Color(110, 86, 64),
				new Color(0, 0, 0, 110),
				new Color(255, 255, 255, 28),
				new Color(236, 229, 221),
				new Color(182, 174, 165),
				Color.WHITE,
				new Color(74, 182, 111),
				new Color(74, 182, 111, 68),
				new Color(122, 166, 84, 180),
				new Color(120, 166, 84, 214),
				new Color(236, 218, 96, 96),
				new Color(122, 166, 84, 122),
				new Color(214, 54, 54, 112),
				new Color(20, 20, 20),
				new Color(244, 244, 244),
				new Color(244, 244, 244, 104),
				new Font("Segoe UI", Font.BOLD, 17),
				new Font("Segoe UI", Font.PLAIN, 13),
				new Font("Segoe UI", Font.PLAIN, 12),
				new Font(Font.MONOSPACED, Font.PLAIN, 12));
	}
}
