package application.gui.workbench.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * A surface that hosts several modes behind a {@link SegmentedSwitcher}, each
 * built lazily on first activation and cached in a {@link CardLayout}.
 *
 * <p>This is the shared primitive behind the workbench's consolidated tabs — the
 * Board surface (Analyze/Play/Solve/Relations) and the Engine surface
 * (Network/Search) are both just a labelled list of lazy builders over this one
 * mechanism, so adding a mode is one label + one builder. The opening mode can
 * be built eagerly when the shell needs to wire something through it at startup;
 * every other mode builds the first time it is selected, preserving its state
 * across later switches.</p>
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
     * Card layout swapping the active mode body.
     */
    private final CardLayout cards = new CardLayout();

    /**
     * Host for the mode bodies.
     */
    private final JPanel body = new JPanel(cards);

    /**
     * Lazy builders, one per mode, in mode order.
     */
    private final transient List<Supplier<JComponent>> builders;

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
        super(new BorderLayout(0, Theme.SPACE_SM));
        setOpaque(true);
        setBackground(Theme.BG);
        this.builders = List.copyOf(builders);
        this.built = new boolean[this.builders.size()];
        switcher = new SegmentedSwitcher(labels);

        // The switcher sits in a quiet toolbar band closed by a hairline, the
        // same chrome the Network and Datasets tabs lead with, so a consolidated
        // tab reads as one surface with selectable modes rather than nested tabs.
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        Ui.styleToolbarBand(header,
                Theme.pad(Theme.SPACE_SM, Theme.SPACE_MD, Theme.SPACE_SM, Theme.SPACE_MD));
        header.add(switcher);

        body.setOpaque(false);
        body.setBorder(Theme.pad(Theme.SPACE_SM, Theme.SPACE_SM, 0, Theme.SPACE_SM));

        add(header, BorderLayout.NORTH);
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
        if (mode < 0 || mode >= builders.size()) {
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
     * Builds (on first use) and reveals a mode's body.
     *
     * @param mode mode index
     */
    private void showMode(int mode) {
        if (mode < 0 || mode >= builders.size()) {
            return;
        }
        if (!built[mode]) {
            JComponent panel = builders.get(mode).get();
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
