package application.gui.workbench;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;

import application.Config;

/**
 * Runtime diagnostics surface for the Network workbench tab.
 *
 * <p>
 * The panel intentionally reports status without triggering heavyweight model
 * loads: model rows show whether each weights file exists and whether the
 * provider has already loaded it; GPU rows show optional native-library and
 * device visibility; the lower half is a read-only, syntax-colored preview of
 * the active CLI TOML config.
 * </p>
 */
final class WorkbenchNetworkDiagnosticsPanel extends JPanel {

    /**
     * Serialization identifier for Swing compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Preferred diagnostics sidebar width.
     */
    private static final int SIDEBAR_WIDTH = 360;

    /**
     * Pixel budget for compact path details in status rows.
     */
    private static final int DETAIL_WIDTH = 210;

    /**
     * Model status rows.
     */
    private final JPanel modelRows = rowsPanel();

    /**
     * GPU status rows.
     */
    private final JPanel gpuRows = rowsPanel();

    /**
     * Read-only TOML config preview.
     */
    private final JTextPane configPane = new JTextPane();

    /**
     * Config path/status label.
     */
    private final JLabel configPathLabel = WorkbenchUi.caption(" ");

    /**
     * Config reload button.
     */
    private final JButton reloadConfigButton = WorkbenchUi.button("Reload", false,
            event -> reloadConfig());

    /**
     * Last provider used for refresh, reused by the reload button.
     */
    private WorkbenchRealActivations provider;

    /**
     * Last selected architecture label.
     */
    private String selectedArchitecture = "";

    /**
     * Creates the diagnostics panel.
     */
    WorkbenchNetworkDiagnosticsPanel() {
        super(new BorderLayout(0, WorkbenchTheme.SPACE_SM));
        setOpaque(true);
        setBackground(WorkbenchTheme.PANEL_SOLID);
        setBorder(WorkbenchTheme.pad(WorkbenchTheme.SPACE_MD));
        setPreferredSize(new Dimension(SIDEBAR_WIDTH, 600));

        JPanel top = WorkbenchUi.transparentPanel(new BorderLayout(0, WorkbenchTheme.SPACE_MD));
        JPanel stack = WorkbenchUi.transparentPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, WorkbenchTheme.SPACE_MD, 0);
        c.gridy = 0;
        stack.add(section("Models", modelRows), c);
        c.gridy = 1;
        c.insets = new Insets(0, 0, 0, 0);
        stack.add(section("GPU", gpuRows), c);
        top.add(stack, BorderLayout.NORTH);

        configPane.setEditable(false);
        configPane.setOpaque(true);
        configPane.setBackground(WorkbenchTheme.TEXT_AREA);
        configPane.setForeground(WorkbenchTheme.TEXT);
        configPane.setSelectionColor(WorkbenchTheme.TEXT_SELECTION);
        configPane.setSelectedTextColor(WorkbenchTheme.TEXT);
        configPane.setFont(WorkbenchTheme.mono(12));
        configPane.setMargin(new Insets(WorkbenchTheme.SPACE_SM, WorkbenchTheme.SPACE_SM,
                WorkbenchTheme.SPACE_SM, WorkbenchTheme.SPACE_SM));
        configPane.setBorder(null);

