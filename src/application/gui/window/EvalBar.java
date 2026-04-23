package application.gui.window;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;
import javax.swing.Timer;

import chess.uci.Evaluation;

/**
 * Draws a compact evaluation bar (centipawns / mate).
 *
 * Animates toward new engine estimates, keeps the evaluation label legible against the themed background, and exposes a clean tracker for the owning history window.
 *
 * @param owner history window that supplies fonts, colors, and render hints.
  * @since 2026
  * @author Lennart A. Conrad
 */
	final class EvalBar extends JPanel {

		/**
	 * Serialization version identifier.
	 */
@java.io.Serial
	private static final long serialVersionUID = 1L;
	/**
	 * Width of the evaluation column.
	 */
	private static final int BAR_WIDTH = 8;
	/**
	 * Cap used to normalize centipawn evaluations.
	 */
	private static final double MAX_CP = 1000.0;
	/**
	 * Owner for fonts, colors, and render helpers.
	 */
	private final GuiWindowHistory owner;
	/**
	 * Current white portion ratio used for rendering.
	 */
	private double whitePortion = 0.5;
	/**
	 * Target ratio that the animation is moving toward.
	 */
	private double targetWhitePortion = 0.5;
	/**
	 * Label text displayed for tooltips.
	 */
	private String label = "—";
	/**
	 * Whether the bar currently shows a valid evaluation.
	 */
	private boolean valid = false;
	/**
	 * Whether the bar is showing stale/no-eval state.
	 */
	private boolean stale = true;
	/**
	 * Timer used to animate between evaluation updates.
	 */
	private Timer animationTimer;
	/**
	 * Tracks hover state for tooltip display.
	 */
	private boolean hoverActive = false;
	/**
	 * Tracks dragging state for tooltip display.
	 */
	private boolean dragActive = false;

	/**
	 * Constructor.
	 * @param owner parameter.
	 */
	EvalBar(GuiWindowHistory owner) {
		this.owner = owner;
		setOpaque(false);
		setPreferredSize(new Dimension(BAR_WIDTH, 420));
		setMinimumSize(new Dimension(BAR_WIDTH, 200));
		MouseAdapter handler = new MouseAdapter() {
						/**
			 * Handles mouse entered.
			 * @param e e value
			 */
@Override
			public void mouseEntered(MouseEvent e) {
				hoverActive = true;
				updateTooltip();
			}

						/**
			 * Handles mouse exited.
			 * @param e e value
			 */
@Override
			public void mouseExited(MouseEvent e) {
				hoverActive = false;
				dragActive = false;
				setToolTipText(null);
			}

						/**
			 * Handles mouse moved.
			 * @param e e value
			 */
@Override
			public void mouseMoved(MouseEvent e) {
				if (!hoverActive) {
					hoverActive = true;
				}
				updateTooltip();
			}

						/**
			 * Handles mouse pressed.
			 * @param e e value
			 */
@Override
			public void mousePressed(MouseEvent e) {
				setDragActive(true);
			}

						/**
			 * Handles mouse released.
			 * @param e e value
			 */
@Override
			public void mouseReleased(MouseEvent e) {
				setDragActive(false);
			}

						/**
			 * Handles mouse dragged.
			 * @param e e value
			 */
@Override
			public void mouseDragged(MouseEvent e) {
				setDragActive(true);
			}
		};
		addMouseListener(handler);
		addMouseMotionListener(handler);
	}

	/**
	 * Updates the drag state and refreshes the tooltip.
	 *
	 * @param active whether a drag gesture is active
	 */
	private void setDragActive(boolean active) {
		dragActive = active;
		updateTooltip();
	}

	/**
	 * Resets the bar to an empty state.
	 */
	void clear() {
		valid = false;
		stale = true;
		label = "—";
		updateTooltip();
		animateTo(0.5);
	}

	/**
	 * Marks the bar as pending while waiting for engine output.
	 */
	void markPending() {
		label = "…";
		valid = false;
		stale = false;
		// Keep the last rendered value while waiting for the next valid result.
		targetWhitePortion = whitePortion;
		if (animationTimer != null && animationTimer.isRunning()) {
			animationTimer.stop();
		}
		updateTooltip();
		repaint();
	}

	/**
	 * Updates the bar with the provided evaluation.
	 *
	 * @param eval evaluation payload.
	 * @param whiteTurn whether white is on move.
	 */
	void setEvaluation(Evaluation eval, boolean whiteTurn) {
		if (eval == null || !eval.isValid()) {
			markPending();
			return;
		}
		int value = eval.getValue();
		if (!whiteTurn) {
			value = -value;
		}
		label = owner.formatEvalLabel(value, eval.isMate());
		updateTooltip();
		double normalized;
		if (eval.isMate()) {
			normalized = value >= 0 ? 1.0 : -1.0;
		} else {
			double clamped = Math.max(-MAX_CP, Math.min(MAX_CP, value));
			normalized = clamped / MAX_CP;
		}
		targetWhitePortion = Math.max(0.0, Math.min(1.0, (normalized + 1.0) / 2.0));
		valid = true;
		stale = false;
		animateTo(targetWhitePortion);
	}

	/**
	 * Starts the animation toward the new target ratio.
	 *
	 * @param target target white ratio.
	 */
	private void animateTo(double target) {
		targetWhitePortion = Math.max(0.0, Math.min(1.0, target));
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
	 * updateTooltip method.
	 */
	private void updateTooltip() {
		if (!hoverActive && !dragActive) {
			setToolTipText(null);
			return;
		}
		String text = valid ? label : ("…".equals(label) ? "…" : "No evaluation");
		setToolTipText(text);
	}

	/**
	 * Performs an incremental animation step.
	 */
	private void animateStep() {
		double diff = targetWhitePortion - whitePortion;
		if (Math.abs(diff) < 0.002) {
			whitePortion = targetWhitePortion;
			animationTimer.stop();
			repaint();
			return;
		}
		whitePortion += diff * 0.22;
		repaint();
	}

	/**
	 * Paints the evaluation gradient, border, and label.
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
		int x = 0;
		int y = 0;
		int barW = w;
		int barH = h;
		if (barW <= 0 || barH <= 0) {
			g2.dispose();
			return;
		}
		if (stale) {
			Color staleFill = owner.blend(owner.getTheme().surfaceAlt(), owner.getTheme().textMuted(), 0.22f);
			g2.setColor(staleFill);
			g2.fillRect(x, y, barW, barH);
		} else {
			Color whiteColor = Color.WHITE;
			Color blackColor = Color.BLACK;
			int whiteHeight = (int) Math.round(barH * whitePortion);
			g2.setColor(blackColor);
			g2.fillRect(x, y + whiteHeight, barW, barH - whiteHeight);
			g2.setColor(whiteColor);
			g2.fillRect(x, y, barW, whiteHeight);
		}
		g2.setColor(owner.getTheme().border());
		g2.drawRect(x, y, Math.max(0, barW - 1), Math.max(0, barH - 1));
		g2.dispose();
	}
}
