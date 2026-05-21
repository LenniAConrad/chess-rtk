package application.gui.workbench;

import java.util.List;

import javax.swing.JTextField;

/**
 * Shared helpers for translating workbench form controls into CLI arguments.
 */
final class WorkbenchCommandArgs {

    /**
     * Prevents instantiation.
     */
    private WorkbenchCommandArgs() {
        // utility
    }

    /**
     * Adds an optional text option.
     *
     * @param args target arguments
     * @param flag option flag
     * @param field source field
     */
    static void addOptionalTextArg(List<String> args, String flag, JTextField field) {
        String value = WorkbenchUi.trimmed(field);
        if (!value.isEmpty()) {
            args.add(flag);
            args.add(value);
        }
    }

    /**
     * Adds an optional positive integer option.
     *
     * @param args target arguments
     * @param flag option flag
     * @param field source field
     */
    static void addOptionalPositiveIntegerArg(List<String> args, String flag, JTextField field) {
        String value = WorkbenchUi.trimmed(field);
        if (value.isEmpty()) {
            return;
        }
        if (!value.matches("[1-9]\\d*")) {
            throw new IllegalArgumentException(flag + " expects a positive integer.");
        }
        args.add(flag);
        args.add(value);
    }
}
