package application.gui.workbench.ui;

/**
 * Named segmented-control primitive. It uses the existing segmented switcher
 * implementation so current Workbench screens and later prompts share one
 * component path.
 */
public final class SegmentedControl extends SegmentedSwitcher {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a segmented control.
     *
     * @param labels segment labels
     */
    public SegmentedControl(String[] labels) {
        super(labels);
    }
}
