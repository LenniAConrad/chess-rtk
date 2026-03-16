package application.gui.model;

import java.util.List;

/**
 * Recently run CLI command snapshot.
 *
 * Stores the label, command string, and argument list so the GUI can show recent actions and re-run them with the same data.
 *
 * @param label label shown in the recent list.
 * @param command CLI command text.
 * @param args argument list used when the command was run.
  * @since 2026
  * @author Lennart A. Conrad
 */
public record RecentCommand(String label, String command, List<String> args) {
	@Override
	/**
	 * toString method.
	 *
	 * @return return value.
	 */
	public String toString() {
		return label;
	}
}
