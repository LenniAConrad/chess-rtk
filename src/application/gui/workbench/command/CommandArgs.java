/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.command;

import application.gui.workbench.ui.Ui;
import java.util.List;
import javax.swing.JTextField;

/**
 * Shared helpers for translating workbench form controls into CLI arguments.
 */
public final class CommandArgs {

    /**
     * Prevents instantiation.
     */
    private CommandArgs() {
        // utility
    }

    /**
     * Adds an optional text option.
     *
     * @param args target arguments
     * @param flag option flag
     * @param field source field
     */
    public static void addOptionalTextArg(List<String> args, String flag, JTextField field) {
        String value = Ui.trimmed(field);
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
    public static void addOptionalPositiveIntegerArg(List<String> args, String flag, JTextField field) {
        String value = Ui.trimmed(field);
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
