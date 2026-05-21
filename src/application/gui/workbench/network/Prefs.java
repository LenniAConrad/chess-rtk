package application.gui.workbench.network;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.session.*;
import application.gui.workbench.ui.*;
import application.gui.workbench.window.*;

import java.util.prefs.Preferences;

/**
 * Thin wrapper around {@link Preferences} for workbench file-dialog state.
 */
public final class Prefs {

    /**
     * Preferences node used for workbench-network file-dialog state.
     */
    private static final Preferences NODE = Preferences.userRoot()
            .node("crtk/workbench/network");

    /**
     * Utility class; prevents instantiation.
     */
    private Prefs() {
        // utility
    }

    /**
     * Returns the persisted export directory.
     *
     * @return export directory path
     */
    public static String exportDir() {
        return NODE.get("exportDir", System.getProperty("user.home"));
    }

    /**
     * Persists the export directory.
     *
     * @param value export directory path
     */
    public static void setExportDir(String value) {
        NODE.put("exportDir", value == null ? System.getProperty("user.home") : value);
    }
}
