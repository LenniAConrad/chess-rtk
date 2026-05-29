package application.gui.workbench.ui;

import application.gui.workbench.audio.SoundCue;
import application.gui.workbench.audio.SoundService;
import application.gui.workbench.layout.FlatTabbedPaneUI;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.geom.Path2D;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.ListCellRenderer;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.plaf.basic.BasicSpinnerUI;
import javax.swing.text.JTextComponent;
import javax.swing.text.NumberFormatter;

/**
 * Small Swing factory and layout helpers used by the workbench.
 */
public final class Ui {

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
     * Default chooser size large enough to avoid cramped row wrapping.
     */
    private static final Dimension FILE_CHOOSER_PREFERRED_SIZE = new Dimension(760, 520);

    /**
     * Minimum chooser size that keeps toolbar, file list, and form rows usable.
     */
    private static final Dimension FILE_CHOOSER_MINIMUM_SIZE = new Dimension(620, 420);

    /**
     * Client-property key marking combos that already have the enabled-state
     * refresh listener installed.
     */
    private static final String COMBO_STATE_LISTENER_PROPERTY =
            Ui.class.getName() + ".comboStateListener";

    /**
     * Client-property key marking file choosers with a window sizing listener.
     */
    private static final String FILE_CHOOSER_SIZE_LISTENER_PROPERTY =
            Ui.class.getName() + ".fileChooserSizeListener";

