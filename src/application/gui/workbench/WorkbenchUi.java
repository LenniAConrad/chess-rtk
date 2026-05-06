package application.gui.workbench;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.geom.Path2D;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JViewport;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSpinnerUI;
import javax.swing.text.JTextComponent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Small Swing factory and layout helpers used by the workbench.
 */
final class WorkbenchUi {

    /**
     * Styled scrollbar thickness.
     */
    private static final int SCROLLBAR_THICKNESS = 10;

    /**
     * Shared UI animation frame delay.
     */
    private static final int ANIMATION_DELAY_MS = 16;

    /**
     * Button fill transition duration.
     */
    private static final int BUTTON_TRANSITION_MS = 65;

    /**
     * Prevents instantiation.
     */
    private WorkbenchUi() {
        // utility
    }

    /**
     * Creates a styled button.
     *
     * @param text text
     * @param primary primary style
     * @param listener listener
     * @return button
     */
    static JButton button(String text, boolean primary, ActionListener listener) {
        JButton button = new StyledButton(text);
        WorkbenchTheme.button(button, primary);
        button.setToolTipText(text);
        button.addActionListener(listener);
        return button;
    }

    /**
     * Creates a compact icon-only button with an accessible label.
     *
     * @param label tooltip and accessible label
     * @param listener listener
     * @return button
     */
    static JButton iconButton(String label, ActionListener listener) {
        JButton button = button(label, false, listener);
        button.setText("");
        button.setToolTipText(label);
        button.getAccessibleContext().setAccessibleName(label);
        button.setMargin(new Insets(6, 8, 6, 8));
        button.setBorder(WorkbenchTheme.pad(5, 7, 5, 7));
        Dimension size = new Dimension(34, 32);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        return button;
    }

    /**
     * Creates a flow panel.
     *
     * @param align alignment
     * @return panel
     */
    static JPanel flow(int align) {
        return transparentPanel(new FlowLayout(align, 6, 3));
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
        panel.setBackground(WorkbenchTheme.BG);
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
     * Wraps scroll-pane content so it fills the viewport when there is spare
     * height, while still scrolling normally when content is taller.
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
     * Creates a label.
     *
     * @param text text
     * @return label
     */
    static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(WorkbenchTheme.MUTED);
        label.setFont(WorkbenchTheme.font(12, Font.BOLD));
        label.setHorizontalAlignment(SwingConstants.LEFT);
        return label;
    }

    /**
     * Creates a styled scroll pane.
     *
     * @param view scrollable view
     * @return scroll pane
     */
    static JScrollPane scroll(JComponent view) {
        JScrollPane pane = new JScrollPane(view);
        styleScrollPane(pane);
        return pane;
    }

    /**
     * Styles an existing scroll pane and its nested scroll bars.
     *
     * @param pane scroll pane
     */
    static void styleScrollPane(JScrollPane pane) {
        Component view = pane.getViewport() == null ? null : pane.getViewport().getView();
        Color viewportBackground = scrollBackground(view);
        pane.setBorder(BorderFactory.createLineBorder(WorkbenchTheme.LINE));
        pane.setOpaque(false);
        pane.setViewportBorder(BorderFactory.createEmptyBorder());
        pane.getViewport().setOpaque(true);
        pane.getViewport().setBackground(viewportBackground);
        pane.setBackground(WorkbenchTheme.TRANSPARENT);
        installScrollCorners(pane, viewportBackground);
        styleScrollBar(pane.getVerticalScrollBar());
        styleScrollBar(pane.getHorizontalScrollBar());
    }

