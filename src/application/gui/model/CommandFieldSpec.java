package application.gui.model;

/**
 * Describes a CLI option to render as a GUI input field.
 *
 * Carries the flag, label, input type, and placeholder text so the command form builder can render and validate each row consistently.
 *
 * @param flag CLI flag name used in the command invocation.
 * @param label user-facing label shown near the input.
 * @param type input kind (text, number, path, flag).
 * @param placeholder hint text shown inside the field.
  * @since 2026
  * @author Lennart A. Conrad
 */
public record CommandFieldSpec(String flag, String label, CommandFieldType type, String placeholder) {
}
