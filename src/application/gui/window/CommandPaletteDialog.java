package application.gui.window;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import application.gui.model.PaletteCommand;
import application.gui.ui.GradientPanel;
import application.gui.ui.RoundedPanel;

/**
 * Command palette dialog for common actions.
 *
 * Hosts a searchable list of palette commands with keyboard shortcuts so users can jump to history, engine, or board features without leaving the focused window.
 *
 * @param owner history window used for theming and command execution helpers.
  * @since 2026
  * @author Lennart A. Conrad
 */
final class CommandPaletteDialog extends JDialog {
	/**
	 * Preferred dialog size before scaling.
	 */
	private static final int BASE_WIDTH = 520;
	/**
	 * BASE_HEIGHT field.
	 */
	private static final int BASE_HEIGHT = 420;

	/**
	 * Owning window used for theme helpers and command execution.
	 */
	private final GuiWindowHistory owner;
	/**
	 * Gradient root pane that holds the palette card.
	 */
	private final GradientPanel dialogRoot;
	/**
	 * Search field used to filter commands.
	 */
	private final JTextField searchField;
	/**
	 * Model backing the list of palette commands.
	 */
	private final DefaultListModel<PaletteCommand> model = new DefaultListModel<>();
	/**
	 * Visible list showing available commands.
	 */
	private final JList<PaletteCommand> list = new JList<>(model);
	/**
	 * Hint label showing keyboard shortcuts.
	 */
	private final JLabel hintLabel;
	/**
	 * Cached command set to filter through.
	 */
	private List<PaletteCommand> commands = new ArrayList<>();

