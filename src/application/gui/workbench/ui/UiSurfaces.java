package application.gui.workbench.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.Border;

/**
 * Surface and container factories shared by Workbench screens through the
 * public {@link Ui} facade.
 */
final class UiSurfaces {

    /**
     * Prevents instantiation.
     */
    private UiSurfaces() {
        // utility
    }

    /**
     * Styles a panel as a workbench toolbar band.
     *
     * @param bar band panel
     * @param padding inner padding inside the hairline border
     */
    static void styleToolbarBand(JComponent bar, Border padding) {
        bar.setOpaque(true);
        bar.setBackground(Theme.PANEL_SOLID);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE), padding));
    }

    /**
     * Creates a flat titled section without adding another card layer.
     *
     * @param title title
     * @param child child
     * @return panel
     */
    static JPanel titled(String title, JComponent child) {
        JPanel panel = new JPanel(new BorderLayout(6, 6)) {
            private static final long serialVersionUID = 1L;

            /**
             * {@inheritDoc}
             */
            @Override
            public Dimension getMaximumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, preferred.height);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public float getAlignmentX() {
                return Component.LEFT_ALIGNMENT;
            }
        };
        panel.setOpaque(false);
        panel.setBorder(Theme.pad(0, 0, 0, 0));
        panel.add(Theme.section(title), BorderLayout.NORTH);
        panel.add(child, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates the shared responsive card grid.
     *
     * @param minColumnWidth minimum column width before the grid reflows
     * @return an empty card grid
     */
    static CardGrid contentGrid(int minColumnWidth) {
        return new CardGrid(minColumnWidth, Theme.SPACE_MD);
    }

    /**
     * Wraps body content in a raised elevated card.
     *
     * @param title header text, or {@code null} for a headerless card
     * @param body card body component
     * @return elevated card component
     */
    static JComponent card(String title, JComponent body) {
        return new Card(title, null, body);
    }

    /**
     * Wraps body content in a raised elevated card with an optional trailing
     * header affordance.
     *
     * @param title header text, or {@code null} for a headerless card
     * @param trailing optional right-aligned header component, or {@code null}
     * @param body card body component
     * @return elevated card component
     */
    static JComponent card(String title, JComponent trailing, JComponent body) {
        return new Card(title, trailing, body);
    }

    /**
     * Creates an inline collapsible information section.
     *
     * @param title section title
     * @param content collapsible content
     * @param expanded initial expansion state
     * @return collapsible section
     */
    static JComponent collapsible(String title, JComponent content, boolean expanded) {
        return new CollapsibleSection(title, content, expanded);
    }

    /**
     * Sets the expansion state for a collapsible section.
     *
     * @param component possible collapsible component
     * @param expanded expansion state
     * @return true when the component was a collapsible section
     */
    static boolean setCollapsibleExpanded(JComponent component, boolean expanded) {
        if (component instanceof CollapsibleSection section) {
            section.setExpanded(expanded);
            return true;
        }
        return false;
    }
}
