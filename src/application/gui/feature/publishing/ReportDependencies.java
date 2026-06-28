package application.gui.feature.publishing;

import application.gui.platform.ClipboardService;
import application.gui.platform.DialogService;
import application.gui.platform.NotificationService;
import application.gui.workbench.game.GameModel;
import chess.core.Position;
import java.awt.Component;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.DefaultListModel;

/**
 * Narrow services needed by the report view inside the Publishing feature.
 *
 * @param owner dialog owner component
 * @param currentPosition current board position supplier
 * @param visibleMoves visible legal move supplier
 * @param gameModel current game model supplier
 * @param tagModel current tag model supplier
 * @param clipboard clipboard service
 * @param console console append service
 * @param notifications notification service
 * @param dialogs dialog service
 */
public record ReportDependencies(
        Component owner,
        Supplier<Position> currentPosition,
        Supplier<short[]> visibleMoves,
        Supplier<GameModel> gameModel,
        Supplier<DefaultListModel<String>> tagModel,
        ClipboardService clipboard,
        ConsoleSink console,
        NotificationService notifications,
        DialogService dialogs) {

    /**
     * Creates a dependency bundle.
     */
    public ReportDependencies {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currentPosition, "currentPosition");
        Objects.requireNonNull(visibleMoves, "visibleMoves");
        Objects.requireNonNull(gameModel, "gameModel");
        Objects.requireNonNull(tagModel, "tagModel");
        Objects.requireNonNull(clipboard, "clipboard");
        Objects.requireNonNull(console, "console");
        Objects.requireNonNull(notifications, "notifications");
        Objects.requireNonNull(dialogs, "dialogs");
    }

    /**
     * Appends text to the Workbench console.
     */
    @FunctionalInterface
    public interface ConsoleSink {

        /**
         * Appends text.
         *
         * @param text text to append
         */
        void appendConsole(String text);
    }
}