    /**
     * Prevents instantiation.
     */
    private Ui() {
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
    public static JButton button(String text, boolean primary, ActionListener listener) {
        JButton button = new StyledButton(text);
        Theme.button(button, primary);
        button.setToolTipText(text);
        if (listener != null) {
            button.addActionListener(listener);
        }
        button.addActionListener(event -> SoundService.play(SoundCue.UI_CLICK));
        return button;
    }

    /**
     * Returns a checkbox after attaching a tooltip.
     *
     * @param <T> checkbox type
     * @param toggle target checkbox
     * @param tooltip tooltip text
     * @return the same checkbox for fluent field initialization
     */
    public static <T extends JCheckBox> T withTooltip(T toggle, String tooltip) {
        toggle.setToolTipText(tooltip);
        return toggle;
    }

    /**
     * Creates a compact icon-only button with an accessible label.
     *
     * @param label tooltip and accessible label
     * @param listener listener
     * @return button
     */
    public static JButton iconButton(String label, ActionListener listener) {
        JButton button = button(label, false, listener);
        button.setText("");
        button.setToolTipText(label);
        button.getAccessibleContext().setAccessibleName(label);
        button.setMargin(new Insets(6, 8, 6, 8));
        button.setBorder(Theme.pad(5, 7, 5, 7));
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
    public static JPanel flow(int align) {
        return transparentPanel(new WrappingFlowLayout(align, 6, 3));
    }

    /**
     * Creates a transparent panel for unframed layout composition.
     *
     * @param layout layout manager
     * @return panel
     */
    public static JPanel transparentPanel(LayoutManager layout) {
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
    public static JPanel buttonRow(int align, JButton... buttons) {
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
    public static JComponent fillViewport(JComponent content) {
        JPanel wrapper = new ViewportFillPanel(new BorderLayout());
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }

    /**
     * Wraps content so it tracks the viewport width, caps the inner content at
     * a readable maximum width, and centers it when the viewport is wider than
     * that cap. This keeps report-like screens usable on laptop-sized windows
     * without letting them stretch edge-to-edge on large monitors.
     *
     * @param content content component
     * @param maxWidth maximum inner content width
     * @return centered viewport wrapper
     */
    public static JComponent centeredViewport(JComponent content, int maxWidth) {
        return new CenteredViewportPanel(content, maxWidth);
    }

    /**
     * Creates a label.
     *
     * @param text text
     * @return label
     */
    public static JLabel label(String text) {
        JLabel label = new JLabel(text);
        Theme.foreground(label, Theme.ForegroundRole.MUTED);
        label.setFont(Theme.font(12, Font.BOLD));
        label.setHorizontalAlignment(SwingConstants.LEFT);
        return label;
    }

    /**
     * Returns trimmed text from a field, treating null document text as blank.
     *
     * @param field source field
     * @return trimmed text
     */
    public static String trimmed(JTextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    /**
     * Creates a muted, plain-weight one-line caption that explains what a
     * panel or section is for.
     *
     * @param text caption text
     * @return styled caption label
     */
    public static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        Theme.foreground(label, Theme.ForegroundRole.MUTED);
        label.setFont(Theme.font(11, Font.PLAIN));
        label.setHorizontalAlignment(SwingConstants.LEFT);
        return label;
    }

    /**
     * Creates a muted, bold caption label for a toolbar control — the small
     * "Architecture" / "View" / "Sort" tags that sit to the left of a combo
     * or switcher. Centralised so every control row uses the same treatment.
     *
     * @param text caption text
     * @return styled caption label
     */
    public static JLabel controlLabel(String text) {
        return label(text);
    }

    /**
     * Pairs a caption with a control in a tight, baseline-aligned row — the
     * standard "label + combo / switcher" toolbar unit. The returned panel is
     * transparent so it drops straight into any toolbar.
     *
     * @param caption caption text (null or blank for a control with no label)
     * @param control the control component
     * @return labelled control row
     */
    public static JPanel labeledControl(String caption, JComponent control) {
        boolean hasCaption = caption != null && !caption.isBlank();
        int gap = hasCaption ? Theme.SPACE_SM : 0;
        JPanel row = transparentPanel(new WrappingFlowLayout(FlowLayout.LEFT, gap, 0));
        if (hasCaption) {
            row.add(controlLabel(caption));
        }
        row.add(control);
        return row;
    }

    /**
     * Creates one compact form option group.
     *
     * @param text label text
     * @param control option control
     * @return option group
     */
    public static JComponent optionGroup(String text, JComponent control) {
        JPanel panel = transparentPanel(new BorderLayout(6, 0));
        control.setPreferredSize(new Dimension(120, 28));
        panel.add(label(text), BorderLayout.WEST);
        panel.add(control, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates a thin vertical divider for separating control groups inside a
     * horizontal toolbar.
     *
     * @return separator component
     */
    public static JComponent toolbarSeparator() {
        JComponent separator = new JComponent() {
            private static final long serialVersionUID = 1L;

/**
 * {@inheritDoc}
 */
@Override
            public Dimension getPreferredSize() {
    return new Dimension(1 + 2 * Theme.SPACE_XS, Theme.CONTROL_HEIGHT);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            protected void paintComponent(Graphics g) {
                int x = getWidth() / 2;
                int inset = Theme.SPACE_XS;
                g.setColor(Theme.LINE);
                g.fillRect(x, inset, 1, Math.max(0, getHeight() - 2 * inset));
            }
        };
        separator.setOpaque(false);
        return separator;
    }

    /**
     * Creates a styled scroll pane.
     *
     * @param view scrollable view
     * @return scroll pane
     */
    public static JScrollPane scroll(JComponent view) {
        JScrollPane pane = new JScrollPane(view);
        styleScrollPane(pane);
        return pane;
    }

    /**
     * Styles an existing scroll pane and its nested scroll bars.
     *
     * @param pane scroll pane
     */
    public static void styleScrollPane(JScrollPane pane) {
        pane.setBorder(BorderFactory.createEmptyBorder());
        refreshScrollPaneTheme(pane);
    }

    /**
     * Reapplies scroll-pane colors and custom scroll bars while preserving the
     * caller's outer border.
     *
     * @param pane scroll pane
     */
    public static void refreshScrollPaneTheme(JScrollPane pane) {
        Component view = pane.getViewport() == null ? null : pane.getViewport().getView();
        Color viewportBackground = scrollBackground(view);
        pane.setOpaque(false);
        pane.setViewportBorder(BorderFactory.createEmptyBorder());
        pane.getViewport().setOpaque(true);
        pane.getViewport().setBackground(viewportBackground);
        pane.setBackground(Theme.TRANSPARENT);
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
    public static JPanel titled(String title, JComponent child) {
        JPanel panel = transparentPanel(new BorderLayout(6, 6));
        panel.setBorder(Theme.pad(0, 0, 0, 0));
        panel.add(Theme.section(title), BorderLayout.NORTH);
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
    public static JComponent collapsible(String title, JComponent content, boolean expanded) {
        return new CollapsibleSection(title, content, expanded);
    }

    /**
     * Sets the expansion state for a component returned by
     * {@link #collapsible(String, JComponent, boolean)}.
     *
     * @param component collapsible component
     * @param expanded expansion state
     * @return true when the component was a collapsible section
     */
    public static boolean setCollapsibleExpanded(JComponent component, boolean expanded) {
        if (component instanceof CollapsibleSection section) {
            section.setExpanded(expanded);
            return true;
        }
        return false;
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
    public static void grid(JPanel panel, Component component, GridBagConstraints c, int x, int y, int width, int height) {
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.gridheight = height;
        c.weightx = width > 1 ? 1 : 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, c);
    }

    /**
     * Adds a transparent filler row so stretched form panels keep controls at
     * the top of the work surface.
     *
     * @param panel target panel
     * @param c reusable constraints
     * @param row grid row
     * @param width grid width
     */
    public static void addVerticalFiller(JPanel panel, GridBagConstraints c, int row, int width) {
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = width;
        c.gridheight = 1;
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        panel.add(transparentPanel(new BorderLayout()), c);
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
    }

    /**
     * Returns default grid constraints.
     *
     * @return constraints
     */
    public static GridBagConstraints constraints() {
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
    public static void styleTabs(JTabbedPane tabs) {
        tabs.setUI(new FlatTabbedPaneUI());
        tabs.setOpaque(false);
        tabs.setBackground(Theme.TRANSPARENT);
        tabs.setForeground(Theme.TEXT);
        tabs.setFont(Theme.font(12, Font.BOLD));
        tabs.setFocusable(true);
    }

    /**
     * Creates a styled tabbed pane.
     *
     * @return tabbed pane
     */
    public static JTabbedPane tabbedPane() {
        JTabbedPane pane = new JTabbedPane();
        pane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        styleTabs(pane);
        return pane;
    }

    /**
     * Applies shared workbench chrome to a popup menu and its current children.
     *
     * @param menu popup menu
     */
    public static void stylePopupMenu(JPopupMenu menu) {
        menu.setOpaque(true);
        menu.setBackground(Theme.PANEL_SOLID);
        menu.setForeground(Theme.TEXT);
        menu.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        for (Component child : menu.getComponents()) {
            stylePopupComponent(child);
        }
    }

    /**
     * Applies shared workbench chrome to one popup menu item.
     *
     * @param item menu item
     */
    public static void stylePopupMenuItem(JMenuItem item) {
        if (item instanceof JRadioButtonMenuItem radio) {
            MenuGlyphs.styleRadioItem(radio);
        } else if (item instanceof JCheckBoxMenuItem check) {
            MenuGlyphs.styleCheckItem(check);
        } else {
            MenuGlyphs.styleItem(item);
        }
    }

    private static void stylePopupComponent(Component component) {
        if (component instanceof JPopupMenu popup) {
            stylePopupMenu(popup);
            return;
        }
        if (component instanceof JMenuItem item) {
            stylePopupMenuItem(item);
        } else if (component instanceof JSeparator separator) {
            separator.setBackground(Theme.PANEL_SOLID);
            separator.setForeground(Theme.LINE);
        } else if (component instanceof JComponent jComponent) {
            jComponent.setOpaque(true);
            jComponent.setBackground(Theme.PANEL_SOLID);
            jComponent.setForeground(Theme.TEXT);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                stylePopupComponent(child);
            }
        }
    }

    /**
     * Styles a combo box.
     *
     * @param combo combo box
     */
    public static void styleCombo(JComboBox<?> combo) {
        combo.setUI(new StyledComboBoxUI());
        combo.setOpaque(false);
        applyComboState(combo);
        combo.setFont(Theme.font(13, Font.PLAIN));
        combo.setBorder(InputChrome.compactBorder(false, false));
        InputChrome.install(combo, true);
        combo.setMaximumRowCount(12);
        combo.setRenderer(new StyledComboRenderer(combo));
        installComboStateListener(combo);
    }

    /**
     * Styles multiple combo boxes.
     *
     * @param combos combo boxes
     */
    public static void styleCombos(JComboBox<?>... combos) {
        for (JComboBox<?> combo : combos) {
            styleCombo(combo);
        }
    }

    /**
     * Installs the enabled-state refresh listener for a styled combo.
     *
     * @param combo combo box
     */
    private static void installComboStateListener(JComboBox<?> combo) {
        if (Boolean.TRUE.equals(combo.getClientProperty(COMBO_STATE_LISTENER_PROPERTY))) {
            return;
        }
        combo.putClientProperty(COMBO_STATE_LISTENER_PROPERTY, Boolean.TRUE);
        combo.addPropertyChangeListener("enabled", event -> {
            applyComboState(combo);
            combo.repaint();
        });
    }

    /**
     * Applies the active palette to an enabled or disabled combo box.
     *
     * @param combo combo box
     */
    private static void applyComboState(JComboBox<?> combo) {
        boolean enabled = combo.isEnabled();
        combo.setBackground(enabled ? Theme.INPUT : Theme.INPUT_DISABLED);
        combo.setForeground(enabled ? Theme.TEXT : Theme.BUTTON_DISABLED_TEXT);
    }

    /**
     * Styles a spinner and its text editor.
     *
     * @param spinner spinner
     */
    public static void styleSpinner(JSpinner spinner) {
        spinner.setUI(new StyledSpinnerUI());
        spinner.setOpaque(false);
        spinner.setBackground(Theme.INPUT);
        spinner.setForeground(Theme.TEXT);
        spinner.setFont(Theme.font(13, Font.PLAIN));
        spinner.setBorder(InputChrome.compactBorder(false, false));
        InputChrome.install(spinner, true);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            styleSpinnerEditor(editor);
        }
    }

    /**
     * Styles a spinner whose editor should accept only integer values.
     *
     * @param spinner spinner
     */
    public static void styleIntegerSpinner(JSpinner spinner) {
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "#,##0"));
        styleSpinner(spinner);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            JFormattedTextField field = editor.getTextField();
            if (field.getFormatter() instanceof NumberFormatter formatter) {
                formatter.setValueClass(Integer.class);
                formatter.setAllowsInvalid(false);
                formatter.setCommitsOnValidEdit(true);
            }
        }
    }

    /**
     * Styles the text field embedded inside a spinner without adding another
     * nested input border.
     *
     * @param editor spinner editor
     */
    static void styleSpinnerEditor(JSpinner.DefaultEditor editor) {
        JFormattedTextField field = editor.getTextField();
        editor.setOpaque(true);
        editor.setBackground(Theme.INPUT);
        field.setOpaque(true);
        field.setBackground(Theme.INPUT);
        field.setForeground(Theme.TEXT);
        field.setDisabledTextColor(Theme.BUTTON_DISABLED_TEXT);
        field.setCaretColor(Theme.ACCENT);
        field.setSelectionColor(Theme.TEXT_SELECTION);
        field.setSelectedTextColor(Theme.TEXT);
        field.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 6));
        field.setFont(Theme.font(13, Font.PLAIN));
        field.setHorizontalAlignment(SwingConstants.RIGHT);
        field.setMinimumSize(new Dimension(32, Theme.CONTROL_HEIGHT - 2));
    }

    /**
     * Styles a compact horizontal slider with workbench palette colors.
     *
     * @param slider slider to style
     */
    public static void styleSlider(JSlider slider) {
        slider.setUI(new StyledSliderUI(slider));
        slider.setOpaque(false);
        Theme.foreground(slider, Theme.ForegroundRole.TEXT);
        slider.setBackground(Theme.PANEL_SOLID);
        slider.setFocusable(true);
    }

    /**
     * Styles multiple spinners.
     *
     * @param spinners spinners
     */
    public static void styleSpinners(JSpinner... spinners) {
        for (JSpinner spinner : spinners) {
            styleSpinner(spinner);
        }
    }

    /**
     * Styles multiple text fields.
     *
     * @param fields text fields
     */
    public static void styleFields(JTextField... fields) {
        for (JTextField field : fields) {
            Theme.field(field);
        }
    }

    /**
     * Styles multiple text areas.
     *
     * @param areas text areas
     */
    public static void styleAreas(JTextArea... areas) {
        for (JTextArea area : areas) {
            Theme.area(area);
        }
    }

    /**
     * Installs empty-field placeholder text without changing the component value.
     *
     * @param component text component
     * @param text placeholder copy
     */
    public static void placeholder(JTextComponent component, String text) {
        Theme.placeholder(component, text);
    }

    /**
     * Styles a checkbox that is not using the custom workbench toggle class.
     *
     * @param box checkbox
     */
    public static void styleCheckBox(JCheckBox box) {
        if (box instanceof ToggleBox) {
            return;
        }
        box.setOpaque(false);
        box.setForeground(Theme.TEXT);
        box.setFont(Theme.font(12, Font.PLAIN));
        box.setFocusPainted(false);
        box.setRolloverEnabled(true);
        box.setIcon(CheckBoxGlyph.INSTANCE);
        box.setSelectedIcon(CheckBoxGlyph.INSTANCE);
        box.setRolloverIcon(CheckBoxGlyph.INSTANCE);
        box.setRolloverSelectedIcon(CheckBoxGlyph.INSTANCE);
        box.setDisabledIcon(CheckBoxGlyph.INSTANCE);
        box.setDisabledSelectedIcon(CheckBoxGlyph.INSTANCE);
        box.setIconTextGap(6);
        box.setBorder(Theme.pad(2, 2, 2, 2));
    }

    /**
     * Styles a compact progress bar.
     *
     * @param bar progress bar
     */
    public static void styleProgressBar(JProgressBar bar) {
        bar.setUI(new ProgressBarChrome());
        bar.setOpaque(false);
        bar.setBorderPainted(false);
        bar.setStringPainted(false);
        bar.setForeground(Theme.ACCENT);
        bar.setBackground(Theme.INPUT_DISABLED);
        bar.setPreferredSize(new Dimension(ProgressBarChrome.COMPACT_SIZE));
        bar.setMinimumSize(new Dimension(ProgressBarChrome.COMPACT_SIZE));
    }

    /**
     * Styles a file chooser before it is shown.
     *
     * @param chooser file chooser
     */
    public static void styleFileChooser(JFileChooser chooser) {
        FileChooserIcons.installDefaults();
        chooser.setBackground(Theme.BG);
        chooser.setForeground(Theme.TEXT);
        chooser.setBorder(Theme.pad(10, 10, 10, 10));
        chooser.setPreferredSize(new Dimension(FILE_CHOOSER_PREFERRED_SIZE));
        chooser.setMinimumSize(new Dimension(FILE_CHOOSER_MINIMUM_SIZE));
        installFileChooserSizing(chooser);
        styleComponentTree(chooser);
        polishFileChooserFonts(chooser);
    }

    /**
     * Installs a listener that applies top-level dialog sizing once Swing has
     * created the file chooser window.
     *
     * @param chooser file chooser
     */
    private static void installFileChooserSizing(JFileChooser chooser) {
        if (Boolean.TRUE.equals(chooser.getClientProperty(FILE_CHOOSER_SIZE_LISTENER_PROPERTY))) {
            return;
        }
        chooser.putClientProperty(FILE_CHOOSER_SIZE_LISTENER_PROPERTY, Boolean.TRUE);
        chooser.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && chooser.isShowing()) {
                applyFileChooserWindowSize(chooser);
            }
        });
    }