    /**
     * Creates a flat titled section without adding another card layer.
     *
     * @param title title
     * @param child child
     * @return panel
     */
    static JPanel titled(String title, JComponent child) {
        JPanel panel = transparentPanel(new BorderLayout(6, 6));
        panel.setBorder(WorkbenchTheme.pad(0, 0, 0, 0));
        panel.add(WorkbenchTheme.section(title), BorderLayout.NORTH);
        panel.add(child, BorderLayout.CENTER);
        return panel;
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
     * Adds a component to a grid bag panel.
     *
     * @param panel panel
     * @param component component
     * @param c constraints
     * @param x x
     * @param y y
     * @param width width
     * @param height height
     */
    static void grid(JPanel panel, Component component, GridBagConstraints c, int x, int y, int width, int height) {
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.gridheight = height;
        c.weightx = width > 1 ? 1 : 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, c);
    }

    /**
     * Returns default grid constraints.
     *
     * @return constraints
     */
    static GridBagConstraints constraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    /**
     * Styles a tabbed pane as a compact solid tab strip.
     *
     * @param tabs tabbed pane
     */
    static void styleTabs(JTabbedPane tabs) {
        tabs.setUI(new WorkbenchTabbedPaneUI());
        tabs.setOpaque(false);
        tabs.setBackground(WorkbenchTheme.TRANSPARENT);
        tabs.setForeground(WorkbenchTheme.TEXT);
        tabs.setFont(WorkbenchTheme.font(12, Font.BOLD));
        tabs.setFocusable(true);
    }

    /**
     * Creates a styled tabbed pane.
     *
     * @return tabbed pane
     */
    static JTabbedPane tabbedPane() {
        JTabbedPane pane = new JTabbedPane();
        styleTabs(pane);
        return pane;
    }

    /**
     * Styles a combo box.
     *
     * @param combo combo box
     */
    static void styleCombo(JComboBox<?> combo) {
        combo.setUI(new StyledComboBoxUI());
        combo.setOpaque(false);
        combo.setBackground(WorkbenchTheme.ELEVATED_SOLID);
        combo.setForeground(WorkbenchTheme.TEXT);
        combo.setFont(WorkbenchTheme.font(13, Font.PLAIN));
        combo.setBorder(BorderFactory.createLineBorder(WorkbenchTheme.INPUT_BORDER));
        combo.setMaximumRowCount(12);
        combo.setRenderer(new StyledComboRenderer());
    }

    /**
     * Styles multiple combo boxes.
     *
     * @param combos combo boxes
     */
    static void styleCombos(JComboBox<?>... combos) {
        for (JComboBox<?> combo : combos) {
            styleCombo(combo);
        }
    }

