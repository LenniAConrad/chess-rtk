package application.gui.workbench.window;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.network.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.session.*;
import application.gui.workbench.ui.*;

/**
 * Routes dashboard quick actions to the owning workbench window.
 */
public final class WindowDashboardActions extends WindowHost implements DashboardActions {

    /**
     * Creates dashboard actions.
     *
     * @param window owning workbench window
     */
    public WindowDashboardActions(WindowBase window) {
        super(window);
    }

    @Override
    public void builtInSearch() {
        window.runBuiltInSearch();
    }

    @Override
    public void bestMove() {
        window.runBestMove();
    }

    @Override
    public void analyze() {
        window.runAnalyze();
    }

    @Override
    public void tags() {
        window.runTagsCommand();
    }

    @Override
    public void perft() {
        window.runPerft();
    }

    @Override
    public void runBatch() {
        window.batchPanel.runBatch();
    }

    @Override
    public void engineSmoke() {
        window.runEngineSmoke();
    }

    @Override
    public void configValidate() {
        window.runConfigValidate();
    }

    @Override
    public void doctor() {
        window.runDoctor();
    }

    @Override
    public void runAllHealthChecks() {
        window.runAllHealthChecks();
    }

    @Override
    public void copyCurrentFen() {
        window.copyText(window.currentFen());
    }

    @Override
    public void openAnalyzeTab() {
        window.selectTab(WindowBase.TAB_ANALYZE);
    }

    @Override
    public void openBatchTab() {
        window.selectTab(WindowBase.TAB_BATCH);
    }

    @Override
    public void openConsoleTab() {
        window.selectTab(WindowBase.TAB_CONSOLE);
    }

    @Override
    public void retryJob(Job job) {
        if (job != null) {
            window.runCommand(job.args(), null);
        }
    }

    @Override
    public void copyJobCommand(Job job) {
        if (job != null) {
            window.copyText(CommandRunner.displayCommand(job.args()));
        }
    }

    @Override
    public void openJobManifest(Job job) {
        if (job != null) {
            window.runArtifacts.openManifest(job);
        }
    }

    @Override
    public void openJobLog(Job job) {
        if (job != null) {
            window.runArtifacts.openLog(job);
        }
    }
}
