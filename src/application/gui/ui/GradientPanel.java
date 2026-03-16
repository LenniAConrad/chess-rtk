package application.gui.ui;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JPanel;

/**
 * Lightweight panel that paints a vertical background gradient.
 *
 * Offers runtime color updates so themed dialogs can share a reusable gradient surface without subclassing paint logic.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class GradientPanel extends JPanel {
	/**
	 * top field.
	 */
	private Color top;
	/**
	 * bottom field.
	 */
	private Color bottom;

	/**
	 * @param top top gradient color.
	 * @param bottom bottom gradient color.
	 */
	public GradientPanel(Color top, Color bottom) {
		this.top = top;
		this.bottom = bottom;
		setOpaque(false);
	}

	/**
	 * Updates the gradient colors.
	 * @param top parameter.
	 * @param bottom parameter.
	 */
	public void setColors(Color top, Color bottom) {
		this.top = top;
		this.bottom = bottom;
		repaint();
	}

	@Override
	/**
	 * paintComponent method.
	 *
	 * @param g parameter.
	 */
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
		g2.fillRect(0, 0, getWidth(), getHeight());
		g2.dispose();
		super.paintComponent(g);
	}
}
