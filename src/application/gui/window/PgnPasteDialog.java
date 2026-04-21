package application.gui.window;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import application.gui.ui.GradientPanel;
import application.gui.ui.RoundedPanel;
/**
 * Dialog for pasting and loading PGN input.
 *
 * Provides a scrollable text area with load/cancel actions so users can drop in multiple games, submit them, and let the history loader parse the pasted string.
 *
 * @param owner history window that owns the dialog and processes the pasted PGN.
  * @since 2026
  * @author Lennart A. Conrad
 */
	final class PgnPasteDialog extends JDialog {

	@java.io.Serial
	private static final long serialVersionUID = 1L;
	/**
	 * Owning history window for theme and parsing helpers.
	 */
	private final GuiWindowHistory owner;
	/**
	 * Gradient backdrop for the modal content.
	 */
	private final GradientPanel dialogRoot;
	/**
	 * Text area where the PGN is pasted.
	 */
	private final JTextArea pgnArea;

	/**
	 * Constructor.
	 * @param owner parameter.
	 */
	PgnPasteDialog(GuiWindowHistory owner) {
		super(owner.frame, "Paste PGN", true);
		this.owner = owner;
		dialogRoot = new GradientPanel(owner.theme.backgroundTop(), owner.theme.backgroundBottom());
		dialogRoot.setLayout(new BorderLayout(12, 12));
		dialogRoot.setBorder(new javax.swing.border.EmptyBorder(12, 12, 12, 12));
		setContentPane(dialogRoot);

		RoundedPanel card = owner.createCard("PGN Input");
		pgnArea = new JTextArea(14, 52);
		pgnArea.setLineWrap(true);
		pgnArea.setWrapStyleWord(true);
		owner.textAreas.add(pgnArea);
		JScrollPane scroll = new JScrollPane(pgnArea);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		owner.scrolls.add(scroll);
		card.setContent(scroll);
		dialogRoot.add(card, BorderLayout.CENTER);

		JPanel hintRow = new JPanel(new BorderLayout());
		hintRow.setOpaque(false);
		hintRow.add(owner.mutedLabel("Paste one or more PGN games, then click Load."), BorderLayout.WEST);
		dialogRoot.add(hintRow, BorderLayout.NORTH);

		JPanel actions = new JPanel(new java.awt.GridLayout(1, 0, 8, 8));
		actions.setOpaque(false);
		JButton load = owner.themedButton("Load", e -> {
			if (owner.loadPgnText(pgnArea.getText())) {
				dispose();
			}
		});
		JButton cancel = owner.themedButton("Cancel", e -> dispose());
		actions.add(load);
		actions.add(cancel);
		dialogRoot.add(actions, BorderLayout.SOUTH);

		owner.applyTheme();
		pack();
		setLocationRelativeTo(owner.frame);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				owner.pgnDialog = null;
			}
		});
	}

	/**
	 * Applies the current theme colors to the dialog surface.
	 */
	void applyDialogTheme() {
		dialogRoot.setColors(owner.theme.backgroundTop(), owner.theme.backgroundBottom());
		dialogRoot.repaint();
	}
}
