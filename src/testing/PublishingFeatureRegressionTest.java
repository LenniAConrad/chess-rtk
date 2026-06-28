package testing;

import application.gui.feature.publishing.PublishingController;
import application.gui.feature.publishing.PublishingDependencies;
import application.gui.feature.publishing.PublishingView;
import application.gui.feature.publishing.ReportView;
import application.gui.platform.NotificationKind;
import application.gui.workbench.game.GameModel;
import application.gui.workbench.publish.PublishingPanel;
import application.gui.workbench.ui.Toast;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertNotNull;
import static testing.TestSupport.assertSame;
import static testing.TestSupport.assertTrue;

/**
 * Regression checks for the Publishing feature seam.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PublishingFeatureRegressionTest {

    /**
     * Prevents instantiation.
     */
    private PublishingFeatureRegressionTest() {
        // utility
    }

    /**
     * Runs the Publishing feature regression checks.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        testLegacyHostDelegatesOnlyThroughFeatureDependencies();
        testControllerCreatesPublishingView();
        System.out.println("PublishingFeatureRegressionTest: all checks passed");
    }

    /**
     * Verifies the legacy host adapter delegates to the narrow feature ports.
     */
    private static void testLegacyHostDelegatesOnlyThroughFeatureDependencies() {
        JPanel owner = new JPanel();
        JPanel reportComponent = new JPanel();
        JButton stopButton = new JButton("stop");
        GameModel model = new GameModel();
        AtomicReference<List<String>> commandArgs = new AtomicReference<>();
        AtomicReference<String> commandStdin = new AtomicReference<>();
        AtomicReference<String> copiedText = new AtomicReference<>();
        AtomicReference<NotificationKind> notificationKind = new AtomicReference<>();
        AtomicReference<String> notificationMessage = new AtomicReference<>();
        AtomicReference<String> dialogTitle = new AtomicReference<>();
        AtomicReference<String> dialogMessage = new AtomicReference<>();
        AtomicInteger stopRequests = new AtomicInteger();
        FakeReportView report = new FakeReportView(reportComponent);

        PublishingPanel.Host host = new PublishingController(new PublishingDependencies(
                owner,
                () -> "8/8/8/8/8/8/8/8 w - - 0 1",
                () -> model,
                () -> "fen one\nfen two",
                report,
                (args, stdin) -> {
                    commandArgs.set(List.copyOf(args));
                    commandStdin.set(stdin);
                },
                copiedText::set,
                new PublishingDependencies.CommandControl() {
                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void stopCommand() {
                        stopRequests.incrementAndGet();
                    }

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public JComponent stopButton() {
                        return stopButton;
                    }
                },
                (kind, message) -> {
                    notificationKind.set(kind);
                    notificationMessage.set(message);
                },
                (title, message) -> {
                    dialogTitle.set(title);
                    dialogMessage.set(message);
                })).legacyHost();

        assertSame(owner, host.owner(), "publishing host owner delegates");
        assertEquals("8/8/8/8/8/8/8/8 w - - 0 1", host.currentFen(), "publishing host current FEN delegates");
        assertSame(model, host.gameModel(), "publishing host game model delegates");
        assertEquals("fen one\nfen two", host.batchInputText(), "publishing host batch input delegates");
        assertSame(reportComponent, host.reportPanel(), "publishing host report component delegates");

        host.generateReport();
        host.runCommand(List.of("engine", "bestmove"), "stdin text");
        host.copyText("copy payload");
        host.stopCommand();
        host.toast(Toast.Kind.ERROR, "publish failed");
        host.showError("Dialog title", "Dialog message");

        assertEquals(1, report.generations(), "publishing host report generation delegates");
        assertEquals(List.of("engine", "bestmove"), commandArgs.get(), "publishing host command args delegate");
        assertEquals("stdin text", commandStdin.get(), "publishing host command stdin delegates");
        assertEquals("copy payload", copiedText.get(), "publishing host clipboard delegates");
        assertEquals(1, stopRequests.get(), "publishing host stop delegates");
        assertSame(stopButton, host.commandStopButton(), "publishing host stop button delegates");
        assertEquals(NotificationKind.ERROR, notificationKind.get(), "publishing host notification severity maps");
        assertEquals("publish failed", notificationMessage.get(), "publishing host notification message delegates");
        assertEquals("Dialog title", dialogTitle.get(), "publishing host dialog title delegates");
        assertEquals("Dialog message", dialogMessage.get(), "publishing host dialog message delegates");
    }

    /**
     * Verifies the controller constructs a concrete view behind the feature
     * contract.
     */
    private static void testControllerCreatesPublishingView() {
        PublishingView view = new PublishingController(emptyDependencies()).createView();
        assertNotNull(view.component(), "publishing controller creates a component");
        assertTrue(view instanceof PublishingPanel, "publishing controller keeps the current legacy panel behind the view");
    }

    /**
     * Creates dependency fakes for view construction.
     *
     * @return dependency bundle
     */
    private static PublishingDependencies emptyDependencies() {
        JPanel owner = new JPanel();
        JPanel reportComponent = new JPanel();
        return new PublishingDependencies(
                owner,
                () -> "8/8/8/8/8/8/8/8 w - - 0 1",
                GameModel::new,
                () -> "",
                new FakeReportView(reportComponent),
                (args, stdin) -> {
                    // fake
                },
                text -> {
                    // fake
                },
                new PublishingDependencies.CommandControl() {
                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void stopCommand() {
                        // fake
                    }

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public JComponent stopButton() {
                        return new JButton("stop");
                    }
                },
                (kind, message) -> {
                    // fake
                },
                (title, message) -> {
                    // fake
                });
    }

    /**
     * Fake report view used by Publishing dependency tests.
     */
    private static final class FakeReportView implements ReportView {

        /**
         * Root component.
         */
        private final JComponent component;

        /**
         * Generation count.
         */
        private int generations;

        /**
         * Creates a fake report view.
         *
         * @param component root component
         */
        private FakeReportView(JComponent component) {
            this.component = component;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public JComponent component() {
            return component;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void generateReport() {
            generations++;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void copyReport() {
            // fake
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void saveReportFile() {
            // fake
        }

        /**
         * Returns the number of report generations.
         *
         * @return generation count
         */
        private int generations() {
            return generations;
        }
    }
}
