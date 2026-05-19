package application.gui.workbench;

/**
 * Immutable snapshot of the workbench's environment-health checks: CLI config
 * validation, the {@code doctor} self-test, and the external-engine UCI smoke
 * test.
 *
 * <p>Each check is one {@link Check} value. The snapshot is immutable; the
 * {@code with*} methods return a new instance so {@link WorkbenchSession} can
 * publish health updates atomically.</p>
 *
 * @param config result of {@code config validate}
 * @param doctor result of {@code doctor}
 * @param engineSmoke result of {@code engine uci-smoke}
 */
record WorkbenchHealthSnapshot(Check config, Check doctor, Check engineSmoke) {

    /**
     * Outcome of a single health check.
     */
    enum Check {

        /**
         * The check has not been run this session.
         */
        UNKNOWN("not run"),

        /**
         * The check is currently running.
         */
        RUNNING("running…"),

        /**
         * The check passed.
         */
        OK("ok"),

        /**
         * The check failed.
         */
        FAILED("failed");

        /**
         * Human-readable label.
         */
        private final String label;

        Check(String label) {
            this.label = label;
        }

        /**
         * Returns the display label.
         *
         * @return label
         */
        String label() {
            return label;
        }

        /**
         * Maps a finished command exit code to a check result.
         *
         * @param exitCode process exit code
         * @return {@link #OK} for zero, {@link #FAILED} otherwise
         */
        static Check ofExitCode(int exitCode) {
            return exitCode == 0 ? OK : FAILED;
        }
    }

    /**
     * Returns a snapshot with every check unknown.
     *
     * @return all-unknown snapshot
     */
    static WorkbenchHealthSnapshot unknown() {
        return new WorkbenchHealthSnapshot(Check.UNKNOWN, Check.UNKNOWN, Check.UNKNOWN);
    }

    /**
     * Returns a copy with the config-validation check replaced.
     *
     * @param value new config check result
     * @return updated snapshot
     */
    WorkbenchHealthSnapshot withConfig(Check value) {
        return new WorkbenchHealthSnapshot(value, doctor, engineSmoke);
    }

    /**
     * Returns a copy with the doctor check replaced.
     *
     * @param value new doctor check result
     * @return updated snapshot
     */
    WorkbenchHealthSnapshot withDoctor(Check value) {
        return new WorkbenchHealthSnapshot(config, value, engineSmoke);
    }

    /**
     * Returns a copy with the engine-smoke check replaced.
     *
     * @param value new engine-smoke check result
     * @return updated snapshot
     */
    WorkbenchHealthSnapshot withEngineSmoke(Check value) {
        return new WorkbenchHealthSnapshot(config, doctor, value);
    }
}