    /**
     * Applies preferred and minimum sizing to the actual chooser dialog.
     *
     * @param chooser file chooser
     */
    private static void applyFileChooserWindowSize(JFileChooser chooser) {
        Window window = SwingUtilities.getWindowAncestor(chooser);
        if (window == null) {
            return;
        }
        Dimension minimum = new Dimension(FILE_CHOOSER_MINIMUM_SIZE);
        Dimension preferred = new Dimension(FILE_CHOOSER_PREFERRED_SIZE);
        window.setMinimumSize(minimum);
        Dimension current = window.getSize();
        if (current.width < preferred.width || current.height < preferred.height) {
            window.setSize(Math.max(current.width, preferred.width), Math.max(current.height, preferred.height));
            window.validate();
            window.setLocationRelativeTo(window.getOwner());
        }
    }

    /**
     * Keeps chooser rows on the UI font stack even though regular workbench
     * lists use monospace for dense data.
     *
     * @param component root component
     */
    private static void polishFileChooserFonts(Component component) {
        if (component instanceof JList<?> list) {
            list.setFont(Theme.font(13, Font.PLAIN));
            list.setFixedCellHeight(Math.max(27, list.getFixedCellHeight()));
        } else if (component instanceof JTable table) {
            table.setFont(Theme.font(12, Font.PLAIN));
            table.setRowHeight(Math.max(27, table.getRowHeight()));
            if (table.getTableHeader() != null) {
                table.getTableHeader().setFont(Theme.font(11, Font.BOLD));
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                polishFileChooserFonts(child);
            }
        }
    }

