package application.gui.ui;

import application.gui.GuiTheme;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.plaf.basic.BasicTabbedPaneUI;

/**
 * Flat, underline-style tab UI tuned to the GUI theme.
 *
 * Draws a single accent underline for the selected tab while keeping borders and focus indicators hidden for a streamlined presentation.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class ThemedTabbedPaneUI extends BasicTabbedPaneUI {
	/**
	 * theme field.
	 */
	private final GuiTheme theme;

	/**
	 * @param theme theme used for tab colors.
	 */
	public ThemedTabbedPaneUI(GuiTheme theme) {
		this.theme = theme;
	}

		/**
	 * Handles install defaults.
	 */
@Override
	/**
	 * installDefaults method.
	 */
	protected void installDefaults() {
		super.installDefaults();
		tabAreaInsets = new Insets(4, 8, 4, 8);
		tabInsets = new Insets(8, 16, 8, 16);
		selectedTabPadInsets = new Insets(0, 0, 0, 0);
		contentBorderInsets = new Insets(0, 0, 0, 0);
	}

	/**
	 * hideTabs method.
	 * @return return value.
	 */
	private boolean hideTabs() {
		return tabPane != null && Boolean.TRUE.equals(tabPane.getClientProperty("hideTabs"));
	}

	/**
	 * editorTabs method.
	 * @return return value.
	 */
	private boolean editorTabs() {
		return tabPane != null && Boolean.TRUE.equals(tabPane.getClientProperty("editorTabs"));
	}

		/**
	 * Handles calculate tab area height.
	 * @param tabPlacement tab placement value
	 * @param horizRunCount horiz run count value
	 * @param maxTabHeight max tab height value
	 * @return computed value
	 */
@Override
	/**
	 * calculateTabAreaHeight method.
	 *
	 * @param tabPlacement parameter.
	 * @param horizRunCount parameter.
	 * @param maxTabHeight parameter.
	 * @return return value.
	 */
	protected int calculateTabAreaHeight(int tabPlacement, int horizRunCount, int maxTabHeight) {
		if (hideTabs()) {
			return 0;
		}
		return super.calculateTabAreaHeight(tabPlacement, horizRunCount, maxTabHeight);
	}

		/**
	 * Handles calculate tab area width.
	 * @param tabPlacement tab placement value
	 * @param vertRunCount vert run count value
	 * @param maxTabWidth max tab width value
	 * @return computed value
	 */
@Override
	/**
	 * calculateTabAreaWidth method.
	 *
	 * @param tabPlacement parameter.
	 * @param vertRunCount parameter.
	 * @param maxTabWidth parameter.
	 * @return return value.
	 */
	protected int calculateTabAreaWidth(int tabPlacement, int vertRunCount, int maxTabWidth) {
		if (hideTabs()) {
			return 0;
		}
		return super.calculateTabAreaWidth(tabPlacement, vertRunCount, maxTabWidth);
	}

		/**
	 * Returns the tab area insets.
	 * @param tabPlacement tab placement value
	 * @return computed value
	 */
@Override
	/**
	 * getTabAreaInsets method.
	 *
	 * @param tabPlacement parameter.
	 * @return return value.
	 */
	protected Insets getTabAreaInsets(int tabPlacement) {
		if (hideTabs()) {
			return new Insets(0, 0, 0, 0);
		}
		return super.getTabAreaInsets(tabPlacement);
	}

		/**
	 * Handles paint tab background.
	 * @param g g value
	 * @param tabPlacement tab placement value
	 * @param tabIndex tab index value
	 * @param x x value
	 * @param y y value
	 * @param w w value
	 * @param h h value
	 * @param isSelected is selected value
	 */
