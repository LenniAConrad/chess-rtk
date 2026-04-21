package application.gui.window;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.border.EmptyBorder;

import application.gui.ui.GradientPanel;
import application.gui.ui.RoundedPanel;
/**
 * Dialog for selecting/validating engine protocol configs.
 *
 * Lets users pick the engine TOML, node/time caps, multipv count, and evaluation toggles while surfacing validation hints before new sessions are spawned.
 *
 * @param owner history window that owns this modal dialog and drives config persistence.
  * @since 2026
  * @author Lennart A. Conrad
 */
final class EngineManagerDialog extends JDialog {

		/**
	 * Serialization version identifier.
	 */
@java.io.Serial
	private static final long serialVersionUID = 1L;
	/**
	 * History window parent for theming/state helpers.
	 */
	private final GuiWindowHistory owner;
	/**
	 * Root gradient panel for the dialog surface.
	 */
	private final GradientPanel dialogRoot;
	/**
	 * Controls for the engine protocol path, node count, timing, and multipv.
	 */
	private final JTextField protocolField;
	/**
	 * nodesField field.
	 */
	private final JTextField nodesField;
	/**
	 * timeField field.
	 */
	private final JTextField timeField;
	/**
	 * multiPvBox field.
	 */
	private final JComboBox<Integer> multiPvBox;
	/**
	 * Optional flag toggle for endless mode.
	 */
	private final JCheckBox endlessBox;

	/**
	 * Constructor.
	 * @param owner parameter.
	 */
	EngineManagerDialog(GuiWindowHistory owner) {
		super(owner.frame, "Engine Manager", true);
		this.owner = owner;
		dialogRoot = new GradientPanel(owner.theme.backgroundTop(), owner.theme.backgroundBottom());
		dialogRoot.setLayout(new BorderLayout(12, 12));
		dialogRoot.setBorder(new EmptyBorder(12, 12, 12, 12));
		setContentPane(dialogRoot);

		RoundedPanel card = owner.createCard("Settings");
		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		JPanel protoRow = new JPanel(new BorderLayout(8, 8));
		protoRow.setOpaque(false);
		protoRow.add(owner.mutedLabel("Protocol"), BorderLayout.WEST);
		protocolField = new JTextField(owner.engineProtocolField.getText());
		owner.textFields.add(protocolField);
		protoRow.add(protocolField, BorderLayout.CENTER);
		protoRow.add(owner.themedButton("Browse", e -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setFileFilter(new FileNameExtensionFilter("Protocol TOML", "toml"));
			int res = chooser.showOpenDialog(this);
			if (res == JFileChooser.APPROVE_OPTION) {
				protocolField.setText(chooser.getSelectedFile().getAbsolutePath());
			}
		}), BorderLayout.EAST);
		body.add(protoRow);
		body.add(Box.createVerticalStrut(8));

		nodesField = new JTextField(owner.engineNodesField.getText());
		timeField = new JTextField(owner.engineTimeField.getText());
		owner.textFields.add(nodesField);
		owner.textFields.add(timeField);
		multiPvBox = new JComboBox<>(new Integer[] { 1, 2, 3 });
		multiPvBox.setSelectedItem(owner.engineMultiPvBox.getSelectedItem());
		owner.combos.add(multiPvBox);

		JPanel settingsRow = new JPanel(new java.awt.GridLayout(1, 0, 8, 8));
		settingsRow.setOpaque(false);
		settingsRow.add(owner.labeledField("Nodes", nodesField));
		settingsRow.add(owner.labeledField("Time ms", timeField));
		settingsRow.add(owner.labeledCombo("MultiPV", multiPvBox));
		body.add(settingsRow);
		body.add(Box.createVerticalStrut(8));

		JPanel flagsRow = new JPanel(new java.awt.GridLayout(1, 0, 8, 8));
		flagsRow.setOpaque(false);
		endlessBox = owner.themedCheckbox("Endless", owner.engineEndlessToggle.isSelected(), null);
		flagsRow.add(endlessBox);
		body.add(flagsRow);

		card.setContent(body);
		dialogRoot.add(card, BorderLayout.CENTER);

		JPanel actions = new JPanel(new java.awt.GridLayout(1, 0, 8, 8));
		actions.setOpaque(false);
		JButton apply = owner.themedButton("Apply", e -> applySettings(false));
		JButton restart = owner.themedButton("Apply & Restart", e -> applySettings(true));
		JButton close = owner.themedButton("Close", e -> dispose());
		actions.add(apply);
		actions.add(restart);
		actions.add(close);
		dialogRoot.add(actions, BorderLayout.SOUTH);

		owner.applyTheme();
		pack();
		setLocationRelativeTo(owner.frame);
		addWindowListener(new WindowAdapter() {
						/**
			 * Handles window closed.
			 * @param e e value
			 */
@Override
			public void windowClosed(WindowEvent e) {
				owner.engineManagerDialog = null;
			}
		});
	}

	/**
	 * Applies user settings and optionally restarts the engine.
	 *
	 * @param restart whether to restart the engine instance.
	 */
	void applySettings(boolean restart) {
		owner.engineProtocolField.setText(protocolField.getText().trim());
		owner.engineNodesField.setText(nodesField.getText().trim());
		owner.engineTimeField.setText(timeField.getText().trim());
		owner.engineMultiPvBox.setSelectedItem(multiPvBox.getSelectedItem());
		owner.engineEndlessToggle.setSelected(endlessBox.isSelected());
		if (restart) {
			owner.restartEngine();
		}
	}

	/**
	 * Refreshes the dialog colors when the theme changes.
	 */
	void applyDialogTheme() {
		dialogRoot.setColors(owner.theme.backgroundTop(), owner.theme.backgroundBottom());
		dialogRoot.repaint();
	}
}