        JScrollPane configScroll = new JScrollPane(configPane,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        WorkbenchUi.styleScrollPane(configScroll);

        JPanel configHeader = WorkbenchUi.transparentPanel(
                new BorderLayout(WorkbenchTheme.SPACE_SM, 0));
        JPanel title = WorkbenchUi.transparentPanel(new BorderLayout(0, WorkbenchTheme.SPACE_XS));
        title.add(WorkbenchTheme.section("Config"), BorderLayout.NORTH);
        title.add(configPathLabel, BorderLayout.SOUTH);
        configHeader.add(title, BorderLayout.CENTER);
        configHeader.add(reloadConfigButton, BorderLayout.EAST);

        JPanel configSection = WorkbenchUi.transparentPanel(new BorderLayout(0, WorkbenchTheme.SPACE_SM));
        configSection.add(configHeader, BorderLayout.NORTH);
        configSection.add(configScroll, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(configSection, BorderLayout.CENTER);
    }

    /**
     * Refreshes model, GPU and config diagnostics.
     *
     * @param newProvider network activation provider
     * @param architecture selected architecture label
     */
    void refresh(WorkbenchRealActivations newProvider, String architecture) {
        provider = newProvider;
        selectedArchitecture = architecture == null ? "" : architecture;
        refreshModels();
        refreshGpu();
        refreshConfigPreview();
    }

    /**
     * Reloads CLI config and refreshes diagnostics.
     */
    private void reloadConfig() {
        Config.reload();
        refresh(provider, selectedArchitecture);
    }

    /**
     * Rebuilds model status rows.
     */
    private void refreshModels() {
        modelRows.removeAll();
        if (provider == null) {
            modelRows.add(statusRow("Provider", "inactive", "Network provider not ready",
                    WorkbenchTheme.MUTED));
        } else {
            for (WorkbenchRealActivations.ModelStatus status : provider.modelStatuses()) {
                String detail = modelDetail(status);
                if (isSelectedModel(status)) {
                    detail = "active - " + detail;
                }
                modelRows.add(statusRow(status.label(), status.state(), detail,
                        modelColor(status)));
            }
        }
        addConfigModelRows();
        modelRows.revalidate();
        modelRows.repaint();
    }

    /**
     * Adds configured model-default rows from the CLI config.
     */
    private void addConfigModelRows() {
        modelRows.add(Box.createVerticalStrut(WorkbenchTheme.SPACE_XS));
        modelRows.add(statusRow("Config LC0", pathState(Config.getLc0ModelPath()),
                pathDetail(Config.getLc0ModelPath()), pathColor(Config.getLc0ModelPath())));
        modelRows.add(statusRow("Config T5", pathState(Config.getT5ModelPath()),
                pathDetail(Config.getT5ModelPath()), pathColor(Config.getT5ModelPath())));
    }

    /**
     * Rebuilds GPU backend status rows.
     */
    private void refreshGpu() {
        gpuRows.removeAll();
        for (GpuState state : gpuStates()) {
            gpuRows.add(statusRow(state.label, state.state, state.detail, state.color));
        }
        gpuRows.revalidate();
        gpuRows.repaint();
    }

    /**
     * Reloads the config preview from disk and syntax-colors it.
     */
    private void refreshConfigPreview() {
        Path configPath = Config.getConfigPath();
        try {
            String text = Files.readString(configPath);
            configPathLabel.setText(pathLabel(configPath));
            configPathLabel.setToolTipText(configPath.toAbsolutePath().toString());
            WorkbenchTomlHighlighter.apply(configPane, text);
        } catch (IOException | RuntimeException ex) {
            configPathLabel.setText("Cannot read " + configPath);
            configPathLabel.setToolTipText(configPath.toAbsolutePath().toString());
            WorkbenchTomlHighlighter.apply(configPane,
                    "# Failed to read config\n# " + ex.getMessage());
        }
    }

    /**
     * Builds all optional GPU backend rows.
     *
     * @return GPU state rows
     */
    private static List<GpuState> gpuStates() {
        List<GpuState> out = new ArrayList<>();
        out.add(gpu("LC0 CUDA", safeLoaded(() -> chess.nn.lc0.cnn.cuda.Support.isLoaded()),
                safeCount(() -> chess.nn.lc0.cnn.cuda.Support.deviceCount())));
        out.add(gpu("LC0 ROCm", safeLoaded(() -> chess.nn.lc0.cnn.rocm.Support.isLoaded()),
                safeCount(() -> chess.nn.lc0.cnn.rocm.Support.deviceCount())));
        out.add(gpu("LC0 oneAPI", safeLoaded(() -> chess.nn.lc0.cnn.oneapi.Support.isLoaded()),
                safeCount(() -> chess.nn.lc0.cnn.oneapi.Support.deviceCount())));
        out.add(gpu("T5 oneAPI", safeLoaded(() -> chess.nn.t5.oneapi.Support.isLoaded()),
                safeCount(() -> chess.nn.t5.oneapi.Support.deviceCount())));
        return out;
    }

    /**
     * Builds a GPU state row.
     *
     * @param label backend label
     * @param loaded true when JNI library loaded
     * @param devices visible device count
     * @return state row
     */
    private static GpuState gpu(String label, boolean loaded, int devices) {
        if (loaded && devices > 0) {
            return new GpuState(label, "available",
                    devices + (devices == 1 ? " device" : " devices"),
                    WorkbenchTheme.STATUS_SUCCESS_TEXT);
        }
        if (loaded) {
            return new GpuState(label, "library", "loaded, no visible device",
                    WorkbenchTheme.STATUS_WARNING_TEXT);
        }
        return new GpuState(label, "cpu", "native backend not loaded",
                WorkbenchTheme.MUTED);
    }

    /**
     * Safely invokes a boolean GPU probe.
     *
     * @param probe probe
     * @return probe result or false
     */
    private static boolean safeLoaded(BooleanProbe probe) {
        try {
            return probe.get();
        } catch (LinkageError | RuntimeException ex) {
            return false;
        }
    }

    /**
     * Safely invokes an integer GPU probe.
     *
     * @param probe probe
     * @return probe result or zero
     */
    private static int safeCount(IntProbe probe) {
        try {
            return Math.max(0, probe.get());
        } catch (LinkageError | RuntimeException ex) {
            return 0;
        }
    }

    /**
     * Creates a titled section container.
     *
     * @param title section title
     * @param content section content
     * @return component
     */
    private static JComponent section(String title, JComponent content) {
        JPanel panel = WorkbenchUi.transparentPanel(new BorderLayout(0, WorkbenchTheme.SPACE_SM));
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, WorkbenchTheme.LINE));
        panel.add(WorkbenchTheme.section(title), BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates a vertical rows panel.
     *
     * @return rows panel
     */
    private static JPanel rowsPanel() {
        JPanel panel = WorkbenchUi.transparentPanel(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    /**
     * Creates one aligned status row.
     *
     * @param label row label
     * @param state state text
     * @param detail detail text
     * @param color state color
     * @return row component
     */
    private static JComponent statusRow(String label, String state, String detail, Color color) {
        JPanel row = WorkbenchUi.transparentPanel(new GridBagLayout());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(WorkbenchTheme.pad(2, 0));

        JLabel labelView = new JLabel(label);
        labelView.setFont(WorkbenchTheme.font(11, Font.BOLD));
        labelView.setForeground(WorkbenchTheme.TEXT);

        JLabel stateView = new JLabel(state);
        stateView.setFont(WorkbenchTheme.font(11, Font.BOLD));
        stateView.setForeground(color);

        JLabel detailView = new JLabel(elide(detail, WorkbenchTheme.font(11, Font.PLAIN)));
        detailView.setFont(WorkbenchTheme.font(11, Font.PLAIN));
        detailView.setForeground(WorkbenchTheme.MUTED);
        detailView.setToolTipText(detail);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, WorkbenchTheme.SPACE_SM);
        c.anchor = GridBagConstraints.WEST;
        row.add(new Dot(color), c);

        c.gridx = 1;
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 0, WorkbenchTheme.SPACE_SM);
        labelView.setPreferredSize(new Dimension(78, 18));
        row.add(labelView, c);

        c.gridx = 2;
        stateView.setPreferredSize(new Dimension(68, 18));
        row.add(stateView, c);

        c.gridx = 3;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 0);
        row.add(detailView, c);
        return row;
    }

