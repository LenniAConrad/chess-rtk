package application.gui.render;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import application.gui.GuiTheme;
import application.gui.window.GuiWindowHistory;

/**
 * Table renderer that adds subtle hover highlighting.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class ThemedTableCellRenderer extends DefaultTableCellRenderer {

	@java.io.Serial
	private static final long serialVersionUID = 1L;
	/**
	 * owner field.
	 */
	private final GuiWindowHistory owner;

	/**
	 * Constructor.
	 * @param owner parameter.
	 */
	public ThemedTableCellRenderer(GuiWindowHistory owner) {
		this.owner = owner;
		setOpaque(true);
	}

	@Override
	/**
	 * getTableCellRendererComponent method.
	 *
	 * @param table parameter.
	 * @param value parameter.
	 * @param isSelected parameter.
	 * @param hasFocus parameter.
	 * @param row parameter.
	 * @param column parameter.
	 * @return return value.
	 */
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
			int row, int column) {
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		GuiTheme theme = owner.getTheme();
		if (!isSelected && owner.isTableHover(table, row)) {
			/**
			 * setBackground method.
			 *
			 * @param themehover parameter.
			 */
			setBackground(theme.hover());
			/**
			 * setForeground method.
			 *
			 * @param themetext parameter.
			 */
			setForeground(theme.text());
		} else if (!isSelected) {
			/**
			 * setBackground method.
			 *
			 * @param themesidebar parameter.
			 */
			setBackground(theme.sidebar());
			/**
			 * setForeground method.
			 *
			 * @param themetext parameter.
			 */
			setForeground(theme.text());
		}
		return this;
	}
}
