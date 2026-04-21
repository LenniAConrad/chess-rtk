package application.gui.model;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Binds a command field spec to its rendered Swing components.
 *
 * Keeps the spec alongside its container panel, checkbox toggle, and text input so the command form builder can sync values and validation state.
 *
 * @param spec declaration driving this row.
 * @param container panel that wraps the field controls.
 * @param checkBox optional flag checkbox when the spec renders a boolean toggle.
 * @param input text field used for typed values.
  * @since 2026
  * @author Lennart A. Conrad
 */
public record CommandFieldBinding(
	/**
	 * Stores the spec.
	 */
	CommandFieldSpec spec,
	/**
	 * Stores the container.
	 */
	JPanel container,
	/**
	 * Stores the check box.
	 */
	JCheckBox checkBox,
	/**
	 * Stores the input.
	 */
	JTextField input
) {
}
