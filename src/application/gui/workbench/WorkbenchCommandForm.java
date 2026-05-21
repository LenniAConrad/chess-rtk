package application.gui.workbench;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import application.gui.workbench.WorkbenchCommandTemplates.CommandOption;
import application.gui.workbench.WorkbenchCommandTemplates.CommandTemplate;
import application.gui.workbench.WorkbenchCommandTemplates.TemplateContext;
import application.gui.workbench.WorkbenchCommandTemplates.ValueSource;

/**
 * Structured command-builder form. Replaces the flat flag table with a layout
 * that mirrors the Batch runner: required inputs come first as plain labelled
 * fields, mutually-exclusive flags are packaged into radio groups so only one
 * can ever be picked, and the remaining optional flags sit below behind a
 * filter. The command preview updates through a single change listener.
 */
final class WorkbenchCommandForm extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Radio-group key used for standalone (non-exclusive) options.
     */
    private static final String NO_GROUP = "";

    /**
     * Listener invoked whenever the built command changes.
     */
    private transient Runnable changeListener = () -> {
        // no-op until wired
    };

    /**
     * Mutable per-option state, in template order.
     */
    private final transient List<Field> fields = new ArrayList<>();

    /**
     * Scrollable body holding the required and optional sections.
     */
    private final JPanel body = new JPanel();

    /**
     * Filter box that narrows the optional section.
     */
    private final JTextField filterField = new JTextField();

    /**
     * Optional-section rows, kept for live filtering.
     */
    private final transient List<FilterRow> optionalRows = new ArrayList<>();

    /**
     * Suppresses change events while the form is being rebuilt.
     */
    private transient boolean rebuilding;

    /**
     * Creates an empty command form.
     */
    WorkbenchCommandForm() {
        super(new BorderLayout());
        setOpaque(false);
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(WorkbenchTheme.pad(WorkbenchTheme.SPACE_XS));
        add(WorkbenchUi.scroll(WorkbenchUi.fillViewport(body)), BorderLayout.CENTER);
        WorkbenchUi.styleFields(filterField);
        WorkbenchUi.placeholder(filterField, "filter optional flags");
        WorkbenchUi.onTextChange(this::applyFilter, filterField);
    }

    /**
     * Sets the change listener invoked when the built command changes.
     *
     * @param listener change callback
     */
    void setChangeListener(Runnable listener) {
        changeListener = listener == null ? () -> {
            // no-op
        } : listener;
    }

    /**
     * Returns the optional-flag filter field so the host window can focus it.
     *
     * @return filter field
     */
    JTextField filterField() {
        return filterField;
    }

    /**
     * Rebuilds the form for a command template.
     *
     * @param template selected command template
     * @param context current workbench context
     */
    void setTemplate(CommandTemplate template, TemplateContext context) {
        rebuilding = true;
        fields.clear();
        optionalRows.clear();
        body.removeAll();
        if (template != null) {
            for (CommandOption option : template.options()) {
                fields.add(new Field(option, option.enabledByDefault(), option.initialValue(context)));
            }
            buildSections();
        }
        rebuilding = false;
        body.revalidate();
        body.repaint();
        fireChange();
    }

    /**
     * Restores every option to its template default.
     *
     * @param context current workbench context
     */
    void resetDefaults(TemplateContext context) {
        for (Field field : fields) {
            field.enabled = field.option.enabledByDefault();
            field.value = field.option.initialValue(context);
        }
        syncControls();
    }

    /**
     * Turns off every optional flag, keeping required defaults.
     */
    void clearOptional() {
        for (Field field : fields) {
            field.enabled = field.option.enabledByDefault();
        }
        syncControls();
    }

    /**
     * Refreshes dynamic values (current FEN, depth, threads, …) that the user
     * has not manually overridden.
     *
     * @param context current workbench context
     */
    void refreshDynamicValues(TemplateContext context) {
        boolean changed = false;
        for (Field field : fields) {
            if (field.option.source() == ValueSource.STATIC) {
                continue;
            }
            // Leave a field the user is actively editing alone.
            if (field.valueField != null && field.valueField.isFocusOwner()) {
                continue;
            }
            String fresh = field.option.initialValue(context);
            if (!fresh.equals(field.value)) {
                field.value = fresh;
                changed = true;
            }
        }
        if (changed) {
            syncControls();
        }
    }

    /**
     * Builds the enabled command arguments in template order.
     *
     * @return command arguments
     */
    List<String> args() {
        List<String> args = new ArrayList<>();
        for (Field field : fields) {
            if (!field.enabled) {
                continue;
            }
            String value = field.value == null ? "" : field.value.trim();
            if (field.option.takesValue() && value.isEmpty()) {
                continue;
            }
            if (!field.option.flag().isBlank()) {
                args.add(field.option.flag());
            }
            if (field.option.takesValue()) {
                args.add(value);
            }
        }
        return List.copyOf(args);
    }

    // ------------------------------------------------------------------
    // Section construction
    // ------------------------------------------------------------------

    /**
     * Lays out the required and optional sections from the current fields.
     */
    private void buildSections() {
        List<Block> blocks = groupIntoBlocks();
        List<Block> required = new ArrayList<>();
        List<Block> optional = new ArrayList<>();
        for (Block block : blocks) {
            (block.required() ? required : optional).add(block);
        }
        if (!required.isEmpty()) {
            body.add(sectionHeader("Required", WorkbenchTheme.ACCENT));
            for (Block block : required) {
                body.add(renderBlock(block, false));
            }
        }
        if (!optional.isEmpty()) {
            body.add(Box.createVerticalStrut(WorkbenchTheme.SPACE_MD));
            body.add(sectionHeader("Optional flags", WorkbenchTheme.MUTED));
            JPanel filterRow = new JPanel(new BorderLayout(WorkbenchTheme.SPACE_SM, 0));
            filterRow.setOpaque(false);
            filterRow.setAlignmentX(LEFT_ALIGNMENT);
            filterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, WorkbenchTheme.CONTROL_HEIGHT));
            filterRow.add(filterField, BorderLayout.CENTER);
            body.add(filterRow);
            body.add(Box.createVerticalStrut(WorkbenchTheme.SPACE_XS));
            for (Block block : optional) {
                JComponent rendered = renderBlock(block, true);
                body.add(rendered);
                optionalRows.add(new FilterRow(rendered, block.searchText()));
            }
        }
        body.add(Box.createVerticalGlue());
        applyFilter();
    }

    /**
     * Groups fields into render blocks: one block per exclusive group, one per
     * standalone option, all in first-appearance order.
     *
     * @return ordered render blocks
     */
    private List<Block> groupIntoBlocks() {
        List<Block> blocks = new ArrayList<>();
        Map<String, Block> groups = new LinkedHashMap<>();
        for (Field field : fields) {
            String group = field.option.exclusiveGroup();
            if (group == null || group.isBlank()) {
                blocks.add(new Block(NO_GROUP, new ArrayList<>(List.of(field))));
                continue;
            }
            Block block = groups.get(group);
            if (block == null) {
                block = new Block(group, new ArrayList<>());
                groups.put(group, block);
                blocks.add(block);
            }
            block.members().add(field);
        }
        return blocks;
    }

    /**
     * Renders one block — either a radio group or a single option row.
     *
     * @param block block to render
     * @param optionalSection true when rendered under the optional section
     * @return rendered component
     */
    private JComponent renderBlock(Block block, boolean optionalSection) {
        if (block.isGroup()) {
            return renderRadioGroup(block, optionalSection);
        }
        return renderSingle(block.members().get(0), optionalSection);
    }

    /**
     * Renders a mutually-exclusive radio group.
     *
     * @param block exclusive group block
     * @param optionalSection true when the group is optional
     * @return rendered group
     */
    private JComponent renderRadioGroup(Block block, boolean optionalSection) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setBackground(WorkbenchTheme.ELEVATED_SOLID);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(WorkbenchTheme.LINE),
                WorkbenchTheme.pad(WorkbenchTheme.SPACE_SM)));
        JLabel title = new JLabel("choose one · " + block.group());
        title.setFont(WorkbenchTheme.font(11, Font.BOLD));
        title.setForeground(WorkbenchTheme.MUTED);
        title.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(WorkbenchTheme.SPACE_XS));

        ButtonGroup buttons = new ButtonGroup();
        boolean anySelected = false;
        for (Field field : block.members()) {
            JRadioButton radio = themedRadio(displayFlag(field.option));
            field.radio = radio;
            buttons.add(radio);
            if (field.enabled && !anySelected) {
                radio.setSelected(true);
                anySelected = true;
            }
            radio.addActionListener(event -> selectRadio(block, field));
            panel.add(buildOptionRow(radio, field, true));
        }
        if (optionalSection) {
            // Optional groups gain an explicit "none" choice.
            JRadioButton none = themedRadio("none");
            buttons.add(none);
            if (!anySelected) {
                none.setSelected(true);
            }
            none.addActionListener(event -> selectRadio(block, null));
            JPanel noneRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            noneRow.setOpaque(false);
            noneRow.setAlignmentX(LEFT_ALIGNMENT);
            noneRow.add(none);
            panel.add(noneRow);
        } else if (!anySelected && !block.members().isEmpty()) {
            // A required group always keeps one member active.
            Field first = block.members().get(0);
            first.enabled = true;
            first.radio.setSelected(true);
        }
        // Cap the height so the vertical BoxLayout does not stretch the group.
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    /**
     * Renders a standalone option as a labelled field or a toggle row.
     *
     * @param field option field
     * @param optionalSection true when rendered as optional
     * @return rendered row
     */
    private JComponent renderSingle(Field field, boolean optionalSection) {
        if (!optionalSection) {
            // Required standalone option: always-on, shown as a plain input.
            field.enabled = true;
            JPanel row = new JPanel(new BorderLayout(WorkbenchTheme.SPACE_SM, 0));
            row.setOpaque(false);
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, WorkbenchTheme.CONTROL_HEIGHT + 6));
            JLabel name = new JLabel(displayFlag(field.option));
            name.setFont(WorkbenchTheme.font(12, Font.BOLD));
            name.setForeground(WorkbenchTheme.TEXT);
            name.setPreferredSize(new Dimension(150, WorkbenchTheme.CONTROL_HEIGHT));
            row.add(name, BorderLayout.WEST);
            if (field.option.takesValue()) {
                row.add(valueField(field), BorderLayout.CENTER);
            } else {
                JLabel on = new JLabel("included");
                on.setFont(WorkbenchTheme.font(11, Font.PLAIN));
                on.setForeground(WorkbenchTheme.MUTED);
                row.add(on, BorderLayout.CENTER);
            }
            row.add(descriptionLabel(field.option), BorderLayout.EAST);
            return row;
        }
        // Optional standalone option: toggle plus optional value.
        WorkbenchToggleBox toggle = new WorkbenchToggleBox(displayFlag(field.option), true);
        toggle.setSelected(field.enabled);
        field.toggle = toggle;
        toggle.addActionListener(event -> {
            field.enabled = toggle.isSelected();
            applyConflicts(field);
            fireChange();
        });
        return buildOptionRow(toggle, field, false);
    }

    /**
     * Builds a row carrying a toggle/radio control, an optional value field,
     * and the option description.
     *
     * @param control leading control (radio or toggle)
     * @param field option field
     * @param insetForGroup true when nested inside a radio group
     * @return rendered row
     */
    private JComponent buildOptionRow(JComponent control, Field field, boolean insetForGroup) {
        JPanel row = new JPanel(new BorderLayout(WorkbenchTheme.SPACE_SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, WorkbenchTheme.CONTROL_HEIGHT + 6));
        JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        west.setOpaque(false);
        west.add(control);
        row.add(west, BorderLayout.WEST);
        if (field.option.takesValue()) {
            row.add(valueField(field), BorderLayout.CENTER);
        }
        row.add(descriptionLabel(field.option), BorderLayout.EAST);
        return row;
    }

    /**
     * Creates the value editor for an option.
     *
     * @param field option field
     * @return styled value field
     */
    private JTextField valueField(Field field) {
        JTextField text = new JTextField(field.value == null ? "" : field.value);
        WorkbenchUi.styleFields(text);
        text.setColumns(18);
        field.valueField = text;
        WorkbenchUi.onTextChange(() -> {
            field.value = text.getText();
            // Typing a value for an optional flag implies enabling it.
            if (!field.enabled && !field.value.isBlank() && field.option.exclusiveGroup().isBlank()
                    && field.toggle != null) {
                field.enabled = true;
                field.toggle.setSelected(true);
            }
            fireChange();
        }, text);
        return text;
    }

    /**
     * Creates the muted description label shown at the row's trailing edge.
     *
     * @param option option metadata
     * @return description label
     */
    private static JLabel descriptionLabel(CommandOption option) {
        JLabel label = new JLabel(option.description());
        label.setFont(WorkbenchTheme.font(11, Font.PLAIN));
        label.setForeground(WorkbenchTheme.MUTED);
        label.setBorder(WorkbenchTheme.pad(0, WorkbenchTheme.SPACE_SM, 0, 0));
        return label;
    }

    /**
     * Creates a theme-coloured radio button.
     *
     * @param text button label
     * @return styled radio button
     */
    private static JRadioButton themedRadio(String text) {
        JRadioButton radio = new JRadioButton(text);
        radio.setOpaque(false);
        radio.setForeground(WorkbenchTheme.TEXT);
        radio.setFont(WorkbenchTheme.font(12, Font.PLAIN));
        radio.setFocusPainted(false);
        return radio;
    }

    /**
     * Builds a section header label.
     *
     * @param text header text
     * @param accent accent colour
     * @return header component
     */
    private static JComponent sectionHeader(String text, Color accent) {
        JLabel label = new JLabel(text.toUpperCase(Locale.ROOT));
        label.setFont(WorkbenchTheme.font(11, Font.BOLD));
        label.setForeground(accent);
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setBorder(WorkbenchTheme.pad(WorkbenchTheme.SPACE_XS, 0, WorkbenchTheme.SPACE_XS, 0));
        return label;
    }

    // ------------------------------------------------------------------
    // State changes
    // ------------------------------------------------------------------

    /**
     * Applies a radio selection inside an exclusive group.
     *
     * @param block exclusive group
     * @param chosen chosen field, or {@code null} for the "none" choice
     */
    private void selectRadio(Block block, Field chosen) {
        for (Field field : block.members()) {
            field.enabled = field == chosen;
        }
        fireChange();
    }

    /**
     * Disables flags that explicitly conflict with a newly-enabled option.
     *
     * @param enabledField field that was just enabled
     */
    private void applyConflicts(Field enabledField) {
        if (!enabledField.enabled) {
            return;
        }
        for (Field other : fields) {
            if (other == enabledField || !other.enabled) {
                continue;
            }
            if (enabledField.option.conflictsWith(other.option)) {
                other.enabled = false;
                if (other.toggle != null) {
                    other.toggle.setSelected(false);
                }
            }
        }
    }

    /**
     * Pushes the current field state back onto the Swing controls.
     */
    private void syncControls() {
        rebuilding = true;
        for (Field field : fields) {
            if (field.toggle != null) {
                field.toggle.setSelected(field.enabled);
            }
            if (field.radio != null) {
                field.radio.setSelected(field.enabled);
            }
            if (field.valueField != null && !field.valueField.isFocusOwner()) {
                field.valueField.setText(field.value == null ? "" : field.value);
            }
        }
        rebuilding = false;
        fireChange();
    }

    /**
     * Filters the optional rows against the filter field.
     */
    private void applyFilter() {
        String query = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase(Locale.ROOT);
        for (FilterRow row : optionalRows) {
            row.component().setVisible(query.isEmpty() || row.text().contains(query));
        }
        body.revalidate();
        body.repaint();
    }

    /**
     * Notifies the change listener unless the form is mid-rebuild.
     */
    private void fireChange() {
        if (!rebuilding) {
            changeListener.run();
        }
    }

    /**
     * Returns an option's user-facing label.
     *
     * @param option option metadata
     * @return display label
     */
    private static String displayFlag(CommandOption option) {
        return option.flag().isBlank() ? "argument" : option.flag();
    }

    // ------------------------------------------------------------------
    // Data holders
    // ------------------------------------------------------------------

    /**
     * Mutable per-option UI state.
     */
    private static final class Field {

        /**
         * Option metadata.
         */
        private final CommandOption option;

        /**
         * Whether the option is currently active.
         */
        private boolean enabled;

        /**
         * Current option value.
         */
        private String value;

        /**
         * Toggle control, when rendered as an optional flag.
         */
        private WorkbenchToggleBox toggle;

        /**
         * Radio control, when part of an exclusive group.
         */
        private JRadioButton radio;

        /**
         * Value editor, when the option takes a value.
         */
        private JTextField valueField;

        /**
         * Creates a field.
         *
         * @param option option metadata
         * @param enabled initial enabled state
         * @param value initial value
         */
        Field(CommandOption option, boolean enabled, String value) {
            this.option = option;
            this.enabled = enabled;
            this.value = value == null ? "" : value;
        }
    }

    /**
     * A render block: an exclusive group or a single standalone option.
     */
    private static final class Block {

        /**
         * Exclusive group name, or {@link #NO_GROUP} for standalone options.
         */
        private final String group;

        /**
         * Member fields.
         */
        private final List<Field> members;

        /**
         * Creates a block.
         *
         * @param group exclusive group name
         * @param members member fields
         */
        Block(String group, List<Field> members) {
            this.group = group;
            this.members = members;
        }

        /**
         * Returns the exclusive group name.
         *
         * @return group name
         */
        String group() {
            return group;
        }

        /**
         * Returns the member fields.
         *
         * @return member fields
         */
        List<Field> members() {
            return members;
        }

        /**
         * Returns whether this block is an exclusive group.
         *
         * @return true for a multi-option exclusive group
         */
        boolean isGroup() {
            return !group.isBlank();
        }

        /**
         * Returns whether this block belongs in the required section — a group
         * with a default member, or a standalone option enabled by default.
         *
         * @return true when required
         */
        boolean required() {
            for (Field field : members) {
                if (field.option.enabledByDefault()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns lowercased search text for optional-row filtering.
         *
         * @return search text
         */
        String searchText() {
            StringBuilder text = new StringBuilder(group);
            for (Field field : members) {
                text.append(' ').append(field.option.flag())
                        .append(' ').append(field.option.description());
            }
            return text.toString().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * An optional row paired with its search text for filtering.
     *
     * @param component rendered row
     * @param text lowercased search text
     */
    private record FilterRow(JComponent component, String text) {
    }
}
