package application.gui.workbench.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * A surface that hosts several modes behind a {@link SegmentedSwitcher}, each
 * built lazily on first activation and cached in a {@link CardLayout}.
 *
 * <p>This is the shared primitive behind the workbench's consolidated tabs: the
 * Board surface (Analyze/Play/Solve/Relations/Draw) and the Engine surface
 * (Evaluator/Search/Tree) are both lists of {@link WorkspaceMode} descriptors
 * over this one mechanism. The opening mode can be built eagerly when the shell
 * needs to wire something through it at startup; every other mode builds the
 * first time it is selected, preserving its state across later switches.</p>
 */
public class SwitchedWorkspace extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Mode switcher shown at the top of the workspace.
     */
    private final SegmentedSwitcher switcher;

    /**
     * Optional shell title shown in the workspace header.
     */
    private final String workspaceTitle;

    /**
     * Shared workspace header for the active mode.
     */
    private final WorkspaceHeader workspaceHeader;

    /**
     * Card layout swapping the active mode body.
     */
    private final CardLayout cards = new CardLayout();

    /**
     * Host for the mode bodies.
     */
    private final JPanel body = new JPanel(cards);

    /**
     * Mode descriptors, in switcher order.
     */
    private final transient List<WorkspaceMode> modes;

    /**
     * Whether each mode's body has been built yet.
     */
    private final boolean[] built;

    /**
     * Optional listener notified with the mode index whenever a mode is shown
     * (after its body is built). Used to re-wire shared resources — e.g. moving
     * the one shared board into the active mode and configuring it.
     */
    private transient IntConsumer modeListener;

    /**
     * Creates a switched workspace.
     *
     * @param labels segment labels in mode order
     * @param builders lazy body builders in mode order (same length as labels)
     * @param eagerMode mode to build and show immediately
     */
    public SwitchedWorkspace(String[] labels, List<Supplier<JComponent>> builders, int eagerMode) {
        this("", toModes(labels, builders), eagerMode);
    }

    /**
     * Creates a titled switched workspace from legacy label/builder lists.
     *
     * @param title surface title
     * @param labels segment labels in mode order
     * @param builders lazy body builders in mode order (same length as labels)
     * @param eagerMode mode to build and show immediately
     */
    public SwitchedWorkspace(String title, String[] labels, List<Supplier<JComponent>> builders, int eagerMode) {
        this(title, toModes(labels, builders), eagerMode);
    }

    /**
     * Creates a switched workspace from mode descriptors.
     *
     * @param modes mode descriptors in switcher order
     * @param eagerMode mode to build and show immediately
     */
    public SwitchedWorkspace(List<WorkspaceMode> modes, int eagerMode) {
        this("", modes, eagerMode);
    }

    /**
     * Creates a titled switched workspace from mode descriptors.
     *
     * @param title surface title
     * @param modes mode descriptors in switcher order
     * @param eagerMode mode to build and show immediately
     */
    public SwitchedWorkspace(String title, List<WorkspaceMode> modes, int eagerMode) {
        super(new BorderLayout(0, Theme.SPACE_SM));
        requireUsableModes(modes);
        requireValidEagerMode(eagerMode, modes.size());
        setOpaque(true);
        setBackground(Theme.BG);
        this.workspaceTitle = title == null ? "" : title.trim();
        this.modes = List.copyOf(modes);
        this.built = new boolean[this.modes.size()];
        switcher = new SegmentedSwitcher(labels(this.modes));
        workspaceHeader = new WorkspaceHeader("", "", null);
        workspaceHeader.setVisible(!workspaceTitle.isBlank());

        // The switcher sits in a quiet toolbar band closed by a hairline, the
        // same chrome the Network and Datasets tabs lead with, so a consolidated
        // tab reads as one surface with selectable modes rather than nested tabs.
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        Ui.styleToolbarBand(header,
                Theme.pad(Theme.SPACE_SM, Theme.SPACE_MD, Theme.SPACE_SM, Theme.SPACE_MD));
        header.add(switcher);

        JPanel top = new JPanel(new BorderLayout(0, 0));
        top.setOpaque(false);
        top.add(workspaceHeader, BorderLayout.NORTH);
        top.add(header, BorderLayout.SOUTH);

        body.setOpaque(true);
        body.setBackground(Theme.BG);
        body.setBorder(Theme.pad(Theme.SPACE_SM, Theme.SPACE_SM, 0, Theme.SPACE_SM));

        add(top, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);

        switcher.addActionListener(event -> showMode(switcher.getSelectedIndex()));
        // Build and show the eager mode now; the switcher already reads index 0,
        // so a non-zero eager mode drives the switcher (which fires showMode).
        if (eagerMode == switcher.getSelectedIndex()) {
            showMode(eagerMode);
        } else {
            switcher.setSelectedIndex(eagerMode);
        }
    }

    /**
     * Sets a listener notified with the mode index each time a mode is shown
     * (after the body is built). Fires immediately for the already-active mode
     * so the caller can wire the initial state.
     *
     * @param listener mode-activation listener, or {@code null} to clear
     */
    public void setModeListener(IntConsumer listener) {
        this.modeListener = listener;
        if (listener != null) {
            listener.accept(mode());
        }
    }

    /**
     * Returns the active mode index.
     *
     * @return active mode
     */
    public int mode() {
        return switcher.getSelectedIndex();
    }

    /**
     * Switches to a mode, building its body on first use. Safe to call with the
     * already-active mode (a no-op beyond ensuring the body is built).
     *
     * @param mode mode index
     */
    public void setMode(int mode) {
        if (mode < 0 || mode >= modes.size()) {
            return;
        }
        if (switcher.getSelectedIndex() == mode) {
            showMode(mode);
        } else {
            switcher.setSelectedIndex(mode);
        }
    }

    /**
     * Returns whether a mode's body has been built.
     *
     * @param mode mode index
     * @return true when built
     */
    public boolean isBuilt(int mode) {
        return mode >= 0 && mode < built.length && built[mode];
    }

    /**
     * Refreshes the active mode header from its metadata suppliers.
     */
    public void refreshHeader() {
        refreshHeader(mode());
    }

    /**
     * Verifies that every switcher label has exactly one lazy body builder.
     *
     * @param labels segment labels in mode order
     * @param builders lazy body builders in mode order
     */
    private static void requireMatchingModeCounts(String[] labels, List<Supplier<JComponent>> builders) {
        if (labels.length != builders.size()) {
            throw new IllegalArgumentException("SwitchedWorkspace labels and builders differ: "
                    + labels.length + " labels, " + builders.size() + " builders");
        }
    }

    /**
     * Converts the legacy label/builder pair into mode descriptors.
     *
     * @param labels segment labels in mode order
     * @param builders lazy body builders in mode order
     * @return mode descriptors
     */
    private static List<WorkspaceMode> toModes(String[] labels, List<Supplier<JComponent>> builders) {
        requireMatchingModeCounts(labels, builders);
        List<WorkspaceMode> converted = new ArrayList<>(labels.length);
        for (int i = 0; i < labels.length; i++) {
            converted.add(new WorkspaceMode(labels[i], builders.get(i)));
        }
        return converted;
    }

    /**
     * Verifies that a descriptor-backed workspace has at least one mode.
     *
     * @param modes mode descriptors in display order
     */
    private static void requireUsableModes(List<WorkspaceMode> modes) {
        if (modes == null || modes.isEmpty()) {
            throw new IllegalArgumentException("SwitchedWorkspace requires at least one mode");
        }
    }

    /**
     * Verifies that the eager mode points at a registered mode.
     *
     * @param eagerMode mode to build and show immediately
     * @param modeCount number of registered modes
     */
    private static void requireValidEagerMode(int eagerMode, int modeCount) {
        if (eagerMode < 0 || eagerMode >= modeCount) {
            throw new IllegalArgumentException("SwitchedWorkspace eager mode out of range: "
                    + eagerMode + " for " + modeCount + " modes");
        }
    }

    /**
     * Extracts segment labels from mode descriptors.
     *
     * @param modes mode descriptors in display order
     * @return segment labels
     */
    private static String[] labels(List<WorkspaceMode> modes) {
        String[] labels = new String[modes.size()];
        for (int i = 0; i < modes.size(); i++) {
            labels[i] = modes.get(i).label();
        }
        return labels;
    }

    /**
     * Builds (on first use) and reveals a mode's body.
     *
     * @param mode mode index
     */
    private void showMode(int mode) {
        if (mode < 0 || mode >= modes.size()) {
            return;
        }
        if (!built[mode]) {
            JComponent panel = modes.get(mode).builder().get();
            JComponent body0 = panel == null ? Ui.transparentPanel(new BorderLayout()) : panel;
            // A mode built after startup misses the shell's initial theme pass, so
            // sub-components that cache palette colours at construction (combos,
            // spinners, scroll viewports) can render with light static-palette
            // defaults — the white-console / light-batch bug. Refresh the freshly
            // built body against the live theme before it is ever shown.
            Theme.refreshComponentTree(body0);
            body.add(body0, key(mode));
            built[mode] = true;
        }
        cards.show(body, key(mode));
        if (modeListener != null) {
            modeListener.accept(mode);
        }
        refreshHeader(mode);
        body.revalidate();
        body.repaint();
        repaint();
    }

    /**
     * Refreshes the workspace header for one mode.
     *
     * @param mode mode index
     */
    private void refreshHeader(int mode) {
        if (mode < 0 || mode >= modes.size()) {
            return;
        }
        WorkspaceMode descriptor = modes.get(mode);
        workspaceHeader.setTitle(headerTitle(descriptor));
        workspaceHeader.setContext(descriptor.context());
        workspaceHeader.setActions(descriptor.actions());
    }

    /**
     * Returns the header title for a mode.
     *
     * @param mode mode descriptor
     * @return title
     */
    private String headerTitle(WorkspaceMode mode) {
        if (workspaceTitle.isBlank()) {
            return mode.label();
        }
        return workspaceTitle + " / " + mode.label();
    }

    /**
     * Returns the card key for a mode.
     *
     * @param mode mode index
     * @return card key
     */
    private static String key(int mode) {
        return "mode-" + mode;
    }
}
