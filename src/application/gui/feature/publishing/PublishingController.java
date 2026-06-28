package application.gui.feature.publishing;

import application.gui.platform.NotificationKind;
import application.gui.workbench.publish.PublishingPanel;
import application.gui.workbench.ui.Toast;
import java.awt.Component;
import java.util.List;
import java.util.Objects;
import javax.swing.JComponent;

/**
 * Compatibility controller that adapts Publishing feature dependencies to the
 * existing Swing panel host contract.
 */
public final class PublishingController {

    /**
     * Feature dependencies.
     */
    private final PublishingDependencies dependencies;

    /**
     * Creates a controller.
     *
     * @param dependencies feature dependencies
     */
    public PublishingController(PublishingDependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    /**
     * Creates the current legacy Swing view through the feature seam.
     *
     * @return publishing view
     */
    public PublishingView createView() {
        return new PublishingPanel(legacyHost());
    }

    /**
     * Creates a legacy host adapter for the existing panel implementation.
     *
     * @return legacy panel host
     */
    public PublishingPanel.Host legacyHost() {
        return new LegacyHost(dependencies);
    }

    /**
     * Adapts narrow feature dependencies to the legacy panel host API.
     */
    private static final class LegacyHost implements PublishingPanel.Host {

        /**
         * Feature dependencies.
         */
        private final PublishingDependencies dependencies;

        /**
         * Creates a legacy host.
         *
         * @param dependencies feature dependencies
         */
        private LegacyHost(PublishingDependencies dependencies) {
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
        public String currentFen() {
            return dependencies.currentFen().get();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public application.gui.workbench.game.GameModel gameModel() {
            return dependencies.gameModel().get();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String batchInputText() {
            return dependencies.batchInputText().get();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public JComponent reportPanel() {
            return dependencies.report().component();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void generateReport() {
            dependencies.report().generateReport();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void runCommand(List<String> args, String stdin) {
            dependencies.commandRunner().runCommand(args, stdin);
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
        public void stopCommand() {
            dependencies.commandControl().stopCommand();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public JComponent commandStopButton() {
            return dependencies.commandControl().stopButton();
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
