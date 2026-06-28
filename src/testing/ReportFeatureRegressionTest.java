package testing;

import application.gui.feature.publishing.ReportController;
import application.gui.feature.publishing.ReportDependencies;
import application.gui.feature.publishing.ReportView;
import application.gui.platform.NotificationKind;
import application.gui.workbench.game.GameModel;
import application.gui.workbench.publish.ReportPanel;
import application.gui.workbench.ui.Toast;
import chess.core.Position;
import chess.core.Setup;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.DefaultListModel;
import javax.swing.JPanel;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertNotNull;
import static testing.TestSupport.assertSame;
import static testing.TestSupport.assertTrue;

/**
 * Regression checks for the report feature seam.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ReportFeatureRegressionTest {

    /**
     * Prevents instantiation.
     */
    private ReportFeatureRegressionTest() {
        // utility
    }

    /**
     * Runs the report feature regression checks.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        testLegacyHostDelegatesOnlyThroughReportDependencies();
        testControllerCreatesReportView();
        System.out.println("ReportFeatureRegressionTest: all checks passed");
    }

    /**
     * Verifies the legacy report host delegates to the narrow feature ports.
     */
    private static void testLegacyHostDelegatesOnlyThroughReportDependencies() {
        JPanel owner = new JPanel();
        Position position = new Position(Setup.getStandardStartFEN());
        short[] moves = { 1, 2, 3 };
        GameModel model = new GameModel();
        DefaultListModel<String> tags = new DefaultListModel<>();
        tags.addElement("fork");
        AtomicReference<String> copiedText = new AtomicReference<>();
        AtomicReference<String> consoleText = new AtomicReference<>();
        AtomicReference<NotificationKind> notificationKind = new AtomicReference<>();
        AtomicReference<String> notificationMessage = new AtomicReference<>();
        AtomicReference<String> dialogTitle = new AtomicReference<>();
        AtomicReference<String> dialogMessage = new AtomicReference<>();

        ReportPanel.Host host = new ReportController(new ReportDependencies(
                owner,
                () -> position,
                () -> moves,
                () -> model,
                () -> tags,
                copiedText::set,
                consoleText::set,
                (kind, message) -> {
                    notificationKind.set(kind);
                    notificationMessage.set(message);
                },
                (title, message) -> {
                    dialogTitle.set(title);
                    dialogMessage.set(message);
                })).legacyHost();

        assertSame(owner, host.owner(), "report host owner delegates");
        assertSame(position, host.currentPosition(), "report host position delegates");
        assertSame(moves, host.visibleMoves(), "report host visible moves delegate");
        assertSame(model, host.gameModel(), "report host game model delegates");
        assertSame(tags, host.tagModel(), "report host tag model delegates");

        host.copyText("report text");
        host.appendConsole("console line");
        host.toast(Toast.Kind.WARNING, "report warning");
        host.showError("Report error", "Failed");

        assertEquals("report text", copiedText.get(), "report host clipboard delegates");
        assertEquals("console line", consoleText.get(), "report host console delegates");
        assertEquals(NotificationKind.WARNING, notificationKind.get(), "report host notification severity maps");
        assertEquals("report warning", notificationMessage.get(), "report host notification message delegates");
        assertEquals("Report error", dialogTitle.get(), "report host dialog title delegates");
        assertEquals("Failed", dialogMessage.get(), "report host dialog message delegates");
    }

    /**
     * Verifies the controller constructs a concrete report view behind the
     * feature contract.
     */
    private static void testControllerCreatesReportView() {
        ReportView view = new ReportController(emptyDependencies()).createView();
        assertNotNull(view.component(), "report controller creates a component");
        assertTrue(view instanceof ReportPanel, "report controller keeps the current legacy panel behind the view");
    }

    /**
     * Creates dependency fakes for view construction.
     *
     * @return dependency bundle
     */
    private static ReportDependencies emptyDependencies() {
        JPanel owner = new JPanel();
        DefaultListModel<String> tags = new DefaultListModel<>();
        return new ReportDependencies(
                owner,
                () -> new Position(Setup.getStandardStartFEN()),
                () -> new short[0],
                GameModel::new,
                () -> tags,
                text -> {
                    // fake
                },
                text -> {
                    // fake
                },
                (kind, message) -> {
                    // fake
                },
                (title, message) -> {
                    // fake
                });
    }
}
