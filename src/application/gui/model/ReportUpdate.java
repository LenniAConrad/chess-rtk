package application.gui.model;

/**
 * Progress payload for report analysis worker.
 *
 * Carries completed and total move counts so the UI can display progress or completion statistics while report analysis runs.
 *
 * @param completed how many entries have been processed.
 * @param total total entries expected for the report.
  * @since 2026
  * @author Lennart A. Conrad
 */
public record ReportUpdate(
	/**
	 * Stores the completed.
	 */
	int completed,
	/**
	 * Stores the total.
	 */
	int total
) {
}
