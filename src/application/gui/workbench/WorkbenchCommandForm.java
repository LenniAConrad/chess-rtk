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
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import application.gui.workbench.WorkbenchCommandTemplates.CommandOption;
import application.gui.workbench.WorkbenchCommandTemplates.CommandTemplate;
import application.gui.workbench.WorkbenchCommandTemplates.TemplateContext;
import application.gui.workbench.WorkbenchCommandTemplates.ValueSource;
import chess.core.Position;

/**
 * Structured command-builder form. Replaces the flat flag table with a layout
 * that mirrors the Batch runner: required inputs come first as plain labelled
 * fields, mutually-exclusive flags are packaged into radio groups so only one
 * can ever be picked, and the remaining optional flags sit below behind a
 * filter.
 *
 * <p>Every row shares one column grid so the controls, value editors, and
 * descriptions line up. Options with a small fixed set of values render as a
 * dropdown rather than a free-text field, and a FEN field is validated live —
 * an invalid command disables Run through the {@linkplain #setRunGate run
 * gate}.</p>
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
    private static final int ROW_HEIGHT = WorkbenchTheme.CONTROL_HEIGHT + 6;

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
     * Sets the run-gate callback, invoked with the command's validity whenever
     * the form changes.
     *
     * @param gate validity callback
     */
    void setRunGate(Consumer<Boolean> gate) {
        runGate = gate == null ? ready -> {
            // no-op
        } : gate;
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
            if (field.editor != null && field.editor.isFocusOwner()) {
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
            body.add(sectionHeader("Required", WorkbenchTheme.STATUS_INFO_TEXT));
            for (Block block : required) {
                body.add(renderBlock(block, false));
                body.add(Box.createVerticalStrut(WorkbenchTheme.SPACE_XS));
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
                body.add(Box.createVerticalStrut(WorkbenchTheme.SPACE_XS));
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
        panel.setBackground(WorkbenchTheme.ELEVATED_SOLID);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(WorkbenchTheme.LINE),
                WorkbenchTheme.pad(WorkbenchTheme.SPACE_SM)));
        JLabel title = new JLabel(block.group());
        title.setFont(WorkbenchTheme.font(11, Font.BOLD));
        title.setForeground(WorkbenchTheme.MUTED);
        title.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(WorkbenchTheme.SPACE_XS));

        List<Field> members = block.members();
        boolean hasNone = optionalSection;
        List<String> chipLabels = new ArrayList<>();
        if (hasNone) {
            chipLabels.add("none");
        }
        for (Field field : members) {
            chipLabels.add(displayFlag(field.option));
        }

        // The card layout swaps the value editor / description to the choice.
        java.awt.CardLayout cards = new java.awt.CardLayout();
        JPanel detail = new JPanel(cards);
        detail.setOpaque(false);
        detail.setAlignmentX(LEFT_ALIGNMENT);
        detail.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
        if (hasNone) {
            detail.add(memberCard(null), "0");
        }
        for (int m = 0; m < members.size(); m++) {
            detail.add(memberCard(members.get(m)), Integer.toString(hasNone ? m + 1 : m));
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
        cards.show(detail, Integer.toString(initial));

        WorkbenchChipGroup chips = new WorkbenchChipGroup(chipLabels);
        chips.setAlignmentX(LEFT_ALIGNMENT);
        chips.setSelectedIndex(initial);
        chips.setOnSelect(index -> {
            Field chosen = hasNone && index == 0 ? null : members.get(hasNone ? index - 1 : index);
            for (Field field : members) {
                field.enabled = field == chosen;
            }
            cards.show(detail, Integer.toString(index));
            fireChange();
        });
        JPanel chipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        chipRow.setOpaque(false);
        chipRow.setAlignmentX(LEFT_ALIGNMENT);
        chipRow.add(chips);
        panel.add(chipRow);
        panel.add(Box.createVerticalStrut(WorkbenchTheme.SPACE_XS));
        panel.add(detail);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    /**
     * Builds the value-editor + description card for one group member.
     *
     * @param field group member, or null for the "none" choice
     * @return card component
     */
    private JComponent memberCard(Field field) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.X_AXIS));
        card.setOpaque(false);
        card.setAlignmentX(LEFT_ALIGNMENT);
        if (field == null) {
            JLabel none = new JLabel("no option from this group is used");
            none.setFont(WorkbenchTheme.font(11, Font.PLAIN));
            none.setForeground(WorkbenchTheme.MUTED);
            card.add(none);
            card.add(Box.createHorizontalGlue());
            return card;
        }
        if (field.option.takesValue()) {
            JComponent editor = valueEditor(field);
            Dimension size = valueEditorSize(field);
            editor.setPreferredSize(size);
            editor.setMaximumSize(size);
            editor.setMinimumSize(size);
            card.add(editor);
            card.add(Box.createHorizontalStrut(WorkbenchTheme.SPACE_MD));
        }
        card.add(descriptionLabel(field.option));
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
            name.setFont(WorkbenchTheme.font(12, Font.BOLD));
            name.setForeground(WorkbenchTheme.TEXT);
            return optionRow(name, field);
        }
        WorkbenchToggleBox toggle = new WorkbenchToggleBox(displayFlag(field.option), true);
        toggle.setSelected(field.enabled);
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
     * value editor, then the description.
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
        row.setBorder(WorkbenchTheme.pad(2, 0, 2, 0));
        row.add(fixedLead(lead));
        row.add(Box.createHorizontalStrut(WorkbenchTheme.SPACE_SM));
        JComponent editor = field.option.takesValue() ? valueEditor(field) : valuePlaceholder(field);
        Dimension size = valueEditorSize(field);
        editor.setPreferredSize(size);
        editor.setMaximumSize(size);
        editor.setMinimumSize(size);
        row.add(editor);
        row.add(Box.createHorizontalStrut(WorkbenchTheme.SPACE_MD));
        row.add(descriptionLabel(field.option));
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
        return new Dimension(width, WorkbenchTheme.CONTROL_HEIGHT);
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
        // A BoxLayout.Y mixes positions when children disagree on alignmentX;
        // every row and lead holder must share the same left alignment.
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
        WorkbenchUi.styleFields(text);
        field.editor = text;
        field.defaultBorder = text.getBorder();
        WorkbenchUi.onTextChange(() -> {
            field.value = text.getText();
            autoEnableOnEdit(field);
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
        WorkbenchUi.styleCombos(combo);
        field.combo = combo;
        combo.addActionListener(event -> {
            Object selected = combo.getSelectedItem();
            field.value = selected == null ? "" : selected.toString();
            autoEnableOnEdit(field);
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
        label.setFont(WorkbenchTheme.font(11, Font.PLAIN));
        label.setForeground(WorkbenchTheme.MUTED);
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
     * Creates the muted description label shown at the row's trailing edge.
     *
     * @param option option metadata
     * @return description label
     */
    private static JLabel descriptionLabel(CommandOption option) {
        JLabel label = new JLabel(option.description());
        label.setFont(WorkbenchTheme.font(11, Font.PLAIN));
        label.setForeground(WorkbenchTheme.MUTED);
        return label;
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
        field.valid = !isFenOption(field.option) || isValidFen(field.value);
        field.editor.setBorder(field.valid
                ? field.defaultBorder
                : BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(WorkbenchTheme.STATUS_ERROR_TEXT, 2),
                        WorkbenchTheme.pad(6, 8, 6, 8)));
        field.editor.setToolTipText(field.valid ? null : "Not a valid FEN");
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
            if (field.enabled && isFenOption(field.option)
                    && field.value != null && !field.value.isBlank()
                    && !isValidFen(field.value)) {
                return false;
            }
        }
        return true;
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
        if (!option.takesValue()) {
            return List.of();
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
         * Whether the current value is valid.
         */
        private boolean valid = true;

        /**
         * Toggle control, when rendered as an optional flag.
         */
        private WorkbenchToggleBox toggle;

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
}
