package application.gui.workbench.ui;

import javax.swing.JTextArea;

/**
 * Read-only monospace command/code preview.
 */
final class CommandBlock extends JTextArea {

    /**
     * Serialization identifier for Swing text component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a command/code preview.
     *
     * @param text preview text
     */
    public CommandBlock(String text) {
        super(text == null ? "" : text);
        setEditable(false);
        setLineWrap(true);
        setWrapStyleWord(false);
        Theme.codeBlock(this);
    }
}
