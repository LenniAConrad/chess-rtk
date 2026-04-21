package application.gui.render;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import application.gui.GuiTheme;
import application.gui.window.GuiWindowHistory;
import application.gui.window.PgnNode;
import application.gui.model.HistoryEntry;

/**
 * Compact list renderer for move tree rows.
 *
 * Renders move prefixes, SAN strings, and glyph columns with indentation and themed highlighting so the history list reveals depth, current selection, and annotations.
 *
 * @param owner history window that supplies fonts, colors, and glyph helpers.
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class TreeCellRenderer extends JPanel implements javax.swing.ListCellRenderer<HistoryEntry> {

		/**
	 * Serialization version identifier.
	 */
@java.io.Serial
	private static final long serialVersionUID = 1L;
	/**
	 * Owner window supplying theme details.
	 */
	private final GuiWindowHistory owner;
	/**
	 * Label showing move prefixes.
	 */
	private final JLabel numberLabel = new JLabel();
	/**
	 * Label showing SAN text.
	 */
	private final JLabel moveLabel = new JLabel();
	/**
	 * Label showing glyph/annotation icons.
	 */
	private final JLabel glyphLabel = new JLabel();
	/**
	 * Container for the number and move labels.
	 */
	private final JPanel leftPanel = new JPanel(new BorderLayout(6, 0));

	/**
	 * @param owner history window for fonts/colors.
	 */
	public TreeCellRenderer(GuiWindowHistory owner) {
		this.owner = owner;
		setLayout(new BorderLayout(6, 0));
		setOpaque(true);
		leftPanel.setOpaque(false);
		numberLabel.setOpaque(false);
		moveLabel.setOpaque(false);
		glyphLabel.setOpaque(false);
		numberLabel.setHorizontalAlignment(SwingConstants.LEFT);
		moveLabel.setHorizontalAlignment(SwingConstants.LEFT);
		glyphLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		leftPanel.add(numberLabel, BorderLayout.WEST);
		leftPanel.add(moveLabel, BorderLayout.CENTER);
		add(leftPanel, BorderLayout.CENTER);
		add(glyphLabel, BorderLayout.EAST);
		setBorder(new EmptyBorder(2, 6, 2, 6));
	}

		/**
	 * Returns the list cell renderer component.
	 * @param list list value
	 * @param value value value
	 * @param index index value
	 * @param isSelected is selected value
	 * @param cellHasFocus cell has focus value
	 * @return computed value
	 */
@Override
	public java.awt.Component getListCellRendererComponent(javax.swing.JList<? extends HistoryEntry> list,
			HistoryEntry value, int index, boolean isSelected, boolean cellHasFocus) {
		String prefix = value != null ? value.prefix() : "";
		String san = value != null ? value.san() : "";
		int depth = value != null ? value.depth() : 0;
		numberLabel.setText(prefix == null ? "" : prefix);
		moveLabel.setText(san == null ? "" : owner.displaySan(san));
		glyphLabel.setText(historyGlyph(value));

		GuiTheme theme = owner.getTheme();
		Font base = owner.scaleFont(theme.bodyFont());
		Font numberFont = base.deriveFont(Font.PLAIN, Math.max(10f, base.getSize2D() * 0.9f));
		Font moveFont = owner.figurineDisplayFont(base);
		numberLabel.setFont(numberFont);
		moveLabel.setFont(moveFont);
		glyphLabel.setFont(base.deriveFont(Font.BOLD));
		float uiScale = owner.getUiScale();
		int numberWidth = Math.round(36 * uiScale);
		numberLabel.setPreferredSize(new Dimension(numberWidth, numberLabel.getPreferredSize().height));
		int indent = Math.max(0, depth) * Math.round(12 * uiScale);
		/**
		 * setBorder method.
		 *
		 * @param 6 parameter.
		 */
		setBorder(new EmptyBorder(2, 6 + indent, 2, 6));

		Color bg = theme.sidebar();
		Color fg = theme.text();
		Color rightFg = theme.textMuted();
		Color numFg = theme.textMuted();
		if (isSelected) {
			bg = theme.selection();
			fg = theme.textStrong();
			rightFg = theme.textStrong();
			numFg = theme.textMuted();
		} else if (owner.isListHover(list, index)) {
			bg = theme.hover();
		} else if (value != null && value.current()) {
			bg = owner.isLightMode() ? new Color(255, 243, 191) : new Color(87, 74, 41);
			fg = theme.textStrong();
			rightFg = theme.textMuted();
			numFg = theme.textMuted();
		}
		/**
		 * setBackground method.
		 *
		 * @param bg parameter.
		 */
		setBackground(bg);
		numberLabel.setForeground(numFg);
		moveLabel.setForeground(fg);
		glyphLabel.setForeground(rightFg);
		return this;
	}

	/**
	 * Returns the glyph string representing evaluation flags and comments.
	 *
	 * @param entry history entry to inspect.
	 * @return glyph string (e.g., NAG, check/mate hints, comments).
	 */
	private String historyGlyph(HistoryEntry entry) {
		if (entry == null || entry.san() == null || entry.san().isBlank()) {
			return "";
		}
		PgnNode node = entry.node();
		String san = entry.san();
		String nag = owner.nagGlyph(node);
		boolean hasComment = node != null && node.getComment() != null && !node.getComment().isBlank();
		String commentMark = hasComment ? "*" : "";
		if (!nag.isEmpty()) {
			return nag + commentMark;
		}
		if (san.contains("!!")) {
			return "!!";
		}
		if (san.contains("??")) {
			return "??";
		}
		if (san.contains("!?")) {
			return "!?";
		}
		if (san.contains("?!")) {
			return "?!";
		}
		if (san.contains("!")) {
			return "!";
		}
		if (san.contains("?")) {
			return "?";
		}
		if (san.contains("#")) {
			return "M";
		}
		if (san.contains("+")) {
			return "+";
		}
		return commentMark;
	}
}
