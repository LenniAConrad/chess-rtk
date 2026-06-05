package application.gui.workbench.ui;

import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Shared document-change listener helpers for Workbench text controls.
 */
final class DocumentChangeSupport {

    /**
     * Prevents instantiation.
     */
    private DocumentChangeSupport() {
        // utility
    }

    /**
     * Creates a document listener from a runnable.
     *
     * @param runnable runnable
     * @return listener
     */
    static DocumentListener changeListener(Runnable runnable) {
        return new DocumentListener() {
            /**
             * Runs the callback when text is inserted.
             *
             * @param event document event
             */
            @Override
            public void insertUpdate(DocumentEvent event) {
                runnable.run();
            }

            /**
             * Runs the callback when text is removed.
             *
             * @param event document event
             */
            @Override
            public void removeUpdate(DocumentEvent event) {
                runnable.run();
            }

            /**
             * Runs the callback when document attributes change.
             *
             * @param event document event
             */
            @Override
            public void changedUpdate(DocumentEvent event) {
                runnable.run();
            }
        };
    }

    /**
     * Adds a shared document-change callback to multiple text fields.
     *
     * @param runnable callback
     * @param fields text fields
     */
    static void onTextChange(Runnable runnable, JTextField... fields) {
        for (JTextField field : fields) {
            field.getDocument().addDocumentListener(changeListener(runnable));
        }
    }
}
