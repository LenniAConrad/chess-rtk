package application.gui.workbench.network;

import application.Config;
import application.gui.workbench.ui.RenderAcceleration;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.TomlHighlighter;
import application.gui.workbench.ui.Ui;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;

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
public final class NetworkDiagnosticsPanel extends JPanel {

    /**
     * Serialization identifier for Swing compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Preferred diagnostics sidebar width.
     */
    private static final int SIDEBAR_WIDTH = 296;

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
     * NN cache status rows.
     */
    private final JPanel cacheRows = rowsPanel();

    /**
     * Read-only TOML config preview.
     */
    private final JTextPane configPane = new JTextPane();

    /**
     * Config path/status label.
     */
    private final JLabel configPathLabel = Ui.caption(" ");

    /**
     * Config reload button.
     */
    private final JButton reloadConfigButton = Ui.button("Reload", false,
            event -> reloadConfig());

    /**
     * Last provider used for refresh, reused by the reload button.
     */
    private RealActivations provider;

    /**
     * Last selected architecture label.
     */
    private String selectedArchitecture = "";

    /**
     * Most recent activation-cache summary supplied by the host.
     */
    private String cacheSummary = "No network snapshots cached yet.";

    /**
     * Whether to include the full config preview section.
     */
    private final boolean includeConfigPreview;

    /**
     * Theme mode used for the currently highlighted config text.
     */
    private Theme.Mode configThemeMode = Theme.mode();

    /**
     * Creates the diagnostics panel.
     */
    public NetworkDiagnosticsPanel() {
        this(true);
    }

    /**
     * Creates the diagnostics panel.
     *
     * @param includeConfigPreview true to include the TOML config preview
     */
    public NetworkDiagnosticsPanel(boolean includeConfigPreview) {
        super(new BorderLayout(0, Theme.SPACE_SM));
        this.includeConfigPreview = includeConfigPreview;
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setBorder(includeConfigPreview ? Theme.pad(Theme.SPACE_MD)
                : Theme.pad(Theme.SPACE_SM, 0, Theme.SPACE_SM, 0));
        setPreferredSize(new Dimension(SIDEBAR_WIDTH, includeConfigPreview ? 600 : 280));
        setMinimumSize(new Dimension(220, includeConfigPreview ? 260 : 180));

        JPanel top = Ui.transparentPanel(new BorderLayout(0, Theme.SPACE_MD));
        JPanel stack = Ui.transparentPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, Theme.SPACE_MD, 0);
        c.gridy = 0;
        stack.add(Ui.collapsible("Models", modelRows, true), c);
        c.gridy = 1;
        stack.add(Ui.collapsible("Backends", gpuRows, !includeConfigPreview), c);
        c.gridy = 2;
        c.insets = new Insets(0, 0, 0, 0);
        stack.add(Ui.collapsible("Cache", cacheRows, true), c);
        top.add(stack, BorderLayout.NORTH);

        configPane.setEditable(false);
        configPane.setOpaque(true);
        configPane.setBackground(Theme.TEXT_AREA);
        configPane.setForeground(Theme.TEXT);
        configPane.setSelectionColor(Theme.TEXT_SELECTION);
        configPane.setSelectedTextColor(Theme.TEXT);
        configPane.setFont(Theme.mono(12));
        configPane.setMargin(new Insets(Theme.SPACE_SM, Theme.SPACE_SM,
                Theme.SPACE_SM, Theme.SPACE_SM));
        configPane.setBorder(null);
        configPane.addPropertyChangeListener("foreground",
                event -> refreshConfigColorsIfNeeded());

