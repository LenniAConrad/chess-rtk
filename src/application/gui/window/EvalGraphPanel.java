package application.gui.window;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

/**
 * Lightweight evaluation graph for mainline positions.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
final class EvalGraphPanel extends JPanel {

		/**
	 * Serialization version identifier.
	 */
@java.io.Serial
	private static final long serialVersionUID = 1L;
	/**
	 * MAX_CP field.
	 */
	private static final int MAX_CP = 1000;
	/**
	 * PAD field.
	 */
	private static final int PAD = 10;
	/**
	 * LABEL_PAD_X field.
	 */
	private static final int LABEL_PAD_X = 6;
	/**
	 * LABEL_PAD_Y field.
	 */
	private static final int LABEL_PAD_Y = 3;
	/**
	 * owner field.
	 */
	private final GuiWindowHistory owner;
	/**
	 * of method.
	 */
	private List<Double> values = List.of();
	/**
	 * currentIndex field.
	 */
	private int currentIndex = -1;
	/**
	 * currentLabel field.
	 */
	private String currentLabel = null;

	/**
	 * Constructor.
	 * @param owner parameter.
	 */
	EvalGraphPanel(GuiWindowHistory owner) {
		this.owner = owner;
		setOpaque(false);
		setPreferredSize(new Dimension(280, 140));
		setMinimumSize(new Dimension(200, 120));
	}

	/**
	 * setSeries method.
	 * @param values parameter.
	 * @param currentIndex parameter.
	 * @param currentLabel parameter.
	 */
	void setSeries(List<Double> values, int currentIndex, String currentLabel) {
		this.values = values == null ? List.of() : new ArrayList<>(values);
		this.currentIndex = currentIndex;
		this.currentLabel = currentLabel;
		repaint();
	}

		/**
	 * Handles paint component.
	 * @param g g value
	 */
@Override
	/**
	 * paintComponent method.
	 *
	 * @param g parameter.
	 */
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		owner.applyRenderHints(g2);
		int w = getWidth();
		int h = getHeight();
		if (w <= 0 || h <= 0) {
			g2.dispose();
			return;
		}
		int left = PAD;
		int top = PAD;
		int right = w - PAD;
		int bottom = h - PAD;
		int plotW = Math.max(1, right - left);
		int plotH = Math.max(1, bottom - top);

		List<Double> series = values;
		if (series.isEmpty()) {
			drawEmptyState(g2, left, top, plotW, plotH);
			g2.dispose();
			return;
		}

		Color axis = owner.theme.border();
		Color zeroLine = owner.blend(owner.theme.textMuted(), owner.theme.surfaceAlt(), 0.6f);
		Color line = owner.theme.accent();
		Color point = owner.theme.textStrong();
		Color marker = owner.theme.accent();

		int midY = top + plotH / 2;
		g2.setColor(zeroLine);
		g2.setStroke(new BasicStroke(1f));
		g2.drawLine(left, midY, right, midY);
		g2.setColor(axis);
		g2.drawRect(left, top, plotW, plotH);

		int count = series.size();
		double step = count <= 1 ? 0.0 : (double) plotW / (double) (count - 1);
		List<int[]> points = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Double value = series.get(i);
			if (value == null) {
				points.add(null);
				continue;
			}
			double clamped = Math.max(-MAX_CP, Math.min(MAX_CP, value));
			double norm = (clamped / MAX_CP + 1.0) / 2.0;
			int x = left + (int) Math.round(step * i);
			int y = top + (int) Math.round((1.0 - norm) * plotH);
			points.add(new int[] { x, y });
		}

		g2.setColor(line);
		g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		for (int i = 1; i < points.size(); i++) {
			int[] prev = points.get(i - 1);
			int[] cur = points.get(i);
			if (prev == null || cur == null) {
				continue;
			}
			g2.drawLine(prev[0], prev[1], cur[0], cur[1]);
		}

		g2.setColor(point);
		for (int i = 0; i < points.size(); i++) {
			int[] p = points.get(i);
			if (p == null) {
				continue;
			}
			g2.fillOval(p[0] - 2, p[1] - 2, 4, 4);
		}

		if (currentIndex >= 0 && currentIndex < count) {
			int x = left + (int) Math.round(step * currentIndex);
			g2.setColor(new Color(marker.getRed(), marker.getGreen(), marker.getBlue(), 120));
			g2.setStroke(new BasicStroke(2f));
			g2.drawLine(x, top, x, bottom);
			int[] currentPoint = points.get(currentIndex);
			if (currentPoint != null) {
				g2.setColor(marker);
				g2.fillOval(currentPoint[0] - 4, currentPoint[1] - 4, 8, 8);
			}
			if (currentLabel != null && !currentLabel.isBlank()) {
				drawLabel(g2, currentLabel, x, top, plotW);
			}
		}

		g2.dispose();
	}

	/**
	 * drawEmptyState method.
	 * @param g2 parameter.
	 * @param left parameter.
	 * @param top parameter.
	 * @param plotW parameter.
	 * @param plotH parameter.
	 */
	private void drawEmptyState(Graphics2D g2, int left, int top, int plotW, int plotH) {
		g2.setColor(owner.theme.border());
		g2.drawRect(left, top, plotW, plotH);
		g2.setColor(owner.theme.textMuted());
		Font base = owner.theme.bodyFont();
		Font font = base.deriveFont(Font.PLAIN, Math.max(10f, base.getSize2D() * 0.95f));
		g2.setFont(font);
		String text = "No eval data yet";
		java.awt.FontMetrics fm = g2.getFontMetrics();
		int textW = fm.stringWidth(text);
		int textH = fm.getHeight();
		int x = left + (plotW - textW) / 2;
		int y = top + (plotH + textH) / 2 - fm.getDescent();
		g2.drawString(text, x, y);
	}

	/**
	 * drawLabel method.
	 * @param g2 parameter.
	 * @param text parameter.
	 * @param x parameter.
	 * @param top parameter.
	 * @param plotW parameter.
	 */
	private void drawLabel(Graphics2D g2, String text, int x, int top, int plotW) {
		Font base = owner.theme.monoFont();
		Font font = base.deriveFont(Font.BOLD, Math.max(10f, base.getSize2D() * 0.9f));
		g2.setFont(font);
		java.awt.FontMetrics fm = g2.getFontMetrics();
		int textW = fm.stringWidth(text);
		int textH = fm.getHeight();
		int labelW = textW + LABEL_PAD_X * 2;
		int labelH = textH + LABEL_PAD_Y * 2;
		int labelX = Math.min(Math.max(x - labelW / 2, PAD), PAD + plotW - labelW);
		int labelY = top + LABEL_PAD_Y;
		Color bg = owner.theme.surface();
		Color border = owner.theme.border();
		Color fg = owner.theme.textStrong();
		g2.setColor(bg);
		g2.fillRoundRect(labelX, labelY, labelW, labelH, 8, 8);
		g2.setColor(border);
		g2.drawRoundRect(labelX, labelY, labelW, labelH, 8, 8);
		g2.setColor(fg);
		g2.drawString(text, labelX + LABEL_PAD_X, labelY + LABEL_PAD_Y + fm.getAscent());
	}
}
