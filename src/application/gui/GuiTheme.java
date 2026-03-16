package application.gui;

import java.awt.Color;
import java.awt.Font;

/**
 * UI theme bundle for the GUI (colors + fonts).
 *
 * Encapsulates accent, surface, and text colors along with title/body/monospace fonts so the windows can switch themes without duplicating constants.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public record GuiTheme(
		Color backgroundTop,
		Color backgroundBottom,
		Color surface,
		Color surfaceAlt,
		Color border,
		Color shadow,
		Color text,
		Color textMuted,
		Color textStrong,
		Color accent,
		Color accentText,
		Color editor,
		Color sidebar,
		Color activityBar,
		Color statusBar,
		Color selection,
		Color hover,
		Font titleFont,
		Font bodyFont,
		Font monoFont) {

	/**
	 * Default light theme tuned to a neutral lichess-like palette.
	 *
	 * @return configured light theme instance.
	 */
	public static GuiTheme light() {
		return new GuiTheme(
				new Color(250, 250, 250),
				new Color(250, 250, 250),
				new Color(255, 255, 255),
				new Color(245, 245, 245),
				new Color(210, 210, 210),
				new Color(0, 0, 0, 12),
				new Color(48, 48, 48),
				new Color(120, 120, 120),
				new Color(30, 30, 30),
				new Color(216, 123, 74),
				Color.WHITE,
				new Color(250, 250, 250),
				new Color(245, 245, 245),
				new Color(235, 235, 235),
				new Color(235, 235, 235),
				new Color(220, 234, 255),
				new Color(238, 238, 238),
				new Font("Segoe UI", Font.BOLD, 13),
				new Font("Segoe UI", Font.PLAIN, 12),
				new Font("JetBrains Mono", Font.PLAIN, 11));
	}

	/**
	 * Default dark theme tuned to a VS Code-inspired palette.
	 *
	 * @return configured dark theme instance.
	 */
	public static GuiTheme dark() {
		return new GuiTheme(
				new Color(24, 24, 24),
				new Color(24, 24, 24),
				new Color(24, 24, 24),
				new Color(32, 32, 32),
				new Color(42, 42, 42),
				new Color(0, 0, 0, 120),
				new Color(204, 204, 204),
				new Color(150, 150, 150),
				Color.WHITE,
				new Color(168, 85, 247),
				Color.WHITE,
				new Color(24, 24, 24),
				new Color(24, 24, 24),
				new Color(32, 32, 32),
				new Color(32, 32, 32),
				new Color(64, 64, 68),
				new Color(32, 32, 32),
				new Font("Segoe UI", Font.BOLD, 13),
				new Font("Segoe UI", Font.PLAIN, 12),
				new Font("JetBrains Mono", Font.PLAIN, 11));
	}
}
