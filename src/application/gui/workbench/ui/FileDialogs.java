package application.gui.workbench.ui;

import java.awt.Component;
import java.io.File;
import java.util.Locale;
import javax.swing.JFileChooser;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Shared styled file-dialog helpers for the workbench.
 */
public final class FileDialogs {

    /**
     * Prevents instantiation.
     */
    private FileDialogs() {
        // utility
    }

    /**
     * Opens a file chooser and writes the selected path into a field.
     *
     * @param owner owner component
     * @param target target field
     * @param save true for save dialogs
     * @param title chooser title
     */
    public static void choosePath(Component owner, JTextField target, boolean save, String title) {
        choosePath(owner, target, save, title, null);
    }

    /**
     * Opens a file chooser with an optional extension filter.
     *
     * @param owner owner component
     * @param target target field
     * @param save true for save dialogs
     * @param title chooser title
     * @param filter optional extension filter
     */
    public static void choosePath(Component owner, JTextField target, boolean save, String title,
            FileNameExtensionFilter filter) {
        String existing = target.getText() == null ? "" : target.getText().trim();
        JFileChooser chooser = createFileChooser(title, existing.isEmpty() ? null : new File(existing), filter);
        int result = save ? chooser.showSaveDialog(owner) : chooser.showOpenDialog(owner);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (save && filter != null) {
                selected = ensureExtension(selected, "." + filter.getExtensions()[0]);
            }
            target.setText(selected.getPath());
        }
    }

    /**
     * Creates and styles a file chooser.
     *
     * @param title optional dialog title
     * @param selectedFile optional selected file
     * @param filter optional file filter
     * @return styled chooser
     */
    public static JFileChooser createFileChooser(String title, File selectedFile, FileNameExtensionFilter filter) {
        JFileChooser chooser = new JFileChooser();
        if (title != null && !title.isBlank()) {
            chooser.setDialogTitle(title);
        }
        if (selectedFile != null) {
            chooser.setSelectedFile(selectedFile);
        }
        if (filter != null) {
            chooser.setFileFilter(filter);
        }
        Ui.styleFileChooser(chooser);
        return chooser;
    }

    /**
     * Ensures a selected file has an extension.
     *
     * @param file selected file
     * @param extension extension including dot
     * @return file with extension
     */
    public static File ensureExtension(File file, String extension) {
        String path = file.getAbsolutePath();
        return path.toLowerCase(Locale.ROOT).endsWith(extension) ? file : new File(path + extension);
    }
}
