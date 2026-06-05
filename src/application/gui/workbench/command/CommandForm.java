package application.gui.workbench.command;

import application.gui.workbench.command.CommandTemplates.CommandOption;
import application.gui.workbench.command.CommandTemplates.CommandTemplate;
import application.gui.workbench.command.CommandTemplates.TemplateContext;
import application.gui.workbench.command.CommandTemplates.ValueSource;
import application.gui.workbench.ui.ChipGroup;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.WrappingFlowLayout;
import chess.core.Position;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
     * Corner radius for a flag cell — matches the app-wide control radius so
     * cells read as the same family as buttons and cards.
     */
    private static final int CELL_ARC = Theme.RADIUS;

    /**
     * Inner padding inside a flag cell.
     */
    private static final int CELL_PAD_Y = 5;

    /**
     * Inner horizontal padding inside a flag cell.
     */
    private static final int CELL_PAD_X = 9;

    /**
     * Gap between a flag cell's lead control and its value editor.
     */
    private static final int CELL_GAP = Theme.SPACE_SM;

    /**
     * Optional-flag category labels.
     */
    private static final String CAT_INPUT = "Input";
    private static final String CAT_LIMITS = "Search limits";
    private static final String CAT_ENGINE = "Engine";
    private static final String CAT_OUTPUT = "Output";
    private static final String CAT_TEXT = "Text";
    private static final String CAT_OPTIONS = "Options";
    private static final String CAT_DIAGNOSTICS = "Diagnostics";

    /**
     * Category display order for the optional-flag section.
     */
    private static final List<String> CATEGORY_ORDER = List.of(CAT_INPUT, CAT_LIMITS, CAT_ENGINE,
            CAT_OUTPUT, CAT_TEXT, CAT_OPTIONS, CAT_DIAGNOSTICS);

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
     * Optional-section category sections, so an emptied category hides its
     * header during filtering.
     */
    private final transient List<CategorySection> categorySections = new ArrayList<>();

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
     * The currently selected template.
     */
    private transient CommandTemplate currentTemplate;

    /**
     * Multi-line position/command editor, lazily built and reused, embedded when
     * the selected template consumes a {@code --input} list.
     */
    private transient PositionListEditor positionEditor;

    /**
     * Host callbacks for the position-list editor (current FEN, dialogs).
     */
    private transient PositionListEditor.Context positionContext;

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
     * Sets the host callbacks the embedded position-list editor uses for the
     * current FEN, file dialogs, and error reporting.
     *
     * @param context position-editor host
     */
    public void setPositionContext(PositionListEditor.Context context) {
        positionContext = context;
    }

    /**
     * Returns whether the selected command reveals the multi-line position list.
     *
     * @return true when a position list is shown
     */
    public boolean hasPositionList() {
        return currentTemplate != null && currentTemplate.usesPositionList();
    }

    /**
     * Returns the current positions/commands text, or an empty string.
     *
     * @return positions text
     */
    public String positionsText() {
        return positionEditor == null ? "" : positionEditor.text();
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
        categorySections.clear();
        optionalDisclosure = null;
        currentTemplate = template;
        body.removeAll();
        if (template != null) {
            if (template.usesPositionList()) {
                installPositionEditor(template);
            }
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
     * Builds (once) and embeds the multi-line position/command editor for a
     * batch-style template, above the option rows. The editor is reused across
     * template switches so typed positions survive moving between batch
     * commands.
     *
     * @param template selected batch-style template
     */
    private void installPositionEditor(CommandTemplate template) {
        if (positionEditor == null) {
            positionEditor = new PositionListEditor(positionContext == null
                    ? new NoopPositionContext()
                    : positionContext);
            positionEditor.setChangeListener(this::fireChange);
        }
        positionEditor.setMode(template.inputKind());
        positionEditor.component().setAlignmentX(LEFT_ALIGNMENT);
        body.add(positionEditor.component());
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
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
            buildCategorySections(optionalPanel, optional);
            optionalDisclosure = Ui.collapsible("Flags", optionalPanel, false);
            body.add(optionalDisclosure);
        }
        body.add(Box.createVerticalGlue());
        applyFilter();
    }

    /**
     * Groups the optional blocks into titled category sections so dependent
     * flags read as one box, sorted by type within each category. Each section
     * is a category title above a wrapping row of self-contained flag cells.
     *
     * @param optionalPanel destination panel
     * @param optional optional blocks in declaration order
     */
    private void buildCategorySections(JPanel optionalPanel, List<Block> optional) {
        Map<String, List<Block>> byCategory = new LinkedHashMap<>();
        for (String category : CATEGORY_ORDER) {
            byCategory.put(category, new ArrayList<>());
        }
        for (Block block : optional) {
            byCategory.computeIfAbsent(categoryOf(block), key -> new ArrayList<>()).add(block);
        }
        boolean first = true;
        for (Map.Entry<String, List<Block>> entry : byCategory.entrySet()) {
            List<Block> members = entry.getValue();
            if (members.isEmpty()) {
                continue;
            }
            members.sort((a, b) -> Integer.compare(typeRank(a), typeRank(b)));
            if (!first) {
                optionalPanel.add(Box.createVerticalStrut(Theme.SPACE_SM));
            }
            first = false;
            JLabel header = new JLabel(entry.getKey());
            header.setFont(Theme.font(11, Font.BOLD));
            header.setForeground(Theme.MUTED);
            header.setAlignmentX(LEFT_ALIGNMENT);
            header.setBorder(Theme.pad(0, 2, Theme.SPACE_XS, 0));
            optionalPanel.add(header);

            JPanel flow = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, FLAG_COLUMN_GAP, FLAG_ROW_GAP));
            flow.setOpaque(false);
            flow.setAlignmentX(LEFT_ALIGNMENT);
            List<FilterRow> rows = new ArrayList<>();
            for (Block block : members) {
                JComponent rendered = renderBlock(block, true);
                flow.add(rendered);
                FilterRow row = new FilterRow(rendered, block.searchText());
                rows.add(row);
                optionalRows.add(row);
            }
            optionalPanel.add(flow);
            categorySections.add(new CategorySection(header, flow, rows));
        }
    }

    /**
     * Returns the display category for an optional block, derived from its
     * existing metadata (exclusive group, value source, or flag name).
     *
     * @param block optional block
     * @return category label
     */
    private static String categoryOf(Block block) {
        if (block.isGroup()) {
            return switch (block.group()) {
                case "position source", "input source" -> CAT_INPUT;
                case "evaluator" -> CAT_ENGINE;
                case "move format", "output format", "output mode", "wdl toggle" -> CAT_OUTPUT;
                case "text engine", "text detail" -> CAT_TEXT;
                default -> CAT_OPTIONS;
            };
        }
        CommandOption option = block.members().get(0).option;
        switch (option.source()) {
            case DURATION, DEPTH, MULTIPV, THREADS, NODES, HASH:
                return CAT_LIMITS;
            case PROTOCOL:
                return CAT_ENGINE;
            default:
                break;
        }
        String flag = option.flag();
        if ("--verbose".equals(flag)) {
            return CAT_DIAGNOSTICS;
        }
        if (flag.startsWith("--json") || flag.startsWith("--jsonl") || "--quiet".equals(flag)
                || "--no-header".equals(flag) || "--fields".equals(flag) || "--include-fen".equals(flag)
                || "--output".equals(flag) || "--weights".equals(flag)) {
            return "--weights".equals(flag) ? CAT_ENGINE : CAT_OUTPUT;
        }
        return CAT_OPTIONS;
    }

    /**
     * Returns a within-category sort rank by control type: exclusive groups,
     * then value flags, then boolean toggles.
     *
     * @param block optional block
     * @return type rank
     */
    private static int typeRank(Block block) {
        if (block.isGroup()) {
            return 0;
        }
        return block.members().get(0).option.takesValue() ? 1 : 2;
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
        JComponent content = block.isGroup()
                ? renderChipGroup(block, optionalSection)
                : renderSingle(block.members().get(0), optionalSection);
        // Bind a flag's label and control into one bordered cell so they read
        // as a single unit instead of a control floating in an empty row.
        return new CellPanel(content);
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
        // The enclosing flag cell supplies the padding and border now.
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
        int detailIndent = optionalSection ? 0 : LEAD_WIDTH + Theme.SPACE_MD;
        java.awt.CardLayout cards = new java.awt.CardLayout();
        JPanel detail = new JPanel(cards);
        detail.setOpaque(false);
        detail.setAlignmentX(LEFT_ALIGNMENT);
        detail.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
        if (hasDetail) {
            if (hasNone) {
                detail.add(memberCard(null, detailIndent), "0");
            }
            for (int m = 0; m < members.size(); m++) {
                detail.add(memberCard(members.get(m), detailIndent), Integer.toString(hasNone ? m + 1 : m));
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
        applyGroupTooltip(title, block);
        applyGroupTooltip(chips, block);

        if (optionalSection) {
            // A compact, self-contained cell: the group title sits above its
            // chips so the box hugs its content as it flows beside its peers.
            chips.setAlignmentX(LEFT_ALIGNMENT);
            JPanel chipsHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            chipsHolder.setOpaque(false);
            chipsHolder.setAlignmentX(LEFT_ALIGNMENT);
            chipsHolder.add(chips);
            chipsHolder.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                    chipsHolder.getPreferredSize().height));
            panel.add(title);
            panel.add(Box.createVerticalStrut(Theme.SPACE_XS));
            panel.add(chipsHolder);
        } else {
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
            JPanel selector = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            selector.setOpaque(false);
            selector.add(chips);
            chipRow.add(lead, BorderLayout.WEST);
            chipRow.add(selector, BorderLayout.CENTER);
            panel.add(chipRow);
        }
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
     * @param indent leading indent so the editor aligns under the chips
     * @return card component
     */
    private JComponent memberCard(Field field, int indent) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.X_AXIS));
        card.setOpaque(false);
        card.setAlignmentX(LEFT_ALIGNMENT);
        if (indent > 0) {
            card.add(Box.createHorizontalStrut(indent));
        }
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
        return optionalFieldContent(toggle, field);
    }

    /**
     * Builds the content of an optional flag cell: the toggle, and its value
     * editor when the flag takes a value, hugging their content so the cell
     * stays compact as it flows beside its peers.
     *
     * @param lead toggle control
     * @param field option field
     * @return cell content
     */
    private JComponent optionalFieldContent(JComponent lead, Field field) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        applyOptionTooltip(row, field.option);
        lead.setAlignmentY(CENTER_ALIGNMENT);
        row.add(lead);
        if (field.option.takesValue() && !field.option.fixedChoice()) {
            row.add(Box.createHorizontalStrut(CELL_GAP));
            JComponent editor = valueEditor(field);
            Dimension size = valueEditorSize(field);
            editor.setPreferredSize(size);
            editor.setMaximumSize(size);
            editor.setMinimumSize(size);
            editor.setAlignmentY(CENTER_ALIGNMENT);
            row.add(editor);
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
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
        // Hide a category header/flow once every flag it holds is filtered out.
        for (CategorySection section : categorySections) {
            boolean anyVisible = false;
            for (FilterRow row : section.rows()) {
                if (row.component().isVisible()) {
                    anyVisible = true;
                    break;
                }
            }
            section.header().setVisible(anyVisible);
            section.flow().setVisible(anyVisible);
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
        // A batch-style command needs a valid, non-empty input list to run.
        return !hasPositionList() || positionEditor == null || positionEditor.isReady();
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
     * A category section in the optional area: a header and its flowing cells,
     * tracked so filtering can hide the header once every cell is filtered out.
     *
     * @param header category title label
     * @param flow wrapping container of flag cells
     * @param rows the section's filterable rows
     */
    private record CategorySection(JComponent header, JComponent flow, List<FilterRow> rows) {
    }

    /**
     * Fallback position-editor context used only until the host wires a real
     * one — provides no current FEN and a null dialog owner.
     */
    private static final class NoopPositionContext implements PositionListEditor.Context {

        /**
         * Returns an empty current FEN.
         *
         * @return empty string
         */
        @Override
        public String currentFen() {
            return "";
        }

        /**
         * Ignores error reports.
         *
         * @param title error title
         * @param message error message
         */
        @Override
        public void showError(String title, String message) {
            // no host wired yet
        }

        /**
         * Returns no dialog owner.
         *
         * @return null
         */
        @Override
        public java.awt.Component dialogParent() {
            return null;
        }
    }

    /**
     * A rounded hairline-bordered cell that binds a flag's label and control
     * into one unit. It paints its own surface so a theme toggle cannot restamp
     * it, and hugs its content height so wrapping/stacking parents stay tight.
     */
    private static final class CellPanel extends JPanel {

        /**
         * Serialization identifier for Swing panel compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Wraps cell content with rounded chrome and padding.
         *
         * @param content cell content
         */
        CellPanel(JComponent content) {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(Theme.pad(CELL_PAD_Y, CELL_PAD_X, CELL_PAD_Y, CELL_PAD_X));
            setAlignmentX(LEFT_ALIGNMENT);
            content.setAlignmentX(LEFT_ALIGNMENT);
            add(content, BorderLayout.CENTER);
        }

        /**
         * Hugs the content height so a BoxLayout parent cannot stretch the cell.
         *
         * @return maximum size with content height
         */
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        /**
         * Paints the rounded cell surface and hairline border.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g.setColor(Theme.ELEVATED_SOLID);
                g.fillRoundRect(0, 0, w - 1, h - 1, CELL_ARC, CELL_ARC);
                g.setColor(Theme.CARD_BORDER);
                g.drawRoundRect(0, 0, w - 1, h - 1, CELL_ARC, CELL_ARC);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

}
