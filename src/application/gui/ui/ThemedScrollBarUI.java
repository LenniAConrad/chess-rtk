package application.gui.ui;

import application.gui.GuiTheme;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Custom scroll bar UI tuned to the GUI theme.
 *
 * Paints rounded tracks/thumbs, hides the arrow buttons, and adjusts alpha blends so the scroll bar matches the lightweight gradient surfaces.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class ThemedScrollBarUI extends BasicScrollBarUI {
	/**
	 * theme field.
	 */
	private final GuiTheme theme;
	/**
	 * thickness field.
	 */
	private final int thickness;
	/**
	 * darkMode field.
	 */
	private final boolean darkMode;

	/**
	 * @param theme current theme colors.
	 * @param thickness desired scroll thumb thickness.
	 */
	public ThemedScrollBarUI(GuiTheme theme, int thickness) {
		this.theme = theme;
		this.thickness = thickness;
		this.darkMode = isDark(theme.backgroundTop());
		this.scrollBarWidth = thickness;
	}

	@Override
	/**
	 * configureScrollBarColors method.
	 */
	protected void configureScrollBarColors() {
		trackColor = withAlpha(theme.surfaceAlt(), 140);
		thumbColor = withAlpha(theme.accent(), darkMode ? 200 : 170);
		thumbDarkShadowColor = thumbColor;
		thumbHighlightColor = withAlpha(theme.accent(), 230);
		thumbLightShadowColor = thumbColor;
	}

	@Override
	/**
	 * paintTrack method.
	 *
	 * @param g parameter.
	 * @param c parameter.
	 * @param trackBounds parameter.
	 */
	protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(trackColor);
		int arc = thickness;
		g2.fillRoundRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height, arc, arc);
		g2.dispose();
	}

	@Override
	/**
	 * paintThumb method.
	 *
	 * @param g parameter.
	 * @param c parameter.
	 * @param thumbBounds parameter.
	 */
	protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
		if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Color base = isThumbRollover() ? thumbHighlightColor : thumbColor;
		g2.setColor(base);
		int arc = thickness;
		g2.fillRoundRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height, arc, arc);
		g2.dispose();
	}

	@Override
	/**
	 * createDecreaseButton method.
	 *
	 * @param orientation parameter.
	 * @return return value.
	 */
	protected JButton createDecreaseButton(int orientation) {
		return createZeroButton();
	}

	@Override
	/**
	 * createIncreaseButton method.
	 *
	 * @param orientation parameter.
	 * @return return value.
	 */
	protected JButton createIncreaseButton(int orientation) {
		return createZeroButton();
	}

	/**
	 * createZeroButton method.
	 * @return return value.
	 */
	private JButton createZeroButton() {
		JButton button = new JButton();
		button.setPreferredSize(new Dimension(0, 0));
		button.setMinimumSize(new Dimension(0, 0));
		button.setMaximumSize(new Dimension(0, 0));
		return button;
	}

	/**
	 * withAlpha method.
	 * @param base parameter.
	 * @param alpha parameter.
	 * @return return value.
	 */
	private Color withAlpha(Color base, int alpha) {
		return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
	}

	/**
	 * isDark method.
	 * @param color parameter.
	 * @return return value.
	 */
	private boolean isDark(Color color) {
		double lum = 0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue();
		return lum < 128;
	}
}
