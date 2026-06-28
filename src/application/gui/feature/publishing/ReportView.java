package application.gui.feature.publishing;

import javax.swing.JComponent;

/**
 * Swing-facing report view contract used by Publishing and shell actions.
 */
public interface ReportView {

    /**
     * Returns the root component.
     *
     * @return root component
     */
    JComponent component();

    /**
     * Generates a report for the current position and game line.
     */
    void generateReport();

    /**
     * Copies the current report, generating it first when needed.
     */
    void copyReport();

    /**
     * Saves the current report to a text file.
     */
    void saveReportFile();
}