    /**
     * Shows a styled confirm dialog.
     *
     * @param owner owner component
     * @param content dialog content
     * @param title dialog title
     * @return JOptionPane result
     */
    public static int showConfirmDialog(Component owner, JComponent content, String title) {
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
    public static void showErrorDialog(Component owner, String title, String message) {
        JOptionPane pane = new JOptionPane(message == null ? title : message, JOptionPane.ERROR_MESSAGE,
                JOptionPane.DEFAULT_OPTION);
        showOptionPane(owner, title, pane);
    }

    /**
     * Applies workbench styling to a component subtree.
     *
     * @param component root component
     */
    public static void styleComponentTree(Component component) {
        ComponentTreeStyler.style(component);
    }

    /**
     * Creates a document listener from a runnable.
     *
     * @param runnable runnable
     * @return listener
     */
    public static DocumentListener changeListener(Runnable runnable) {
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
    public static void onTextChange(Runnable runnable, JTextField... fields) {
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
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(Theme.LINE));
        styleComponentTree(dialog.getContentPane());
        dialog.setVisible(true);
        dialog.dispose();
        Object value = pane.getValue();
        return value instanceof Integer option ? option.intValue() : JOptionPane.CLOSED_OPTION;
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
            return Theme.PANEL_SOLID;
        }
        Color background = jComponent.getBackground();
        return jComponent.isOpaque() && background != null && background.getAlpha() == 255
                ? background
                : Theme.PANEL_SOLID;
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
            setBackground(Theme.BG);
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
     * Scrollable wrapper that centers a single child up to a maximum width
     * while still tracking the viewport width to avoid horizontal scrolling on
     * smaller screens.
     */
    private static final class CenteredViewportPanel extends JPanel implements Scrollable {

        /**
         * Serialization identifier for Swing panel compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Wrapped content component.
         */
        private final JComponent content;

        /**
         * Maximum centered content width.
         */
        private final int maxWidth;

        /**
         * Creates the centered viewport wrapper.
         *
         * @param content content component
         * @param maxWidth maximum centered content width
         */
        CenteredViewportPanel(JComponent content, int maxWidth) {
            super(null);
            this.content = content;
            this.maxWidth = Math.max(320, maxWidth);
            setOpaque(false);
            setBackground(Theme.BG);
            add(content);
        }

        /**
         * Lays out the child at the smaller of the viewport width and the
         * configured content cap.
         */
        @Override
        public void doLayout() {
            Insets insets = getInsets();
            int availableWidth = Math.max(0, getWidth() - insets.left - insets.right);
            int availableHeight = Math.max(0, getHeight() - insets.top - insets.bottom);
            int childWidth = Math.min(maxWidth, availableWidth);
            Dimension preferred = content.getPreferredSize();
            int childHeight = Math.max(availableHeight, preferred.height);
            int childX = insets.left + Math.max(0, (availableWidth - childWidth) / 2);
            content.setBounds(childX, insets.top, childWidth, childHeight);
        }

        /**
         * Returns the preferred wrapper size.
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredSize() {
            Insets insets = getInsets();
            Dimension preferred = content.getPreferredSize();
            return new Dimension(Math.min(maxWidth, preferred.width) + insets.left + insets.right,
                    preferred.height + insets.top + insets.bottom);
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
         * Tracks viewport width to avoid horizontal scrolling.
         *
         * @return true
         */
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        /**
         * Tracks viewport height when content is shorter than the available
         * area.
         *
         * @return true when the wrapper should fill the viewport height
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
    public static double easeOutCubic(double value) {
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
    public static String elide(String text, FontMetrics metrics, int maxWidth) {
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
         * Button corner radius. Aligned with {@link Theme#RADIUS} so buttons
         * match the subtle 3px rounding modern editor apps (VS Code, Cursor,
         * Atom) use on interactive controls. Larger 8px pills looked too
         * "designed"; structural surfaces around them are square so the
         * mismatch felt out of place.
         */
        private static final int RADIUS = Theme.RADIUS;

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
                boolean primary = Boolean.TRUE.equals(getClientProperty(Theme.CLIENT_PRIMARY));
                Color fill = buttonFill(primary);
                Color border = isEnabled() ? Theme.buttonBorder(primary) : Theme.BUTTON_DISABLED_BORDER;
                setForeground(isEnabled()
                        ? primary ? Theme.PRIMARY_BUTTON_TEXT : Theme.SECONDARY_BUTTON_TEXT
                        : Theme.BUTTON_DISABLED_TEXT);
                g.setColor(fill);
                g.fillRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1), RADIUS, RADIUS);
                g.setColor(border);
                g.drawRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1), RADIUS, RADIUS);
                if (isFocusOwner()) {
                    g.setColor(Theme.withAlpha(Theme.INPUT_FOCUS, 90));
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
            boolean primary = Boolean.TRUE.equals(getClientProperty(Theme.CLIENT_PRIMARY));
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
                return Theme.BUTTON_DISABLED_BG;
            }
            if (getModel().isPressed()) {
                return Theme.buttonPressed(primary);
            }
            if (getModel().isRollover()) {
                return Theme.buttonHover(primary);
            }
            return Theme.buttonBackground(primary);
        }

        /**
         * Returns the non-hover button fill for transition starts.
         *
         * @param primary whether the button uses primary styling
         * @return resting fill color
         */
        private Color restingButtonFill(boolean primary) {
            return isEnabled() ? Theme.buttonBackground(primary) : Theme.BUTTON_DISABLED_BG;
        }
    }

    /**
     * Minimal combo-box UI without platform-gray arrow-button artifacts.
     */
    private static final class StyledComboBoxUI extends BasicComboBoxUI {

        /**
         * Paints the full input well before the current value and arrow button
         * are painted, avoiding unfilled platform-background gaps between the
         * value renderer and the chevron area.
         *
         * @param graphics graphics context
         * @param component combo component
         */
        @Override
        public void paint(Graphics graphics, JComponent component) {
            graphics.setColor(comboBox.isEnabled() ? Theme.INPUT : Theme.INPUT_DISABLED);
            graphics.fillRect(1, 1,
                    Math.max(0, component.getWidth() - 2),
                    Math.max(0, component.getHeight() - 2));
            super.paint(graphics, component);
        }

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
            graphics.setColor(comboBox.isEnabled() ? Theme.INPUT : Theme.INPUT_DISABLED);
            graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        /**
         * Paints the selected value with workbench disabled-state colors rather
         * than the platform look-and-feel's default disabled combo colors.
         *
         * @param graphics graphics context
         * @param bounds value bounds
         * @param hasFocus whether the combo has focus
         */
        @Override
        public void paintCurrentValue(Graphics graphics, Rectangle bounds, boolean hasFocus) {
            ListCellRenderer<Object> renderer = comboBox.getRenderer();
            Component component = renderer.getListCellRendererComponent(listBox,
                    comboBox.getSelectedItem(), -1, false, false);
            component.setFont(comboBox.getFont());
            component.setForeground(comboBox.isEnabled() ? Theme.TEXT : Theme.BUTTON_DISABLED_TEXT);
            component.setBackground(comboBox.isEnabled() ? Theme.INPUT : Theme.INPUT_DISABLED);
            currentValuePane.paintComponent(graphics, component, comboBox, bounds.x, bounds.y,
                    bounds.width, bounds.height, true);
        }
    }

    /**
     * Minimal horizontal slider UI for compact settings controls.
     */
    private static final class StyledSliderUI extends BasicSliderUI {

        /**
         * Track height.
         */
        private static final int TRACK_HEIGHT = 4;

        /**
         * Thumb size.
         */
        private static final int THUMB_SIZE = 14;

        /**
         * Creates the slider UI.
         *
         * @param slider target slider
         */
        StyledSliderUI(JSlider slider) {
            super(slider);
        }

        /**
         * Returns the compact thumb size.
         *
         * @return thumb size
         */
        @Override
        protected Dimension getThumbSize() {
            return new Dimension(THUMB_SIZE, THUMB_SIZE);
        }

        /**
         * Paints the flat track and filled range.
         *
         * @param graphics graphics context
         */
        @Override
        public void paintTrack(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int y = trackRect.y + (trackRect.height - TRACK_HEIGHT) / 2;
                int x = trackRect.x;
                int width = trackRect.width;
                int fill = Math.max(0, thumbRect.x + thumbRect.width / 2 - x);
                g.setColor(Theme.LINE);
                g.fillRoundRect(x, y, width, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
                g.setColor(slider.isEnabled() ? Theme.ACCENT : Theme.BUTTON_DISABLED_TEXT);
                g.fillRoundRect(x, y, fill, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
            } finally {
                g.dispose();
            }
        }

        /**
         * Paints the compact circular thumb.
         *
         * @param graphics graphics context
         */
        @Override
        public void paintThumb(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(slider.isEnabled() ? Theme.ACCENT : Theme.BUTTON_DISABLED_TEXT);
                g.fillOval(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height);
                g.setColor(Theme.PANEL_SOLID);
                g.drawOval(thumbRect.x, thumbRect.y, thumbRect.width - 1, thumbRect.height - 1);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Minimal spinner UI with solid arrow buttons.
     */
    private static final class StyledSpinnerUI extends BasicSpinnerUI {

        /**
         * Paints the full input well before the editor and arrow buttons are
         * painted, avoiding unfilled platform-background gaps inside focused
         * spinner borders.
         *
         * @param graphics graphics context
         * @param component spinner component
         */
        @Override
        public void paint(Graphics graphics, JComponent component) {
            graphics.setColor(component.isEnabled() ? Theme.INPUT : Theme.INPUT_DISABLED);
            graphics.fillRect(1, 1,
                    Math.max(0, component.getWidth() - 2),
                    Math.max(0, component.getHeight() - 2));
            super.paint(graphics, component);
        }

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
                g.setColor(isEnabled() ? Theme.INPUT : Theme.INPUT_DISABLED);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(isEnabled() ? Theme.MUTED : Theme.BUTTON_DISABLED_TEXT);
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
         * Combo that owns this renderer, used to mirror disabled state while
         * painting the selected value outside the popup list.
         */
        private final JComboBox<?> owner;

        /**
         * Creates a combo renderer.
         *
         * @param owner owning combo box
         */
        StyledComboRenderer(JComboBox<?> owner) {
            this.owner = owner;
        }

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
            setFont(Theme.font(13, Font.PLAIN));
            boolean enabled = owner == null || owner.isEnabled();
            setForeground(enabled ? Theme.TEXT : Theme.BUTTON_DISABLED_TEXT);
            if (!enabled) {
                setBackground(Theme.INPUT_DISABLED);
            } else {
                setBackground(selected ? Theme.SELECTION_SOLID : Theme.ELEVATED_SOLID);
            }
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
                g.setColor(Theme.SCROLLBAR_TRACK);
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
                g.setColor(isDragging ? Theme.SCROLLBAR_THUMB_HOVER
                        : isThumbRollover() ? Theme.withAlpha(Theme.SCROLLBAR_THUMB, 190)
                        : Theme.SCROLLBAR_THUMB);
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
