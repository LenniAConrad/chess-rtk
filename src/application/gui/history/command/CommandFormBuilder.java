package application.gui.history.command;

import application.gui.history.ui.HistoryUiFactory;
import application.gui.model.CommandFieldBinding;
import application.gui.model.CommandFieldSpec;
import application.gui.model.CommandFieldType;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

 /**
  * Renders the form for a selected CLI command inside the history window.
  *
  * @since 2026
  * @author Lennart A. Conrad
  */
public final class CommandFormBuilder {

    /**
     * uiFactory field.
     */
    private final HistoryUiFactory uiFactory;
    /**
     * textFieldRegistrar field.
     */
    private final Consumer<JTextField> textFieldRegistrar;

    /**
     * CommandFormBuilder method.
     *
     * @param uiFactory parameter.
     * @param textFieldRegistrar parameter.
     */
    public CommandFormBuilder(HistoryUiFactory uiFactory, Consumer<JTextField> textFieldRegistrar) {
        this.uiFactory = Objects.requireNonNull(uiFactory, "uiFactory");
        this.textFieldRegistrar = Objects.requireNonNull(textFieldRegistrar, "textFieldRegistrar");
    }

    /**
     * render method.
     *
     * @param commandFieldsPanel parameter.
     * @param commandBindings parameter.
     * @param fields parameter.
     * @param flagFactory parameter.
     */
    public void render(JPanel commandFieldsPanel, Collection<CommandFieldBinding> commandBindings,
            CommandFieldSpec[] fields,
            Function<CommandFieldSpec, JCheckBox> flagFactory) {
        commandFieldsPanel.removeAll();
        commandBindings.clear();
        if (fields == null || fields.length == 0) {
            commandFieldsPanel.revalidate();
            commandFieldsPanel.repaint();
            return;
        }
        for (CommandFieldSpec field : fields) {
            CommandFieldBinding binding = buildCommandField(field, flagFactory);
            commandBindings.add(binding);
            commandFieldsPanel.add(binding.container());
            commandFieldsPanel.add(Box.createVerticalStrut(6));
        }
        commandFieldsPanel.revalidate();
        commandFieldsPanel.repaint();
    }

    /**
     * buildCommandField method.
     *
     * @param field parameter.
     * @param flagFactory parameter.
     * @return return value.
     */
    private CommandFieldBinding buildCommandField(CommandFieldSpec field,
            Function<CommandFieldSpec, JCheckBox> flagFactory) {
        if (field.type() == CommandFieldType.FLAG) {
            JCheckBox box = flagFactory.apply(field);
            JPanel flagRow = new JPanel(new BorderLayout());
            flagRow.setOpaque(false);
            flagRow.add(box, BorderLayout.WEST);
            return new CommandFieldBinding(field, flagRow, box, null);
        }
        JTextField input = new JTextField();
        textFieldRegistrar.accept(input);
        int columns = switch (field.type()) {
            case NUMBER -> 6;
            case PATH -> 18;
            case TEXT -> 12;
            case FLAG -> 8;
        };
        input.setColumns(columns);
        if (!field.placeholder().isEmpty()) {
            input.setToolTipText(field.placeholder());
        }
        Dimension pref = input.getPreferredSize();
        input.setMaximumSize(new Dimension(pref.width, pref.height));

        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.add(uiFactory.mutedLabel(field.label()));
        row.add(Box.createHorizontalStrut(8));
        row.add(input);
        row.add(Box.createHorizontalGlue());

        JPanel container = new JPanel();
        container.setOpaque(false);
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.add(row);
        if (!field.placeholder().isEmpty()) {
            container.add(uiFactory.mutedLabel(field.placeholder()));
        }
        return new CommandFieldBinding(field, container, null, input);
    }
}
