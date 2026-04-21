package application.gui.render;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

import application.gui.GuiTheme;
import application.gui.window.GuiWindowHistory;
import application.gui.model.PvEntry;

/**
 * Renderer for engine PV rows with eval/depth chips.
 *
 * Draws the PV index, truncated move string, and themed chips so each list row shows eval/depth data alongside the move sequence.
 *
 * @param owner history window supplying theme colors and fonts.
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class PvCellRenderer extends JPanel implements javax.swing.ListCellRenderer<PvEntry> {

	@java.io.Serial
	private static final long serialVersionUID = 1L;
	/**
	 * History window that supplies theme data.
	 */
	private final GuiWindowHistory owner;
	/**
	 * Label showing the PV index.
	 */
	private final JLabel toggleLabel = new JLabel();
	/**
	 * Label showing the PV index.
	 */
	private final JLabel indexLabel = new JLabel();
	/**
	 * Container for expand icon and PV index.
	 */
	private final JPanel leadPanel = new JPanel(new BorderLayout(4, 0));
	/**
	 * Label showing the PV move string.
	 */
	private final JTextArea movesArea = new JTextArea();
	/**
	 * Chip displaying the evaluation label.
	 */
	private final JLabel evalChip = new JLabel();
	/**
	 * Chip displaying the depth.
	 */
	private final JLabel depthChip = new JLabel();
	/**
	 * Container holding the chips on the row.
	 */
	private final JPanel chipRow = new JPanel(new java.awt.GridLayout(1, 0, 6, 0));

	/**
	 * @param owner window providing fonts/colors.
	 */
	public PvCellRenderer(GuiWindowHistory owner) {
		this.owner = owner;
		setLayout(new BorderLayout(8, 0));
		setOpaque(true);
		toggleLabel.setHorizontalAlignment(SwingConstants.CENTER);
		toggleLabel.setOpaque(false);
		indexLabel.setOpaque(true);
		indexLabel.setHorizontalAlignment(SwingConstants.CENTER);
		movesArea.setEditable(false);
		movesArea.setFocusable(false);
		movesArea.setLineWrap(true);
		movesArea.setWrapStyleWord(true);
		movesArea.setOpaque(false);
		movesArea.setBorder(new EmptyBorder(0, 0, 0, 0));
		evalChip.setOpaque(true);
		depthChip.setOpaque(true);
		chipRow.setOpaque(false);
		chipRow.add(evalChip);
		chipRow.add(depthChip);
		leadPanel.setOpaque(false);
		leadPanel.add(toggleLabel, BorderLayout.WEST);
		leadPanel.add(indexLabel, BorderLayout.CENTER);
		add(leadPanel, BorderLayout.WEST);
		add(movesArea, BorderLayout.CENTER);
		add(chipRow, BorderLayout.EAST);
		setBorder(new EmptyBorder(4, 6, 4, 6));
	}

	@Override
	public java.awt.Component getListCellRendererComponent(javax.swing.JList<? extends PvEntry> list, PvEntry value,
			int index, boolean isSelected, boolean cellHasFocus) {
		String moves = value != null ? value.moves() : "";
		String eval = value != null ? value.eval() : "";
		String depth = value != null ? value.depth() : "";
		String pvText = value != null ? String.valueOf(value.pv()) : "";
		GuiTheme theme = owner.getTheme();

		indexLabel.setText(pvText);
		int hoverPly = hoveredPly(list, index);
		evalChip.setText(eval.isBlank() ? "—" : eval);
		depthChip.setText("d " + depth);

		Font base = owner.scaleFont(theme.bodyFont());
		Font movesFont = owner.figurineDisplayFont(base);
		Font chipFont = base.deriveFont(Font.PLAIN, Math.max(10f, base.getSize2D() * 0.85f));
		int movesWidthPx = owner.computePvMovesWidth(list, value);
		java.awt.FontMetrics baseFm = list.getFontMetrics(movesFont);
		boolean expanded = owner.isPvExpanded(value);
		boolean canExpand = owner.canExpandPvEntry(value, movesWidthPx, baseFm);
		String displayMoves = expanded ? owner.normalizePvText(moves) : owner.collapsePvText(moves, movesWidthPx, baseFm);
		movesArea.setFont(movesFont);
		movesArea.setLineWrap(expanded);
		movesArea.setWrapStyleWord(true);
		movesArea.setText(displayMoves);
		movesArea.setSize(new Dimension(movesWidthPx, Short.MAX_VALUE));
		Dimension areaPref = movesArea.getPreferredSize();
		movesArea.setPreferredSize(new Dimension(movesWidthPx, areaPref.height));
		/**
		 * applyHoverHighlight method.
		 *
		 * @param displayMoves parameter.
		 * @param hoverPly parameter.
		 * @param theme parameter.
		 */
		applyHoverHighlight(displayMoves, hoverPly, theme);
		indexLabel.setFont(base.deriveFont(Font.BOLD));
		evalChip.setFont(chipFont);
		depthChip.setFont(chipFont);
		toggleLabel.setFont(base.deriveFont(Font.BOLD));

		int indexSize = Math.round(26 * owner.getUiScale());
		indexLabel.setPreferredSize(new Dimension(indexSize, indexSize));
		indexLabel.setMinimumSize(new Dimension(indexSize, indexSize));
		int toggleSize = Math.max(12, Math.round(14 * owner.getUiScale()));
		toggleLabel.setPreferredSize(new Dimension(toggleSize, indexSize));
		toggleLabel.setMinimumSize(new Dimension(toggleSize, indexSize));
		toggleLabel.setText(canExpand ? (expanded ? "\u25be" : "\u25b8") : " ");
		toggleLabel.setForeground(canExpand ? theme.textMuted() : owner.blend(theme.textMuted(), theme.sidebar(), 0.4f));

		Color bg = theme.sidebar();
		if (isSelected) {
			bg = theme.selection();
		} else if (owner.isListHover(list, index)) {
			bg = theme.hover();
		}
		/**
		 * setBackground method.
		 *
		 * @param bg parameter.
		 */
		setBackground(bg);
		movesArea.setForeground(isSelected ? theme.textStrong() : theme.text());

		Color chipBg = owner.blend(theme.surfaceAlt(), theme.border(), 0.2f);
		Color chipBorder = theme.border();
		Color chipFg = theme.textMuted();
		evalChip.setBackground(chipBg);
		evalChip.setForeground(chipFg);
		evalChip.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(chipBorder),
				new EmptyBorder(2, 6, 2, 6)));
		depthChip.setBackground(chipBg);
		depthChip.setForeground(chipFg);
		depthChip.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(chipBorder),
				new EmptyBorder(2, 6, 2, 6)));

		indexLabel.setBackground(owner.blend(theme.accent(), theme.surfaceAlt(), 0.25f));
		indexLabel.setForeground(theme.textStrong());
		indexLabel.setBorder(BorderFactory.createLineBorder(theme.border()));

		return this;
	}

	/**
	 * hoveredPly method.
	 * @param list parameter.
	 * @param index parameter.
	 * @return return value.
	 */
	private int hoveredPly(javax.swing.JList<? extends PvEntry> list, int index) {
		if (owner.getPvHoverHighlightAmount() <= 0.001f) {
			return -1;
		}
		Object hoverIdxObj = list.getClientProperty("pvHoverMoveIndex");
		Object hoverPlyObj = list.getClientProperty("pvHoverPly");
		if (!(hoverIdxObj instanceof Integer) || !(hoverPlyObj instanceof Integer)) {
			return -1;
		}
		int hoverIdx = (Integer) hoverIdxObj;
		int hoverPly = (Integer) hoverPlyObj;
		if (hoverIdx != index || hoverPly <= 0) {
			return -1;
		}
		return hoverPly;
	}

	/**
	 * applyHoverHighlight method.
	 * @param text parameter.
	 * @param hoverPly parameter.
	 * @param theme parameter.
	 */
	private void applyHoverHighlight(String text, int hoverPly, GuiTheme theme) {
		Highlighter highlighter = movesArea.getHighlighter();
		highlighter.removeAllHighlights();
		if (text == null || text.isBlank() || hoverPly <= 0) {
			return;
		}
		int[] range = tokenRange(text, hoverPly);
		if (range == null) {
			return;
		}
		float amount = owner.getPvHoverHighlightAmount();
		float mix = 0.20f + 0.34f * amount;
		Color tokenBg = owner.blend(theme.accent(), theme.sidebar(), mix);
		try {
			highlighter.addHighlight(range[0], range[1], new DefaultHighlighter.DefaultHighlightPainter(tokenBg));
		} catch (BadLocationException ignored) {
			// ignore invalid highlight ranges
		}
	}

	/**
	 * tokenRange method.
	 * @param text parameter.
	 * @param tokenIndex1Based parameter.
	 * @return return value.
	 */
	private int[] tokenRange(String text, int tokenIndex1Based) {
		if (tokenIndex1Based <= 0 || text == null || text.isBlank()) {
			return null;
		}
		int token = 0;
		int i = 0;
		final int n = text.length();
		while (i < n) {
			while (i < n && Character.isWhitespace(text.charAt(i))) {
				i++;
			}
			if (i >= n) {
				break;
			}
			int start = i;
			while (i < n && !Character.isWhitespace(text.charAt(i))) {
				i++;
			}
			int end = i;
			token++;
			if (token == tokenIndex1Based) {
				return new int[] { start, end };
			}
		}
		return null;
	}
}
