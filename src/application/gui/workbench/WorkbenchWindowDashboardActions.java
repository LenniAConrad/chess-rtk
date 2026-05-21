package application.gui.workbench;

/**
 * Routes dashboard quick actions to the owning workbench window.
 */
final class WorkbenchWindowDashboardActions extends WorkbenchWindowHost implements WorkbenchDashboardActions {

    /**
     * Creates dashboard actions.
     *
     * @param window owning workbench window
     */
    WorkbenchWindowDashboardActions(WorkbenchWindowBase window) {
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
        window.selectTab(WorkbenchWindowBase.TAB_ANALYZE);
    }

    @Override
    public void openBatchTab() {
        window.selectTab(WorkbenchWindowBase.TAB_BATCH);
    }

    @Override
    public void openConsoleTab() {
        window.selectTab(WorkbenchWindowBase.TAB_CONSOLE);
    }

    @Override
    public void retryJob(WorkbenchJob job) {
        if (job != null) {
            window.runCommand(job.args(), null);
        }
    }

    @Override
    public void copyJobCommand(WorkbenchJob job) {
        if (job != null) {
            window.copyText(WorkbenchCommandRunner.displayCommand(job.args()));
        }
    }

    @Override
    public void openJobManifest(WorkbenchJob job) {
        if (job != null) {
            window.runArtifacts.openManifest(job);
        }
    }

    @Override
    public void openJobLog(WorkbenchJob job) {
        if (job != null) {
            window.runArtifacts.openLog(job);
        }
    }
}
