package application.gui.workbench.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.LayoutManager;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Layout factories shared by Workbench panels through the public {@link Ui}
 * facade.
 */
final class UiLayout {

    /**
     * Prevents instantiation.
     */
    private UiLayout() {
        // utility
    }

    /**
     * Creates a transparent wrapping flow panel.
     *
     * @param align flow alignment
     * @return panel
     */
    static JPanel flow(int align) {
        return transparentPanel(new WrappingFlowLayout(align, 6, 3));
    }

    /**
     * Creates a transparent panel for unframed layout composition.
     *
     * @param layout layout manager
     * @return panel
     */
    static JPanel transparentPanel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        panel.setBackground(Theme.BG);
        return panel;
    }

    /**
     * Creates a styled row of buttons.
     *
     * @param align flow alignment
     * @param buttons buttons to add
     * @return button row
     */
    static JPanel buttonRow(int align, JButton... buttons) {
        JPanel panel = flow(align);
        for (JButton oneButton : buttons) {
            panel.add(oneButton);
        }
        return panel;
    }

    /**
     * Creates a styled row of arbitrary controls.
     *
     * @param align flow alignment
     * @param controls controls to add
     * @return control row
     */
    static JPanel controlRow(int align, JComponent... controls) {
        JPanel panel = flow(align);
        for (JComponent control : controls) {
            panel.add(control);
        }
        return panel;
    }

    /**
     * Wraps scroll-pane content so it fills spare viewport height.
     *
     * @param content content component
     * @return viewport-filling wrapper
     */
    static JComponent fillViewport(JComponent content) {
        JPanel wrapper = new ViewportFillPanel(new BorderLayout());
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }

    /**
     * Wraps content in a width-capped centered viewport.
     *
     * @param content content component
     * @param maxWidth maximum inner content width
     * @return centered viewport wrapper
     */
    static JComponent centeredViewport(JComponent content, int maxWidth) {
        return new CenteredViewportPanel(content, maxWidth);
    }

    /**
     * Creates a thin vertical toolbar separator.
     *
     * @return separator component
     */
    static JComponent toolbarSeparator() {
        JComponent separator = new ToolbarSeparator();
        separator.setOpaque(false);
        return separator;
    }

    /**
     * Thin vertical divider used between toolbar control groups.
     */
    private static final class ToolbarSeparator extends JComponent {

        /**
         * Serialization identifier for Swing component compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Returns the stable separator footprint.
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(1 + 2 * Theme.SPACE_XS, Theme.CONTROL_HEIGHT);
        }

        /**
         * Paints the centered hairline divider.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            int x = getWidth() / 2;
            int inset = Theme.SPACE_XS;
            graphics.setColor(Theme.LINE);
            graphics.fillRect(x, inset, 1, Math.max(0, getHeight() - 2 * inset));
        }
    }
}
