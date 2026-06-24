package application.gui.workbench.ui;

import application.cli.PathOps;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import javax.swing.JFileChooser;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;
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
        FileChooserIcons.installDefaults();
        JFileChooser chooser = new JFileChooser();
        if (title != null && !title.isBlank()) {
            chooser.setDialogTitle(title);
        }
        if (selectedFile != null) {
            applySelectedFile(chooser, selectedFile);
        } else {
            chooser.setCurrentDirectory(defaultDirectory());
        }
        if (filter != null) {
            chooser.setFileFilter(new ReadableExtensionFilter(filter));
        }
        Ui.styleFileChooser(chooser);
        return chooser;
    }

    /**
     * Returns the default directory used by workbench file choosers.
     *
     * @return dump directory when available, otherwise the process directory
     */
    private static File defaultDirectory() {
        try {
            Files.createDirectories(PathOps.DEFAULT_DUMP_DIR);
            return PathOps.DEFAULT_DUMP_DIR.toFile();
        } catch (IOException ex) {
            return new File(".");
        }
    }

    /**
     * Applies the initial selection while keeping relative save paths anchored in
     * their parent directory.
     *
     * @param chooser chooser to update
     * @param selectedFile source selected file
     */
    private static void applySelectedFile(JFileChooser chooser, File selectedFile) {
        File parent = selectedFile.getParentFile();
        if (parent != null) {
            try {
                Files.createDirectories(parent.toPath());
                chooser.setCurrentDirectory(parent);
            } catch (IOException ex) {
                chooser.setCurrentDirectory(defaultDirectory());
            }
            chooser.setSelectedFile(new File(selectedFile.getName()));
            return;
        }
        chooser.setCurrentDirectory(defaultDirectory());
        chooser.setSelectedFile(selectedFile);
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

    /**
     * File filter wrapper with a stable, human-readable combo-box label.
     */
    private static final class ReadableExtensionFilter extends FileFilter {

        /**
         * Lowercase extensions accepted by this filter.
         */
        private final String[] extensions;

        /**
         * Display text used by Swing combo boxes and labels.
         */
        private final String description;

        /**
         * Creates one readable wrapper around Swing's final extension filter.
         *
         * @param filter source extension filter
         */
        ReadableExtensionFilter(FileNameExtensionFilter filter) {
            this.extensions = normalizedExtensions(filter.getExtensions());
            this.description = formatDescription(filter.getDescription(), extensions);
        }

        /**
         * Returns whether the file is accepted by the extension set.
         *
         * @param file candidate file
         * @return true when the file is a directory or has an accepted extension
         */
        @Override
        public boolean accept(File file) {
            if (file == null) {
                return false;
            }
            if (file.isDirectory()) {
                return true;
            }
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            if (dot < 0 || dot == name.length() - 1) {
                return false;
            }
            String candidate = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            for (String extension : extensions) {
                if (candidate.equals(extension)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns the readable filter description.
         *
         * @return display description
         */
        @Override
        public String getDescription() {
            return description;
        }

        /**
         * Returns the same readable value for renderers that call toString().
         *
         * @return display description
         */
        @Override
        public String toString() {
            return description;
        }

        /**
         * Normalizes extensions for case-insensitive matching.
         *
         * @param source source extensions
         * @return lowercase extensions without leading dots
         */
        private static String[] normalizedExtensions(String[] source) {
            String[] normalized = new String[source.length];
            for (int i = 0; i < source.length; i++) {
                String value = source[i] == null ? "" : source[i].trim();
                normalized[i] = value.startsWith(".") ? value.substring(1).toLowerCase(Locale.ROOT)
                        : value.toLowerCase(Locale.ROOT);
            }
            return normalized;
        }

        /**
         * Creates a readable chooser-filter label.
         *
         * @param label source label
         * @param normalizedExtensions lowercase extensions
         * @return formatted label
         */
        private static String formatDescription(String label, String[] normalizedExtensions) {
            String base = label == null || label.isBlank() ? "Supported files" : label.trim();
            StringBuilder patterns = new StringBuilder();
            for (String extension : normalizedExtensions) {
                if (extension.isBlank()) {
                    continue;
                }
                if (patterns.length() > 0) {
                    patterns.append(", ");
                }
                patterns.append("*.").append(extension);
            }
            return patterns.length() == 0 ? base : base + " (" + patterns + ")";
        }
    }
}
