package application.gui.layout.command;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import application.gui.model.CommandSpec;
import application.gui.model.RecentCommand;
import application.gui.ui.RoundedPanel;

/**
 * CommandCenterPanel class.
 *
 * Provides class behavior for the GUI module.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class CommandCenterPanel {

    /**
     * CommandCenterPanel method.
     */
    private CommandCenterPanel() {
        // utility class
    }

    /**
     * Result record.
     *
     * Provides record behavior for the GUI module.
     *
     * @since 2026
     * @author Lennart A. Conrad
     */
    public record Result(
            RoundedPanel panel,
            JComboBox<String> commandSelect,
            JPanel commandFieldsPanel,
            JPanel commandFenPanel,
            JTextField commandFenField,
            JLabel commandFenHint,
            JTextField commandExtraArgsField,
            JCheckBox useFenToggle,
            JButton runButton,
            JButton stopButton,
            JButton helpButton,
            DefaultListModel<RecentCommand> recentCommandModel,
            JList<RecentCommand> recentCommandList,
            JScrollPane recentCommandScroll,
            Dimension recentScrollPref,
            JTextArea commandOutput) {}

    /**
     * build method.
     *
     * @param ctx parameter.
     * @param commandSpecs parameter.
     * @return return value.
     */
    public static Result build(CommandCenterContext ctx, Map<String, CommandSpec> commandSpecs) {
        RoundedPanel card = ctx.buildFlatCard("Command Center");
        ctx.registerFlatCard(card);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        JPanel headRow = new JPanel(new BorderLayout(8, 8));
        headRow.setOpaque(false);
        JLabel selectLabel = ctx.createMutedLabel("Command");
        headRow.add(selectLabel, BorderLayout.WEST);
        JComboBox<String> commandSelect = new JComboBox<>(commandSpecs.keySet().toArray(new String[0]));
        ctx.registerComboBox(commandSelect);
        headRow.add(commandSelect, BorderLayout.CENTER);

        JPanel commandFieldsPanel = new JPanel();
        commandFieldsPanel.setOpaque(false);
        commandFieldsPanel.setLayout(new BoxLayout(commandFieldsPanel, BoxLayout.Y_AXIS));

        JPanel commandFenPanel = new JPanel();
        commandFenPanel.setOpaque(false);
        commandFenPanel.setLayout(new BoxLayout(commandFenPanel, BoxLayout.Y_AXIS));

        JPanel fenRow = new JPanel(new BorderLayout(8, 4));
        fenRow.setOpaque(false);
        fenRow.add(ctx.createMutedLabel("FEN"), BorderLayout.WEST);
        JTextField commandFenField = new JTextField();
        ctx.registerTextField(commandFenField);
        fenRow.add(commandFenField, BorderLayout.CENTER);
        JCheckBox useFenToggle = ctx.createThemedCheckbox("Use current FEN", true, e -> ctx.requestFenToggle());
        fenRow.add(useFenToggle, BorderLayout.EAST);
        commandFenPanel.add(fenRow);
        JLabel commandFenHint = ctx.createMutedLabel("Example: rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        commandFenPanel.add(commandFenHint);

        JTextField commandExtraArgsField = new JTextField();
        ctx.registerTextField(commandExtraArgsField);
        JPanel extraRow = new JPanel(new BorderLayout(8, 8));
        extraRow.setOpaque(false);
        extraRow.add(ctx.createMutedLabel("Extra args"), BorderLayout.WEST);
        extraRow.add(commandExtraArgsField, BorderLayout.CENTER);

        JPanel actionRow = new JPanel(new GridLayout(1, 0, 8, 8));
        actionRow.setOpaque(false);
        JButton runButton = ctx.createThemedButton("Run", e -> ctx.requestCommandRun());
        JButton stopButton = ctx.createThemedButton("Stop", e -> ctx.requestCommandStop());
        JButton helpButton = ctx.createThemedButton("Help", e -> ctx.requestCommandHelp());
        stopButton.setEnabled(false);
        actionRow.add(runButton);
        actionRow.add(stopButton);
        actionRow.add(helpButton);
        ctx.registerButton(runButton);
        ctx.registerButton(stopButton);
        ctx.registerButton(helpButton);

        DefaultListModel<RecentCommand> recentCommandModel = new DefaultListModel<>();
        JList<RecentCommand> recentCommandList = new JList<>(recentCommandModel);
        ctx.registerList(recentCommandList);
        recentCommandList.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        recentCommandList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = recentCommandList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        ctx.requestRecentCommand(idx);
                    }
                }
            }
        });

        JScrollPane recentScroll = new JScrollPane(recentCommandList);
        ctx.registerScrollPane(recentScroll);
        Dimension recentScrollPref = new Dimension(260, 90);
        recentScroll.setBorder(BorderFactory.createEmptyBorder());
        recentScroll.setPreferredSize(ctx.scaledDimension(recentScrollPref));

        JTextArea commandOutput = new JTextArea(8, 26);
        commandOutput.setEditable(false);
        commandOutput.setLineWrap(true);
        commandOutput.setWrapStyleWord(true);
        ctx.registerTextArea(commandOutput);
        JScrollPane outScroll = new JScrollPane(commandOutput);
        outScroll.setBorder(BorderFactory.createEmptyBorder());
        ctx.registerScrollPane(outScroll);

        body.add(headRow);
        body.add(Box.createVerticalStrut(10));
        body.add(commandFenPanel);
        body.add(Box.createVerticalStrut(8));
        body.add(commandFieldsPanel);
        body.add(Box.createVerticalStrut(8));
        body.add(extraRow);
        body.add(Box.createVerticalStrut(8));
        body.add(actionRow);
        body.add(Box.createVerticalStrut(8));
        body.add(ctx.createMutedLabel("Recent"));
        body.add(Box.createVerticalStrut(6));
        body.add(recentScroll);
        body.add(Box.createVerticalStrut(8));
        body.add(outScroll);
        commandSelect.addActionListener(e -> ctx.requestCommandFormUpdate());
        card.setContent(body);
        return new Result(card, commandSelect, commandFieldsPanel, commandFenPanel, commandFenField, commandFenHint,
                commandExtraArgsField, useFenToggle, runButton, stopButton, helpButton, recentCommandModel,
                recentCommandList, recentScroll, recentScrollPref, commandOutput);
    }
}
