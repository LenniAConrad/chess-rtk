package application.gui.render;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import application.gui.GuiTheme;
import application.gui.window.GuiWindowHistory;
import application.gui.model.ReportEntry;

/**
 * Renderer for game analysis report rows.
 *
 * Renders move prefixes, SAN text, eval/loss chips, and NAG labels with themed colors so the report list clearly highlights critical evaluation metrics.
 *
 * @param owner history window providing theme and scaling helpers.
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class ReportCellRenderer extends JPanel implements javax.swing.ListCellRenderer<ReportEntry> {

		/**
	 * Serialization version identifier.
	 */
@java.io.Serial
	private static final long serialVersionUID = 1L;
	/**
	 * History window supplying fonts, colors, and data.
	 */
	private final GuiWindowHistory owner;
	/**
	 * Label showing the move prefix.
	 */
	private final JLabel numberLabel = new JLabel();
	/**
	 * Label showing SAN text.
	 */
	private final JLabel moveLabel = new JLabel();
	/**
	 * Chip displaying evaluation label.
	 */
	private final JLabel evalChip = new JLabel();
	/**
	 * Chip displaying loss label.
	 */
	private final JLabel lossChip = new JLabel();
	/**
	 * Label showing the NAG symbol.
	 */
	private final JLabel nagLabel = new JLabel();
	/**
	 * Left-side container for numbering and SAN text.
	 */
	private final JPanel leftPanel = new JPanel(new BorderLayout(6, 0));
	/**
	 * Container holding the evaluation/loss chips.
	 */
	private final JPanel chipRow = new JPanel(new java.awt.GridLayout(1, 0, 6, 0));

	/**
	 * @param owner history window supplying theming helpers.
	 */
	public ReportCellRenderer(GuiWindowHistory owner) {
		this.owner = owner;
		setLayout(new BorderLayout(8, 0));
		setOpaque(true);
		numberLabel.setOpaque(false);
		moveLabel.setOpaque(false);
		nagLabel.setOpaque(false);
		numberLabel.setHorizontalAlignment(SwingConstants.LEFT);
		moveLabel.setHorizontalAlignment(SwingConstants.LEFT);
		nagLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		evalChip.setOpaque(true);
		lossChip.setOpaque(true);
		chipRow.setOpaque(false);
		chipRow.add(evalChip);
		chipRow.add(lossChip);
		leftPanel.setOpaque(false);
		leftPanel.add(numberLabel, BorderLayout.WEST);
		leftPanel.add(moveLabel, BorderLayout.CENTER);
		add(leftPanel, BorderLayout.CENTER);
		JPanel rightPanel = new JPanel(new BorderLayout(6, 0));
		rightPanel.setOpaque(false);
		rightPanel.add(chipRow, BorderLayout.CENTER);
		rightPanel.add(nagLabel, BorderLayout.EAST);
		add(rightPanel, BorderLayout.EAST);
		setBorder(new EmptyBorder(4, 6, 4, 6));
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
	public java.awt.Component getListCellRendererComponent(javax.swing.JList<? extends ReportEntry> list,
			ReportEntry value, int index, boolean isSelected, boolean cellHasFocus) {
		String prefix = value != null ? value.prefix() : "";
		String san = value != null ? value.san() : "";
		String eval = value != null ? value.eval() : "";
		String loss = value != null ? value.loss() : "";
		String nag = value != null ? value.nag() : "";

		numberLabel.setText(prefix);
		moveLabel.setText(owner.truncate(owner.displaySan(san), 32));
		evalChip.setText(eval.isBlank() ? "—" : eval);
		lossChip.setText(loss.isBlank() ? "—" : loss);
		nagLabel.setText(nag);

		GuiTheme theme = owner.getTheme();
		Font base = owner.scaleFont(theme.bodyFont());
		Font chipFont = base.deriveFont(Font.PLAIN, Math.max(10f, base.getSize2D() * 0.85f));
		Font moveFont = owner.figurineDisplayFont(base);
		numberLabel.setFont(base.deriveFont(Font.BOLD));
		moveLabel.setFont(moveFont);
		evalChip.setFont(chipFont);
		lossChip.setFont(chipFont);
		nagLabel.setFont(base.deriveFont(Font.BOLD));

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
		moveLabel.setForeground(theme.text());
		numberLabel.setForeground(theme.textMuted());
		nagLabel.setForeground(theme.text());

		Color chipBg = owner.blend(theme.surfaceAlt(), theme.border(), 0.2f);
		Color chipBorder = theme.border();
		Color chipFg = theme.textMuted();
		evalChip.setBackground(chipBg);
		evalChip.setForeground(chipFg);
		evalChip.setBorder(BorderFactory.createLineBorder(chipBorder));
		lossChip.setBackground(chipBg);
		lossChip.setForeground(chipFg);
		lossChip.setBorder(BorderFactory.createLineBorder(chipBorder));
		return this;
	}
}
