package application.gui.platform;

/**
 * Shows non-blocking user notifications.
 */
@FunctionalInterface
public interface NotificationService {

    /**
     * Shows a notification.
     *
     * @param kind notification kind
     * @param message notification message
     */
    void notify(NotificationKind kind, String message);
}
