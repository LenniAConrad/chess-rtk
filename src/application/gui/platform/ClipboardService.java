package application.gui.platform;

/**
 * Copies text to the host platform clipboard.
 */
@FunctionalInterface
public interface ClipboardService {

    /**
     * Copies text.
     *
     * @param text text to copy
     */
    void copyText(String text);
}
