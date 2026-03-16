package application.gui.model;

/**
 * Supported input types for GUI command fields.
 *
 * Drives whether a row renders a free-text input, numeric spinner, path selector, or checkbox for boolean flags.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public enum CommandFieldType {
	/**
	 * TEXT enum constant.
	 */
	TEXT,
	/**
	 * NUMBER enum constant.
	 */
	NUMBER,
	/**
	 * PATH enum constant.
	 */
	PATH,
	/**
	 * FLAG enum constant.
	 */
	FLAG
}