        JScrollPane configScroll = new JScrollPane(configPane,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        Ui.styleScrollPane(configScroll);

        JPanel configHeader = Ui.transparentPanel(
    new BorderLayout(Theme.SPACE_SM, 0));
        configHeader.add(configPathLabel, BorderLayout.CENTER);
        configHeader.add(reloadConfigButton, BorderLayout.EAST);

        JPanel configBody = Ui.transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        configBody.add(configHeader, BorderLayout.NORTH);
        configBody.add(configScroll, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        if (includeConfigPreview) {
            add(Ui.collapsible("Config", configBody, false), BorderLayout.CENTER);
        } else {
            add(Ui.transparentPanel(new BorderLayout()), BorderLayout.CENTER);
        }
    }

    /**
     * Refreshes theme-sensitive syntax colors before descendants paint.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        refreshConfigColorsIfNeeded();
        super.paintComponent(graphics);
    }

    /**
     * Refreshes model, GPU and config diagnostics.
     *
     * @param newProvider network activation provider
     * @param architecture selected architecture label
     */
    public void refresh(RealActivations newProvider, String architecture) {
        refresh(newProvider, architecture, cacheSummary);
    }

    /**
     * Refreshes model, GPU, cache, and config diagnostics.
     *
     * @param newProvider network activation provider
     * @param architecture selected architecture label
     * @param newCacheSummary current activation-cache summary
     */
    public void refresh(RealActivations newProvider, String architecture,
            String newCacheSummary) {
        provider = newProvider;
        selectedArchitecture = architecture == null ? "" : architecture;
        cacheSummary = newCacheSummary == null || newCacheSummary.isBlank()
                ? "No network snapshots cached yet."
                : newCacheSummary;
        refreshModels();
        refreshGpu();
        refreshCache();
        if (includeConfigPreview) {
            refreshConfigPreview();
        }
    }

    /**
     * Reloads CLI config and refreshes diagnostics.
     */
    private void reloadConfig() {
        Config.reload();
        refresh(provider, selectedArchitecture, cacheSummary);
    }

    /**
     * Rebuilds model status rows.
     */
    private void refreshModels() {
        modelRows.removeAll();
        if (provider == null) {
            modelRows.add(statusRow("Provider", "inactive", "Network provider not ready",
                    Theme.ForegroundRole.MUTED));
        } else {
            for (RealActivations.ModelStatus status : provider.modelStatuses()) {
                String detail = modelDetail(status);
                if (isSelectedModel(status)) {
                    detail = "active - " + detail;
                }
                modelRows.add(statusRow(status.label(), status.state(), detail,
                        modelRole(status)));
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
        modelRows.add(Box.createVerticalStrut(Theme.SPACE_XS));
        modelRows.add(statusRow("Config LC0", pathState(Config.getLc0ModelPath()),
                pathDetail(Config.getLc0ModelPath()), pathRole(Config.getLc0ModelPath())));
        modelRows.add(statusRow("Config T5", pathState(Config.getT5ModelPath()),
                pathDetail(Config.getT5ModelPath()), pathRole(Config.getT5ModelPath())));
    }

    /**
     * Rebuilds GPU backend status rows.
     */
    private void refreshGpu() {
        gpuRows.removeAll();
        for (GpuState state : gpuStates()) {
            gpuRows.add(statusRow(state.label, state.state, state.detail, state.role));
        }
        gpuRows.revalidate();
        gpuRows.repaint();
    }

    /**
     * Rebuilds activation-cache status rows.
     */
    private void refreshCache() {
        cacheRows.removeAll();
        Theme.ForegroundRole role = cacheSummary.startsWith("0 /")
                ? Theme.ForegroundRole.MUTED
                : Theme.ForegroundRole.INFO;
        cacheRows.add(statusRow("Activation", cacheSummary.startsWith("0 /") ? "empty" : "ready",
                cacheSummary, role));
        cacheRows.revalidate();
        cacheRows.repaint();
    }

    /**
     * Reloads the config preview from disk and syntax-colors it.
     */
    private void refreshConfigPreview() {
        Path configPath = Config.getConfigPath();
        try {
            String text = Files.readString(configPath, StandardCharsets.UTF_8);
            configPathLabel.setText(pathLabel(configPath));
            configPathLabel.setToolTipText(configPath.toAbsolutePath().toString());
            TomlHighlighter.apply(configPane, text);
            configThemeMode = Theme.mode();
        } catch (IOException | RuntimeException ex) {
            configPathLabel.setText("Cannot read " + configPath);
            configPathLabel.setToolTipText(configPath.toAbsolutePath().toString());
            TomlHighlighter.apply(configPane,
                    "# Failed to read config\n# " + ex.getMessage());
            configThemeMode = Theme.mode();
        }
    }

    /**
     * Reapplies config preview colors if the active theme changed.
     */
    private void refreshConfigColorsIfNeeded() {
        if (configThemeMode == Theme.mode()) {
            return;
        }
        configThemeMode = Theme.mode();
        configPane.setBackground(Theme.TEXT_AREA);
        configPane.setForeground(Theme.TEXT);
        configPane.setSelectionColor(Theme.TEXT_SELECTION);
        configPane.setSelectedTextColor(Theme.TEXT);
        TomlHighlighter.apply(configPane, configPane.getText());
    }

    /**
     * Builds all optional GPU backend rows.
     *
     * @return GPU state rows
     */
    private static List<GpuState> gpuStates() {
        List<GpuState> out = new ArrayList<>();
        out.add(java2dState());
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
     * Builds the Java2D rendering-acceleration row.
     *
     * @return Java2D state row
     */
    private static GpuState java2dState() {
        RenderAcceleration.RenderStatus status = RenderAcceleration.status();
        Theme.ForegroundRole role = status.volatileImageAccelerated()
                ? Theme.ForegroundRole.SUCCESS
                : status.gpuRequested()
                        ? Theme.ForegroundRole.INFO
                        : Theme.ForegroundRole.MUTED;
        String state = status.volatileImageAccelerated()
                ? "gpu"
                : status.gpuRequested() ? "requested" : "default";
        return new GpuState("Java2D", state, RenderAcceleration.summary(), role);
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
                    Theme.ForegroundRole.SUCCESS);
        }
        if (loaded) {
            return new GpuState(label, "library", "loaded, no visible device",
                    Theme.ForegroundRole.WARNING);
        }
        return new GpuState(label, "cpu", "native backend not loaded",
                Theme.ForegroundRole.MUTED);
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
     * Creates a vertical rows panel.
     *
     * @return rows panel
     */
    private static JPanel rowsPanel() {
        JPanel panel = Ui.transparentPanel(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    /**
     * Creates one aligned status row.
     *
     * @param label row label
     * @param state state text
     * @param detail detail text
     * @param role state color role
     * @return row component
     */
    private static JComponent statusRow(String label, String state, String detail,
            Theme.ForegroundRole role) {
        JPanel row = Ui.transparentPanel(new GridBagLayout());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(Theme.pad(2, 0));

        JLabel labelView = new JLabel(label);
        labelView.setFont(Theme.font(11, Font.BOLD));
        Theme.foreground(labelView, Theme.ForegroundRole.TEXT);

        JLabel stateView = new JLabel(state);
        stateView.setFont(Theme.font(11, Font.BOLD));
        Theme.foreground(stateView, role);

        JLabel detailView = new JLabel(elide(detail, Theme.font(11, Font.PLAIN)));
        detailView.setFont(Theme.font(11, Font.PLAIN));
        Theme.foreground(detailView, Theme.ForegroundRole.MUTED);
        detailView.setToolTipText(detail);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, Theme.SPACE_SM);
        c.anchor = GridBagConstraints.WEST;
        row.add(new Dot(role), c);

        c.gridx = 1;
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 0, Theme.SPACE_SM);
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
    private static String modelDetail(RealActivations.ModelStatus status) {
        String detail = status.detail();
        if (status.path() == null) {
            return detail;
        }
        return detail + " - " + pathLabel(status.path());
    }

    /**
     * Returns whether a model row corresponds to the currently selected
     * architecture.
     *
     * @param status model status
     * @return true when active
     */
    private boolean isSelectedModel(RealActivations.ModelStatus status) {
        if (selectedArchitecture == null || selectedArchitecture.isBlank()) {
            return false;
        }
        if (status.label().startsWith("NNUE")) {
            return selectedArchitecture.startsWith("NNUE");
        }
        if (status.label().contains("CNN")) {
            return selectedArchitecture.contains("CNN");
        }
        if (status.label().contains("BT4")) {
            return selectedArchitecture.contains("BT4");
        }
        return selectedArchitecture.equals(status.label());
    }

    /**
     * Returns the status role for a workbench model.
     *
     * @param status model status
     * @return color role
     */
    private static Theme.ForegroundRole modelRole(RealActivations.ModelStatus status) {
        if (status.loaded()) {
            return Theme.ForegroundRole.SUCCESS;
        }
        if (!status.present() || "fallback".equals(status.state())) {
            return Theme.ForegroundRole.WARNING;
        }
        return Theme.ForegroundRole.INFO;
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
     * Returns status role for a configured path.
     *
     * @param text path text
     * @return color role
     */
    private static Theme.ForegroundRole pathRole(String text) {
        return "present".equals(pathState(text))
                ? Theme.ForegroundRole.SUCCESS
                : Theme.ForegroundRole.WARNING;
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
        return Ui.elide(text, metrics, DETAIL_WIDTH);
    }

    /**
     * Boolean probe functional interface.
     */
    @FunctionalInterface
    private interface BooleanProbe {
        /**
         * Returns the probed boolean value.
         *
         * @return probe value
         */
        boolean get();
    }

    /**
     * Integer probe functional interface.
     */
    @FunctionalInterface
    private interface IntProbe {
        /**
         * Returns the probed integer value.
         *
         * @return probe value
         */
        int get();
    }

    /**
     * GPU state row.
     *
     * @param label backend label
     * @param state state text
     * @param detail detail text
     * @param role status color role
     */
    private record GpuState(String label, String state, String detail,
            Theme.ForegroundRole role) {
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
         * Dot color role.
         */
        private final Theme.ForegroundRole role;

        /**
         * Creates a dot.
         *
         * @param role dot color role
         */
        Dot(Theme.ForegroundRole role) {
            this.role = role;
            setOpaque(false);
            setPreferredSize(new Dimension(10, 18));
        }

        /**
         * Paints the status dot.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.foregroundColor(role));
                int d = 7;
                g.fillOval(1, (getHeight() - d) / 2, d, d);
            } finally {
                g.dispose();
            }
        }
    }
}