	/**
	 * Constructor.
	 * @param owner parameter.
	 */
	CommandPaletteDialog(GuiWindowHistory owner) {
		super(owner.frame, "Command Palette", false);
		this.owner = owner;
		setModalityType(ModalityType.MODELESS);
		setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

		dialogRoot = new GradientPanel(owner.theme.backgroundTop(), owner.theme.backgroundBottom());
		dialogRoot.setLayout(new BorderLayout(12, 12));
		dialogRoot.setBorder(new EmptyBorder(12, 12, 12, 12));
		setContentPane(dialogRoot);

		RoundedPanel card = owner.createCard("Command Palette");
		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		searchField = new JTextField();
		owner.textFields.add(searchField);
		body.add(owner.labeledField("Search", searchField));
		body.add(Box.createVerticalStrut(8));

		list.setCellRenderer(new PaletteCellRenderer(owner));
		owner.lists.add(list);
		JScrollPane scroll = new JScrollPane(list);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		owner.scrolls.add(scroll);
		body.add(scroll);

		hintLabel = owner.mutedLabel("Enter to run, Esc to close");
		body.add(Box.createVerticalStrut(6));
		body.add(hintLabel);

		card.setContent(body);
		dialogRoot.add(card, BorderLayout.CENTER);

		searchField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				filter();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				filter();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				filter();
			}
		});

		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					runSelected();
				}
			}
		});

		bindKey(searchField, KeyEvent.VK_ENTER, 0, this::runSelected);
		bindKey(list, KeyEvent.VK_ENTER, 0, this::runSelected);
		bindKey(searchField, KeyEvent.VK_ESCAPE, 0, this::hideDialog);
		bindKey(list, KeyEvent.VK_ESCAPE, 0, this::hideDialog);
		bindKey(searchField, KeyEvent.VK_DOWN, 0, () -> moveSelection(1));
		bindKey(searchField, KeyEvent.VK_UP, 0, () -> moveSelection(-1));

		owner.applyTheme();
		updateDialogSize();
		setLocationRelativeTo(owner.frame);
	}

	/**
	 * Opens the palette with the provided command list.
	 *
	 * @param commands list of commands to show in the searchable list.
	 */
	void open(List<PaletteCommand> commands) {
		setCommands(commands);
		searchField.setText("");
		filter();
		if (!isVisible()) {
			setLocationRelativeTo(owner.frame);
		}
		setVisible(true);
		toFront();
		SwingUtilities.invokeLater(() -> searchField.requestFocusInWindow());
	}

	/**
	 * Applies the current theme colors to the dialog root.
	 */
	void applyDialogTheme() {
		dialogRoot.setColors(owner.theme.backgroundTop(), owner.theme.backgroundBottom());
		int pad = Math.max(8, Math.round(12 * owner.uiScale));
		dialogRoot.setBorder(new EmptyBorder(pad, pad, pad, pad));
		updateDialogSize();
		dialogRoot.repaint();
	}

	/**
	 * Updates the dialog’s preferred size based on UI scaling.
	 */
	private void updateDialogSize() {
		int width = Math.round(BASE_WIDTH * owner.uiScale);
		int height = Math.round(BASE_HEIGHT * owner.uiScale);
		Dimension target = new Dimension(Math.max(420, width), Math.max(320, height));
		setMinimumSize(target);
		Dimension current = getSize();
		if (current.width < target.width || current.height < target.height) {
			setSize(target);
		}
	}

	/**
	 * Stores a defensive copy of the provided command list.
	 *
	 * @param commands commands to display.
	 */
	private void setCommands(List<PaletteCommand> commands) {
		this.commands = commands == null ? new ArrayList<>() : new ArrayList<>(commands);
	}

	/**
	 * Filters the list model based on the search text.
	 */
	private void filter() {
		String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
		model.clear();
		for (PaletteCommand cmd : commands) {
			if (matches(cmd, query)) {
				model.addElement(cmd);
			}
		}
		if (!model.isEmpty()) {
			list.setSelectedIndex(0);
		}
	}

	/**
	 * Determines whether a command matches the current query.
	 *
	 * @param cmd palette command to test.
	 * @param query normalized search query.
	 * @return true when the search terms are contained in the command metadata.
	 */
	private boolean matches(PaletteCommand cmd, String query) {
		if (cmd == null) {
			return false;
		}
		if (query.isBlank()) {
			return true;
		}
		String hay = cmd.searchText();
		if (hay == null) {
			return false;
		}
		String[] parts = query.split("\\s+");
		for (String part : parts) {
			if (!hay.contains(part)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Moves the keyboard selection up or down.
	 *
	 * @param delta number of positions to shift (positive = down, negative = up).
	 */
	private void moveSelection(int delta) {
		int size = model.size();
		if (size == 0) {
			return;
		}
		int idx = list.getSelectedIndex();
		if (idx < 0) {
			idx = 0;
		}
		int next = Math.max(0, Math.min(size - 1, idx + delta));
		list.setSelectedIndex(next);
		list.ensureIndexIsVisible(next);
	}

	/**
	 * Executes the currently selected palette command.
	 */
	private void runSelected() {
		PaletteCommand cmd = list.getSelectedValue();
		if (cmd == null || cmd.action() == null) {
			return;
		}
		hideDialog();
		cmd.action().run();
	}

	/**
	 * Hides the palette dialog.
	 */
	private void hideDialog() {
		setVisible(false);
	}

	/**
	 * Binds a keystroke to the provided action on the target component.
	 *
	 * @param target component to receive the binding.
	 * @param keyCode keystroke code.
	 * @param modifiers modifier mask.
	 * @param action action to execute.
	 */
	private void bindKey(JComponent target, int keyCode, int modifiers, Runnable action) {
		InputMap im = target.getInputMap(JComponent.WHEN_FOCUSED);
		ActionMap am = target.getActionMap();
		String id = "palette-" + keyCode + "-" + modifiers;
		im.put(KeyStroke.getKeyStroke(keyCode, modifiers), id);
		am.put(id, new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				action.run();
			}
		});
	}

	/**
	 * Class declaration.
	  *
	  * @since 2026
	  * @author Lennart A. Conrad
	 */
	private static final class PaletteCellRenderer extends JPanel implements ListCellRenderer<PaletteCommand> {
		/**
		 * Owning window used for font scaling and theming.
		 */
		private final GuiWindowHistory owner;
		/**
		 * Label showing the command name.
		 */
		private final JLabel title = new JLabel();
		/**
		 * Label showing the hint text.
		 */
		private final JLabel hint = new JLabel();

		/**
		 * @param owner history window for theming information.
		 */
		private PaletteCellRenderer(GuiWindowHistory owner) {
			super(new BorderLayout());
			this.owner = owner;
			setOpaque(true);
			JPanel stack = new JPanel();
			stack.setOpaque(false);
			stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
			stack.add(title);
			stack.add(Box.createVerticalStrut(2));
			stack.add(hint);
			add(stack, BorderLayout.CENTER);
			setBorder(new EmptyBorder(6, 10, 6, 10));
		}

		@Override
		public java.awt.Component getListCellRendererComponent(JList<? extends PaletteCommand> list, PaletteCommand value,
				int index, boolean isSelected, boolean cellHasFocus) {
			String label = value != null ? value.label() : "";
			String hintText = value != null ? value.hint() : "";
			title.setText(label == null ? "" : label);
			hint.setText(hintText == null ? "" : hintText);

			Font base = owner.scaleFont(owner.theme.bodyFont());
			if (base != null) {
				title.setFont(base.deriveFont(Font.BOLD));
				hint.setFont(base.deriveFont(Font.PLAIN, Math.max(10f, base.getSize2D() * 0.9f)));
			}
			title.setForeground(owner.theme.textStrong());
			hint.setForeground(owner.theme.textMuted());

			if (isSelected) {
				/**
				 * setBackground method.
				 *
				 * @param listgetSelectionBackground parameter.
				 */
				setBackground(list.getSelectionBackground());
			} else {
				/**
				 * setBackground method.
				 *
				 * @param listgetBackground parameter.
				 */
				setBackground(list.getBackground());
			}
			return this;
		}
	}
}
