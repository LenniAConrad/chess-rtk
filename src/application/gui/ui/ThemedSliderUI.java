package application.gui.ui;

import application.gui.GuiTheme;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JSlider;
import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicSliderUI;

/**
 * Custom slider UI that matches the GUI theme.
 *
 * Paints rounded tracks/thumbs with accent fills and smooth animation so slider movements stay in sync with the rest of the themed controls.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class ThemedSliderUI extends BasicSliderUI {
	/**
	 * theme field.
	 */
	private final GuiTheme theme;
	/**
	 * animatedValue field.
	 */
	private float animatedValue;
	/**
	 * targetValue field.
	 */
	private int targetValue;
	/**
	 * timer field.
	 */
	private Timer timer;
	/**
	 * changeListener field.
	 */
	private ChangeListener changeListener;

	/**
	 * @param slider slider to decorate.
	 * @param theme theme providing colors.
	 */
	public ThemedSliderUI(JSlider slider, GuiTheme theme) {
		super(slider);
		this.theme = theme;
		this.animatedValue = slider.getValue();
		this.targetValue = slider.getValue();
	}

		/**
	 * Handles install ui.
	 * @param c c value
	 */
@Override
	/**
	 * installUI method.
	 *
	 * @param c parameter.
	 */
	public void installUI(JComponent c) {
		super.installUI(c);
		animatedValue = slider.getValue();
		targetValue = slider.getValue();
		changeListener = e -> onSliderChanged();
		slider.addChangeListener(changeListener);
	}

		/**
	 * Handles uninstall ui.
	 * @param c c value
	 */
@Override
	/**
	 * uninstallUI method.
	 *
	 * @param c parameter.
	 */
	public void uninstallUI(JComponent c) {
		if (timer != null) {
			timer.stop();
			timer = null;
		}
		if (changeListener != null) {
			slider.removeChangeListener(changeListener);
			changeListener = null;
		}
		super.uninstallUI(c);
	}

		/**
	 * Handles paint track.
	 * @param g g value
	 */
@Override
	/**
	 * paintTrack method.
	 *
	 * @param g parameter.
	 */
	public void paintTrack(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int trackThickness = 6;
		int x = trackRect.x;
		int y = trackRect.y;
		int w = trackRect.width;
		int h = trackRect.height;
		float frac = fraction();
		g2.setColor(theme.surfaceAlt());
		if (slider.getOrientation() == SwingConstants.HORIZONTAL) {
			int trackY = y + (h - trackThickness) / 2;
			g2.fillRoundRect(x, trackY, w, trackThickness, trackThickness, trackThickness);
			int fill = Math.round(w * frac);
			fill = Math.max(0, Math.min(w, fill));
			g2.setColor(theme.accent());
			g2.fillRoundRect(x, trackY, fill, trackThickness, trackThickness, trackThickness);
		} else {
			int trackX = x + (w - trackThickness) / 2;
			g2.fillRoundRect(trackX, y, trackThickness, h, trackThickness, trackThickness);
			int fill = Math.round(h * frac);
			fill = Math.max(0, Math.min(h, fill));
			g2.setColor(theme.accent());
			g2.fillRoundRect(trackX, y + h - fill, trackThickness, fill, trackThickness, trackThickness);
		}
		g2.dispose();
	}

		/**
	 * Handles paint thumb.
	 * @param g g value
	 */
@Override
	/**
	 * paintThumb method.
	 *
	 * @param g parameter.
	 */
	public void paintThumb(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int size = getThumbSize().width;
		float frac = fraction();
		int x;
		int y;
		if (slider.getOrientation() == SwingConstants.HORIZONTAL) {
			x = trackRect.x + Math.round((trackRect.width - size) * frac);
			y = trackRect.y + (trackRect.height - size) / 2;
		} else {
			x = trackRect.x + (trackRect.width - size) / 2;
			y = trackRect.y + Math.round((trackRect.height - size) * (1f - frac));
		}
		g2.setColor(theme.surface());
		g2.fillOval(x, y, size, size);
		g2.setColor(theme.accent());
		g2.drawOval(x, y, size, size);
		g2.dispose();
	}

		/**
	 * Returns the thumb size.
	 * @return computed value
	 */
@Override
	/**
	 * getThumbSize method.
	 *
	 * @return return value.
	 */
	protected Dimension getThumbSize() {
		return new Dimension(18, 18);
	}

	/**
	 * onSliderChanged method.
	 */
	private void onSliderChanged() {
		int value = slider.getValue();
		if (slider.getValueIsAdjusting()) {
			animatedValue = value;
			targetValue = value;
			stopTimer();
			slider.repaint();
			return;
		}
		targetValue = value;
		startTimer();
	}

	/**
	 * startTimer method.
	 */
	private void startTimer() {
		if (timer == null) {
			timer = new Timer(16, e -> animateStep());
			timer.setRepeats(true);
		}
		if (!timer.isRunning()) {
			timer.start();
		}
	}

	/**
	 * stopTimer method.
	 */
	private void stopTimer() {
		if (timer != null && timer.isRunning()) {
			timer.stop();
		}
	}

	/**
	 * animateStep method.
	 */
	private void animateStep() {
		float diff = targetValue - animatedValue;
		if (Math.abs(diff) < 0.5f) {
			animatedValue = targetValue;
			stopTimer();
			slider.repaint();
			return;
		}
		animatedValue += diff * 0.25f;
		slider.repaint();
	}

	/**
	 * fraction method.
	 * @return return value.
	 */
	private float fraction() {
		int min = slider.getMinimum();
		int max = slider.getMaximum();
		if (max == min) {
			return 0f;
		}
		float v = animatedValue;
		if (v < min) {
			v = min;
		}
		if (v > max) {
			v = max;
		}
		float frac = (v - min) / (float) (max - min);
		if (slider.getInverted()) {
			frac = 1f - frac;
		}
		return Math.max(0f, Math.min(1f, frac));
	}
}
