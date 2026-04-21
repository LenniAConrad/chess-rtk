package application.gui.render;

import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

import application.gui.GuiTheme;
import application.gui.window.GuiWindowHistory;

/**
 * Default list renderer that applies theme hover colors.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class ThemedListCellRenderer extends DefaultListCellRenderer {

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
	public ThemedListCellRenderer(GuiWindowHistory owner) {
		this.owner = owner;
		setOpaque(true);
	}

	@Override
	/**
	 * getListCellRendererComponent method.
	 *
	 * @param list parameter.
	 * @param value parameter.
	 * @param index parameter.
	 * @param isSelected parameter.
	 * @param cellHasFocus parameter.
	 * @return return value.
	 */
	public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
			boolean cellHasFocus) {
		super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
		GuiTheme theme = owner.getTheme();
		if (!isSelected && owner.isListHover(list, index)) {
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
		}
		return this;
	}
}
