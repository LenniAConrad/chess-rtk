package application.gui.workbench.ui;

import java.awt.Color;
import javax.swing.JComponent;

/**
 * Semantic foreground-role helpers behind the stable {@link Theme} facade.
 */
final class ThemeForeground {

    private ThemeForeground() {
        // utility
    }

    static void apply(JComponent component, Theme.ForegroundRole role) {
        if (component == null) {
            return;
        }
        Theme.ForegroundRole resolved = role == null ? Theme.ForegroundRole.TEXT : role;
        component.putClientProperty(Theme.FOREGROUND_ROLE_PROPERTY, resolved);
        component.setForeground(color(resolved));
    }

    static Color color(Theme.ForegroundRole role) {
        return switch (role == null ? Theme.ForegroundRole.TEXT : role) {
            case MUTED -> Theme.MUTED;
            case SUCCESS -> Theme.STATUS_SUCCESS_TEXT;
            case WARNING -> Theme.STATUS_WARNING_TEXT;
            case ERROR -> Theme.STATUS_ERROR_TEXT;
            case INFO -> Theme.STATUS_INFO_TEXT;
            case TERMINAL -> Theme.TERMINAL_TEXT;
            case TEXT -> Theme.TEXT;
        };
    }
}
