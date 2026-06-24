package application.gui.workbench.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/**
 * Small Swing factory and layout helpers used by the workbench.
 */
public final class Ui {

    /**
     * Shared UI animation frame delay.
     */
    static final int ANIMATION_DELAY_MS = 16;

    /**
     * Button fill transition duration.
     */
    static final int BUTTON_TRANSITION_MS = 65;

    /**
     * Prevents instantiation.
     */
    private Ui() {
        // utility
    }

    /**
     * Creates a styled button.
     *
     * @param text text to render or parse
     * @param primary primary style
     * @param listener event listener
     * @return button
     */
    public static JButton button(String text, boolean primary, ActionListener listener) {
        return AppButton.create(text, primary ? Theme.ButtonVariant.PRIMARY : Theme.ButtonVariant.SECONDARY,
                listener);
    }

    /**
     * Creates a styled button with an explicit hierarchy variant.
     *
     * @param text text to render or parse
     * @param variant action hierarchy variant
     * @param listener event listener
     * @return button
     */
    public static JButton button(String text, Theme.ButtonVariant variant, ActionListener listener) {
        return AppButton.create(text, variant, listener);
    }

    /**
     * Creates a destructive action button.
     *
     * @param text text to render or parse
     * @param listener event listener
     * @return button
     */
    public static JButton destructiveButton(String text, ActionListener listener) {
        return AppButton.destructive(text, listener);
    }

