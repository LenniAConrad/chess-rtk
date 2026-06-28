package application.gui.platform;

/**
 * GUI notification severity independent of a concrete toast implementation.
 */
public enum NotificationKind {
    /**
     * Informational message.
     */
    INFO,
    /**
     * Successful operation.
     */
    SUCCESS,
    /**
     * Warning that does not require immediate action.
     */
    WARNING,
    /**
     * Error surfaced non-blockingly.
     */
    ERROR
}
