package application.gui.feature.publishing;

import application.gui.platform.NotificationKind;
import application.gui.workbench.game.GameModel;
import application.gui.workbench.publish.ReportPanel;
import application.gui.workbench.ui.Toast;
import chess.core.Position;
import java.awt.Component;
import java.util.Objects;
import javax.swing.DefaultListModel;

/**
 * Compatibility controller that adapts report feature dependencies to the
 * existing Swing panel host contract.
 */
public final class ReportController {

    /**
     * Feature dependencies.
     */
    private final ReportDependencies dependencies;

    /**
     * Creates a controller.
     *
     * @param dependencies feature dependencies
     */
    public ReportController(ReportDependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    /**
     * Creates the current legacy Swing view through the report seam.
     *
     * @return report view
     */
    public ReportView createView() {
        return new ReportPanel(legacyHost());
    }

    /**
     * Creates a legacy host adapter for the existing panel implementation.
     *
     * @return legacy report host
     */
    public ReportPanel.Host legacyHost() {
        return new LegacyHost(dependencies);
    }

    /**
     * Adapts narrow feature dependencies to the legacy report host API.
     */
    private static final class LegacyHost implements ReportPanel.Host {

        /**
         * Feature dependencies.
         */
        private final ReportDependencies dependencies;

        /**
         * Creates a legacy host.
         *
         * @param dependencies feature dependencies
         */
        private LegacyHost(ReportDependencies dependencies) {
            this.dependencies = dependencies;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Component owner() {
            return dependencies.owner();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Position currentPosition() {
            return dependencies.currentPosition().get();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public short[] visibleMoves() {
            return dependencies.visibleMoves().get();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public GameModel gameModel() {
            return dependencies.gameModel().get();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DefaultListModel<String> tagModel() {
            return dependencies.tagModel().get();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void copyText(String text) {
            dependencies.clipboard().copyText(text);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void appendConsole(String text) {
            dependencies.console().appendConsole(text);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void toast(Toast.Kind kind, String message) {
            dependencies.notifications().notify(notificationKind(kind), message);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void showError(String title, String message) {
            dependencies.dialogs().showError(title, message);
        }

        /**
         * Maps legacy toast severities into platform notification severities.
         *
         * @param kind legacy toast severity
         * @return platform notification severity
         */
        private static NotificationKind notificationKind(Toast.Kind kind) {
            return switch (kind) {
                case SUCCESS -> NotificationKind.SUCCESS;
                case WARNING -> NotificationKind.WARNING;
                case ERROR -> NotificationKind.ERROR;
                case INFO -> NotificationKind.INFO;
            };
        }
    }
}
