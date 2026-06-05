package application.gui.workbench.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/**
 * Applies Workbench theme, sizing, and font polish to Swing file choosers.
 */
final class FileChooserStyler {

    /**
     * Default chooser size large enough to avoid cramped row wrapping.
     */
    private static final Dimension PREFERRED_SIZE = new Dimension(760, 520);

    /**
     * Minimum chooser size that keeps toolbar, file list, and form rows usable.
     */
    private static final Dimension MINIMUM_SIZE = new Dimension(620, 420);

    /**
     * Client-property key marking file choosers with a window sizing listener.
     */
    private static final String SIZE_LISTENER_PROPERTY =
            FileChooserStyler.class.getName() + ".sizeListener";

    /**
     * Prevents instantiation.
     */
    private FileChooserStyler() {
        // utility
    }

    /**
     * Styles a file chooser before it is shown.
     *
     * @param chooser file chooser
     */
    static void style(JFileChooser chooser) {
        FileChooserIcons.installDefaults();
        chooser.setBackground(Theme.BG);
        chooser.setForeground(Theme.TEXT);
        chooser.setBorder(Theme.pad(Theme.SPACE_MD));
        chooser.setPreferredSize(new Dimension(PREFERRED_SIZE));
        chooser.setMinimumSize(new Dimension(MINIMUM_SIZE));
        installSizing(chooser);
        ComponentTreeStyler.style(chooser);
        polishFonts(chooser);
    }

    /**
     * Installs a listener that applies top-level dialog sizing once Swing has
     * created the file chooser window.
     *
     * @param chooser file chooser
     */
    private static void installSizing(JFileChooser chooser) {
        if (Boolean.TRUE.equals(chooser.getClientProperty(SIZE_LISTENER_PROPERTY))) {
            return;
        }
        chooser.putClientProperty(SIZE_LISTENER_PROPERTY, Boolean.TRUE);
        chooser.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && chooser.isShowing()) {
                applyWindowSize(chooser);
            }
        });
    }

    /**
     * Applies preferred and minimum sizing to the actual chooser dialog.
     *
     * @param chooser file chooser
     */
    private static void applyWindowSize(JFileChooser chooser) {
        Window window = SwingUtilities.getWindowAncestor(chooser);
        if (window == null) {
            return;
        }
        Dimension minimum = new Dimension(MINIMUM_SIZE);
        Dimension preferred = new Dimension(PREFERRED_SIZE);
        window.setMinimumSize(minimum);
        Dimension current = window.getSize();
        if (current.width < preferred.width || current.height < preferred.height) {
            window.setSize(Math.max(current.width, preferred.width), Math.max(current.height, preferred.height));
            window.validate();
            window.setLocationRelativeTo(window.getOwner());
        }
    }

    /**
     * Keeps chooser rows on the UI font stack even though regular Workbench
     * lists use monospace for dense data.
     *
     * @param component root component
     */
    private static void polishFonts(Component component) {
        if (component instanceof JList<?> list) {
            list.setFont(Theme.font(13, java.awt.Font.PLAIN));
            list.setFixedCellHeight(Math.max(Theme.TABLE_ROW_HEIGHT, list.getFixedCellHeight()));
        } else if (component instanceof JTable table) {
            table.setFont(Theme.font(12, java.awt.Font.PLAIN));
            table.setRowHeight(Math.max(Theme.TABLE_ROW_HEIGHT, table.getRowHeight()));
            if (table.getTableHeader() != null) {
                table.getTableHeader().setFont(Theme.font(11, java.awt.Font.BOLD));
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                polishFonts(child);
            }
        }
    }
}
