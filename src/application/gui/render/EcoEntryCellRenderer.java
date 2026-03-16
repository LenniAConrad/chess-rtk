package application.gui.render;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;

import application.gui.GuiTheme;
import application.gui.window.GuiWindowHistory;
import chess.eco.Entry;

/**
 * Compact ECO list renderer styled like a VS Code explorer list.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class EcoEntryCellRenderer extends JLabel implements ListCellRenderer<Entry> {
	/**
	 * owner field.
	 */
	private final GuiWindowHistory owner;

	/**
	 * Constructor.
	 * @param owner parameter.
	 */
	public EcoEntryCellRenderer(GuiWindowHistory owner) {
		this.owner = owner;
		setOpaque(true);
		setBorder(new EmptyBorder(4, 6, 4, 6));
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
	public Component getListCellRendererComponent(JList<? extends Entry> list, Entry value, int index,
			boolean isSelected, boolean cellHasFocus) {
		GuiTheme theme = owner.getTheme();
		String eco = value != null ? safe(value.getECO()) : "";
		String name = value != null ? safe(value.getName()) : "";
		String moves = value != null ? safe(value.getMovetext()) : "";
		String muted = colorHex(theme.textMuted());
		String strong = colorHex(theme.text());
		String moveText = owner.truncate(moves, 80);
		String html = "<html><span style='color:" + strong + "; font-weight:600;'>" + eco + "</span> "
				+ "<span style='color:" + muted + ";'>" + name + "</span><br>"
				+ "<span style='color:" + muted + ";'>" + moveText + "</span></html>";
		/**
		 * setText method.
		 *
		 * @param html parameter.
		 */
		setText(html);
		/**
		 * setFont method.
		 *
		 * @param ownerscaleFontthemebodyFont parameter.
		 */
		setFont(owner.scaleFont(theme.bodyFont()));
		if (isSelected) {
			/**
			 * setBackground method.
			 *
			 * @param themeselection parameter.
			 */
			setBackground(theme.selection());
		} else if (owner.isListHover(list, index)) {
			/**
			 * setBackground method.
			 *
			 * @param themehover parameter.
			 */
			setBackground(theme.hover());
		} else {
			/**
			 * setBackground method.
			 *
			 * @param themesidebar parameter.
			 */
			setBackground(theme.sidebar());
		}
		return this;
	}

	/**
	 * colorHex method.
	 * @param color parameter.
	 * @return return value.
	 */
	private String colorHex(Color color) {
		if (color == null) {
			return "#cccccc";
		}
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	/**
	 * safe method.
	 * @param value parameter.
	 * @return return value.
	 */
	private String safe(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
