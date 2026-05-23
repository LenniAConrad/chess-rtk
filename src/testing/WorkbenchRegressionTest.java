/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package testing;

/**
 * Headless regression checks for workbench support classes.
 */
@SuppressWarnings("java:S2187")
public final class WorkbenchRegressionTest {

    /**
     * Prevents instantiation.
     */
    private WorkbenchRegressionTest() {
        // utility
    }

    /**
     * Runs all workbench regression checks.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        WorkbenchCommandRegression.run();
        WorkbenchUiRegression.run();
        WorkbenchGameRegression.run();
        WorkbenchBoardRegression.run();
        WorkbenchBackendRegression.run();
        WorkbenchDatasetRegression.run();
        System.out.println("WorkbenchRegressionTest: all checks passed");
    }
}