    /**
     * Styles a spinner and its text editor.
     *
     * @param spinner spinner
     */
    static void styleSpinner(JSpinner spinner) {
        spinner.setUI(new StyledSpinnerUI());
        spinner.setOpaque(false);
        spinner.setBackground(WorkbenchTheme.PANEL_SOLID);
        spinner.setForeground(WorkbenchTheme.TEXT);
        spinner.setFont(WorkbenchTheme.font(13, Font.PLAIN));
        spinner.setBorder(BorderFactory.createLineBorder(WorkbenchTheme.INPUT_BORDER));
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            WorkbenchTheme.field(editor.getTextField());
        }
    }

    /**
     * Styles multiple spinners.
     *
     * @param spinners spinners
     */
    static void styleSpinners(JSpinner... spinners) {
        for (JSpinner spinner : spinners) {
            styleSpinner(spinner);
        }
    }

    /**
     * Styles multiple text fields.
     *
     * @param fields text fields
     */
    static void styleFields(JTextField... fields) {
        for (JTextField field : fields) {
            WorkbenchTheme.field(field);
        }
    }

    /**
     * Styles multiple text areas.
     *
     * @param areas text areas
     */
    static void styleAreas(JTextArea... areas) {
        for (JTextArea area : areas) {
            WorkbenchTheme.area(area);
        }
    }

    /**
     * Installs empty-field placeholder text without changing the component value.
     *
     * @param component text component
     * @param text placeholder copy
     */
    static void placeholder(JTextComponent component, String text) {
        WorkbenchTheme.placeholder(component, text);
    }

    /**
     * Styles a checkbox that is not using the custom workbench toggle class.
     *
     * @param box checkbox
     */
    static void styleCheckBox(JCheckBox box) {
        if (box instanceof WorkbenchToggleBox) {
            return;
        }
        box.setOpaque(false);
        box.setForeground(WorkbenchTheme.TEXT);
        box.setFont(WorkbenchTheme.font(12, Font.PLAIN));
        box.setFocusPainted(false);
    }

    /**
     * Styles a file chooser before it is shown.
     *
     * @param chooser file chooser
     */
    static void styleFileChooser(JFileChooser chooser) {
        chooser.setBackground(WorkbenchTheme.BG);
        chooser.setForeground(WorkbenchTheme.TEXT);
        chooser.setBorder(WorkbenchTheme.pad(10, 10, 10, 10));
        styleComponentTree(chooser);
    }

    /**
     * Shows a styled confirm dialog.
     *
     * @param owner owner component
     * @param content dialog content
     * @param title dialog title
     * @return JOptionPane result
     */
    static int showConfirmDialog(Component owner, JComponent content, String title) {
        JOptionPane pane = new JOptionPane(content, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        return showOptionPane(owner, title, pane);
    }

    /**
     * Shows a styled error dialog.
     *
     * @param owner owner component
     * @param title dialog title
     * @param message dialog message
     */
    static void showErrorDialog(Component owner, String title, String message) {
        JOptionPane pane = new JOptionPane(message == null ? title : message, JOptionPane.ERROR_MESSAGE,
                JOptionPane.DEFAULT_OPTION);
        showOptionPane(owner, title, pane);
    }

    /**
     * Applies workbench styling to a component subtree.
     *
     * @param component root component
     */
    static void styleComponentTree(Component component) {
        if (component == null) {
            return;
        }
        styleComponent(component);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                styleComponentTree(child);
            }
        }
        if (component instanceof JScrollPane pane) {
            styleScrollPane(pane);
        }
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

    /**
     * Shows an option pane after styling generated dialog content.
     *
     * @param owner owner component
     * @param title dialog title
     * @param pane option pane
     * @return selected option
     */
    private static int showOptionPane(Component owner, String title, JOptionPane pane) {
        styleComponentTree(pane);
        JDialog dialog = pane.createDialog(owner, title);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(WorkbenchTheme.LINE));
        styleComponentTree(dialog.getContentPane());
        dialog.setVisible(true);
        dialog.dispose();
        Object value = pane.getValue();
        return value instanceof Integer option ? option.intValue() : JOptionPane.CLOSED_OPTION;
    }

    /**
     * Applies styling for one component in a recursively scanned dialog tree.
     *
     * @param component component
     */
    private static void styleComponent(Component component) {
        if (component instanceof JFileChooser chooser) {
            chooser.setOpaque(true);
            chooser.setBackground(WorkbenchTheme.BG);
            chooser.setForeground(WorkbenchTheme.TEXT);
        } else if (component instanceof JScrollPane pane) {
            styleScrollPane(pane);
        } else if (component instanceof JTextArea area) {
            WorkbenchTheme.area(area);
        } else if (component instanceof JTextField field) {
            WorkbenchTheme.field(field);
        } else if (component instanceof JComboBox<?> combo) {
            styleCombo(combo);
        } else if (component instanceof JSpinner spinner) {
            styleSpinner(spinner);
        } else if (component instanceof JTable table) {
            WorkbenchTheme.table(table, Math.max(24, table.getRowHeight()));
        } else if (component instanceof JList<?> list) {
            WorkbenchTheme.list(list);
        } else if (component instanceof JTabbedPane tabs) {
            styleTabs(tabs);
        } else if (component instanceof JCheckBox box) {
            styleCheckBox(box);
        } else if (component instanceof AbstractButton button) {
            styleAbstractButton(button);
        } else if (component instanceof JLabel label) {
            label.setForeground(WorkbenchTheme.TEXT);
            label.setFont(WorkbenchTheme.font(12, Font.PLAIN));
        } else if (component instanceof JPanel panel) {
            panel.setOpaque(true);
            panel.setBackground(WorkbenchTheme.PANEL_SOLID);
        } else if (component instanceof JComponent jComponent) {
            jComponent.setBackground(WorkbenchTheme.PANEL_SOLID);
            jComponent.setForeground(WorkbenchTheme.TEXT);
        }
    }

    /**
     * Styles a standard dialog button without breaking icon-only chooser controls.
     *
     * @param button button
     */
    private static void styleAbstractButton(AbstractButton button) {
        String text = button.getText();
        if (text != null && !text.isBlank() && button instanceof JButton) {
            WorkbenchTheme.button(button, false);
            return;
        }
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setForeground(WorkbenchTheme.TEXT);
        button.setFont(WorkbenchTheme.font(12, Font.PLAIN));
        button.setBorder(WorkbenchTheme.pad(4, 6, 4, 6));
    }

    /**
     * Styles one scroll bar.
     *
     * @param bar scroll bar
     */
    private static void styleScrollBar(JScrollBar bar) {
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder());
        bar.setPreferredSize(new Dimension(SCROLLBAR_THICKNESS, SCROLLBAR_THICKNESS));
        bar.setUnitIncrement(18);
        bar.setUI(new StyledScrollBarUI());
    }

    /**
     * Returns a solid viewport background for one scrollable view.
     *
     * @param view scrollable view
     * @return viewport background
     */
    private static Color scrollBackground(Component view) {
        if (!(view instanceof JComponent jComponent)) {
            return WorkbenchTheme.PANEL_SOLID;
        }
        Color background = jComponent.getBackground();
        return jComponent.isOpaque() && background != null && background.getAlpha() == 255
                ? background
                : WorkbenchTheme.PANEL_SOLID;
    }

    /**
     * Scrollable wrapper that stretches short content to the visible area.
     */
    private static final class ViewportFillPanel extends JPanel implements Scrollable {

        /**
         * Serialization identifier for Swing panel compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Creates a viewport-filling panel.
         *
         * @param layout layout manager
         */
        ViewportFillPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBackground(WorkbenchTheme.BG);
        }

        /**
         * Returns the natural viewport size.
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        /**
         * Returns a compact scroll increment.
         *
         * @param visibleRect visible rectangle
         * @param orientation scroll orientation
         * @param direction scroll direction
         * @return unit increment
         */
        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        /**
         * Returns a viewport-sized scroll increment.
         *
         * @param visibleRect visible rectangle
         * @param orientation scroll orientation
         * @param direction scroll direction
         * @return block increment
         */
        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            int size = orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
            return Math.max(24, size - 24);
        }

        /**
         * Tracks viewport width to prevent horizontal gutters.
         *
         * @return true
         */
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        /**
         * Tracks viewport height only while the content is shorter than the
         * available space.
         *
         * @return true when short content should fill the viewport
         */
        @Override
        public boolean getScrollableTracksViewportHeight() {
            Container parent = getParent();
            return parent instanceof JViewport viewport && getPreferredSize().height < viewport.getHeight();
        }
    }

    /**
     * Installs matching corner fillers to avoid default gray scroll-pane corners.
     *
     * @param pane scroll pane
     * @param background corner background
     */
    private static void installScrollCorners(JScrollPane pane, Color background) {
        pane.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, scrollCorner(background));
        pane.setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, scrollCorner(background));
        pane.setCorner(ScrollPaneConstants.LOWER_LEFT_CORNER, scrollCorner(background));
        pane.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, scrollCorner(background));
    }

    /**
     * Creates one solid scroll-pane corner filler.
     *
     * @param background background color
     * @return corner component
     */
    private static JComponent scrollCorner(Color background) {
        JPanel corner = new JPanel();
        corner.setOpaque(true);
        corner.setBackground(background);
        return corner;
    }

    /**
     * Applies an ease-out cubic animation curve.
     *
     * @param value linear progress
     * @return eased progress
     */
    static double easeOutCubic(double value) {
        double progress = clamp(value, 0.0, 1.0);
        return 1.0 - Math.pow(1.0 - progress, 3.0);
    }

    /**
     * Shortens text to fit a fixed pixel width.
     *
     * @param text source text
     * @param metrics font metrics
     * @param maxWidth maximum width
     * @return fitted text
     */
    static String elide(String text, FontMetrics metrics, int maxWidth) {
        if (text == null || text.isEmpty() || metrics.stringWidth(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String suffix = "...";
        int suffixWidth = metrics.stringWidth(suffix);
        if (suffixWidth >= maxWidth) {
            return "";
        }
        int budget = maxWidth - suffixWidth;
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (metrics.stringWidth(text.substring(0, mid)) <= budget) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low <= 0 ? "" : text.substring(0, low) + suffix;
    }

    /**
     * Blends two colors.
     *
     * @param from start color
     * @param to end color
     * @param progress progress from 0 to 1
     * @return blended color
     */
    private static Color blend(Color from, Color to, double progress) {
        double amount = clamp(progress, 0.0, 1.0);
        return new Color(
                blendChannel(from.getRed(), to.getRed(), amount),
                blendChannel(from.getGreen(), to.getGreen(), amount),
                blendChannel(from.getBlue(), to.getBlue(), amount),
                blendChannel(from.getAlpha(), to.getAlpha(), amount));
    }

    /**
     * Blends one color channel.
     *
     * @param from start channel
     * @param to end channel
     * @param progress progress
     * @return blended channel
     */
    private static int blendChannel(int from, int to, double progress) {
        return (int) Math.round(from + (to - from) * progress);
    }

    /**
     * Returns whether two colors are equal by ARGB value.
     *
     * @param left first color
     * @param right second color
     * @return true when both colors match
     */
    private static boolean sameColor(Color left, Color right) {
        return left != null && right != null && left.getRGB() == right.getRGB();
    }

    /**
     * Clamps a value.
     *
     * @param value value
     * @param min minimum
     * @param max maximum
     * @return clamped value
     */
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Rounded button that paints a flat chip.
     */
    private static final class StyledButton extends JButton {

        /**
         * Serialization identifier for Swing button compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Chip corner radius.
         */
        private static final int RADIUS = 5;

        /**
         * Cached focus-ring color so it does not allocate per paint.
         */
        private static final Color BUTTON_FOCUS_RING = WorkbenchTheme.withAlpha(WorkbenchTheme.INPUT_FOCUS, 90);

        /**
         * Live set of buttons currently animating; ticked by the shared timer.
         */
        private static final java.util.Set<StyledButton> ACTIVE_BUTTONS =
                java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

        /**
         * Shared 60 fps animation timer that advances all active buttons.
         */
        private static final Timer SHARED_FILL_TIMER = createSharedFillTimer();

        /**
         * Whether this button is currently being ticked by the shared timer.
         */
        private boolean fillRunning;

        /**
         * Fill color at the start of the current transition.
         */
        private Color transitionStartFill;

        /**
         * Desired fill color at the end of the current transition.
         */
        private Color transitionTargetFill;

        /**
         * Current interpolated fill color.
         */
        private Color animatedFill;

        /**
         * Start time for the current fill transition.
         */
        private long fillTransitionStartedAt;

        /**
         * Creates a rounded chip button.
         *
         * @param text button text
         */
        StyledButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setOpaque(false);
            setRolloverEnabled(true);
            getModel().addChangeListener(event -> startFillTransition());
        }

        /**
         * Creates and returns the shared 60 fps animation timer.
         *
         * @return shared coalescing timer
         */
        private static Timer createSharedFillTimer() {
            Timer timer = new Timer(ANIMATION_DELAY_MS, event -> {
                if (ACTIVE_BUTTONS.isEmpty()) {
                    ((Timer) event.getSource()).stop();
                    return;
                }
                for (StyledButton button : ACTIVE_BUTTONS.toArray(new StyledButton[0])) {
                    button.tickFillTransition();
                }
            });
            timer.setCoalesce(true);
            return timer;
        }

        /**
         * Joins the shared animation tick set.
         */
        private void joinAnimation() {
            if (fillRunning) {
                return;
            }
            fillRunning = true;
            ACTIVE_BUTTONS.add(this);
            if (!SHARED_FILL_TIMER.isRunning()) {
                SHARED_FILL_TIMER.start();
            }
        }

        /**
         * Leaves the shared animation tick set.
         */
        private void leaveAnimation() {
            if (!fillRunning) {
                return;
            }
            fillRunning = false;
            ACTIVE_BUTTONS.remove(this);
        }

        /**
         * Returns whether this button is currently animating.
         *
         * @return true while the button participates in the shared tick
         */
        private boolean isFillRunning() {
            return fillRunning;
        }

        /**
         * Paints a rounded button body.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean primary = Boolean.TRUE.equals(getClientProperty(WorkbenchTheme.CLIENT_PRIMARY));
                Color fill = buttonFill(primary);
                Color border = isEnabled() ? WorkbenchTheme.buttonBorder(primary) : WorkbenchTheme.BUTTON_DISABLED_BORDER;
                setForeground(isEnabled()
                        ? primary ? WorkbenchTheme.PRIMARY_BUTTON_TEXT : WorkbenchTheme.SECONDARY_BUTTON_TEXT
                        : WorkbenchTheme.BUTTON_DISABLED_TEXT);
                g.setColor(fill);
                g.fillRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1), RADIUS, RADIUS);
                g.setColor(border);
                g.drawRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1), RADIUS, RADIUS);
                if (isFocusOwner()) {
                    g.setColor(BUTTON_FOCUS_RING);
                    g.drawRoundRect(2, 2, Math.max(0, getWidth() - 5), Math.max(0, getHeight() - 5), RADIUS, RADIUS);
                }
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }

        /**
         * Returns the current flat button fill.
         *
         * @param primary whether the button uses primary styling
         * @return fill color
         */
        private Color buttonFill(boolean primary) {
            Color desired = desiredButtonFill(primary);
            boolean targetChangedWhileIdle = !sameColor(desired, transitionTargetFill) && !isFillRunning();
            if (animatedFill == null || targetChangedWhileIdle) {
                animatedFill = desired;
                transitionTargetFill = desired;
            }
            return animatedFill == null ? desired : animatedFill;
        }

        /**
         * Starts a color transition toward the current button state.
         */
        private void startFillTransition() {
            boolean primary = Boolean.TRUE.equals(getClientProperty(WorkbenchTheme.CLIENT_PRIMARY));
            Color desired = desiredButtonFill(primary);
            Color current = animatedFill == null ? restingButtonFill(primary) : animatedFill;
            boolean alreadyAtTarget = sameColor(desired, transitionTargetFill);
            boolean unchangedWhileIdle = sameColor(desired, current) && !isFillRunning();
            if (alreadyAtTarget || unchangedWhileIdle) {
                transitionTargetFill = desired;
                animatedFill = current;
                repaint();
                return;
            }
            transitionStartFill = current;
            transitionTargetFill = desired;
            fillTransitionStartedAt = System.currentTimeMillis();
            joinAnimation();
            repaint();
        }

        /**
         * Advances the fill transition.
         */
        private void tickFillTransition() {
            if (transitionStartFill == null || transitionTargetFill == null) {
                leaveAnimation();
                return;
            }
            double progress = clamp((System.currentTimeMillis() - fillTransitionStartedAt)
                    / (double) BUTTON_TRANSITION_MS, 0.0, 1.0);
            animatedFill = blend(transitionStartFill, transitionTargetFill, easeOutCubic(progress));
            if (progress >= 1.0) {
                animatedFill = transitionTargetFill;
                leaveAnimation();
            }
            repaint();
        }

        /**
         * Returns the desired fill for the current button state.
         *
         * @param primary whether the button uses primary styling
         * @return desired fill color
         */
        private Color desiredButtonFill(boolean primary) {
            if (!isEnabled()) {
                return WorkbenchTheme.BUTTON_DISABLED_BG;
            }
            if (getModel().isPressed()) {
                return WorkbenchTheme.buttonPressed(primary);
            }
            if (getModel().isRollover()) {
                return WorkbenchTheme.buttonHover(primary);
            }
            return WorkbenchTheme.buttonBackground(primary);
        }

        /**
         * Returns the non-hover button fill for transition starts.
         *
         * @param primary whether the button uses primary styling
         * @return resting fill color
         */
        private Color restingButtonFill(boolean primary) {
            return isEnabled() ? WorkbenchTheme.buttonBackground(primary) : WorkbenchTheme.BUTTON_DISABLED_BG;
        }
    }

    /**
     * Inline collapsible section for optional information strips.
     */
    private static final class CollapsibleSection extends JPanel {

        /**
         * Serialization identifier for Swing panel compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Section title.
         */
        private final String title;

        /**
         * Toggle button.
         */
        private final JButton toggle;

        /**
         * Collapsible content.
         */
        private final JComponent content;

        /**
         * Current expansion state.
         */
        private boolean expanded;

        /**
         * Creates an inline collapsible section.
         *
         * @param title section title
         * @param content collapsible content
         * @param expanded initial expansion state
         */
        CollapsibleSection(String title, JComponent content, boolean expanded) {
            super(new BorderLayout(8, 0));
            this.title = title == null || title.isBlank() ? "Info" : title;
            this.content = content;
            toggle = button(this.title, false, event -> setExpanded(!this.expanded));
            setOpaque(false);
            setBackground(WorkbenchTheme.BG);
            add(toggle, BorderLayout.WEST);
            add(content, BorderLayout.CENTER);
            setExpanded(expanded);
        }

        /**
         * Updates the expansion state.
         *
         * @param value true when expanded
         */
        private void setExpanded(boolean value) {
            expanded = value;
            content.setVisible(value);
            toggle.setText(title + (value ? " -" : " +"));
            toggle.setToolTipText(value ? "Collapse " + title : "Expand " + title);
            WorkbenchTheme.button(toggle, false);
            revalidate();
            repaint();
        }
    }

    /**
     * Minimal combo-box UI without platform-gray arrow-button artifacts.
     */
    private static final class StyledComboBoxUI extends BasicComboBoxUI {

        /**
         * Creates the combo arrow button.
         *
         * @return arrow button
         */
        @Override
        protected JButton createArrowButton() {
            return new ArrowButton(SwingConstants.SOUTH);
        }

        /**
         * Paints the current value background.
         *
         * @param graphics graphics context
         * @param bounds value bounds
         * @param hasFocus whether the combo has focus
         */
        @Override
        public void paintCurrentValueBackground(Graphics graphics, Rectangle bounds, boolean hasFocus) {
            graphics.setColor(comboBox.isEnabled() ? WorkbenchTheme.ELEVATED_SOLID : WorkbenchTheme.INPUT_DISABLED);
            graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
    }

    /**
     * Minimal spinner UI with solid arrow buttons.
     */
    private static final class StyledSpinnerUI extends BasicSpinnerUI {

        /**
         * Creates the next-value button.
         *
         * @return next button
         */
        @Override
        protected Component createNextButton() {
            JButton button = new ArrowButton(SwingConstants.NORTH);
            installNextButtonListeners(button);
            return button;
        }

        /**
         * Creates the previous-value button.
         *
         * @return previous button
         */
        @Override
        protected Component createPreviousButton() {
            JButton button = new ArrowButton(SwingConstants.SOUTH);
            installPreviousButtonListeners(button);
            return button;
        }
    }

    /**
     * Small chevron button used by combos and spinners.
     */
    private static final class ArrowButton extends JButton {

        /**
         * Serialization identifier for Swing button compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Arrow direction.
         */
        private final int direction;

        /**
         * Creates an arrow button.
         *
         * @param direction SwingConstants arrow direction
         */
        ArrowButton(int direction) {
            this.direction = direction;
            setBorder(BorderFactory.createEmptyBorder());
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
            setPreferredSize(new Dimension(24, 20));
        }

        /**
         * Paints the chevron.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(isEnabled() ? WorkbenchTheme.MUTED : WorkbenchTheme.BUTTON_DISABLED_TEXT);
                Path2D path = new Path2D.Double();
                double centerX = getWidth() / 2.0;
                double centerY = getHeight() / 2.0;
                if (direction == SwingConstants.NORTH) {
                    path.moveTo(centerX - 4, centerY + 2);
                    path.lineTo(centerX, centerY - 2);
                    path.lineTo(centerX + 4, centerY + 2);
                } else {
                    path.moveTo(centerX - 4, centerY - 2);
                    path.lineTo(centerX, centerY + 2);
                    path.lineTo(centerX + 4, centerY - 2);
                }
                g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(path);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Combo-box renderer that avoids platform-default blue or gray row flashes.
     */
    private static final class StyledComboRenderer extends DefaultListCellRenderer {

        /**
         * Serialization identifier for Swing renderer compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Returns the rendered combo row.
         *
         * @param list source list
         * @param value row value
         * @param index row index
         * @param selected whether selected
         * @param focused whether focused
         * @return renderer component
         */
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            setFont(WorkbenchTheme.font(13, Font.PLAIN));
            setForeground(WorkbenchTheme.TEXT);
            setBackground(selected ? WorkbenchTheme.SELECTION_SOLID : WorkbenchTheme.ELEVATED_SOLID);
            return this;
        }
    }

    /**
     * Thin solid scrollbar UI.
     */
    private static final class StyledScrollBarUI extends BasicScrollBarUI {

        /**
         * Thumb corner radius.
         */
        private static final int THUMB_RADIUS = 8;

        /**
         * Creates an invisible scrollbar button.
         *
         * @param orientation button orientation
         * @return zero-size button
         */
        @Override
        protected JButton createDecreaseButton(int orientation) {
            return invisibleButton();
        }

        /**
         * Creates an invisible scrollbar button.
         *
         * @param orientation button orientation
         * @return zero-size button
         */
        @Override
        protected JButton createIncreaseButton(int orientation) {
            return invisibleButton();
        }

        /**
         * Paints the transparent track.
         *
         * @param graphics graphics context
         * @param component component
         * @param bounds track bounds
         */
        @Override
        protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(WorkbenchTheme.SCROLLBAR_TRACK);
                g.fillRoundRect(bounds.x + 2, bounds.y + 2, Math.max(0, bounds.width - 4),
                        Math.max(0, bounds.height - 4), THUMB_RADIUS, THUMB_RADIUS);
            } finally {
                g.dispose();
            }
        }

        /**
         * Paints the scrollbar thumb.
         *
         * @param graphics graphics context
         * @param component component
         * @param bounds thumb bounds
         */
        @Override
        protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
            if (bounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(isDragging ? WorkbenchTheme.SCROLLBAR_THUMB_HOVER
                        : isThumbRollover() ? WorkbenchTheme.withAlpha(WorkbenchTheme.SCROLLBAR_THUMB, 190)
                        : WorkbenchTheme.SCROLLBAR_THUMB);
                g.fillRoundRect(bounds.x + 2, bounds.y + 2, Math.max(0, bounds.width - 4),
                        Math.max(0, bounds.height - 4), THUMB_RADIUS, THUMB_RADIUS);
            } finally {
                g.dispose();
            }
        }

        /**
         * Creates a zero-size invisible button.
         *
         * @return button
         */
        private static JButton invisibleButton() {
            JButton button = new JButton();
            Dimension size = new Dimension(0, 0);
            button.setPreferredSize(size);
            button.setMinimumSize(size);
            button.setMaximumSize(size);
            return button;
        }
    }
}
