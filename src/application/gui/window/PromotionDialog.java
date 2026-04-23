package application.gui.window;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;

import application.gui.ui.RoundedPanel;
import chess.core.Piece;

/**
 * Modal dialog for selecting promotion piece.
 *
 * Displays a floating rounded panel with piece icons whose order depends on the drag direction, then captures the chosen promotion or cancellation before resuming the move.
 *
 * @param owner history window providing theme and board coordinates.
 * @param from source square of the pawn move.
 * @param to destination square triggering the promotion.
  * @since 2026
  * @author Lennart A. Conrad
 */
	final class PromotionDialog extends JDialog {

		/**
	 * Serialization version identifier.
	 */
@java.io.Serial
	private static final long serialVersionUID = 1L;
	/**
	 * Owning history window for theme/board helpers.
	 */
	private final GuiWindowHistory owner;
	/**
	 * Rounded panel containing the promotion buttons.
	 */
	private final RoundedPanel shell;
	/**
	 * Transparent overlay that fills the frame while the dialog is shown.
	 */
	private final JPanel overlay;
	/**
	 * Selected promotion result.
	 */
	private byte promotion = GuiWindowHistory.PROMOTION_NONE;
	/**
	 * Whether the dialog was dismissed without selection.
	 */
	private boolean cancelled = true;

	/**
	 * Constructor.
	 * @param owner parameter.
	 * @param from parameter.
	 * @param to parameter.
	 */
	PromotionDialog(GuiWindowHistory owner, byte from, byte to) {
		super(owner.frame, true);
		this.owner = owner;
		setUndecorated(true);
		setModalityType(ModalityType.APPLICATION_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setBackground(new Color(0, 0, 0, 0));

		byte piece = owner.position.getBoard()[from];
		boolean whitePiece = Piece.isWhite(piece);

		int tile = owner.boardPanel.tileSize > 0 ? owner.boardPanel.tileSize : 64;
		int pad = Math.max(6, Math.round(tile * 0.1f));
		int buttonSize = Math.max(44, Math.round(tile * 0.95f));

		overlay = new JPanel(null);
		overlay.setOpaque(false);
		setContentPane(overlay);

		shell = new RoundedPanel(18);
		shell.setTheme(owner.theme.surface(), owner.theme.border());
		shell.setShadow(owner.theme.shadow(), 6);
		shell.setLayout(new BorderLayout());
		shell.setBorder(new EmptyBorder(pad, pad, pad, pad));

		JPanel stack = new JPanel(new GridLayout(4, 1, 0, 0));
		stack.setOpaque(false);

		boolean promoteUpOnScreen = owner.boardPanel.screenRank(to) < owner.boardPanel.screenRank(from);
		byte[] order = promoteUpOnScreen
				? new byte[] { GuiWindowHistory.PROMOTION_QUEEN, GuiWindowHistory.PROMOTION_ROOK,
						GuiWindowHistory.PROMOTION_BISHOP, GuiWindowHistory.PROMOTION_KNIGHT }
				: new byte[] { GuiWindowHistory.PROMOTION_KNIGHT, GuiWindowHistory.PROMOTION_BISHOP,
						GuiWindowHistory.PROMOTION_ROOK, GuiWindowHistory.PROMOTION_QUEEN };
		for (byte promo : order) {
			BufferedImage image = owner.promotionPieceImage(promo, whitePiece);
			PromotionButton button = new PromotionButton(image, promo, buttonSize);
			button.addActionListener(e -> {
				promotion = promo;
				cancelled = false;
				dispose();
			});
			stack.add(button);
		}

		shell.add(stack, BorderLayout.CENTER);
		shell.setSize(shell.getPreferredSize());
		overlay.add(shell);
		Dimension frameSize = owner.frame.getSize();
		setSize(frameSize);
		overlay.setSize(frameSize);
		setLocation(owner.frame.getLocationOnScreen());
		owner.positionPromotionCard(this, shell, from, to, tile, pad);

		overlay.addMouseListener(new MouseAdapter() {
						/**
			 * Handles mouse pressed.
			 * @param e e value
			 */
@Override
			public void mousePressed(MouseEvent e) {
				if (!shell.getBounds().contains(e.getPoint())) {
					cancelAndClose();
				}
			}
		});

		getRootPane().registerKeyboardAction(e -> cancelAndClose(),
				KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
	}

	/**
	 * Returns the chosen promotion piece (defaulting to queen when none selected).
	 *
	 * @return promotion piece code.
	 */
	byte getPromotion() {
		return promotion == GuiWindowHistory.PROMOTION_NONE ? GuiWindowHistory.PROMOTION_QUEEN : promotion;
	}

	/**
	 * @return true if the dialog was closed without a selection.
	 */
	boolean wasCancelled() {
		return cancelled;
	}

	/**
	 * Marks the dialog as cancelled and closes it.
	 */
	private void cancelAndClose() {
		cancelled = true;
		dispose();
	}

	/**
	 * Promotion dialog button with a piece icon.
	  *
	  * @since 2026
	  * @author Lennart A. Conrad
	 */
	private final class PromotionButton extends JButton {

				/**
		 * Serialization version identifier.
		 */
@java.io.Serial
		private static final long serialVersionUID = 1L;
		/**
		 * @param image piece icon image.
		 * @param promo promotion code.
		 * @param size size of the button.
		 */
		PromotionButton(BufferedImage image, byte promo, int size) {
			setPreferredSize(new Dimension(size, size));
			setMinimumSize(new Dimension(size, size));
			setMaximumSize(new Dimension(size, size));
			setIcon(owner.scaledIcon(image, Math.round(size * 0.78f)));
			setBorder(new EmptyBorder(4, 4, 4, 4));
			setFocusPainted(false);
			setBorderPainted(false);
			setContentAreaFilled(false);
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText(owner.promoLabel(promo));
		}

				/**
		 * Handles paint component.
		 * @param g g value
		 */
@Override
		/**
		 * Paints the rounded button background and gloss for hover/press states.
		 *
		 * @param g graphics context.
		 */
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			owner.applyRenderHints(g2);
			int w = getWidth();
			int h = getHeight();
			int arc = Math.max(12, Math.min(w, h) / 3);
			Color base = owner.theme.surfaceAlt();
			g2.setColor(base);
			g2.fillRoundRect(0, 0, w, h, arc, arc);
			if (getModel().isRollover() || getModel().isPressed()) {
				int alpha = getModel().isPressed() ? 70 : 45;
				Color glow = new Color(owner.theme.accent().getRed(), owner.theme.accent().getGreen(),
						owner.theme.accent().getBlue(), alpha);
				g2.setColor(glow);
				g2.fillRoundRect(0, 0, w, h, arc, arc);
			}
			g2.setColor(owner.theme.border());
			g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
			g2.dispose();
			super.paintComponent(g);
		}
	}
}
