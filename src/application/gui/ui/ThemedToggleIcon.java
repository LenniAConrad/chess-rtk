package application.gui.ui;

import application.gui.GuiTheme;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;

/**
 * Toggle switch icon rendered in the current theme.
 *
 * Paints rounded tracks and knobs using accent, surface, and border colors so toggle buttons align with the rest of the UI palette.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class ThemedToggleIcon implements Icon {
	/**
	 * theme field.
	 */
	private final GuiTheme theme;
	/**
	 * selected field.
	 */
	private final boolean selected;
	/**
	 * width field.
	 */
	private final int width;
	/**
	 * height field.
	 */
	private final int height;

	/**
	 * @param theme current theme colors.
	 * @param selected whether the toggle is on.
	 */
	public ThemedToggleIcon(GuiTheme theme, boolean selected) {
		this(theme, selected, 38, 20);
	}

	/**
	 * @param theme current theme colors.
	 * @param selected whether the toggle is on.
	 * @param width total width in pixels.
	 * @param height total height in pixels.
	 */
	public ThemedToggleIcon(GuiTheme theme, boolean selected, int width, int height) {
		this.theme = theme;
		this.selected = selected;
		this.width = width;
		this.height = height;
	}

	@Override
	/**
	 * getIconWidth method.
	 *
	 * @return return value.
	 */
	public int getIconWidth() {
		return width;
	}

	@Override
	/**
	 * getIconHeight method.
	 *
	 * @return return value.
	 */
	public int getIconHeight() {
		return height;
	}

	@Override
	/**
	 * paintIcon method.
	 *
	 * @param c parameter.
	 * @param g parameter.
	 * @param x parameter.
	 * @param y parameter.
	 */
	public void paintIcon(Component c, Graphics g, int x, int y) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int arc = height;
		Color track = selected ? theme.accent() : theme.surfaceAlt();
		Color border = theme.border();
		Color knob = theme.surface();
		g2.setColor(track);
		g2.fillRoundRect(x, y, width, height, arc, arc);
		g2.setColor(border);
		g2.drawRoundRect(x, y, width - 1, height - 1, arc, arc);

		int knobSize = height - 4;
		int knobY = y + 2;
		int knobX = selected ? (x + width - knobSize - 2) : (x + 2);
		g2.setColor(new Color(0, 0, 0, 35));
		g2.fillOval(knobX, knobY + 1, knobSize, knobSize);
		g2.setColor(knob);
		g2.fillOval(knobX, knobY, knobSize, knobSize);
		g2.setColor(border);
		g2.drawOval(knobX, knobY, knobSize - 1, knobSize - 1);

		g2.dispose();
	}
}
