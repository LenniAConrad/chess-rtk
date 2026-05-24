package application.gui.workbench.layout;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BorderLayout;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Lightweight placeholder that builds an expensive editor panel only after it
 * is first attached to the visible Swing hierarchy.
 */
public final class LazyPanel extends JPanel {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Human-readable panel name used in the loading placeholder.
     */
    private final String name;

    /**
     * Factory that creates the real panel on the event-dispatch thread.
     */
    private final transient Supplier<JComponent> factory;

    /**
     * Whether materialization has already been queued.
     */
    private boolean scheduled;

    /**
     * Whether the real panel has replaced the placeholder.
     */
    private boolean loaded;

    /**
     * Creates a lazy editor panel wrapper.
     *
     * @param name human-readable panel name
     * @param factory factory that creates the real panel
     */
    public LazyPanel(String name, Supplier<JComponent> factory) {
        super(new BorderLayout());
        this.name = name == null || name.isBlank() ? "Panel" : name;
        this.factory = factory;
        setOpaque(true);
        setBackground(Theme.BG);
        addPlaceholder();
    }

    /**
     * Queues materialization once the placeholder becomes displayable.
     */
    @Override
    public void addNotify() {
        super.addNotify();
        scheduleMaterialize();
    }

    /**
     * Returns whether the wrapped panel has already been created.
     *
     * @return true when the real panel is installed
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Adds a compact placeholder that can paint before heavy panel creation
     * starts on the next event-dispatch turn.
     */
    private void addPlaceholder() {
        removeAll();
        JLabel label = Ui.label("Loading " + name + "...");
        Theme.foreground(label, Theme.ForegroundRole.MUTED);
        JPanel center = Ui.transparentPanel(new java.awt.GridBagLayout());
        center.add(label);
        add(center, BorderLayout.CENTER);
    }

    /**
     * Schedules real panel creation after the current paint/layout turn.
     */
    private void scheduleMaterialize() {
        if (scheduled || loaded) {
            return;
        }
        scheduled = true;
        SwingUtilities.invokeLater(this::materialize);
    }

    /**
     * Creates and installs the real panel.
     */
    private void materialize() {
        if (loaded) {
            return;
        }
        JComponent panel = factory == null ? null : factory.get();
        removeAll();
        if (panel == null) {
            JLabel error = Ui.label(name + " unavailable");
            Theme.foreground(error, Theme.ForegroundRole.WARNING);
            JPanel center = Ui.transparentPanel(new java.awt.GridBagLayout());
            center.add(error);
            add(center, BorderLayout.CENTER);
        } else {
            Theme.refreshComponentTree(panel);
            add(panel, BorderLayout.CENTER);
        }
        loaded = true;
        revalidate();
        repaint();
    }
}
