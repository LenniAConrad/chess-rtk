package application.gui.feature.publishing;

import javax.swing.JComponent;

/**
 * Swing-facing Publishing view contract used by the Workbench shell.
 */
public interface PublishingView {

    /**
     * Returns the root component.
     *
     * @return root component
     */
    JComponent component();

    /**
     * Refreshes the command preview immediately.
     */
    void updateCommand();

    /**
     * Queues a command preview refresh.
     */
    void requestCommandUpdate();

    /**
     * Runs the current publishing workflow.
     */
    void runCommand();
}
