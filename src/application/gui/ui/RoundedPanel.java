package application.gui.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JPanel;

/**
 * Rounded rectangle panel with optional border and shadow.
 *
 * Provides setters for fill/stroke colors, shadow offsets, and simplified content placement so the GUI can reuse consistent card styling.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class RoundedPanel extends JPanel {

	@java.io.Serial
	private static final long serialVersionUID = 1L;
	/**
	 * radius field.
	 */
	private final int radius;
	/**
	 * fill field.
	 */
	private Color fill;
	/**
	 * stroke field.
	 */
	private Color stroke;
	/**
	 * shadow field.
	 */
	private Color shadow;
	/**
	 * shadowOffset field.
	 */
	private int shadowOffset = 0;

	/**
	 * @param radius corner radius in pixels.
	 */
	public RoundedPanel(int radius) {
		this.radius = radius;
		setOpaque(false);
	}

	/**
	 * Sets fill and stroke colors for the rounded rect.
	 * @param fill parameter.
	 * @param stroke parameter.
	 */
	public void setTheme(Color fill, Color stroke) {
		this.fill = fill;
		this.stroke = stroke;
		repaint();
	}

	/**
	 * Sets shadow color and offset (0 disables shadow).
	 * @param shadow parameter.
	 * @param offset parameter.
	 */
	public void setShadow(Color shadow, int offset) {
		this.shadow = shadow;
		this.shadowOffset = Math.max(0, offset);
		repaint();
	}

    /**
     * Convenience for adding the primary content to CENTER.
     * @param content parameter.
     */
    public void setContent(java.awt.Component content) {
        add(content, java.awt.BorderLayout.CENTER);
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
		int w = getWidth();
		int h = getHeight();
		if (shadow != null && shadowOffset > 0) {
			g2.setColor(shadow);
			g2.fillRoundRect(shadowOffset, shadowOffset, w - shadowOffset - 1, h - shadowOffset - 1, radius, radius);
		}
		if (fill != null) {
			g2.setColor(fill);
			g2.fillRoundRect(0, 0, w - 1 - shadowOffset, h - 1 - shadowOffset, radius, radius);
		}
		if (stroke != null) {
			g2.setColor(stroke);
			g2.drawRoundRect(0, 0, w - 1 - shadowOffset, h - 1 - shadowOffset, radius, radius);
		}
		g2.dispose();
		super.paintComponent(g);
	}
}
