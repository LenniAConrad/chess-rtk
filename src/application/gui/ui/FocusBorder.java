package application.gui.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.border.Border;

/**
 * Simple focus border wrapper used for consistent focus rings on inputs.
 *
 * Delegates to the wrapped border while adding a rounded focus ring whenever the component becomes the focus owner, keeping the entire input row cohesive with the theme.
 *
 * @param inner wrapped base border that still needs painting.
 * @param focusColor highlight color used while the component owns focus.
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class FocusBorder implements Border {
	/**
	 * Wrapped border painted before the focus ring.
	 */
	private final Border inner;
	/**
	 * Color used when the component is in focus.
	 */
	private final Color focusColor;

	/**
	 * Constructor.
	 * @param inner parameter.
	 * @param focusColor parameter.
	 */
	public FocusBorder(Border inner, Color focusColor) {
		this.inner = inner;
		this.focusColor = focusColor;
	}

		/**
	 * Handles paint border.
	 * @param c c value
	 * @param g g value
	 * @param x x value
	 * @param y y value
	 * @param width width value
	 * @param height height value
	 */
@Override
	/**
	 * paintBorder method.
	 *
	 * @param c parameter.
	 * @param g parameter.
	 * @param x parameter.
	 * @param y parameter.
	 * @param width parameter.
	 * @param height parameter.
	 */
	public void paintBorder(java.awt.Component c, Graphics g, int x, int y, int width, int height) {
		if (inner != null) {
			inner.paintBorder(c, g, x, y, width, height);
		}
		if (!c.isFocusOwner()) {
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(focusColor);
		int arc = Math.max(6, Math.round(8));
		g2.drawRoundRect(x + 1, y + 1, Math.max(0, width - 3), Math.max(0, height - 3), arc, arc);
		g2.dispose();
	}

		/**
	 * Returns the border insets.
	 * @param c c value
	 * @return computed value
	 */
@Override
	/**
	 * getBorderInsets method.
	 *
	 * @param c parameter.
	 * @return return value.
	 */
	public Insets getBorderInsets(java.awt.Component c) {
		return inner == null ? new Insets(0, 0, 0, 0) : inner.getBorderInsets(c);
	}

		/**
	 * Returns whether border opaque.
	 * @return computed value
	 */
@Override
	/**
	 * isBorderOpaque method.
	 *
	 * @return return value.
	 */
	public boolean isBorderOpaque() {
		return false;
	}
}