@Override
	/**
	 * paintTabBackground method.
	 *
	 * @param g parameter.
	 * @param tabPlacement parameter.
	 * @param tabIndex parameter.
	 * @param x parameter.
	 * @param y parameter.
	 * @param w parameter.
	 * @param h parameter.
	 * @param isSelected parameter.
	 */
	protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h,
			boolean isSelected) {
		if (hideTabs()) {
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		if (isSelected) {
			g2.setColor(editorTabs() ? theme.editor() : theme.surfaceAlt());
			g2.fillRoundRect(x + 2, y + 2, Math.max(0, w - 4), Math.max(0, h - 4), 10, 10);
		} else {
			g2.setColor(editorTabs() ? theme.surfaceAlt() : theme.sidebar());
			g2.fillRect(x, y, w, h);
		}
		g2.dispose();
	}

		/**
	 * Handles paint tab area.
	 * @param g g value
	 * @param tabPlacement tab placement value
	 * @param selectedIndex selected index value
	 */
@Override
	/**
	 * paintTabArea method.
	 *
	 * @param g parameter.
	 * @param tabPlacement parameter.
	 * @param selectedIndex parameter.
	 */
	protected void paintTabArea(Graphics g, int tabPlacement, int selectedIndex) {
		if (hideTabs()) {
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setColor(editorTabs() ? theme.editor() : theme.sidebar());
		g2.fillRect(0, 0, tabPane.getWidth(), tabPane.getHeight());
		g2.dispose();
		super.paintTabArea(g, tabPlacement, selectedIndex);
	}

		/**
	 * Handles paint tab border.
	 * @param g g value
	 * @param tabPlacement tab placement value
	 * @param tabIndex tab index value
	 * @param x x value
	 * @param y y value
	 * @param w w value
	 * @param h h value
	 * @param isSelected is selected value
	 */
@Override
	/**
	 * paintTabBorder method.
	 *
	 * @param g parameter.
	 * @param tabPlacement parameter.
	 * @param tabIndex parameter.
	 * @param x parameter.
	 * @param y parameter.
	 * @param w parameter.
	 * @param h parameter.
	 * @param isSelected parameter.
	 */
	protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h,
			boolean isSelected) {
		if (hideTabs()) {
			return;
		}
		if (!isSelected) {
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(theme.accent());
		/**
		 * underlineY field.
		 */
		int underlineY = y + h - 1;
		int pad = Math.max(6, w / 12);
		g2.fillRoundRect(x + pad, underlineY, w - pad * 2, 1, 6, 6);
		g2.dispose();
	}

		/**
	 * Handles paint text.
	 * @param g g value
	 * @param tabPlacement tab placement value
	 * @param font font value
	 * @param metrics metrics value
	 * @param tabIndex tab index value
	 * @param title title value
	 * @param textRect text rect value
	 * @param isSelected is selected value
	 */
@Override
	/**
	 * paintText method.
	 *
	 * @param g parameter.
	 * @param tabPlacement parameter.
	 * @param font parameter.
	 * @param metrics parameter.
	 * @param tabIndex parameter.
	 * @param title parameter.
	 * @param textRect parameter.
	 * @param isSelected parameter.
	 */
	protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics, int tabIndex, String title,
			Rectangle textRect, boolean isSelected) {
		if (hideTabs()) {
			return;
		}
		g.setFont(font);
		g.setColor(isSelected ? theme.textStrong() : theme.textMuted());
		/**
		 * x field.
		 */
		int x = textRect.x;
		int y = textRect.y + metrics.getAscent();
		g.drawString(title, x, y);
	}

		/**
	 * Handles paint content border.
	 * @param g g value
	 * @param tabPlacement tab placement value
	 * @param selectedIndex selected index value
	 */
@Override
	/**
	 * paintContentBorder method.
	 *
	 * @param g parameter.
	 * @param tabPlacement parameter.
	 * @param selectedIndex parameter.
	 */
	protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setColor(editorTabs() ? theme.editor() : theme.sidebar());
		g2.fillRect(0, 0, tabPane.getWidth(), tabPane.getHeight());
		g2.dispose();
	}

		/**
	 * Handles paint focus indicator.
	 * @param g g value
	 * @param tabPlacement tab placement value
	 * @param rects rects value
	 * @param tabIndex tab index value
	 * @param iconRect icon rect value
	 * @param textRect text rect value
	 * @param isSelected is selected value
	 */
@Override
	/**
	 * paintFocusIndicator method.
	 *
	 * @param g parameter.
	 * @param tabPlacement parameter.
	 * @param rects parameter.
	 * @param tabIndex parameter.
	 * @param iconRect parameter.
	 * @param textRect parameter.
	 * @param isSelected parameter.
	 */
	protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex,
			Rectangle iconRect, Rectangle textRect, boolean isSelected) {
		// Suppress the dotted focus ring for a cleaner look.
	}

}
