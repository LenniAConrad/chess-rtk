package application.gui.feature.publishing;

import application.gui.platform.ClipboardService;
import application.gui.platform.DialogService;
import application.gui.platform.NotificationService;
import application.gui.workbench.game.GameModel;
import java.awt.Component;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.JComponent;

/**
 * Narrow services needed by the Publishing feature.
 *
 * <p>This record is the first compatibility seam between the Workbench shell
 * and the legacy {@code PublishingPanel}. New Publishing code should depend on
 * this record or smaller ports instead of receiving {@code WindowBase} or a
 * {@code WindowHost}.</p>
 *
 * @param owner dialog owner component
 * @param currentFen current board FEN supplier
 * @param gameModel current game model supplier
 * @param batchInputText batch input supplier
 * @param report report view
 * @param commandRunner command runner service
 * @param clipboard clipboard service
 * @param commandControl foreground command control service
 * @param notifications notification service
 * @param dialogs dialog service
 */
public record PublishingDependencies(
        Component owner,
        Supplier<String> currentFen,
        Supplier<GameModel> gameModel,
        Supplier<String> batchInputText,
        ReportView report,
        CommandRunner commandRunner,
        ClipboardService clipboard,
        CommandControl commandControl,
        NotificationService notifications,
        DialogService dialogs) {

    /**
     * Creates a dependency bundle.
     */
    public PublishingDependencies {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currentFen, "currentFen");
        Objects.requireNonNull(gameModel, "gameModel");
        Objects.requireNonNull(batchInputText, "batchInputText");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(commandRunner, "commandRunner");
        Objects.requireNonNull(clipboard, "clipboard");
        Objects.requireNonNull(commandControl, "commandControl");
        Objects.requireNonNull(notifications, "notifications");
        Objects.requireNonNull(dialogs, "dialogs");
    }

    /**
     * Runs a CLI command.
     */
    @FunctionalInterface
    public interface CommandRunner {

        /**
         * Runs a command with optional standard input.
         *
         * @param args command arguments
         * @param stdin standard input text
         */
        void runCommand(List<String> args, String stdin);
    }

    /**
     * Controls the foreground command.
     */
    public interface CommandControl {

        /**
         * Stops the foreground command.
         */
        void stopCommand();

        /**
         * Creates a stop button bound to the foreground command lifecycle.
         *
         * @return stop button component
         */
        JComponent stopButton();
    }

}
