package application.gui.workbench;

import java.util.prefs.Preferences;

/**
 * Thin wrapper around {@link Preferences} for workbench file-dialog state.
 */
final class WorkbenchPrefs {

    /**
     * Preferences node used for workbench-network file-dialog state.
     */
    private static final Preferences NODE = Preferences.userRoot()
            .node("crtk/workbench/network");

    /**
     * Utility class; prevents instantiation.
     */
    private WorkbenchPrefs() {
        // utility
    }

    /**
     * Returns the persisted export directory.
     *
     * @return export directory path
     */
    static String exportDir() {
        return NODE.get("exportDir", System.getProperty("user.home"));
    }

    /**
     * Persists the export directory.
     *
     * @param value export directory path
     */
    static void setExportDir(String value) {
        NODE.put("exportDir", value == null ? System.getProperty("user.home") : value);
    }
}
