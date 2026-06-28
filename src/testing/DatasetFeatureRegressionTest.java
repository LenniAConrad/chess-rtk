package testing;

import application.gui.feature.dataset.DatasetController;
import application.gui.feature.dataset.DatasetDependencies;
import application.gui.feature.dataset.DatasetView;
import application.gui.workbench.dataset.DatasetPanel;
import java.util.concurrent.atomic.AtomicReference;

import static testing.TestSupport.assertNotNull;
import static testing.TestSupport.assertTrue;

/**
 * Regression checks for the Dataset feature seam.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class DatasetFeatureRegressionTest {

    /**
     * Prevents instantiation.
     */
    private DatasetFeatureRegressionTest() {
        // utility
    }

    /**
     * Runs the Dataset feature regression checks.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        testControllerCreatesDatasetView();
        testDependenciesRequireNavigationPorts();
        System.out.println("DatasetFeatureRegressionTest: all checks passed");
    }

    /**
     * Verifies the controller constructs a concrete view behind the feature
     * contract.
     */
    private static void testControllerCreatesDatasetView() {
        AtomicReference<String> sharedBoardFen = new AtomicReference<>();
        AtomicReference<String> detachedBoardFen = new AtomicReference<>();
        DatasetView view = new DatasetController(new DatasetDependencies(
                sharedBoardFen::set,
                detachedBoardFen::set)).createView();

        assertNotNull(view.component(), "dataset controller creates a component");
        assertTrue(view instanceof DatasetPanel, "dataset controller keeps the current legacy panel behind the view");
        view.analyzeCurrentSource();
    }

    /**
     * Verifies the dependency bundle does not accept missing navigation ports.
     */
    private static void testDependenciesRequireNavigationPorts() {
        assertNullRejected(() -> new DatasetDependencies(null, fen -> {
            // fake
        }), "dataset dependencies require shared-board navigation");
        assertNullRejected(() -> new DatasetDependencies(fen -> {
            // fake
        }, null), "dataset dependencies require detached-board navigation");
    }

    /**
     * Verifies a null dependency throws.
     *
     * @param action action expected to throw
     * @param label assertion label
     */
    private static void assertNullRejected(Runnable action, String label) {
        try {
            action.run();
        } catch (NullPointerException expected) {
            return;
        }
        throw new AssertionError(label + ": expected NullPointerException");
    }
}
