package application.gui.ui;

import application.gui.GuiTheme;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/**
 * Split pane UI that paints a themed divider.
 *
 * Overrides the divider to use the configured border color with zero insets so split bars stay thin and aligned with the surrounding theme.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class ThemedSplitPaneUI extends BasicSplitPaneUI {
	/**
	 * theme field.
	 */
	private final GuiTheme theme;

	/**
	 * @param theme theme used for divider colors.
	 */
	public ThemedSplitPaneUI(GuiTheme theme) {
		this.theme = theme;
	}

	@Override
	/**
	 * createDefaultDivider method.
	 *
	 * @return return value.
	 */
	public BasicSplitPaneDivider createDefaultDivider() {
		return new ThemedDivider(this, theme.border());
	}

	/**
	 * Class declaration.
	  *
	  * @since 2026
	  * @author Lennart A. Conrad
	 */
	private static final class ThemedDivider extends BasicSplitPaneDivider {

		@java.io.Serial
		private static final long serialVersionUID = 1L;
		/**
		 * color field.
		 */
		private final Color color;

		/**
		 * Constructor.
		 * @param ui parameter.
		 * @param color parameter.
		 */
		private ThemedDivider(BasicSplitPaneUI ui, Color color) {
			super(ui);
			this.color = color;
			setBackground(color);
			setBorder(new EmptyBorder(0, 0, 0, 0));
		}

		@Override
		/**
		 * paint method.
		 *
		 * @param g parameter.
		 */
		public void paint(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setColor(color);
			g2.fillRect(0, 0, getWidth(), getHeight());
			g2.dispose();
		}
	}
}
