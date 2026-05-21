package application.gui.workbench.session;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.network.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.ui.*;
import application.gui.workbench.window.*;

/**
 * Immutable snapshot of the workbench's environment-health checks: CLI config
 * validation, the {@code doctor} self-test, and the external-engine UCI smoke
 * test.
 *
 * <p>Each check is one {@link Check} value. The snapshot is immutable; the
 * {@code with*} methods return a new instance so {@link Session} can
 * publish health updates atomically.</p>
 *
 * @param config result of {@code config validate}
 * @param doctor result of {@code doctor}
 * @param engineSmoke result of {@code engine uci-smoke}
 */
public record HealthSnapshot(Check config, Check doctor, Check engineSmoke) {

    /**
     * Outcome of a single health check.
     */
    public enum Check {

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

        /**
         * Creates a health-check outcome.
         *
         * @param label display label
         */
        Check(String label) {
            this.label = label;
        }

        /**
         * Returns the display label.
         *
         * @return label
         */
        public String label() {
            return label;
        }

        /**
         * Maps a finished command exit code to a check result.
         *
         * @param exitCode process exit code
         * @return {@link #OK} for zero, {@link #FAILED} otherwise
         */
        public static Check ofExitCode(int exitCode) {
            return exitCode == 0 ? OK : FAILED;
        }
    }

    /**
     * Returns a snapshot with every check unknown.
     *
     * @return all-unknown snapshot
     */
    public static HealthSnapshot unknown() {
    return new HealthSnapshot(Check.UNKNOWN, Check.UNKNOWN, Check.UNKNOWN);
    }

    /**
     * Returns a copy with the config-validation check replaced.
     *
     * @param value new config check result
     * @return updated snapshot
     */
    public HealthSnapshot withConfig(Check value) {
    return new HealthSnapshot(value, doctor, engineSmoke);
    }

    /**
     * Returns a copy with the doctor check replaced.
     *
     * @param value new doctor check result
     * @return updated snapshot
     */
    public HealthSnapshot withDoctor(Check value) {
    return new HealthSnapshot(config, value, engineSmoke);
    }

    /**
     * Returns a copy with the engine-smoke check replaced.
     *
     * @param value new engine-smoke check result
     * @return updated snapshot
     */
    public HealthSnapshot withEngineSmoke(Check value) {
    return new HealthSnapshot(config, doctor, value);
    }
}