    /**
     * Returns detail text for a workbench model.
     *
     * @param status model status
     * @return detail
     */
    private static String modelDetail(WorkbenchRealActivations.ModelStatus status) {
        String path = status.path() == null ? "" : status.path().toString();
        String prefix = path.isBlank() ? "" : path + " - ";
        return prefix + status.detail();
    }

    /**
     * Returns whether a model row corresponds to the currently selected
     * architecture.
     *
     * @param status model status
     * @return true when active
     */
    private boolean isSelectedModel(WorkbenchRealActivations.ModelStatus status) {
        if (selectedArchitecture == null || selectedArchitecture.isBlank()) {
            return false;
        }
        if ("NNUE".equals(status.label())) {
            return selectedArchitecture.startsWith("NNUE");
        }
        return selectedArchitecture.equals(status.label());
    }

    /**
     * Returns the status color for a workbench model.
     *
     * @param status model status
     * @return color
     */
    private static Color modelColor(WorkbenchRealActivations.ModelStatus status) {
        if (status.loaded()) {
            return WorkbenchTheme.STATUS_SUCCESS_TEXT;
        }
        if (!status.present() || "fallback".equals(status.state())) {
            return WorkbenchTheme.STATUS_WARNING_TEXT;
        }
        return WorkbenchTheme.STATUS_INFO_TEXT;
    }

