package application.gui.ui;

import java.awt.Color;
import java.awt.Graphics;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;

import application.gui.GuiTheme;

/**
 * Dark-themed combo box UI that suppresses default light bevel borders.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class ThemedComboBoxUI extends BasicComboBoxUI {
	/**
	 * theme field.
	 */
	private final GuiTheme theme;

	/**
	 * Constructor.
	 * @param theme parameter.
	 */
	public ThemedComboBoxUI(GuiTheme theme) {
		this.theme = theme;
	}

	@Override
	/**
	 * createArrowButton method.
	 *
	 * @return return value.
	 */
	protected JButton createArrowButton() {
		JButton button = new JButton("▾");
		button.setFocusable(false);
		button.setBorder(new EmptyBorder(0, 8, 0, 8));
		button.setForeground(theme.textMuted());
		button.setBackground(theme.surfaceAlt());
		button.setOpaque(true);
		button.setContentAreaFilled(true);
		button.setBorderPainted(false);
		return button;
	}

	@Override
	/**
	 * createRenderer method.
	 *
	 * @return return value.
	 */
	protected ListCellRenderer<Object> createRenderer() {
		return new javax.swing.DefaultListCellRenderer() {
			@Override
			public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				java.awt.Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				Color bg = isSelected ? theme.selection() : theme.surfaceAlt();
				list.setBackground(theme.surfaceAlt());
				list.setForeground(theme.text());
				list.setSelectionBackground(theme.selection());
				list.setSelectionForeground(theme.textStrong());
				c.setBackground(bg);
				c.setForeground(isSelected ? theme.textStrong() : theme.text());
				return c;
			}
		};
	}

	@Override
	/**
	 * installUI method.
	 *
	 * @param c parameter.
	 */
	public void installUI(JComponent c) {
		super.installUI(c);
		if (c instanceof JComboBox<?> combo) {
			combo.setOpaque(true);
			combo.setBackground(theme.surfaceAlt());
			combo.setForeground(theme.text());
			Border border = new MatteBorder(1, 1, 1, 1, theme.border());
			combo.setBorder(border);
			combo.setFocusable(false);
		}
	}

	@Override
	/**
	 * paint method.
	 *
	 * @param g parameter.
	 * @param c parameter.
	 */
	public void paint(Graphics g, JComponent c) {
		super.paint(g, c);
		g.setColor(theme.border());
		g.drawRect(0, 0, c.getWidth() - 1, c.getHeight() - 1);
		if (arrowButton != null) {
			int x = arrowButton.getX();
			g.drawLine(x, 1, x, c.getHeight() - 2);
		}
	}
}