    /**
     * Creates a ghost action button.
     *
     * @param text text to render or parse
     * @param listener event listener
     * @return button
     */
    public static JButton ghostButton(String text, ActionListener listener) {
        return AppButton.ghost(text, listener);
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
     * @param listener event listener
     * @return button
     */
    public static JButton iconButton(String label, ActionListener listener) {
        return IconButton.create(label, listener);
    }

    /**
     * Creates a compact transport/utility button that shows a glyph but exposes
     * a separate descriptive tooltip and accessible name.
     *
     * @param glyph visible glyph rendered on the button
     * @param accessibleLabel tooltip and accessible label
     * @param listener optional action listener
     * @return styled compact button showing the glyph
     */
    public static JButton iconButton(String glyph, String accessibleLabel, ActionListener listener) {
        return IconButton.create(glyph, accessibleLabel, listener);
    }

    /**
     * Marks an input control as valid or invalid, driving its themed error
     * border through the shared input chrome instead of ad hoc borders.
     *
     * @param component input control styled through the Workbench input chrome
     * @param invalid whether the control currently holds an invalid value
     */
    public static void setInvalid(JComponent component, boolean invalid) {
        InputChrome.setInvalid(component, invalid);
    }

    /**
     * Creates a flow panel.
     *
     * @param align alignment
     * @return panel
     */
    public static JPanel flow(int align) {
        return UiLayout.flow(align);
    }

    /**
     * Creates a transparent panel for unframed layout composition.
     *
     * @param layout layout manager
     * @return panel
     */
    public static JPanel transparentPanel(LayoutManager layout) {
        return UiLayout.transparentPanel(layout);
    }

    /**
     * Creates an opaque Workbench panel surface.
     *
     * @param layout layout manager
     * @return surface panel
     */
    public static SurfacePanel panel(LayoutManager layout) {
        return new SurfacePanel(layout);
    }

    /**
     * Creates a styled row of buttons.
     *
     * @param align flow alignment
     * @param buttons buttons to add
     * @return button row
     */
    public static JPanel buttonRow(int align, JButton... buttons) {
        return UiLayout.buttonRow(align, buttons);
    }

    /**
     * Creates a styled row of button-like controls. This overload is used when
     * a row mixes normal buttons with custom controls such as {@link HoldButton}.
     *
     * @param align flow alignment
     * @param controls controls to add
     * @return button row
     */
    public static JPanel buttonRow(int align, JComponent... controls) {
        return UiLayout.controlRow(align, controls);
    }

    /**
     * Creates a styled row of arbitrary controls — used when a row mixes plain
     * buttons with custom controls such as a {@link HoldButton}.
     *
     * @param align flow alignment
     * @param controls controls to add
     * @return control row
     */
    public static JPanel controlRow(int align, JComponent... controls) {
        return UiLayout.controlRow(align, controls);
    }

    /**
     * Wraps scroll-pane content so it fills the viewport when there is spare
     * height, while still scrolling normally when content is taller.
     *
     * @param content content component
     * @return viewport-filling wrapper
     */
    public static JComponent fillViewport(JComponent content) {
        return UiLayout.fillViewport(content);
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
        return UiLayout.centeredViewport(content, maxWidth);
    }

    /**
     * Creates a label.
     *
     * @param text text to render or parse
     * @return label
     */
    public static JLabel label(String text) {
        return UiFormControls.label(text);
    }

    /**
     * Returns trimmed text from a field, treating null document text as blank.
     *
     * @param field source field
     * @return trimmed text
     */
    public static String trimmed(JTextField field) {
        return UiFormControls.trimmed(field);
    }

    /**
     * Creates a muted, plain-weight one-line caption that explains what a
     * panel or section is for.
     *
     * @param text caption text
     * @return styled caption label
     */
    public static JLabel caption(String text) {
        return UiFormControls.caption(text);
    }

    /**
     * Creates one compact legend row with a color chip, title, and caption.
     *
     * @param color chip color
     * @param title title text
     * @param detail caption text
     * @return legend row
     */
    public static JComponent legendRow(Color color, String title, String detail) {
        JPanel row = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        row.add(colorChip(color), BorderLayout.WEST);
        JPanel text = transparentPanel(new java.awt.GridLayout(0, 1));
        JLabel label = new JLabel(title);
        label.setFont(Theme.font(12, Font.BOLD));
        Theme.foreground(label, Theme.ForegroundRole.TEXT);
        text.add(label);
        text.add(caption(detail));
        row.add(text, BorderLayout.CENTER);
        return row;
    }

    /**
     * Creates a small rounded color chip for legend rows.
     *
     * @param color chip color
     * @return color chip component
     */
    private static JComponent colorChip(Color color) {
        return colorChip(color, 12);
    }

    /**
     * Creates a small rounded color chip.
     *
     * @param color chip color
     * @param size chip side length
     * @return color chip component
     */
    public static JComponent colorChip(Color color, int size) {
        int side = Math.max(8, size);
        return new JComponent() {
            /**
             * Serialization identifier for Swing compatibility.
             */
            private static final long serialVersionUID = 1L;

            {
                setAlignmentY(Component.CENTER_ALIGNMENT);
            }

            /**
             * Returns the fixed legend-chip size.
             *
             * @return chip dimensions
             */
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(side, side);
            }

            /**
             * Returns the minimum chip size.
             *
             * @return chip dimensions
             */
            @Override
            public Dimension getMinimumSize() {
                return getPreferredSize();
            }

            /**
             * Returns the maximum chip size.
             *
             * @return chip dimensions
             */
            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }

            /**
             * Paints the legend color chip.
             *
             * @param graphics drawing context
             */
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(color == null ? Theme.LINE : color);
                    int inset = Math.max(1, side / 6);
                    int height = Math.max(2, side - inset * 2);
                    g.fillRoundRect(0, inset, side, height, 4, 4);
                } finally {
                    g.dispose();
                }
            }
        };
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
        return UiFormControls.labeledControl(caption, control);
    }

    /**
     * Pairs a fixed-width label with one control in a compact, left-aligned row.
     *
     * @param text label text
     * @param control control component
     * @param labelWidth fixed label width in pixels
     * @return labelled control row
     */
    public static JComponent labelControlRow(String text, JComponent control, int labelWidth) {
        return UiFormControls.labelControlRow(text, control, labelWidth);
    }

    /**
     * Creates a compact field row.
     *
     * @param text label text
     * @param control control component
     * @param labelWidth fixed label width
     * @return field row
     */
    public static JComponent fieldRow(String text, JComponent control, int labelWidth) {
        return new FieldRow(text, control, labelWidth);
    }

    /**
     * Creates one compact form option group.
     *
     * @param text label text
     * @param control option control
     * @return option group
     */
    public static JComponent optionGroup(String text, JComponent control) {
        return UiFormControls.optionGroup(text, control);
    }

    /**
     * Styles a panel as a workbench toolbar band: an opaque {@link Theme#PANEL_SOLID}
     * strip closed by a bottom hairline, with the given inner padding. Shared by
     * the tab toolbars and mode switchers (Dashboard, Network, and the
     * SwitchedWorkspace header) so every band reads identically.
     *
     * @param bar band panel
     * @param padding inner padding inside the hairline border
     */
    public static void styleToolbarBand(JComponent bar, javax.swing.border.Border padding) {
        UiSurfaces.styleToolbarBand(bar, padding);
    }

    /**
     * Creates a compact section header.
     *
     * @param title section title
     * @param detail optional one-line detail
     * @param trailing optional trailing component
     * @return section header
     */
    public static JComponent sectionHeader(String title, String detail, JComponent trailing) {
        return new SectionHeader(title, detail, trailing);
    }

    /**
     * Creates a top-level workspace header.
     *
     * @param title surface or mode title
     * @param context current context summary
     * @param actions optional primary actions
     * @return workspace header
     */
    public static WorkspaceHeader workspaceHeader(String title, String context, JComponent actions) {
        return new WorkspaceHeader(title, context, actions);
    }

    /**
     * Creates a thin vertical divider for separating control groups inside a
     * horizontal toolbar.
     *
     * @return separator component
     */
    public static JComponent toolbarSeparator() {
        return UiLayout.toolbarSeparator();
    }

    /**
     * Creates a styled scroll pane.
     *
     * @param view scrollable view
     * @return scroll pane
     */
    public static JScrollPane scroll(JComponent view) {
        return ScrollPaneStyler.scroll(view);
    }

    /**
     * Creates a styled scroll pane whose viewport adopts a declared embedding
     * surface instead of the default panel fill. Use this when the scroll sits
     * inside a card or elevated row, so the scrolled region matches that surface
     * rather than stamping a darker {@code PANEL_SOLID} box inside it. The
     * supplier is re-evaluated on every theme refresh so the colour tracks the
     * active light/dark palette.
     *
     * @param view scrollable view
     * @param viewportSurface supplier of the surface colour the viewport sits on
     * @return scroll pane
     */
    public static JScrollPane scroll(JComponent view, java.util.function.Supplier<Color> viewportSurface) {
        return ScrollPaneStyler.scroll(view, viewportSurface);
    }

    /**
     * Styles an existing scroll pane and its nested scroll bars.
     *
     * @param pane scroll pane
     */
    public static void styleScrollPane(JScrollPane pane) {
        ScrollPaneStyler.style(pane);
    }

    /**
     * Reapplies scroll-pane colors and custom scroll bars while preserving the
     * caller's outer border.
     *
     * @param pane scroll pane
     */
    public static void refreshScrollPaneTheme(JScrollPane pane) {
        ScrollPaneStyler.refresh(pane);
    }

    /**
     * Styles a tree view with shared Workbench chrome.
     *
     * @param tree tree view
     */
    public static void styleTree(JTree tree) {
        TreeStyler.style(tree);
    }

    /**
     * Creates a flat titled section without adding another card layer.
     *
     * @param title display title
     * @param child child component
     * @return panel
     */
    public static JPanel titled(String title, JComponent child) {
        return UiSurfaces.titled(title, child);
    }

    /**
     * Creates a responsive masonry grid that flows its cards into as many
     * equal-width columns as the window allows (each at least
     * {@code minColumnWidth} wide) and packs them shortest-column-first. This
     * is the shared primitive for surfaces that should use the full desktop
     * canvas instead of a narrow centred column.
     *
     * @param minColumnWidth minimum column width before the grid reflows
     * @return an empty card grid; add cards with {@link JComponent#add}
     */
    public static CardGrid contentGrid(int minColumnWidth) {
        return UiSurfaces.contentGrid(minColumnWidth);
    }

    /**
     * Sets a table column's preferred width when the column exists, ignoring
     * out-of-range indices. Shared so every panel pins column widths the same
     * way instead of carrying its own copy of this guard.
     *
     * @param columns column model
     * @param index column index
     * @param width preferred width
     */
    public static void setColumnWidth(javax.swing.table.TableColumnModel columns, int index, int width) {
        if (columns != null && index >= 0 && index < columns.getColumnCount()) {
            columns.getColumn(index).setPreferredWidth(width);
        }
    }

    /**
     * Wraps body content in a raised elevated card with a header eyebrow. The
     * card paints via {@link Theme#paintElevatedCard} so its surface, border,
     * and shadow stay consistent with the dashboard cards in both themes. Use
     * for static grouped content (chart panels, setup forms) that wants the
     * same surface treatment without the dashboard's hover-lift behaviour.
     *
     * @param title header text, or {@code null} for a headerless card
     * @param body card body component
     * @return elevated card component
     */
    public static JComponent card(String title, JComponent body) {
        return UiSurfaces.card(title, body);
    }

    /**
     * Wraps body content in a raised elevated card with a header eyebrow and an
     * optional trailing affordance (count, status dot, action).
     *
     * @param title header text, or {@code null} for a headerless card
     * @param trailing optional right-aligned header component, or {@code null}
     * @param body card body component
     * @return elevated card component
     */
    public static JComponent card(String title, JComponent trailing, JComponent body) {
        return UiSurfaces.card(title, trailing, body);
    }

    /**
     * Builds a centred empty-state block: a quiet title, a one-line hint, and an
     * optional row of actions. Centralised so every "nothing here yet" surface
     * (empty datasets, blank batch input, idle play tab) reads the same instead
     * of each panel inventing its own placeholder text.
     *
     * @param title short title, e.g. "No dataset loaded"
     * @param hint one-line explanation of how to proceed
     * @param actions optional action buttons shown beneath the hint
     * @return centred empty-state component
     */
    public static JComponent emptyState(String title, String hint, JButton... actions) {
        return new EmptyState(title, hint, actions);
    }

    /**
     * Creates a read-only monospace command/code preview.
     *
     * @param text preview text
     * @return command block
     */
    public static CommandBlock commandBlock(String text) {
        return new CommandBlock(text);
    }

    /**
     * Paints a centred empty-state directly onto a graphics context, for
     * custom-painted surfaces (charts, graphs) that cannot host a child
     * component. Draws a quiet bold title with a muted hint beneath it.
     *
     * @param g graphics context (a scratch copy is used internally)
     * @param bounds area to centre within
     * @param title short title
     * @param hint one-line hint, or {@code null}
     */
    public static void paintEmptyState(Graphics2D g, Rectangle bounds, String title, String hint) {
        EmptyState.paint(g, bounds, title, hint);
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
        return UiSurfaces.collapsible(title, content, expanded);
    }

    /**
     * Creates an inline collapsible information section whose expanded content is
     * capped and scrolls internally once it grows past the cap.
     *
     * @param title section title
     * @param content collapsible content
     * @param expanded initial expansion state
     * @param maxExpandedHeight maximum height for expanded content
     * @return collapsible section
     */
    public static JComponent collapsible(String title, JComponent content, boolean expanded, int maxExpandedHeight) {
        return UiSurfaces.collapsible(title, content, expanded, maxExpandedHeight);
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
        return UiSurfaces.setCollapsibleExpanded(component, expanded);
    }

    /**
     * Adds a component to a grid bag panel.
     *
     * @param panel Swing panel
     * @param component Swing component
     * @param c constraints
     * @param x x-coordinate
     * @param y y-coordinate
     * @param width width in pixels
     * @param height height in pixels
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
        c.insets = new Insets(Theme.SPACE_XS, Theme.SPACE_XS, Theme.SPACE_XS, Theme.SPACE_XS);
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    /**
     * Styles a tabbed pane as a compact solid tab strip.
     *
     * @param tabs tabbed pane
     */
    public static void styleTabs(JTabbedPane tabs) {
        Tabs.style(tabs);
    }

    /**
     * Creates a styled tabbed pane.
     *
     * @return tabbed pane
     */
    public static JTabbedPane tabbedPane() {
        return Tabs.create();
    }

    /**
     * Creates a segmented control.
     *
     * @param labels segment labels
     * @return segmented control
     */
    public static SegmentedSwitcher segmentedControl(String... labels) {
        return new SegmentedSwitcher(labels);
    }

    /**
     * Applies shared workbench chrome to a popup menu and its current children.
     *
     * @param menu popup menu
     */
    public static void stylePopupMenu(JPopupMenu menu) {
        PopupMenuStyler.styleMenu(menu);
    }

    /**
     * Applies shared workbench chrome to one popup menu item.
     *
     * @param item menu item
     */
    public static void stylePopupMenuItem(JMenuItem item) {
        PopupMenuStyler.styleItem(item);
    }

    /**
     * Styles a combo box.
     *
     * @param combo combo box
     */
    public static void styleCombo(JComboBox<?> combo) {
        ControlStyler.styleCombo(combo);
    }

    /**
     * Styles multiple combo boxes.
     *
     * @param combos combo boxes
     */
    public static void styleCombos(JComboBox<?>... combos) {
        ControlStyler.styleCombos(combos);
    }

    /**
     * Styles a spinner and its text editor.
     *
     * @param spinner spinner component
     */
    public static void styleSpinner(JSpinner spinner) {
        ControlStyler.styleSpinner(spinner);
    }

    /**
     * Styles a spinner whose editor should accept only integer values.
     *
     * @param spinner spinner component
     */
    public static void styleIntegerSpinner(JSpinner spinner) {
        ControlStyler.styleIntegerSpinner(spinner);
    }

    /**
     * Styles the text field embedded inside a spinner without adding another
     * nested input border.
     *
     * @param editor spinner editor
     */
    static void styleSpinnerEditor(JSpinner.DefaultEditor editor) {
        ControlStyler.styleSpinnerEditor(editor);
    }

    /**
     * Styles a compact horizontal slider with workbench palette colors.
     *
     * @param slider slider to style
     */
    public static void styleSlider(JSlider slider) {
        ControlStyler.styleSlider(slider);
    }

    /**
     * Styles multiple spinners.
     *
     * @param spinners spinner components
     */
    public static void styleSpinners(JSpinner... spinners) {
        ControlStyler.styleSpinners(spinners);
    }

    /**
     * Styles multiple text fields.
     *
     * @param fields text fields
     */
    public static void styleFields(JTextField... fields) {
        ControlStyler.styleFields(fields);
    }

    /**
     * Styles multiple text areas.
     *
     * @param areas text areas
     */
    public static void styleAreas(JTextArea... areas) {
        ControlStyler.styleAreas(areas);
    }

    /**
     * Installs empty-field placeholder text without changing the component value.
     *
     * @param component text component
     * @param text placeholder copy
     */
    public static void placeholder(JTextComponent component, String text) {
        ControlStyler.placeholder(component, text);
    }

    /**
     * Styles a checkbox that is not using the custom workbench toggle class.
     *
     * @param box checkbox
     */
    public static void styleCheckBox(JCheckBox box) {
        ControlStyler.styleCheckBox(box);
    }

    /**
     * Styles a compact progress bar.
     *
     * @param bar progress bar
     */
    public static void styleProgressBar(JProgressBar bar) {
        ControlStyler.styleProgressBar(bar);
    }

    /**
     * Styles a file chooser before it is shown.
     *
     * @param chooser file chooser
     */
    public static void styleFileChooser(JFileChooser chooser) {
        FileChooserStyler.style(chooser);
    }

    /**
     * Shows a styled confirm dialog.
     *
     * @param owner owner component
     * @param content dialog content
     * @param title dialog title
     * @return JOptionPane
     */
    public static int showConfirmDialog(Component owner, JComponent content, String title) {
        return OptionPaneStyler.showConfirmDialog(owner, content, title);
    }

    /**
     * Shows a styled error dialog.
     *
     * @param owner owner component
     * @param title dialog title
     * @param message dialog message
     */
    public static void showErrorDialog(Component owner, String title, String message) {
        OptionPaneStyler.showErrorDialog(owner, title, message);
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
     * @param runnable action to execute
     * @return listener
     */
    public static DocumentListener changeListener(Runnable runnable) {
        return DocumentChangeSupport.changeListener(runnable);
    }

    /**
     * Adds a shared document-change callback to multiple text fields.
     *
     * @param runnable callback
     * @param fields text fields
     */
    public static void onTextChange(Runnable runnable, JTextField... fields) {
        DocumentChangeSupport.onTextChange(runnable, fields);
    }

    /**
     * Applies an ease-out cubic animation curve.
     *
     * @param value linear progress
     * @return eased progress
     */
    public static double easeOutCubic(double value) {
        return UiMotion.easeOutCubic(value);
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
        return UiText.elide(text, metrics, maxWidth);
    }

}