    /**
     * Returns state text for a configured path.
     *
     * @param text path text
     * @return state
     */
    private static String pathState(String text) {
        if (text == null || text.isBlank()) {
            return "empty";
        }
        try {
            return Files.exists(Path.of(text)) ? "present" : "missing";
        } catch (RuntimeException ex) {
            return "invalid";
        }
    }

    /**
     * Returns detail text for a configured path.
     *
     * @param text path text
     * @return detail
     */
    private static String pathDetail(String text) {
        if (text == null || text.isBlank()) {
            return "no path configured";
        }
        try {
            return Path.of(text).toString();
        } catch (RuntimeException ex) {
            return ex.getMessage();
        }
    }

    /**
     * Returns status color for a configured path.
     *
     * @param text path text
     * @return color
     */
    private static Color pathColor(String text) {
        return "present".equals(pathState(text))
                ? WorkbenchTheme.STATUS_SUCCESS_TEXT
                : WorkbenchTheme.STATUS_WARNING_TEXT;
    }

    /**
     * Returns a compact display path.
     *
     * @param path path
     * @return compact label
     */
    private static String pathLabel(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path file = absolute.getFileName();
        Path parent = absolute.getParent();
        if (parent == null || file == null) {
            return absolute.toString();
        }
        Path parentName = parent.getFileName();
        return (parentName == null ? parent : parentName) + "/" + file;
    }

    /**
     * Elides detail text to a stable row width.
     *
     * @param text text
     * @param font font
     * @return elided text
     */
    private static String elide(String text, Font font) {
        JLabel probe = new JLabel();
        probe.setFont(font);
        FontMetrics metrics = probe.getFontMetrics(font);
        return WorkbenchUi.elide(text, metrics, DETAIL_WIDTH);
    }

    /**
     * Boolean probe functional interface.
     */
    @FunctionalInterface
    private interface BooleanProbe {
        boolean get();
    }

    /**
     * Integer probe functional interface.
     */
    @FunctionalInterface
    private interface IntProbe {
        int get();
    }

    /**
     * GPU state row.
     *
     * @param label backend label
     * @param state state text
     * @param detail detail text
     * @param color status color
     */
    private record GpuState(String label, String state, String detail, Color color) {
    }

    /**
     * Small colored state dot.
     */
    private static final class Dot extends JComponent {

        /**
         * Serialization identifier for Swing compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Dot color.
         */
        private final Color color;

        /**
         * Creates a dot.
         *
         * @param color dot color
         */
        Dot(Color color) {
            this.color = color;
            setOpaque(false);
            setPreferredSize(new Dimension(10, 18));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(color);
                int d = 7;
                g.fillOval(1, (getHeight() - d) / 2, d, d);
            } finally {
                g.dispose();
            }
        }
    }
}
