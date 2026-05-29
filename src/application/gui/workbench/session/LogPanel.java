package application.gui.workbench.session;

import application.gui.workbench.command.Console;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.ui.Theme;
import chess.debug.SessionCache;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

import static application.gui.workbench.ui.Ui.button;
import static application.gui.workbench.ui.Ui.buttonRow;
import static application.gui.workbench.ui.Ui.caption;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.transparentPanel;

/**
 * Dedicated persisted-log viewer for the workbench. The panel scans the shared
 * session directory for application logs and command-run logs, then renders
 * them in a terminal-like read-only view.
 */
public final class LogPanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Maximum number of log files shown in the file list.
     */
    private static final int MAX_LOG_FILES = 300;

    /**
     * Maximum number of bytes read from a single log in aggregate mode.
     */
    private static final int PER_FILE_TAIL_BYTES = 256 * 1024;

    /**
     * Maximum number of characters displayed in the combined log view.
     */
    private static final int AGGREGATE_CHAR_LIMIT = 2 * 1024 * 1024;

    /**
     * Aggregate-list label.
     */
    private static final String ALL_LOGS_LABEL = "All logs";

    /**
     * Message shown when no log files exist yet.
     */
    private static final String NO_LOGS_TEXT =
            "No persisted logs found yet.\n\nRun a command, open a model, or use the app normally; "
                    + "new .log files will appear here after refresh.\n";

    /**
     * Timestamp format for log section headers.
     */
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                    .withZone(ZoneId.systemDefault());

    /**
     * Model backing the log-file list.
     */
    private final DefaultListModel<LogEntry> logModel = new DefaultListModel<>();

    /**
     * Selectable list of aggregate and individual log files.
     */
    private final JList<LogEntry> logList = new JList<>(logModel);

    /**
     * Terminal-style log text renderer.
     */
    private final Console logView = new Console();

    /**
     * Footer status label for scan, load, and file-operation feedback.
     */
    private final JLabel statusLabel = caption("No logs scanned yet.");

    /**
     * Clipboard callback supplied by the owning window.
     */
    private final transient Consumer<String> copyText;

    /**
     * Currently scanned individual log entries, excluding the aggregate item.
     */
    private transient List<LogEntry> currentLogs = List.of();

    /**
     * Generation counter that discards stale scan workers.
     */
    private transient int scanGeneration;

    /**
     * Generation counter that discards stale load workers.
     */
    private transient int loadGeneration;

    /**
     * Creates a log panel that copies paths through the system clipboard.
     */
    public LogPanel() {
        this(LogPanel::copyToSystemClipboard);
    }

    /**
     * Creates a log panel.
     *
     * @param copyText clipboard callback for copied paths
     */
    public LogPanel(Consumer<String> copyText) {
        this.copyText = copyText == null ? LogPanel::copyToSystemClipboard : copyText;
        configure();
        refreshLogs();
    }

    /**
     * Scans the session directory again and reloads the current selection.
     */
    public void refreshLogs() {
        int generation = ++scanGeneration;
        showStatus("Scanning session logs...", Theme.ForegroundRole.MUTED);
        new SwingWorker<List<LogEntry>, Void>() {

            /**
             * Scans log files off the event-dispatch thread.
             *
             * @return discovered log entries
             * @throws IOException when the directory cannot be walked
             */
            @Override
            protected List<LogEntry> doInBackground() throws IOException {
                return scanSessionLogs();
            }

            /**
             * Installs the scan results on the event-dispatch thread.
             */
            @Override
            protected void done() {
                if (generation != scanGeneration) {
                    return;
                }
                try {
                    installEntries(get());
                } catch (Exception ex) {
                    currentLogs = List.of();
                    logModel.clear();
                    showText("Log scan failed: " + ex.getMessage() + '\n');
                    showStatus("Log scan failed.", Theme.ForegroundRole.ERROR);
                }
            }
        }.execute();
    }

    /**
     * Applies the static Swing layout and component styling.
     */
    private void configure() {
        setLayout(new BorderLayout(Theme.SPACE_MD, Theme.SPACE_MD));
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setForeground(Theme.TEXT);
        setBorder(Theme.pad(10, 10, 10, 10));

        add(header(), BorderLayout.NORTH);
        add(splitPane(), BorderLayout.CENTER);
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        add(statusLabel, BorderLayout.SOUTH);
    }

    /**
     * Creates the tab header with compact log actions.
     *
     * @return header component
     */
    private JComponent header() {
        JPanel header = transparentPanel(new BorderLayout(Theme.SPACE_MD, 0));
        JPanel title = transparentPanel(new BorderLayout(0, 2));
        title.add(Theme.section("Logs"), BorderLayout.NORTH);
        title.add(caption("Persisted application and command logs"), BorderLayout.CENTER);
        header.add(title, BorderLayout.WEST);

        JButton refresh = button("Refresh", false, event -> refreshLogs());
        JButton openFolder = button("Open Folder", false, event -> openSessionFolder());
        JButton openSelected = button("Open Selected", false, event -> openSelected());
        JButton copyPath = button("Copy Path", false, event -> copySelectedPath());
        header.add(buttonRow(FlowLayout.RIGHT, openFolder, openSelected, copyPath, refresh), BorderLayout.EAST);
        return header;
    }

    /**
     * Creates the two-pane log browser.
     *
     * @return split pane
     */
    private JSplitPane splitPane() {
        Theme.list(logList);
        logList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        logList.setCellRenderer(new LogEntryRenderer());
        logList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadSelectedLog();
            }
        });

        JPanel filePanel = transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        filePanel.add(Theme.section("Files"), BorderLayout.NORTH);
        filePanel.add(scroll(logList), BorderLayout.CENTER);

        JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, filePanel, scroll(logView));
        SplitPaneStyler.style(pane);
        pane.setResizeWeight(0.24);
        pane.setDividerLocation(280);
        return pane;
    }

    /**
     * Updates the list model from scan results.
     *
     * @param entries discovered individual log entries
     */
    private void installEntries(List<LogEntry> entries) {
        currentLogs = List.copyOf(entries);
        logModel.clear();
        if (currentLogs.isEmpty()) {
            showText(NO_LOGS_TEXT);
            showStatus("No log files in " + rootLabel() + ".", Theme.ForegroundRole.MUTED);
            return;
        }

        logModel.addElement(aggregateEntry(currentLogs));
        for (LogEntry entry : currentLogs) {
            logModel.addElement(entry);
        }
        logList.setSelectedIndex(0);
        showStatus(currentLogs.size() + " log files found in " + rootLabel() + ".",
                Theme.ForegroundRole.MUTED);
    }

    /**
     * Loads the selected aggregate or individual log entry in the background.
     */
    private void loadSelectedLog() {
        LogEntry selected = logList.getSelectedValue();
        if (selected == null) {
            return;
        }
        int generation = ++loadGeneration;
        showStatus("Loading " + selected.label() + "...", Theme.ForegroundRole.MUTED);
        new SwingWorker<String, Void>() {

            /**
             * Reads the selected log text off the event-dispatch thread.
             *
             * @return display text
             * @throws IOException when the selected file cannot be read
             */
            @Override
            protected String doInBackground() throws IOException {
                return selected.aggregate()
                        ? aggregateText(currentLogs)
                        : singleLogText(selected);
            }

            /**
             * Installs the loaded log text on the event-dispatch thread.
             */
            @Override
            protected void done() {
                if (generation != loadGeneration) {
                    return;
                }
                try {
                    showText(get());
                    showStatus(statusForSelection(selected), Theme.ForegroundRole.MUTED);
                } catch (Exception ex) {
                    showText("Log load failed: " + ex.getMessage() + '\n');
                    showStatus("Log load failed.", Theme.ForegroundRole.ERROR);
                }
            }
        }.execute();
    }

    /**
     * Scans the session directory for log files.
     *
     * @return newest-first log entries
     * @throws IOException when walking the session directory fails
     */
    private static List<LogEntry> scanSessionLogs() throws IOException {
        Path root = sessionRoot();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<LogEntry> entries = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk
                    .filter(LogPanel::isLogFile)
                    .forEach(path -> addEntry(entries, root, path));
        }
        entries.sort(Comparator.comparing(LogEntry::modified).reversed());
        if (entries.size() <= MAX_LOG_FILES) {
            return List.copyOf(entries);
        }
        return List.copyOf(entries.subList(0, MAX_LOG_FILES));
    }

    /**
     * Adds one filesystem entry when metadata can be read.
     *
     * @param entries mutable result list
     * @param root session root
     * @param path candidate path
     */
    private static void addEntry(List<LogEntry> entries, Path root, Path path) {
        try {
            Path absolute = path.toAbsolutePath().normalize();
            Instant modified = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant();
            long size = Files.size(path);
            String label = root.relativize(absolute).toString().replace(File.separatorChar, '/');
            entries.add(new LogEntry(absolute, label, modified, size, false));
        } catch (IOException | IllegalArgumentException ex) {
            // Ignore log files that disappear or cannot be relativized while scanning.
        }
    }

    /**
     * Returns whether a path is a regular UTF-8 log candidate.
     *
     * @param path candidate path
     * @return true when the path should appear in the log list
     */
    private static boolean isLogFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".log");
    }

    /**
     * Builds the aggregate pseudo-entry.
     *
     * @param entries individual log entries
     * @return aggregate entry
     */
    private static LogEntry aggregateEntry(List<LogEntry> entries) {
        long size = entries.stream().mapToLong(LogEntry::size).sum();
        Instant modified = entries.stream()
                .map(LogEntry::modified)
                .max(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);
        return new LogEntry(sessionRoot(), ALL_LOGS_LABEL, modified, size, true);
    }

    /**
     * Reads a single selected log.
     *
     * @param entry log entry
     * @return display text
     * @throws IOException when reading fails
     */
    private static String singleLogText(LogEntry entry) throws IOException {
        return headerFor(entry) + readTail(entry.path(), AGGREGATE_CHAR_LIMIT);
    }

    /**
     * Builds a newest-first combined log view.
     *
     * @param entries individual log entries
     * @return combined display text
     * @throws IOException when a selected file cannot be read
     */
    private static String aggregateText(List<LogEntry> entries) throws IOException {
        if (entries.isEmpty()) {
            return NO_LOGS_TEXT;
        }
        StringBuilder text = new StringBuilder(Math.min(AGGREGATE_CHAR_LIMIT, entries.size() * 4096));
        for (LogEntry entry : entries) {
            appendWithinLimit(text, headerFor(entry));
            appendWithinLimit(text, readTail(entry.path(), PER_FILE_TAIL_BYTES));
            appendWithinLimit(text, "\n");
            if (text.length() >= AGGREGATE_CHAR_LIMIT) {
                appendTruncationNotice(text);
                break;
            }
        }
        return text.toString();
    }

    /**
     * Appends text while preserving the aggregate character limit.
     *
     * @param builder output builder
     * @param value text to append
     */
    private static void appendWithinLimit(StringBuilder builder, String value) {
        if (builder.length() >= AGGREGATE_CHAR_LIMIT || value == null || value.isEmpty()) {
            return;
        }
        int remaining = AGGREGATE_CHAR_LIMIT - builder.length();
        builder.append(value, 0, Math.min(remaining, value.length()));
    }

    /**
     * Appends a clear aggregate truncation marker when space permits.
     *
     * @param builder output builder
     */
    private static void appendTruncationNotice(StringBuilder builder) {
        String notice = "\n[combined view truncated; open a selected log for more]\n";
        int start = Math.max(0, builder.length() - notice.length());
        builder.replace(start, builder.length(), notice.substring(0,
                Math.min(notice.length(), AGGREGATE_CHAR_LIMIT - start)));
    }

    /**
     * Reads a UTF-8 file, falling back to a tail read for large logs.
     *
     * @param path file path
     * @param byteLimit maximum bytes to read
     * @return log text
     * @throws IOException when reading fails
     */
    private static String readTail(Path path, int byteLimit) throws IOException {
        long size = Files.size(path);
        if (size <= byteLimit) {
            return Files.readString(path, StandardCharsets.UTF_8);
        }
        int bytesToRead = (int) Math.min(size, byteLimit);
        ByteBuffer buffer = ByteBuffer.allocate(bytesToRead);
        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            channel.position(size - bytesToRead);
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                // Read until the requested tail region is buffered.
            }
        }
        buffer.flip();
        return "[showing last " + humanSize(bytesToRead) + " of " + humanSize(size) + "]\n"
                + StandardCharsets.UTF_8.decode(buffer);
    }

    /**
     * Builds a visible section header for one log file.
     *
     * @param entry log entry
     * @return section header
     */
    private static String headerFor(LogEntry entry) {
        return "===== " + entry.label() + " | " + TIME_FORMAT.format(entry.modified())
                + " | " + humanSize(entry.size()) + " =====\n";
    }

    /**
     * Formats a size for compact UI display.
     *
     * @param bytes byte count
     * @return formatted size
     */
    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kib = bytes / 1024.0;
        if (kib < 1024.0) {
            return String.format(Locale.ROOT, "%.1f KiB", Double.valueOf(kib));
        }
        return String.format(Locale.ROOT, "%.1f MiB", Double.valueOf(kib / 1024.0));
    }

    /**
     * Returns status copy for the selected entry.
     *
     * @param entry selected entry
     * @return status text
     */
    private static String statusForSelection(LogEntry entry) {
        if (entry.aggregate()) {
            return "Showing newest-first combined logs from " + rootLabel() + ".";
        }
        return entry.label() + " · " + humanSize(entry.size())
                + " · modified " + TIME_FORMAT.format(entry.modified());
    }

    /**
     * Returns the session log root.
     *
     * @return absolute session root
     */
    private static Path sessionRoot() {
        return SessionCache.directory().toAbsolutePath().normalize();
    }

    /**
     * Returns a user-facing root directory label.
     *
     * @return root label
     */
    private static String rootLabel() {
        return sessionRoot().toString();
    }

    /**
     * Replaces the terminal view contents.
     *
     * @param text log text
     */
    private void showText(String text) {
        logView.clearOutput();
        logView.appendOutput(text);
        logView.setCaretPosition(0);
    }

    /**
     * Updates the footer status label.
     *
     * @param text status text
     * @param role semantic foreground role
     */
    private void showStatus(String text, Theme.ForegroundRole role) {
        statusLabel.setText(text);
        Theme.foreground(statusLabel, role);
    }

    /**
     * Opens the selected log in the desktop shell.
     */
    private void openSelected() {
        LogEntry selected = logList.getSelectedValue();
        if (selected == null || selected.aggregate()) {
            openSessionFolder();
            return;
        }
        openPath(selected.path(), "Open selected log");
    }

    /**
     * Copies the selected path or session root path.
     */
    private void copySelectedPath() {
        LogEntry selected = logList.getSelectedValue();
        Path path = selected == null || selected.aggregate() ? sessionRoot() : selected.path();
        try {
            copyText.accept(path.toString());
            showStatus("Copied " + path + ".", Theme.ForegroundRole.SUCCESS);
        } catch (RuntimeException ex) {
            showStatus("Copy failed: " + ex.getMessage(), Theme.ForegroundRole.ERROR);
        }
    }

    /**
     * Opens the shared session log folder.
     */
    private void openSessionFolder() {
        try {
            Files.createDirectories(sessionRoot());
            openPath(sessionRoot(), "Open logs folder");
        } catch (IOException ex) {
            showStatus("Open folder failed: " + ex.getMessage(), Theme.ForegroundRole.ERROR);
        }
    }

    /**
     * Opens a file or directory through the desktop shell.
     *
     * @param path path to open
     * @param actionName action label for status text
     */
    private void openPath(Path path, String actionName) {
        DesktopOpen.Result result = DesktopOpen.open(path);
        switch (result.status()) {
            case OPENED -> showStatus("Opened " + path + ".", Theme.ForegroundRole.SUCCESS);
            case UNSUPPORTED_DESKTOP, UNSUPPORTED_OPEN ->
                    showStatus(actionName + " is not supported on this desktop.", Theme.ForegroundRole.WARNING);
            case FAILED -> showStatus(actionName + " failed: " + result.detail(), Theme.ForegroundRole.ERROR);
        }
    }

    /**
     * Copies text through the system clipboard.
     *
     * @param text text to copy
     */
    private static void copyToSystemClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new StringSelection(text == null ? "" : text), null);
    }

    /**
     * Immutable log-list entry.
     *
     * @param path absolute log path, or session root for the aggregate entry
     * @param label display label
     * @param modified last-modified timestamp
     * @param size size in bytes
     * @param aggregate true for the combined pseudo-entry
     */
    private record LogEntry(Path path, String label, Instant modified, long size, boolean aggregate) {

        /**
         * Returns the display label.
         *
         * @return label text
         */
        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * List renderer that keeps log entries aligned with the active theme.
     */
    private static final class LogEntryRenderer extends DefaultListCellRenderer {

        /**
         * Serialization identifier for Swing renderer compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Renders one log-list row.
         *
         * @param list source list
         * @param value row value
         * @param index row index
         * @param selected whether the row is selected
         * @param focused whether the row has focus
         * @return renderer component
         */
        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean selected,
                boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
            if (value instanceof LogEntry entry) {
                label.setText(entry.aggregate()
                        ? ALL_LOGS_LABEL + " · " + humanSize(entry.size())
                        : entry.label() + " · " + humanSize(entry.size()));
                label.setToolTipText(entry.aggregate() ? rootLabel() : entry.path().toString());
                label.setFont(entry.aggregate() ? Theme.font(12, Font.BOLD) : Theme.mono(12));
            }
            label.setBorder(Theme.pad(2, 8, 2, 8));
            return label;
        }
    }
}
