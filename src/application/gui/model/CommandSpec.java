package application.gui.model;

import java.util.List;

/**
 * Declarative description of a CLI command and its GUI fields.
 *
 * Declares the command name, Fen compatibility, and field specs needed to render the form without hardcoding the layout.
 *
 * @param name command identifier used in the dropdown.
 * @param supportsFen whether the command accepts a Fen input.
 * @param fields ordered field specs shown for the command.
  * @since 2026
  * @author Lennart A. Conrad
 */
public record CommandSpec(String name, boolean supportsFen, List<CommandFieldSpec> fields) {
	/**
	 * withName method.
	 * @param name parameter.
	 * @return return value.
	 */
	public CommandSpec withName(String name) {
		return new CommandSpec(name, supportsFen, fields);
	}
}
