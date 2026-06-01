package application.gui.workbench.command;

import application.gui.workbench.command.CommandTemplates.CommandOption;
import application.gui.workbench.command.CommandTemplates.CommandTemplate;
import application.gui.workbench.command.CommandTemplates.TemplateContext;
import application.gui.workbench.command.CommandTemplates.ValueSource;
import application.gui.workbench.ui.ChipGroup;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import chess.core.Position;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import utility.CommandLine;

/**
 * Structured command-builder form. Replaces the flat flag table with a layout
 * that mirrors the Batch runner: required inputs come first as plain labelled
 * fields, mutually-exclusive flags are packaged into radio groups so only one
 * can ever be picked, and the remaining optional flags sit below behind a
 * filter.
 *
 * <p>Every row shares one column grid so the controls and value editors line
 * up. Option descriptions stay available as tooltips instead of repeated
 * helper copy. Options with a small fixed set of values render as a dropdown
 * rather than a free-text field, and a FEN field is validated live — an invalid
 * command disables Run through the {@linkplain #setRunGate run gate}.</p>
 */
public final class CommandForm extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Radio-group key used for standalone (non-exclusive) options.
     */
    private static final String NO_GROUP = "";

    /**
     * Fixed width of the leading control column, so value editors in every row
     * start at the same x.
     */
    private static final int LEAD_WIDTH = 248;

    /**
     * Fixed width of the value-editor column.
     */
    private static final int VALUE_WIDTH = 280;

    /**
     * Wider value-editor width for long position strings.
     */
    private static final int FEN_VALUE_WIDTH = 520;

    /**
     * Row height.
     */
    private static final int ROW_HEIGHT = Theme.CONTROL_HEIGHT + 6;

    /**
     * Horizontal gap between optional-flag columns.
     */
    private static final int FLAG_COLUMN_GAP = Theme.SPACE_LG;

    /**
     * Vertical gap between optional-flag rows.
     */
    private static final int FLAG_ROW_GAP = Theme.SPACE_XS;

    /**
     * Listener invoked whenever the built command changes.
     */
    private transient Runnable changeListener = () -> {
        // no-op until wired
    };

    /**
     * Callback invoked with the command's runnable validity.
     */
    private transient Consumer<Boolean> runGate = ready -> {
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
     * Optional flags disclosure for command templates that expose advanced
     * switches.
     */
    private JComponent optionalDisclosure;

    /**
     * Suppresses change events while the form is being rebuilt.
     */
    private transient boolean rebuilding;

    /**
     * Creates an empty command form.
     */
    public CommandForm() {
        super(new BorderLayout());
        setOpaque(false);
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(Theme.pad(Theme.SPACE_XS));
        add(Ui.scroll(Ui.fillViewport(body)), BorderLayout.CENTER);
        Ui.styleFields(filterField);
        Ui.placeholder(filterField, "filter flags");
        Ui.onTextChange(this::applyFilter, filterField);
    }

    /**
     * Sets the change listener invoked when the built command changes.
     *
     * @param listener change callback
     */
    public void setChangeListener(Runnable listener) {
        changeListener = listener == null ? () -> {
            // no-op
        } : listener;
    }

    /**
     * Sets the run-gate callback, invoked with the command's validity whenever
     * the form changes.
     *
     * @param gate validity callback
     */
    public void setRunGate(Consumer<Boolean> gate) {
        runGate = gate == null ? ready -> {
            // no-op
        } : gate;
    }

    /**
     * Returns the optional-flag filter field so the host window can focus it.
     *
     * @return filter field
     */
    public JTextField filterField() {
        return filterField;
    }

    /**
     * Expands the optional flags section when it exists.
     */
    public void expandOptionalFlags() {
        if (optionalDisclosure != null) {
            Ui.setCollapsibleExpanded(optionalDisclosure, true);
        }
    }

    /**
     * Rebuilds the form for a command template.
     *
     * @param template selected command template
     * @param context current workbench context
     */
    public void setTemplate(CommandTemplate template, TemplateContext context) {
        rebuilding = true;
        fields.clear();
        optionalRows.clear();
        optionalDisclosure = null;
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
    public void resetDefaults(TemplateContext context) {
        for (Field field : fields) {
            field.enabled = field.option.enabledByDefault();
            setDynamicDefault(field, field.option.initialValue(context));
        }
        syncControls();
    }

    /**
     * Turns off every optional flag, keeping required defaults.
     */
    public void clearOptional() {
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
    public void refreshDynamicValues(TemplateContext context) {
        boolean changed = false;
        for (Field field : fields) {
            if (field.option.source() == ValueSource.STATIC) {
                continue;
            }
            String fresh = field.option.initialValue(context);
            if (field.editor != null && field.editor.isFocusOwner()) {
                field.dynamicValue = fresh;
                if (valuesEqual(field.value, fresh)) {
                    field.manuallyOverridden = false;
                }
                continue;
            }
            if (field.manuallyOverridden) {
                field.dynamicValue = fresh;
                if (valuesEqual(field.value, fresh)) {
                    field.manuallyOverridden = false;
                }
                continue;
            }
            if (!fresh.equals(field.value)) {
                setDynamicDefault(field, fresh);
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
    public List<String> args() {
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
                if (field.option.splitValue()) {
                    args.addAll(CommandLine.split(value));
                } else {
                    args.add(value);
                }
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
            // Required fields lead the form by position; an explicit "Required"
            // heading reads as redundant and was removed by deliberate UX
            // decision (see WorkbenchCommandRegression).
            for (Block block : required) {
                body.add(renderBlock(block, false));
                body.add(Box.createVerticalStrut(Theme.SPACE_XS));
            }
        }
        if (!optional.isEmpty()) {
            body.add(Box.createVerticalStrut(Theme.SPACE_SM));
            JPanel optionalPanel = new JPanel();
            optionalPanel.setOpaque(false);
            optionalPanel.setLayout(new BoxLayout(optionalPanel, BoxLayout.Y_AXIS));
            optionalPanel.setAlignmentX(LEFT_ALIGNMENT);
            JPanel filterRow = new JPanel(new BorderLayout(Theme.SPACE_SM, 0));
            filterRow.setOpaque(false);
            filterRow.setAlignmentX(LEFT_ALIGNMENT);
            filterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.CONTROL_HEIGHT));
            filterRow.add(filterField, BorderLayout.CENTER);
            optionalPanel.add(filterRow);
            optionalPanel.add(Box.createVerticalStrut(Theme.SPACE_XS));
            JPanel flagGrid = new FlagGridPanel();
            flagGrid.setOpaque(false);
            flagGrid.setAlignmentX(LEFT_ALIGNMENT);
            for (Block block : optional) {
                JComponent rendered = renderBlock(block, true);
                flagGrid.add(rendered);
                optionalRows.add(new FilterRow(rendered, block.searchText()));
            }
            optionalPanel.add(flagGrid);
            optionalDisclosure = Ui.collapsible("Flags", optionalPanel, false);
            body.add(optionalDisclosure);
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
     * Renders one block — either a chip group or a single option row.
     *
     * @param block block to render
     * @param optionalSection true when rendered under the optional section
     * @return rendered component
     */
    private JComponent renderBlock(Block block, boolean optionalSection) {
        if (block.isGroup()) {
            return renderChipGroup(block, optionalSection);
        }
        return renderSingle(block.members().get(0), optionalSection);
    }

    /**
     * Renders a mutually-exclusive group as an animated chip selector with a
     * value editor that swaps to the chosen member.
     *
     * @param block exclusive group block
     * @param optionalSection true when the group is optional
     * @return rendered group
     */
    private JComponent renderChipGroup(Block block, boolean optionalSection) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setOpaque(false);
        // Padding-only border; the previous bottom divider line stacked with
        // section headers and other group dividers, adding chrome noise.
        // Spacing alone gives the chip group enough visual separation.
        panel.setBorder(Theme.pad(Theme.SPACE_XS, 0, Theme.SPACE_SM, 0));
        JLabel title = new JLabel(block.group());
        title.setFont(Theme.font(11, Font.BOLD));
        title.setForeground(Theme.MUTED);
        title.setAlignmentX(LEFT_ALIGNMENT);

        List<Field> members = block.members();
        boolean hasNone = optionalSection;
        List<String> chipLabels = new ArrayList<>();
        if (hasNone) {
            chipLabels.add("default");
        }
        for (Field field : members) {
            chipLabels.add(displayFlag(field.option));
        }

        boolean hasDetail = members.stream().anyMatch(CommandForm::needsValueEditor);
        java.awt.CardLayout cards = new java.awt.CardLayout();
        JPanel detail = new JPanel(cards);
        detail.setOpaque(false);
        detail.setAlignmentX(LEFT_ALIGNMENT);
        detail.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
        if (hasDetail) {
            if (hasNone) {
                detail.add(memberCard(null), "0");
            }
            for (int m = 0; m < members.size(); m++) {
                detail.add(memberCard(members.get(m)), Integer.toString(hasNone ? m + 1 : m));
            }
        }

        int initial = 0;
        for (int m = 0; m < members.size(); m++) {
            if (members.get(m).enabled) {
                initial = hasNone ? m + 1 : m;
                break;
            }
        }
        if (!hasNone) {
            // A required group always keeps exactly one member active.
            for (int m = 0; m < members.size(); m++) {
                members.get(m).enabled = m == initial;
            }
        }
        if (hasDetail) {
            cards.show(detail, Integer.toString(initial));
        }

        ChipGroup chips = new ChipGroup(chipLabels);
        chips.setSelectedIndex(initial);
        chips.setOnSelect(index -> {
            Field chosen = hasNone && index == 0 ? null : members.get(hasNone ? index - 1 : index);
            for (Field field : members) {
                field.enabled = field == chosen;
            }
            if (hasDetail) {
                cards.show(detail, Integer.toString(index));
            }
            fireChange();
        });
        JPanel chipRow = new JPanel(new BorderLayout(Theme.SPACE_MD, 0));
        chipRow.setOpaque(false);
        chipRow.setAlignmentX(LEFT_ALIGNMENT);
        chipRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
        JPanel lead = new JPanel(new BorderLayout());
        lead.setOpaque(false);
        lead.add(title, BorderLayout.CENTER);
        lead.setPreferredSize(new Dimension(LEAD_WIDTH, ROW_HEIGHT));
        lead.setMinimumSize(new Dimension(LEAD_WIDTH, ROW_HEIGHT));
        lead.setMaximumSize(new Dimension(LEAD_WIDTH, ROW_HEIGHT));
        applyGroupTooltip(lead, block);
        applyGroupTooltip(chips, block);
        JPanel selector = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        selector.setOpaque(false);
        selector.add(chips);
        chipRow.add(lead, BorderLayout.WEST);
        chipRow.add(selector, BorderLayout.CENTER);
        panel.add(chipRow);
        if (hasDetail) {
            panel.add(Box.createVerticalStrut(Theme.SPACE_XS));
            panel.add(detail);
        }
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    /**
     * Returns whether a group member needs a visible detail editor.
     *
     * @param field option field
     * @return true when the option shows a value editor below its chip group
     */
    private static boolean needsValueEditor(Field field) {
        return field.option.takesValue() && !field.option.fixedChoice();
    }

    /**
     * Builds the detail card for one group member.
     *
     * @param field group member, or null for the "none" choice
     * @return card component
     */
    private JComponent memberCard(Field field) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.X_AXIS));
        card.setOpaque(false);
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.add(Box.createHorizontalStrut(LEAD_WIDTH + Theme.SPACE_MD));
        if (field == null) {
            JLabel none = new JLabel("no flag applied");
            none.setFont(Theme.font(11, Font.PLAIN));
            none.setForeground(Theme.MUTED);
            card.add(none);
            card.add(Box.createHorizontalGlue());
            return card;
        }
        if (field.option.takesValue() && !field.option.fixedChoice()) {
            JComponent editor = valueEditor(field);
            Dimension size = valueEditorSize(field);
            editor.setPreferredSize(size);
            editor.setMaximumSize(size);
            editor.setMinimumSize(size);
            card.add(editor);
        }
        applyOptionTooltip(card, field.option);
        card.add(Box.createHorizontalGlue());
        return card;
    }

    /**
     * Renders a standalone option as an aligned row.
     *
     * @param field option field
     * @param optionalSection true when rendered as optional
     * @return rendered row
     */
    private JComponent renderSingle(Field field, boolean optionalSection) {
        if (!optionalSection) {
            field.enabled = true;
            JLabel name = new JLabel(displayFlag(field.option));
            name.setFont(Theme.font(12, Font.BOLD));
            name.setForeground(Theme.TEXT);
            applyOptionTooltip(name, field.option);
            return optionRow(name, field);
        }
        ToggleBox toggle = new ToggleBox(displayFlag(field.option), true);
        toggle.setSelected(field.enabled);
        applyOptionTooltip(toggle, field.option);
        field.toggle = toggle;
        toggle.addActionListener(event -> {
            field.enabled = toggle.isSelected();
            applyConflicts(field);
            fireChange();
        });
        return optionRow(toggle, field);
    }

    /**
     * Builds one aligned option row: a fixed-width lead control, a fixed-width
     * value editor, then flexible whitespace.
     *
     * @param lead leading control (radio, toggle, or required label)
     * @param field option field
     * @return rendered row
     */
    private JComponent optionRow(JComponent lead, Field field) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
        row.setBorder(Theme.pad(2, 0, 2, 0));
        applyOptionTooltip(row, field.option);
        row.add(fixedLead(lead));
        row.add(Box.createHorizontalStrut(Theme.SPACE_SM));
        JComponent editor = field.option.takesValue() && !field.option.fixedChoice()
                ? valueEditor(field)
                : valuePlaceholder(field);
        Dimension size = valueEditorSize(field);
        editor.setPreferredSize(size);
        editor.setMaximumSize(size);
        editor.setMinimumSize(size);
        row.add(editor);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    /**
     * Returns a value-editor size appropriate for the option's content.
     *
     * @param field option field
     * @return editor dimensions
     */
    private static Dimension valueEditorSize(Field field) {
        int width = field.option.source() == ValueSource.CURRENT_FEN ? FEN_VALUE_WIDTH : VALUE_WIDTH;
        return new Dimension(width, Theme.CONTROL_HEIGHT);
    }

    /**
     * Wraps a lead control in a fixed-width container so value editors align.
     *
     * @param lead lead control
     * @return fixed-width wrapper
     */
    private static JComponent fixedLead(JComponent lead) {
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(lead);
        holder.setPreferredSize(new Dimension(LEAD_WIDTH, ROW_HEIGHT));
        holder.setMinimumSize(new Dimension(LEAD_WIDTH, ROW_HEIGHT));
        holder.setMaximumSize(new Dimension(LEAD_WIDTH, ROW_HEIGHT));
        holder.setAlignmentX(LEFT_ALIGNMENT);
        return holder;
    }

    /**
     * Builds the value editor for an option — a dropdown for a fixed value set,
     * otherwise a validated text field.
     *
     * @param field option field
     * @return value editor component
     */
    private JComponent valueEditor(Field field) {
        List<String> choices = enumChoices(field.option);
        if (!choices.isEmpty()) {
            return choiceEditor(field, choices);
        }
        JTextField text = new JTextField(field.value == null ? "" : field.value);
        Ui.styleFields(text);
        applyOptionTooltip(text, field.option);
        field.editor = text;
        field.defaultBorder = text.getBorder();
        Ui.onTextChange(() -> {
            field.value = text.getText();
            if (!rebuilding) {
                markManualOverride(field);
                autoEnableOnEdit(field);
            }
            updateFieldValidity(field);
            fireChange();
        }, text);
        updateFieldValidity(field);
        return text;
    }

    /**
     * Builds a dropdown value editor for an option with a fixed value set.
     *
     * @param field option field
     * @param choices allowed values
     * @return dropdown editor
     */
    private JComponent choiceEditor(Field field, List<String> choices) {
        List<String> items = new ArrayList<>();
        boolean optionalValue = !field.option.enabledByDefault()
                && field.option.exclusiveGroup().isBlank();
        if (optionalValue && !choices.contains("")) {
            items.add("");
        }
        items.addAll(choices);
        JComboBox<String> combo = new JComboBox<>(items.toArray(String[]::new));
        combo.setSelectedItem(items.contains(field.value) ? field.value
                : field.option.defaultValue());
        Ui.styleCombos(combo);
        combo.setRenderer(new EmptyChoiceRenderer());
        applyOptionTooltip(combo, field.option);
        field.combo = combo;
        combo.addActionListener(event -> {
            Object selected = combo.getSelectedItem();
            field.value = selected == null ? "" : selected.toString();
            if (!rebuilding) {
                markManualOverride(field);
                autoEnableOnEdit(field);
            }
            fireChange();
        });
        return combo;
    }

    /**
     * Builds the trailing placeholder for a value-less flag.
     *
     * @param field option field
     * @return placeholder component
     */
    private JComponent valuePlaceholder(Field field) {
        JLabel label = new JLabel(field.enabled && field.option.enabledByDefault()
                && field.option.exclusiveGroup().isBlank() ? "included" : "");
        label.setFont(Theme.font(11, Font.PLAIN));
        label.setForeground(Theme.MUTED);
        applyOptionTooltip(label, field.option);
        return label;
    }

    /**
     * Auto-enables an optional standalone flag when its value is edited.
     *
     * @param field option field
     */
    private void autoEnableOnEdit(Field field) {
        if (!field.enabled && field.value != null && !field.value.isBlank()
                && field.option.exclusiveGroup().isBlank() && field.toggle != null) {
            field.enabled = true;
            field.toggle.setSelected(true);
        }
    }

    /**
     * Marks a dynamic field as user-overridden once it no longer matches the
     * latest shared context value.
     *
     * @param field option field
     */
    private static void markManualOverride(Field field) {
        if (field.option.source() == ValueSource.STATIC) {
            return;
        }
        field.manuallyOverridden = !valuesEqual(field.value, field.dynamicValue);
    }

    /**
     * Applies a dynamic context value as the field's current default.
     *
     * @param field option field
     * @param value dynamic value
     */
    private static void setDynamicDefault(Field field, String value) {
        String normalized = value == null ? "" : value;
        field.value = normalized;
        field.dynamicValue = normalized;
        field.manuallyOverridden = false;
    }

    /**
     * Compares option values after normalizing null to the empty string.
     *
     * @param first first value
     * @param second second value
     * @return true when equal
     */
    private static boolean valuesEqual(String first, String second) {
        return Objects.equals(first == null ? "" : first, second == null ? "" : second);
    }

    /**
     * Applies an option description as a tooltip.
     *
     * @param component target component
     * @param option option metadata
     */
    private static void applyOptionTooltip(JComponent component, CommandOption option) {
        String tooltip = tooltipText(option);
        if (!tooltip.isBlank()) {
            component.setToolTipText(tooltip);
        }
    }

    /**
     * Applies a compact combined tooltip for an exclusive option group.
     *
     * @param component target component
     * @param block option group
     */
    private static void applyGroupTooltip(JComponent component, Block block) {
        StringBuilder text = new StringBuilder();
        for (Field field : block.members()) {
            String optionTooltip = tooltipText(field.option);
            if (!optionTooltip.isBlank()) {
                if (text.length() > 0) {
                    text.append(" | ");
                }
                text.append(displayFlag(field.option)).append(": ").append(optionTooltip);
            }
        }
        if (text.length() > 0) {
            component.setToolTipText(text.toString());
        }
    }

    /**
     * Returns the tooltip text for an option.
     *
     * @param option option metadata
     * @return tooltip text, or an empty string
     */
    private static String tooltipText(CommandOption option) {
        return option.description() == null ? "" : option.description().trim();
    }

    // ------------------------------------------------------------------
    // State changes + validation
    // ------------------------------------------------------------------

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
            if (field.editor != null && !field.editor.isFocusOwner()) {
                field.editor.setText(field.value == null ? "" : field.value);
                updateFieldValidity(field);
            }
            if (field.combo != null) {
                field.combo.setSelectedItem(field.value == null ? "" : field.value);
            }
        }
        rebuilding = false;
        fireChange();
    }

    /**
     * Re-validates a FEN field, tinting its border red when the FEN cannot be
     * parsed.
     *
     * @param field option field
     */
    private void updateFieldValidity(Field field) {
        if (field.editor == null) {
            return;
        }
        String error = validationError(field);
        field.valid = error == null;
        field.editor.setBorder(field.valid
                ? field.defaultBorder
                : BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.STATUS_ERROR_TEXT, 2),
                        Theme.pad(6, 8, 6, 8)));
        field.editor.setToolTipText(error == null ? tooltipText(field.option) : error);
    }

    /**
     * Filters the optional rows against the filter field.
     */
    private void applyFilter() {
        String query = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase(Locale.ROOT);
        for (FilterRow row : optionalRows) {
            boolean visible = query.isEmpty() || row.text().contains(query);
            row.component().setVisible(visible);
        }
        body.revalidate();
        body.repaint();
    }

    /**
     * Notifies listeners unless the form is mid-rebuild.
     */
    private void fireChange() {
        if (rebuilding) {
            return;
        }
        changeListener.run();
        runGate.accept(isRunnable());
    }

    /**
     * Returns whether the built command is currently runnable — every active
     * FEN field must hold a parseable FEN.
     *
     * @return true when runnable
     */
    private boolean isRunnable() {
        for (Field field : fields) {
            if (field.enabled && validationError(field) != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a validation error for the field value, or null when valid.
     *
     * @param field option field
     * @return validation error or null
     */
    private static String validationError(Field field) {
        if (field.value != null && !field.value.isBlank() && isFenOption(field.option)
                && !isValidFen(field.value)) {
            return "Not a valid FEN";
        }
        if (field.option.splitValue()) {
            try {
                CommandLine.split(field.value);
            } catch (IllegalArgumentException ex) {
                return ex.getMessage();
            }
        }
        return null;
    }

    /**
     * Returns whether an option carries a FEN value.
     *
     * @param option option metadata
     * @return true for FEN-valued options
     */
    private static boolean isFenOption(CommandOption option) {
        if (option.source() == ValueSource.CURRENT_FEN) {
            return true;
        }
        String flag = option.flag();
        return "--fen".equals(flag) || "--other".equals(flag) || "--right".equals(flag);
    }

    /**
     * Returns whether a string parses as a chess position.
     *
     * @param fen candidate FEN
     * @return true when valid
     */
    private static boolean isValidFen(String fen) {
        if (fen == null || fen.isBlank()) {
            return true;
        }
        try {
    return new Position(fen.trim()) != null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Extracts a fixed value set from an option's description — the comma- or
     * "or"-separated single-word tokens that the CLI accepts. Returns an empty
     * list for free-text options.
     *
     * @param option option metadata
     * @return ordered allowed values, or empty when free-text
     */
    private static List<String> enumChoices(CommandOption option) {
        if (!option.takesValue() || option.fixedChoice()) {
            return List.of();
        }
        if (!option.choices().isEmpty()) {
            return option.choices();
        }
        List<String> tokens = new ArrayList<>();
        for (String raw : option.description().split(",")) {
            String piece = raw;
            int colon = piece.lastIndexOf(':');
            if (colon >= 0) {
                piece = piece.substring(colon + 1);
            }
            piece = piece.trim().toLowerCase(Locale.ROOT);
            if (piece.startsWith("or ")) {
                piece = piece.substring(3).trim();
            }
            if (piece.startsWith("and ")) {
                piece = piece.substring(4).trim();
            }
            if (piece.matches("[a-z0-9][a-z0-9-]{0,13}") && !tokens.contains(piece)) {
                tokens.add(piece);
            }
        }
        String def = option.defaultValue() == null ? "" : option.defaultValue().toLowerCase(Locale.ROOT);
        boolean isEnum = tokens.size() >= 2 && tokens.size() <= 8
                && (tokens.contains(def) || def.isBlank());
        return isEnum ? tokens : List.of();
    }

    /**
     * Returns an option's user-facing label.
     *
     * @param option option metadata
     * @return display label
     */
    private static String displayFlag(CommandOption option) {
        String defaultValue = option.defaultValue() == null ? "" : option.defaultValue();
        if (option.fixedChoice()) {
    return choiceLabel(defaultValue);
        }
        if (option.flag().isBlank() && !option.takesValue() && !defaultValue.isBlank()) {
            return defaultValue;
        }
        if (option.flag().isBlank() && option.splitValue()) {
            return option.choices().isEmpty() ? "arguments" : "command";
        }
        return option.flag().isBlank() ? "argument" : option.flag();
    }

    /**
     * Formats a fixed selector value as a compact UI label.
     *
     * @param value CLI value
     * @return display label
     */
    private static String choiceLabel(String value) {
    return switch (value == null ? "" : value) {
            case "uci" -> "UCI";
            case "san" -> "SAN";
            case "both" -> "Both";
            case "uci-info" -> "UCI info";
            default -> value == null || value.isBlank()
                    ? "default"
                    : Character.toUpperCase(value.charAt(0)) + value.substring(1);
        };
    }

    /**
     * Dropdown renderer that makes optional empty values visible.
     */
    private static final class EmptyChoiceRenderer extends DefaultListCellRenderer {

        /**
         * Serialization identifier for Swing renderer compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Renders a dropdown value, showing blank optional values as "not set".
         *
         * @param list owning list
         * @param value raw value
         * @param index row index
         * @param isSelected true when selected
         * @param cellHasFocus true when focused
         * @return renderer component
         */
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            Object shown = value instanceof String text && text.isBlank() ? "not set" : value;
            JLabel label = (JLabel) super.getListCellRendererComponent(list, shown, index, isSelected,
                    cellHasFocus);
            label.setOpaque(true);
            label.setBorder(Theme.pad(6, 8));
            label.setFont(Theme.font(13, Font.PLAIN));
            label.setBackground(isSelected ? Theme.SELECTION_SOLID : Theme.ELEVATED_SOLID);
            label.setForeground(!isSelected && value instanceof String text && text.isBlank()
                    ? Theme.MUTED
                    : Theme.TEXT);
            return label;
        }
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
         * Latest value supplied by the shared workbench context for dynamic
         * options.
         */
        private String dynamicValue;

        /**
         * Whether a dynamic value has been edited independently of the shared
         * workbench context.
         */
        private boolean manuallyOverridden;

        /**
         * Whether the current value is valid.
         */
        private boolean valid = true;

        /**
         * Toggle control, when rendered as an optional flag.
         */
        private ToggleBox toggle;

        /**
         * Free-text editor, when the option takes a free-text value.
         */
        private JTextField editor;

        /**
         * The free-text editor's resting border, restored when the value is
         * valid.
         */
        private javax.swing.border.Border defaultBorder;

        /**
         * Dropdown editor, when the option takes a fixed value.
         */
        private JComboBox<String> combo;

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
            setDynamicDefault(this, value);
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
         * Returns whether this block belongs in the required section.
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

    /**
     * Wrapping optional-flag grid. It keeps each flag row internally aligned,
     * but places rows side by side whenever the available width can fit more
     * than one column.
     */
    private static final class FlagGridPanel extends JPanel {

        /**
         * Serialization identifier for Swing panel compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Creates the wrapping flag grid.
         */
        FlagGridPanel() {
            super(new FlagGridLayout());
            setOpaque(false);
        }

        /**
         * Keeps BoxLayout parents from constraining the grid to one narrow
         * preferred width.
         *
         * @return maximum grid size
         */
        @Override
        public Dimension getMaximumSize() {
            Dimension preferred = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, preferred.height);
        }
    }

    /**
     * Responsive row-major layout for optional command flags.
     */
    private static final class FlagGridLayout implements LayoutManager {

        /**
         * Accepts a child without named layout constraints.
         *
         * @param name ignored constraint name
         * @param component child component
         */
        @Override
        public void addLayoutComponent(String name, Component component) {
            // no named constraints
        }

        /**
         * Removes a child from the layout.
         *
         * @param component removed component
         */
        @Override
        public void removeLayoutComponent(Component component) {
            // no cached component state
        }

        /**
         * Calculates preferred size from the available parent width.
         *
         * @param parent parent container
         * @return preferred layout size
         */
        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return layoutSize(parent, availableWidth(parent));
        }

        /**
         * Calculates minimum size from one minimum-width column.
         *
         * @param parent parent container
         * @return minimum layout size
         */
        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return layoutSize(parent, minimumColumnWidth(parent));
        }

        /**
         * Lays out visible children in responsive rows.
         *
         * @param parent parent container
         */
        @Override
        public void layoutContainer(Container parent) {
            synchronized (parent.getTreeLock()) {
                Insets insets = parent.getInsets();
                int width = Math.max(0, parent.getWidth() - insets.left - insets.right);
                int columnWidth = minimumColumnWidth(parent);
                int columns = columnCount(width, columnWidth);
                List<Component> visible = visibleChildren(parent);
                int y = insets.top;
                for (int rowStart = 0; rowStart < visible.size(); rowStart += columns) {
                    int rowEnd = Math.min(visible.size(), rowStart + columns);
                    int rowHeight = rowHeight(visible, rowStart, rowEnd);
                    for (int index = rowStart; index < rowEnd; index++) {
                        Component child = visible.get(index);
                        int column = index - rowStart;
                        child.setBounds(insets.left + column * (columnWidth + FLAG_COLUMN_GAP),
                                y, columnWidth, rowHeight);
                    }
                    y += rowHeight + FLAG_ROW_GAP;
                }
            }
        }

        /**
         * Computes the preferred or minimum layout size for a target width.
         *
         * @param parent parent container
         * @param targetWidth available content width
         * @return layout size
         */
        private static Dimension layoutSize(Container parent, int targetWidth) {
            synchronized (parent.getTreeLock()) {
                Insets insets = parent.getInsets();
                int columnWidth = minimumColumnWidth(parent);
                int columns = columnCount(Math.max(columnWidth, targetWidth), columnWidth);
                List<Component> visible = visibleChildren(parent);
                int height = 0;
                for (int rowStart = 0; rowStart < visible.size(); rowStart += columns) {
                    int rowEnd = Math.min(visible.size(), rowStart + columns);
                    height += rowHeight(visible, rowStart, rowEnd);
                    if (rowEnd < visible.size()) {
                        height += FLAG_ROW_GAP;
                    }
                }
                int rows = visible.isEmpty() ? 0 : (visible.size() + columns - 1) / columns;
                int width = columns * columnWidth + Math.max(0, columns - 1) * FLAG_COLUMN_GAP;
                if (rows == 0) {
                    width = 0;
                }
                return new Dimension(width + insets.left + insets.right,
                        height + insets.top + insets.bottom);
            }
        }

        /**
         * Returns the usable width for preferred-size calculations.
         *
         * @param parent parent container
         * @return available width
         */
        private static int availableWidth(Container parent) {
            int width = parent.getWidth();
            if (width <= 0 && parent.getParent() != null) {
                width = parent.getParent().getWidth();
            }
            if (width <= 0) {
                width = minimumColumnWidth(parent);
            }
            Insets insets = parent.getInsets();
            return Math.max(0, width - insets.left - insets.right);
        }

        /**
         * Returns the largest preferred child width; this is the column width.
         *
         * @param parent parent container
         * @return minimum useful column width
         */
        private static int minimumColumnWidth(Container parent) {
            int width = 1;
            for (Component child : parent.getComponents()) {
                if (child.isVisible()) {
                    width = Math.max(width, child.getPreferredSize().width);
                }
            }
            return width;
        }

        /**
         * Returns how many columns fit in the target width.
         *
         * @param width available width
         * @param columnWidth column width
         * @return column count
         */
        private static int columnCount(int width, int columnWidth) {
            return Math.max(1, (width + FLAG_COLUMN_GAP) / Math.max(1, columnWidth + FLAG_COLUMN_GAP));
        }

        /**
         * Returns visible child components in layout order.
         *
         * @param parent parent container
         * @return visible children
         */
        private static List<Component> visibleChildren(Container parent) {
            List<Component> visible = new ArrayList<>();
            for (Component child : parent.getComponents()) {
                if (child.isVisible()) {
                    visible.add(child);
                }
            }
            return visible;
        }

        /**
         * Computes the maximum preferred height in one grid row.
         *
         * @param visible visible children
         * @param start inclusive start index
         * @param end exclusive end index
         * @return row height
         */
        private static int rowHeight(List<Component> visible, int start, int end) {
            int height = 0;
            for (int i = start; i < end; i++) {
                height = Math.max(height, visible.get(i).getPreferredSize().height);
            }
            return height;
        }
    }
}
