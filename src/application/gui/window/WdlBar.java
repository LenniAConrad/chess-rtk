package application.gui.window;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JPanel;
import javax.swing.Timer;

import chess.uci.Chances;

/**
 * Draws a win/draw/loss bar for engine WDL output.
 *
 * Animates the stacked W/D/L segments and percentage label with themed colors so the history window can present the current chances for either side.
 *
 * @param owner history window that supplies colors, fonts, and render configuration.
  * @since 2026
  * @author Lennart A. Conrad
 */
final class WdlBar extends JPanel {

	@java.io.Serial
	private static final long serialVersionUID = 1L;
	/**
	 * Column width for the WDL strip.
	 */
	private static final int BAR_WIDTH = 28;
	/**
	 * Window owning the bar for theme helpers.
	 */
	private final GuiWindowHistory owner;
	/**
	 * Animated portions for the three outcomes.
	 */
	private double whitePortion = 0.34;
	/**
	 * drawPortion field.
	 */
	private double drawPortion = 0.33;
	/**
	 * blackPortion field.
	 */
	private double blackPortion = 0.33;
	/**
	 * targetWhite field.
	 */
	private double targetWhite = 0.34;
	/**
	 * targetDraw field.
	 */
	private double targetDraw = 0.33;
	/**
	 * targetBlack field.
	 */
	private double targetBlack = 0.33;
	/**
	 * Text shown beneath the bar.
	 */
	private String label = "WDL";
	/**
	 * Whether the values currently represent real data.
	 */
	private boolean valid = false;
	/**
	 * Timer used to animate portion changes.
	 */
	private Timer animationTimer;

	/**
	 * Constructor.
	 * @param owner parameter.
	 */
	WdlBar(GuiWindowHistory owner) {
		this.owner = owner;
		setOpaque(false);
		setPreferredSize(new Dimension(BAR_WIDTH, 140));
		setMinimumSize(new Dimension(BAR_WIDTH, 100));
	}

	/**
	 * Resets the bar to a neutral state.
	 */
	void clear() {
		valid = false;
		label = "WDL";
		animateTo(0.34, 0.33, 0.33);
	}

	/**
	 * Indicates the bar is awaiting updated chances.
	 */
	void markPending() {
		label = "…";
		valid = true;
		repaint();
	}

	/**
	 * Updates the bar based on win/draw/loss chances supplied by the engine.
	 *
	 * @param chances WDL chances object.
	 * @param whiteTurn whether white is on move (affects win/loss assignment).
	 */
	void setChances(Chances chances, boolean whiteTurn) {
		if (chances == null) {
			clear();
			return;
		}
		double win = chances.getWinChance() / 1000.0;
		double draw = chances.getDrawChance() / 1000.0;
		double loss = chances.getLossChance() / 1000.0;
		double white = whiteTurn ? win : loss;
		double black = whiteTurn ? loss : win;
		double total = white + draw + black;
		if (total <= 0.0) {
			clear();
			return;
		}
		white /= total;
		draw /= total;
		black /= total;
		int wPct = (int) Math.round(white * 100);
		int dPct = (int) Math.round(draw * 100);
		int bPct = Math.max(0, 100 - wPct - dPct);
		label = "W " + wPct + "%  D " + dPct + "%  B " + bPct + "%";
		valid = true;
		animateTo(white, draw, black);
	}

	/**
	 * Animates toward the requested portion values.
	 * @param white parameter.
	 * @param draw parameter.
	 * @param black parameter.
	 */
	private void animateTo(double white, double draw, double black) {
		targetWhite = Math.max(0.0, Math.min(1.0, white));
		targetDraw = Math.max(0.0, Math.min(1.0, draw));
		targetBlack = Math.max(0.0, Math.min(1.0, black));
		double total = targetWhite + targetDraw + targetBlack;
		if (total > 0) {
			targetWhite /= total;
			targetDraw /= total;
			targetBlack /= total;
		}
		if (animationTimer == null) {
			animationTimer = new Timer(16, e -> animateStep());
			animationTimer.setCoalesce(true);
		}
		if (!animationTimer.isRunning()) {
			animationTimer.start();
		}
		repaint();
	}

	/**
	 * Performs a single animation frame adjusting the portion values.
	 */
	private void animateStep() {
		double diffW = targetWhite - whitePortion;
		double diffD = targetDraw - drawPortion;
		double diffB = targetBlack - blackPortion;
		if (Math.abs(diffW) + Math.abs(diffD) + Math.abs(diffB) < 0.004) {
			whitePortion = targetWhite;
			drawPortion = targetDraw;
			blackPortion = targetBlack;
			animationTimer.stop();
			repaint();
			return;
		}
		whitePortion += diffW * 0.22;
		drawPortion += diffD * 0.22;
		blackPortion += diffB * 0.22;
		repaint();
	}

	/**
	 * Paints the WDL segments, border, and label.
	 *
	 * @param g graphics context.
	 */
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		owner.applyRenderHints(g2);
		int w = getWidth();
		int h = getHeight();
		int inset = 4;
		int barW = Math.min(BAR_WIDTH, w - inset * 2);
		if (barW <= 0 || h <= 0) {
			g2.dispose();
			return;
		}
		g2.setFont(owner.theme.bodyFont());
		int labelH = g2.getFontMetrics().getAscent();
		int barH = h - inset * 2 - labelH - 6;
		if (barH <= 0) {
			g2.dispose();
			return;
		}
		int barX = inset + (w - barW) / 2;
		int barY = inset;

		Color whiteColor = new Color(245, 245, 245);
		Color drawColor = new Color(170, 170, 170);
		Color blackColor = new Color(30, 30, 30);

		g2.setColor(owner.theme.surfaceAlt());
		g2.fillRoundRect(barX, barY, barW, barH, 10, 10);

		int wHeight = Math.max(0, (int) Math.round(barH * whitePortion));
		int dHeight = Math.max(0, (int) Math.round(barH * drawPortion));
		int bHeight = Math.max(0, barH - wHeight - dHeight);
		int y = barY;
		if (wHeight > 0) {
			g2.setColor(whiteColor);
			g2.fillRoundRect(barX, y, barW, wHeight, 10, 10);
			y += wHeight;
		}
		if (dHeight > 0) {
			g2.setColor(drawColor);
			g2.fillRect(barX, y, barW, dHeight);
			y += dHeight;
		}
		if (bHeight > 0) {
			g2.setColor(blackColor);
			g2.fillRoundRect(barX, y, barW, bHeight, 10, 10);
		}

		g2.setColor(owner.theme.border());
		g2.drawRoundRect(barX, barY, barW, barH, 10, 10);

		String text = valid ? label : "WDL";
		int textW = g2.getFontMetrics().stringWidth(text);
		int textX = barX + Math.max(0, (barW - textW) / 2);
		int textY = barY + barH + g2.getFontMetrics().getAscent() + 4;
		g2.setColor(owner.theme.textMuted());
		g2.drawString(text, textX, textY);
		g2.dispose();
	}
}
