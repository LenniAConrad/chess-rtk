package application.gui.workbench.window;

import application.gui.workbench.command.CommandPalette.PaletteAction;
import application.gui.workbench.ui.Theme;
import java.util.Arrays;
import java.util.List;

/**
 * Command-palette action assembly for {@link WindowLifecycle}.
 */
final class WindowPaletteActions {

    /**
     * Prevents instantiation.
     */
    private WindowPaletteActions() {
        // utility
    }

    /**
     * Builds the command-palette actions for one workbench window.
     *
     * @param owner owning workbench window
     * @return palette action list
     */
    static List<PaletteAction> actions(WindowLifecycle owner) {
        return Arrays.asList(
                new PaletteAction("Engine", "Analyze board", "Run built-in search on the current FEN",
                        owner::runBuiltInSearch),
                new PaletteAction("Engine", "Best move", "Run engine bestmove with the current duration",
                        owner::runBestMove),
                new PaletteAction("Engine", "Engine analysis", "Run multipv analysis for the current position",
                        owner::runAnalyze),
                new PaletteAction("Engine", "Live external engine", "Toggle continuous UCI analysis for the board",
                        () -> owner.setLiveExternalEngineEnabled(!owner.liveExternalEngineEnabled)),
                new PaletteAction("View", "Analysis data", "Show live evaluation, depth, and speed graphs",
                        owner::showAnalysisData),
                new PaletteAction("View", "Opening tree", "Show ECO opening tree and candidate continuations",
                        () -> owner.showBoardDetail("ECO")),
                new PaletteAction("View", "Review game", "Run deterministic post-game review and retry tools",
                        owner::runCurrentGameReview),
                new PaletteAction("View", "Saved games", "Open saved Workbench games for resume or review",
                        () -> owner.showBoardDetail("Games")),
                new PaletteAction("View", "Author study", "Show study/repertoire TOML authoring",
                        () -> owner.showBoardDetail("Study")),
                new PaletteAction("View", "Endgame tablebase", "Show tablebase eligibility and endgame analysis",
                        () -> owner.showBoardDetail("Endgame")),
                new PaletteAction("Copy", "Copy analysis CSV", "Copy live analysis graph data as CSV",
                        owner::copyAnalysisCsv),
                new PaletteAction("Copy", "Copy analysis report", "Copy the live analysis summary report",
                        owner::copyAnalysisReport),
                new PaletteAction("Engine", "Print analysis report", "Print the live analysis graph report",
                        owner::printAnalysisReport),
                new PaletteAction("Engine", "Position tags", "Generate tags for the current FEN",
                        owner::runTagsCommand),
                new PaletteAction("Debug", "Perft", "Run perft with the selected depth and threads",
                        owner::runPerft),
                new PaletteAction("Run", "Run built command", "Execute the selected command template",
                        owner::runSelectedTemplate),
                new PaletteAction("Run", "PGN import", "Open the PGN import command template",
                        () -> owner.openCommandTemplate("PGN import")),
                new PaletteAction("Run", "PGN find", "Open the PGN store query template for the current FEN",
                        () -> owner.openCommandTemplate("PGN find")),
                new PaletteAction("Run", "PGN show", "Open the stored-game display command template",
                        () -> owner.openCommandTemplate("PGN show")),
                new PaletteAction("Run", "PGN stats", "Open the PGN store statistics command template",
                        () -> owner.openCommandTemplate("PGN stats")),
                new PaletteAction("Run", "Review game command", "Open the review-to-study command template",
                        () -> owner.openCommandTemplate("Review game")),
                new PaletteAction("Copy", "Copy built command", "Copy the command-builder preview",
                        owner::copyBuiltCommand),
                new PaletteAction("Edit", "Reset built command", "Restore default command-builder options",
                        owner::resetSelectedTemplate),
                new PaletteAction("View", "Filter command flags", "Focus the command-builder option filter",
                        owner::focusOptionFilter),
                new PaletteAction("Run", "Run publishing", "Execute the selected publishing workflow",
                        owner::runPublishingCommand),
                new PaletteAction("Run", "Generate report", "Refresh the report maker output",
                        owner::generateReport),
                new PaletteAction("Copy", "Copy report", "Copy the current report output", owner::copyReport),
                new PaletteAction("Copy", "Copy FEN", "Copy the current board FEN",
                        () -> owner.copyText(owner.fenField.getText())),
                new PaletteAction("View", "Toggle dark mode", "Switch the workbench appearance palette",
                        () -> owner.setDarkMode(!Theme.isDark())),
                new PaletteAction("Settings", "Board settings",
                        "Adjust board highlights, arrows, notation, animations, and eval",
                        owner::showDisplaySettings),
                new PaletteAction("Settings", "Engine settings",
                        "Adjust external engine protocol, nodes, and hash", owner::showEngineSettings),
                new PaletteAction("Engine", "Engine smoke test", "Validate that the configured UCI engine starts",
                        owner::runEngineSmoke),
                new PaletteAction("Debug", "Validate config", "Run config validation", owner::runConfigValidate),
                new PaletteAction("View", "Focus FEN", "Move focus to the position field", owner::focusFenField),
                new PaletteAction("Run", "Stop command", "Cancel the running child process", owner::stopCommand),
                new PaletteAction("View", "Open analyze tab", "Show board analysis tools",
                        () -> owner.openBoard(WindowBase.BOARD_ANALYZE)),
                new PaletteAction("View", "Open play", "Play a game versus the engine on the board",
                        () -> owner.openBoard(WindowBase.BOARD_PLAY)),
                new PaletteAction("View", "Open relations",
                        "Overlay OTIS tactical-incidence channels on the board",
                        () -> owner.openBoard(WindowBase.BOARD_RELATIONS)),
                new PaletteAction("View", "Open draw", "Annotate and export the current board",
                        () -> owner.openBoard(WindowBase.BOARD_DRAW)),
                new PaletteAction("View", "New analyze tab", "Open another independent analysis workspace",
                        owner::openNewAnalyzeTab),
                new PaletteAction("View", "Focus game line", "Show the merged game tools", owner::focusGameInput),
                new PaletteAction("File", "PGN database", "Search, filter, dedupe, and report on PGN games",
                        owner::showPgnExplorer),
                new PaletteAction("File", "Player prep report", "Open PGN database and use Copy Prep Report",
                        owner::showPgnExplorer),
                new PaletteAction("View", "Open commands tab", "Show the command builder",
                        () -> owner.openRun(WindowBase.RUN_BUILD)),
                new PaletteAction("View", "Open datasets tab", "Inspect and analyze training datasets",
                        () -> owner.selectTab(WindowBase.TAB_DATASETS)),
                new PaletteAction("Dataset", "Analyze dataset", "Scan the selected dataset source",
                        () -> owner.datasetPanel().analyzeCurrentSource()),
                new PaletteAction("View", "Open publish tab", "Show report and publishing tools",
                        () -> owner.selectTab(WindowBase.TAB_PUBLISH)),
                new PaletteAction("View", "Open describe tab", "Show deterministic position text",
                        () -> owner.selectTab(WindowBase.TAB_DESCRIBE)),
                new PaletteAction("View", "Open evaluator",
                        "Inspect the evaluator (neural or classical) over the current position",
                        () -> owner.openEngine(WindowBase.ENGINE_NETWORK)),
                new PaletteAction("View", "Open MCTS search", "Inspect the shared PUCT/MCTS search tree",
                        () -> owner.openEngine(WindowBase.ENGINE_SEARCH)),
                new PaletteAction("View", "Open engine gauntlet", "Run deterministic built-in engine self-play",
                        () -> owner.openEngine(WindowBase.ENGINE_GAUNTLET)),
                new PaletteAction("View", "Show Console", "Open the command-output tab",
                        owner::showConsoleDock),
                new PaletteAction("View", "Show Logs", "Open and refresh the log browser tab",
                        owner::showLogsDock),
                new PaletteAction("View", "Open puzzles tab", "Train PGN tactics with variation branches",
                        () -> owner.openBoard(WindowBase.BOARD_SOLVE)),
                new PaletteAction("Workbench", "Split tab right",
                        "Move the active workbench tab into a right editor group",
                        owner.tabs::splitSelectedTabRight),
                new PaletteAction("Workbench", "Split tab down",
                        "Move the active workbench tab into a lower editor group",
                        owner.tabs::splitSelectedTabDown),
                new PaletteAction("Workbench", "Close active tab", "Hide the active workbench tab",
                        owner.tabs::closeSelectedTab),
                new PaletteAction("Workbench", "Reopen all tabs", "Restore every hidden workbench tab",
                        owner.tabs::reopenAllTabs),
                new PaletteAction("File", "Open logs folder", "Show persisted workbench command logs",
                        owner::openLogsDockAndDirectory));
    }
}
