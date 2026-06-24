package application.gui.workbench.window;

import application.gui.workbench.audio.SoundService;
import application.gui.workbench.ui.Theme;

/**
 * Menu-controller factories for {@link WindowLifecycle}.
 */
final class WindowMenus {

    /**
     * Prevents instantiation.
     */
    private WindowMenus() {
        // utility
    }

    /**
     * Updates the tings menu.
     *
     * @param owner owning component or type
     * @return tings menu
     */
    static SettingsMenu settingsMenu(WindowLifecycle owner) {
        return new SettingsMenu(new SettingsMenu.Controller() {
            /**
             * Returns the active appearance mode.
             *
             * @return active appearance mode
             */
            @Override
            public Theme.Mode themeMode() {
                return Theme.mode();
            }

            /**
             * Applies the selected appearance mode.
             *
             * @param mode requested appearance mode
             */
            @Override
            public void setThemeMode(Theme.Mode mode) {
                owner.setDarkMode(mode == Theme.Mode.DARK);
            }

            /**
             * Returns the active UI density.
             *
             * @return active density
             */
            @Override
            public Theme.Density density() {
                return Theme.density();
            }

            /**
             * Applies the selected UI density.
             *
             * @param density requested density
             */
            @Override
            public void setDensity(Theme.Density density) {
                owner.setDensity(density);
            }

            /**
             * Returns whether sounds are enabled.
             *
             * @return true when sounds are enabled
             */
            @Override
            public boolean soundEnabled() {
                return !SoundService.isMuted();
            }

            /**
             * Applies the sound enabled flag.
             *
             * @param enabled true when sounds are enabled
             */
            @Override
            public void setSoundEnabled(boolean enabled) {
                SoundService.setMuted(!enabled);
                owner.settingsMenu.syncMode();
            }

            /**
             * Opens board and display settings.
             */
            @Override
            public void showDisplaySettings() {
                owner.showDisplaySettings();
            }

            /**
             * Opens engine settings.
             */
            @Override
            public void showEngineSettings() {
                owner.showEngineSettings();
            }

            /**
             * Opens sound settings.
             */
            @Override
            public void showSoundSettings() {
                owner.showDisplaySettings();
            }

            /**
             * Opens the command palette.
             */
            @Override
            public void showCommandPalette() {
                owner.showCommandPalette();
            }

            /**
             * Opens the persisted logs folder.
             */
            @Override
            public void openLogsDirectory() {
                owner.openLogsDockAndDirectory();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void openPgn() {
                owner.showPgnExplorer();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void savePgn() {
                owner.savePgnFile();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void copyFen() {
                owner.copyText(owner.currentFen());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void copyBuiltCommand() {
                owner.copyBuiltCommand();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void openDashboard() {
                owner.selectTab(WindowBase.TAB_DASHBOARD);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void openAnalyze() {
                owner.openBoard(WindowBase.BOARD_ANALYZE);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void openCommands() {
                owner.openRun(WindowBase.RUN_BUILD);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void openBatch() {
                owner.openRun(WindowBase.RUN_BUILD);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void openDatasets() {
                owner.selectTab(WindowBase.TAB_DATASETS);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void openPublish() {
                owner.selectTab(WindowBase.TAB_PUBLISH);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void openNetwork() {
                owner.openEngine(WindowBase.ENGINE_NETWORK);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void openMcts() {
                owner.openEngine(WindowBase.ENGINE_SEARCH);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void openPuzzles() {
                owner.openBoard(WindowBase.BOARD_SOLVE);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void newAnalyzeTab() {
                owner.openNewAnalyzeTab();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void showConsole() {
                owner.showConsoleDock();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void showLogs() {
                owner.showLogsDock();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void firstPosition() {
                owner.jumpActivePositionToStart();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void previousPosition() {
                owner.navigateActivePosition(-1);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void nextPosition() {
                owner.navigateActivePosition(1);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void lastPosition() {
                owner.jumpActivePositionToEnd();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void runBuiltCommand() {
                owner.runSelectedTemplate();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void runBestMove() {
                owner.runBestMove();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void runAnalyze() {
                owner.runAnalyze();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void runPerft() {
                owner.runPerft();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void runBatch() {
                owner.runSelectedTemplate();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void runPublishing() {
                owner.runPublishingCommand();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void runAllChecks() {
                owner.runAllHealthChecks();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void stopCommand() {
                owner.stopCommand();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void splitRight() {
                owner.tabs.splitSelectedTabRight();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void splitDown() {
                owner.tabs.splitSelectedTabDown();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void reopenAllTabs() {
                owner.tabs.reopenAllTabs();
            }
        });
    }

    /**
     * Lays out the window menus.
     *
     * @param owner owning component or type
     * @return layout menu
     */
    static LayoutMenu layoutMenu(WindowLifecycle owner) {
        return new LayoutMenu(new LayoutMenu.Controller() {
            /**
             * Returns whether the workbench status bar is visible.
             *
             * @return true when visible
             */
            @Override
            public boolean statusBarVisible() {
                return owner.isStatusBarVisible();
            }

            /**
             * Applies workbench status-bar visibility.
             *
             * @param visible true to show the status bar
             */
            @Override
            public void setStatusBarVisible(boolean visible) {
                owner.setStatusBarVisible(visible);
            }

            /**
             * Splits the selected tab to the right.
             */
            @Override
            public void splitRight() {
                owner.tabs.splitSelectedTabRight();
            }

            /**
             * Splits the selected tab below.
             */
            @Override
            public void splitDown() {
                owner.tabs.splitSelectedTabDown();
            }

            /**
             * Splits the selected tab to the left.
             */
            @Override
            public void splitLeft() {
                owner.tabs.splitSelectedTabLeft();
            }

            /**
             * Splits the selected tab above.
             */
            @Override
            public void splitUp() {
                owner.tabs.splitSelectedTabUp();
            }

            /**
             * Detaches the selected tab into a separate window.
             */
            @Override
            public void detachTab() {
                if (owner.tabs != null) {
                    owner.tabs.detachSelectedTab();
                }
            }

            /**
             * Reopens all tabs.
             */
            @Override
            public void reopenAllTabs() {
                owner.tabs.reopenAllTabs();
            }

            /**
             * Closes all tabs except the selected one.
             */
            @Override
            public void closeOtherTabs() {
                owner.tabs.closeOtherTabs();
            }

            /**
             * Returns the open tab count.
             *
             * @return open tab count
             */
            @Override
            public int openTabCount() {
                return owner.tabs == null ? 0 : owner.tabs.openTabCount();
            }

            /**
             * Returns the visible editor group count.
             *
             * @return visible editor group count
             */
            @Override
            public int visibleGroupCount() {
                return owner.tabs == null ? 0 : owner.tabs.visibleGroupCount();
            }
        });
    }
}
