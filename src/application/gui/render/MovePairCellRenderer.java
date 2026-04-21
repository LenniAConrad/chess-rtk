package application.gui.render;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import application.gui.GuiTheme;
import application.gui.window.GuiWindowHistory;
import application.gui.model.MovePair;

/**
 * Renderer for underboard move pairs (white/black columns).
 *
 * Lays out the move number alongside white/black SAN strings, applying accent/highlight colors and bold fonts for the latest ply so the list stays readable.
 *
 * @param owner history window providing theming helpers.
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class MovePairCellRenderer extends JPanel implements javax.swing.ListCellRenderer<MovePair> {

	@java.io.Serial
	private static final long serialVersionUID = 1L;
	/**
	 * History window supplying fonts, colors, and scaling.
	 */
	private final GuiWindowHistory owner;
	/**
	 * Label showing the move number.
	 */
	private final JLabel moveNoLabel = new JLabel();
	/**
	 * Label recording the white SAN.
	 */
	private final JLabel whiteLabel = new JLabel();
	/**
	 * Label recording the black SAN.
	 */
	private final JLabel blackLabel = new JLabel();

	/**
	 * @param owner history window that owns this renderer.
	 */
	public MovePairCellRenderer(GuiWindowHistory owner) {
		this.owner = owner;
		setLayout(new BorderLayout(6, 0));
		setOpaque(true);
		moveNoLabel.setOpaque(false);
		whiteLabel.setOpaque(false);
		blackLabel.setOpaque(false);
		JPanel moves = new JPanel(new GridLayout(1, 2, 6, 0));
		moves.setOpaque(false);
		moves.add(whiteLabel);
		moves.add(blackLabel);
		add(moveNoLabel, BorderLayout.WEST);
		add(moves, BorderLayout.CENTER);
		setBorder(new EmptyBorder(2, 6, 2, 6));
	}

	@Override
	public java.awt.Component getListCellRendererComponent(javax.swing.JList<? extends MovePair> list, MovePair value,
			int index, boolean isSelected, boolean cellHasFocus) {
		String moveNo = value != null ? value.moveNo() + "." : "";
		moveNoLabel.setText(moveNo);
		whiteLabel.setText(value != null ? owner.displaySan(value.whiteSan()) : "");
		blackLabel.setText(value != null ? owner.displaySan(value.blackSan()) : "");

		GuiTheme theme = owner.getTheme();
		Font base = owner.scaleFont(theme.bodyFont());
		Font numFont = base.deriveFont(Font.PLAIN, Math.max(10f, base.getSize2D() * 0.85f));
		Font sanBase = owner.figurineDisplayFont(base);
		Font boldFont = sanBase.deriveFont(Font.BOLD);
		moveNoLabel.setFont(numFont);

		float uiScale = owner.getUiScale();
		int numWidth = Math.round(36 * uiScale);
		moveNoLabel.setPreferredSize(new Dimension(numWidth, moveNoLabel.getPreferredSize().height));
		int lastIndex = owner.getMoveHistory().size() - 1;
		boolean lastWhite = value != null && value.whiteIndex() == lastIndex;
		boolean lastBlack = value != null && value.blackIndex() == lastIndex;
		/**
		 * isLastRow field.
		 */
		boolean isLastRow = lastWhite || lastBlack;
		whiteLabel.setFont(lastWhite ? boldFont : sanBase);
		blackLabel.setFont(lastBlack ? boldFont : sanBase);

		Color bg = theme.sidebar();
		Color fg = theme.text();
		Color numFg = theme.textMuted();
		if (isSelected) {
			bg = theme.selection();
		} else if (owner.isListHover(list, index)) {
			bg = theme.hover();
		} else if (isLastRow) {
			bg = owner.isLightMode() ? new Color(255, 243, 191) : new Color(87, 74, 41);
		}
		/**
		 * setBackground method.
		 *
		 * @param bg parameter.
		 */
		setBackground(bg);
		moveNoLabel.setForeground(numFg);
		whiteLabel.setForeground(fg);
		blackLabel.setForeground(fg);
		return this;
	}
}
