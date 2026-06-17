package application.gui.workbench.ui;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.UIManager;

/**
 * Platform look-and-feel and Swing default installation for {@link Theme}.
 */
final class ThemeInstaller {

    /**
     * Theme installer logger.
     */
    private static final Logger LOGGER = Logger.getLogger(ThemeInstaller.class.getName());

    /**
     * Prevents instantiation.
     */
    private ThemeInstaller() {
        // utility
    }

    static void install() {
        try {
            UIManager.setLookAndFeel(preferredLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | javax.swing.UnsupportedLookAndFeelException ex) {
            LOGGER.log(Level.FINE, "Preferred LookAndFeel unavailable; keeping default LookAndFeel.", ex);
        }
        ThemeFonts.installDefaults();
        UIManager.put("Panel.background", Theme.BG);
        UIManager.put("Label.foreground", Theme.TEXT);
        UIManager.put("TabbedPane.background", Theme.BG);
        UIManager.put("TabbedPane.foreground", Theme.TEXT);
        UIManager.put("TabbedPane.selected", Theme.PANEL);
        UIManager.put("TabbedPane.contentAreaColor", Theme.BG);
        UIManager.put("TabbedPane.focus", Theme.LINE);
        UIManager.put("Focus.color", Theme.FOCUS_RING);
        UIManager.put("Button.focus", Theme.FOCUS_RING);
        UIManager.put("TextField.focus", Theme.FOCUS_RING);
        UIManager.put("ComboBox.focus", Theme.FOCUS_RING);
        UIManager.put("Table.background", Theme.ELEVATED_SOLID);
        UIManager.put("Table.foreground", Theme.TEXT);
        UIManager.put("Table.gridColor", Theme.LINE);
        UIManager.put("Table.selectionBackground", Theme.SELECTION_SOLID);
        UIManager.put("Table.selectionForeground", Theme.TEXT);
        UIManager.put("Tree.background", Theme.ELEVATED_SOLID);
        UIManager.put("Tree.foreground", Theme.TEXT);
        UIManager.put("Tree.selectionBackground", Theme.SELECTION_SOLID);
        UIManager.put("Tree.selectionForeground", Theme.TEXT);
        UIManager.put("ScrollPane.background", Theme.BG);
        UIManager.put("Viewport.background", Theme.PANEL_SOLID);
        UIManager.put("TextField.background", Theme.INPUT);
        UIManager.put("TextField.foreground", Theme.TEXT);
        UIManager.put("TextField.caretForeground", Theme.ACCENT);
        UIManager.put("TextField.selectionBackground", Theme.TEXT_SELECTION);
        UIManager.put("TextField.selectionForeground", Theme.TEXT);
        UIManager.put("TextField.inactiveBackground", Theme.INPUT_DISABLED);
        UIManager.put("TextArea.background", Theme.TEXT_AREA);
        UIManager.put("TextArea.foreground", Theme.TEXT);
        UIManager.put("TextArea.caretForeground", Theme.ACCENT);
        UIManager.put("TextArea.selectionBackground", Theme.TEXT_SELECTION);
        UIManager.put("TextArea.selectionForeground", Theme.TEXT);
        UIManager.put("TextArea.inactiveBackground", Theme.INPUT_DISABLED);
        UIManager.put("TextPane.selectionBackground", Theme.TEXT_SELECTION);
        UIManager.put("TextPane.selectionForeground", Theme.TEXT);
        UIManager.put("ComboBox.background", Theme.ELEVATED_SOLID);
        UIManager.put("ComboBox.foreground", Theme.TEXT);
        UIManager.put("ComboBox.selectionBackground", Theme.SELECTION_SOLID);
        UIManager.put("ComboBox.selectionForeground", Theme.TEXT);
        UIManager.put("List.selectionBackground", Theme.SELECTION_SOLID);
        UIManager.put("List.selectionForeground", Theme.TEXT);
        UIManager.put("Button.disabledText", Theme.BUTTON_DISABLED_TEXT);
        UIManager.put("TextField.inactiveForeground", Theme.MUTED);
        UIManager.put("TextArea.inactiveForeground", Theme.MUTED);
        UIManager.put("CheckBox.background", Theme.PANEL_SOLID);
        UIManager.put("CheckBox.foreground", Theme.TEXT);
        UIManager.put("RadioButton.background", Theme.PANEL_SOLID);
        UIManager.put("RadioButton.foreground", Theme.TEXT);
        UIManager.put("MenuBar.background", Theme.BG);
        UIManager.put("MenuBar.foreground", Theme.TEXT);
        UIManager.put("Menu.background", Theme.BG);
        UIManager.put("Menu.foreground", Theme.TEXT);
        UIManager.put("Menu.selectionBackground", Theme.SELECTION_SOLID);
        UIManager.put("Menu.selectionForeground", Theme.TEXT);
        UIManager.put("MenuItem.background", Theme.PANEL_SOLID);
        UIManager.put("MenuItem.foreground", Theme.TEXT);
        UIManager.put("MenuItem.selectionBackground", Theme.SELECTION_SOLID);
        UIManager.put("MenuItem.selectionForeground", Theme.TEXT);
        UIManager.put("CheckBoxMenuItem.background", Theme.PANEL_SOLID);
        UIManager.put("CheckBoxMenuItem.foreground", Theme.TEXT);
        UIManager.put("CheckBoxMenuItem.selectionBackground", Theme.SELECTION_SOLID);
        UIManager.put("CheckBoxMenuItem.selectionForeground", Theme.TEXT);
        UIManager.put("RadioButtonMenuItem.background", Theme.PANEL_SOLID);
        UIManager.put("RadioButtonMenuItem.foreground", Theme.TEXT);
        UIManager.put("RadioButtonMenuItem.selectionBackground", Theme.SELECTION_SOLID);
        UIManager.put("RadioButtonMenuItem.selectionForeground", Theme.TEXT);
        UIManager.put("PopupMenu.background", Theme.PANEL_SOLID);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(Theme.LINE));
        UIManager.put("OptionPane.background", Theme.PANEL_SOLID);
        UIManager.put("OptionPane.messageForeground", Theme.TEXT);
        UIManager.put("FileChooser.background", Theme.BG);
        UIManager.put("FileChooser.foreground", Theme.TEXT);
        UIManager.put("FileChooser.listViewBackground", Theme.ELEVATED_SOLID);
        UIManager.put("FileChooser.listViewBorder", BorderFactory.createLineBorder(Theme.LINE));
        UIManager.put("FileChooser.lookInLabelText", "Location");
        UIManager.put("FileChooser.saveInLabelText", "Location");
        UIManager.put("FileChooser.fileNameLabelText", "File");
        UIManager.put("FileChooser.folderNameLabelText", "Folder");
        UIManager.put("FileChooser.filesOfTypeLabelText", "Type");
        UIManager.put("FileChooser.acceptAllFileFilterText", "All files");
        UIManager.put("FileChooser.openButtonText", "Open");
        UIManager.put("FileChooser.saveButtonText", "Save");
        UIManager.put("FileChooser.cancelButtonText", "Cancel");
        UIManager.put("FileChooser.fileNameHeaderText", "Name");
        UIManager.put("FileChooser.fileSizeHeaderText", "Size");
        UIManager.put("FileChooser.fileTypeHeaderText", "Type");
        UIManager.put("FileChooser.fileDateHeaderText", "Modified");
        UIManager.put("FileChooser.upFolderToolTipText", "Parent folder");
        UIManager.put("FileChooser.homeFolderToolTipText", "Home");
        UIManager.put("FileChooser.newFolderToolTipText", "New folder");
        UIManager.put("FileChooser.listViewButtonToolTipText", "List view");
        UIManager.put("FileChooser.detailsViewButtonToolTipText", "Details view");
        FileChooserIcons.installDefaults();
        UIManager.put("ToolTip.background", Theme.TOOLTIP_BG);
        UIManager.put("ToolTip.foreground", Theme.TOOLTIP_TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(Theme.TOOLTIP_BORDER));
    }

    private static String preferredLookAndFeelClassName() {
        return isMacOs()
                ? UIManager.getSystemLookAndFeelClassName()
                : UIManager.getCrossPlatformLookAndFeelClassName();
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("mac");
    }
}
