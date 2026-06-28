package application.gui.feature.dataset;

import javax.swing.JComponent;

/**
 * Swing-facing Dataset view contract used by the Workbench shell.
 */
public interface DatasetView {

    /**
     * Returns the root component.
     *
     * @return root component
     */
    JComponent component();

    /**
     * Starts analysis for the currently selected dataset source.
     */
    void analyzeCurrentSource();
}
